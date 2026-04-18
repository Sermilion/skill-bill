from __future__ import annotations

from pathlib import Path

from skill_bill.shell_content_contract import (
  ShellContentContractError,
  discover_installable_pack_skills,
  load_platform_pack,
)


REPO_ROOT = Path(__file__).resolve().parents[1]

ORCHESTRATION_PLAYBOOKS: dict[str, str] = {
  "stack-routing": "orchestration/stack-routing/PLAYBOOK.md",
  "review-orchestrator": "orchestration/review-orchestrator/PLAYBOOK.md",
  "review-delegation": "orchestration/review-delegation/PLAYBOOK.md",
  "telemetry-contract": "orchestration/telemetry-contract/PLAYBOOK.md",
  "shell-content-contract": "orchestration/shell-content-contract/PLAYBOOK.md",
}

ADDON_DIRECTORY_NAME = "addons"
ADDON_IMPLEMENTATION_SUFFIX = "-implementation.md"
ADDON_REVIEW_SUFFIX = "-review.md"
ADDON_REPORTING_LINE = "Selected add-ons: none | <add-on slugs>"

CODE_REVIEW_SIDECARS: tuple[str, ...] = (
  "stack-routing.md",
  "review-orchestrator.md",
  "review-delegation.md",
  "telemetry-contract.md",
)
QUALITY_CHECK_SIDECARS: tuple[str, ...] = ("stack-routing.md", "telemetry-contract.md")
BASE_RUNTIME_SUPPORTING_FILES: dict[str, tuple[str, ...]] = {
  "bill-code-review": (
    "stack-routing.md",
    "review-delegation.md",
    "telemetry-contract.md",
    "shell-content-contract.md",
  ),
  "bill-quality-check": QUALITY_CHECK_SIDECARS,
  "bill-feature-implement": ("telemetry-contract.md",),
  "bill-feature-verify": ("telemetry-contract.md",),
  "bill-pr-description": ("telemetry-contract.md",),
}


def _iter_child_directories(root: Path) -> tuple[Path, ...]:
  if not root.is_dir():
    return ()
  return tuple(
    entry
    for entry in sorted(root.iterdir())
    if entry.is_dir() and not entry.name.startswith(".")
  )


def _discover_addon_inventory(root: Path) -> tuple[dict[str, tuple[str, ...]], dict[str, tuple[str, ...]]]:
  governed_addons: dict[str, tuple[str, ...]] = {}
  support_files: dict[str, tuple[str, ...]] = {}
  packs_root = root / "platform-packs"
  for platform_dir in _iter_child_directories(packs_root):
    addons_dir = platform_dir / ADDON_DIRECTORY_NAME
    if not addons_dir.is_dir():
      continue

    addon_slugs: set[str] = set()
    topic_files: list[str] = []
    for file_path in sorted(addons_dir.glob("*.md")):
      file_name = file_path.name
      if file_name.endswith(ADDON_IMPLEMENTATION_SUFFIX):
        addon_slugs.add(file_name[: -len(ADDON_IMPLEMENTATION_SUFFIX)])
        continue
      if file_name.endswith(ADDON_REVIEW_SUFFIX):
        addon_slugs.add(file_name[: -len(ADDON_REVIEW_SUFFIX)])
        continue
      topic_files.append(file_name)

    governed_addons[platform_dir.name] = tuple(sorted(addon_slugs))
    support_files[platform_dir.name] = tuple(topic_files)

  return governed_addons, support_files


def compute_addon_supporting_file_targets(root: Path) -> dict[str, str]:
  governed_addons, support_files = _discover_addon_inventory(root)
  targets: dict[str, str] = {}

  for stack, addon_slugs in governed_addons.items():
    for addon_slug in addon_slugs:
      targets[f"{addon_slug}{ADDON_IMPLEMENTATION_SUFFIX}"] = (
        f"platform-packs/{stack}/addons/{addon_slug}{ADDON_IMPLEMENTATION_SUFFIX}"
      )
      targets[f"{addon_slug}{ADDON_REVIEW_SUFFIX}"] = (
        f"platform-packs/{stack}/addons/{addon_slug}{ADDON_REVIEW_SUFFIX}"
      )

  for stack, file_names in support_files.items():
    for file_name in file_names:
      targets[file_name] = f"platform-packs/{stack}/addons/{file_name}"

  return targets


def _addon_review_sidecars_for_platform(root: Path, platform: str) -> tuple[str, ...]:
  governed_addons, support_files = _discover_addon_inventory(root)
  return (
    tuple(f"{addon_slug}{ADDON_REVIEW_SUFFIX}" for addon_slug in governed_addons.get(platform, ()))
    + support_files.get(platform, ())
  )


def compute_runtime_supporting_files(root: Path) -> dict[str, tuple[str, ...]]:
  """Return required sibling sidecars per skill name.

  Sidecars stay anchored to the skill directory that owns canonical
  ``SKILL.md``. Split-layout skills may also ship ``implementation.md``, but
  that does not change sidecar names, locations, or lookup rules.
  """
  runtime_supporting_files = dict(BASE_RUNTIME_SUPPORTING_FILES)
  for pack_dir in _iter_child_directories(root / "platform-packs"):
    try:
      pack = load_platform_pack(pack_dir)
    except ShellContentContractError:
      continue
    addon_review_sidecars = _addon_review_sidecars_for_platform(root, pack.slug)
    for skill in discover_installable_pack_skills(pack):
      if skill.family == "code-review-baseline":
        runtime_supporting_files[skill.skill_name] = CODE_REVIEW_SIDECARS + addon_review_sidecars
      elif skill.family == "quality-check":
        runtime_supporting_files[skill.skill_name] = QUALITY_CHECK_SIDECARS
      else:
        runtime_supporting_files[skill.skill_name] = addon_review_sidecars

  return runtime_supporting_files


