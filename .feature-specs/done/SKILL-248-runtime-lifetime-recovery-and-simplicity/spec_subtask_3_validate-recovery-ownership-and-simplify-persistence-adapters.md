# SKILL-248 Subtask 3 - Validate recovery ownership and simplify persistence adapters

Parent spec: [.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec.md](spec.md)
Issue key: SKILL-248

## Scope

Address F-003, F-005, and F-006 in investigation.md. Own runtime-infra-fs/DecompositionManifestBundleJournal.kt and DecompositionManifestBundleJournalOperations.kt, the journal's contract and failure owners, and the SQLite goal progress bridge and database identity/readiness code.

Validate the complete journal before applying any entry or cleaning any directory. Give its persisted versioned envelope a canonical schema, owning keys, and a typed invalid-journal failure according to repository contract rules. Validate the marker name and transaction identity, the exact transaction-owned staging directory, unique targets and staging entries, path containment, and file digests. Check existing staged bytes against the stored digest before replacement; when an entry is already applied, validate the target digest. Resolve symlink and traversal cases explicitly so lexical containment cannot authorize another directory. Keep the marker and remaining staging evidence on rejection. Do not blindly regenerate intended writes from corrupt data. Document the compatibility and operator-recovery path for existing 0.1 journals. Preserve journal roll-forward after interruption and the distinction between SQLite commit and filesystem projection.

Remove WorkflowGoalRunnerProgressBridge and the three private progress read/write/aggregate interfaces that repeat the existing inward contracts. Let the current progress-recording owner implement the existing GoalRunnerWorkflowProgressStore, GoalRunnerWorkflowLedgerWriteStore, and GoalRunnerAttemptLedgerStore contracts as needed. Update the containing workflow adapter and builder. Keep reconcile, block, terminal, and child-repair bridges where they own real transactions or projection behavior. No new abstraction replaces the deleted progress forwarder.

In DatabaseWriteReadinessGate, compare the DatabaseIdentity already read at each decision point. DatabaseIdentity.matchesFile currently reads the same file and PRAGMA again. Remove that redundant reread with a value comparison. Keep the second observation after acquiring the initialization lock, identity/version/replacement invalidation, and failure recovery. Do not add a pathname-only cache or merge initialization with ordinary connection setup.

## Acceptance Criteria

1. Recovery validates the entire journal and all staged or already-applied entry digests before it begins a new apply pass. A checksum mismatch changes no additional target and retains recoverable evidence.
2. A journal that points at an unrelated sibling directory, escapes through traversal or symlinks, repeats targets, or contains malformed or unsupported fields cannot authorize move or cleanup operations. It produces a typed, attributable failure.
3. Cleanup only removes the exact staging directory and files owned by the validated transaction. A malformed journal cannot delete a sibling evidence directory.
4. Valid current journals and the explicitly supported historical 0.1 shape retain interrupted roll-forward behavior. A rejected legacy or corrupt record fails visibly with an actionable recovery path, without replaying a committed database mutation.
5. The journal schema, Kotlin version and keys, validation seam, typed errors, and parity tests agree. Existing source-generation and schema-resource packaging boundaries remain intact.
6. WorkflowGoalRunnerProgressBridge and its private progress interface hierarchy disappear. Existing public ports continue to return the same progress, ledger, and event results with unchanged transaction ownership.
7. Database readiness uses each identity observation once for its cache decision. The locked recheck still occurs when needed; failed initialization, replacement at the same path, version change, and concurrent initialization preserve recovery.
8. Regressions cover both reproduced journal failures, interrupted recovery and invalid path ownership. Existing progress/ledger and readiness tests exercise the surviving production owners. No architecture baseline or suppression grows.

## Non-Goals

- No general transaction framework, new storage engine, connection pool, cache library, or broad port deletion.
- No claim that multiple file renames form a filesystem transaction; retain the existing journal recovery model.
- No removal of transaction-owning bridges or schema and compatibility guards to reduce line counts.

## Dependency Notes

Depends on: none
No prerequisite. This is one bounded durable-adapter pass. Journal validation is the substantive change; the two local SQLite simplifications ship here without separate runtime ceremonies or unused intermediate abstractions.

## Validation Strategy

Convert the two journal probes into intended-behavior tests. Add a valid partial-apply recovery case and rejection cases for staged corruption, unsupported version, duplicate entries, unrelated staging ownership, and symlink escape. Run decomposition bundle, projection recovery, schema parity, goal progress/ledger, DatabaseWriteReadinessTest, and transaction rollback tests. Use observable initialization/recovery behavior rather than pinning exact SQL counts. Review the value-comparison code to confirm it does not reopen the database for the same observation. Record actual net deletion, preserving the original estimate as an estimate.

## Next Path

Continue through the goal runtime completion path after all subtasks complete.

## Spec Path

.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec_subtask_3_validate-recovery-ownership-and-simplify-persistence-adapters.md
