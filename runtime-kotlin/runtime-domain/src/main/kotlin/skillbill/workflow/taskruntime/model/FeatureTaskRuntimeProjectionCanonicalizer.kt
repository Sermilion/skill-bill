package skillbill.workflow.taskruntime.model

internal object FeatureTaskRuntimeProjectionCanonicalizer {

  fun canonicalize(produced: Map<String, Any?>): FeatureTaskRuntimeProjectionCanonicalization {
    val records = mutableListOf<FeatureTaskRuntimeProjectionCanonicalizationRecord>()
    val governed = discardUnknownTopLevelKeys(produced, records)
    val declaredIds = buildDeclaredIdMap(governed)
    val canonical = LinkedHashMap<String, Any?>(governed.size)
    governed.forEach { (key, value) ->
      canonical[key] = canonicalizeTopLevel(key, value, declaredIds, records)
    }
    return FeatureTaskRuntimeProjectionCanonicalization(
      canonical = canonical,
      diagnostics = records.take(MAX_CANONICALIZATION_RECORDS),
    )
  }

  private fun discardUnknownTopLevelKeys(
    produced: Map<String, Any?>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Map<String, Any?> {
    val kind = produced["projection_kind"] as? String ?: return produced
    val governedKeys = FEATURE_TASK_RUNTIME_CLOSED_PROJECTION_OBJECT_KEYS[kind] ?: return produced
    return FeatureTaskRuntimeProjectionCanonicalizerMutations.discardUnknownKeys(produced, governedKeys, kind, records)
  }

  private fun buildDeclaredIdMap(produced: Map<String, Any?>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    fun harvest(listKey: String) {
      (produced[listKey] as? List<*>)?.forEach { entry ->
        val id = (entry as? Map<*, *>)?.get("task_id") as? String ?: return@forEach
        map.putIfAbsent(id, FeatureTaskRuntimeProjectionCanonicalizer.canonicalizeTaskId(id))
      }
    }
    harvest("tasks")
    harvest("task_commitments")
    return map
  }

  private fun canonicalizeTopLevel(
    key: String,
    value: Any?,
    declaredIds: Map<String, String>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Any? = FeatureTaskRuntimeProjectionCanonicalizerTopLevel.canonicalizeTopLevelKey(
    key,
    value,
    declaredIds,
    records,
  )

  fun canonicalizeTaskId(raw: String): String = raw.trim()
    .lowercase()
    .replace(FEATURE_TASK_RUNTIME_ID_SEPARATOR_RUN, "-")
    .replace(FEATURE_TASK_RUNTIME_ID_INVALID_CHAR, "")
    .replace(FEATURE_TASK_RUNTIME_ID_HYPHEN_RUN, "-")
}
