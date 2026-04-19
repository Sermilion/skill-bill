---
name: scaffold-payload
description: Payload schema for the new-skill scaffolder (SKILL-15). Documents the JSON contract consumed by `skill-bill new-skill --payload`, the `new_skill_scaffold` MCP tool, and the `bill-create-skill` skill.
---

# Scaffold Payload Contract

This is the canonical payload schema for the new-skill scaffolder. Every
caller of `skill_bill.scaffold.scaffold(payload)` — the CLI, the MCP tool,
and the `bill-create-skill` skill — ships a payload that conforms to
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
  - `"horizontal"` — placed under `skills/<name>/SKILL.md`.
  - `"platform-override-piloted"` — placed under
    `platform-packs/<slug>/<family>/<name>/SKILL.md` plus a manifest edit
    for shelled families. Pre-shell families are placed under
    `skills/<platform>/<name>/SKILL.md` with an interim-location note.
  - `"platform-pack"` — creates a new `platform-packs/<slug>/` root with a
    generated baseline `code-review` skill, a default `quality-check` skill,
    thin `feature-implement` / `feature-verify` platform stubs, and a freshly
    rendered `platform.yaml`.
  - `"code-review-area"` — placed under
    `platform-packs/<slug>/code-review/<name>/SKILL.md` plus additions to
    `declared_code_review_areas`, `declared_files.areas`, and
    `area_metadata` in the owning `platform.yaml`.
  - `"add-on"` — placed at `platform-packs/<platform>/addons/<name>.md` (flat; no
    sub-directory).
- `name` — the canonical `bill-...` slug for the new skill. For
  `platform-pack` and `code-review-area`, the scaffolder derives canonical
  names when this key is omitted; if provided, the value must still match the
  canonical shape.

## Conditionally Required Keys

- `platform` — required for `platform-override-piloted`, `code-review-area`,
  `platform-pack`, and `add-on`.
  - For `platform-override-piloted`, `code-review-area`, and `add-on`, it
    must name an existing platform slug (e.g. `kotlin`, `kmp`).
  - For `platform-pack`, it is the new platform slug to create.
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
- `routing_signals` — required for `platform-pack` only when the platform
  does not have a built-in preset. Must be a mapping with a non-empty
  `strong` list and optional `tie_breakers` / `addon_signals` lists. For
  known platforms such as `java` and `php`, the scaffolder can infer these
  defaults.

## Optional Keys

- `description` — one-line description copied into the frontmatter.
- `display_name` — human-friendly label for `platform-pack`. Defaults to a
  title-cased version of `platform`.
- `skeleton_mode` — `starter` or `full` for `platform-pack`. Defaults to
  `starter`.
  - `starter` creates the pack root, baseline `code-review`, default
    `quality-check`, and thin `feature-implement` / `feature-verify` stubs.
  - `full` also creates bare specialist stubs for every approved
    code-review area and registers them in the generated manifest, including
    the `area_metadata` entries used to auto-render governed `## Descriptor`
    sections.
- `governs_addons` — optional boolean for `platform-pack`. Defaults to
  `false`.
- `content_body` — optional free-form Markdown body written verbatim to the
  sibling `content.md` file. When present, the scaffolder trims trailing
  whitespace and writes the body plus a single trailing newline. When absent,
  a minimal deterministic placeholder is written so the validator passes and
  the author has a starting point. Only applies to kinds that produce a
  `SKILL.md` (`horizontal`, `platform-override-piloted`, `platform-pack`,
  `code-review-area`); the `add-on` kind is a flat file and does not get a
  `content.md` sibling.
- `repo_root` — absolute path override used by tests. Defaults to the
  current working directory.

## Worked Examples

### Horizontal skill

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "horizontal",
  "name": "bill-new-horizontal",
  "description": "Use for ..."
}
```

### Platform-override (piloted, code-review family)

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-override-piloted",
  "name": "bill-kotlin-code-review-new",
  "platform": "kotlin",
  "family": "code-review",
  "description": "..."
}
```

