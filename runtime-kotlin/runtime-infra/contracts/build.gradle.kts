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
  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(testFixtures(project(":runtime-infra:host")))
  testImplementation(project(":runtime-infra:workflow"))
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

private val infraContractsResourceRoot = "skillbill/infrastructure/contracts"
private val sharedContracts = "skillbill/contracts"
private val jvmResources = "skillbill/infrastructure/host/jvm"
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
    includeInTestProcessResources = fields.drop(4).none { it == "main-only" },
  )
}

private val governedResourceSpecs =
  listOf(
    "copyAgentAddonSchema|$contractSource/agent-addon-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-122: canonical agent-addon schema",
    "copyReviewContextSchema|$contractSource/review-context-schema.yaml|" +
      "$sharedContracts|SKILL-125: canonical review-context schema",
    "copyPlatformPackSchema|$contractSource/platform-pack-schema.yaml|" +
      "$infraContractsResourceRoot|" +
      "SKILL-52: canonical platform-pack schema. Run from the repo root and ensure " +
      "orchestration/contracts/platform-pack-schema.yaml exists",
    "copyNativeAgentCompositionSchema|" +
      "$contractSource/native-agent-composition-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-52: canonical native-agent composition schema",
    "copyNativeAgentLinkInventorySchema|" +
      "$contractSource/native-agent-link-inventory-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-129: canonical native-agent link inventory schema",
    "copyWorkflowStateSchema|$contractSource/workflow-state-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-52: canonical workflow-state schema",
    "copyInstallPlanSchema|$contractSource/install-plan-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-52: canonical install-plan schema",
    "copyDecompositionManifestSchema|" +
      "$contractSource/decomposition-manifest-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-52: canonical decomposition manifest schema",
    "copyDecompositionManifestBundleJournalSchema|" +
      "$contractSource/decomposition-manifest-bundle-journal-schema.yaml|" +
      "$infraContractsResourceRoot|" +
      "SKILL-248: canonical decomposition manifest bundle journal schema",
    "copyGoalObservabilityEventSchema|" +
      "$contractSource/goal-observability-event-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-61: canonical goal-observability event schema",
    "copyGoalProgressEventSchema|$contractSource/goal-progress-event-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-64: canonical goal progress event schema",
    "copyIdeStatusSchema|$contractSource/ide-status-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-148: canonical IDE status schema",
    "copyGoalSubtaskReviewStateSchema|" +
      "$contractSource/goal-subtask-review-state-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-119: canonical goal-subtask review-state schema",
    "copyRejectedOutputDiagnosticSchema|" +
      "$contractSource/rejected-output-diagnostic-schema.yaml|" +
      "$infraContractsResourceRoot|" +
      "SKILL-134: canonical rejected-output diagnostic schema|main-only",
    "copyProducerOutputEvidenceSchema|" +
      "$contractSource/producer-output-evidence-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-152: canonical producer output evidence schema|main-only",
    "copyFeatureTaskRuntimeWorkerOwnershipSchema|" +
      "$contractSource/feature-task-runtime-worker-ownership-schema.yaml|" +
      "$infraContractsResourceRoot|" +
      "SKILL-120: canonical feature-task runtime worker-ownership schema",
    "copyFeatureTaskExecutionIdentitySchema|" +
      "$contractSource/feature-task-execution-identity-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-120: canonical feature-task execution-identity schema",
    "copyFeatureTaskRuntimePhaseOutputSchema|" +
      "$contractSource/feature-task-runtime-phase-output-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-65: canonical feature-task-runtime phase output schema",
    "copyFeatureTaskRuntimeHandoffEnvelopeSchema|" +
      "$contractSource/feature-task-runtime-handoff-envelope-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-137: canonical handoff-envelope schema",
    "copyFeatureTaskRuntimePhaseLaunchBriefingSchema|" +
      "$contractSource/feature-task-runtime-phase-launch-briefing-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-146: canonical phase-launch-briefing schema",
    "copyFeatureTaskRuntimePhaseHandoffSchema|" +
      "$contractSource/feature-task-runtime-phase-handoff-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-146: canonical phase-handoff schema",
    "copyFeatureTaskRuntimePersistenceSchema|" +
      "$contractSource/feature-task-runtime-persistence-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-146: canonical feature-task-runtime persistence schema",
    "copyFeatureTaskRuntimeProjectionMeasurementSchema|" +
      "$contractSource/feature-task-runtime-projection-measurement-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-146: canonical projection-measurement schema",
    "copyFeatureTaskRuntimeSharedEvidenceProjectionSchema|" +
      "$contractSource/feature-task-runtime-shared-evidence-projection-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-164: canonical shared-evidence projection schema",
    "copyFeatureTaskRuntimeBuildReceiptSchema|" +
      "$contractSource/feature-task-runtime-build-receipt.yaml|" +
      "$infraContractsResourceRoot|SKILL-204: canonical build-receipt schema",
    "copyFeatureTaskRuntimeValidationEvidenceSchema|" +
      "$contractSource/feature-task-runtime-validation-evidence-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-360: canonical validation-evidence schema",
    "copyGoalPlanningPreparationSchema|" +
      "$contractSource/goal-planning-preparation-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-128: canonical goal planning preparation schema",
    "copyFeatureTaskRuntimePlanningProjectionsSchema|" +
      "$contractSource/feature-task-runtime-planning-projections-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-137: canonical planning-projections schema",
    "copyFeatureTaskRuntimeImplementationAttemptSchema|" +
      "$contractSource/feature-task-runtime-implementation-attempt-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-150: canonical implementation-attempt schema",
    "copyFeatureTaskRuntimeCheckpointIdentitySchema|" +
      "$contractSource/feature-task-runtime-checkpoint-identity-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-150: canonical checkpoint-identity schema",
    "copyFeatureTaskRuntimeQuarantineSchema|" +
      "$contractSource/feature-task-runtime-quarantine-schema.yaml|" +
      "$infraContractsResourceRoot|SKILL-140: canonical quarantine schema",
  ).map(::governedResourceSpec)

governedResources {
  missingSourceMessageTemplate.set("\$owner is missing at \$sourcePath.")
  governedResourceSpecs.forEach { spec ->
    entry(
      GovernedResourceEntry(
        taskName = spec.taskName,
        repoRelativeSource = spec.source,
        destinationDir = spec.destination,
        owner = spec.owner,
        sourceFromRuntimeKotlinProject = spec.sourceFromRuntimeKotlinProject,
        requireSourceIsFile = spec.requireSourceIsFile,
        includeInMainProcessResources = true,
        includeInTestProcessResources = spec.includeInTestProcessResources,
      ),
    )
  }
}
