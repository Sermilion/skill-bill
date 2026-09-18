package skillbill.infrastructure.fs.launcher.process

import skillbill.idestatus.model.AgentActivityLabel
import skillbill.ports.agentrun.model.AgentRunActivityStampSink
import skillbill.ports.agentrun.model.AgentRunOutputStream

internal fun ProcessWaitLoop.pollWorkflowProgress(nowNanos: Long) {
  val read = request.progressProbe.readProgressToken(degradation)
  if (read.failed) return
  val progressToken = read.value
  if (progressToken == null && lastProgressToken != null) {
    lastProgressToken = null
    degradation.recordProbeAbsence("progress_token")
    return
  }
  if (progressToken != lastProgressToken) {
    lastProgressToken = progressToken
    lastWorkflowProgressNanos = nowNanos
    lastProgressInstant = clock.instant()
    fileActivityWindowStartNanos = null
    writeProgressLabel()
    request.activityStampSink.safeStamp(AgentActivityLabel.DURABLE_PROGRESS)
  }
}

internal fun ProcessWaitLoop.pollOutputActivity(nowNanos: Long) {
  val observedMillis = outputTracker.lastObservedAt()?.toEpochMilli() ?: return
  if (observedMillis != lastObservedOutputMillis) {
    lastObservedOutputMillis = observedMillis
    lastOutputNanos = nowNanos
    request.activityStampSink.safeStamp(AgentActivityLabel.STDOUT)
  }
}

internal fun ProcessWaitLoop.pollFileActivity(nowNanos: Long) {
  val read = request.activityProbe.readActivityToken(degradation)
  if (read.failed) return
  val activityToken = read.value
  if (activityToken == null && lastActivityToken != null) {
    lastActivityToken = null
    degradation.recordProbeAbsence("activity_token")
    return
  }
  if (activityToken != lastActivityToken) {
    lastActivityToken = activityToken
    lastActivityInstant = clock.instant()
    if (fileActivityWindowStartNanos == null) {
      fileActivityWindowStartNanos = nowNanos
    }
    writeActivityLabel()
    request.activityStampSink.safeStamp(AgentActivityLabel.WORKTREE_WRITE)
  }
}

internal fun ProcessWaitLoop.pollStatusHeartbeat(nowNanos: Long) {
  val alive = process.isAlive
  if (alive) lastLiveHeartbeatNanos = nowNanos
  if (nowNanos - lastStatusHeartbeatNanos < statusHeartbeatNanos) return
  lastStatusHeartbeatNanos = nowNanos
  lifecycleEmitter.emitHeartbeat(alive)
  request.progressProbe.readProgressLabel(degradation).value?.takeIf(String::isNotBlank)?.let { label ->
    lastProgressLabel = label
  }
  val workflowLabel = lastProgressLabel?.takeIf(String::isNotBlank)
  val activityLabel = lastActivityLabel?.takeIf(String::isNotBlank)
  val details = listOfNotNull(
    workflowLabel?.let { "workflow: $it" },
    activityLabel?.let { "file activity: $it" },
  ).joinToString("; ")
    .takeIf(String::isNotBlank)
    ?.let { "; $it" }
    .orEmpty()
  request.outputSink.write(
    AgentRunOutputStream.STDERR,
    "skill-bill: status heartbeat (${request.statusHeartbeatInterval}): child run still active$details\n",
  )
}

internal fun ProcessWaitLoop.writeProgressLabel() {
  request.progressProbe.readProgressLabel(degradation).value
    ?.takeIf(String::isNotBlank)
    ?.let { label ->
      lastProgressLabel = label
      lastSnapshotInstant = clock.instant()
      request.outputSink.write(AgentRunOutputStream.STDERR, "skill-bill: workflow progress: $label\n")
    }
}

internal fun ProcessWaitLoop.writeActivityLabel() {
  val activityLabel = request.activityProbe.readActivityLabel(degradation).value?.takeIf(String::isNotBlank)
  if (activityLabel != null) {
    lastActivityLabel = activityLabel
  }
  val workflowLabel = request.progressProbe.readProgressLabel(degradation).value?.takeIf(String::isNotBlank)
  val suffix = listOfNotNull(activityLabel, workflowLabel).joinToString("; ")
    .takeIf(String::isNotBlank)
    ?.let { label -> ": $label" }
    .orEmpty()
  request.outputSink.write(
    AgentRunOutputStream.STDERR,
    "skill-bill: file activity observed; durable workflow progress is still pending$suffix\n",
  )
}

internal fun AgentRunActivityStampSink.safeStamp(label: AgentActivityLabel) {
  runCatching { stamp(label) }
}
