package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.workflow.IdeStatusSchemaValidator
import skillbill.ports.idestatus.IdeStatusValidator
import skillbill.ports.idestatus.IdeStatusWireMap

@Inject
class IdeStatusValidatorAdapter : IdeStatusValidator {
  override fun validate(snapshot: IdeStatusWireMap, sourceLabel: String) {
    IdeStatusSchemaValidator.validate(snapshot, sourceLabel)
  }
}
