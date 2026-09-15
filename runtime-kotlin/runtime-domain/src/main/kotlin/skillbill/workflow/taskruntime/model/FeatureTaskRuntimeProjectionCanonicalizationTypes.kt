package skillbill.workflow.taskruntime.model

internal data class FeatureTaskRuntimeProjectionCanonicalization(
  val canonical: Map<String, Any?>,
  val diagnostics: List<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
)

internal data class FeatureTaskRuntimeProjectionCanonicalizationRecord(
  val fieldPath: String,
  val transforms: List<FeatureTaskRuntimeProjectionCanonicalizationTransform>,
  val originalId: String? = null,
  val canonicalId: String? = null,
)

internal enum class FeatureTaskRuntimeProjectionCanonicalizationTransform(val wireValue: String) {
  TASK_ID_NORMALIZED("task_id_normalized"),
  TABS_TO_SPACE("tabs_to_space"),
  BACKTICKS_STRIPPED("backticks_stripped"),
  TRIMMED("trimmed"),
  UNKNOWN_KEY_DISCARDED("unknown_key_discarded"),

  SCALAR_PROMOTED_TO_OBJECT("scalar_promoted_to_object"),

  MISNAMED_KEY_ADOPTED("misnamed_key_adopted"),
}

internal val FEATURE_TASK_RUNTIME_CLOSED_PROJECTION_OBJECT_KEYS: Map<String, Set<String>> = emptyMap()

const val MAX_CANONICALIZATION_RECORDS: Int = 256

const val MAX_RECORDED_ID_LENGTH: Int = 128
