# SKILL-350 Subtask 1 - Stabilize component inputs and launcher substitution

Parent spec: [.feature-specs/SKILL-350-runtime-core-composition-and-architecture-guards/spec.md](spec.md)
Issue key: SKILL-350

## Scope

Address F-001 and F-002 in [investigation.md](investigation.md).

RuntimeComponent.runtimeContext currently invokes RuntimeBootstrapBindings for each graph resolution. The generated CLI and MCP components also call this provider directly. Give the component one resolved context, with a clearly defined first-resolution or construction boundary. Reuse the existing component scope or a component-owned immutable value. Verify the generated child graphs rather than assuming an annotation alone controls direct calls.

The same resolved context must own environment facts and requester selection. Preserve supplied objects. Do not singleton-scope stateless use cases or cache live telemetry settings as a workaround. A second component may take a new ambient snapshot.

Wire the existing ExecutableLookup into the injected FileSystemAgentRunLauncher constructor. The internal default path currently constructs PathExecutableLookup independently. Keep the default host PATH behavior when no override exists, and keep the explicit AgentRunLauncher override working. No new launcher abstraction is needed.

Primary ownership is RuntimeComponent.kt, RuntimeBootstrapBindings.kt, RuntimeGoalRunnerLaunchProvides.kt, and FileSystemAgentRunLauncher.kt. Add a few generated-component behavior tests beside RuntimeComponentScopedIdentityTest and AgentRunServiceRuntimeComponentTest, plus affected adapter coverage if needed. Update the owning architecture documentation. The existing launcher test assumes Junie is absent on the host PATH, so make its missing-executable setup explicit as part of this change.

## Acceptance Criteria

1. After a component first resolves its environment, changing an ambient home value or repository path resolution cannot make a later service in that component use different invocation facts. A new component can resolve the changed facts.
2. Repeated service resolution and generated CLI or MCP parent access share the component-selected requester. With a non-default connect timeout, they do not create additional independent HttpClient instances. A supplied requester is preserved.
3. The database factory and telemetry configuration resolve from the same component environment. The regression that left the database under home A while reading config under home B is covered.
4. The injected ExecutableLookup reaches the default filesystem launcher. A false result yields the existing unavailable-launch outcome and leaves a controlled executable marker absent, even with the fixture executable on PATH.
5. Existing default discovery, supported launcher alias resolution, explicit AgentRunLauncher overrides, and supplied workflow git operations remain supported. Test fixtures cannot start an installed agent CLI.
6. Stateful scoped collaborators retain same-component identity and separate-component isolation. No process-global context cache or blanket singleton conversion is introduced.

## Non-Goals

- Architecture scanner and module inventory changes, which belong to subtask 2.
- Changing process execution contracts, fallback agent policy, telemetry wire shapes, or database transaction semantics.
- A new HTTP resource framework or tests that rely on wall-clock performance thresholds.

## Dependency Notes

Depends on: none
No dependency on subtask 2. The production fixes and their behavior tests form one independently shippable commit. Recheck SKILL-349 changes before implementation.

## Validation Strategy

Run generated-component regression tests for the split-home scenario, requester reuse, supplied object identity, and separate-component configuration. Verify generated CLI and MCP access paths, because they directly call parent provider methods. Exercise launcher refusal with a temporary executable fixture and assert no marker or process start. Run existing launcher alias and missing-executable coverage, runtime-core tests, affected CLI/MCP checks, and the governed quality gate for the phase. Apply bill-unit-test-value-check to changed tests.

## Next Path

Continue to subtask 2 through skill-bill goal SKILL-350.

## Spec Path

.feature-specs/SKILL-350-runtime-core-composition-and-architecture-guards/spec_subtask_1_stabilize-component-inputs-and-launcher-substitution.md
