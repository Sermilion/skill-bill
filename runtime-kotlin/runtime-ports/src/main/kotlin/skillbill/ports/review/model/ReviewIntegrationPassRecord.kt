package skillbill.ports.review.model

data class ReviewIntegrationPassRecord(
  val commitSequenceDigest: String,
  val terminalOutcome: String,
) {
  init {
    require(commitSequenceDigest.isNotBlank()) { "Integration pass record must name its commit sequence." }
    require(terminalOutcome.isNotBlank()) { "Integration pass record must name its terminal outcome." }
  }
}
