# SKILL-380 Subtask 13 - `/skill-bill` dispatcher, retire listed skills, delete bill-monitor

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Last commit. After this subtask the listed catalog is exactly `skill-bill`.

**Dispatcher (`skills/skill-bill/content.md`).** Create this listed skill.
Keep today's `bill-feature` ceremony: update-check, intake, token forwarding,
preflight, one confirmation question, rehydrate, launch, relay. Parse `phase:`
and `operation:` the same way as the CLI. Those forms call
`skill-bill --agent <currently-executing-agent>` and contain no product
checklists from retired skills.

Forms:

- `/skill-bill <intake>` — skeleton (today's `bill-feature`, one confirmation)
- `/skill-bill <intake> phase:<name>` / `/skill-bill phase:<name>`
- `/skill-bill operation:<name> …`

Do not leave a second listed skill. No `/bill-feature` alias unless install can
express it without a second catalog entry.

**Delete listed `skills/bill-*` trees** except the inline sidecar below:
`bill-feature`, `bill-feature-spec`, `bill-code-review`, `bill-code-check`,
`bill-pr-description`, `bill-boundary-history`, `bill-boundary-decisions`,
`bill-pr-review-fix`, `bill-unit-test-value-check`, `bill-update-check`,
`bill-release`, `bill-feature-verify`, `bill-feature-guard`,
`bill-feature-guard-cleanup`, `bill-monitor`. Move any remaining authored rules
into the already landed strategy or operation. `bill-monitor` is not an
operation. `skill-bill goal status` remains CLI-only.

**Keep unlisted.** `skills/bill-code-review-inline`: change `internal-for` to
`skill-bill`. Pack specialist `content.md` stays unlisted native-agent source.

**Install and docs.** Listed catalog test is `{skill-bill}`. Pack specialist
`content.md` stays unlisted native-agent source; if `internal-for` needs a
listed parent, that parent is `skill-bill`. Rewrite `AGENTS.md`,
`docs/skill-source-generation.md`, `docs/internal-skills-architecture.md`,
`docs/getting-started.md`, README slash-command tables. Remove CLI help that
names `/bill-monitor`. Run `./install.sh`.

## Acceptance Criteria

1. After `./install.sh`, the listed skill catalog is exactly `skill-bill`. `skills/bill-feature` and `skills/bill-monitor` do not exist. No production doc tells the operator to invoke `/bill-monitor` or `/bill-feature` as a listed skill.
2. `/skill-bill <intake>` with no `phase:` or `operation:` launches the skeleton with the one confirmation gate.
3. Every `phase:` from subtasks 5–7 and every `operation:` from subtasks 8–12 is reachable through the dispatcher. `phase:` and `operation:` together remains a usage error.
4. Former `bill-feature`, `bill-pr-description`, `bill-code-check`, `bill-code-review`, `bill-feature-spec`, `bill-boundary-history`, `bill-boundary-decisions`, `bill-pr-review-fix`, `bill-unit-test-value-check`, `bill-update-check`, `bill-release`, `bill-feature-verify`, `bill-feature-guard`, and `bill-feature-guard-cleanup` trees are gone from `skills/`. `bill-code-review-inline` remains with `internal-for: skill-bill`.
5. Isolated and operation fixtures from waves B and C still match. Skeleton byte fixtures from subtask 3 still match aside from docs.

## Non-goals

- Isolated `commit_push`, plugin UI, worktree locks.
- Deleting platform-pack specialist `content.md` or native-agent generation.
- Removing `skill-bill new/show/fill/render` authoring CLI.
- Migrating `bill-monitor` to an operation.
- Deleting `bill-code-review-inline`.

## Dependency notes

- Depends on subtasks 6, 7, 9, 10, 11, and 12 so every dispatcher target exists.
  Does not depend on subtask 4. Recheck install catalog tests at start. Local
  clone for Spotless and `./install.sh`.

## Validation strategy

Catch: a second listed skill; `/bill-monitor` still documented; dispatcher
missing a phase or operation. Cover with an install-catalog test, a docs grep
in that test, and a dispatcher routing table test. Run
`cd runtime-kotlin && ./gradlew check` plus CLI and infra-skills. Run
`./install.sh`. Run `skill-bill operation:unit-test-value-check` on changed
tests.

## Next path

After this subtask, `skill-bill goal SKILL-380` completes the goal and opens the PR.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_13_single-dispatcher-and-catalog.md
