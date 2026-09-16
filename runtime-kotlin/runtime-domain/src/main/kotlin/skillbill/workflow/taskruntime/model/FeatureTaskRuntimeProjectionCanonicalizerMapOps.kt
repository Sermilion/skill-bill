package skillbill.workflow.taskruntime.model

internal object FeatureTaskRuntimeProjectionCanonicalizerMapOps {
  fun mapEntries(value: Any?, transform: (Int, Map<String, Any?>) -> Map<String, Any?>): Any? {
    val list = value as? List<*> ?: return value
    return list.mapIndexed { index, entry ->
      val entryMap = entry as? Map<*, *> ?: return@mapIndexed entry
      val stringKeyed = entryMap.stringKeyedView() ?: return@mapIndexed entry
      transform(index, stringKeyed)
    }
  }

  fun mapObject(value: Any?, transform: (Map<String, Any?>) -> Map<String, Any?>): Any? {
    val map = value as? Map<*, *> ?: return value
    val stringKeyed = map.stringKeyedView() ?: return value
    return transform(stringKeyed)
  }

  fun Map<*, *>.stringKeyedView(): Map<String, Any?>? {
    if (keys.any { it !is String }) return null
    return entries.associate { (key, entryValue) -> key as String to entryValue }
  }
}
