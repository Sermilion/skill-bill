---
issue_key: SKILL-19
feature_name: bootstrap-implementation-split-for-skills
feature_size: LARGE
status: Complete
created: 2026-04-18
depends_on: SKILL-14 (code-review shell pilot -- shipped), SKILL-15 (new-skill scaffolder -- shipped), SKILL-16 (quality-check shell pilot -- shipped)
---

# SKILL-19 -- Bootstrap/Implementation Split For Skills

## Status
Complete

## Sources
- Feature implementation pre-planning briefing for `SKILL-19 Part 2: Bootstrap/Implementation Split For Skills`
- Repository standards from `CLAUDE.md` and `AGENTS.md`
- Existing scaffold, install, validator, and shell-content contract code in this repo

## Acceptance criteria
1. New scaffolded skills emit both `SKILL.md` and `implementation.md`.
2. `SKILL.md` is a thin bootstrap that references the active implementation file.
3. Install and discovery continue to work with no agent-path or manifest-path changes.
4. Validation fails when a bootstrap references a missing implementation file.
5. Platform-pack manifests still declare `.../SKILL.md`, not `implementation.md`.
6. Existing sidecar contract rules continue to apply under the split layout.
7. Authored implementation text supplied during scaffolding is written into `implementation.md`.
8. At least one real migrated skill demonstrates the split layout works end to end.
9. Tests cover both legacy single-file acceptance and split-layout acceptance/rejection paths.

## Non-goals
- No changes to agent install paths.
- No replacement of `SKILL.md` as the discovered runtime file.
- No dynamic runtime implementation selection in Part 2.
- No manifest migration away from `SKILL.md`.
- No silent support for arbitrary undocumented layouts.

## Consolidated spec
Introduce a two-layer skill layout:
- `SKILL.md` becomes the stable runtime/bootstrap entrypoint.
- The actual authored prompt lives in a referenced implementation file such as `implementation.md`.

This preserves the current install/discovery model, while allowing teams to swap or test different prompt implementations without changing the canonical skill path or package shape.

### Problem
Today `SKILL.md` serves too many roles at once:
- install/discovery entrypoint
- validator target
- runtime prompt
- scaffolder output
- long-form authored implementation

That creates three problems:
1. prompt experiments require editing the canonical entrypoint
2. scaffolder-owned structure and human-authored behavior are mixed together
3. shared contracts are duplicated across many skill files

The desired model is:
- stable bootstrap surface for tooling
- swappable implementation text for authors
- stronger separation between runtime contract and authored behavior

### Goals
- Keep `SKILL.md` as the canonical install/discovery entrypoint.
- Move most authored behavior into `implementation.md`.
- Allow swapping implementations without renaming or relocating the skill.
- Keep validator-backed governance.
- Preserve existing pack and install semantics.
- Make future A/B or experimental prompt variants possible.

### Current constraints
The current repo hard-codes `SKILL.md` as the installable skill entrypoint in:
- `skill_bill/install.py:120`
- `skill_bill/install.py:356`
- `scripts/validate_agent_configs.py:289`

The shell/content contract also validates section presence in declared skill files, which today are `SKILL.md` files under `skills/` and `platform-packs/`.

### Proposal

#### New skill shape
Each installable skill directory continues to contain `SKILL.md`, but the authored implementation moves to a sibling file. Minimum layout:

```text
<skill-dir>/
  SKILL.md
  implementation.md
```

Optional future layout:

```text
<skill-dir>/
  SKILL.md
  implementation.md
  implementations/
    default.md
    compact.md
    experimental.md
```

#### Runtime model
`SKILL.md` is a short bootstrap document that:
- identifies the skill via frontmatter
- states that `implementation.md` is the primary authored implementation
- references required sidecars such as `telemetry-contract.md`, `review-orchestrator.md`, `stack-routing.md`
- contains only the minimum stable bootstrapping and governance text

`implementation.md` contains the actual prompt behavior, including:
- task workflow
- domain-specific instructions
- examples
- output rules that are specific to the skill

