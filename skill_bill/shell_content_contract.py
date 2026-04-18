"""Shell+content contract loader for optional governed code-review platform packs.

This module is the runtime authority for loading and validating user-owned
platform packs against the versioned contract documented in
``orchestration/shell-content-contract/PLAYBOOK.md``.

The loader is intentionally strict: malformed content raises a specific named
exception rather than silently falling back. Missing or empty
``platform-packs/`` is a valid framework-only state; callers should report it
explicitly instead of treating it as an error.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
import re


def _import_yaml():
  """Import PyYAML lazily so importing this module does not require it.

  The shell+content loader requires PyYAML at runtime, but it lives in a
  package that is frequently imported from tooling entry points with
  narrower deps. Loading YAML on demand keeps that boundary clean without
  silently ignoring a broken install.

  Raises:
    PyYAMLMissingError: when PyYAML cannot be imported. Callers get an
      actionable message instead of a raw ``ModuleNotFoundError`` traceback.
  """
  try:
    import yaml  # type: ignore[import-untyped]
  except ImportError as error:
    raise PyYAMLMissingError(
      "PyYAML is required to load platform packs. Install it via the project "
      "venv (`./.venv/bin/pip install pyyaml>=6`) or run the validator through "
      "`.venv/bin/python3 scripts/validate_agent_configs.py`."
    ) from error
  return yaml


SHELL_CONTRACT_VERSION: str = "1.0"
ZERO_PLATFORM_PACKS_MESSAGE_TEMPLATE: str = (
  "No optional platform packs discovered under '{path}'. "
  "Framework-only mode is active."
)

APPROVED_CODE_REVIEW_AREAS: frozenset[str] = frozenset(
  {
    "api-contracts",
    "architecture",
    "performance",
    "persistence",
    "platform-correctness",
    "reliability",
    "security",
    "testing",
    "ui",
    "ux-accessibility",
  }
)

REQUIRED_CONTENT_SECTIONS: tuple[str, ...] = (
  "## Description",
  "## Specialist Scope",
  "## Inputs",
  "## Outputs Contract",
  "## Execution Mode Reporting",
  "## Telemetry Ceremony Hooks",
)

# Required H2 sections for per-platform quality-check content files. The
# bill-quality-check shell is horizontal and does not require the three
# code-review-specific sections (Specialist Scope, Inputs, Outputs Contract).
REQUIRED_QUALITY_CHECK_SECTIONS: tuple[str, ...] = (
  "## Description",
  "## Execution Steps",
  "## Fix Strategy",
  "## Execution Mode Reporting",
  "## Telemetry Ceremony Hooks",
)

MANIFEST_FILENAME: str = "platform.yaml"
SKILL_IMPLEMENTATION_FILENAME: str = "implementation.md"

_SECTION_HEADING_PATTERN = re.compile(r"^##\s+[^\n]+$")
_FRONTMATTER_PATTERN = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
_SKILL_BOOTSTRAP_LINK_PATTERN = re.compile(
  r"\[implementation\.md\]\(implementation\.md\)"
)
# Fenced code-block markers recognized when scanning for H2 sections.
# Covers both triple-backtick and triple-tilde fences (with or without a
# language tag). A fence line must have the marker as the first non-space
# characters on the line.
_FENCE_PATTERN = re.compile(r"^\s*(?:```|~~~)")


class ShellContentContractError(Exception):
  """Base class for all contract failures.

  Callers (the shell, the validator, tests) catch this base type to surface
  any contract failure uniformly, but each concrete subclass names the
  specific failure mode so operators know exactly which artifact to fix.
  """


class MissingManifestError(ShellContentContractError):
  """Raised when a platform pack directory has no ``platform.yaml``."""


class InvalidManifestSchemaError(ShellContentContractError):
  """Raised when ``platform.yaml`` fails schema validation."""


class ContractVersionMismatchError(ShellContentContractError):
  """Raised when a pack's ``contract_version`` does not match the shell."""


class MissingContentFileError(ShellContentContractError):
  """Raised when a declared content file path does not exist on disk."""


class MissingRequiredSectionError(ShellContentContractError):
  """Raised when a declared content file is missing a required H2 section."""