### Platform-override (piloted, quality-check family)

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-override-piloted",
  "name": "bill-kotlin-quality-check",
  "platform": "kotlin",
  "family": "quality-check"
}
```

This lands the skill at
`platform-packs/kotlin/quality-check/bill-kotlin-quality-check/SKILL.md` and edits
the owning pack's `platform.yaml` to register
`declared_quality_check_file: quality-check/bill-kotlin-quality-check/SKILL.md`.
The scaffolded skill links the governed sibling sidecars
`stack-routing.md`, `telemetry-contract.md`, and `shell-ceremony.md`.

### New platform pack

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-pack",
  "platform": "java",
  "display_name": "Java",
  "skeleton_mode": "starter",
  "description": "Use when reviewing Java server and library changes."
}
```

This creates `platform-packs/java/platform.yaml`,
`platform-packs/java/code-review/bill-java-code-review/SKILL.md`,
`platform-packs/java/quality-check/bill-java-quality-check/SKILL.md`,
`skills/java/bill-java-feature-implement/SKILL.md`, and
`skills/java/bill-java-feature-verify/SKILL.md`. The quality-check skill
and the thin pre-shell feature stubs are scaffolded by default. The built-in
`java` preset supplies the routing signals, and the follow-on
`code-review-area` flow can add specialists such as architecture or
performance without manual manifest or README edits.

### Full platform skeleton

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "platform-pack",
  "platform": "java",
  "skeleton_mode": "full"
}
```

This creates the starter Java pack plus bare specialist stubs for every
approved code-review area (`architecture`, `performance`,
`platform-correctness`, `security`, `testing`, `api-contracts`,
`persistence`, `reliability`, `ui`, `ux-accessibility`). The generated
files are intentionally minimal so the user can enrich the authored
sidecars afterwards.

### Code-review area

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "code-review-area",
  "name": "bill-kotlin-code-review-api-contracts",
  "platform": "kotlin",
  "area": "api-contracts"
}
```

### Add-on

```json
{
  "scaffold_payload_version": "1.0",
  "kind": "add-on",
  "name": "android-paging",
  "platform": "kmp"
}
```

## Loud-Fail Exception Catalog

All exceptions derive from `skill_bill.scaffold_exceptions.ScaffoldError`:

- `ScaffoldPayloadVersionMismatchError` — `scaffold_payload_version`
  disagrees with the scaffolder.
- `InvalidScaffoldPayloadError` — missing required key, malformed value, or
  unapproved area slug.
- `UnknownSkillKindError` — `kind` is not one of the supported kinds.
- `UnknownPreShellFamilyError` — pre-shell family not in
  `PRE_SHELL_FAMILIES`.
- `MissingPlatformPackError` — platform pack (`platform-packs/<slug>/`)
  does not exist; create a conforming `platform.yaml` before retrying.
- `MissingSupportingFileTargetError` — a file name declared in
  `RUNTIME_SUPPORTING_FILES` for this skill is not registered in
  `SUPPORTING_FILE_TARGETS`; register the target or drop the reference.
  The scaffolder never silently skips supporting-file symlinks.
- `SkillAlreadyExistsError` — target path already occupied.
- `ScaffoldValidatorError` — post-scaffold validator run failed; all
  staged changes are rolled back.
- `ScaffoldRollbackError` — rollback itself failed (the only failure mode
  that may leave the repo partially mutated).

## SKILL-21: Shell+content split (v1.1)

SKILL-21 added sibling `content.md` authoring and bumped the shell contract
to `1.1`. Callers of the scaffold payload do not need to change, but the
output changes:

- Every kind that produces a `SKILL.md` now also writes a sibling
  `content.md` under the same skill directory.
- The generated `SKILL.md` carries two additional frontmatter keys,
  `shell_contract_version` and `template_version`.
- The generated `SKILL.md` carries a new required `## Execution` H2 whose
  body is byte-identical across every governed skill and links to
  `content.md`.

Loud-fail exceptions added to the shell contract catalog (raised by the
loader and surfaced by the validator, not by the scaffolder itself):

- `ContractVersionMismatchError` — pack declares an outdated
  `contract_version`; the message points at
  `scripts/migrate_to_content_md.py`.
- `MissingContentBodyFileError` — sibling `content.md` is missing for a
  governed skill.
- `InvalidExecutionSectionError` — the `## Execution` section body is not
  the canonical byte-identical form.
