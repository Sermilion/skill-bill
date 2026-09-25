## [2026-09-25] SKILL-378 subtask 2 — Feature-task run-loop step classes
Areas: runtime-kotlin/runtime-engine/skillbill/engine/featuretask/runloop/{core,phase,output,settlement,checkpoint,state}, runtime-kotlin/runtime-core/repoTest/skillbill/architecture
- Broke the 20-node mutual-reference cycle across the feature-task run-loop step objects; the declaration graph now has 23 nodes and no strongly connected component larger than one.
- New step owners: `PhaseBlocking` (runloop/phase) holds block and phase-state-read primitives, `ValidationScope` (runloop/settlement) holds validation changed-paths and the single pack build command, `ReviewCompletion` (runloop/output) holds goal-review detection and review completion persisters.
- Added an architecture guard asserting run-loop step declarations reference each other acyclically, plus a synthetic two-node fixture proving the census names both members of a mutual pair.
- Guard reuses the existing `stronglyConnectedComponents` scan via `ArchitectureScanSupport.cyclicComponents` rather than a second scanner; `CommentStripper` moved private -> internal and gained a defaulted `blankStringLiterals` flag so the default path stays byte-identical. reusable
- Pattern: strip comments and string literals before building a declaration-reference graph — observability seam literals otherwise forge false edges and re-form the cycle.
- Legacy SQLite busy-reason text matching is now one named constant consumed at the two resume sites; it classifies persisted historical blocked reasons, not live failures (typed `DatabaseBusyError` remains the live disposition).
- Limitation: guard is host-classed in `RuntimeEnginePublicTopLevelDeclarationArchitectureTest`; the carrier class `FeatureTaskRuntimeRunLoopSession` stays in the node set as a zero-out-edge sink rather than being name-excluded.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-09-17] SKILL-352 subtask 3 — Record fallbacks, restore ownership, and shrink surface
Areas: runtime-kotlin/{runtime-engine,runtime-domain,runtime-ports,runtime-application,runtime-infra-sqlite,runtime-core}
- Goal-runner durable-read fallbacks now emit bounded diagnostics and project degraded status; lease timestamps and closed runtime vocabularies use typed domain values.
- Consolidated best-effort diagnostic emission, removed the engine JSON scanner, and moved engine-only declarations behind their producing boundary while narrowing public surface.
- Pattern: keep fallback recording, projection degradation, and ownership decisions at typed runtime seams. reusable
- Limitation: the full engine visibility census and test-class/prose-assertion split remain partial follow-up work.
Feature flag: N/A
Acceptance criteria: 6/8 implemented

## [2026-09-17] SKILL-352 subtask 2 — One persistence seam and typed durable failures
Areas: runtime-kotlin/{runtime-application,runtime-contracts,runtime-core,runtime-domain,runtime-engine,runtime-infra-fs,runtime-infra-sqlite}
- Consolidated feature-task workflow artifact reads and writes behind the runtime-engine persistence owner and removed recorder role-interface bundles.
- Routed goal-planning and review-policy artifact decoding through declared wire-key owners, typed durable errors, and non-null domain decoder overloads.
- Pattern: keep artifact encoding, decoding, and persistence patches at one typed runtime boundary. reusable
- Limitation: several shared-context integrity paths still use `require`/`error`; unsupported packet versions use the typed failure path.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-09-14] WE-4789 — Validation gate execution evidence
Areas: orchestration/contracts, runtime-kotlin/{runtime-contracts,runtime-domain,runtime-engine,runtime-infra-fs,runtime-infra-sqlite,runtime-cli,runtime-ports}
- Validation gate settlement now projects `executed_work_units`, `executed_checks`, and top-level `checks` from `ValidationGateRunResult` instead of discarding gate task identities at the coordinator seam.
- Gradle packs derive stable `module|task` identities from `> Task` stdout lines even when actionable-summary work units are zero; explicit empty `executed_checks` distinguishes truthful zero-work from legacy evidence loss.
- Goal status and workflow status read the same `validation_result` gate execution evidence as the settled validate artifact; issue 341 command/exit verdict rules are unchanged.
- Pattern: one typed `FeatureTaskRuntimeValidationGateExecutionEvidence` projection across runner, settlement, persistence, and status surfaces. reusable
Feature flag: N/A
Acceptance criteria: 8/8 implemented

## [2026-09-13] Issue 342 — Repair lease and pause clearance
Areas: runtime-kotlin/{runtime-engine/runtime-cli/runtime-ports}/goalrunner
- Added durable stale parent execution-lease, stale child worker-lease, and runner-interrupted pause wedge diagnosis and repair.
- Repair apply clears stale leases and pause residue only without a live unexpired owner, preserves operator_stop, and retains LIVE_LEASE_REFUSED safety semantics.
- CLI inspect and apply output names wedge classes and affected workflow ids without exposing storage tables.
- Pattern: keep parent and child wedge diagnosis/apply flows explicit and reuse the existing live-owner refusal boundary. reusable
- Regression coverage includes engine and CLI fixtures matching the issue 342 manual database workaround.
- Limitation: contract-version hard reset, review-base recovery, and existing child phase-output wedge behavior remain outside this change.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-09-13] Issue 341 — Validate evidence integrity
Areas: orchestration/contracts, runtime-kotlin/{runtime-domain,runtime-engine,runtime-infra-fs,runtime-infra-sqlite}
- Validation settlement now consumes schema-backed command and exit-code evidence, requires the required command to exit `0`, and keeps red or missing evidence incomplete across persistence and resume.
- The existing repair path receives remaining validation findings after a red result; repeated unchanged findings and exhausted repair budget remain durable blockers.
- Goal status projects valid command/exit evidence and reports an integrity problem when completed evidence is absent or invalid.
- Pattern: keep one minimal validation-evidence projection across contract, domain, settlement, persistence, and status seams; provider metadata remains opaque. reusable
- Limitation: platform validation routing and review, build, and planning evidence contracts are unchanged.
Feature flag: N/A
Acceptance criteria: 11/11 implemented

## [2026-09-11] SKILL-233 subtask 8 — Engine module and package roots
Areas: runtime-kotlin/{runtime-engine,runtime-application,runtime-infra-fs,runtime-infra-sqlite,runtime-contracts,runtime-core,runtime-cli,runtime-mcp,runtime-domain,runtime-ports,agent,ARCHITECTURE.md}
- Extracted feature-task, goal-runner, goal-planning, and planning-projection runtime behavior into the `runtime-engine` module under one `skillbill.engine` root, with a pinned inbound API and no reverse application dependency. reusable
- Moved infrastructure and application packages to their owning roots, using explicit schema-path constants at validator seams so resource lookup remains stable after package relocation. reusable
- Pattern: record module edges and package ownership in one catalog plus architecture tests, and keep empty per-module baselines as proof that new root violations are rejected rather than suppressed. reusable
- Limitation: the extraction and package moves preserve behavior and signatures; further engine subdivision remains outside this change.
Feature flag: N/A
Acceptance criteria: 12/12 implemented