class InvalidContentFrontmatterError(ShellContentContractError):
  """Raised when a declared installable skill file has invalid frontmatter."""


class PyYAMLMissingError(ShellContentContractError):
  """Raised when PyYAML is not installed in the active Python environment.

  The loader requires PyYAML to parse ``platform.yaml``. Instead of letting
  the raw ``ModuleNotFoundError`` bubble up, the loader catches it and
  raises this subclass with an actionable install message, so the CLI and
  the validator print a friendly error instead of a traceback.
  """


@dataclass(frozen=True)
class ResolvedSkillMarkdown:
  """Resolved bootstrap plus active markdown for an installable skill."""

  bootstrap_file: Path
  active_file: Path
  bootstrap_text: str = field(hash=False)
  active_text: str = field(hash=False)
  uses_split_layout: bool = False


@dataclass(frozen=True)
class RoutingSignals:
  """Normalized routing signals for a platform pack."""

  strong: tuple[str, ...]
  tie_breakers: tuple[str, ...]
  addon_signals: tuple[str, ...]


@dataclass(frozen=True)
class PlatformPack:
  """A loaded and validated platform pack.

  Attributes mirror the manifest schema but normalize collections into tuples
  so the loaded pack is hashable and immutable.
  """

  slug: str
  pack_root: Path
  contract_version: str
  routing_signals: RoutingSignals
  declared_code_review_areas: tuple[str, ...]
  declared_files: dict[str, Path] = field(hash=False)
  governs_addons: bool = False
  display_name: str | None = None
  notes: str | None = None
  declared_quality_check_file: Path | None = None

  @property
  def routed_skill_name(self) -> str:
    """Return the contract-preserving routed skill name for this pack."""

    return f"bill-{self.slug}-code-review"


@dataclass(frozen=True)
class InstallablePackSkill:
  """An installable skill declared by a platform pack."""

  skill_name: str
  skill_dir: Path
  source_file: Path
  family: str


def skill_bootstrap_references_implementation(markdown_text: str) -> bool:
  """Return True when a canonical ``SKILL.md`` points at ``implementation.md``."""

  return bool(_SKILL_BOOTSTRAP_LINK_PATTERN.search(markdown_text))


def resolve_active_skill_markdown(
  skill_file: Path | str,
  *,
  owner_label: str | None = None,
) -> ResolvedSkillMarkdown:
  """Resolve the active markdown file for a canonical installable skill.

  ``SKILL.md`` remains the canonical install/discovery file. When it contains
  a bootstrap reference to sibling ``implementation.md``, the implementation
  file becomes the active body for section validation and runtime reading.
  """
  bootstrap_file = Path(skill_file).resolve()
  bootstrap_text = bootstrap_file.read_text(encoding="utf-8")
  if not skill_bootstrap_references_implementation(bootstrap_text):
    return ResolvedSkillMarkdown(
      bootstrap_file=bootstrap_file,
      active_file=bootstrap_file,
      bootstrap_text=bootstrap_text,
      active_text=bootstrap_text,
      uses_split_layout=False,
    )

  implementation_file = bootstrap_file.parent / SKILL_IMPLEMENTATION_FILENAME
  if not implementation_file.is_file():
    artifact_label = f"{owner_label}: " if owner_label else ""
    raise MissingContentFileError(
      f"{artifact_label}skill bootstrap '{bootstrap_file}' references missing "
      f"implementation file '{implementation_file}'."
    )

  return ResolvedSkillMarkdown(
    bootstrap_file=bootstrap_file,
    active_file=implementation_file,
    bootstrap_text=bootstrap_text,
    active_text=implementation_file.read_text(encoding="utf-8"),
    uses_split_layout=True,
  )


