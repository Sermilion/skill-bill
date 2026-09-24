package skillbill.cli.scaffold.commands

import skillbill.contracts.SharedPayloadKeys
import skillbill.ports.scaffold.catalog.model.ScaffoldExplainResult
import skillbill.ports.scaffold.catalog.model.ScaffoldListResult
import skillbill.ports.scaffold.catalog.model.ScaffoldShowResult
import skillbill.ports.scaffold.model.ScaffoldSkillStatus
import skillbill.ports.scaffold.repo.model.ScaffoldUpgradeResult
import skillbill.ports.scaffold.repo.model.ScaffoldValidateResult
import skillbill.ports.scaffold.repo.model.ScaffoldValidationMode
import skillbill.ports.scaffold.source.model.ScaffoldEditWithBodyFileResult
import skillbill.ports.scaffold.source.model.ScaffoldFillResult
import skillbill.ports.scaffold.source.model.ScaffoldSaveExactContentResult

internal fun ScaffoldListResult.toCliMap(): Map<String, Any?> =
  linkedMapOf(
    "repo_root" to repoRoot,
    "skill_count" to skillCount,
    "skills" to skills.map(ScaffoldSkillStatus::toWireMap),
  )

internal fun ScaffoldShowResult.toCliMap(): Map<String, Any?> = status.toWireMap()

internal fun ScaffoldExplainResult.toCliMap(): Map<String, Any?> {
  val map =
    linkedMapOf<String, Any?>(
      "explanation" to explanation,
      "editable_surface" to editableSurface,
      "generated_surface" to generatedSurface,
      "governed_sidecars" to governedSidecars,
      "normal_workflow" to normalWorkflow,
      "notes" to notes,
    )
  skill?.let { map["skill"] = it.toWireMap() }
  return map
}

internal fun ScaffoldValidateResult.toCliMap(): Map<String, Any?> {
  val map = linkedMapOf<String, Any?>("repo_root" to repoRoot, "mode" to mode.wireValue)
  if (mode == ScaffoldValidationMode.SELECTED) {
    map["skill_names"] = skillNames ?: emptyList<String>()
  }
  map[SharedPayloadKeys.STATUS] = status.wireValue
  map["issues"] = issues
  if (mode == ScaffoldValidationMode.SELECTED) {
    map["suggested_commands"] = suggestedCommands ?: emptyList<String>()
  }
  return map
}

internal fun ScaffoldUpgradeResult.toCliMap(): Map<String, Any?> =
  linkedMapOf(
    "repo_root" to repoRoot,
    "regenerated_count" to regeneratedCount,
    "regenerated_files" to regeneratedFiles,
    "content_md_touched" to contentMdTouched,
    "shell_ceremony_touched" to shellCeremonyTouched,
    "validator_ran" to validatorRan,
  )

internal fun ScaffoldFillResult.toCliMap(): Map<String, Any?> {
  val map = LinkedHashMap<String, Any?>(status.toWireMap())
  map["wrapper_regenerated"] = wrapperRegenerated
  map["updated_section"] = updatedSection
  map["validator_ran"] = validatorRan
  return map
}

internal fun ScaffoldSaveExactContentResult.toCliMap(): Map<String, Any?> {
  val map = LinkedHashMap<String, Any?>(status.toWireMap())
  map["wrapper_regenerated"] = wrapperRegenerated
  map["updated_section"] = updatedSection
  map["validator_ran"] = validatorRan
  return map
}

internal fun ScaffoldEditWithBodyFileResult.toCliMap(): Map<String, Any?> {
  val map =
    linkedMapOf<String, Any?>(
      "used_editor" to usedEditor,
      "guided_sections" to guidedSections,
      "updated_section" to updatedSection,
      "validator_ran" to validatorRan,
    )
  map.putAll(status.toWireMap())
  map["wrapper_regenerated"] = wrapperRegenerated
  return map
}
