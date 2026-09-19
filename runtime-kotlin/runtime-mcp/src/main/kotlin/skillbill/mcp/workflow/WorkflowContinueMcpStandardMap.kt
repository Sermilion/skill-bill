package skillbill.mcp.workflow

import skillbill.application.workflow.persist.WorkflowWireProjections
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.workflow.WorkflowWirePayloadKeys
import skillbill.workflow.engine.model.WorkflowContinueView
import skillbill.workflow.model.WorkflowContinueStatus
internal fun standardMcpContinueMap(
  view: WorkflowContinueView,
  dbPath: String,
  decompositionExtras: Map<String, Any?>,
): Map<String, Any?> {
  val map = LinkedHashMap(WorkflowWireProjections.compactContinueMap(view.compact).toPayload())
  map[WorkflowWirePayloadKeys.SESSION_SUMMARY] = view.sessionSummary.toPayload()
  map["read_only_full_state_command"] =
    readOnlyFullStateCommand(dbPath, view.resume.snapshot.workflowId, view.skillName)
  decompositionExtras.forEach { (key, value) -> map[key] = value }
  map["db_path"] = dbPath
  if (view.continueStatus == WorkflowContinueStatus.BLOCKED) {
    val missingArtifacts = view.resume.missingArtifacts
    map[SharedPayloadKeys.STATUS] = "error"
    map["error"] =
      "Cannot continue workflow until the missing artifacts are restored: " +
      missingArtifacts.joinToString()
  } else {
    map[SharedPayloadKeys.STATUS] = "ok"
  }
  return map
}
