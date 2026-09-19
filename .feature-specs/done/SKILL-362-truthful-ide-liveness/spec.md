# SKILL-362 - truthful-ide-liveness

## Mode

single_spec

## Intended outcome

The IntelliJ plugin shows **Paused** only for an operator pause. A live goal whose SQLite heartbeats fail or whose execution lease expired still shows as active or idle, matching process liveness, not as paused. Activity stamps and journal ticks no longer drop on `SQLITE_BUSY` in a way that flips a running child to idle.

## Scope

The investigation covers IDE status projection, goal execution liveness, agent-activity stamps, the worktree edit journal, and the shared SQLite busy timeout. See [investigation.md](investigation.md) for three findings, the SKILL-361 observation, and rejected per-project databases.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001, F-002, F-003 | Idle is not paused; live processes stay live under SQLite contention; inspect runs when a lease heartbeat was lost | 1 |

Prepared in local mode on 2026-09-19. SKILL-362 follows SKILL-361, the highest existing local spec key; the user authorised the next available key. Baseline HEAD is `6ba6649c3e378fa52b627513482b7458365d80fd`. This bundle prepares work only; the subtask starts pending.

## Acceptance Criteria

1. `lifecycle_state` is `paused` only when goal control state is paused or an operator-decision pause applies. `paused_at` is present for that pause. An expired or missing execution lease with `paused: false` does not produce `paused`.
2. `executionLiveness == idle` without operator pause projects `lifecycle_state: idle` (schema already allows it). The plugin maps that payload to Idle, not Paused. The status bar does not say paused.
3. While a parent goal JVM or child feature-task process is running, `execution_liveness` is `live` even if concurrent stamp or journal writes hit `SQLITE_BUSY`. Process inspect is consulted when the lease row is expired or the heartbeat write failed.
4. `AgentActivityStampWriter` and `WorktreeEditJournalWriter` either persist after contention or emit their existing bounded failure record without changing lifecycle to paused. A test holds the database busy longer than the current 5s timeout and still yields live or idle as in (1)–(3), never paused.
5. `IdeStatusServiceGoalProjectionTest` no longer requires an expired parent lease to project paused. Operator-pause tests still project paused. `ARCHITECTURE.md` or `../../../runtime-kotlin/agent/decisions.md` records that idle liveness is not pause, and amends the 2026-06-26 SQLite busy-timeout entry if the timeout or retry policy changes.

## Constraints

- Follow `../../../runtime-kotlin/ARCHITECTURE.md` design principles, `docs/code-principles.md`, `docs/observability-policy.md`, and AGENTS.md.
- Keep one user-level review-metrics SQLite file. Do not add a global lock that forbids concurrent goals.
- Do not change phase order, review policy, or commit-before-review. This is status truth and heartbeat persistence.
- Kotlin under `runtime-kotlin` and `intellij-plugin` carries no `//` comments and no non-KDoc block comments. Wire keys stay in `runtime-contracts`.
- Schema `lifecycle_state` already includes `idle` and `paused`; do not remove `paused`.

## Non-goals

- Fixing SKILL-361 packaging or the gitignored `build/` directory.
- Splitting the metrics database per repository.
- A new plugin status family beyond idle / paused / active already in the mapper.
- Raising idle-timeout minutes for agent sessions as a substitute for truthful liveness.

## Validation strategy

Name the regression before each test: expired lease still labelled paused; operator pause lost; live PID reported idle because inspect was skipped; SQLITE_BUSY on a stamp flipping lifecycle to paused; plugin Paused for an `idle` payload. Drive `IdeStatusService` with expired lease, live inspect, operator pause, and a contended SQLite writer. Drive the plugin mapper with `lifecycle_state: idle` vs `paused`. Run ide-status, goal-status, sqlite session, and plugin mapper tests, then the pack-declared quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Run `skill-bill goal SKILL-362` when implementation is intended. The prepared manifest is the goal runner's input.
