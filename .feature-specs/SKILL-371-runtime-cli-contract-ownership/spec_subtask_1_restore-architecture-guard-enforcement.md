# SKILL-371 subtask 1 - Restore architecture guard enforcement

Parent: [spec.md](spec.md). Finding: F-001 in [investigation.md](investigation.md).

## Scope

Make every runtime-core architecture test that names a module source root read files from that root. Then fix, in this commit, the violations those tests report.

- `ArchitectureScanSupport.runtimeRoot` resolves to the repository root. Tests that pass bare module paths (`"runtime-cli/src/main/kotlin"`) or filter `relativePath` on `"runtime-application/src/main/kotlin/"` scan nothing. Resolve module paths through `RuntimeModuleCatalog.runtimeKotlinModuleDirectory`, or through one root that is `runtime-kotlin/`. Pick one convention, apply it everywhere, and delete the other.
- Walkers that return empty on a missing root must fail instead: `RuntimeArchitectureTestSupport.engineInboundApiViolations`, `mainPackageRootsForModule`, `ArchitectureScanSupport` file walkers, and the private `kotlinFilesUnder` copies in `ImplementationOwnershipArchitectureTest`, `InstallPolicyOwnershipArchitectureTest`, and `RuntimeEnforcementHardeningArchitectureTest`. Path-presence checks such as "retired adapters stay absent" must first assert that their parent source root exists.
- Known affected tests: `RuntimeEngineInboundApiTest`, `RuntimeApplicationSharedEngineEdgeArchitectureTest` (in `RuntimeEngineBoundaryArchitectureTest.kt`), `RuntimeRawMapArchitectureTest` (inner-layer raw maps), and `RuntimeArchitectureTest` (three domain filters). Recensus after the fix. Any other test that was vacuous for the same reason is in scope, with two exceptions that SKILL-373 subtask 2 deletes and this subtask leaves as they are: the `ImplementationOwnershipArchitectureTest` `forbiddenSourcePackages` checks and `RuntimeLayerBoundaryArchitectureTest` "retired review and telemetry adapters stay absent".
- Fix reported violations:
  - `ReviewAccountingSummary.toBoundedPayload` (runtime-domain) and `toReviewAccountingPayload` (runtime-application) must not be public raw-map functions. Keep one serializer, with non-public visibility or a typed contract, at the owning boundary. Its consumers are `CodeReviewCommand` output, the SQLite accounting persistence path, and `ReviewAccountingDurableRedactionTest`. They keep the same rendered and persisted bytes. `runtime-infra/sqlite` holds a third copy, `ReviewAccountingBoundedSerialization.encodeReviewAccountingBoundedPayload`, plus a public same-name `toBoundedPayload()` extension in `ReviewAccountingWireExtensions.kt`. SKILL-376 subtask 1 deletes both and persists through the serializer this subtask keeps, so do not edit them here beyond keeping them compiling.
  - Refresh `PINNED_ENGINE_INBOUND_API_TYPES` to the engine's real inbound surface after SKILL-361's package nesting (`goalrunner.status`, `goalrunner.preflight`). Experiment imports are gone once SKILL-378 subtask 1 deletes experiment support. If any remain, pin nothing for them and stop, because that means the dependency below was not met. Do not pin a type only to make the test pass.
  - runtime-ports public raw-map signatures that the restored filter reports. After SKILL-378 subtask 1 deletes the experiment port files, these remain: `IdeStatusValidator.toWireMap`, `IdeStatusProblemDetails.from` / `asWireEntries`, and `ReviewFinishedTelemetryPayload` (five signatures in three files). For each one, move the wire map to the adapter-side serializer or replace it with a typed model. Wire bytes stay identical.
  - Any further violation the restored guards report.
- Out of scope: `PortsDeclarationArchitectureTest` and `PortNullObjectAbsenceArchitectureTest`. They are also vacuous, but through their own walker, which doubles `runtime-kotlin/` and drops `runtime-infra:<name>` ids. SKILL-377 subtask 3 repairs them using the scan-root convention this subtask chooses. Document that convention in `ARCHITECTURE.md`.
- Update the enforcement-status text in `runtime-kotlin/ARCHITECTURE.md` so it states which scanners are verified to read files.

## Acceptance Criteria

1. Every architecture scanner that takes a module source root fails when the root does not exist, and each affected test asserts that it read at least one Kotlin file per named root.
2. A synthetic unpinned engine reference placed in runtime-cli main source, and a synthetic public raw-map function placed in runtime-application main source, each fail their scanner through its real entry point (fixture-based, not by adding production files).
3. `RuntimeRawMapArchitectureTest` and `RuntimeArchitectureTest` path filters match files under `runtime-kotlin/<module>/src/main/kotlin/`, and the raw-map test reports zero violations on the tree because the two accounting functions no longer expose a public raw map.
4. `RuntimeEngineInboundApiTest` passes against the current runtime-application, runtime-cli, and runtime-mcp sources with a pinned list whose every entry names an existing engine type.
5. No architecture baseline file, exemption list, or pinned list gains an entry whose only purpose is to tolerate a violation found by this subtask.
6. `runtime-kotlin/ARCHITECTURE.md` names the scan-root convention and states that missing roots fail.

## Non-Goals

- Adding new architecture rules or scanner classes.
- Changing CLI output, exit codes, or command behavior, apart from the experiments rendering change the engine pin requires.
- Consolidating the `runtimeRoot` walkers that already resolve `runtime-kotlin/` correctly, unless the chosen convention replaces them.

## Dependency Notes

Runs first within this bundle. It depends on two other bundles having landed: SKILL-370, which moves application and engine packages that the pinned list names, and SKILL-378 subtask 1, which deletes experiment support. SKILL-373 subtask 2 runs after this subtask and deletes the two tests excluded above. Subtasks 2 and 3 depend on this subtask.

## Validation Strategy

Run the runtime-core test suite, including every architecture test, plus runtime-application, runtime-domain, and runtime-cli tests. Before and after, record the number of files each affected scanner visits. The before count is zero. Changed and new tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

Continue with subtask 2 (`spec_subtask_2_one-cli-process-output-contract.md`).
