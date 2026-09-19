# SKILL-360 - retire-platform-quality-check-overrides

## Mode

single_spec

## Intended outcome

Quality checks run through `bill-code-check` against the dominant pack's `validation_gate`. Platform packs no longer ship a quality-check skill, and `skill-bill new` no longer creates one. A new platform is a pack, review skills, and a gate, not a second checker skill.

## Scope

Delete shipped pack quality-check `content.md` files and their `declared_quality_check_file` keys. Stop platform-pack scaffold (CLI, `--payload`, MCP `new_skill_scaffold`) from emitting `platform-packs/<slug>/quality-check/`. Rewrite `bill-code-check` and `orchestration/skill-classes/quality-check-shell.yaml` so the shell runs the dominant pack's collect-all and confirmation argv itself. Routing still selects the dominant pack from `platform.yaml`; it no longer loads or installs a pack checker sidecar.

Prepared in local mode on 2026-09-19. Baseline is `main` at `ae87634af`. This bundle prepares work only; the subtask starts pending.

## Acceptance Criteria

1. No shipped pack under `platform-packs/` contains a `quality-check/` skill directory or a `declared_quality_check_file` key.
2. `skill-bill new` for `kind: platform-pack` (wizard, `--payload`, and MCP `new_skill_scaffold`) does not write a quality-check skill path, does not set `declared_quality_check_file`, and does not register quality-check pointers.
3. `bill-code-check` collect-all is exactly the dominant pack `validation_gate.collect_all_full_gate_command`, and confirmation is exactly that pack's cache-bypassing collect-all. It does not read a pack quality-check sidecar and does not rediscover a different full-suite command.
4. A dominant pack with no `validation_gate` fails quality-check with a typed missing-gate error. It does not fall back to a sidecar, a conventional task name, or another pack.
5. Install staging does not copy pack quality-check files into `bill-code-check` as internal sidecars. Quality-check telemetry `routed_skill` is `bill-code-check`.
6. Docs that currently tell authors to add a pack quality-check skill (`AGENTS.md`, `docs/skill-source-generation.md`, `docs/getting-started.md`, `docs/getting-started-for-teams.md`, `orchestration/shell-content-contract/SCAFFOLD_PAYLOAD.md`) describe pack `validation_gate` as the quality-check contract instead.

## Constraints

- Follow `../../runtime-kotlin/ARCHITECTURE.md` design principles, `docs/code-principles.md`, `docs/observability-policy.md`, and AGENTS.md.
- Keep pack routing, Kotlin/KMP dominance, and shipped `validation_gate` argv unchanged.
- `declared_quality_check_file` may remain an unused optional schema field for one cycle so leftover custom packs still parse; runtime routing, install, and scaffold must not consume it.
- Kotlin under `runtime-kotlin` carries no `//` comments and no non-KDoc block comments.
- Wire and payload keys stay in `runtime-contracts`.
- Run `./install.sh` after changing source skills, renderer behavior, or support pointer generation.

## Non-goals

- Removing or rewriting pack code-review baselines, specialist areas, or add-ons.
- Changing shipped `validation_gate` commands, findings globs, or build vs collect-all distinctness.
- A generic command-discovery fallback that invents `./gradlew check`, `npm test`, or `xcodebuild` when the pack has no gate.
- Removing `quality_check_started` / `quality_check_finished` MCP tools or the quality-check telemetry family.
- Requiring `validation_gate` on packs that are never selected as the dominant quality-check stack.

## Validation strategy

Name the regression before each test: a new platform-pack scaffold that still writes `quality-check/bill-<slug>-code-check`; install that stages a pack checker sidecar under `bill-code-check`; `routeQualityCheck` throwing `MissingContentFileError` because `declared_quality_check_file` is absent; `bill-code-check` instructing a sidecar read; a pack without `validation_gate` succeeding quality-check; telemetry `routed_skill` still naming `bill-kotlin-code-check`. Cover scaffold dry-run and apply, install plan, quality-check routing, shell-class render of `bill-code-check`, and fixture packs that no longer need a checker file. Run targeted scaffold, install, and CLI tests, then the pack-declared quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Run `skill-bill goal SKILL-360`. The prepared manifest is the goal runner's input.
