package skillbill.review

import skillbill.review.model.ParallelReviewLaneResult
import skillbill.review.model.ParallelReviewMergeResult
import skillbill.review.model.ParallelReviewMergedFinding
import skillbill.review.model.ParallelReviewRawFinding
import skillbill.review.model.ParallelReviewSeverity

object ParallelReviewMerger {
  fun merge(lane1: ParallelReviewLaneResult, lane2: ParallelReviewLaneResult): ParallelReviewMergeResult {
    val findings1 = ParallelReviewFindingParser.parse(lane1.rawOutput)
    val findings2 = ParallelReviewFindingParser.parse(lane2.rawOutput)

    val allEntries = mutableListOf<FindingEntry>()
    findings1.forEachIndexed { i, f -> allEntries += FindingEntry(f, lane1.agentId, i) }
    findings2.forEachIndexed { i, f -> allEntries += FindingEntry(f, lane2.agentId, i) }

    fun dedupKey(f: ParallelReviewRawFinding): String =
      "${f.location.trim().lowercase()}|${f.description.trim().lowercase()}"

    val grouped = linkedMapOf<String, MutableList<FindingEntry>>()
    allEntries.forEach { entry ->
      grouped.getOrPut(dedupKey(entry.finding)) { mutableListOf() } += entry
    }

    val candidates = grouped.values.map { entries ->
      val coalesced = entries.map { it.agentId }.distinct().size > 1
      val highestSeverity = entries.minByOrNull { it.finding.severity.ordinal }!!.finding.severity
      val firstEntry = entries.minByOrNull { it.appearanceOrder }!!
      MergedCandidate(
        agentIds = entries.map { it.agentId }.distinct(),
        severity = highestSeverity,
        confidence = firstEntry.finding.confidence,
        location = firstEntry.finding.location,
        description = firstEntry.finding.description,
        isCoalesced = coalesced,
        firstAppearance = firstEntry.appearanceOrder,
      )
    }

    val sorted = candidates.sortedWith(
      compareBy<MergedCandidate> { it.severity.ordinal }
        .thenBy { if (it.isCoalesced) 0 else 1 }
        .thenBy { it.firstAppearance },
    )

    val mergedFindings = sorted.mapIndexed { index, candidate ->
      ParallelReviewMergedFinding(
        fNumber = "F-%03d".format(index + 1),
        agentIds = candidate.agentIds,
        severity = candidate.severity,
        confidence = candidate.confidence,
        location = candidate.location,
        description = candidate.description,
      )
    }

    val formattedOutput = mergedFindings.joinToString("\n") { f ->
      val agentLabel = f.agentIds.joinToString(", ")
      "- [${f.fNumber}] [$agentLabel] ${f.severity.displayName} | ${f.confidence} | ${f.location} | ${f.description}"
    }

    return ParallelReviewMergeResult(
      findings = mergedFindings,
      formattedOutput = formattedOutput,
    )
  }

  private data class FindingEntry(
    val finding: ParallelReviewRawFinding,
    val agentId: String,
    val appearanceOrder: Int,
  )

  private data class MergedCandidate(
    val agentIds: List<String>,
    val severity: ParallelReviewSeverity,
    val confidence: String,
    val location: String,
    val description: String,
    val isCoalesced: Boolean,
    val firstAppearance: Int,
  )
}
