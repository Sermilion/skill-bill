package skillbill.contracts.workflow.identity.evidence

object ReadinessEvidencePayloadKeys {
  const val READINESS_EVIDENCE: String = "readiness_evidence"
  const val CONTRACT_VERSION: String = "contract_version"
  const val SOURCE_TREE_SHA: String = "source_tree_sha"
  const val BASE_REF_SHA: String = "base_ref_sha"
  const val HEAD_SHA: String = "head_sha"
  const val SELECTED_CHECKS: String = "selected_checks"
  const val CHECK_RESULTS: String = "check_results"
  const val CHECK_ID: String = "check_id"
  const val COMMAND: String = "command"
  const val EXIT_CODE: String = "exit_code"
  const val STATUS: String = "status"
}
