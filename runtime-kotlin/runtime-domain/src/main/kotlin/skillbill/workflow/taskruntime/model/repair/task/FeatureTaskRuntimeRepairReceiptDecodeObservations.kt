package skillbill.workflow.taskruntime.model.repair.task
import skillbill.text.Utf8Text

data class FeatureTaskRuntimeRepairReceiptDecodeObservations(
  val truncationRecords: List<String> = emptyList(),
) {
  internal class Collector {
    private val records = mutableListOf<String>()

    fun add(record: String) {
      records.add(record)
    }

    fun finish(): FeatureTaskRuntimeRepairReceiptDecodeObservations =
      FeatureTaskRuntimeRepairReceiptDecodeObservations(records.toList())
  }
}

data class FeatureTaskRuntimeRepairReceiptDecoded(
  val receipt: FeatureTaskRuntimeRepairReceipt,
  val observations: FeatureTaskRuntimeRepairReceiptDecodeObservations,
)

internal fun forwardOptionalReceiptReason(
  raw: String?,
  fieldPath: String,
  maxUtf8Bytes: Int,
  collector: FeatureTaskRuntimeRepairReceiptDecodeObservations.Collector?,
): String? {
  val trimmed = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
  val forwarded = Utf8Text.truncateToUtf8Bytes(trimmed, maxUtf8Bytes)
  if (Utf8Text.utf8Size(forwarded) < Utf8Text.utf8Size(trimmed)) {
    collector?.add(repairReceiptReasonTruncationRecord(fieldPath, maxUtf8Bytes))
  }
  return forwarded
}

internal fun repairReceiptReasonTruncationRecord(
  fieldPath: String,
  maxUtf8Bytes: Int,
): String =
  "seam=FeatureTaskRuntimeRepairReceiptEntry.fromArtifactMap " +
    "value_used='$fieldPath truncated to $maxUtf8Bytes UTF-8 bytes' " +
    "value_expected='$fieldPath within $maxUtf8Bytes UTF-8 bytes' " +
    "cause=agent census reason exceeded the repair receipt byte cap"
