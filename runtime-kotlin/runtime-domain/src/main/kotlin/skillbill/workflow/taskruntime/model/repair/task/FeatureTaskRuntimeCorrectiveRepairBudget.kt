package skillbill.workflow.taskruntime.model.repair.task
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.handoff.task.itemCount

data class FeatureTaskRuntimeCorrectiveRepairBudget(
  val maxResponseUtf8Bytes: Int,
  val maxPromptUtf8Bytes: Int,
  val maxCollectionItems: Int,
) {
  init {
    require(maxResponseUtf8Bytes > 0) {
      "FeatureTaskRuntimeCorrectiveRepairBudget.maxResponseUtf8Bytes must be positive, was $maxResponseUtf8Bytes."
    }
    require(maxPromptUtf8Bytes > 0) {
      "FeatureTaskRuntimeCorrectiveRepairBudget.maxPromptUtf8Bytes must be positive, was $maxPromptUtf8Bytes."
    }
    require(maxCollectionItems > 0) {
      "FeatureTaskRuntimeCorrectiveRepairBudget.maxCollectionItems must be positive, was $maxCollectionItems."
    }
    require(maxPromptUtf8Bytes >= maxResponseUtf8Bytes) {
      "FeatureTaskRuntimeCorrectiveRepairBudget.maxPromptUtf8Bytes ($maxPromptUtf8Bytes) must be at least " +
        "maxResponseUtf8Bytes ($maxResponseUtf8Bytes) so an exact body can be framed."
    }
  }

  fun requireCollectionWithinLimit(itemCount: Int, label: String = "corrective-repair projection") {
    require(itemCount >= 0) {
      "FeatureTaskRuntimeCorrectiveRepairBudget collection count for $label must be non-negative, was $itemCount."
    }
    require(itemCount <= maxCollectionItems) {
      "FeatureTaskRuntimeCorrectiveRepairBudget: $label carries $itemCount items against the " +
        "$maxCollectionItems-item collection budget; the runtime rejects rather than truncating."
    }
  }

  companion object {

    val DEFAULT: FeatureTaskRuntimeCorrectiveRepairBudget =
      FeatureTaskRuntimeCorrectiveRepairBudget(
        maxResponseUtf8Bytes = MAX_RESPONSE_UTF8_BYTES,
        maxPromptUtf8Bytes = MAX_PROMPT_UTF8_BYTES,
        maxCollectionItems = MAX_COLLECTION_ITEMS,
      )

    const val MAX_RESPONSE_UTF8_BYTES: Int = 65_536
    const val MAX_PROMPT_UTF8_BYTES: Int = 98_304
    const val MAX_COLLECTION_ITEMS: Int = 16
  }
}
