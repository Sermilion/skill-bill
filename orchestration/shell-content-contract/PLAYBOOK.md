---
name: shell-content-contract
description: Versioned schema contract between the governed code-review shell and optional user-owned platform packs. Platform packs declare the contract version they target; the shell loader validates manifests and content against this schema.
---

# Shared Shell Content Contract

This is the canonical shell+content contract. The governed code-review shell
(`skills/base/bill-code-review/SKILL.md`) owns ceremony, orchestration, output
structure, telemetry, and contract enforcement. Optional platform packs under
`platform-packs/<platform>/` own extension-specific reviewer reasoning when
present. This file specifies the boundary between the two.

Skills consume this file through sibling symlinks (e.g. `shell-content-contract.md`
inside the shell skill directory), so changes here propagate to every linked
skill immediately.

Do not reference this repo-relative path directly from installable skills — use
the sibling symlink instead.

## Contract Version

The current shell contract version is **`1.0`**.

- The shell pins its target version. Platform packs must declare the same version.
- Any platform pack whose `contract_version` does not equal the shell's version
  must cause the shell loader to fail loudly with a migration message that
  includes both versions and the offending pack slug.
- Contract versions follow `MAJOR.MINOR`. Major changes are breaking; minor
  changes are additive and do not break existing packs.

## Required Platform Manifest (`platform.yaml`)

Every optional platform pack lives at
`platform-packs/<platform-slug>/platform.yaml`.

Required top-level fields:

- `platform` — the platform slug. Must match the enclosing directory name.
- `contract_version` — the shell contract version this pack targets. Must be
  the string `1.0` today. The loader rejects mismatches.
- `routing_signals` — an object declaring how the router should detect this
  platform. Required sub-fields:
  - `strong` — list of strings. Each entry is a strong signal (a path marker,
    file extension, dependency coordinate, or language-level marker) that
    indicates the platform when seen in the review scope.
  - `tie_breakers` — list of strings describing post-detection rules that
    disambiguate this platform against overlapping optional extensions.
  - `addon_signals` — optional list of signal hints used by governed add-ons
    that belong to this platform. May be an empty list.
- `declared_code_review_areas` — list of area slugs. Each entry must be one of
  the approved areas: `architecture`, `performance`, `platform-correctness`,
  `security`, `testing`, `api-contracts`, `persistence`, `reliability`, `ui`,
  `ux-accessibility`. This list is the source of truth for specialist
  code-review skills that actually exist in the pack. The list may be empty
  for meta packs (e.g. self-config) that have no specialist areas or for a
  brand-new platform pack that starts with only its baseline review skill.
- `declared_files` — object mapping logical content slots to content file
  paths, relative to the platform pack root. Required keys:
  - `baseline` — the per-platform baseline review content file path (the
    orchestrator-equivalent skill content).
  - `areas` — object mapping each entry of `declared_code_review_areas` to
    its content file path.