def load_platform_pack(pack_root: Path | str) -> PlatformPack:
  """Load and validate a single platform pack.

  Args:
    pack_root: path to ``platform-packs/<slug>/``.

  Raises:
    MissingManifestError: when ``platform.yaml`` is absent.
    InvalidManifestSchemaError: when the manifest is malformed.
    ContractVersionMismatchError: when ``contract_version`` is wrong.
    MissingContentFileError: when a declared file does not exist.
    MissingRequiredSectionError: when a content file lacks a required
      H2 section.

  Returns:
    A validated :class:`PlatformPack`.
  """

  pack_root = Path(pack_root).resolve()
  slug = pack_root.name
  manifest_path = pack_root / MANIFEST_FILENAME

  if not manifest_path.is_file():
    raise MissingManifestError(
      f"Platform pack '{slug}': expected manifest at '{manifest_path}' but it is missing."
    )

  yaml = _import_yaml()
  try:
    raw = yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
  except yaml.YAMLError as error:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest '{manifest_path}' is not valid YAML: {error}"
    ) from error

  pack = _build_pack(slug=slug, pack_root=pack_root, manifest_path=manifest_path, raw=raw)
  validate_platform_pack(pack, contract_version=SHELL_CONTRACT_VERSION)
  return pack


def validate_platform_pack(pack: PlatformPack, contract_version: str) -> None:
  """Enforce loud-fail rules on a previously built pack.

  The function raises the first error it detects; callers that want to
  enumerate every failure across a tree should load packs individually and
  accumulate exceptions.
  """

  if pack.contract_version != contract_version:
    raise ContractVersionMismatchError(
      f"Platform pack '{pack.slug}': declares contract_version "
      f"'{pack.contract_version}' but the shell expects '{contract_version}'. "
      "Update the pack to the new schema or pin the shell to the pack's version."
    )

  expected_areas = set(pack.declared_code_review_areas)
  declared_area_files = pack.declared_files.get("areas", {})
  if not isinstance(declared_area_files, dict):
    raise InvalidManifestSchemaError(
      f"Platform pack '{pack.slug}': declared_files.areas must be a mapping."
    )
  missing_area_slots = expected_areas - set(declared_area_files.keys())
  if missing_area_slots:
    raise InvalidManifestSchemaError(
      f"Platform pack '{pack.slug}': declared_files.areas is missing entries for "
      f"{sorted(missing_area_slots)}."
    )

  baseline_path = pack.declared_files.get("baseline")
  if not isinstance(baseline_path, Path):
    raise InvalidManifestSchemaError(
      f"Platform pack '{pack.slug}': declared_files.baseline is required."
    )

  _assert_content_file_ok(pack, slot="baseline", file_path=baseline_path)
  _assert_declared_skill_frontmatter(
    pack,
    slot="baseline",
    file_path=baseline_path,
    expected_name=pack.routed_skill_name,
  )

  for area in pack.declared_code_review_areas:
    area_path = declared_area_files[area]
    if not isinstance(area_path, Path):
      raise InvalidManifestSchemaError(
        f"Platform pack '{pack.slug}': declared_files.areas['{area}'] must resolve to a path."
      )
    _assert_content_file_ok(pack, slot=f"areas.{area}", file_path=area_path)
    if area_path.name == "SKILL.md":
      _assert_declared_skill_frontmatter(
        pack,
        slot=f"areas.{area}",
        file_path=area_path,
        expected_name=f"bill-{pack.slug}-code-review-{area}",
      )


def discover_platform_packs(platform_packs_root: Path | str) -> list[PlatformPack]:
  """Discover and load every platform pack under the given root.

  The first loader error aborts discovery with the specific exception so
  callers can act on a single precise message.

  Missing or empty ``platform-packs/`` is valid and returns ``[]``. Callers
  that need user-facing messaging should surface
  :func:`describe_zero_platform_packs_state` instead of inventing their own
  fallback wording.
  """

  packs_root = Path(platform_packs_root).resolve()
  if not packs_root.is_dir():
    return []

  discovered: list[PlatformPack] = []
  for entry in sorted(packs_root.iterdir()):
    if not entry.is_dir():
      continue
    if entry.name.startswith("."):
      continue
    discovered.append(load_platform_pack(entry))
  return discovered


def describe_zero_platform_packs_state(platform_packs_root: Path | str) -> str:
  """Return the canonical user-facing message for the zero-pack state."""

  packs_root = Path(platform_packs_root).resolve()
  return ZERO_PLATFORM_PACKS_MESSAGE_TEMPLATE.format(path=str(packs_root))


