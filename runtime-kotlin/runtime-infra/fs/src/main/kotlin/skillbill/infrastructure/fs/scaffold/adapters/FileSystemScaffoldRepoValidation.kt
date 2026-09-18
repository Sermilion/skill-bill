package skillbill.infrastructure.fs.scaffold.adapters

import me.tatarka.inject.annotations.Inject
import skillbill.error.InvalidScaffoldPayloadError
import skillbill.error.MissingRequiredSectionError
import skillbill.infrastructure.fs.agentaddon.discoverAgentAddons
import skillbill.infrastructure.fs.scaffold.authoring.AuthoringTarget
import skillbill.infrastructure.fs.scaffold.authoring.validateTarget
import skillbill.infrastructure.fs.scaffold.platformpack.declaredCodeReviewSkillNames
import skillbill.infrastructure.fs.scaffold.platformpack.loadPlatformPack
import skillbill.infrastructure.fs.scaffold.platformpack.unsupportedCompositionModeReason
import skillbill.infrastructure.fs.scaffold.runtime.CONTENT_BODY_FILENAME
import skillbill.infrastructure.fs.scaffold.runtime.ScaffoldPlan
import skillbill.infrastructure.fs.scaffold.runtime.displayNameFromSlug
import skillbill.ports.scaffold.repo.ScaffoldRepoValidationPort
import skillbill.ports.scaffold.repo.model.ScaffoldAuthoringValidationRequest
import skillbill.ports.scaffold.repo.model.ScaffoldAuthoringValidationResult
import skillbill.scaffold.model.CodeReviewBaselineLayer
import skillbill.scaffold.policy.scaffold.SKILL_KIND_AGENT_ADDON
import skillbill.scaffold.policy.scaffold.SKILL_KIND_HORIZONTAL
import java.nio.file.Files
import java.nio.file.Path
import skillbill.scaffold.policy.scaffold.parseBaselineLayerPayload as policyParseBaselineLayerPayload

@Inject
class FileSystemScaffoldRepoValidation : ScaffoldRepoValidationPort {
  override fun validateAuthoringTarget(
    request: ScaffoldAuthoringValidationRequest,
  ): ScaffoldAuthoringValidationResult {
    val target = AuthoringTarget(
      skillName = request.skillName,
      packageName = request.packageName,
      platform = request.platform,
      displayName = request.displayName,
      family = request.family,
      area = request.area,
      skillFile = request.skillFile,
      contentFile = request.contentFile,
    )
    val issues = validateTarget(target, request.repoRoot)
    return ScaffoldAuthoringValidationResult(issues = issues)
  }

  internal fun optionalBaselineLayers(
    payload: Map<String, Any?>,
    repoRoot: Path,
    newPlatform: String,
  ): List<CodeReviewBaselineLayer> {
    val raw = payload["baseline_layers"] ?: return emptyList()
    if (raw !is List<*>) {
      failBaselineLayersNotAList()
    }
    if (raw.isEmpty()) {
      failBaselineLayersEmpty()
    }
    val layers = raw.mapIndexed { index, entry -> policyParseBaselineLayerPayload(index, entry) }
    validateBaselineLayerPayloadReferences(layers, repoRoot, newPlatform)
    return layers
  }

  internal fun validateBaselineLayerPayloadReferences(
    layers: List<CodeReviewBaselineLayer>,
    repoRoot: Path,
    newPlatform: String,
  ) {
    val seenTargets = mutableSetOf<Pair<String, String>>()
    layers.forEachIndexed { index, layer ->
      val targetLabel = "${layer.platform}/${layer.skill}"
      if (layer.platform == newPlatform) {
        failBaselineSelfReference(index, targetLabel)
      }
      if (!seenTargets.add(layer.platform to layer.skill)) {
        failBaselineDuplicate(targetLabel)
      }
      val targetRoot = repoRoot.resolve("platform-packs").resolve(layer.platform)
      if (!Files.isDirectory(targetRoot) || !Files.isRegularFile(targetRoot.resolve("platform.yaml"))) {
        failBaselineMissingPack(index, layer.platform)
      }
      val targetPack = loadPlatformPack(targetRoot)
      if (layer.skill !in targetPack.declaredCodeReviewSkillNames()) {
        failBaselineMissingSkill(index, layer.platform, layer.skill)
      }
      validateBaselineLayerModeSupported(index, layer)
    }
  }

