# SKILL-371 subtask 1 - Restore architecture guard enforcement

Parent: [spec.md](spec.md). Finding: F-001 in [investigation.md](investigation.md).

## Scope

Make every runtime-core architecture test that names a module source root read files from that root. Then fix, in this commit, the violations those tests report.

- `ArchitectureScanSupport.runtimeRoot` resolves to the repository root. Tests that pass bare module paths (`"runtime-cli/src/main/kotlin"`) or filter `relativePath` on `"runtime-application/src/main/kotlin/"` scan nothing. Resolve module paths through `RuntimeModuleCatalog.runtimeKotlinModuleDirectory`, or through one root that is `../../../runtime-kotlin`. Pick one convention, apply it everywhere, and delete the other.
- Walkers that return empty on a missing root must fail instead: `RuntimeArchitectureTestSupport.engineInboundApiViolations`, `mainPackageRootsForModule`, `ArchitectureScanSupport` file walkers, and the private `kotlinFilesUnder` copies in `ImplementationOwnershipArchitectureTest`, `InstallPolicyOwnershipArchitectureTest`, and `RuntimeEnforcementHardeningArchitectureTest`. Path-presence checks such as "retired adapters stay absent" must first assert that their parent source root exists.
- Known affected tests: `RuntimeEngineInboundApiTest`, `RuntimeApplicationSharedEngineEdgeArchitectureTest` (in `RuntimeEngineBoundaryArchitectureTest.kt`), `RuntimeRawMapArchitectureTest` (inner-layer raw maps), and `RuntimeArchitectureTest` (three domain filters). Recensus after the fix. Any other test that was vacuous for the same reason is in scope. If `ImplementationOwnershipArchitectureTest` `forbiddenSourcePackages` or `RuntimeLayerBoundaryArchitectureTest` "retired review and telemetry adapters stay absent" still pass without reading the files they name, delete them in this commit.
- Fix reported violations:
  - `ReviewAccountingSummary.toBoundedPayload` (runtime-domain) and `toReviewAccountingPayload` (runtime-application) must not be public raw-map functions. Keep one serializer, with non-public visibility or a typed contract, at the owning boundary. Its consumers are `CodeReviewCommand` output, the SQLite accounting persistence path, and `ReviewAccountingDurableRedactionTest`. They keep the same rendered and persisted bytes. `runtime-infra/sqlite` holds a third copy, `ReviewAccountingBoundedSerialization.encodeReviewAccountingBoundedPayload`, plus a public same-name `toBoundedPayload()` extension in `ReviewAccountingWireExtensions.kt`. If the restored guard reports those copies, fix them in this commit.
  - Refresh `PINNED_ENGINE_INBOUND_API_TYPES` to the engine's real inbound surface (`goalrunner.status`, `goalrunner.preflight`). If experiment imports still fail the pin, remove those imports or the experiment call sites in this commit. Do not pin an experiment type to make the test pass, and do not stop for another issue.
  - Every public raw-map signature the restored filter reports, including experiment port files if they are still present. Known non-experiment signatures: `IdeStatusValidator.toWireMap`, `IdeStatusProblemDetails.from` / `asWireEntries`, and `ReviewFinishedTelemetryPayload`. For each one, move the wire map to the adapter-side serializer or replace it with a typed model. Wire bytes stay identical.
  - Any further violation the restored guards report.
- Also repair `PortsDeclarationArchitectureTest` and `PortNullObjectAbsenceArchitectureTest` when they name a module source root and scan nothing. Apply the same scan-root convention. Document that convention in `ARCHITECTURE.md`.
- Update the enforcement-status text in `../../../runtime-kotlin/ARCHITECTURE.md` so it states which scanners are verified to read files.

## Acceptance Criteria

1. Every architecture scanner that takes a module source root fails when the root does not exist, and each affected test asserts that it read at least one Kotlin file per named root.
2. A synthetic unpinned engine reference placed in runtime-cli main source, and a synthetic public raw-map function placed in runtime-application main source, each fail their scanner through its real entry point (fixture-based, not by adding production files).
3. `RuntimeRawMapArchitectureTest` and `RuntimeArchitectureTest` path filters match files under `runtime-kotlin/<module>/src/main/kotlin/`, and the raw-map test reports zero violations on the tree because the two accounting functions no longer expose a public raw map.
4. `RuntimeEngineInboundApiTest` passes against the current runtime-application, runtime-cli, and runtime-mcp sources with a pinned list whose every entry names an existing engine type.
5. No architecture baseline file, exemption list, or pinned list gains an entry whose only purpose is to tolerate a violation found by this subtask.
6. `../../../runtime-kotlin/ARCHITECTURE.md` names the scan-root convention and states that missing roots fail.

## Non-Goals

- Adding new architecture rules or scanner classes.
- Changing CLI output, exit codes, or command behavior, apart from the experiments rendering change the engine pin requires.
- Consolidating the `runtimeRoot` walkers that already resolve `../../../runtime-kotlin` correctly, unless the chosen convention replaces them.

## Dependency Notes

Runs first within this bundle. It does not wait for another issue. Use the package names that exist now, and fix every violation the restored guards report on this tree. Subtasks 2 and 3 depend on this subtask.

## Validation Strategy

Run the runtime-core test suite, including every architecture test, plus runtime-application, runtime-domain, and runtime-cli tests. Before and after, record the number of files each affected scanner visits. The before count is zero. Changed and new tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

Continue with subtask 2 (`spec_subtask_2_one-cli-process-output-contract.md`).
