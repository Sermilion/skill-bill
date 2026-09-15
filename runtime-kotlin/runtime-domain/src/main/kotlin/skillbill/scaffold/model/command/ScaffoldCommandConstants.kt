package skillbill.scaffold.model.command

import skillbill.scaffold.policy.scaffold.ACTIVE_CREATION_SKILL_KINDS
import skillbill.scaffold.policy.scaffold.RETIRED_PARTIAL_SCAFFOLD_KIND_ALIASES
import skillbill.scaffold.policy.scaffold.SCAFFOLD_PAYLOAD_VERSION
import skillbill.scaffold.policy.scaffold.SKILL_KIND_ADD_ON
import skillbill.scaffold.policy.scaffold.SKILL_KIND_AGENT_ADDON
import skillbill.scaffold.policy.scaffold.SKILL_KIND_CODE_REVIEW_AREA
import skillbill.scaffold.policy.scaffold.SKILL_KIND_HORIZONTAL
import skillbill.scaffold.policy.scaffold.SKILL_KIND_PLATFORM_OVERRIDE_PILOTED
import skillbill.scaffold.policy.scaffold.SKILL_KIND_PLATFORM_PACK
import skillbill.scaffold.policy.scaffold.SUPPORTED_SKILL_KINDS
import skillbill.scaffold.policy.scaffold.isRetiredPartialScaffoldKindAlias
import skillbill.scaffold.policy.scaffold.rejectRetiredPartialScaffoldKind

val SCAFFOLD_COMMAND_PAYLOAD_VERSION: String get() = SCAFFOLD_PAYLOAD_VERSION
val SCAFFOLD_COMMAND_KIND_HORIZONTAL: String get() = SKILL_KIND_HORIZONTAL
val SCAFFOLD_COMMAND_KIND_PLATFORM_OVERRIDE_PILOTED: String get() = SKILL_KIND_PLATFORM_OVERRIDE_PILOTED
val SCAFFOLD_COMMAND_KIND_PLATFORM_PACK: String get() = SKILL_KIND_PLATFORM_PACK
val SCAFFOLD_COMMAND_KIND_CODE_REVIEW_AREA: String get() = SKILL_KIND_CODE_REVIEW_AREA
val SCAFFOLD_COMMAND_KIND_ADD_ON: String get() = SKILL_KIND_ADD_ON
val SCAFFOLD_COMMAND_KIND_AGENT_ADDON: String get() = SKILL_KIND_AGENT_ADDON
val SUPPORTED_SCAFFOLD_COMMAND_KINDS: Set<String> get() = SUPPORTED_SKILL_KINDS
val ACTIVE_SCAFFOLD_COMMAND_KINDS: Set<String> get() = ACTIVE_CREATION_SKILL_KINDS
val RETIRED_PARTIAL_SCAFFOLD_COMMAND_KIND_ALIASES: Set<String> get() = RETIRED_PARTIAL_SCAFFOLD_KIND_ALIASES

fun isRetiredPartialScaffoldCommandKindAlias(kind: String): Boolean = isRetiredPartialScaffoldKindAlias(kind)

fun rejectRetiredPartialScaffoldCommandKind(kind: String): Nothing = rejectRetiredPartialScaffoldKind(kind)
