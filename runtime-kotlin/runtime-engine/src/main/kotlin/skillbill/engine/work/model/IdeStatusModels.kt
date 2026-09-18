package skillbill.engine.work.model

import skillbill.workflow.model.FeatureTaskRouteScope
import java.time.Instant
import skillbill.ports.idestatus.model.IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH as PORT_IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH
import skillbill.ports.idestatus.model.IdeStatusCurrentModel as PortIdeStatusCurrentModel
import skillbill.ports.idestatus.model.IdeStatusCurrentPhaseExecution as PortIdeStatusCurrentPhaseExecution
import skillbill.ports.idestatus.model.IdeStatusCurrentPhaseExecutionKind as PortIdeStatusCurrentPhaseExecutionKind
import skillbill.ports.idestatus.model.IdeStatusCurrentSubtask as PortIdeStatusCurrentSubtask
import skillbill.ports.idestatus.model.IdeStatusFreshness as PortIdeStatusFreshness
import skillbill.ports.idestatus.model.IdeStatusLifecycleState as PortIdeStatusLifecycleState
import skillbill.ports.idestatus.model.IdeStatusPauseReason as PortIdeStatusPauseReason
import skillbill.ports.idestatus.model.IdeStatusPauseReasonCode as PortIdeStatusPauseReasonCode
import skillbill.ports.idestatus.model.IdeStatusPlanning as PortIdeStatusPlanning
import skillbill.ports.idestatus.model.IdeStatusProblem as PortIdeStatusProblem
import skillbill.ports.idestatus.model.IdeStatusProblemCode as PortIdeStatusProblemCode
import skillbill.ports.idestatus.model.IdeStatusProgress as PortIdeStatusProgress
import skillbill.ports.idestatus.model.IdeStatusRepositoryResolution as PortIdeStatusRepositoryResolution
import skillbill.ports.idestatus.model.IdeStatusRequest as PortIdeStatusRequest
import skillbill.ports.idestatus.model.IdeStatusResult as PortIdeStatusResult
import skillbill.ports.idestatus.model.IdeStatusSelectionTier as PortIdeStatusSelectionTier
import skillbill.ports.idestatus.model.IdeStatusSnapshot as PortIdeStatusSnapshot
import skillbill.ports.idestatus.model.IdeStatusStep as PortIdeStatusStep
import skillbill.ports.idestatus.model.IdeStatusWorkflowFamily as PortIdeStatusWorkflowFamily

typealias IdeStatusWorkflowFamily = PortIdeStatusWorkflowFamily
typealias IdeStatusLifecycleState = PortIdeStatusLifecycleState
typealias IdeStatusFreshness = PortIdeStatusFreshness
typealias IdeStatusPauseReasonCode = PortIdeStatusPauseReasonCode
typealias IdeStatusPauseReason = PortIdeStatusPauseReason
typealias IdeStatusProblemCode = PortIdeStatusProblemCode
typealias IdeStatusSelectionTier = PortIdeStatusSelectionTier
typealias IdeStatusStep = PortIdeStatusStep
typealias IdeStatusProgress = PortIdeStatusProgress
typealias IdeStatusCurrentSubtask = PortIdeStatusCurrentSubtask
typealias IdeStatusCurrentModel = PortIdeStatusCurrentModel
typealias IdeStatusPlanning = PortIdeStatusPlanning
typealias IdeStatusCurrentPhaseExecutionKind = PortIdeStatusCurrentPhaseExecutionKind
typealias IdeStatusCurrentPhaseExecution = PortIdeStatusCurrentPhaseExecution
typealias IdeStatusProblem = PortIdeStatusProblem
typealias IdeStatusRepositoryResolution = PortIdeStatusRepositoryResolution
typealias IdeStatusSnapshot = PortIdeStatusSnapshot
typealias IdeStatusRequest = PortIdeStatusRequest
typealias IdeStatusResult = PortIdeStatusResult

const val IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH: Int =
  PORT_IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH

data class IdeStatusCandidate(
  val workflowId: String,
  val workflowFamily: IdeStatusWorkflowFamily,
  val issueKey: String?,
  val currentState: String,
  val lifecycleState: IdeStatusLifecycleState,
  val selectionTier: IdeStatusSelectionTier,
  val updatedAt: Instant,
  val startedAt: Instant?,
  val routeScope: FeatureTaskRouteScope? = null,
  val isGoalAuthoritative: Boolean = workflowFamily == IdeStatusWorkflowFamily.FEATURE_GOAL,
)
