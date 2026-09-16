package skillbill.infrastructure.fs

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestBundleJournalPayloadKeys
import skillbill.error.InvalidDecompositionManifestSchemaError
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

internal object DecompositionManifestBundleJournalRecovery {
  fun recoverPending(parent: Path?, journal: DecompositionManifestBundleJournal) {
    withDecompositionManifestBundleLock(parent) { journal.recoverPendingUnlocked(parent) }
  }

  fun recoverPendingUnlocked(parent: Path?, journal: DecompositionManifestBundleJournal) {
    if (parent == null || !Files.isDirectory(parent)) return
    val markerGlob =
      "${DecompositionManifestBundleJournal.BUNDLE_PREFIX}*" +
        "${DecompositionManifestBundleJournal.MARKER_SUFFIX}"
    Files.newDirectoryStream(parent, markerGlob)
      .use { markers ->
        markers.toList().forEach { marker ->
          val transaction = journal.read(marker)
          journal.apply(transaction)
          journal.cleanup(transaction)
        }
      }
  }

  fun failIfPending(parent: Path?) {
    if (parent == null || !Files.isDirectory(parent)) return
    val markerGlob =
      "${DecompositionManifestBundleJournal.BUNDLE_PREFIX}*" +
        "${DecompositionManifestBundleJournal.MARKER_SUFFIX}"
    Files.newDirectoryStream(parent, markerGlob)
      .use { markers ->
        if (markers.iterator().hasNext()) {
          throw InvalidDecompositionManifestSchemaError(
            sourceLabel = parent.toString(),
            reason = "decomposition manifest bundle has an incomplete journal; launch recovery is required.",
            failureCode = "incomplete_bundle",
          )
        }
      }
  }

  fun failIfPendingUnder(root: Path) {
    Files.walk(root).use { paths ->
      paths
        .filter(Files::isDirectory)
        .forEach(::failIfPending)
    }
  }
}

internal object DecompositionManifestBundleJournalCreate {
  fun create(
    parent: Path,
    writes: List<Pair<Path, String>>,
    yamlMapper: YAMLMapper,
    journal: DecompositionManifestBundleJournal,
  ): DecompositionManifestBundleTransaction {
    val transactionId = UUID.randomUUID().toString()
    val stagingDirectory = parent.resolve(
      "${DecompositionManifestBundleJournal.BUNDLE_PREFIX}$transactionId" +
        DecompositionManifestBundleJournal.STAGING_SUFFIX,
    )
    Files.createDirectories(stagingDirectory)
    val entries = writes.mapIndexed { index, (path, content) ->
      val target = path.toAbsolutePath().normalize()
      val staged = stagingDirectory.resolve("entry-$index")
      journal.writeAtomically(staged, content)
      DecompositionManifestBundleEntry(target, staged, DecompositionManifestBundleJournalIo.sha256(content))
    }
    val marker = parent.resolve(
      "${DecompositionManifestBundleJournal.BUNDLE_PREFIX}$transactionId" +
        DecompositionManifestBundleJournal.MARKER_SUFFIX,
    )
    journal.writeAtomically(
      marker,
      yamlMapper.writeValueAsString(
        mapOf(
          SharedPayloadKeys.CONTRACT_VERSION to DecompositionManifestBundleJournal.BUNDLE_CONTRACT_VERSION,
          DecompositionManifestBundleJournalPayloadKeys.STAGING_DIRECTORY to stagingDirectory.toString(),
          DecompositionManifestBundleJournalPayloadKeys.ENTRIES to entries.map { entry ->
            mapOf(
              DecompositionManifestBundleJournalPayloadKeys.TARGET to entry.target.toString(),
              DecompositionManifestBundleJournalPayloadKeys.STAGED to entry.staged.toString(),
              DecompositionManifestBundleJournalPayloadKeys.SHA256 to entry.sha256,
            )
          },
        ),
      ),
    )
    return DecompositionManifestBundleTransaction(marker, stagingDirectory, entries)
  }
}

internal object DecompositionManifestBundleJournalIo {
  fun apply(transaction: DecompositionManifestBundleTransaction) {
    DecompositionManifestBundleJournalValidation.validateTransaction(transaction)
    transaction.entries.forEach { entry ->
      when {
        Files.isRegularFile(entry.staged) -> moveAtomically(entry.staged, entry.target)
        Files.isRegularFile(entry.target) -> Unit
        else -> error("Decomposition manifest bundle journal is incomplete for '${entry.target}'.")
      }
    }
  }

  fun cleanup(transaction: DecompositionManifestBundleTransaction) {
    DecompositionManifestBundleJournalValidation.validateTransaction(transaction)
    cleanupValidated(transaction)
  }

  fun cleanupValidated(transaction: DecompositionManifestBundleTransaction) {
    Files.deleteIfExists(transaction.marker)
    deleteRecursively(transaction.stagingDirectory)
  }

  fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

  fun read(marker: Path): DecompositionManifestBundleTransaction =
    DecompositionManifestBundleJournalValidation.readValidated(marker)

  internal fun moveAtomically(source: Path, target: Path) {
    Files.createDirectories(requireNotNull(target.parent))
    try {
      Files.move(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }
}
