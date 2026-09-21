package skillbill.ports.experiment.config

import skillbill.config.model.ExperimentAvailabilityPolicy

interface MachineExperimentConfigStore {
  fun readExperimentsAvailability(): ExperimentAvailabilityPolicy?
}
