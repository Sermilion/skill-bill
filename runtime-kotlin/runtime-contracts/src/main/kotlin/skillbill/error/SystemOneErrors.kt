package skillbill.error

import skillbill.contracts.experiment.config.ExperimentConfigPayloadKeys
import skillbill.contracts.experiment.config.ExperimentNames
import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.error.core.SkillBillRuntimeException

class SystemOneNotEnabledError :
  SkillBillRuntimeException(
    "TypeSafe is experimental and off. Add '${ExperimentNames.TYPESAFE}' to the machine config " +
      "'${ExperimentConfigPayloadKeys.EXPERIMENTS}' array, or run `skill-bill typesafe configure --enable` " +
      "after setting an API key.",
  )

class SystemOneApiKeyMissingError :
  SkillBillRuntimeException(
    "TypeSafe API key is missing. Store it with `skill-bill typesafe configure --api-key <key>` " +
      "or set ${SystemOneConfigPayloadKeys.ROOT}.${SystemOneConfigPayloadKeys.API_KEY} " +
      "in ~/.config/skill-bill/config.json.",
  )

class SystemOneHttpResponseError(
  val statusCode: Int,
  detail: String,
) : SkillBillRuntimeException("TypeSafe API request failed with HTTP $statusCode: $detail")

class SystemOneMalformedResponseError(
  detail: String,
) : SkillBillRuntimeException("TypeSafe API response was malformed: $detail")
