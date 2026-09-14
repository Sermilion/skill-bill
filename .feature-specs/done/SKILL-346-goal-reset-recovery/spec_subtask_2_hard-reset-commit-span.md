# SKILL-346 · Subtask 2 — Hard reset and subtask commit span recovery

## Scope

Fix defect 1 from issue 346: `goal reset --hard` clears durable checkpoint
identity but leaves the feature branch tip as a subtask commit with a
`Skill-Bill-Subtask` trailer. The next run hits ambiguous active subtask span
because empty checkpoint identity cannot accept a trailer-carrying review base,
including legitimate subtask N>1 bases that are the prior subtask commit.

Choose one coherent product behavior (or a small explicit matrix) and implement
it end to end:

- Preflight hard reset to refuse when the branch tip trailer matches this goal
  and no durable identity will remain, with actionable next steps; or
- Hard reset coordinates git state (for example reset branch tip to review base
  or drop orphan trailer commit) while preserving earlier subtask commits; and/or
- A supported operator or automatic path records durable checkpoint identity for
  a trailer-matching HEAD when issue key and subtask id match the manifest
  active subtask, fulfilling restore its durable identity without DB surgery.

Preserve checkpoint refs and completed subtask commits. Do not squash unrelated
history or infer ownership without proof.

## Acceptance Criteria

1. After hard reset followed by goal relaunch, the active subtask does not block
   solely with ambiguous span when HEAD trailer and manifest active subtask
   match and ownership is provable via trailer or prior checkpoint refs.
2. Hard reset never returns success while leaving durable identity empty and a
   same-goal trailer on branch tip that the commit gate would always reject for
   that subtask, unless reset output documents the branch action taken.
3. Subtask 2+ relaunch with review base equal to the prior subtask trailer commit
   succeeds or fails with a single actionable command, not advice with no CLI path.
4. Regression test reproduces the issue 346 sequence: subtask commit exists,
   interrupt, soft scoped recommendation unusable, hard reset ok, relaunch no
   longer stuck on ambiguous span (or hard reset refused upfront with clear remedy).
5. Focused subtask commit resolver / migration tests and dominant-stack quality
   check pass on touched modules.

## Non-Goals

- Rewriting SKILL-235 commit-based review policy wholesale.
- Force-pushing or rewriting published remote history beyond existing amend/finalise
  semantics.

## Dependency Notes

- Builds on subtask 1 so operators reach runnable recovery; implementation may
  land in the same modules but commits separately.

## Validation Strategy

1. Run `FeatureTaskRuntimeSubtaskCommitResolverTest` and new hard-reset span
   regression tests.
2. Run dominant-stack quality check on touched Kotlin modules.

## Next Path

Goal complete when both subtasks are `complete` and issue 346 acceptance is met.
