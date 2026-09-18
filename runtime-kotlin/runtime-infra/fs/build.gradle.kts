@file:Suppress("ktlint:standard:max-line-length")

plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
  id("skillbill.governed-resources")
}
dependencies {
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.snakeyaml)
  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}

data class GovernedResourceSpec(
  val taskName: String,
  val source: String,
  val destination: String,
  val owner: String,
  val sourceFromRuntimeKotlinProject: Boolean = false,
  val requireSourceIsFile: Boolean = false,
  val includeInTestProcessResources: Boolean = true,
)

private val infraFsContracts = "skillbill/infrastructure/fs/contracts"
private val sharedContracts = "skillbill/contracts"
private val jvmResources = "skillbill/infrastructure/fs/jvm"
private val reviewResources = "skillbill/review"
private val contractSource = "orchestration/contracts"

private fun governedResourceSpec(encoded: String): GovernedResourceSpec {
  val fields = encoded.split("|")
  return GovernedResourceSpec(
    taskName = fields[0],
    source = fields[1],
    destination = fields[2],
    owner = fields[3],
    sourceFromRuntimeKotlinProject = fields.getOrNull(4) == "runtime",
    requireSourceIsFile = fields.getOrNull(5) == "file",
    includeInTestProcessResources = fields.getOrNull(6) != "main-only",
  )
}

