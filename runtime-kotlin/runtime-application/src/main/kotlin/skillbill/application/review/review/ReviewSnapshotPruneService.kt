package skillbill.application.review.review
import me.tatarka.inject.annotations.Inject
import skillbill.application.review.model.ReviewSnapshotPruneResult
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.review.evidence.ReviewSnapshotGateway
import skillbill.ports.review.model.ReviewSnapshot
import java.io.IOException

@Inject
class ReviewSnapshotPruneService(
  private val database: DatabaseSessionFactory,
  private val gateway: ReviewSnapshotGateway,
  private val diagnostics: RuntimeDiagnostics,
) {
  fun prune(confirmed: Boolean): ReviewSnapshotPruneResult {
    val liveDbPath = database.resolveDbPath()
    val candidates = gateway.listSnapshots(liveDbPath)
    val deleted = mutableListOf<ReviewSnapshot>()
    val failed = mutableListOf<ReviewSnapshot>()
    if (confirmed) {
      candidates.forEach { snapshot ->

        val removed =
          try {
            gateway.delete(snapshot)
          } catch (error: IOException) {
            diagnostics.warning("review snapshot prune: '${snapshot.path}' could not be deleted.", error)
            false
          }
        if (removed) deleted += snapshot else failed += snapshot
      }
    }
    return ReviewSnapshotPruneResult(
      liveDbPath = liveDbPath.toString(),
      confirmed = confirmed,
      candidates = candidates,
      deleted = deleted,
      failed = failed,
    )
  }
}
