package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDeliveredProjectionRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationFieldAdoption
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalPlanningImport
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeImplementationAttempt
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import skillbill.workflow.taskruntime.model.featureTaskRuntimeImplementationAttemptRecordToWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeImplementationAttemptsFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeRunInvariantsFromArtifactMap
import skillbill.workflow.taskruntime.model.toArtifactMap

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
