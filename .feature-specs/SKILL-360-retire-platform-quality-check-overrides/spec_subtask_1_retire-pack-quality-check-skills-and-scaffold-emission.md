# SKILL-360 Subtask 1 - Retire pack quality-check skills and scaffold emission

Parent spec: [.feature-specs/SKILL-360-retire-platform-quality-check-overrides/spec.md](spec.md)
Issue key: SKILL-360

## Scope

Deliver the whole feature in this commit.

Delete every shipped `platform-packs/<slug>/quality-check/` tree and drop `declared_quality_check_file` from those manifests. Stop platform-pack scaffold planning, rendering, rollback, and manifest edits from creating a quality-check skill. Rewrite `skills/bill-code-check/content.md` and `orchestration/skill-classes/quality-check-shell.yaml` so the shell selects the dominant pack and runs that pack's `validation_gate` collect-all and confirmation argv. Retire `quality-check-leaf` matching and install sidecar staging for pack checkers. Update authoring, skill-remove, fixtures, and docs so a new pack is not required to invent a checker skill. Keep `skill-bill remove` able to strip a leftover `declared_quality_check_file` if one still exists. Record in pack or runtime `agent/decisions.md` that quality-check commands are pack `validation_gate` argv, not pack skills.

## Acceptance Criteria

1. No shipped pack under `platform-packs/` contains a `quality-check/` skill directory or a `declared_quality_check_file` key.
2. `skill-bill new` for `kind: platform-pack` (wizard, `--payload`, and MCP `new_skill_scaffold`) does not write a quality-check skill path, does not set `declared_quality_check_file`, and does not register quality-check pointers.
3. `bill-code-check` collect-all is exactly the dominant pack `validation_gate.collect_all_full_gate_command`, and confirmation is exactly that pack's cache-bypassing collect-all. It does not read a pack quality-check sidecar and does not rediscover a different full-suite command.
4. A dominant pack with no `validation_gate` fails quality-check with a typed missing-gate error. It does not fall back to a sidecar, a conventional task name, or another pack.
5. Install staging does not copy pack quality-check files into `bill-code-check` as internal sidecars. Quality-check telemetry `routed_skill` is `bill-code-check`.
6. Docs that currently tell authors to add a pack quality-check skill (`AGENTS.md`, `docs/skill-source-generation.md`, `docs/getting-started.md`, `docs/getting-started-for-teams.md`, `orchestration/shell-content-contract/SCAFFOLD_PAYLOAD.md`) describe pack `validation_gate` as the quality-check contract instead.

## Non-goals

No code-review specialist removal, no shipped gate argv changes, no invented discovery fallback, no MCP telemetry-tool removal.

## Dependency notes

None. Starts from `main`.

## Validation strategy

Name the regression before each test: scaffold still emitting `quality-check/bill-<slug>-code-check`; install still staging that sidecar; routing still requiring `declared_quality_check_file`; a missing gate treated as success; `routed_skill` remaining a pack checker name. Update fixture packs and parity tests that currently require a checker file. Run targeted scaffold, install, CLI, and skill-class tests, then the pack-declared quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

None. This is the only subtask.

## Spec Path

.feature-specs/SKILL-360-retire-platform-quality-check-overrides/spec_subtask_1_retire-pack-quality-check-skills-and-scaffold-emission.md