def discover_installable_pack_skills(pack: PlatformPack) -> tuple[InstallablePackSkill, ...]:
  """Return the installable skills declared by ``pack``."""

  skills: list[InstallablePackSkill] = [
    InstallablePackSkill(
      skill_name=pack.routed_skill_name,
      skill_dir=pack.declared_files["baseline"].parent,
      source_file=pack.declared_files["baseline"],
      family="code-review-baseline",
    )
  ]

  for area in pack.declared_code_review_areas:
    area_path = pack.declared_files["areas"][area]
    if area_path.name != "SKILL.md":
      continue
    skills.append(
      InstallablePackSkill(
        skill_name=f"bill-{pack.slug}-code-review-{area}",
        skill_dir=area_path.parent,
        source_file=area_path,
        family="code-review-area",
      )
    )

  if pack.declared_quality_check_file is not None:
    file_path = load_quality_check_content(pack)
    skills.append(
      InstallablePackSkill(
        skill_name=f"bill-{pack.slug}-quality-check",
        skill_dir=file_path.parent,
        source_file=file_path,
        family="quality-check",
      )
    )

  return tuple(skills)


def _build_pack(
  *,
  slug: str,
  pack_root: Path,
  manifest_path: Path,
  raw: Any,
) -> PlatformPack:
  if not isinstance(raw, dict):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest '{manifest_path}' must be a YAML mapping at the top level."
    )

  declared_platform = raw.get("platform")
  if declared_platform != slug:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest 'platform' field is "
      f"'{declared_platform}', expected '{slug}' to match the directory name."
    )

  contract_version_raw = raw.get("contract_version")
  if contract_version_raw is None:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest is missing required field 'contract_version'."
    )
  contract_version = str(contract_version_raw)

  routing_raw = raw.get("routing_signals")
  if not isinstance(routing_raw, dict):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest field 'routing_signals' must be a mapping."
    )

  routing_signals = RoutingSignals(
    strong=_as_string_tuple(slug, routing_raw.get("strong"), "routing_signals.strong"),
    tie_breakers=_as_string_tuple(slug, routing_raw.get("tie_breakers"), "routing_signals.tie_breakers"),
    addon_signals=_as_string_tuple(
      slug, routing_raw.get("addon_signals", []), "routing_signals.addon_signals"
    ),
  )

  declared_areas_raw = raw.get("declared_code_review_areas")
  if declared_areas_raw is None:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest is missing required field 'declared_code_review_areas'."
    )
  if not isinstance(declared_areas_raw, list):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'declared_code_review_areas' must be a list."
    )
  declared_areas: list[str] = []
  for entry in declared_areas_raw:
    if not isinstance(entry, str):
      raise InvalidManifestSchemaError(
        f"Platform pack '{slug}': every entry in 'declared_code_review_areas' must be a string."
      )
    if entry not in APPROVED_CODE_REVIEW_AREAS:
      raise InvalidManifestSchemaError(
        f"Platform pack '{slug}': declared area '{entry}' is not approved; "
        f"must be one of {sorted(APPROVED_CODE_REVIEW_AREAS)}."
      )
    declared_areas.append(entry)

  declared_files_raw = raw.get("declared_files")
  if not isinstance(declared_files_raw, dict):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': manifest field 'declared_files' must be a mapping."
    )
  baseline_raw = declared_files_raw.get("baseline")
  if not isinstance(baseline_raw, str) or not baseline_raw:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'declared_files.baseline' must be a non-empty path string."
    )
  areas_raw = declared_files_raw.get("areas", {})
  if not isinstance(areas_raw, dict):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'declared_files.areas' must be a mapping."
    )
  for area_key, area_path_value in areas_raw.items():
    if not isinstance(area_key, str) or not isinstance(area_path_value, str):
      raise InvalidManifestSchemaError(
        f"Platform pack '{slug}': 'declared_files.areas' entries must be string->string."
      )

  # Loud-fail when a manifest declares an area in ``declared_files.areas`` that
  # is not listed in ``declared_code_review_areas``. A typo or stale entry
  # used to be silently dropped, which weakens the loud-fail contract.
  extra_area_keys = sorted(set(areas_raw.keys()) - set(declared_areas))
  if extra_area_keys:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'declared_files.areas' contains entries "
      f"{extra_area_keys} that are not listed in 'declared_code_review_areas'. "
      "Remove the extras or add them to the declared area list."
    )

  declared_files: dict[str, Any] = {
    "baseline": (pack_root / baseline_raw).resolve(),
    "areas": {area: (pack_root / areas_raw[area]).resolve() for area in declared_areas if area in areas_raw},
  }

  governs_addons_raw = raw.get("governs_addons", False)
  if not isinstance(governs_addons_raw, bool):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'governs_addons' must be a boolean."
    )

  display_name_raw = raw.get("display_name")
  if display_name_raw is not None and not isinstance(display_name_raw, str):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'display_name' must be a string when provided."
    )

  notes_raw = raw.get("notes")
  if notes_raw is not None and not isinstance(notes_raw, str):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'notes' must be a string when provided."
    )

  declared_quality_check_raw = raw.get("declared_quality_check_file")
  declared_quality_check_path: Path | None
  if declared_quality_check_raw is None:
    declared_quality_check_path = None
  elif isinstance(declared_quality_check_raw, str) and declared_quality_check_raw:
    declared_quality_check_path = (pack_root / declared_quality_check_raw).resolve()
  else:
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': 'declared_quality_check_file' must be a non-empty path string when provided."
    )

  return PlatformPack(
    slug=slug,
    pack_root=pack_root,
    contract_version=contract_version,
    routing_signals=routing_signals,
    declared_code_review_areas=tuple(declared_areas),
    declared_files=declared_files,
    governs_addons=governs_addons_raw,
    display_name=display_name_raw,
    notes=notes_raw,
    declared_quality_check_file=declared_quality_check_path,
  )


