package skillbill.error

import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys

class SystemOneNotEnabledError :
  SkillBillRuntimeException(
    "TypeSafe is experimental and off. Enable it with `skill-bill typesafe configure --enable` " +
      "after setting ${SystemOneConfigPayloadKeys.ROOT}.${SystemOneConfigPayloadKeys.API_KEY} " +
      "in machine config.",
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
