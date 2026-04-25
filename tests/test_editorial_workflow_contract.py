from __future__ import annotations

from pathlib import Path
import shutil
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
scripts_dir = ROOT / "scripts"
if str(scripts_dir) not in sys.path:
  sys.path.insert(0, str(scripts_dir))

from scripts.validate_agent_configs import (  # noqa: E402
  validate_editorial_workflow_skills,
  validate_skill_file,
)


EDITORIAL_SKILL_DIR = ROOT / "skills" / "bill-gaming-editorial-desk"


class EditorialWorkflowContractTest(unittest.TestCase):
  maxDiff = None

  def test_valid_editorial_skill_scaffold_validates(self) -> None:
    issues: list[str] = []

    validate_skill_file(
      "bill-gaming-editorial-desk",
      EDITORIAL_SKILL_DIR / "SKILL.md",
      issues,
    )
    validate_editorial_workflow_skills(ROOT, issues)

    self.assertEqual([], issues)

  def test_rejects_missing_required_editorial_skill_sections(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      temp_root = Path(temp_dir)
      target_dir = temp_root / "skills" / "bill-gaming-editorial-desk"
      shutil.copytree(EDITORIAL_SKILL_DIR, target_dir, symlinks=True)
      skill_file = target_dir / "SKILL.md"
      skill_file.write_text(
        skill_file.read_text(encoding="utf-8").replace(
          "## Candidate Selection Pause",
          "## Candidate Selection Removed",
        ),
        encoding="utf-8",
      )

      issues: list[str] = []
      validate_editorial_workflow_skills(temp_root, issues)

    self.assertIn(
      f"{target_dir}: editorial workflow skill must include '## Candidate Selection Pause'",
      issues,
    )

  def test_source_check_output_contract_markers_are_pinned(self) -> None:
    reference = (EDITORIAL_SKILL_DIR / "reference.md").read_text(encoding="utf-8")

    for marker in (
      "source_verification_contract_v1",
      "confirmed_fact",
      "reputable_reporting",
      "community_claim",
      "rumor",
      "leak",
      "speculation",
      "unsupported_claims",
      "missing_primary_sources",
    ):
      self.assertIn(marker, reference)

  def test_candidate_ranking_output_contract_markers_are_pinned(self) -> None:
    reference = (EDITORIAL_SKILL_DIR / "reference.md").read_text(encoding="utf-8")

    for marker in (
      "candidate_ranking_contract_v1",
      "newsworthiness",
      "timeliness",
      "source_confidence",
      "audience_fit",
      "angle_strength",
      "coverage_gap",
      "social_signal",
      "effort",
      "risk",
    ):
      self.assertIn(marker, reference)


if __name__ == "__main__":
  unittest.main()
