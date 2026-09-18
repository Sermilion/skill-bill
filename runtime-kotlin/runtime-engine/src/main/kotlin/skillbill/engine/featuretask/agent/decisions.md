# featuretask runtime boundary decisions

## [2026-09-18] commit_push does not launch an agent
Context: Agents emitted two phase-output envelopes on `commit_push`, and the schema gate blocked a non-retrying phase. The only agent field the runtime consumed was a commit subject; staging, commit, push, and `commit_sha` were already runtime-owned.
Decision: `commit_push` skips the agent launch. The runtime stages every dirty non-ignored path, commits with a subject from the issue key and subtask name, pushes, and persists `commit_sha`. Downstream readers still use `commit_push_result.commit_sha`.
Reason: A subject string is not worth a structured agent turn. Double JSON at a non-retrying gate stranded finished subtasks.
Revisit when: commit subjects need human-authored outcome text that the manifest subtask name cannot carry.

## [2026-09-17] Audit repair cycles stay in one session and remaining text carries a reason
Context: Auditors inspected once, emitted remaining ACs with no why, and the runtime relaunched. Three runtime relaunches would recreate the remaining-criteria storm.
Decision: The audit briefing asks for up to three repair cycles inside the same agent session. Remaining-criteria text that is not `[]` includes a reason per leftover criterion. That briefing is runtime-owned and identical for every dominant platform pack; packs do not author remaining-criteria settlement. The runtime still treats any non-empty remaining text as unstructured prose: no schema on that list, one outer remaining-criteria retry, then block when the text is unchanged.
Reason: Cycle count and reasons are instructions to the agent. Enforcing them in the envelope would be a new contract. Relaunching the process is the bounded outer retry, not the inner loop.
Revisit when: remaining-criteria settlement grows a structured per-cycle field.

## [2026-09-17] Remaining-criteria audit retries repair beyond implement owned paths
Context: Implement can checkpoint a narrow `scoped_owned_paths` list. Audit then treated that list as a write allowlist, re-emitted the same remaining ACs, and the runtime relaunched with the old checkpoint forever.
Decision: Remaining-criteria text is a focus hint on a fresh audit session that may edit any files those criteria require. Audit extends the owned-path inventory and checkpoints those writes before the next round. The same remaining list as the prior session blocks.
Reason: The remaining-AC loop is repair, not a frozen re-read of implement. Identical leftover text with no progress is a stall, not another session.
Revisit when: remaining-criteria settlement is a topology edge with a declared per-edge cap.

## [2026-09-16] commit_push has no extra-files category
Context: The commit_push briefing told agents never to list `.feature-specs/` in `changed_paths`, and finalisation dropped those paths from the staged set. Agents then emitted two envelopes to "fix" the list, which blocked a non-retrying phase. Spec moves such as `.feature-specs/done/` were left uncommitted.
Decision: The agent may list every dirty path. Finalisation stages every dirty non-ignored path, including `.feature-specs/`. Goal-level leftover spec dirt after the parent records `commit_sha` stays ignored at goal finalize so same-branch completion does not block on that post-commit write.
Reason: There is no extra-files category. Spec trees are worktree output of the subtask. The briefing that carved them out caused a double emit and stranded deliverable spec moves.
Revisit when: the parent stops writing the decomposition manifest after the subtask commit.

## [2026-09-15] SKILL-247 subtask 3 — run-loop helper dependency baseline and after-state
Context: F-005 documented 122 context extensions and duplicate PlanningBranch run-loop overloads; subtask 3 narrows PlanningBranch, Drive, ValidationGate, AttemptSettlement, Review, and PhaseAttempts seams without changing phase order.
Decision: Record before/after extension counts and retained broad inputs in `runtime-kotlin/ARCHITECTURE.md` under State Ownership; peel run-loop-only overloads; keep `runPhaseDriveLoop` and `invalidateReviewGenerationIfNeeded` as the only Drive context extensions; pass carried-forward review, gate settlement, pack routing, and checkpoint calculations through explicit arguments; retain context at validation agent-turn/fix-loop orchestration and review launch capture because those paths still coordinate the launch, activity, diagnostics, clock, transition, and session ports; remove public collaborator aliases on `FeatureTaskRuntimeRunLoop`.
Reason: Helpers must not gain authority through a renamed all-access receiver; the loop stays the sole owner of forward drive and session transitions.
Revisit when: a new helper needs a full context and no smaller port set can be named.

