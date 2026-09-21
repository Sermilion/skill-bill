package skillbill.ports.experiment.descriptor

import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.descriptor.model.ExperimentDescriptorRecord as ExperimentDescriptorRecordModel

typealias ExperimentDescriptorRecord = ExperimentDescriptorRecordModel

interface ExperimentDescriptorCatalog {
  fun listCompatible(mode: ExperimentExecutionMode): List<ExperimentDescriptorRecord>

  fun resolve(name: String, mode: ExperimentExecutionMode): ExperimentDescriptorRecord?
}
