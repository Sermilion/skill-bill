# SKILL-248 Subtask 2 - Bound Git process lifetime

Parent spec: [.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec.md](spec.md)
Issue key: SKILL-248

## Scope

Address F-002 in investigation.md. Own runtime-infra-fs/GitProcessCommands.kt and the Git process, scoped staging, checkpoint, and workflow operation tests that exercise it.

Give each started Git process and its streams and drain workers one cleanup owner before any blocking input, output, or wait operation. Drain output while writing stdin so a full stdout pipe cannot prevent stdin completion. Apply one operation deadline to input delivery, child waiting, and output settlement, with a separate finite cleanup budget. Preserve the existing longer timeout for hooked commit and push commands. A child exit must not lead to an unbounded output-thread join.

On interruption, timeout, input failure, or output failure, stop and settle the process resources this invocation owns. Account for owned hook descendants without process-name matching or killing unrelated processes. Preserve cooperative interruption and the primary failure. Never return mutable or unfinished capture as a successful result. Keep binary patch, NUL-delimited staging input, trimming, and Git exit-status semantics at current callers. If a shared low-level helper is warranted, keep it local and justify it with the existing Git paths; do not route Git through an agent-specific launcher.

## Acceptance Criteria

1. Interrupting runGitProcess while a child is blocked leaves no live owned Git process or owned hook child and no unbounded drain worker. The caller retains the cooperative interruption signal.
2. A child that fills stdout while reading a large stdin payload can make progress because input and output proceed concurrently. The operation either completes or reports its deadline within a bounded cleanup allowance.
3. A child that exits while a descendant retains its output pipe cannot cause an unlimited join. Incomplete output is a failure or explicit incomplete result, never ordinary success.
4. Failures during stdin writing, drain startup, wait, stream close, and child termination still attempt all applicable cleanup. A secondary failure does not replace the original cause.
5. The ordinary and hooked-command timeout policies, binary diff handling, NUL-separated index input, and successful WorkflowGitOperationResult values remain compatible.
6. Behavior tests use a temporary repository and locally owned processes, with PID or handle capture, synchronization, and guaranteed test cleanup. They cover interruption, pipe backpressure, inherited output handles, timeout, and normal completion.

## Non-Goals

- No Git library replacement, agent launcher dependency, blanket subprocess rewrite, or new process-management framework.
- No changes to staging scope, reviewed tree identity, checkpoint pruning policy, or commit ownership.

## Dependency Notes

Depends on: none
No prerequisite. Git has its own process and pipe lifecycle and can ship independently of goal coordinator and persistence repairs.

## Validation Strategy

Run GitProcessSupportTest and the GitWorkflowGitOperations baseline, recovery, and diff tests, plus scoped staging and checkpoint tests. Convert the Git interruption probe into an intended-behavior regression and add deterministic pipe backpressure and inherited-pipe cases. Verify tests terminate their owned processes on failure. Run affected architecture guards and the applicable workflow quality gate during implementation.

## Next Path

.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec_subtask_3_validate-recovery-ownership-and-simplify-persistence-adapters.md

## Spec Path

.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec_subtask_2_bound-git-process-lifetime.md
