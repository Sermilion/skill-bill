import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File

plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.snakeyaml)
  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(project(":runtime-application"))
  testImplementation(project(":runtime-engine"))
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileTestKotlin") {
  dependsOn(":runtime-application:compileKotlin")
  friendPaths.from(
    rootProject.layout.projectDirectory.dir("runtime-application/build/classes/kotlin/main"),
  )
}

enum class GovernedResourceDestination(val dirSuffix: String) {
  INFRA_FS_CONTRACTS("skillbill/infrastructure/fs/contracts"),
  SHARED_CONTRACTS("skillbill/contracts"),
  JVM("skillbill/infrastructure/fs/jvm"),
  REVIEW("skillbill/review"),
}

data class GovernedResourceCopy(
  val taskName: String,
  val repoRelativeSource: String,
  val destination: GovernedResourceDestination,
  val missingSourceMessage: String,
  val sourceFromRuntimeKotlinProject: Boolean = false,
  val requireSourceIsFile: Boolean = false,
  val includeInMainProcessResources: Boolean = true,
  val includeInTestProcessResources: Boolean = true,
)

private val repoRootDir = rootProject.projectDir.parentFile
private val runtimeKotlinProjectDir = rootProject.projectDir

private fun resolveCanonicalSource(spec: GovernedResourceCopy): File {
  val base = if (spec.sourceFromRuntimeKotlinProject) runtimeKotlinProjectDir else repoRootDir
  return base.resolve(spec.repoRelativeSource)
}

private fun registerGovernedCopy(spec: GovernedResourceCopy): TaskProvider<Copy> {
  val sourceFile = resolveCanonicalSource(spec)
  val sourcePath = sourceFile.absolutePath
  val missingSourceMessageTemplate = spec.missingSourceMessage
  val requireSourceIsFile = spec.requireSourceIsFile
  val validateSource =
    tasks.register("validate${spec.taskName.replaceFirstChar { it.uppercase() }}Source") {
      doLast {
        val failureMessage =
          missingSourceMessageTemplate
            .replace("\$schemaPath", sourcePath)
            .replace("\$guardPath", sourcePath)
            .replace("\$contractPath", sourcePath)
        if (requireSourceIsFile) {
          require(sourceFile.isFile) { failureMessage }
        } else {
          require(sourceFile.exists()) { failureMessage }
        }
      }
    }
  return tasks.register<Copy>(spec.taskName) {
    dependsOn(validateSource)
    from(sourcePath)
    into(
      layout.buildDirectory.dir(
        "generated/skillbill-infrastructure-fs/${spec.destination.dirSuffix}",
      ),
    )
    inputs.file(sourcePath)
  }
}

