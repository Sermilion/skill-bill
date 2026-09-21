package skillbill.ports.experiment.codegraph
import skillbill.ports.experiment.codegraph.model.CodeGraphPairUsageSnapshot

interface CodeGraphUsageLedgerPort {
  fun recordSetup(pairId: String, installDurationMs: Long)

  fun recordIndex(pairId: String, durationMs: Long)

  fun recordSync(pairId: String, durationMs: Long)

  fun recordQuery(pairId: String, evidenceBytes: Long)

  fun markDegraded(pairId: String, reason: String)

  fun snapshot(pairId: String): CodeGraphPairUsageSnapshot
}
