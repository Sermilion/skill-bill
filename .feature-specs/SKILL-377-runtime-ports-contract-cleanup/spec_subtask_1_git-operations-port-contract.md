# SKILL-377 subtask 1 - Git operations port contract

Parent: [spec.md](spec.md). Findings: F-002, F-003, and the git rows of F-004 in [investigation.md](investigation.md).

## Scope

Flatten the aggregate (F-002):

- `WorkflowGitOperations` inherits every capability interface in `runtime-ports/.../ports/workflow/gitops/` that remains after SKILL-378 subtask 1 (which deletes linked-worktree operations): branch, remote, commit history, worktree, suppression evidence, checkpoint history, goal-subtask review, repository fingerprint, readiness tree identity, repository-owned paths, runtime-phase file manifest, and scoped staging.
- Rename capability members to the names today's forwarders expose (`updateRef` → `updateCheckpointRef`, `captureBaseline` → `captureGoalSubtaskReviewBaseline`, `changedPathsAgainstBase` → `readinessChangedPathsAgainstBase`, and so on), so each existing call site keeps its name and changes only imports.
- Delete the remaining getters (8 at baseline; SKILL-378 removes `linkedWorktreeOperations`) and the 23 top-level forwarding extensions, including the dead `resolveReadinessTreeIdentityPayload`.
- The `runtime-infra/workflow` adapter implements the aggregate with Kotlin interface delegation (`by`) over its existing capability objects. Test doubles in `runtime-ports` testFixtures do the same.
- Consumers keep injecting `WorkflowGitOperations`. Narrowing a consumer to one capability is allowed but not required.

Type the payloads inner layers parse (F-003):

- Checkpoint ref listing returns the ref names as a list. `deleteCheckpointRefsUnderPrefix` becomes a capability member implemented by the adapter.
- Readiness tree identity returns `ReadinessTreeIdentity?` in a typed result. Delete `ReadinessTreeIdentityPayloadCodec`.
- Index-state capture and restore use an opaque snapshot value that the adapter produces and consumes. Path content identities return a typed map.
- `createCommit` reports "nothing to commit" as its own result variant, decided by the adapter. Delete `recordsNothingToCommit` and update `GoalRunnerFinalization`.
- Delete `WorkflowGitOperationResult.fromWire` and the `invoke(status: String)` operator.
- Single-scalar operations (sha, branch name, message, status text) keep `WorkflowGitOperationResult`.
- Recensus the `\u0000` decode sites in engine and application (13 at baseline). Each moves into the adapter or disappears with a typed result.

Git defaults (F-004): make `resetSoftToCommit`, `resetHardToCommit`, `isCommitAncestor`, `resolveCommit`, `readHeadTrackedFile`, `recoverBaseline`, `stageAll`, and `repositoryCheckpointFingerprint` abstract. Fakes that relied on a default get it from the existing `Noop*` testFixtures doubles.

## Acceptance Criteria

1. `WorkflowGitOperations` declares no property, and runtime-ports main declares no top-level function whose receiver is `WorkflowGitOperations`.
2. The workflow git adapter implements every capability through the aggregate. The composition root binds one `WorkflowGitOperations`, and no production class reads a git sub-port through a getter.
3. No runtime-engine or runtime-application main source contains `\u0000`, or parses a `WorkflowGitOperationResult` value into refs, identities, index state, or path identities.
4. `ReadinessTreeIdentityPayloadCodec`, `recordsNothingToCommit`, `WorkflowGitOperationResult.fromWire`, and its string-status `invoke` do not exist. Goal-runner finalization detects "nothing to commit" through a typed result.
5. None of the eight git capability members listed in scope has a default body.
6. Adapter tests cover each newly typed result against a real temporary git repository, including an empty ref listing and a commit with nothing staged.
7. Checkpoint refs, commit messages, pushed branches, and persisted workflow artifacts that git operations influence are byte-identical to baseline on the existing goal-runner and feature-task tests.

## Non-Goals

- Changing git argv, timeouts, process handling, or error text, apart from removing stderr matching outside the adapter.
- Typed results for single-scalar operations.
- Requiring consumers to inject narrow capabilities.
- Any change to non-git ports.

## Dependency Notes

Runs first. Requires SKILL-370 and SKILL-378 subtask 1, which deletes the experiment git consumers and linked-worktree operations. Also requires SKILL-376 subtask 1, which reworks the git process code in `runtime-infra/workflow` and declares git result semantics a non-goal. Subtasks 2 and 3 run after this one.

Recommended before SKILL-380 subtask 1.

## Validation Strategy

Run the runtime-ports, runtime-infra workflow, runtime-engine, runtime-application, and runtime-cli test suites and the architecture suite. The regression each new adapter test catches: a malformed or empty git listing no longer reaches the engine as text, and an empty commit is no longer inferred from English stderr. Changed tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

`skill-bill goal SKILL-377`