def _as_string_tuple(slug: str, value: Any, field_label: str) -> tuple[str, ...]:
  if value is None:
    return ()
  if not isinstance(value, list):
    raise InvalidManifestSchemaError(
      f"Platform pack '{slug}': '{field_label}' must be a list of strings."
    )
  result: list[str] = []
  for entry in value:
    if not isinstance(entry, str):
      raise InvalidManifestSchemaError(
        f"Platform pack '{slug}': every entry in '{field_label}' must be a string."
      )
    result.append(entry)
  return tuple(result)


def _assert_content_file_ok(pack: PlatformPack, *, slot: str, file_path: Path) -> None:
  if not file_path.is_file():
    raise MissingContentFileError(
      f"Platform pack '{pack.slug}': declared content file for slot '{slot}' "
      f"is missing at '{file_path}'."
    )

  resolved = resolve_active_skill_markdown(
    file_path,
    owner_label=f"Platform pack '{pack.slug}'",
  )
  headings = _collect_top_level_h2_headings(resolved.active_text)
  for required in REQUIRED_CONTENT_SECTIONS:
    if required not in headings:
      raise MissingRequiredSectionError(
        f"Platform pack '{pack.slug}': content file '{resolved.active_file}' is missing "
        f"required section '{required}'."
      )


def load_quality_check_content(pack: PlatformPack) -> Path:
  """Return the resolved path to a pack's declared quality-check content file.

  Loud-fail rules:

  - Raises :class:`MissingContentFileError` when ``declared_quality_check_file``
    is ``None`` (callers must gate the call) or the referenced file does not
    exist on disk.
  - Raises :class:`MissingRequiredSectionError` when the content file is
    missing one of the :data:`REQUIRED_QUALITY_CHECK_SECTIONS` H2 sections.

  The function never silently falls back to another pack. Callers must either
  use the declared file from the selected pack or report that the selected
  optional extension does not provide quality-check content.
  """
  if pack.declared_quality_check_file is None:
    raise MissingContentFileError(
      f"Platform pack '{pack.slug}': declared_quality_check_file not set "
      "(call is only valid after checking pack.declared_quality_check_file is not None)."
    )

  file_path = pack.declared_quality_check_file
  if not file_path.is_file():
    raise MissingContentFileError(
      f"Platform pack '{pack.slug}': declared quality-check content file "
      f"is missing at '{file_path}'."
    )

  resolved = resolve_active_skill_markdown(
    file_path,
    owner_label=f"Platform pack '{pack.slug}'",
  )
  headings = _collect_top_level_h2_headings(resolved.active_text)
  for required in REQUIRED_QUALITY_CHECK_SECTIONS:
    if required not in headings:
      raise MissingRequiredSectionError(
        f"Platform pack '{pack.slug}': quality-check content file '{resolved.active_file}' "
        f"is missing required section '{required}'."
      )
  _assert_declared_skill_frontmatter(
    pack,
    slot="declared_quality_check_file",
    file_path=file_path,
    expected_prefix=f"bill-{pack.slug}-quality-check",
  )
  return file_path


