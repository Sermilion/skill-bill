# SKILL-352 Subtask 3 - Record fallbacks, restore ownership, and shrink surface

Parent spec: [.feature-specs/SKILL-352-runtime-engine-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-352

## Scope

Resolve F-005, F-006, F-008, F-011, and F-012 in [investigation.md](investigation.md); apply F-013 to the test classes touched.

Own `GoalRunnerStatusProjectionAssembler.kt`, `GoalRunnerRepairCoordinator.kt`, `GoalRunnerProgressEventEmitter.kt`, `GoalRunnerObservabilityEmitter.kt`, `GoalRunnerResetReplanCoordinator.kt`, `GoalRunnerPurgeCoordinator.kt`, `RuntimeOwnedPersistenceBoundary.kt`, `GoalRunnerLaunchModels.kt`, the `work` package, `runtime-application/src/main/kotlin/skillbill/application/idestatus/model/IdeStatusModels.kt`, the other 11 engine-only application declarations named in F-008, every public top-level declaration in `runtime-engine/src/main`, `RuntimeEngineInboundApiTest`, and the module and shared-edge sections of `../../../runtime-kotlin/ARCHITECTURE.md`.

Make the four cited fallbacks fail typed or emit a bounded record and surface the degraded read in the projection; give the diagnostics-port guard one owner. Extract one best-effort recorder for the two emitters and delete the engine brace scanner in favour of the adapter that owns agent-output parsing. Move the 30 engine-only application types into the engine areas that produce them. Re-run the census, narrow visibility, delete the dead, and add the public-declaration guard. Type timestamps and closed vocabularies at the cited seams.

## Acceptance Criteria

1. The attempt-ledger read at `GoalRunnerStatusProjectionAssembler.kt:128`, the child and parent execution-liveness resolutions, and the repair coordinator's worker-ownership read either fail typed or emit one record with seam, value expected, and value used; the status projection carries a degraded-read marker instead of zero counts or silent `UNKNOWN`. Tests assert the record fields or the typed failure and the projection marker.
2. `runCatching { diagnostics.warning(…) }` has one owner; the emitters, coordinators, and persistence boundary call it. The count of hand-copied guards is zero.
3. `GoalRunnerProgressEventEmitter` and `GoalRunnerObservabilityEmitter` delegate to one best-effort recorder with one cancellation and interruption rule and one declared message cap; their emitted payloads are byte-identical for supported inputs. `topLevelJsonObjectCandidates` is deleted from the engine and its callers use the adapter-owned scanner; no fourth scanner exists.
4. The 19 IDE-status model types, `FeatureTaskRuntimeRejectedOutputWrite`, the four `FeatureTaskRuntime*Telemetry` request models, `FeatureSpecPreparationRuntime`, `FeatureSpecPreparationWriter`, `SpecSourceResolver`, `FeatureTaskRuntimeSharedReviewEvidenceResolver`, `encodeDecompositionManifestYaml`, and `findMatchingDecompositionManifests` live in the engine area that produces or solely calls them, after a re-run consumer census confirms no other production consumer. The CLI and MCP reach IDE status through `IdeStatusService` and `IdeStatusRequest` exactly as before, and the pinned inbound API is updated only for types those adapters reference. An architecture test owns the remaining shared application edge. `ARCHITECTURE.md` does not inventory those types.
5. After a fresh census plus compilation and the full runtime-kotlin suite, same-file-only public declarations are `private`, module-only declarations are `internal`, and unreferenced declarations are deleted. An architecture test fails a new public top-level declaration in `runtime-engine/src/main` outside the pinned inbound API and `model` packages. The CLI, MCP, and core compile and their tests pass.
6. At the cited seams, `expiresAt` and comparable lease timestamps are parsed once at the port boundary with a typed failure; liveness class, worker role, and planning disposition are enums with `wireValue`, reusing a domain enum where one already names the vocabulary. Snapshot and observability payloads are byte-identical for supported values.
7. Test classes touched by this subtask are split by behaviour family where they exceed one responsibility, and prose-substring assertions in them are replaced by typed-outcome or failure-code assertions where a typed value exists.
8. No new suppression, baseline row, or exemption.

## Non-goals

No change to phase order, review policy, or telemetry semantics; no new observability event kinds. No change to helper inputs or bags (subtask 1) or persistence ownership and failure identity (subtask 2). No suite-wide test rewrite.

## Dependency notes

Depends on: subtasks 1 and 2, because the visibility census and the shared-edge list are only accurate after their deletions and moves land. Rebase on the branch head before the census.

## Validation strategy

Name the regression before each test: a ledger read failure that today shows zero attempts must show a degraded marker; a malformed lease timestamp must fail typed rather than become `UNKNOWN`; a moved type must still reach the CLI through the pinned API. Assert diagnostic records through the diagnostics port fixture, not log text. Diff emitter payloads before and after. Rely on compilation of all modules plus `./gradlew check` on runtime-kotlin for the surface reduction. Run the engine suite, `runtime-cli`, `runtime-mcp`, `runtime-application`, and the `runtime-core` guards including the new public-declaration check, then the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

After this subtask settles, the goal completes; run `skill-bill goal SKILL-352` status to confirm all three subtasks record a `commit_sha`.

## Spec Path

.feature-specs/SKILL-352-runtime-engine-boundaries-and-simplicity/spec_subtask_3_record-fallbacks-restore-ownership-and-shrink-surface.md
