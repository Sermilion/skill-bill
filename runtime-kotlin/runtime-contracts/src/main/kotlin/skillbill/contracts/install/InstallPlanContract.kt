package skillbill.contracts.install

import skillbill.contracts.JsonPayloadContract

class InstallPlanContract private constructor(
  private val wire: Map<String, Any?>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = LinkedHashMap(wire)

  companion object {
    fun wrap(wire: Map<String, Any?>): InstallPlanContract = InstallPlanContract(LinkedHashMap(wire))
  }
}
