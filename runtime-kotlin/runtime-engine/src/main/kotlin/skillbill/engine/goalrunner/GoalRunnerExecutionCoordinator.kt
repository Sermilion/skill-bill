package skillbill.engine.goalrunner

import me.tatarka.inject.annotations.Inject
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_RUNNER_INTERRUPTED
import skillbill.goalrunner.model.GoalRunnerExecutionLease
import skillbill.ports.featuretask.model.FeatureTaskRuntimeWorkerLeaseState
import skillbill.ports.featuretask.model.FeatureTaskRuntimeWorkerOwnership
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.process.DaemonThreadPort
import skillbill.ports.process.IdentifierGeneratorPort
import skillbill.ports.process.ShutdownHookPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeHeartbeatPlan
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeHeartbeatTick
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeProcessIdentity
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeProcessInspection
import java.time.Clock
import java.time.Duration
import java.time.Instant

interface GoalRunnerExecutionCoordinator {
  fun <T> runOwned(parentWorkflowId: String, block: () -> T): T

  companion object {
    val NONE: GoalRunnerExecutionCoordinator = object : GoalRunnerExecutionCoordinator {
      override fun <T> runOwned(parentWorkflowId: String, block: () -> T): T = block()
    }
  }
}

class GoalRunnerExecutionAlreadyRunningException(parentWorkflowId: String, detail: String) : IllegalStateException(
  "Goal parent '$parentWorkflowId' cannot start: $detail",
)

fun GoalRunnerExecutionLease.asWorkerOwnership(parentWorkflowId: String) = FeatureTaskRuntimeWorkerOwnership(
  workflowId = parentWorkflowId,
  generation = generation,
  ownerToken = ownerToken,
  hostIdentity = hostIdentity,
  bootIdentity = bootIdentity,
  pid = pid,
  processBirthToken = processBirthToken,
  leaseState = FeatureTaskRuntimeWorkerLeaseState.ACTIVE,
  heartbeatAt = heartbeatAt,
  expiresAt = expiresAt,
  phaseId = "goal_runner",
  phaseAttempt = 1,
)

