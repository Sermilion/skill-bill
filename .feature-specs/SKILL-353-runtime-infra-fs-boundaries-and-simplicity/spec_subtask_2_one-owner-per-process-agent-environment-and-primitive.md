# SKILL-353 Subtask 2 - One owner per process, agent, environment read, and primitive

Parent spec: [.feature-specs/SKILL-353-runtime-infra-fs-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-353

## Scope

Resolve F-003, F-004, F-005, and F-006 in [investigation.md](investigation.md).

Own `GitProcessInvocation.kt`, `GitProcessCommands.kt`, `GitWorkflowSelectedDiff.kt`, `ImmutableReviewFile.kt`, `FileSystemDiffResolver.kt`, `GhGoalPullRequestPort.kt`, `InstallerProcessAdapter.kt`, `jvm/GateJvmResolver.kt`, `validation/FileSystemValidationGateRunner.kt`, `scaffold/pointer/GeneratedArtifactGuardReport.kt`, `GitProcessLifetimeBehaviorTest` and the tests of the migrated callers; the agent vocabulary declarations `InstallAgent` and `AgentSymlinkProvider` in `runtime-domain`, `nativeagent/rendering/NativeAgentProvider.kt`, `agentaddon/AgentAddonAgentIds.kt`, the fifteen `when (provider)` sites and the two `parseEmbeddedLogicalName` copies; the eighteen files reading `"user.home"` and the ambient-read sites in `install/plan/InstallPrimitives.kt`, `install/runtime/InstallOperationsPaths.kt`, `install/runtime/InstallOperations.kt`, `nativeagent/support/AgentHomePaths.kt`, `install/support/*ConfigPaths.kt`, and `launcher/mcp/McpRegistrationOperations.kt`; and the atomic-write, digest, containment, discovery, and rollback helpers across `install/**`, `scaffold/pointer`, `nativeagent/rendering`, `launcher/process/AtomicFileWrites.kt`, and the root stores.

Route the four bespoke git sites through `invokeGitProcess`, adding redirect-to-file capture only if `ImmutableReviewFile` needs it after measurement, and give `gh`, the JVM guard, the gate runner, and the installer one bounded runner or the same invocation with an executable parameter; delete the private lifetime in `GitWorkflowSelectedDiff` and give `GeneratedArtifactGuardReport` a deadline and teardown. Declare the supported agents once as a domain enum with `wireValue` and `fromWire` carrying the per-agent facts the `when` sites compute today, make the three existing enums that enum or typed views of it, delete `AgentAddonAgentIds`, and keep one `parseEmbeddedLogicalName`. Resolve `userHome` and `environment` from the injected `EnvironmentContext` or `HostPlatformPort` at adapter construction and pass resolved paths down. Give the module one internal owner each for atomic write, atomic move, directory replacement, SHA-256 digest, and path containment; make the per-area rollback owners use them; merge `discover*` walkers where two walk one tree with one predicate.

## Acceptance Criteria

1. Every `ProcessBuilder` construction in the module is inside `invokeGitProcess`, `ProcessRunLifetime`, or one bounded runner shared with the installer adapter; `GitWorkflowSelectedDiff` contains no deadline arithmetic, drain thread, or `destroy` call; `GeneratedArtifactGuardReport` runs under a deadline with teardown; `GitProcessLifetimeBehaviorTest` or a sibling asserts for each migrated caller that a timed-out or interrupted child is gone and no capture is published as settled.
2. One domain enum declares the supported agents; `InstallAgent`, `AgentSymlinkProvider`, and `NativeAgentProvider` are that enum or typed views of it; `AgentAddonAgentIds` is deleted; each former `when (provider)` site reads its fact from the declaration; `parseEmbeddedLogicalName` exists once; agent literals in production sources are limited to the enum's `wireValue` declarations. Install plans, MCP config paths, and native-agent link targets are byte-identical to the current fixtures.
3. `"user.home"` is read in at most one production file; `System.getenv` and `System.getProperty` sites in the owned files resolve through the injected context or host port; `install/plan/InstallPrimitives.kt` receives config roots as facts; the ambient-environment recorder shows the baseline shrank and no row was added.
4. Atomic write, atomic move, directory replacement, SHA-256 digest, and path containment each have exactly one internal owner; the eight named atomic helpers and the eight `sha256` functions are gone; rollback owners in install apply, scaffold staging, scaffold install link, skill remove, and the evidence endpoint call the shared primitives; promoted files, symlink targets, digests, and inventory bytes match the pre-change fixtures.
5. No new suppression, baseline row, or exemption. Files stay under 1,200 lines and 40 functions without count splits.

## Non-goals

No file moves between packages or visibility narrowing; that is subtask 3. No change to validators, failure identity at decode seams, or the degradation channel; that is subtask 1. No change to git argv, process deadlines, capture caps, or the per-provider command builders and output decoders.

## Dependency notes

Depends on: none. This commit includes every call-site change its signatures require so it ships alone. Subtask 1 rebases on it if it lands first; subtask 3 runs after both.

## Validation strategy

Name the regression before each test: a hung `git ls-files` blocking the scaffold guard, a diff child surviving interruption, an agent added in one enum and missed in another, a config root chosen from the wrong home, a partially written file promoted, a digest computed over the wrong bytes. Run the git lifetime, installer, launcher, install, native-agent, skill-remove, and scaffold suites plus `runtime-core` architecture guards including the ambient-environment recorder and the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_3_cluster-the-root-move-the-layer-proof-and-narrow-the-surface.md` through the goal runtime after this subtask and subtask 1 settle.

## Spec Path

.feature-specs/SKILL-353-runtime-infra-fs-boundaries-and-simplicity/spec_subtask_2_one-owner-per-process-agent-environment-and-primitive.md
