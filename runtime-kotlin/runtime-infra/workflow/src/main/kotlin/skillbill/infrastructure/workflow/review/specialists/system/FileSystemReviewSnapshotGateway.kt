package skillbill.infrastructure.workflow.review.specialists.system
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.workflow.decomposition.entries
import skillbill.infrastructure.workflow.decomposition.entry
import skillbill.infrastructure.workflow.decomposition.parent
import skillbill.infrastructure.workflow.featuretask.parent
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.filesystem.sizeBytes
import skillbill.infrastructure.workflow.git.goal.snapshot
import skillbill.infrastructure.workflow.git.scoped.entries
import skillbill.infrastructure.workflow.git.scoped.snapshot
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.entries
import skillbill.infrastructure.workflow.git.workflow.parent
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.specialists.coordinate.entry
import skillbill.infrastructure.workflow.review.specialists.review.entry
import skillbill.ports.review.evidence.ReviewSnapshotGateway
import skillbill.ports.review.model.ReviewSnapshot
import java.nio.file.Files
import java.nio.file.Path

@Inject
class FileSystemReviewSnapshotGateway : ReviewSnapshotGateway {
  override fun listSnapshots(liveDbPath: Path): List<ReviewSnapshot> {
    val live = liveDbPath.toAbsolutePath().normalize()
    val directory = live.parent ?: return emptyList()
    if (!Files.isDirectory(directory)) return emptyList()
    val liveName = live.fileName.toString()

    val stem = liveName.removeSuffix(SUFFIX)
    val pattern = Regex("^${Regex.escape(stem)}\\.(.+)${Regex.escape(SUFFIX)}$")
    return Files.newDirectoryStream(directory).use { entries ->
      entries.mapNotNull { entry ->
        if (!Files.isRegularFile(entry)) return@mapNotNull null
        val label = pattern.find(entry.fileName.toString())?.groupValues?.get(1) ?: return@mapNotNull null
        ReviewSnapshot(
          path = entry.toAbsolutePath().normalize(),
          label = label,
          sizeBytes = Files.size(entry),
          lastModified = Files.getLastModifiedTime(entry).toInstant().toString(),
        )
      }
    }.sortedByDescending(ReviewSnapshot::lastModified)
  }

  override fun delete(snapshot: ReviewSnapshot): Boolean = Files.deleteIfExists(snapshot.path)

  private companion object {
    const val SUFFIX = ".db"
  }
}
