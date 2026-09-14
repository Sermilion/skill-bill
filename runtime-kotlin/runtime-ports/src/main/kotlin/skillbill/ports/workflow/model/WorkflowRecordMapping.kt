package skillbill.ports.workflow.model

import skillbill.contracts.workflow.FeatureImplementSessionSummaryContract
import skillbill.contracts.workflow.FeatureVerifySessionSummaryContract
import skillbill.contracts.workflow.WorkflowContinueSessionSummary
import skillbill.workflow.engine.model.WorkflowStateSnapshot

/**
 * SKILL-48 Subtask 2a: `toSnapshot` is a pure record-to-snapshot
 * mapping helper. The canonical workflow-state schema validator runs
 * at the in-process construction sites (`WorkflowEngine.openRecord` /
 * `updateRecord`) and at every read seam (`fullPayload` /
 * `summaryPayload`), which together cover every WorkflowService
 * caller (open / update / get / list / latest / resume / continue).
 * Keeping the validator out of this mapping helper avoids running the
 * network-y schema engine on raw DB rows; the next call into
 * WorkflowEngine validates the shape downstream.
 *
 * Backward-compatibility note: this mapping is INTENTIONALLY not
 * validating. Legacy durable records that drifted from the current
 * per-skill enums (e.g. an obsolete `workflow_status` or a removed
 * `step_id` after a definition change) pass through `toSnapshot`
 * untouched and then loud-fail with `InvalidWorkflowStateSchemaError`
 * at the next `WorkflowEngine` read seam. There is no compatibility
 * shim by design — see the runtime-contract backward-compatibility
 * paragraph in `AGENTS.md` for the operator recovery story.
 */
fun WorkflowStateRecord.toSnapshot(): WorkflowStateSnapshot = WorkflowStateSnapshot(
  workflowId = workflowId,
  sessionId = sessionId,
  workflowName = workflowName,
  mode = mode?.wireValue,
  contractVersion = contractVersion,
  workflowStatus = workflowStatus,
  currentStepId = currentStepId,
  stepsJson = stepsJson,
  artifactsJson = artifactsJson,
  startedAt = startedAt,
  updatedAt = updatedAt,
  finishedAt = finishedAt,
)

fun FeatureImplementSessionSummary.toContract(): FeatureImplementSessionSummaryContract =
  FeatureImplementSessionSummaryContract(
    sessionId = sessionId,
    issueKeyProvided = issueKeyProvided,
    issueKeyType = issueKeyType,
    specInputTypes = specInputTypes,
    specWordCount = specWordCount,
    featureSize = featureSize,
    featureName = featureName,
    rolloutNeeded = rolloutNeeded,
    acceptanceCriteriaCount = acceptanceCriteriaCount,
    openQuestionsCount = openQuestionsCount,
    specSummary = specSummary,
  )

fun FeatureVerifySessionSummary.toContract(): FeatureVerifySessionSummaryContract =
  FeatureVerifySessionSummaryContract(
    sessionId = sessionId,
    acceptanceCriteriaCount = acceptanceCriteriaCount,
    rolloutRelevant = rolloutRelevant,
    specSummary = specSummary,
  )

fun FeatureVerifySessionSummary.toContinueSessionSummary(): WorkflowContinueSessionSummary = WorkflowContinueSessionSummary(
  sessionId = sessionId,
  acceptanceCriteriaCount = acceptanceCriteriaCount,
  rolloutRelevant = rolloutRelevant,
  specSummary = specSummary,
)
