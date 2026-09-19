package skillbill.engine.featuretask.lifecycle.core
import me.tatarka.inject.annotations.Inject
import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeCrashReconciliationReason
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeCrashReconciliationResult
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.featuretask.model.FeatureTaskRuntimeCrashReconciliationCandidate
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.taskruntime.model.isConfirmedDead
import java.time.Clock

@Inject
class FeatureTaskRuntimeCrashReconciler(
  private val database: DatabaseSessionFactory,
  private val supervisor: FeatureTaskRuntimeWorkerSupervisor,
  private val diagnostics: RuntimeDiagnostics,
  private val clock: Clock,
) {
  fun reconcile(): FeatureTaskRuntimeCrashReconciliationResult {
    val now = clock.instant().toString()
    val candidates = runCatching {
      database.read { it.workflowStates.findFeatureTaskRuntimeCrashReconciliationCandidates(now) }
    }.getOrElse { error ->
      RuntimeDiagnosticsBestEffortWarning.record(
        diagnostics,
        "Crash-reconciliation candidate scan failed; startup is unaffected.",
        error,
      )
      return FeatureTaskRuntimeCrashReconciliationResult.NONE
    }
    if (candidates.isEmpty()) return FeatureTaskRuntimeCrashReconciliationResult.NONE
    val reasonClassCounts = mutableMapOf<String, Int>()
    var reconciledCount = 0
    candidates.forEach { candidate ->
      reconcileCandidate(candidate)?.let { reasonClass ->
        reasonClassCounts.merge(reasonClass, 1, Int::plus)

        if (reasonClass != FAULT_REASON_CLASS) reconciledCount++
      }
    }
    return FeatureTaskRuntimeCrashReconciliationResult(reconciledCount, reasonClassCounts)
  }

  private fun reconcileCandidate(candidate: FeatureTaskRuntimeCrashReconciliationCandidate): String? = runCatching {
    if (!supervisor.inspect(candidate.ownership).isConfirmedDead()) {
      return@runCatching null
    }
    val reason = interruptionReason()

    val reconciled = database.transaction {
      it.workflowStates.reconcileFeatureTaskRuntimeCrashedWorker(
        workflowId = candidate.ownership.workflowId,
        ownerToken = candidate.ownership.ownerToken,
        generation = candidate.ownership.generation,
        interruptionReason = "${reason.wireValue}: worker lease expired and process confirmed dead",
        nowInstant = clock.instant().toString(),
      )
    }
    if (reconciled) reason.wireValue else null
  }.getOrElse { error ->
    RuntimeDiagnosticsBestEffortWarning.record(
      diagnostics,
      "Crash reconciliation faulted on a candidate; the pass continues and the fault is counted.",
      error,
    )
    FAULT_REASON_CLASS
  }

  private companion object {
    const val FAULT_REASON_CLASS = "reconcile_fault"
  }

  private fun interruptionReason(): FeatureTaskRuntimeCrashReconciliationReason =
    FeatureTaskRuntimeCrashReconciliationReason.LEASE_EXPIRED
}
