package skillbill.ports.validation.model

data class DiscoveredPrCheck(
  val checkId: String,
  val command: String,
  val pathPatterns: List<String>,
)

sealed interface PrCheckDiscoveryResult {
  data class Discovered(val checks: List<DiscoveredPrCheck>) : PrCheckDiscoveryResult

  data class Failed(val reason: String) : PrCheckDiscoveryResult
}

data class PrCheckRunResult(
  val exitCode: Int,
  val durationMs: Long,
)
