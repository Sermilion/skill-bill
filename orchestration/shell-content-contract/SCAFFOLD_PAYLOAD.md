---
name: scaffold-payload
description: Payload schema for the new-skill scaffolder (SKILL-15). Documents the JSON contract consumed by `skill-bill new-skill --payload`, the `new_skill_scaffold` MCP tool, and the bill-new-skill-all-agents skill.
---

# Scaffold Payload Contract

This is the canonical payload schema for the new-skill scaffolder. Every
caller of `skill_bill.scaffold.scaffold(payload)` — the CLI, the MCP tool,
and the `bill-new-skill-all-agents` skill — ships a payload that conforms to
this schema. Mismatches raise specific named exceptions and abort the run;
no silent coercion.

## Versioning

- Required key: `scaffold_payload_version`.
- Current value: `"1.0"`.
- Any payload that declares a different version raises
  `ScaffoldPayloadVersionMismatchError`. Bump the scaffolder and every caller
  in lockstep when the contract changes.
- Version follows `MAJOR.MINOR`. Minor changes are additive; major changes
  are breaking.

## Required Keys

Every payload MUST include:

- `scaffold_payload_version` — exact match for the scaffolder's expected
  version string.
- `kind` — one of:
  - `"horizontal"` — placed under `skills/base/<name>/SKILL.md`.
  - `"platform-override-piloted"` — placed under
    `platform-packs/<slug>/<family>/<name>/SKILL.md` plus a manifest edit
    for shelled families. Pre-shell families are placed under
    `skills/<platform>/<name>/SKILL.md` with an interim-location note.
  - `"code-review-area"` — placed under
    `platform-packs/<slug>/code-review/<name>/SKILL.md` plus additions to
    `declared_code_review_areas` and `declared_files.areas` in the owning
    `platform.yaml`.
  - `"add-on"` — placed at `platform-packs/<platform>/addons/<name>.md` (flat; no
    sub-directory).
- `name` — the canonical `bill-...` slug for the new skill.

## Conditionally Required Keys

- `platform` — required for `platform-override-piloted`, `code-review-area`,
  and `add-on`. Must be the slug of an optional extension when one exists.
- `family` — required for `platform-override-piloted`. One of the known
  families:
  - Shelled: `code-review`, `quality-check`.
  - Pre-shell (see :data:`skill_bill.constants.PRE_SHELL_FAMILIES`):
    `feature-implement`, `feature-verify`.
- `area` — required for `code-review-area`. Must be one of the approved
  areas in :data:`skill_bill.shell_content_contract.APPROVED_CODE_REVIEW_AREAS`:
  `architecture`, `performance`, `platform-correctness`, `security`,
  `testing`, `api-contracts`, `persistence`, `reliability`, `ui`,
  `ux-accessibility`.

## Optional Keys

- `description` — one-line description copied into the frontmatter.
- `implementation_text` — optional authored Markdown written verbatim into
  the scaffolded `implementation.md`. When omitted, the scaffolder emits the
  default required sections for the selected family.
- `platform_manifest` — object used only when scaffolding the first baseline
  code-review skill for a brand-new shelled platform pack. When present, the
  scaffolder creates `platform-packs/<slug>/platform.yaml` atomically before
  validating the new baseline skill. The object must include:
  - `platform` — exact pack slug; must match the payload `platform`.
  - `contract_version` — exact shell contract version (`"1.0"` today).
  - `routing_signals.strong` — list of strong detection markers.
  - `routing_signals.tie_breakers` — list of tie-breaker rules.
  - `routing_signals.addon_signals` — optional list of governed add-on slugs.
  - `declared_code_review_areas` — must be `[]` for the first baseline skill.
    The baseline still owns approved area coverage inline; add specialist
    area skills later and register them here only when they actually exist.
  - `declared_files.baseline` — relative path to the baseline skill file.
  - `declared_files.areas` — must be `{}` for the first baseline skill.
  - `display_name`, `governs_addons`, and `notes` are optional.
