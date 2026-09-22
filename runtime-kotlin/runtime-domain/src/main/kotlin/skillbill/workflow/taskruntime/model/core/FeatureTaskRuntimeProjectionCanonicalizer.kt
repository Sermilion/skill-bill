package skillbill.workflow.taskruntime.model.core
import skillbill.contracts.JsonCodec
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys

internal object FeatureTaskRuntimeProjectionCanonicalizer {
  fun canonicalize(produced: Map<String, Any?>): FeatureTaskRuntimeProjectionCanonicalization {
    val records = mutableListOf<FeatureTaskRuntimeProjectionCanonicalizationRecord>()
    val declaredIds = buildDeclaredIdMap(produced)
    val canonical = LinkedHashMap<String, Any?>(produced.size)
    produced.forEach { (key, value) ->
      canonical[key] = canonicalizeTopLevelKey(key, value, declaredIds, records)
    }
    return FeatureTaskRuntimeProjectionCanonicalization(
      canonical = canonical,
      diagnostics = records.take(MAX_CANONICALIZATION_RECORDS),
    )
  }

  fun canonicalizeTaskId(raw: String): String =
    raw.trim()
      .lowercase()
      .replace(FEATURE_TASK_RUNTIME_ID_SEPARATOR_RUN, "-")
      .replace(FEATURE_TASK_RUNTIME_ID_INVALID_CHAR, "")
      .replace(FEATURE_TASK_RUNTIME_ID_HYPHEN_RUN, "-")

  private fun buildDeclaredIdMap(produced: Map<String, Any?>): Map<String, String> {
    val map = LinkedHashMap<String, String>()

    fun harvest(listKey: String) {
      (produced[listKey] as? List<*>)?.forEach { entry ->
        val id = (entry as? Map<*, *>)?.get("task_id") as? String ?: return@forEach
        map.putIfAbsent(id, canonicalizeTaskId(id))
      }
    }
    harvest("tasks")
    harvest("task_commitments")
    return map
  }