private val governedResourceSpecs =
  listOf(
    "copyAgentAddonSchema|$contractSource/agent-addon-schema.yaml|$infraFsContracts|SKILL-122: canonical agent-addon schema",
    "copyJavaGuard|build-logic/convention/src/main/resources/skill-bill-java-guard.sh|$jvmResources|SKILL-244: canonical Java guard script. The runtime image must ship the single authored guard so gate JVM resolution has a rule|runtime",
    "copySpecialistContract|orchestration/review-orchestrator/specialist-contract.md|$reviewResources|Authoritative delegated-review specialist contract|file",
    "copyReviewContextSchema|$contractSource/review-context-schema.yaml|$sharedContracts|SKILL-125: canonical review-context schema",
    "copyPlatformPackSchema|$contractSource/platform-pack-schema.yaml|$infraFsContracts|SKILL-52: canonical platform-pack schema. Run from the repo root and ensure orchestration/contracts/platform-pack-schema.yaml exists",
    "copyNativeAgentCompositionSchema|$contractSource/native-agent-composition-schema.yaml|$infraFsContracts|SKILL-52: canonical native-agent composition schema",
    "copyNativeAgentLinkInventorySchema|$contractSource/native-agent-link-inventory-schema.yaml|$infraFsContracts|SKILL-129: canonical native-agent link inventory schema",
    "copyWorkflowStateSchema|$contractSource/workflow-state-schema.yaml|$infraFsContracts|SKILL-52: canonical workflow-state schema",
    "copyInstallPlanSchema|$contractSource/install-plan-schema.yaml|$infraFsContracts|SKILL-52: canonical install-plan schema",
    "copyDecompositionManifestSchema|$contractSource/decomposition-manifest-schema.yaml|$infraFsContracts|SKILL-52: canonical decomposition manifest schema",
    "copyDecompositionManifestBundleJournalSchema|$contractSource/decomposition-manifest-bundle-journal-schema.yaml|$infraFsContracts|SKILL-248: canonical decomposition manifest bundle journal schema",
    "copyGoalObservabilityEventSchema|$contractSource/goal-observability-event-schema.yaml|$infraFsContracts|SKILL-61: canonical goal-observability event schema",
    "copyGoalProgressEventSchema|$contractSource/goal-progress-event-schema.yaml|$infraFsContracts|SKILL-64: canonical goal progress event schema",
    "copyIdeStatusSchema|$contractSource/ide-status-schema.yaml|$infraFsContracts|SKILL-148: canonical IDE status schema",
    "copyGoalSubtaskReviewStateSchema|$contractSource/goal-subtask-review-state-schema.yaml|$infraFsContracts|SKILL-119: canonical goal-subtask review-state schema",
    "copyRejectedOutputDiagnosticSchema|$contractSource/rejected-output-diagnostic-schema.yaml|$infraFsContracts|SKILL-134: canonical rejected-output diagnostic schema|main-only",
    "copyProducerOutputEvidenceSchema|$contractSource/producer-output-evidence-schema.yaml|$infraFsContracts|SKILL-152: canonical producer output evidence schema|main-only",
    "copyFeatureTaskRuntimeWorkerOwnershipSchema|$contractSource/feature-task-runtime-worker-ownership-schema.yaml|$infraFsContracts|SKILL-120: canonical feature-task runtime worker-ownership schema",
    "copyFeatureTaskExecutionIdentitySchema|$contractSource/feature-task-execution-identity-schema.yaml|$infraFsContracts|SKILL-120: canonical feature-task execution-identity schema",
    "copyFeatureTaskRuntimePhaseOutputSchema|$contractSource/feature-task-runtime-phase-output-schema.yaml|$infraFsContracts|SKILL-65: canonical feature-task-runtime phase output schema",
    "copyFeatureTaskRuntimeHandoffEnvelopeSchema|$contractSource/feature-task-runtime-handoff-envelope-schema.yaml|$infraFsContracts|SKILL-137: canonical handoff-envelope schema",
    "copyFeatureTaskRuntimePhaseLaunchBriefingSchema|$contractSource/feature-task-runtime-phase-launch-briefing-schema.yaml|$infraFsContracts|SKILL-146: canonical phase-launch-briefing schema",
    "copyFeatureTaskRuntimePhaseHandoffSchema|$contractSource/feature-task-runtime-phase-handoff-schema.yaml|$infraFsContracts|SKILL-146: canonical phase-handoff schema",
    "copyFeatureTaskRuntimePersistenceSchema|$contractSource/feature-task-runtime-persistence-schema.yaml|$infraFsContracts|SKILL-146: canonical feature-task-runtime persistence schema",
    "copyFeatureTaskRuntimeProjectionMeasurementSchema|$contractSource/feature-task-runtime-projection-measurement-schema.yaml|$infraFsContracts|SKILL-146: canonical projection-measurement schema",
    "copyFeatureTaskRuntimeSharedEvidenceProjectionSchema|$contractSource/feature-task-runtime-shared-evidence-projection-schema.yaml|$infraFsContracts|SKILL-164: canonical shared-evidence projection schema",
    "copyFeatureTaskRuntimeBuildReceiptSchema|$contractSource/feature-task-runtime-build-receipt.yaml|$infraFsContracts|SKILL-204: canonical build-receipt schema",
    "copyFeatureTaskRuntimeValidationEvidenceSchema|$contractSource/feature-task-runtime-validation-evidence-schema.yaml|$infraFsContracts|SKILL-360: canonical validation-evidence schema",
    "copyGoalPlanningPreparationSchema|$contractSource/goal-planning-preparation-schema.yaml|$infraFsContracts|SKILL-128: canonical goal planning preparation schema",
    "copyFeatureTaskRuntimePlanningProjectionsSchema|$contractSource/feature-task-runtime-planning-projections-schema.yaml|$infraFsContracts|SKILL-137: canonical planning-projections schema",
    "copyFeatureTaskRuntimeImplementationAttemptSchema|$contractSource/feature-task-runtime-implementation-attempt-schema.yaml|$infraFsContracts|SKILL-150: canonical implementation-attempt schema",
    "copyFeatureTaskRuntimeCheckpointIdentitySchema|$contractSource/feature-task-runtime-checkpoint-identity-schema.yaml|$infraFsContracts|SKILL-150: canonical checkpoint-identity schema",
    "copyFeatureTaskRuntimeQuarantineSchema|$contractSource/feature-task-runtime-quarantine-schema.yaml|$infraFsContracts|SKILL-140: canonical quarantine schema",
  ).map(::governedResourceSpec)

governedResources {
  missingSourceMessageTemplate.set("\$owner is missing at \$sourcePath.")
  governedResourceSpecs.forEach { spec ->
    entry(
      spec.taskName,
      spec.source,
      spec.destination,
      spec.owner,
      spec.sourceFromRuntimeKotlinProject,
      spec.requireSourceIsFile,
      true,
      spec.includeInTestProcessResources,
    )
  }
}

apply(from = "infra-fs-area-source-sets.gradle.kts")
