# SKILL-347 Subtask 2 - Bind activity and projection state to their owners

Parent spec: [.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec.md](spec.md)
Issue key: SKILL-347

## Scope

Own F-003 and F-004. Update idestatus/AgentActivityStampWriter.kt, workflow/WorkflowService.kt, workflow/ContinuationStepResult.kt, the decomposition continuation producers, and decomposition/DecompositionManifestProjectionWorkflow.kt. Adjust the existing activity repository and composition wiring only where their current lifetime or timestamp contracts require it.

Replace the companion HashMap with state owned by the relevant writer, sink, or established runtime lifetime. Choose the smallest approach that preserves the 250 ms throttling purpose and bounds retained workflow entries. Distinguish an observed event from a successfully persisted event. A failed write must remain eligible for retry, including a newer generation whose write succeeds while an older one fails. Avoid a process-global lock across database I/O. Preserve monotonic repository updates and child-to-parent publication.

Carry the authoritative parent workflow ID together with projection artifacts through continuation. The public input string may be an issue key or a child workflow ID and is not the projection owner. Resolve the parent in the transaction that selects or changes the manifest. After commit, settle the projection against that exact owner. Missing expected owners must fail visibly instead of silently returning. Retry reads authoritative state and repairs only the filesystem projection.

Keep database transitions atomic. This change must not replay a committed transition because the later file write failed.

## Acceptance Criteria

1. Two independently scoped writers using the same workflow ID and instant both attempt publication to their own database boundary. Writer construction and teardown do not retain entries in process-global mutable state.
2. An ordinary failed activity write emits a bounded diagnostic and does not mark that event as persisted. A later eligible event can retry, and failure of an older write cannot erase acknowledgement of a newer write.
3. Activity retention has a documented bound or explicit lifecycle cleanup. Existing debounce behavior, evidence-read publication, timestamp ordering, and child-to-parent updates remain correct.
4. Issue-key continuation and direct workflow continuation carry the actual projection-owning parent ID to post-commit settlement. A child continuation that produces a parent manifest also settles on the parent.
5. A failed manifest projection leaves the database transition committed and records failure on the owning parent. An absent or wrong expected parent is visible as a typed failure or governed diagnostic rather than a successful silent return.
6. Projection-only retry repairs the manifest and clears the owning parent's failure marker without reopening completed phases, incrementing attempts, or creating another child. Ordinary unknown-workflow lookup keeps its existing result.
7. Tests exercise the complete continueWorkflow entry path with issue-key and workflow-ID inputs and an injected projection write failure. Activity tests cover independent owners, failed-write retry, and controlled concurrent completion ordering.

## Non-Goals

- No durable activity queue, cache dependency, new workflow storage authority, or workflow identity wrapper migration.
- No changes to Git branch selection, lease takeover, projection schema, or successful continuation output unless a demonstrated contract requirement makes one necessary.

## Dependency Notes

Depends on: none
No prerequisite. Ship activity and projection ownership repairs together as one state-correctness pass, with their respective boundary tests. Preserve subtask 1 cancellation handling if it has already landed.

## Validation Strategy

Run activity publication and status freshness tests plus workflow continuation, decomposition projection, and SQLite projection recovery tests. The new projection test must prove the committed parent and child records survive a file failure, that the parent carries the failure marker, and that retry changes no workflow transition. Use latches or deterministic callbacks for concurrency, not wall-clock sleeps.

## Next Path

.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec_subtask_3_simplify-review-composition-and-stateless-use-cases.md

## Spec Path

.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec_subtask_2_bind-activity-and-projection-state-to-their-owners.md