## [2026-09-15] Run-evidence ownership derives from the active run, not the store prefix
Context: every path under `.skill-bill/` was runtime-private, so the whole `run-evidence` store was exempt from the owned-path inventory. This run's own artifacts never blocked, but a foreign workflow's artifact and a file forged under the same directory were swept out with them.
Decision: `isRuntimePrivatePath` no longer claims `.skill-bill/run-evidence`. The checkpoint scope resolves ownership from the active run's identity against the deterministic publication address `.skill-bill/run-evidence/<workflow-id>/<fingerprint>/`. Only this run's own address is runtime-owned; anything else under the store root stays an ordinary actionable path.
Reason: the exemption was load-bearing in one direction only. A prefix-free rule blocks the run's own evidence; a blanket prefix rule silently absorbs someone else's file. The address the store already writes carries the provenance both directions need.
Alternatives considered: a durable published-inventory port read at checkpoint time (rejected for now: the publication address is already deterministic from workflow id and checkpoint fingerprint, so a second durable authority would restate it).
Revisit when: the store stops addressing artifacts by workflow id, or a run must adopt evidence published under another workflow's address.

## [2026-09-15] A verification boundary needs an observed lane disposition, not just empty claims
Context: a review pass that produced no output at all reached `emptyClaimsVerificationShortCircuit` with no claims and a blank prose input, which recorded `VERIFICATION` as REACHED. A lane that never ran was indistinguishable from a lane that genuinely found nothing.
Decision: `ParallelReviewLaneStatus` carries the pass's `ReviewLaneReviewDisposition`. The verification boundary is recorded only when the lane succeeded, was not interrupted, and published output. Otherwise the stage returns a typed `ReviewVerificationNonSuccess` that preserves existing findings and durable verdicts and leaves the boundary unreached.
Reason: `mergeResult.formattedOutput` substitutes a placeholder for blank lane output, so the absence signal was already erased by the time verification read it. The disposition has to be carried from where it is observed.
Revisit when: a review mode can legitimately complete a pass without publishing any output.

## [2026-09-15] SKILL-236 missing-terminal and PR-failure observations: reproduction disposition
Context: subtask 3 owns recording which of the reported missing-terminal-outcome and PR-failure observations reproduce inside its scope.
Decision: Reproduced and repaired here — the verification stage boundary reached on an empty or failed review pass, and the evidence broker's unconditional `repeated_evidence_read` refusal. Governed by SKILL-235's landed decision and receipt reconciliation (commit 93370d75d) — terminal decision and repair-receipt finalisation; this subtask reuses it and adds no second finalisation authority. Unreproduced and claimed unrepaired — the historical PR-step failures, which no fixture in this scope reproduces.
Reason: claiming a repair for a failure class nobody reproduced would put a false clearance in the very telemetry this feature exists to make truthful.
Revisit when: a reproduction fixture for the PR-step failures lands, at which point the repair belongs with SKILL-235's finalisation authority rather than here.

## [2026-09-11] Active subtask owns every dirty path
Context: commit_push blocked needs_human when any dirty file was missing from the frozen ownership inventory, including required `agent/history.md` writes.
Decision: The active subtask owns every non-runtime-private dirty path. Checkpoints and commit_push stage them. Review identity does not treat extra dirt as foreign.
Reason: The inventory was a branch-setup snapshot, so later legal writes looked like someone else's work and stalled the goal. Operator intent is that whatever is dirty on this subtask is this subtask's.
Revisit when: a run must share a worktree with a second in-flight subtask that must keep its own uncommitted files out of this commit.

## [2026-09-10] write_history and commit_push never reopen earlier phases
Context: After the bounded review_fix round, implement_fix leaves owned dirty files. commit_push treated that as a stale review, wiped audit and later phases, and the drive loop bounced back to audit.
Decision: `write_history` and `commit_push` stay forward-only. Owned implement/implement_fix paths plus declared boundary-history may be finalised. Foreign dirty content blocks needs_human. Matching subtask trailer on HEAD keeps review identity across tree drift.
Reason: The shipped topology already allows only `audit_gap` and `review_fix`. Replaying audit/review from finalisation discarded a completed review-fix round and could not converge while the same owned files stayed dirty.
Alternatives considered: Keep re-entering audit for any non-history dirty path (rejected: infinite bounce after a legal implement_fix). Stage history-only and leave implement_fix files dirty (rejected: the bounded fix round would never land).
Revisit when: a later phase needs a declared backward edge, which would be a topology change rather than a finalisation side path.
Superseded by: Active subtask owns every dirty path (2026-09-11)