- `governs_addons` — optional boolean. Packs that own governed add-ons must
  set this to `true`. Defaults to `false` when omitted. (Used by internal
  tooling; does not affect the shell's loud-fail behavior.)

Optional top-level fields:

- `display_name` — human-readable label for installers and docs.
  When present, installers should show this label instead of auto-formatting
  the slug.
- `notes` — free-form maintainer notes.
- `declared_quality_check_file` — path (string) to a per-platform
  quality-check SKILL.md file, relative to the platform pack root. When
  present, the shell loader validates the referenced file against the
  quality-check content contract (see below). Omitting the key is valid —
  the shell contract version stays `1.0` and packs without the key remain
  contract-compliant. Shells must not assume another optional pack will supply
  replacement content when this key is omitted.

## Required Content Files

Every path declared in `declared_files` must exist on disk relative to the
platform pack root.

Each declared content file must be a Markdown file whose canonical entrypoint
remains `SKILL.md`. The declared `SKILL.md` file must carry the YAML
frontmatter block (`---` ... `---`). It may either:

- contain the full authored content itself (legacy single-file layout), or
- act as a thin bootstrap that points at sibling `implementation.md`.

When the canonical bootstrap points at `implementation.md`, the loader
validates the required H2 sections from `implementation.md` while continuing
to validate frontmatter from `SKILL.md`. Manifests still declare the
`.../SKILL.md` path, not `implementation.md`.

The active content body must contain all of the following H2 sections:

- `## Description`
- `## Specialist Scope`
- `## Inputs`
- `## Outputs Contract`
- `## Execution Mode Reporting`
- `## Telemetry Ceremony Hooks`

Section order is not enforced, but each section heading must appear exactly as
written (case-sensitive, H2 only).

The baseline file declared by `declared_files.baseline` is an installable
skill. Its frontmatter `name` must be `bill-<slug>-code-review`, and its
frontmatter `description` must be non-empty.

Baseline code-review files own the fallback behavior for approved areas:

- When a triggered approved area is present in `declared_code_review_areas`,
  the baseline skill may route that area to the matching installed
  `bill-<slug>-code-review-<area>` specialist.
- When a triggered approved area is not declared yet, the baseline skill must
  still review that area inline instead of silently dropping coverage or
  pretending the specialist already exists.

Content files may include additional H2 sections beyond the required set.
Sibling sidecar files remain anchored to the skill directory that contains
canonical `SKILL.md`; the split layout does not introduce any alternate
runtime directory or manifest path rules.

## Required Content File (quality-check)

When a platform pack declares the optional `declared_quality_check_file`
top-level key, the canonical referenced `SKILL.md` file may either contain
the full content directly or bootstrap to sibling `implementation.md`. The
active content body must contain all of the
following H2 sections:

- `## Description`
- `## Execution Steps`
- `## Fix Strategy`
- `## Execution Mode Reporting`
- `## Telemetry Ceremony Hooks`

The quality-check content contract is intentionally narrower than the
code-review contract: the shared `bill-quality-check` shell is horizontal
and does not require the `## Specialist Scope`, `## Inputs`, or
`## Outputs Contract` sections.

Section order is not enforced, but each section heading must appear
exactly as written (case-sensitive, H2 only). Content files may include
additional H2 sections beyond the required set.

The quality-check file declared by `declared_quality_check_file` is also an
installable skill. Its frontmatter `name` must start with
`bill-<slug>-quality-check`, and its frontmatter `description` must be
non-empty.

## Loud-Fail Rules

The shell loader must refuse to run when any of the following conditions
apply. Each condition maps to a specific named exception. No silent fallback
is ever permitted.

- Missing `platform.yaml` → `MissingManifestError`.
- `contract_version` missing, malformed, or not equal to the shell's version
  → `ContractVersionMismatchError`. The message must include both the
  shell's expected version and the pack's declared version.
- Required manifest field missing or invalid (missing `platform`, invalid
  `declared_code_review_areas`, invalid `declared_files` map, etc.) →
  `InvalidManifestSchemaError`.
- A file path declared under `declared_files` does not exist →
  `MissingContentFileError`. The message must include the slot key and the
  resolved path.
- A declared content file is missing one of the required H2 sections →
  `MissingRequiredSectionError`. The message must include the missing
  section heading and the file path.
- An installable declared skill file has missing or incorrect frontmatter →
  `InvalidContentFrontmatterError`. The message must include the expected
  skill name and the file path.

Every error message must name the specific artifact at fault (pack slug,
file path, section heading, or version string) so operators can repair the
issue without guessing.

### Loud-Fail Rules (quality-check)

The `bill-quality-check` shell resolves the per-platform quality-check file
through a dedicated loader (`skill_bill.shell_content_contract.load_quality_check_content`).
The loader enforces two additional loud-fail rules when a pack declares the
optional `declared_quality_check_file` key:

- The file referenced by `declared_quality_check_file` does not exist →
  `MissingContentFileError`. The message must include the pack slug and the
  resolved file path.
- The canonical bootstrap references `implementation.md` but the file does
  not exist → `MissingContentFileError`. The message must include the
  bootstrap path and the missing implementation path.
- The declared quality-check content file is missing one of the required H2
  sections listed above → `MissingRequiredSectionError`. The message must
  include the missing section heading and the file path.

Calling `load_quality_check_content` on a pack whose
`declared_quality_check_file` is `None` also raises
`MissingContentFileError` rather than silently returning nothing — callers
must gate the call on `pack.declared_quality_check_file is not None` and
report the unsupported-extension state explicitly.

## Discovery Semantics

The shell loader, validator, and stack-routing playbook all share a common
discovery algorithm:

1. Walk `platform-packs/` for immediate subdirectories when the directory
   exists.
2. For each candidate slug, load `platform-packs/<slug>/platform.yaml` via the
   loader.
3. Validate each pack against this contract.
4. If the directory is missing or no pack directories are present, discovery
   returns no packs. That zero-pack result is valid and must be reported as a
   framework-only core state rather than as an error.
5. The routed skill name for a platform pack with slug `<slug>` is
   `bill-<slug>-code-review`. Installers and runtime skills must preserve this
   contract so existing user-facing commands keep working.

Discovery must not hardcode platform names. Any routing decision that cares
about ordering must read priority from each pack's manifest, not from an
enumerated list.

## New-skill Scaffolding

The payload contract that drives the new-skill scaffolder lives in the
sibling `SCAFFOLD_PAYLOAD.md`. It specifies the required JSON shape, the
version handshake, the supported `kind` values, the pre-shell family list,
and the loud-fail exception catalog. The scaffolder refuses to run when the
payload does not conform to that contract.

## Relationship To Stack Routing

`orchestration/stack-routing/PLAYBOOK.md` is the user-facing routing
playbook. It defines the signal collection order and the tie-breaker rules in
prose. The discovery algorithm above is the machine-readable mirror of that
prose contract. Stack-routing authors must keep the two in sync: when a pack
changes `routing_signals`, the prose description in stack-routing.md must not
contradict it.
