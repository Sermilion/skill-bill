package skillbill.infrastructure.skills.scaffold.payload

import skillbill.scaffold.model.command.ScaffoldCommandRequest
import skillbill.scaffold.policy.SKILL_KIND_ADD_ON
import skillbill.scaffold.policy.SKILL_KIND_AGENT_ADDON
import skillbill.scaffold.policy.SKILL_KIND_CODE_REVIEW_AREA
import skillbill.scaffold.policy.SKILL_KIND_HORIZONTAL
import skillbill.scaffold.policy.SKILL_KIND_PLATFORM_OVERRIDE_PILOTED
import skillbill.scaffold.policy.SKILL_KIND_PLATFORM_PACK

internal fun ScaffoldCommandRequest.toRawScaffoldPayload(): Map<String, Any?> {
  val base =
    linkedMapOf<String, Any?>(
      "scaffold_payload_version" to scaffoldPayloadVersion,
    )
  repoRoot?.let { base["repo_root"] = it }
  when (this) {
    is ScaffoldCommandRequest.HorizontalSkill -> appendHorizontalFields(base)
    is ScaffoldCommandRequest.PlatformPack -> appendPlatformPackFields(base)
    is ScaffoldCommandRequest.PlatformOverride -> appendPlatformOverrideFields(base)
    is ScaffoldCommandRequest.CodeReviewArea -> appendCodeReviewAreaFields(base)
    is ScaffoldCommandRequest.AddOn -> appendAddOnFields(base)
    is ScaffoldCommandRequest.AgentAddon -> appendAgentAddonFields(base)
  }
  return base
}

private fun ScaffoldCommandRequest.AgentAddon.appendAgentAddonFields(base: MutableMap<String, Any?>) {
  base["kind"] = SKILL_KIND_AGENT_ADDON
  base["slug"] = slug
  base["description"] = description
  base["agent_ids"] = agentIds
  base["consumers"] = consumers
  contentBody?.let { base["content_body"] = it }
}

private fun ScaffoldCommandRequest.HorizontalSkill.appendHorizontalFields(base: MutableMap<String, Any?>) {
  base["kind"] = SKILL_KIND_HORIZONTAL
  base["name"] = name
  if (description.isNotBlank()) base["description"] = description
  contentBody?.let { base["content_body"] = it }
  if (subagentSpecialists.isNotEmpty()) base["subagent_specialists"] = subagentSpecialists
  if (suppressSubagents) base["no_subagents"] = true
}

private fun ScaffoldCommandRequest.PlatformPack.appendPlatformPackFields(base: MutableMap<String, Any?>) {
  base["kind"] = SKILL_KIND_PLATFORM_PACK
  base["platform"] = platform
  nameOverride?.let { base["name"] = it }
  if (displayName.isNotBlank()) base["display_name"] = displayName
  if (description.isNotBlank()) base["description"] = description
  routingSignals?.let { signals ->
    val routing = linkedMapOf<String, Any?>()
    signals.strong?.let { routing["strong"] = it }
    signals.tieBreakers?.let { routing["tie_breakers"] = it }
    base["routing_signals"] = routing
  }
  if (baselineLayers.isNotEmpty()) {
    base["baseline_layers"] =
      baselineLayers.map { layer ->
        linkedMapOf<String, Any?>(
          "platform" to layer.platform,
          "skill" to layer.skill,
          "scope" to layer.scope.wireValue,
          "required" to layer.required,
          "mode" to layer.mode.wireValue,
        )
      }
  }
  subagentSpecialists?.let { base["subagent_specialists"] = it }
  if (suppressSubagents) base["no_subagents"] = true
  contentBody?.let { base["content_body"] = it }
  packLocationPath?.let { base["pack_location_path"] = it }
  packRegistration?.let { base["pack_registration"] = it }
}

private fun ScaffoldCommandRequest.PlatformOverride.appendPlatformOverrideFields(base: MutableMap<String, Any?>) {
  base["kind"] = SKILL_KIND_PLATFORM_OVERRIDE_PILOTED
  base["platform"] = platform
  base["family"] = family
  nameOverride?.let { base["name"] = it }
  if (description.isNotBlank()) base["description"] = description
  contentBody?.let { base["content_body"] = it }
  subagentSpecialists?.let { base["subagent_specialists"] = it }
  if (suppressSubagents) base["no_subagents"] = true
}

private fun ScaffoldCommandRequest.CodeReviewArea.appendCodeReviewAreaFields(base: MutableMap<String, Any?>) {
  base["kind"] = SKILL_KIND_CODE_REVIEW_AREA
  base["platform"] = platform
  base["area"] = area
  nameOverride?.let { base["name"] = it }
  if (description.isNotBlank()) base["description"] = description
  contentBody?.let { base["content_body"] = it }
}

private fun ScaffoldCommandRequest.AddOn.appendAddOnFields(base: MutableMap<String, Any?>) {
  base["kind"] = SKILL_KIND_ADD_ON
  base["name"] = name
  base["platform"] = platform
  if (description.isNotBlank()) base["description"] = description
  body?.let { base["body"] = it }
  addonLocationPath?.let { base["addon_location_path"] = it }
  consumerSkillDirs?.let { base["consumer_skill_dirs"] = it }
}
