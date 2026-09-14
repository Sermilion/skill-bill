package skillbill.contracts.install

import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.SharedPayloadKeys

class InstallPlanContract private constructor(
  private val wire: Map<String, Any?>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = LinkedHashMap(wire)

  companion object {
    fun wrap(wire: Map<String, Any?>): InstallPlanContract = InstallPlanContract(LinkedHashMap(wire))
  }
}

fun installPlanContractPayload(wire: Map<String, Any?>): Map<String, Any?> = InstallPlanContract.wrap(wire).toPayload()

fun installPlanContractStatusPlanned(): String = InstallPlanPayloadKeys.PLANNED_STATUS

fun installPlanContractVersionKey(): String = SharedPayloadKeys.CONTRACT_VERSION
