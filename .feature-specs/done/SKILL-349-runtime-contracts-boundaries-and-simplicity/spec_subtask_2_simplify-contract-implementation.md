# SKILL-349 subtask 2 - Simplify contract implementation

## Scope

Resolve F-005 through F-008. Keep the named ambient clock seam with JDK behavior. Fold WorkflowContracts into the existing application workflow mapper. Give phase-output failure vocabulary one contract owner. Remove only the unused declarations identified by the investigation after checking current callers.

Own the affected contracts files, WorkflowWireProjections and its consumers/tests, phase-output failure enum imports and conformance tests, the composition clock provider if required, and owning documentation. Preserve the current module graph.

## Acceptance Criteria

1. JvmSystemClock delegates to a live JDK clock with UTC and millisecond precision. Changing zone retains a live time source; it does not return a fixed instant. Consumers continue to accept java.time.Clock and the domain cannot acquire ambient time.
2. WorkflowContracts is removed and its one production caller builds each payload in the application mapper. Full, summary, resume, and continue output retain field order, null-mode omission, continuation mode, and the exact existing extra-field overwrite precedence.
3. Existing CLI/MCP workflow payload behavior and schema fixtures use the consolidated mapping. The change introduces no extra DTO-to-map-to-map pass or replacement forwarding object. Governing fields reference existing wire-key constants.
4. A contract-owned phase-output failure enum declares the eleven current wire tokens and coarse classifications once. Domain consumers and InvalidFeatureTaskRuntimePhaseOutputSchemaError derive behavior from it. Unknown-token handling and existing external error behavior remain compatible.
5. RecordingNullObjectDiagnostics, requireScalar, scaffold requireInt, the three unused top-level InstallPlanContract convenience functions, and failureWireByValueOrNull are removed if they remain unused. No replacement registry, callback binding, or shim is added. The used InstallPlanContract wrapper and required throwing decoders remain.
6. The existing conformance coverage still proves wire uniqueness and unknown-token rejection. Focused observable tests protect payload mapping and clock behavior. No tests are added merely to assert that unused declarations were deleted.
7. Documentation reflects the resulting owners and the earlier SKILL-233 diagnostic-deletion decision. No architecture baseline or suppression expands.

## Non-goals

No blanket error hierarchy redesign, new clock port, new module, compatibility-alias layer without a real consumer, or claimed performance gain without measurement. No deletion of required schema/version declarations, typed errors, useful DTOs, or legitimate single-adapter ports.

## Dependency notes

No hard dependency on subtask 1. The goal schedules this second for a straightforward branch history. It changes separate responsibilities and can ship independently. Reconcile imports and helper deletions with subtask 1 rather than restoring previous code.

## Validation strategy

Exercise workflow output through the application mapper and relevant CLI/MCP tests, including mode omission and extra-field precedence. Check the clock through its public Clock behavior and preserve millisecond semantics; avoid sleep-based timing assertions. Prefer delegating to the JDK over maintaining a custom time source for testing. Run existing failure-wire conformance tests and affected compilation after moving the enum. Run the module/dependency and ambient-effect architecture guards without expanding their allowances. Run the governed implementation quality gate and bill-unit-test-value-check for changed tests.

## Next path

Complete the prepared goal through the runtime's normal review, validation, history, and commit phases. No additional implementation subtask is planned.
