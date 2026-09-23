package skillbill.ports.experiment.validation

import skillbill.ports.experiment.pair.model.ExperimentPairPayload

interface ExperimentPayloadValidationPort {
  fun validatePair(
    payload: ExperimentPairPayload,
    sourceLabel: String,
  )

  fun validateObservation(
    payload: ExperimentPairPayload,
    sourceLabel: String,
  )

  fun validateReport(
    payload: ExperimentPairPayload,
    sourceLabel: String,
  )
}
