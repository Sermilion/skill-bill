package skillbill.workflow.taskruntime.artifact
import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeRunInvariants
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalContinuationFieldAdoption
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalPlanningImport
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.FeatureTaskRuntimeImplementationAttempt
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.featureTaskRuntimeImplementationAttemptRecordToWire
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.featureTaskRuntimeImplementationAttemptsFromWire
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.featureTaskRuntimeRunInvariantsFromArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.toArtifactMap
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeDeliveredProjectionRecord

fun FeatureTaskRuntimeImplementationAttempt.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeImplementationAttemptFromArtifact(raw: Any?): FeatureTaskRuntimeImplementationAttempt? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeImplementationAttempt::fromArtifactMap)

fun decodeImplementationAttemptsFromArtifact(raw: Any?): List<FeatureTaskRuntimeImplementationAttempt> =
  featureTaskRuntimeImplementationAttemptsFromWire(raw)

fun Any.toWorkflowArtifactMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(this)

fun implementationAttemptRecordWorkflowArtifact(attempts: List<FeatureTaskRuntimeImplementationAttempt>): Any =
  featureTaskRuntimeImplementationAttemptRecordToWire(attempts)

fun FeatureTaskRuntimeRunInvariants.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeRunInvariantsFromArtifact(raw: Any?): FeatureTaskRuntimeRunInvariants? =
  JsonCodec.anyToStringAnyMap(raw)?.let { featureTaskRuntimeRunInvariantsFromArtifactMap(it) }

fun FeatureTaskRuntimeResolvedBranch.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeResolvedBranchFromArtifact(raw: Any?): FeatureTaskRuntimeResolvedBranch? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeResolvedBranch::fromArtifactMap)

fun FeatureTaskRuntimeDecomposeTerminal.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeDecomposeTerminalFromArtifact(raw: Any?): FeatureTaskRuntimeDecomposeTerminal? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeDecomposeTerminal::fromArtifactMap)

fun FeatureTaskRuntimeGoalContinuationArtifact.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeGoalContinuationArtifactFromArtifact(raw: Any?): FeatureTaskRuntimeGoalContinuationArtifact? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeGoalContinuationArtifact::fromArtifactMap)

fun FeatureTaskRuntimeGoalContinuationFieldAdoption.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun FeatureTaskRuntimeGoalPlanningImport.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeGoalContinuationFieldAdoptionFromArtifact(raw: Any?): FeatureTaskRuntimeGoalContinuationFieldAdoption? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeGoalContinuationFieldAdoption::fromArtifactMap)

fun FeatureTaskRuntimeDeliveredProjectionRecord.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeDeliveredProjectionRecordFromArtifact(raw: Any?): FeatureTaskRuntimeDeliveredProjectionRecord? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeDeliveredProjectionRecord::fromArtifactMap)
