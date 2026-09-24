package skillbill.workflow.model.goalreview

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
  val forwarded = truncateToUtf8Bytes(trimmed, maxUtf8Bytes)
  if (utf8Size(forwarded) < utf8Size(trimmed)) {
    collector?.add(repairReceiptReasonTruncationRecord(fieldPath, maxUtf8Bytes))
  }
  return forwarded
}

private fun truncateToUtf8Bytes(
  text: String,
  maxBytes: Int,
): String {
  if (text.length <= maxBytes / MAX_UTF8_BYTES_PER_CHAR) return text
  val encoded = text.encodeToByteArray()
  if (encoded.size <= maxBytes) return text
  var end = maxBytes
  while (end > 0 && (encoded[end].toInt() and CONTINUATION_MASK) == CONTINUATION_MARKER) end -= 1
  return encoded.decodeToString(0, end)
}

private fun utf8Size(text: String): Int = text.encodeToByteArray().size

private const val MAX_UTF8_BYTES_PER_CHAR = 3
private const val CONTINUATION_MASK = 0xC0
private const val CONTINUATION_MARKER = 0x80

internal fun repairReceiptReasonTruncationRecord(
  fieldPath: String,
  maxUtf8Bytes: Int,
): String =
  "seam=FeatureTaskRuntimeRepairReceiptEntry.fromArtifactMap " +
    "value_used='$fieldPath truncated to $maxUtf8Bytes UTF-8 bytes' " +
    "value_expected='$fieldPath within $maxUtf8Bytes UTF-8 bytes' " +
    "cause=agent census reason exceeded the repair receipt byte cap"
