package skillbill.ports.experiment.isolation.model

data class ExperimentIsolationObservation(
  val policyBreaches: List<String> = emptyList(),
) {
  val valid: Boolean get() = policyBreaches.isEmpty()
}
