from __future__ import annotations

from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from validate_agent_configs import (  # noqa: E402
  validate_platform_pack_skill_file,
  validate_platform_packs,
  validate_skill_file,
)


VALIDATOR_PATH = ROOT / "scripts" / "validate_agent_configs.py"
OPTIONAL_PACK_FIXTURE = ROOT / "tests" / "fixtures" / "optional_pack" / "example-pack"


class ValidateAgentConfigsE2ETest(unittest.TestCase):
  def test_zero_pack_core_repo_is_valid(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      (repo_root / "platform-packs").mkdir()
      self.normalize_zero_pack_repo(repo_root)

      result = self.run_validator(repo_root)

      self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
      self.assertIn("0 platform packs", result.stdout)

  def test_validate_platform_packs_accepts_synthetic_optional_pack(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir)
      shutil.copytree(
        OPTIONAL_PACK_FIXTURE,
        repo_root / "platform-packs" / "example-pack",
        symlinks=True,
      )

      issues: list[str] = []
      slugs = validate_platform_packs(repo_root, issues)

      self.assertEqual(issues, [])
      self.assertEqual(slugs, ["example-pack"])

  def test_validate_platform_pack_skill_file_accepts_synthetic_optional_pack_skill(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir)
      skill_file = repo_root / "platform-packs" / "example-pack" / "code-review" / "bill-example-pack-code-review" / "SKILL.md"
      shutil.copytree(
        OPTIONAL_PACK_FIXTURE,
        repo_root / "platform-packs" / "example-pack",
        symlinks=True,
      )

      issues: list[str] = []
      validate_platform_pack_skill_file("bill-example-pack-code-review", skill_file, issues)

      self.assertEqual(issues, [])

  def test_validate_skill_file_rejects_extension_prefixed_base_skill(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir)
      (repo_root / "platform-packs" / "example-pack").mkdir(parents=True)
      skill_dir = repo_root / "skills" / "base" / "bill-example-pack-ship-it"
      skill_dir.mkdir(parents=True)
      skill_dir.joinpath("SKILL.md").write_text(
        "---\n"
        "name: bill-example-pack-ship-it\n"
        "description: invalid base skill\n"
        "---\n\n"
        "## Project Overrides\n"
        "See .agents/skill-overrides.md.\n\n",
        encoding="utf-8",
      )

      issues: list[str] = []
      validate_skill_file(repo_root, "bill-example-pack-ship-it", skill_dir / "SKILL.md", issues)

      self.assertTrue(any("base skills must use neutral names" in issue for issue in issues))

  def test_validate_skill_file_accepts_split_layout_skill(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir)
      skill_dir = repo_root / "skills" / "base" / "bill-framework-helper"
      skill_dir.mkdir(parents=True)
      skill_dir.joinpath("SKILL.md").write_text(
        "---\n"
        "name: bill-framework-helper\n"
        "description: split-layout skill\n"
        "---\n\n"
        "# Skill Bootstrap\n\n"
        "This `SKILL.md` file stays canonical for install and discovery.\n"
        "Read and follow the active implementation in [implementation.md](implementation.md).\n",
        encoding="utf-8",
      )
      skill_dir.joinpath("implementation.md").write_text(
        "## Project Overrides\n\n"
        "See .agents/skill-overrides.md.\n\n"
        "## Description\n\n"
        "Split-layout implementation.\n",
        encoding="utf-8",
      )

      issues: list[str] = []
      validate_skill_file(repo_root, "bill-framework-helper", skill_dir / "SKILL.md", issues)

      self.assertEqual(issues, [])

  def test_validate_skill_file_rejects_split_layout_missing_implementation(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir)
      skill_dir = repo_root / "skills" / "base" / "bill-framework-helper"
      skill_dir.mkdir(parents=True)
      skill_dir.joinpath("SKILL.md").write_text(
        "---\n"
        "name: bill-framework-helper\n"
        "description: split-layout skill\n"
        "---\n\n"
        "# Skill Bootstrap\n\n"
        "This `SKILL.md` file stays canonical for install and discovery.\n"
        "Read and follow the active implementation in [implementation.md](implementation.md).\n",
        encoding="utf-8",
      )

      issues: list[str] = []
      validate_skill_file(repo_root, "bill-framework-helper", skill_dir / "SKILL.md", issues)

      self.assertTrue(any("references missing implementation file" in issue for issue in issues))

  def test_validate_skill_file_rejects_split_layout_bootstrap_missing_required_sidecar_reference(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = Path(temp_dir)
      skill_dir = repo_root / "skills" / "base" / "bill-feature-verify"
      skill_dir.mkdir(parents=True)
      skill_dir.joinpath("SKILL.md").write_text(
        "---\n"
        "name: bill-feature-verify\n"
        "description: split-layout skill\n"
        "---\n\n"
        "# Skill Bootstrap\n\n"
        "This `SKILL.md` file stays canonical for install and discovery.\n"
        "Read and follow the active implementation in [implementation.md](implementation.md) before acting.\n",
        encoding="utf-8",
      )
      skill_dir.joinpath("implementation.md").write_text(
        "## Project Overrides\n\n"
        "See .agents/skill-overrides.md.\n\n"
        "## Description\n\n"
        "Split-layout implementation.\n\n"
        "Follow [telemetry-contract.md](telemetry-contract.md).\n",
        encoding="utf-8",
      )
      (repo_root / ".agents").mkdir(parents=True)
      (repo_root / ".agents" / "skill-overrides.md").write_text("# Skill Overrides\n", encoding="utf-8")
      (repo_root / "orchestration" / "telemetry-contract").mkdir(parents=True)
      (repo_root / "orchestration" / "telemetry-contract" / "PLAYBOOK.md").write_text(
        "# Telemetry Contract\n",
        encoding="utf-8",
      )
      skill_dir.joinpath("telemetry-contract.md").symlink_to(
        repo_root / "orchestration" / "telemetry-contract" / "PLAYBOOK.md"
      )

      issues: list[str] = []
      validate_skill_file(repo_root, "bill-feature-verify", skill_dir / "SKILL.md", issues)

      self.assertTrue(
        any("split-layout bootstrap must reference local supporting file 'telemetry-contract.md'" in issue for issue in issues)
      )

  def test_real_migrated_skill_passes_validation(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      skill_file = repo_root / "skills" / "base" / "bill-feature-verify" / "SKILL.md"

      issues: list[str] = []
      validate_skill_file(repo_root, "bill-feature-verify", skill_file, issues)

      self.assertEqual(issues, [])

  def copy_repo(self, temp_root: Path) -> Path:
    repo_root = temp_root / "repo"
    shutil.copytree(
      ROOT,
      repo_root,
      symlinks=True,
      ignore=shutil.ignore_patterns(
        ".git",
        ".venv",
        "__pycache__",
        "*.pyc",
        "skill_bill.egg-info",
      ),
    )
    return repo_root

  def normalize_zero_pack_repo(self, repo_root: Path) -> None:
    known_skills = sorted(path.parent.name for path in (repo_root / "skills").rglob("SKILL.md"))

    readme_lines = [
      "# Test Repo",
      "",
      f"collection of {len(known_skills)} AI skills",
      "",
      f"### Core ({len(known_skills)} skills)",
      "| Command | Description |",
      "| --- | --- |",
    ]
    for skill_name in known_skills:
      readme_lines.append(f"| `/{skill_name}` | Test entry for `{skill_name}`. |")
    (repo_root / "README.md").write_text("\n".join(readme_lines) + "\n", encoding="utf-8")

    skill_reference_pattern = re.compile(r"(?<![A-Za-z0-9.-])(bill-[a-z0-9-]+)(?![A-Za-z0-9-])")
    known_skill_set = set(known_skills)
    for skill_file in (repo_root / "skills").rglob("SKILL.md"):
      text = skill_file.read_text(encoding="utf-8")
      normalized = skill_reference_pattern.sub(
        lambda match: match.group(1) if match.group(1) in known_skill_set else "optional-pack skill",
        text,
      )
      skill_file.write_text(normalized, encoding="utf-8")

  def run_validator(self, repo_root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
      [sys.executable, str(VALIDATOR_PATH), str(repo_root)],
      cwd=repo_root,
      capture_output=True,
      text=True,
      check=False,
    )


if __name__ == "__main__":
  unittest.main()
