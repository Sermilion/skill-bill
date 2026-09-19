package skillbill.cli.scaffold.commands
import skillbill.cli.scaffold.payload.content
import skillbill.cli.scaffold.payload.layer
import skillbill.cli.scaffold.wizard.platform
import skillbill.contracts.SharedPayloadKeys
import skillbill.ports.scaffold.catalog.model.ScaffoldExplainSkill
import skillbill.ports.scaffold.model.ScaffoldReviewComposition
import skillbill.ports.scaffold.model.ScaffoldSectionStatus
import skillbill.ports.scaffold.model.ScaffoldSkillStatus

internal fun ScaffoldSkillStatus.toWireMap(): Map<String, Any?> {
  val map = linkedMapOf<String, Any?>(
    "skill_name" to skillName,
    "package" to packageName,
    "platform" to platform,
    "family" to family,
    "area" to area,
    "content_file" to contentFile,
    "render_command" to renderCommand,
    "completion_status" to completionStatus.wireValue,
    "section_count" to sectionCount,
    "sections" to sections.map(ScaffoldSectionStatus::toWireMap),
    "recommended_commands" to recommendedCommands,
  )
  reviewComposition?.let { map["review_composition"] = it.toWireMap() }
  contentPreview?.let { map["content_preview"] = it }
  content?.let { map["content"] = it }
  issues?.let { map["issues"] = it }
  if (category != "skill") map["category"] = category
  slug?.let { map["slug"] = it }
  description?.let { map["description"] = it }
  if (supportedAgents.isNotEmpty()) map["supported_agents"] = supportedAgents
  if (consumers.isNotEmpty()) map["consumers"] = consumers
  manifestFile?.let { map["manifest_file"] = it }
  return map
}

internal fun ScaffoldSectionStatus.toWireMap(): Map<String, Any?> = linkedMapOf(
  "heading" to heading,
  SharedPayloadKeys.STATUS to status.wireValue,
  "line_count" to lineCount,
  "preview" to preview,
)

internal fun ScaffoldReviewComposition.toWireMap(): Map<String, Any?> = linkedMapOf(
  "source" to source,
  SharedPayloadKeys.SUMMARY to summary,
  "baseline_layers" to baselineLayers.map { layer ->
    linkedMapOf<String, Any?>(
      "platform" to layer.platform,
      "skill" to layer.skill,
      "scope" to layer.scope,
      "required" to layer.required,
      "mode" to layer.mode,
    )
  },
)

internal fun ScaffoldExplainSkill.toWireMap(): Map<String, Any?> = linkedMapOf(
  "skill_name" to skillName,
  "content_file" to contentFile,
  "render_command" to renderCommand,
  "recommended_commands" to recommendedCommands,
)