GOVERNED_STACK_ADDONS, GOVERNED_ADDON_SUPPORT_FILES = _discover_addon_inventory(REPO_ROOT)
ADDON_SUPPORTING_FILE_TARGETS: dict[str, str] = compute_addon_supporting_file_targets(REPO_ROOT)

SUPPORTING_FILE_TARGETS: dict[str, str] = {
  "stack-routing.md": ORCHESTRATION_PLAYBOOKS["stack-routing"],
  "review-orchestrator.md": ORCHESTRATION_PLAYBOOKS["review-orchestrator"],
  "review-delegation.md": ORCHESTRATION_PLAYBOOKS["review-delegation"],
  "telemetry-contract.md": ORCHESTRATION_PLAYBOOKS["telemetry-contract"],
  "shell-content-contract.md": ORCHESTRATION_PLAYBOOKS["shell-content-contract"],
  **ADDON_SUPPORTING_FILE_TARGETS,
}

RUNTIME_SUPPORTING_FILES: dict[str, tuple[str, ...]] = compute_runtime_supporting_files(REPO_ROOT)

REVIEW_DELEGATION_REQUIRED_SECTIONS = (
  "## GitHub Copilot CLI",
  "## Claude Code",
  "## OpenAI Codex",
  "## GLM",
)

PORTABLE_REVIEW_SKILLS = tuple(
  skill_name
  for skill_name in RUNTIME_SUPPORTING_FILES
  if skill_name.startswith("bill-")
  and skill_name.endswith("-code-review")
  and skill_name != "bill-code-review"
)

REVIEW_RUN_ID_PLACEHOLDER = "Review run ID: <review-run-id>"
REVIEW_RUN_ID_FORMAT = "rvw-YYYYMMDD-HHMMSS-XXXX"
REVIEW_SESSION_ID_PLACEHOLDER = "Review session ID: <review-session-id>"
REVIEW_SESSION_ID_FORMAT = "rvs-<uuid4>"
APPLIED_LEARNINGS_PLACEHOLDER = "Applied learnings: none | <learning references>"
RISK_REGISTER_FINDING_FORMAT = "- [F-001] <Severity> | <Confidence> | <file:line> | <description>"
TELEMETRY_OWNERSHIP_HEADING = "Telemetry Ownership"
TRIAGE_OWNERSHIP_HEADING = "Triage Ownership"
PARENT_IMPORT_RULE = (
  "If this review owns the final merged review output for the current review lifecycle, call the "
  "`import_review` MCP tool:"
)
CHILD_NO_IMPORT_RULE = (
  "If this review is delegated or layered under another review, do not call `import_review`."
)
CHILD_METADATA_HANDOFF_RULE = (
  "Return the complete review output plus summary metadata (`review_session_id`, `review_run_id`, "
  "detected scope/stack, execution mode, specialist reviews) to the parent review instead."
)
PARENT_TRIAGE_RULE = (
  "If this review owns the final merged review output for the current review lifecycle and the user "
  "responds to findings, call the `triage_findings` MCP tool:"
)
CHILD_NO_TRIAGE_RULE = (
  "If this review is delegated or layered under another review, do not call `triage_findings`;"
)
NO_FINDINGS_TRIAGE_RULE = "Skip triage recording when the final parent-owned review produced no findings."


TELEMETERABLE_SKILLS: tuple[str, ...] = tuple(
  skill_name
  for skill_name, supporting_files in RUNTIME_SUPPORTING_FILES.items()
  if "telemetry-contract.md" in supporting_files
)

INLINE_TELEMETRY_CONTRACT_MARKERS: tuple[str, ...] = (
  "Standalone-first contract",
  "child_steps aggregation",
  "Graceful degradation",
  "Routers never emit",
)


def skills_requiring_supporting_file(file_name: str) -> tuple[str, ...]:
  return tuple(
    skill_name
    for skill_name, supporting_files in RUNTIME_SUPPORTING_FILES.items()
    if file_name in supporting_files
  )


def governed_addon_slugs_for_stack(stack: str) -> tuple[str, ...]:
  return GOVERNED_STACK_ADDONS.get(stack, ())


def supporting_file_targets(root: Path) -> dict[str, Path]:
  return {
    file_name: root / relative_path
    for file_name, relative_path in {
      "stack-routing.md": ORCHESTRATION_PLAYBOOKS["stack-routing"],
      "review-orchestrator.md": ORCHESTRATION_PLAYBOOKS["review-orchestrator"],
      "review-delegation.md": ORCHESTRATION_PLAYBOOKS["review-delegation"],
      "telemetry-contract.md": ORCHESTRATION_PLAYBOOKS["telemetry-contract"],
      "shell-content-contract.md": ORCHESTRATION_PLAYBOOKS["shell-content-contract"],
      **compute_addon_supporting_file_targets(root),
    }.items()
  }
