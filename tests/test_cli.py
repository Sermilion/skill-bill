from __future__ import annotations

import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from skill_bill.cli import main  # noqa: E402
from skill_bill.upgrade import upgrade_skill_wrappers  # noqa: E402


_GOVERNED_FRONTMATTER = """\
---
name: {name}
description: Fixture content.
---
"""

_DESCRIPTOR = """\
## Descriptor

Governed skill: `{name}`
Family: `{family}`
Platform pack: `kotlin` (Kotlin)
{area_line}Description: {description}
"""

_OLD_PROJECT_OVERRIDES = """\
## Project Overrides

Legacy project override text that should be collapsed by upgrade.

If `.agents/skill-overrides.md` exists in the project root and contains a matching section, read it.
"""

_SETUP_SECTION = """\
## Setup

Fixture setup text.
"""

_SHELL_CEREMONY = """\
## Project Overrides

If `.agents/skill-overrides.md` exists in the project root and contains a matching section, read it and apply it as the highest-priority instruction for this skill.

## Inputs

Fixture shell ceremony inputs.

## Execution Mode Reporting

Execution mode: inline | delegated

## Telemetry Ceremony Hooks

Follow `telemetry-contract.md` when it is present.
"""


def _governed_skill_body(
  *,
  name: str,
  family: str,
  description: str,
  area: str = "",
) -> str:
  area_line = f"Area: `{area}`\n" if area else ""
  return (
    _GOVERNED_FRONTMATTER.format(name=name)
    + "\n"
    + _DESCRIPTOR.format(
      name=name,
      family=family,
      area_line=area_line,
      description=description,
    )
    + "\n"
    + "## Execution\n\n"
    + "Follow the instructions in [content.md](content.md).\n\n"
    + "## Ceremony\n\n"
    + "Follow the shell ceremony in [shell-ceremony.md](shell-ceremony.md).\n\n"
    + "When telemetry applies, follow [telemetry-contract.md](telemetry-contract.md).\n\n"
    + "## Old Boilerplate\n\n"
    + "This stale wrapper section should be removed by upgrade.\n"
  )


def _build_upgrade_repo(tmp_path: Path) -> Path:
  repo = tmp_path / "repo"
  shell_ceremony = repo / "orchestration" / "shell-content-contract" / "shell-ceremony.md"
  shell_ceremony.parent.mkdir(parents=True, exist_ok=True)
  shell_ceremony.write_text(_SHELL_CEREMONY, encoding="utf-8")

  horizontal_skill = repo / "skills" / "bill-horizontal-test"
  horizontal_skill.mkdir(parents=True, exist_ok=True)
  (horizontal_skill / "shell-ceremony.md").symlink_to(shell_ceremony)
  (horizontal_skill / "SKILL.md").write_text(
    "---\n"
    "name: bill-horizontal-test\n"
    "description: Fixture horizontal skill.\n"
    "---\n\n"
    "# Fixture Horizontal Skill\n\n"
    f"{_OLD_PROJECT_OVERRIDES}\n"
    f"{_SETUP_SECTION}",
    encoding="utf-8",
  )

  pack_root = repo / "platform-packs" / "kotlin"
  pack_root.mkdir(parents=True, exist_ok=True)
  (pack_root / "platform.yaml").write_text(
    """\
platform: kotlin
contract_version: "1.0"
display_name: Kotlin
governs_addons: false

routing_signals:
  strong:
    - ".kt"
  tie_breakers:
    - "fixture tie-breaker"
  addon_signals: []

declared_code_review_areas:
  - architecture

declared_files:
  baseline: code-review/bill-kotlin-code-review/SKILL.md
  areas:
    architecture: code-review/bill-kotlin-code-review-architecture/SKILL.md

area_metadata:
  architecture:
    focus: "architecture, boundaries, and dependency direction"
""",
    encoding="utf-8",
  )

  baseline_dir = pack_root / "code-review" / "bill-kotlin-code-review"
  baseline_dir.mkdir(parents=True, exist_ok=True)
  (baseline_dir / "SKILL.md").write_text(
    _governed_skill_body(
      name="bill-kotlin-code-review",
      family="code-review",
      description="Use when reviewing Kotlin changes across code-review specialists.",
    ),
    encoding="utf-8",
  )
  (baseline_dir / "content.md").write_text("# Content\n\nUnchanged baseline content.\n", encoding="utf-8")
  (baseline_dir / "shell-ceremony.md").symlink_to(shell_ceremony)

  area_dir = pack_root / "code-review" / "bill-kotlin-code-review-architecture"
  area_dir.mkdir(parents=True, exist_ok=True)
  (area_dir / "SKILL.md").write_text(
    _governed_skill_body(
      name="bill-kotlin-code-review-architecture",
      family="code-review",
      area="architecture",
      description="Use when reviewing Kotlin changes for architecture, boundaries, and dependency direction.",
    ),
    encoding="utf-8",
  )
  (area_dir / "content.md").write_text("# Content\n\nUnchanged area content.\n", encoding="utf-8")
  (area_dir / "shell-ceremony.md").symlink_to(shell_ceremony)
  return repo


