package skillbill.infrastructure.fs.install.nativeagent

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import skillbill.error.InvalidNativeAgentLinkInventoryDecodeError
import skillbill.infrastructure.fs.launcher.process.sha256Hex
import skillbill.error.ShellContentContractException
import skillbill.install.model.SupportedAgent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

internal object NativeAgentLinkInventoryDecode {
  fun decode(
    path: Path,
    home: Path,
    managedRoots: List<Path>,
    mapper: ObjectMapper,
    schema: JsonSchema,
  ): List<NativeAgentLinkInventoryEntry> {
    try {
      if (Files.size(path) > NativeAgentLinkInventoryLimits.MAX_BYTES) {
        throw decodeError(path, "inventory exceeds ${NativeAgentLinkInventoryLimits.MAX_BYTES} bytes")
      }
      val root = mapper.readTree(path.toFile())
        ?: throw decodeError(path, "<root> must be an object")
      if (!root.isObject) {
        throw decodeError(path, "<root> must be an object")
      }
      val schemaErrors = schema.validate(root)
      if (schemaErrors.isNotEmpty()) {
        throw decodeError(path, schemaErrors.joinToString("; ") { it.message })
      }
      val entries = root["entries"]?.elements()?.asSequence()?.map { node ->
        NativeAgentLinkInventoryEntry(
          logicalName = node.requiredText("logical_name", path),
          provider = node.requiredText("provider", path),
          installedPath = Path.of(node.requiredText("installed_path", path)),
          cacheTargetPath = Path.of(node.requiredText("cache_target_path", path)),
          contentDigest = node.requiredText("content_digest", path),
          sourceRoot = Path.of(node.requiredText("source_root", path)),
        )
      }?.toList() ?: throw decodeError(path, "entries is required")
      validateDecodedEntries(entries, home, managedRoots, path)
      return entries
    } catch (error: CancellationException) {
      throw error
    } catch (error: ShellContentContractException) {
      throw error
    } catch (error: IOException) {
      throw decodeError(path, error.message.orEmpty(), error)
    } catch (error: IllegalArgumentException) {
      throw decodeError(path, error.message.orEmpty(), error)
    }
  }

  fun validateSemanticEntries(entries: List<NativeAgentLinkInventoryEntry>, home: Path, managedRoots: List<Path>) {
    validateDecodedEntries(entries, home, managedRoots, Path.of("<semantic>"))
  }

  fun isSemanticallyValid(entry: NativeAgentLinkInventoryEntry, home: Path, managedRoots: List<Path>): Boolean {
    return runCatching {
      val provider = NativeAgentLinkInventoryPaths.provider(entry.provider)
      val raw = Files.readSymbolicLink(entry.installedPath)
      val resolved = entry.installedPath.parent.resolve(raw).toAbsolutePath().normalize()
      entry.installedPath.fileName.toString() == provider.fileName(entry.logicalName) &&
        Files.isSymbolicLink(entry.installedPath) &&
        resolved == entry.cacheTargetPath &&
        isCanonicalNativeAgentArtifactTarget(home, provider, entry.logicalName, resolved, managedRoots) &&
        Files.isRegularFile(resolved) &&
        Files.isReadable(resolved) &&
        parseEmbeddedLogicalName(resolved, SupportedAgent.fromWire(entry.provider)) == entry.logicalName &&
        sha256Hex(Files.readAllBytes(resolved)) == entry.contentDigest
    }.getOrDefault(false)
  }

  private fun validateDecodedEntries(
    entries: List<NativeAgentLinkInventoryEntry>,
    home: Path,
    managedRoots: List<Path>,
    path: Path,
  ) {
    if (entries.map { it.provider to it.installedPath.normalize() }.distinct().size != entries.size) {
      throw decodeError(path, "duplicate provider/installed_path entry")
    }
    if (
      entries.groupBy { Triple(it.provider, it.installedPath.parent.normalize(), it.logicalName) }
        .values.any { it.size > 1 }
    ) {
      throw decodeError(path, "duplicate provider/directory/logical_name entry")
    }
    entries.forEach { entry ->
      if (entry.provider !in NativeAgentLinkInventoryLimits.PROVIDERS) {
        throw decodeError(path, "unsupported provider '${entry.provider}'")
      }
      if (!entry.contentDigest.matches(Regex("[0-9a-f]{${NativeAgentLinkInventoryLimits.DIGEST_HEX_LENGTH}}"))) {
        throw decodeError(path, "invalid content_digest")
      }
      if (!entry.installedPath.isAbsolute) throw decodeError(path, "installed_path must be absolute")
      if (!entry.cacheTargetPath.isAbsolute) throw decodeError(path, "cache_target_path must be absolute")
      if (
        !entry.sourceRoot.isAbsolute ||
        entry.sourceRoot.toString().length > NativeAgentLinkInventoryLimits.MAX_SOURCE_ROOT_LENGTH
      ) {
        throw decodeError(path, "source_root must be an absolute bounded path")
      }
      if (entry.sourceRoot != entry.sourceRoot.normalize()) {
        throw decodeError(path, "source_root must be normalized")
      }
      if (entry.installedPath != entry.installedPath.normalize()) {
        throw decodeError(path, "installed_path must be normalized")
      }
      if (entry.cacheTargetPath != entry.cacheTargetPath.normalize()) {
        throw decodeError(path, "cache_target_path must be normalized")
      }
      if (!NativeAgentLinkInventoryLimits.LOGICAL_NAME.matches(entry.logicalName)) {
        throw decodeError(path, "logical_name must be a single filename stem")
      }
      val provider = NativeAgentLinkInventoryPaths.provider(entry.provider)
      val allowedDirs = provider.homeAgentDirs(home).map { it.toAbsolutePath().normalize() }
      if (entry.installedPath.parent !in allowedDirs) {
        throw decodeError(path, "installed_path is outside provider directory")
      }
      if (entry.installedPath.fileName.toString() != provider.fileName(entry.logicalName)) {
        throw decodeError(path, "installed_path does not match provider/logical_name identity")
      }
      if (
        !isCanonicalNativeAgentArtifactTarget(home, provider, entry.logicalName, entry.cacheTargetPath, managedRoots)
      ) {
        throw decodeError(path, "cache_target_path does not match a trusted provider artifact")
      }
    }
  }

  private fun JsonNode.requiredText(field: String, path: Path): String =
    get(field)?.asText()?.takeIf(String::isNotBlank)
      ?: throw decodeError(path, "$field is required")

  private fun decodeError(path: Path, reason: String, cause: Throwable? = null): InvalidNativeAgentLinkInventoryDecodeError =
    InvalidNativeAgentLinkInventoryDecodeError(
      path = path.toString(),
      reason = "$reason. Delete it and reinstall.",
      cause = cause,
    )
}
