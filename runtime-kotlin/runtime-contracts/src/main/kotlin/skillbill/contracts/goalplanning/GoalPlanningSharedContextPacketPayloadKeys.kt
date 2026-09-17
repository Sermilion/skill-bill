package skillbill.contracts.goalplanning

import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys

object GoalPlanningSharedContextPacketPayloadKeys {
  const val PACKET_VERSION: String = "packet_version"
  const val REPOSITORY_IDENTITY: String = "repository_identity"
  const val NORMALIZED_ISSUE_KEY: String = "normalized_issue_key"
  val PARENT_SPEC_PATH: String = DecompositionPlanningPayloadKeys.PARENT_SPEC_PATH
  const val PARENT_SPEC: String = "parent_spec"
  const val DECOMPOSITION_MANIFEST: String = "decomposition_manifest"
  const val BOUNDARY_MEMORY: String = "boundary_memory"
  const val CATALOG: String = "catalog"
  const val TRUNCATED: String = "truncated"
  const val HEADING_ID: String = "heading_id"
  const val SOURCE_PATH: String = "source_path"
  const val KIND: String = "kind"
  const val HEADING: String = "heading"
  const val VALIDATION_GUIDANCE: String = "validation_guidance"
  const val ORDERED_SUBTASKS: String = "ordered_subtasks"
  const val PLANNING_DISPOSITION: String = "planning_disposition"
  const val INTEGRITY_SHA256: String = "integrity_sha256"
  const val PLATFORM_PACKS: String = "platform_packs"
}