#### Contract split
Move content into three buckets:
1. `SKILL.md`
- bootstrap only
- stable
- low-churn
- install/discovery target
2. `implementation.md`
- skill-specific runtime behavior
- high-churn
- swappable for experiments
3. sidecars
- shared contracts reused across many skills
- examples: telemetry, routing, delegation, review merge rules

#### Required bootstrap contract
Every `SKILL.md` must include:
- YAML frontmatter with `name` and `description`
- explicit reference to `implementation.md`
- explicit reference to any required sidecars for that family
- enough text to tell the agent to read those sibling files before acting

Example shape:

```md
---
name: bill-php-code-review
description: Use when conducting a thorough PHP PR code review across backend/server projects.
---

# bill-php-code-review

Read `implementation.md` before acting.

This skill also depends on these sibling runtime files:
- `stack-routing.md`
- `review-orchestrator.md`
- `review-delegation.md`
- `telemetry-contract.md`
```

#### Required implementation contract
Every `implementation.md` must:
- exist beside `SKILL.md`
- be treated as required by validator and scaffolder
- contain the actual authored behavior for the skill family
- contain any family-specific sections still required at runtime unless delegated to sidecars

For code-review skills, Part 2 should prefer moving `Inputs`, `Outputs Contract`, and similar repeated content into shared sidecars where possible, keeping only truly skill-specific logic in `implementation.md`.

#### Future variant support
Part 2 should reserve, but not require, variant layouts. Allowed but optional:
- `implementations/default.md`
- `implementations/compact.md`
- `implementations/experimental.md`

Rules:
- `SKILL.md` must point to exactly one active implementation.
- If variants exist, one must be designated active.
- No runtime auto-selection in Part 2.
- Switching variants is a repo edit, not a runtime flag.

#### Scaffolder changes
`skill_bill/scaffold.py` and `skill_bill/scaffold_template.py` must change to emit:
- `SKILL.md` bootstrap
- `implementation.md`
- existing sidecar symlinks

Scaffolder responsibilities:
- own bootstrap template
- own scaffolder-owned ceremony text only if it remains inline
- write the initial implementation file from authored content
- support future variant creation without changing install semantics

The scaffolder must now ingest authored implementation text rather than discarding it and only deriving description.

#### Validator changes
`scripts/validate_agent_configs.py` and the shell-content contract loader must validate the assembled runtime layout, not only raw `SKILL.md`.

New checks:
- every installable skill directory must contain `SKILL.md`
- every bootstrap skill must reference an existing implementation file
- every referenced implementation file must exist
- family-required sidecars must still exist
- bootstrap/implementation references must use sibling files only
- no repo-relative runtime references
- platform-pack manifests still declare `.../SKILL.md`, not `implementation.md`

For shelled platform packs, `load_platform_pack()` should continue loading `SKILL.md` as the canonical declared file, then validate the referenced implementation as part of the skill package contract.

#### Install/discovery behavior
No install/discovery behavior changes in Part 2.
- installers still symlink the skill directory
- discovery still finds `SKILL.md`
- manifests still point to `SKILL.md`
- agent homes still receive the skill directory as before

#### Migration plan
Phase 1
Add bootstrap+implementation support while preserving old single-file skills.
- validator accepts both legacy and split layouts
- scaffolder emits split layout for new skills
- legacy skills remain valid

Phase 2
Migrate core/base skills and pack skills incrementally.
- add `implementation.md`
- reduce `SKILL.md` to bootstrap
- move shared repeated content into sidecars where appropriate

Phase 3
Make split layout the default governed pattern.
- validator warns or rejects new legacy single-file skills
- scaffolder no longer emits fully-authored single-file skills

#### Backward compatibility
Part 2 should be additive first.
Legacy skill directories with only `SKILL.md` remain valid during migration.
A later follow-up can decide whether single-file skills become disallowed.

#### Current feature-specific constraints
The current install/discovery model must remain stable:
- no agent-path changes
- no manifest-path changes
- no dynamic runtime implementation selection
- no silent support for undocumented layouts