- `repo_root` — absolute path override used by tests. Defaults to the
  current working directory.

## Worked Examples

### Horizontal skill

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "horizontal",
  "name": "bill-new-horizontal",
  "description": "Use for ...",
  "implementation_text": "## Project Overrides\n\n..."
}
```

### Platform-override (piloted, code-review family)

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-override-piloted",
  "name": "bill-example-code-review-new",
  "platform": "example-pack",
  "family": "code-review",
  "description": "..."
}
```

### Platform-override (new baseline code-review pack)

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-override-piloted",
  "name": "bill-kmp-code-review",
  "platform": "kmp",
  "family": "code-review",
  "description": "Use when conducting Android/KMP code review.",
  "platform_manifest": {
    "platform": "kmp",
    "contract_version": "1.0",
    "display_name": "Kmp",
    "governs_addons": true,
    "routing_signals": {
      "strong": ["@Composable", "kmp"],
      "tie_breakers": ["Prefer the kmp pack when Android/KMP markers are present."],
      "addon_signals": ["android-compose"]
    },
    "declared_code_review_areas": [],
    "declared_files": {
      "baseline": "code-review/bill-kmp-code-review/SKILL.md",
      "areas": {}
    }
  }
}
```

### Platform-override (piloted, quality-check family)

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-override-piloted",
  "name": "bill-example-quality-check",
  "platform": "example-pack",
  "family": "quality-check"
}
```

This lands the skill at
`platform-packs/example-pack/quality-check/bill-example-quality-check/SKILL.md` plus
`platform-packs/example-pack/quality-check/bill-example-quality-check/implementation.md`,
and edits
the owning pack's `platform.yaml` to register
`declared_quality_check_file: quality-check/bill-example-quality-check/SKILL.md`.
The scaffolded skill links the sibling sidecars `stack-routing.md` and
`telemetry-contract.md` just like the shelled code-review example above.

### Code-review area

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "code-review-area",
  "name": "bill-example-code-review-api-contracts",
  "platform": "example-pack",
  "area": "api-contracts"
}
```

### Add-on

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "add-on",
  "name": "example-addon",
  "platform": "example-pack"
}
```

## Authoring Notes

- Every scaffolded skill keeps `SKILL.md` as the canonical install/discovery
  entrypoint and writes the active authored body to sibling
  `implementation.md`.
- A brand-new platform pack starts with only its baseline
  `bill-<slug>-code-review` skill plus `platform.yaml`.
- Do not treat future specialist areas as already-installed skill files in
  that first baseline. Describe them as approved review areas that the
  baseline can run inline until a later scaffold adds the specialist and
  registers it under `declared_code_review_areas`.

## Loud-Fail Exception Catalog

All exceptions derive from `skill_bill.scaffold_exceptions.ScaffoldError`:

- `ScaffoldPayloadVersionMismatchError` — `scaffold_payload_version`
  disagrees with the scaffolder.
- `InvalidScaffoldPayloadError` — missing required key, malformed value, or
  unapproved area slug.
- `UnknownSkillKindError` — `kind` is not one of the four supported kinds.
- `UnknownPreShellFamilyError` — pre-shell family not in
  `PRE_SHELL_FAMILIES`.
- `MissingPlatformPackError` — optional platform pack (`platform-packs/<slug>/`)
  does not exist; create or import a conforming `platform.yaml` before retrying.
- `MissingSupportingFileTargetError` — a file name declared in
  `RUNTIME_SUPPORTING_FILES` for this skill is not registered in
  `SUPPORTING_FILE_TARGETS`; register the target or drop the reference.
  The scaffolder never silently skips supporting-file symlinks.
- `SkillAlreadyExistsError` — target path already occupied.
- `ScaffoldValidatorError` — post-scaffold validator run failed; all
  staged changes are rolled back.
- `ScaffoldRollbackError` — rollback itself failed (the only failure mode
  that may leave the repo partially mutated).