  internal fun validateScaffold(plan: ScaffoldPlan, repoRoot: Path) {
    if (plan.kind == SKILL_KIND_AGENT_ADDON) {
      discoverAgentAddons(repoRoot)
      return
    }
    if (plan.kind == SKILL_KIND_HORIZONTAL) {
      val issues = validateTarget(plannedAuthoringTarget(plan), repoRoot)
      if (issues.isNotEmpty()) {
        failMissingRequiredSection(plan.skillName, issues.first())
      }
      return
    }
    loadPlatformPack(repoRoot.resolve("platform-packs").resolve(plan.platform))
  }

  internal fun plannedAuthoringTarget(plan: ScaffoldPlan): AuthoringTarget = AuthoringTarget(
    skillName = plan.skillName,
    packageName = plan.platform.ifBlank { "base" },
    platform = plan.platform,
    displayName = plan.displayName.ifBlank { displayNameFromSlug(plan.skillName.removePrefix("bill-")) },
    family = plan.family,
    area = plan.area,
    skillFile = plan.skillFile,
    contentFile = plan.contentFile ?: plan.skillPath.resolve(CONTENT_BODY_FILENAME),
  )

  private fun validateBaselineLayerModeSupported(index: Int, layer: CodeReviewBaselineLayer) {
    val unsupportedReason = unsupportedCompositionModeReason(layer)
    if (unsupportedReason != null) {
      failBaselineUnsupportedMode(index, layer, unsupportedReason)
    }
  }
}

private fun failBaselineLayersNotAList(): Nothing = throw InvalidScaffoldPayloadError(
  "Scaffold payload field 'baseline_layers' must be a list of baseline layer objects.",
)

private fun failBaselineLayersEmpty(): Nothing = throw InvalidScaffoldPayloadError(
  "Scaffold payload field 'baseline_layers' must contain at least one layer when provided.",
)

private fun failBaselineSelfReference(index: Int, targetLabel: String): Nothing = throw InvalidScaffoldPayloadError(
  "Scaffold payload field 'baseline_layers[$index]' self-references the new platform pack '$targetLabel'.",
)

private fun failBaselineDuplicate(targetLabel: String): Nothing = throw InvalidScaffoldPayloadError(
  "Scaffold payload field 'baseline_layers' contains duplicate layer '$targetLabel'.",
)

private fun failBaselineMissingPack(index: Int, platform: String): Nothing = throw InvalidScaffoldPayloadError(
  "Scaffold payload field 'baseline_layers[$index]' references missing platform pack '$platform'.",
)

private fun failBaselineMissingSkill(index: Int, platform: String, skill: String): Nothing =
  throw InvalidScaffoldPayloadError(
    "Scaffold payload field 'baseline_layers[$index]' references missing code-review skill " +
      "'$skill' in platform pack '$platform'.",
  )

private fun failBaselineUnsupportedMode(
  index: Int,
  layer: CodeReviewBaselineLayer,
  unsupportedReason: String,
): Nothing = throw InvalidScaffoldPayloadError(
  "Scaffold payload field 'baseline_layers[$index].mode' uses mode '${layer.mode.wireValue}' with " +
    "unsupported referenced skill '${layer.platform}/${layer.skill}'. $unsupportedReason",
)

private fun failMissingRequiredSection(skillName: String, firstIssue: String): Nothing =
  throw MissingRequiredSectionError("Horizontal skill '$skillName': $firstIssue")
