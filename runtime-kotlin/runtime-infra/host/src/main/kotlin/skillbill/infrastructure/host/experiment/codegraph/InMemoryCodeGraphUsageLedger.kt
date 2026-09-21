package skillbill.infrastructure.host.experiment.codegraph

import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import skillbill.ports.experiment.codegraph.model.CodeGraphPairUsageSnapshot
import java.util.concurrent.ConcurrentHashMap

class InMemoryCodeGraphUsageLedger : CodeGraphUsageLedgerPort {
  private data class MutableUsage(
    var installDurationMs: Long = 0,
    var indexDurationMs: Long = 0,
    var syncDurationMs: Long = 0,
    var queryCount: Int = 0,
    var evidenceConsumedBytes: Long = 0,
    var degraded: Boolean = false,
    var degradationReason: String? = null,
  )

  private val usage = ConcurrentHashMap<String, MutableUsage>()

  override fun recordSetup(pairId: String, installDurationMs: Long) {
    usage.computeIfAbsent(pairId) { MutableUsage() }.installDurationMs = installDurationMs
  }

  override fun recordIndex(pairId: String, durationMs: Long) {
    usage.computeIfAbsent(pairId) { MutableUsage() }.indexDurationMs += durationMs
  }

  override fun recordSync(pairId: String, durationMs: Long) {
    usage.computeIfAbsent(pairId) { MutableUsage() }.syncDurationMs += durationMs
  }

  override fun recordQuery(pairId: String, evidenceBytes: Long) {
    val entry = usage.computeIfAbsent(pairId) { MutableUsage() }
    entry.queryCount += 1
    entry.evidenceConsumedBytes += evidenceBytes
  }

  override fun markDegraded(pairId: String, reason: String) {
    val entry = usage.computeIfAbsent(pairId) { MutableUsage() }
    entry.degraded = true
    entry.degradationReason = reason
  }

  override fun snapshot(pairId: String): CodeGraphPairUsageSnapshot {
    val entry = usage[pairId] ?: return CodeGraphPairUsageSnapshot(pairId = pairId, notExercised = true)
    return CodeGraphPairUsageSnapshot(
      pairId = pairId,
      installDurationMs = entry.installDurationMs,
      indexDurationMs = entry.indexDurationMs,
      syncDurationMs = entry.syncDurationMs,
      queryCount = entry.queryCount,
      evidenceConsumedBytes = entry.evidenceConsumedBytes,
      degraded = entry.degraded,
      notExercised = entry.queryCount == 0 && entry.indexDurationMs == 0L,
      degradationReason = entry.degradationReason,
    )
  }
}
