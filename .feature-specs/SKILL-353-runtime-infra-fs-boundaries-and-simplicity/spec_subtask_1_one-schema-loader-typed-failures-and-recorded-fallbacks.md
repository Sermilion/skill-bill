# SKILL-353 Subtask 1 - One schema loader, typed failures, and recorded fallbacks

Parent spec: [.feature-specs/SKILL-353-runtime-infra-fs-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-353

## Scope

Resolve F-002, F-007, and F-008 in [investigation.md](investigation.md).

Own `contracts/**` (26 files), `agentaddon/AgentAddonSchemaValidator.kt`, `scaffold/platformpack/PlatformPackSchemaValidator.kt`, the seventeen root `*ValidatorAdapter.kt` and `*ValidatorInfraAdapter.kt` files, `RuntimeValidatorProvides` in `runtime-core`, the typed-error declarations in `runtime-contracts` `skillbill.error` that the changed seams need, `launcher/review/GovernedReviewEvidenceEndpoint.kt`, `install/nativeagent/NativeAgentLinkInventoryDecode.kt`, `NativeAgentLinkInventoryReconcile.kt`, `NativeAgentLinkInventoryWrite.kt`, `NativeAgentLinkInventoryBootstrap.kt`, `install/staging/InstallStaging.kt`, `install/identity/SkillContentIdentity.kt`, `FileSystemCheckedOutBranchSource.kt`, `JdkFeatureTaskRuntimeWorkerSupervisor.kt`, `scaffold/validation/GovernedSkillDriftReport.kt`, `launcher/agentrun/AgentRunAdapters.kt`, and the other `runCatching` fallback sites the investigation census lists, plus the ambient-environment baseline recorder output and the observability section of `ARCHITECTURE.md`.

Introduce one internal loader that compiles a classpath schema once, asserts identity and contract version against the `runtime-contracts` constants, and returns ordered violations with dotted path and offending value. Reduce every validator to its schema declaration, family error factory, and family-specific message shaping, and make each implement its domain or port interface as an `@Inject class` bound in `RuntimeValidatorProvides`. Delete the fifteen forwarding adapters; fold the two adapters with real adaptation into their validators or keep them under the area's name. Delete every working-directory schema walk. Raise typed errors for non-object validator input, evidence-endpoint request decoding, link-inventory and staging decode, and agent-id parsing, and catch those types where `IllegalArgumentException` or `IllegalStateException` is caught today. Classify each listed fallback as typed failure, recorded degradation through `RuntimeDiagnostics`, or documented absence, and make the code say so; choose the diagnostics port as the single degradation channel and record any remaining `java.util.logging` site with its reason.

## Acceptance Criteria

1. Exactly one loader under `contracts/` constructs `JsonSchemaFactory` and compiles schemas; no `*SchemaValidator` declares `by lazy` schema state, `ObjectMapper()`, an identity assertion, or a three-way load `catch`. `Path.of("")` appears in no validator, `fun walkFor*` is gone, and `RECORD_ARCHITECTURE_BASELINES=1` shrinks `runtime-infra-fs-ambient-environment-baseline.txt` by at least 17 rows without adding one.
2. The fifteen forwarding adapters are deleted, each validator implements its port directly, `RuntimeValidatorProvides` binds the validators, and every existing `*SchemaValidatorTest` and `*SchemaContractVersionTest` passes unchanged in its assertions on well-formed and malformed fixtures.
3. A missing classpath schema resource raises the family's typed `Invalid*SchemaError` naming the resource; a test proves it never reads the working directory.
4. Non-object validator input, evidence-endpoint request decoding, link-inventory decode, staging manifest decode, and `AgentAddonAgentIds.parse` (or its successor) raise typed errors; the `catch (…: IllegalArgumentException|IllegalStateException)` sites at those seams catch the typed error; no module class extends `IllegalStateException`; tests at changed seams assert the typed class.
5. Every fallback site the investigation lists either throws a typed error, emits one `RuntimeDiagnostics` record with seam, expected value, and used value, or carries a recorded legitimate-absence reason; `NativeAgentLinkInventoryBootstrap` no longer substitutes `EMPTY_DIGEST` silently; the remaining `java.util.logging` sites are listed with reasons in `ARCHITECTURE.md`.
6. No new suppression, baseline row, or exemption. Files stay under 1,200 lines and 40 functions without count splits.

## Non-goals

No file moves between packages and no visibility narrowing beyond what a deleted adapter requires; that is subtask 3. No change to process lifetime, agent vocabulary, environment reads, or filesystem primitives; that is subtask 2. No change to which well-formed inputs validate or to packaged schema resources.

## Dependency notes

Depends on: none. This commit includes every call-site and binding change its signatures require so it ships alone. Subtask 2 rebases on it if it lands first; subtask 3 runs after both.

## Validation strategy

Name the regression before each test: a schema compiled twice, a family error losing its field path, a mispackaged runtime validating against a working-directory file, a malformed inventory recovering as a programming error, a fallback that hides an unreadable artifact. Run every `*SchemaValidatorTest`, the contract-version parity tests, the evidence endpoint and link-inventory suites, `runtime-core` architecture guards including the ambient-environment recorder, and the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_2_one-owner-per-process-agent-environment-and-primitive.md` through the goal runtime after this subtask settles.

## Spec Path

.feature-specs/SKILL-353-runtime-infra-fs-boundaries-and-simplicity/spec_subtask_1_one-schema-loader-typed-failures-and-recorded-fallbacks.md
