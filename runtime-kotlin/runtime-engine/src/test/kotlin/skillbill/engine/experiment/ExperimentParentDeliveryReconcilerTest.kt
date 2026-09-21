package skillbill.engine.experiment

import skillbill.engine.goalrunner.experiment.ExperimentParentDeliveryReconciler
import skillbill.ports.experiment.publication.ExperimentDeferredPublicationPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryRequest
import skillbill.ports.experiment.publication.ExperimentPublicationAttempt
import skillbill.ports.experiment.publication.ExperimentPublicationGateway
import skillbill.ports.experiment.publication.ExperimentPublicationResult
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentParentDeliveryReconcilerTest {
  @Test
  fun `only completed control work is published and repeated reconciliation is idempotent at gateway`() {
    val attempts = mutableListOf<ExperimentPublicationAttempt>()
    val published = mutableSetOf<String>()
    val reconciler = ExperimentParentDeliveryReconciler(
      deferredPublication = object : ExperimentDeferredPublicationPort {
        override fun recordDeferredAttempt(attempt: ExperimentPublicationAttempt) {
          attempts += attempt
        }

        override fun parentMayPublish(pairId: String): Boolean = true

        override fun publicationRecorded(pairId: String, commitSha: String): Boolean =
          published.contains("$pairId:$commitSha")
      },
      publicationGateway = ExperimentPublicationGateway { attempt ->
        if (published.add("${attempt.pairId}:${attempt.commitSha}")) {
          ExperimentPublicationResult(published = true)
        } else {
          ExperimentPublicationResult(published = true, alreadyPublished = true)
        }
      },
    )

    val request = ExperimentParentDeliveryRequest("SKILL-366", Path.of("."))
    val blocked = reconciler.reconcile("pair", "control-wf", "sha", controlCompleted = false, request = request)
    val first = reconciler.reconcile("pair", "control-wf", "sha", controlCompleted = true, request = request)
    val retry = reconciler.reconcile("pair", "control-wf", "sha", controlCompleted = true, request = request)

    assertEquals(false, blocked.published)
    assertTrue(first.published)
    assertTrue(retry.alreadyPublished)
    assertEquals(1, attempts.size)
  }
}