def _top_level_h2_headings(text: str) -> list[str]:
  return [line.strip() for line in text.splitlines() if line.startswith("## ")]


class UpgradeCliTest(unittest.TestCase):
  maxDiff = None

  def setUp(self) -> None:
    self._tmpdir = tempfile.TemporaryDirectory()
    self.addCleanup(self._tmpdir.cleanup)
    self.repo = _build_upgrade_repo(Path(self._tmpdir.name))

  def test_upgrade_regenerates_wrappers_without_touching_sidecars(self) -> None:
    baseline_skill = (
      self.repo
      / "platform-packs"
      / "kotlin"
      / "code-review"
      / "bill-kotlin-code-review"
      / "SKILL.md"
    )
    baseline_content = baseline_skill.with_name("content.md")
    baseline_ceremony = baseline_skill.with_name("shell-ceremony.md")
    content_before = baseline_content.read_bytes()
    ceremony_before = baseline_ceremony.read_bytes()

    stdout = io.StringIO()
    with contextlib.redirect_stdout(stdout):
      exit_code = main(["upgrade", "--repo-root", str(self.repo), "--format", "json"])

    self.assertEqual(exit_code, 0)
    payload = json.loads(stdout.getvalue())
    self.assertGreaterEqual(payload["regenerated_count"], 2)
    self.assertFalse(payload["content_md_touched"])
    self.assertFalse(payload["shell_ceremony_touched"])

    upgraded_body = baseline_skill.read_text(encoding="utf-8")
    self.assertEqual(
      _top_level_h2_headings(upgraded_body),
      ["## Descriptor", "## Execution", "## Ceremony"],
    )
    self.assertNotIn("## Old Boilerplate", upgraded_body)
    self.assertEqual(content_before, baseline_content.read_bytes())
    self.assertEqual(ceremony_before, baseline_ceremony.read_bytes())

    horizontal_skill = self.repo / "skills" / "bill-horizontal-test" / "SKILL.md"
    horizontal_body = horizontal_skill.read_text(encoding="utf-8")
    self.assertIn("Follow the shell ceremony in [shell-ceremony.md](shell-ceremony.md).", horizontal_body)
    self.assertNotIn("Legacy project override text", horizontal_body)

  def test_upgrade_rolls_back_when_validator_fails(self) -> None:
    baseline_skill = (
      self.repo
      / "platform-packs"
      / "kotlin"
      / "code-review"
      / "bill-kotlin-code-review"
      / "SKILL.md"
    )
    horizontal_skill = self.repo / "skills" / "bill-horizontal-test" / "SKILL.md"
    baseline_before = baseline_skill.read_bytes()
    horizontal_before = horizontal_skill.read_bytes()

    with mock.patch("skill_bill.upgrade._run_validator", side_effect=RuntimeError("boom")):
      with self.assertRaises(RuntimeError):
        upgrade_skill_wrappers(self.repo)

    self.assertEqual(baseline_before, baseline_skill.read_bytes())
    self.assertEqual(horizontal_before, horizontal_skill.read_bytes())
