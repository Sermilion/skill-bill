package skillbill.engine.featuretask.lifecycle.core

import skillbill.application.telemetry.model.FeatureTaskRuntimeAgentContext
import skillbill.engine.featuretask.runner.FeatureTaskRuntimeRunner
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord

fun featureTaskRuntimeAgentContext(
  records: Map<String, FeatureTaskRuntimePhaseRecord>?,
): FeatureTaskRuntimeAgentContext {
  val phaseRecords = records ?: return FeatureTaskRuntimeAgentContext()
  return FeatureTaskRuntimeAgentContext(
    resolvedAgentIds = phaseRecords.values.distinctNames { it.resolvedAgentId },
    launchedModels = phaseRecords.values.distinctNames { it.launchedModel },
  )
}

private fun Collection<FeatureTaskRuntimePhaseRecord>.distinctNames(
  select: (FeatureTaskRuntimePhaseRecord) -> String?,
): List<String>? =
  mapNotNull { select(it)?.takeIf(String::isNotBlank) }
    .distinct()
    .sorted()
    .takeIf { it.isNotEmpty() }

fun FeatureTaskRuntimeRunner.featureTaskRuntimeAgentContext(workflowId: String): FeatureTaskRuntimeAgentContext =
  featureTaskRuntimeAgentContext(recorder.loadPhaseRecords(workflowId))
