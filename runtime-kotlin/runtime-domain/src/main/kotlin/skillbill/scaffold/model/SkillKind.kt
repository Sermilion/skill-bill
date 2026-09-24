package skillbill.scaffold.model

import skillbill.error.shellcontent.UnknownSkillKindError

enum class SkillKind(val wireValue: String) {
  HORIZONTAL("horizontal"),
  PLATFORM_OVERRIDE_PILOTED("platform-override-piloted"),
  PLATFORM_PACK("platform-pack"),
  CODE_REVIEW_AREA("code-review-area"),
  ADD_ON("add-on"),
  AGENT_ADDON("agent-addon"),
  ;

  companion object {
    fun fromWire(value: String): SkillKind =
      entries.firstOrNull { it.wireValue == value }
        ?: throw UnknownSkillKindError(
          "Scaffold payload declares unsupported kind '$value'. " +
            "Supported kinds: ${entries.map(SkillKind::wireValue)}.",
        )
  }
}
