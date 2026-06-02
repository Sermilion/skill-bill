package skillbill.ports.persistence

import skillbill.ports.persistence.model.FeatureImplementSessionSummary
import skillbill.ports.persistence.model.FeatureVerifySessionSummary
import skillbill.ports.persistence.model.WorkflowStateRecord

/**
 * Durable workflow-state persistence, composed from one capability interface
 * per workflow family. The composition keeps each family's method surface
 * cohesive and small enough that no single interface crosses the detekt
 * `TooManyFunctions` threshold, while [WorkflowStateRepository] remains the
 * single port adapters implement and callers depend on.
 *
 * No infrastructure types leak through this surface: every method speaks only
 * the port-owned [WorkflowStateRecord] / session-summary models.
 */
interface WorkflowStateRepository :
  FeatureImplementWorkflowStateRepository,
  FeatureVerifyWorkflowStateRepository,
  FeatureTaskRuntimeWorkflowStateRepository

interface FeatureImplementWorkflowStateRepository {
  fun saveFeatureImplementWorkflow(row: WorkflowStateRecord)

  fun getFeatureImplementWorkflow(workflowId: String): WorkflowStateRecord?

  fun listFeatureImplementWorkflows(limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureImplementWorkflow(): WorkflowStateRecord?

  fun getFeatureImplementSessionSummary(sessionId: String): FeatureImplementSessionSummary?
}

interface FeatureVerifyWorkflowStateRepository {
  fun saveFeatureVerifyWorkflow(row: WorkflowStateRecord)

  fun getFeatureVerifyWorkflow(workflowId: String): WorkflowStateRecord?

  fun listFeatureVerifyWorkflows(limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureVerifyWorkflow(): WorkflowStateRecord?

  fun getFeatureVerifySessionSummary(sessionId: String): FeatureVerifySessionSummary?
}

/**
 * SKILL-65 Subtask 2 (AC1, AC2): family-specific persistence for the
 * experimental feature-task-runtime pipeline. Reuses the existing
 * [WorkflowStateRecord] port model (which carries `stepsJson`/`artifactsJson`),
 * so per-phase records and the append-only phase ledger ride inside the same
 * durable artifacts envelope as the other families. There is intentionally no
 * session-summary method for this family.
 */
interface FeatureTaskRuntimeWorkflowStateRepository {
  fun saveFeatureTaskRuntimeWorkflow(row: WorkflowStateRecord)

  fun getFeatureTaskRuntimeWorkflow(workflowId: String): WorkflowStateRecord?

  fun listFeatureTaskRuntimeWorkflows(limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureTaskRuntimeWorkflow(): WorkflowStateRecord?
}
