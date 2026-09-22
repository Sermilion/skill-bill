package skillbill.ports.experiment.validation

interface ExperimentPayloadValidationPort {
  fun validatePair(
    payload: Map<String, Any?>,
    sourceLabel: String,
  )

  fun validateObservation(
    payload: Map<String, Any?>,
    sourceLabel: String,
  )

  fun validateReport(
    payload: Map<String, Any?>,
    sourceLabel: String,
  )
}
