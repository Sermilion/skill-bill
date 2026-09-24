package skillbill.scaffold.model.command

import skillbill.scaffold.policy.ACTIVE_CREATION_SKILL_KINDS
import skillbill.scaffold.policy.SCAFFOLD_PAYLOAD_VERSION
import skillbill.scaffold.policy.SKILL_KIND_ADD_ON
import skillbill.scaffold.policy.SKILL_KIND_AGENT_ADDON
import skillbill.scaffold.policy.SKILL_KIND_HORIZONTAL
import skillbill.scaffold.policy.SKILL_KIND_PLATFORM_PACK
import skillbill.scaffold.policy.isRetiredPartialScaffoldKindAlias
import skillbill.scaffold.policy.rejectRetiredPartialScaffoldKind

val SCAFFOLD_COMMAND_PAYLOAD_VERSION: String get() = SCAFFOLD_PAYLOAD_VERSION
val SCAFFOLD_COMMAND_KIND_HORIZONTAL: String get() = SKILL_KIND_HORIZONTAL
val SCAFFOLD_COMMAND_KIND_PLATFORM_PACK: String get() = SKILL_KIND_PLATFORM_PACK
val SCAFFOLD_COMMAND_KIND_ADD_ON: String get() = SKILL_KIND_ADD_ON
val SCAFFOLD_COMMAND_KIND_AGENT_ADDON: String get() = SKILL_KIND_AGENT_ADDON
val ACTIVE_SCAFFOLD_COMMAND_KINDS: Set<String> get() = ACTIVE_CREATION_SKILL_KINDS

fun isRetiredPartialScaffoldCommandKindAlias(kind: String): Boolean = isRetiredPartialScaffoldKindAlias(kind)

fun rejectRetiredPartialScaffoldCommandKind(kind: String): Nothing = rejectRetiredPartialScaffoldKind(kind)