private val governedResourceCopies =
  listOf(
    GovernedResourceCopy(
      taskName = "copyAgentAddonSchema",
      repoRelativeSource = "orchestration/contracts/agent-addon-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage = "SKILL-122: canonical agent-addon schema is missing at \$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyJavaGuard",
      repoRelativeSource = "build-logic/convention/src/main/resources/skill-bill-java-guard.sh",
      destination = GovernedResourceDestination.JVM,
      missingSourceMessage =
        "SKILL-244: canonical Java guard script is missing at \$guardPath. " +
          "The runtime image must ship the single authored guard " +
          "so gate JVM resolution has a rule.",
      sourceFromRuntimeKotlinProject = true,
    ),
    GovernedResourceCopy(
      taskName = "copySpecialistContract",
      repoRelativeSource = "orchestration/review-orchestrator/specialist-contract.md",
      destination = GovernedResourceDestination.REVIEW,
      missingSourceMessage =
        "Authoritative delegated-review specialist contract is missing at " +
          "\$contractPath.",
      requireSourceIsFile = true,
    ),
    GovernedResourceCopy(
      taskName = "copyReviewContextSchema",
      repoRelativeSource = "orchestration/contracts/review-context-schema.yaml",
      destination = GovernedResourceDestination.SHARED_CONTRACTS,
      missingSourceMessage =
        "SKILL-125: canonical review-context schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyPlatformPackSchema",
      repoRelativeSource = "orchestration/contracts/" + "platform-pack-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-52: canonical platform-pack schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure " +
          "`orchestration/contracts/platform-pack-schema.yaml` exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyNativeAgentCompositionSchema",
      repoRelativeSource = "orchestration/contracts/native-agent-composition-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-52: canonical native-agent composition schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyNativeAgentLinkInventorySchema",
      repoRelativeSource = "orchestration/contracts/native-agent-link-inventory-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-129: canonical native-agent link inventory schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyWorkflowStateSchema",
      repoRelativeSource = "orchestration/contracts/workflow-state-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-52: canonical workflow-state schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyInstallPlanSchema",
      repoRelativeSource = "orchestration/contracts/install-plan-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-52: canonical install-plan schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyDecompositionManifestSchema",
      repoRelativeSource = "orchestration/contracts/decomposition-manifest-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-52: canonical decomposition manifest schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyGoalObservabilityEventSchema",
      repoRelativeSource = "orchestration/contracts/goal-observability-event-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-61: canonical goal-observability event schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyGoalProgressEventSchema",
      repoRelativeSource = "orchestration/contracts/goal-progress-event-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-64: canonical goal progress event schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyIdeStatusSchema",
      repoRelativeSource = "orchestration/contracts/ide-status-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-148: canonical IDE status schema is missing at \$schemaPath. " +
          "Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyGoalSubtaskReviewStateSchema",
      repoRelativeSource = "orchestration/contracts/goal-subtask-review-state-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-119: canonical goal-subtask review-state schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyRejectedOutputDiagnosticSchema",
      repoRelativeSource = "orchestration/contracts/rejected-output-diagnostic-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-134: canonical rejected-output diagnostic schema is missing at " +
          "\$schemaPath.",
      includeInTestProcessResources = false,
    ),
    GovernedResourceCopy(
      taskName = "copyProducerOutputEvidenceSchema",
      repoRelativeSource = "orchestration/contracts/producer-output-evidence-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-152: canonical producer output evidence schema is missing at " +
          "\$schemaPath.",
      includeInTestProcessResources = false,
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeWorkerOwnershipSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-worker-ownership-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-120: canonical feature-task runtime worker-ownership schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskExecutionIdentitySchema",
      repoRelativeSource = "orchestration/contracts/feature-task-execution-identity-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-120: canonical feature-task execution-identity schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimePhaseOutputSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-phase-output-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-65: canonical feature-task-runtime phase output schema is missing at " +
          "\$schemaPath. Run from the repo root and ensure the schema file exists.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeHandoffEnvelopeSchema",
      repoRelativeSource =
        "orchestration/contracts/" +
          "feature-task-runtime-handoff-envelope-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-137: canonical handoff-envelope schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimePhaseLaunchBriefingSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-phase-launch-briefing-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-146: canonical phase-launch-briefing schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimePhaseHandoffSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-phase-handoff-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-146: canonical phase-handoff schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimePersistenceSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-persistence-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-146: canonical feature-task-runtime persistence schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeProjectionMeasurementSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-projection-measurement-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-146: canonical projection-measurement schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeSharedEvidenceProjectionSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-shared-evidence-projection-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-164: canonical shared-evidence projection schema is missing at " +
          "orchestration/contracts/feature-task-runtime-shared-evidence-projection-schema.yaml " +
          "(resolved path: \$schemaPath).",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeBuildReceiptSchema",
      repoRelativeSource = "orchestration/contracts/feature-task-runtime-build-receipt.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-204: canonical build-receipt schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeValidationEvidenceSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-validation-evidence-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-360: canonical validation-evidence schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyGoalPlanningPreparationSchema",
      repoRelativeSource = "orchestration/contracts/goal-planning-preparation-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-128: canonical goal planning preparation schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimePlanningProjectionsSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-planning-projections-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-137: canonical planning-projections schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeImplementationAttemptSchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-implementation-attempt-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-150: canonical implementation-attempt schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeCheckpointIdentitySchema",
      repoRelativeSource =
        "orchestration/contracts/" + "feature-task-runtime-checkpoint-identity-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage =
        "SKILL-150: canonical checkpoint-identity schema is missing at " +
          "\$schemaPath.",
    ),
    GovernedResourceCopy(
      taskName = "copyFeatureTaskRuntimeQuarantineSchema",
      repoRelativeSource = "orchestration/contracts/feature-task-runtime-quarantine-schema.yaml",
      destination = GovernedResourceDestination.INFRA_FS_CONTRACTS,
      missingSourceMessage = "SKILL-140: canonical quarantine schema is missing at \$schemaPath.",
    ),
  )

private val governedCopyTasks = governedResourceCopies.map { registerGovernedCopy(it) }

sourceSets.named("main") {
  resources.srcDir(layout.buildDirectory.dir("generated/skillbill-infrastructure-fs"))
}

tasks.named("processResources") {
  governedResourceCopies.zip(governedCopyTasks).forEach { (spec, task) ->
    if (spec.includeInMainProcessResources) {
      dependsOn(task)
    }
  }
}