@Inject
class DefaultGoalRunnerExecutionCoordinator(
  private val manifestStore: GoalRunnerManifestStore,
  private val supervisor: FeatureTaskRuntimeWorkerSupervisor,
  private val clock: Clock,
  private val shutdownHookPort: ShutdownHookPort,
  private val daemonThreadPort: DaemonThreadPort,
  private val identifierGeneratorPort: IdentifierGeneratorPort,
) : GoalRunnerExecutionCoordinator {
  override fun <T> runOwned(parentWorkflowId: String, block: () -> T): T {
    val existing = manifestStore.executionLease(parentWorkflowId)
    val expectedOwnerToken = existing?.let { reclaimableOwnerToken(parentWorkflowId, it) }
    val lease = newLease(existing, supervisor.currentProcess())
    if (!manifestStore.acquireExecutionLease(parentWorkflowId, lease, expectedOwnerToken)) {
      throw GoalRunnerExecutionAlreadyRunningException(
        parentWorkflowId,
        "another goal runner claimed the execution lease before this run could start",
      )
    }
    if (existing != null && leaseIsExpired(existing)) {
      clearStalePauseOrReleaseLease(parentWorkflowId, lease)
    }
    val plan = FeatureTaskRuntimeHeartbeatPlan(
      label = parentWorkflowId,
      intervalSeconds = HEARTBEAT_SECONDS,
      leaseSeconds = LEASE_DURATION.seconds,
    )
    val heartbeat = supervisor.startHeartbeat(plan) {
      val now = clock.instant()
      val updated = lease.copy(heartbeatAt = now.toString(), expiresAt = now.plus(LEASE_DURATION).toString())
      if (manifestStore.heartbeatExecutionLease(parentWorkflowId, updated)) {
        FeatureTaskRuntimeHeartbeatTick.Renewed
      } else {
        FeatureTaskRuntimeHeartbeatTick.FencingLost(
          "goal parent '$parentWorkflowId' execution lease fencing was lost",
        )
      }
    }
    val shutdownHookRegistration = shutdownHookPort.register {
      recordInterruption(parentWorkflowId)
    }
    val result = try {
      block()
    } finally {
      shutdownHookRegistration.unregister()
      heartbeat.stop()
      manifestStore.releaseExecutionLease(
        parentWorkflowId,
        lease.ownerToken,
        lease.generation,
      )
    }
    heartbeat.fencingLostReason()?.let { reason ->
      throw GoalRunnerExecutionAlreadyRunningException(parentWorkflowId, reason)
    }
    return result
  }

  private fun clearStalePauseOrReleaseLease(parentWorkflowId: String, lease: GoalRunnerExecutionLease) {
    val failure = runCatching {
      clearStaleRunnerInterruptedPause(parentWorkflowId)
    }.exceptionOrNull()
    if (failure != null) {
      runCatching {
        manifestStore.releaseExecutionLease(
          parentWorkflowId,
          lease.ownerToken,
          lease.generation,
        )
      }.onFailure { failure.addSuppressed(it) }
      throw failure
    }
  }

  fun recordInterruption(parentWorkflowId: String) {
    daemonThreadPort.runWithJoinBudget(
      action = {
        runCatching {
          manifestStore.pauseNow(
            parentWorkflowId = parentWorkflowId,
            reason = GOAL_PAUSE_REASON_RUNNER_INTERRUPTED,
            pausedAt = clock.instant().toString(),
            overwriteExistingReason = false,
          )
        }
      },
      joinBudgetMillis = SHUTDOWN_WRITE_BUDGET.toMillis(),
    )
  }

  private fun leaseIsExpired(lease: GoalRunnerExecutionLease): Boolean =
    !Instant.parse(lease.expiresAt).isAfter(clock.instant())

  private fun clearStaleRunnerInterruptedPause(parentWorkflowId: String) {
    manifestStore.clearRunnerInterruptedPause(parentWorkflowId)
  }

  private fun reclaimableOwnerToken(parentWorkflowId: String, existing: GoalRunnerExecutionLease): String {
    if (leaseIsExpired(existing)) return existing.ownerToken
    val ownership = existing.asWorkerOwnership(parentWorkflowId)
    return when (supervisor.inspect(ownership)) {
      FeatureTaskRuntimeProcessInspection.NotRunning -> existing.ownerToken
      FeatureTaskRuntimeProcessInspection.ExactLive ->
        reclaimAfterLiveOwner(parentWorkflowId, existing, ownership)
      is FeatureTaskRuntimeProcessInspection.OwnershipMismatch ->
        cannotStart(parentWorkflowId, "the existing process owner is ambiguous")
      is FeatureTaskRuntimeProcessInspection.Unsupported ->
        cannotStart(parentWorkflowId, "the existing process owner cannot be inspected")
    }
  }

  private fun reclaimAfterLiveOwner(
    parentWorkflowId: String,
    existing: GoalRunnerExecutionLease,
    ownership: FeatureTaskRuntimeWorkerOwnership,
  ): String {
    if (isCurrentProcess(existing)) {
      cannotStart(parentWorkflowId, "this process already owns the execution lease")
    }
    if (!isDuplicateLaunchRace(existing)) {
      cannotStart(parentWorkflowId, "another goal runner process is live")
    }
    supervisor.awaitExit(ownership, DUPLICATE_LAUNCH_WINDOW)
    return when (supervisor.inspect(ownership)) {
      FeatureTaskRuntimeProcessInspection.NotRunning -> existing.ownerToken
      FeatureTaskRuntimeProcessInspection.ExactLive ->
        cannotStart(parentWorkflowId, "another goal runner process is live")
      is FeatureTaskRuntimeProcessInspection.OwnershipMismatch ->
        cannotStart(parentWorkflowId, "the existing process owner is ambiguous")
      is FeatureTaskRuntimeProcessInspection.Unsupported ->
        cannotStart(parentWorkflowId, "the existing process owner cannot be inspected")
    }
  }

  private fun isCurrentProcess(existing: GoalRunnerExecutionLease): Boolean {
    val current = supervisor.currentProcess()
    return existing.pid == current.pid && existing.processBirthToken == current.processBirthToken
  }

  private fun isDuplicateLaunchRace(existing: GoalRunnerExecutionLease): Boolean {
    val ownerStartMs = existing.processBirthToken.toLongOrNull() ?: return false
    val ageMs = clock.millis() - ownerStartMs
    return ageMs in 0 until DUPLICATE_LAUNCH_WINDOW.toMillis()
  }

  private fun cannotStart(parentWorkflowId: String, detail: String): Nothing =
    throw GoalRunnerExecutionAlreadyRunningException(parentWorkflowId, detail)

  private fun newLease(
    existing: GoalRunnerExecutionLease?,
    process: FeatureTaskRuntimeProcessIdentity,
  ): GoalRunnerExecutionLease {
    val now = clock.instant()
    return GoalRunnerExecutionLease(
      generation = (existing?.generation ?: 0) + 1,
      ownerToken = identifierGeneratorPort.randomToken(),
      hostIdentity = process.hostIdentity,
      bootIdentity = process.bootIdentity,
      pid = process.pid,
      processBirthToken = process.processBirthToken,
      heartbeatAt = now.toString(),
      expiresAt = now.plus(LEASE_DURATION).toString(),
    )
  }

  private companion object {
    val LEASE_DURATION: Duration = Duration.ofSeconds(30)
    val DUPLICATE_LAUNCH_WINDOW: Duration = Duration.ofSeconds(60)
    const val HEARTBEAT_SECONDS: Long = 10

    val SHUTDOWN_WRITE_BUDGET: Duration = Duration.ofSeconds(2)
  }
}