  private fun canonicalizeTopLevelKey(
    key: String,
    value: Any?,
    declaredIds: Map<String, String>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Any? =
    when (key) {
      "tasks" ->
        mapEntries(value) { index, entry ->
          canonicalizeTaskEntry(entry, declaredIds, records, index)
        }
      "task_commitments" ->
        mapEntries(value) { index, entry ->
          canonicalizeCommitmentEntry(entry, records, index)
        }
      "deviations" ->
        mapEntries(value) { index, entry ->
          canonicalizeDeviationEntry(entry, records, index)
        }
      "completed_task_ids" -> canonicalizeReferenceIds(value, declaredIds, records, key)
      "tests_executed" ->
        discardUnknownKeysInEntries(
          value,
          FEATURE_TASK_RUNTIME_TEST_EXECUTION_KEYS,
          key,
          records,
        )
      "reconciliation_evidence" -> canonicalizeReconciliationEvidence(value, records)
      "repository_checkpoint" -> canonicalizeRepositoryCheckpoint(value, records, key)
      in FEATURE_TASK_RUNTIME_NONBLANK_STRING_LIST_KEYS -> trimStringList(value, records, key)
      else -> value
    }

  private fun canonicalizeReconciliationEvidence(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Any? =
    mapObject(promotedReconciliationEvidence(value, records)) {
      trimNonBlank(
        discardUnknownKeys(
          adoptedProseKey(
            it,
            FEATURE_TASK_RUNTIME_RECONCILIATION_EVIDENCE_KEYS,
            "evidence",
            "reconciliation_evidence",
            records,
          ),
          FEATURE_TASK_RUNTIME_RECONCILIATION_EVIDENCE_KEYS,
          "reconciliation_evidence",
          records,
        ),
        "evidence",
        records,
        "reconciliation_evidence.evidence",
      )
    }

  private fun canonicalizeRepositoryCheckpoint(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    key: String,
  ): Any? =
    mapObject(value) { checkpoint ->
      trimNonBlank(
        trimNonBlank(
          trimNonBlank(
            discardUnknownKeys(
              checkpoint,
              FEATURE_TASK_RUNTIME_REPOSITORY_CHECKPOINT_KEYS,
              key,
              records,
            ),
            "fingerprint",
            records,
            "repository_checkpoint.fingerprint",
          ),
          "base_ref",
          records,
          "repository_checkpoint.base_ref",
        ),
        "head_ref",
        records,
        "repository_checkpoint.head_ref",
      )
    }

  private fun canonicalizeTaskEntry(
    entry: Map<String, Any?>,
    declaredIds: Map<String, String>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    index: Int,
  ): Map<String, Any?> {
    val governed =
      discardUnknownKeys(
        entry,
        FEATURE_TASK_RUNTIME_PLAN_TASK_KEYS,
        "tasks[$index]",
        records,
      )
    val result = LinkedHashMap<String, Any?>(governed.size)
    governed.forEach { (key, value) ->
      result[key] =
        when (key) {
          "task_id" -> canonicalizeDeclaredId(value, records, "tasks[$index].task_id")
          DecompositionPlanningPayloadKeys.DEPENDS_ON ->
            canonicalizeReferenceIds(
              value,
              declaredIds,
              records,
              "tasks[$index].depends_on",
            )
          "description" -> canonicalizeCompactSummary(value, records, "tasks[$index].description")
          in FEATURE_TASK_RUNTIME_NONBLANK_STRING_LIST_KEYS ->
            trimStringList(value, records, "tasks[$index].$key")
          else -> value
        }
    }
    return result
  }

  private fun canonicalizeCommitmentEntry(
    entry: Map<String, Any?>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    index: Int,
  ): Map<String, Any?> {
    val governed =
      discardUnknownKeys(
        entry,
        FEATURE_TASK_RUNTIME_TASK_COMMITMENT_KEYS,
        "task_commitments[$index]",
        records,
      )
    val result = LinkedHashMap<String, Any?>(governed.size)
    governed.forEach { (key, value) ->
      result[key] =
        when (key) {
          "task_id" -> canonicalizeDeclaredId(value, records, "task_commitments[$index].task_id")
          in FEATURE_TASK_RUNTIME_NONBLANK_STRING_LIST_KEYS ->
            trimStringList(value, records, "task_commitments[$index].$key")
          else -> value
        }
    }
    return result
  }

  private fun canonicalizeDeviationEntry(
    entry: Map<String, Any?>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    index: Int,
  ): Map<String, Any?> {
    val governed =
      discardUnknownKeys(
        entry,
        FEATURE_TASK_RUNTIME_DEVIATION_KEYS,
        "deviations[$index]",
        records,
      )
    val result = LinkedHashMap<String, Any?>(governed.size)
    governed.forEach { (key, value) ->
      result[key] =
        when (key) {
          "ref" -> trimNonBlankValue(value, records, "deviations[$index].ref")
          "note" -> canonicalizeCompactSummary(value, records, "deviations[$index].note")
          else -> value
        }
    }
    return result
  }

  private fun canonicalizeDeclaredId(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    fieldPath: String,
  ): Any? {
    val raw = value as? String ?: return value
    val canonical = canonicalizeTaskId(raw)
    recordIdChange(raw, canonical, fieldPath, records)
    return canonical
  }

  private fun canonicalizeReferenceIds(
    value: Any?,
    declaredIds: Map<String, String>,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    fieldPath: String,
  ): Any? {
    val list = value as? List<*> ?: return value
    return list.mapIndexed { index, raw ->
      if (raw !is String) return@mapIndexed raw
      val canonical = declaredIds[raw] ?: canonicalizeTaskId(raw)
      recordIdChange(raw, canonical, "$fieldPath[$index]", records)
      canonical
    }
  }

  private fun canonicalizeCompactSummary(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    fieldPath: String,
  ): Any? {
    val raw = value as? String ?: return value
    val afterTabs = raw.replace(FEATURE_TASK_RUNTIME_TAB_RUN, " ")
    val afterBackticks = afterTabs.replace("`", "")
    val trimmed = afterBackticks.trim()
    val transforms =
      buildList {
        if (afterTabs != raw) add(FeatureTaskRuntimeProjectionCanonicalizationTransform.TABS_TO_SPACE)
        if (afterBackticks != afterTabs) add(FeatureTaskRuntimeProjectionCanonicalizationTransform.BACKTICKS_STRIPPED)
        if (trimmed != afterBackticks) add(FeatureTaskRuntimeProjectionCanonicalizationTransform.TRIMMED)
      }
    if (transforms.isNotEmpty()) {
      records += textFreeRecord(fieldPath, transforms)
    }
    return trimmed
  }

  private fun trimStringList(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    fieldPath: String,
  ): Any? {
    val list = value as? List<*> ?: return value
    return list.mapIndexed { index, raw ->
      trimNonBlankValue(raw, records, "$fieldPath[$index]")
    }
  }

  private fun mapEntries(
    value: Any?,
    transform: (Int, Map<String, Any?>) -> Map<String, Any?>,
  ): Any? {
    val list = value as? List<*> ?: return value
    return list.mapIndexed { index, entry ->
      val entryMap = entry as? Map<*, *> ?: return@mapIndexed entry
      val stringKeyed = entryMap.stringKeyedView() ?: return@mapIndexed entry
      transform(index, stringKeyed)
    }
  }

  private fun mapObject(
    value: Any?,
    transform: (Map<String, Any?>) -> Map<String, Any?>,
  ): Any? {
    val map = value as? Map<*, *> ?: return value
    val stringKeyed = map.stringKeyedView() ?: return value
    return transform(stringKeyed)
  }

  private fun Map<*, *>.stringKeyedView(): Map<String, Any?>? {
    if (keys.any { it !is String }) return null
    return entries.associate { (key, entryValue) -> key as String to entryValue }
  }

  private fun discardUnknownKeys(
    map: Map<String, Any?>,
    governedKeys: Set<String>,
    fieldPath: String,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Map<String, Any?> {
    if (map.keys.all { it in governedKeys }) return map
    val retained = LinkedHashMap<String, Any?>(map.size)
    map.forEach { (key, value) ->
      if (key in governedKeys) {
        retained[key] = value
      } else {
        records +=
          textFreeRecord(
            "$fieldPath.${key.take(MAX_RECORDED_ID_LENGTH)}",
            listOf(FeatureTaskRuntimeProjectionCanonicalizationTransform.UNKNOWN_KEY_DISCARDED),
          )
      }
    }
    return retained
  }

  private fun adoptedProseKey(
    map: Map<String, Any?>,
    governedKeys: Set<String>,
    proseKey: String,
    fieldPath: String,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Map<String, Any?> {
    if (map.containsKey(proseKey)) return map
    val donor = map.keys.singleOrNull { it !in governedKeys } ?: return map
    val prose = (map[donor] as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return map
    records +=
      textFreeRecord(
        "$fieldPath.$proseKey",
        listOf(FeatureTaskRuntimeProjectionCanonicalizationTransform.MISNAMED_KEY_ADOPTED),
      )
    val adopted = LinkedHashMap<String, Any?>(map.size)
    map.forEach { (key, value) -> if (key != donor) adopted[key] = value }
    adopted[proseKey] = prose
    return adopted
  }

  private fun discardUnknownKeysInEntries(
    value: Any?,
    governedKeys: Set<String>,
    fieldPath: String,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Any? {
    val list = value as? List<*> ?: return value
    return list.mapIndexed { index, entry ->
      val stringKeyed = JsonCodec.anyToStringAnyMap(entry) ?: return@mapIndexed entry
      discardUnknownKeys(stringKeyed, governedKeys, "$fieldPath[$index]", records)
    }
  }

  private fun promotedReconciliationEvidence(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ): Any? {
    val evidence = (value as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return value
    records +=
      textFreeRecord(
        "reconciliation_evidence",
        listOf(FeatureTaskRuntimeProjectionCanonicalizationTransform.SCALAR_PROMOTED_TO_OBJECT),
      )
    return linkedMapOf<String, Any?>("reconciled" to true, "evidence" to evidence)
  }

  private fun trimNonBlank(
    map: Map<String, Any?>,
    key: String,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    fieldPath: String,
  ): Map<String, Any?> {
    if (key !in map) return map
    val result = LinkedHashMap<String, Any?>(map)
    result[key] = trimNonBlankValue(map[key], records, fieldPath)
    return result
  }

  private fun trimNonBlankValue(
    value: Any?,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
    fieldPath: String,
  ): Any? {
    val raw = value as? String ?: return value
    val trimmed = raw.trim()
    if (trimmed != raw) {
      records += textFreeRecord(fieldPath, listOf(FeatureTaskRuntimeProjectionCanonicalizationTransform.TRIMMED))
    }
    return trimmed
  }

  private fun recordIdChange(
    raw: String,
    canonical: String,
    fieldPath: String,
    records: MutableList<FeatureTaskRuntimeProjectionCanonicalizationRecord>,
  ) {
    if (canonical == raw) return
    records +=
      FeatureTaskRuntimeProjectionCanonicalizationRecord(
        fieldPath = fieldPath,
        transforms = listOf(FeatureTaskRuntimeProjectionCanonicalizationTransform.TASK_ID_NORMALIZED),
        originalId = raw.take(MAX_RECORDED_ID_LENGTH),
        canonicalId = canonical.take(MAX_RECORDED_ID_LENGTH),
      )
  }

  private fun textFreeRecord(
    fieldPath: String,
    transforms: List<FeatureTaskRuntimeProjectionCanonicalizationTransform>,
  ) = FeatureTaskRuntimeProjectionCanonicalizationRecord(fieldPath = fieldPath, transforms = transforms)
}