tasks.named("processTestResources") {
  governedResourceCopies.zip(governedCopyTasks).forEach { (spec, task) ->
    if (spec.includeInTestProcessResources) {
      dependsOn(task)
    }
  }
}

tasks.register<JavaExec>("platformPackSubstanceReport") {
  group = "verification"
  description = "Emit the maintained platform-pack substance report in text or JSON form."
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set(
    "skillbill.infrastructure.fs.scaffold.substance.PlatformPackSubstanceReportMainKt",
  )
  args(
    "--repo-root=${providers.gradleProperty(
      "repoRoot",
    ).orElse(rootProject.projectDir.parentFile.absolutePath).get()}",
    "--format=${providers.gradleProperty("reportFormat").orElse("text").get()}",
  )
}

val infraFsAreaLayerOrder =
  listOf(
    "Jvm",
    "Contracts",
    "AgentAddon",
    "NativeAgent",
    "Scaffold",
    "Install",
    "Launcher",
    "Infrastructure",
    "GoalPlanning",
    "SkillRemove",
  )

val infraFsAreaSourceDirs =
  mapOf(
    "Jvm" to "skillbill/infrastructure/fs/jvm",
    "Infrastructure" to "skillbill/infrastructure/fs",
    "Install" to "skillbill/infrastructure/fs/install",
    "Launcher" to "skillbill/infrastructure/fs/launcher",
    "NativeAgent" to "skillbill/infrastructure/fs/nativeagent",
    "Scaffold" to "skillbill/infrastructure/fs/scaffold",
    "AgentAddon" to "skillbill/infrastructure/fs/agentaddon",
    "Contracts" to "skillbill/infrastructure/fs/contracts",
    "GoalPlanning" to "skillbill/infrastructure/fs/goalplanning",
    "SkillRemove" to "skillbill/infrastructure/fs/skillremove",
  )

val javaPlugin = extensions.getByType(JavaPluginExtension::class.java)
val kotlinPlugin = extensions.getByType(KotlinJvmProjectExtension::class.java)
val mainSourceSet = javaPlugin.sourceSets.getByName("main")
val infraFsAreaSourceSets =
  infraFsAreaLayerOrder.associateWith { areaName ->
    val sourceSetName = "infraFs${areaName}Area"
    val areaSourceSet = javaPlugin.sourceSets.create(sourceSetName)
    areaSourceSet.java.srcDir(
      layout.projectDirectory.dir("src/main/kotlin/${infraFsAreaSourceDirs.getValue(areaName)}"),
    )
    configurations.getByName(areaSourceSet.implementationConfigurationName).extendsFrom(
      configurations.getByName(mainSourceSet.implementationConfigurationName),
    )
    configurations.getByName(areaSourceSet.compileOnlyConfigurationName).extendsFrom(
      configurations.getByName(mainSourceSet.compileOnlyConfigurationName),
    )
    areaSourceSet
  }

infraFsAreaLayerOrder.forEachIndexed { areaIndex, areaName ->
  val areaSourceSet = infraFsAreaSourceSets.getValue(areaName)
  infraFsAreaLayerOrder.take(areaIndex).forEach { lowerAreaName ->
    val lowerSourceSet = infraFsAreaSourceSets.getValue(lowerAreaName)
    dependencies.add(areaSourceSet.implementationConfigurationName, lowerSourceSet.output)
  }
  val compileTaskName = "compileInfraFs${areaName}AreaKotlin"
  tasks.named<KotlinJvmCompile>(compileTaskName) {
    infraFsAreaLayerOrder.take(areaIndex).forEach { lowerAreaName ->
      val lowerSourceSet = infraFsAreaSourceSets.getValue(lowerAreaName)
      friendPaths.from(lowerSourceSet.output.classesDirs)
    }
  }
}

val verifyInfraFsAreaCompileTasks =
  infraFsAreaLayerOrder.mapIndexed { areaIndex, areaName ->
    val compileTaskName = "compileInfraFs${areaName}AreaKotlin"
    tasks.named(compileTaskName) {
      infraFsAreaLayerOrder.take(areaIndex).forEach { lowerAreaName ->
        dependsOn("compileInfraFs${lowerAreaName}AreaKotlin")
      }
      dependsOn("processResources")
    }
  }

tasks.register("verifyInfraFsAreaCompile") {
  group = "verification"
  description = "Compile each runtime-infra-fs area without sibling areas on the classpath."
  verifyInfraFsAreaCompileTasks.forEach { dependsOn(it) }
}

tasks.named("check") {
  dependsOn("verifyInfraFsAreaCompile")
}