def _collect_top_level_h2_headings(text: str) -> set[str]:
  """Return the set of real H2 headings outside fenced code blocks.

  A naive regex scan would incorrectly match ``## Specialist Scope`` inside
  a fenced code block — that would let a pack author silently omit a real
  section while "documenting" it in a code example. This walker tracks
  fence state line-by-line and only collects headings while outside fences.
  """
  headings: set[str] = set()
  in_fence = False
  for line in text.splitlines():
    if _FENCE_PATTERN.match(line):
      in_fence = not in_fence
      continue
    if in_fence:
      continue
    if _SECTION_HEADING_PATTERN.match(line):
      headings.add(line.strip())
  return headings


def _assert_declared_skill_frontmatter(
  pack: PlatformPack,
  *,
  slot: str,
  file_path: Path,
  expected_name: str | None = None,
  expected_prefix: str | None = None,
) -> None:
  frontmatter = _parse_frontmatter(file_path, pack.slug)
  declared_name = frontmatter.get("name", "")
  if expected_name is not None and declared_name != expected_name:
    raise InvalidContentFrontmatterError(
      f"Platform pack '{pack.slug}': declared content file for slot '{slot}' "
      f"must declare frontmatter name '{expected_name}', found '{declared_name}' "
      f"in '{file_path}'."
    )
  if expected_prefix is not None and not declared_name.startswith(expected_prefix):
    raise InvalidContentFrontmatterError(
      f"Platform pack '{pack.slug}': declared content file for slot '{slot}' "
      f"must declare frontmatter name starting with '{expected_prefix}', found "
      f"'{declared_name}' in '{file_path}'."
    )

  description = frontmatter.get("description", "")
  if not description:
    raise InvalidContentFrontmatterError(
      f"Platform pack '{pack.slug}': declared content file for slot '{slot}' "
      f"must declare a non-empty frontmatter description in '{file_path}'."
    )


def _parse_frontmatter(file_path: Path, slug: str) -> dict[str, str]:
  text = file_path.read_text(encoding="utf-8")
  match = _FRONTMATTER_PATTERN.match(text)
  if not match:
    raise InvalidContentFrontmatterError(
      f"Platform pack '{slug}': declared content file '{file_path}' is missing "
      "the required YAML frontmatter block."
    )

  values: dict[str, str] = {}
  for line in match.group(1).splitlines():
    if ":" not in line:
      continue
    key, value = line.split(":", 1)
    values[key.strip()] = value.strip()
  return values


__all__ = [
  "APPROVED_CODE_REVIEW_AREAS",
  "ContractVersionMismatchError",
  "InstallablePackSkill",
  "InvalidContentFrontmatterError",
  "InvalidManifestSchemaError",
  "MissingContentFileError",
  "MissingManifestError",
  "MissingRequiredSectionError",
  "PlatformPack",
  "PyYAMLMissingError",
  "REQUIRED_CONTENT_SECTIONS",
  "REQUIRED_QUALITY_CHECK_SECTIONS",
  "ResolvedSkillMarkdown",
  "RoutingSignals",
  "SHELL_CONTRACT_VERSION",
  "SKILL_IMPLEMENTATION_FILENAME",
  "ShellContentContractError",
  "ZERO_PLATFORM_PACKS_MESSAGE_TEMPLATE",
  "describe_zero_platform_packs_state",
  "discover_installable_pack_skills",
  "discover_platform_packs",
  "load_platform_pack",
  "load_quality_check_content",
  "resolve_active_skill_markdown",
  "skill_bootstrap_references_implementation",
  "validate_platform_pack",
]
