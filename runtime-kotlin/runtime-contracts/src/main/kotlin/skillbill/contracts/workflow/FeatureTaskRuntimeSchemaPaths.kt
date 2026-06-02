package skillbill.contracts.workflow

/**
 * SKILL-65 Subtask 1: pinned runtime-side mirror of the canonical
 * feature-task-runtime phase-output schema's `contract_version`. The parity
 * test fails the build if this constant and the schema's
 * `properties.contract_version.const` diverge. To bump the contract, edit
 * BOTH sites in the same change.
 */
const val FEATURE_TASK_RUNTIME_CONTRACT_VERSION: String = "0.1"

/**
 * Single source of truth for where the canonical feature-task-runtime
 * per-phase output schema lives. The Gradle copy task in
 * `runtime-infra-fs/build.gradle.kts` must mirror these values because
 * Gradle's Kotlin DSL cannot import runtime constants directly.
 */
object FeatureTaskRuntimePhaseOutputSchemaPaths {
  /** Repo-relative path to the canonical schema YAML on disk. */
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/feature-task-runtime-phase-output-schema.yaml"

  /** Classpath resource path where runtime-infra-fs bundles the schema for runtime loads. */
  const val CLASSPATH_RESOURCE: String =
    "skillbill/contracts/feature-task-runtime-phase-output-schema.yaml"

  /**
   * Expected value of the canonical schema's `$id`. The validator
   * asserts this value on load so stale or shadowed resources fail
   * loudly at the parse seam.
   */
  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/feature-task-runtime-phase-output-schema.yaml"
}
