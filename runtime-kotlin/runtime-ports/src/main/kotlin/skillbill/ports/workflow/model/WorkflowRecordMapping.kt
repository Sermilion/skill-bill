package skillbill.ports.workflow.model

import skillbill.contracts.workflow.FeatureImplementSessionSummaryContract
import skillbill.contracts.workflow.FeatureVerifySessionSummaryContract
import skillbill.contracts.workflow.WorkflowContinueSessionSummary
import skillbill.workflow.engine.model.WorkflowStateSnapshot

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

fun FeatureVerifySessionSummary.toContract(): FeatureVerifySessionSummaryContract = FeatureVerifySessionSummaryContract(
  sessionId = sessionId,
  acceptanceCriteriaCount = acceptanceCriteriaCount,
  rolloutRelevant = rolloutRelevant,
  specSummary = specSummary,
)

fun FeatureVerifySessionSummary.toContinueSessionSummary(): WorkflowContinueSessionSummary =
  WorkflowContinueSessionSummary(
    sessionId = sessionId,
    acceptanceCriteriaCount = acceptanceCriteriaCount,
    rolloutRelevant = rolloutRelevant,
    specSummary = specSummary,
  )
