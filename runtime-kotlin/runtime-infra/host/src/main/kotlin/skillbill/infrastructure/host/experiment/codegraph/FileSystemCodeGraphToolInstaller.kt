package skillbill.infrastructure.host.experiment.codegraph

import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.error.shellcontent.CodeGraphInstallRefusalError
import skillbill.infrastructure.contracts.sha256Hex
import skillbill.ports.experiment.codegraph.CodeGraphToolInstallPort
import skillbill.ports.experiment.codegraph.model.CodeGraphInstalledTool
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

private const val INSTALL_LOCK_RETRY_MILLIS: Long = 25L
private const val OVERRIDE_VERSION_TIMEOUT_MILLIS: Long = 5_000L
private const val OVERRIDE_OUTPUT_BYTE_CAP: Int = 8_192

@OptIn(ExperimentalPathApi::class)
class FileSystemCodeGraphToolInstaller(
  private val dependencyStartPath: Path,
  private val transport: (String) -> ByteArray = { url ->
    URI(url).toURL().openStream().use { input -> input.readBytes() }
  },
) : CodeGraphToolInstallPort {
  private data class InstallContext(
    val dependency: Map<String, Any?>,
    val asset: Map<*, *>,
    val releaseTag: String,
    val platformId: String,
    val binaryName: String,
    val expectedDigest: String,
    val binaryPath: Path,
    val digestPath: Path,
  )

  private data class DownloadInstallRequest(
    val dependency: Map<String, Any?>,
    val asset: Map<*, *>,
    val userHome: Path,
    val releaseTag: String,
    val platformId: String,
    val binaryName: String,
    val binaryPath: Path,
    val expectedDigest: String,
  )

  override fun install(request: CodeGraphToolInstallRequest): CodeGraphInstalledTool {
    val context = installContext(request)
    request.localExecutableOverride?.let { override ->
      return resolveLocalOverride(
        dependency = context.dependency,
        override = override,
        releaseTag = context.releaseTag,
        platformId = context.platformId,
      )
    }
    cachedTool(context)?.let { return it }
    return installUnderLock(request.userHome, context)
  }

  private fun installContext(request: CodeGraphToolInstallRequest): InstallContext {
    val dependency = CodeGraphDependencyLoader.load(dependencyStartPath)
    val releaseTag = requestedReleaseTag(dependency, request.pinnedReleaseTag)
    val platformId = managedPlatformId()
    val asset = platformAsset(dependency, platformId)
    val expectedDigest = assetDigest(asset)
    val binaryName = requiredBinaryName(dependency)
    return InstallContext(
      dependency = dependency,
      asset = asset,
      releaseTag = releaseTag,
      platformId = platformId,
      binaryName = binaryName,
      expectedDigest = expectedDigest,
      binaryPath = CodeGraphToolLayout.binaryPath(request.userHome, releaseTag, binaryName, platformId),
      digestPath = CodeGraphToolLayout.assetDigestPath(request.userHome, releaseTag),
    )
  }

  private fun requestedReleaseTag(dependency: Map<String, Any?>, requestedTag: String?): String {
    val declaredTag = declaredReleaseTag(dependency)
    val releaseTag = requestedTag ?: declaredTag
    if (releaseTag != declaredTag) {
      throw CodeGraphInstallRefusalError(
        "requested release '$releaseTag' does not match governed release '$declaredTag'.",
      )
    }
    return releaseTag
  }

  private fun managedPlatformId(): String = CodeGraphHostPlatform.currentPlatformId()
    ?: throw CodeGraphInstallRefusalError("host platform is not supported for managed CodeGraph install.")

  private fun assetDigest(asset: Map<*, *>): String =
    asset[CodeGraphDependencyPayloadKeys.SHA256]?.toString()?.trim()?.lowercase()
      ?: throw CodeGraphInstallRefusalError("platform asset is missing a sha256 digest.")

  private fun requiredBinaryName(dependency: Map<String, Any?>): String = cliBinaryName(dependency)
    ?: throw CodeGraphInstallRefusalError("dependency declaration is missing cli.binary_name.")

  private fun cachedTool(context: InstallContext): CodeGraphInstalledTool? {
    if (!context.binaryPath.exists() || !Files.isRegularFile(context.binaryPath)) return null
    if (!Files.isRegularFile(context.digestPath) ||
      Files.readString(context.digestPath).trim().lowercase() != context.expectedDigest
    ) {
      throw CodeGraphInstallRefusalError(
        "cached CodeGraph asset digest mismatch; refusing to execute a corrupt install.",
      )
    }
    if (!Files.isExecutable(context.binaryPath)) {
      throw CodeGraphInstallRefusalError("cached CodeGraph binary is not executable.")
    }
    return CodeGraphInstalledTool(
      context.releaseTag,
      context.binaryPath,
      context.platformId,
      context.expectedDigest,
    )
  }

  private fun installUnderLock(userHome: Path, context: InstallContext): CodeGraphInstalledTool {
    val lockFile = CodeGraphToolLayout.toolsRoot(userHome).resolve(CodeGraphToolLayout.INSTALL_LOCK_FILE)
    Files.createDirectories(lockFile.parent)
    return RandomAccessFile(lockFile.toFile(), "rw").channel.use { channel ->
      val lock = acquireLock(channel)
      try {
        cachedTool(context) ?: downloadAndInstall(
          DownloadInstallRequest(
            dependency = context.dependency,
            asset = context.asset,
            userHome = userHome,
            releaseTag = context.releaseTag,
            platformId = context.platformId,
            binaryName = context.binaryName,
            binaryPath = context.binaryPath,
            expectedDigest = context.expectedDigest,
          ),
        )
      } finally {
        lock.release()
      }
    }
  }

  private fun acquireLock(channel: FileChannel): FileLock {
    while (true) {
      val lock = try {
        channel.tryLock()
      } catch (_: OverlappingFileLockException) {
        null
      }
      if (lock != null) return lock
      try {
        Thread.sleep(INSTALL_LOCK_RETRY_MILLIS)
      } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CodeGraphInstallRefusalError("CodeGraph install was interrupted.", error)
      }
    }
  }

  override fun releaseOwnedProcesses(pairId: String) {
    CodeGraphProcessRegistry.destroyOwned(pairId)
  }

  override fun resolveInstalledBinary(userHome: Path, releaseTag: String): Path? {
    val dependency = CodeGraphDependencyLoader.load(dependencyStartPath)
    return sequenceOf(dependency)
      .filter { releaseTag == declaredReleaseTag(it) }
      .mapNotNull { cliBinaryName(it)?.let { binaryName -> it to binaryName } }
      .mapNotNull { (declaredDependency, binaryName) ->
        CodeGraphHostPlatform.currentPlatformId()?.let { platformId ->
          Triple(declaredDependency, binaryName, platformId)
        }
      }
      .mapNotNull { (declaredDependency, binaryName, platformId) ->
        val asset = platformAsset(declaredDependency, platformId)
        val expectedDigest = asset[CodeGraphDependencyPayloadKeys.SHA256]
          ?.toString()
          ?.trim()
          ?.lowercase()
          ?: return@mapNotNull null
        val binaryPath = CodeGraphToolLayout.binaryPath(userHome, releaseTag, binaryName, platformId)
        val digestPath = CodeGraphToolLayout.assetDigestPath(userHome, releaseTag)
        binaryPath.takeIf {
          Files.isRegularFile(it) &&
            Files.isExecutable(it) &&
            Files.isRegularFile(digestPath) &&
            Files.readString(digestPath).trim().lowercase() == expectedDigest
        }
      }
      .firstOrNull()
  }

  override fun removeOwnedVersion(userHome: Path, releaseTag: String, heldReleaseTags: Set<String>): Boolean {
    if (releaseTag in heldReleaseTags) {
      throw CodeGraphInstallRefusalError("CodeGraph release '$releaseTag' is held by an active or resumable pair.")
    }
    val root = CodeGraphToolLayout.versionRoot(userHome, releaseTag)
    if (!root.startsWith(CodeGraphToolLayout.toolsRoot(userHome)) || !root.exists()) return false
    root.deleteRecursively()
    return true
  }

  private fun resolveLocalOverride(
    dependency: Map<String, Any?>,
    override: Path,
    releaseTag: String,
    platformId: String,
  ): CodeGraphInstalledTool {
    val executable = requireExecutable(override)
    val versionArgv = (dependency[CodeGraphDependencyPayloadKeys.CLI] as? Map<*, *>)
      ?.get(CodeGraphDependencyPayloadKeys.VERSION_ARGV) as? List<*>
    val command = overrideVersionCommand(executable, versionArgv)
    val process = startOverride(command)
    verifyOverrideVersion(process, releaseTag)
    return CodeGraphInstalledTool(
      releaseTag = releaseTag,
      binaryPath = executable,
      platformId = platformId,
      sha256 = sha256Hex(Files.readAllBytes(executable)),
      provenance = "local_override",
    )
  }

  private fun requireExecutable(override: Path): Path {
    val executable = override.toAbsolutePath().normalize()
    if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
      throw CodeGraphInstallRefusalError("configured CodeGraph executable is not an executable file.")
    }
    return executable
  }

  private fun overrideVersionCommand(executable: Path, versionArgv: List<*>?): List<String> = buildList {
    if (executable.fileName.toString().endsWith(".cmd", ignoreCase = true)) {
      add("cmd")
      add("/c")
    }
    add(executable.toString())
    versionArgv.orEmpty().filterIsInstance<String>().forEach(::add)
  }

  private fun startOverride(command: List<String>): Process = try {
    ProcessBuilder(command).redirectErrorStream(true).start()
  } catch (error: IOException) {
    throw CodeGraphInstallRefusalError("configured CodeGraph executable could not be started.", error)
  }

  private fun verifyOverrideVersion(process: Process, releaseTag: String) {
    if (!process.waitFor(OVERRIDE_VERSION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
      process.destroyForcibly()
      throw CodeGraphInstallRefusalError("configured CodeGraph executable did not report its version.")
    }
    val output = process.inputStream.readNBytes(OVERRIDE_OUTPUT_BYTE_CAP).decodeToString()
    if (process.exitValue() != 0 || !output.contains(releaseTag)) {
      throw CodeGraphInstallRefusalError(
        "configured CodeGraph executable did not identify the pinned release '$releaseTag'.",
      )
    }
  }

  private fun declaredReleaseTag(dependency: Map<String, Any?>): String =
    (dependency[CodeGraphDependencyPayloadKeys.UPSTREAM] as? Map<*, *>)
      ?.get(CodeGraphDependencyPayloadKeys.RELEASE_TAG)
      ?.toString()
      ?.trim()
      ?.takeIf(String::isNotBlank)
      ?: throw CodeGraphInstallRefusalError("pinned release tag is missing from the dependency declaration.")

  private fun cliBinaryName(dependency: Map<String, Any?>): String? =
    (dependency[CodeGraphDependencyPayloadKeys.CLI] as? Map<*, *>)
      ?.get(CodeGraphDependencyPayloadKeys.BINARY_NAME)
      ?.toString()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

  private fun downloadAndInstall(request: DownloadInstallRequest): CodeGraphInstalledTool {
    val downloadUrl = request.asset[CodeGraphDependencyPayloadKeys.DOWNLOAD_URL]?.toString()?.trim()
      ?: throw CodeGraphInstallRefusalError("platform asset is missing download_url.")
    assertAllowedDownloadHost(request.dependency, downloadUrl)
    val versionRoot = CodeGraphToolLayout.versionRoot(request.userHome, request.releaseTag)
    val stagingRoot = versionRoot.resolveSibling("${request.releaseTag}.staging-${System.nanoTime()}")
    if (stagingRoot.exists()) stagingRoot.deleteRecursively()
    Files.createDirectories(stagingRoot)
    val archiveName = request.asset[CodeGraphDependencyPayloadKeys.ASSET_NAME]?.toString()?.trim()
      ?: "codegraph-archive"
    val archivePath = stagingRoot.resolve(archiveName)
    val digestPath = CodeGraphToolLayout.assetDigestPath(request.userHome, request.releaseTag)
    var committed = false
    try {
      val installed = installDownloadedArchive(request, downloadUrl, archivePath, stagingRoot, versionRoot)
      committed = true
      return installed
    } finally {
      if (!committed) {
        Files.deleteIfExists(request.binaryPath)
        Files.deleteIfExists(digestPath)
      }
      if (stagingRoot.exists()) stagingRoot.deleteRecursively()
    }
  }

  private fun installDownloadedArchive(
    request: DownloadInstallRequest,
    downloadUrl: String,
    archivePath: Path,
    stagingRoot: Path,
    versionRoot: Path,
  ): CodeGraphInstalledTool {
    val bytes = transport(downloadUrl)
    val digest = sha256Hex(bytes).lowercase()
    if (digest != request.expectedDigest) {
      throw CodeGraphInstallRefusalError("downloaded asset digest mismatch; refusing to install.")
    }
    Files.write(archivePath, bytes)
    val archiveKind = request.asset[CodeGraphDependencyPayloadKeys.ARCHIVE_KIND]?.toString()?.trim().orEmpty()
    extractArchive(archivePath, archiveKind, stagingRoot)
    val extractedRoot = extractedRoot(stagingRoot, request.binaryName)
    if (versionRoot.exists()) versionRoot.deleteRecursively()
    Files.createDirectories(versionRoot.parent)
    Files.move(extractedRoot, versionRoot, StandardCopyOption.REPLACE_EXISTING)
    grantExecute(request.binaryPath, request.platformId)
    if (!Files.isExecutable(request.binaryPath)) {
      Files.deleteIfExists(request.binaryPath)
      throw CodeGraphInstallRefusalError("installed CodeGraph binary is not executable.")
    }
    Files.writeString(CodeGraphToolLayout.assetDigestPath(request.userHome, request.releaseTag), request.expectedDigest)
    return CodeGraphInstalledTool(
      request.releaseTag,
      request.binaryPath,
      request.platformId,
      request.expectedDigest,
    )
  }

  private fun extractedRoot(stagingRoot: Path, binaryName: String): Path {
    val extractedBinary = requiredExtractedBinary(stagingRoot, binaryName)
    val extractedRoot = requiredExtractionRoot(extractedBinary)
    if (!extractedRoot.startsWith(stagingRoot)) {
      throw CodeGraphInstallRefusalError("archive has an invalid CodeGraph extraction path.")
    }
    return extractedRoot
  }

  private fun requiredExtractedBinary(stagingRoot: Path, binaryName: String): Path =
    locateBinary(stagingRoot, binaryName)
      ?: throw CodeGraphInstallRefusalError("archive did not contain executable '$binaryName'.")

  private fun requiredExtractionRoot(extractedBinary: Path): Path = extractedBinary.parent?.parent
    ?: throw CodeGraphInstallRefusalError("archive has an invalid CodeGraph runtime layout.")

  private fun assertAllowedDownloadHost(dependency: Map<String, Any?>, downloadUrl: String) {
    val host = URI(downloadUrl).host?.lowercase()?.trim()
      ?: throw CodeGraphInstallRefusalError("download URL is missing a host.")
    val allowed = (dependency[CodeGraphDependencyPayloadKeys.ALLOWED_DOWNLOAD_HOSTS] as? List<*>)
      ?.mapNotNull { it?.toString()?.trim()?.lowercase() }
      ?.toSet()
      ?: emptySet()
    if (host !in allowed) {
      throw CodeGraphInstallRefusalError("download host '$host' is not allowed.")
    }
  }

  private fun platformAsset(dependency: Map<String, Any?>, platformId: String): Map<*, *> {
    val assets = dependency[CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS] as? List<*> ?: emptyList<Any?>()
    return assets.filterIsInstance<Map<*, *>>().firstOrNull { asset ->
      asset[CodeGraphDependencyPayloadKeys.PLATFORM_ID]?.toString() == platformId
    } ?: throw CodeGraphInstallRefusalError("no platform asset is declared for '$platformId'.")
  }

  private fun extractArchive(archivePath: Path, archiveKind: String, destination: Path) {
    val command = when (archiveKind) {
      "tar_gz" -> listOf("tar", "-xzf", archivePath.toString(), "-C", destination.toString())
      "zip" -> listOf("unzip", "-q", archivePath.toString(), "-d", destination.toString())
      else -> throw CodeGraphInstallRefusalError("unsupported archive kind '$archiveKind'.")
    }
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
      throw CodeGraphInstallRefusalError("failed to extract archive: $output")
    }
  }

  private fun locateBinary(root: Path, binaryName: String): Path? {
    val names = setOf(binaryName, "$binaryName.cmd", "$binaryName.exe")
    return Files.walk(root).use { paths ->
      paths.filter { Files.isRegularFile(it) && it.fileName.toString() in names }.findFirst().orElse(null)
    }
  }

  private fun grantExecute(path: Path, platformId: String) {
    if (platformId.startsWith("win32-")) return
    runCatching {
      val permissions = Files.getPosixFilePermissions(path).toMutableSet()
      permissions.add(PosixFilePermission.OWNER_EXECUTE)
      permissions.add(PosixFilePermission.GROUP_EXECUTE)
      permissions.add(PosixFilePermission.OTHERS_EXECUTE)
      Files.setPosixFilePermissions(path, permissions)
    }.getOrElse {
      throw CodeGraphInstallRefusalError("could not make the installed CodeGraph binary executable.")
    }
  }
}
