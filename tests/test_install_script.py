from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
OPTIONAL_PACK_FIXTURE = ROOT / "tests" / "fixtures" / "optional_pack" / "example-pack"
VALID_PACK_FIXTURE = ROOT / "tests" / "fixtures" / "shell_content_contract" / "valid_pack"
INVALID_PACK_FIXTURE = ROOT / "tests" / "fixtures" / "shell_content_contract" / "invalid_schema"


def skill_names(repo_root: Path, package_name: str) -> set[str]:
  from skill_bill.install import discover_installable_skills
  return {
    skill.skill_name
    for skill in discover_installable_skills(repo_root)
    if skill.package_name == package_name
  }


class InstallScriptTest(unittest.TestCase):
  maxDiff = None

  def test_installs_framework_only_core_when_no_optional_packs_exist(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      self.prepare_agent_homes(Path(temp_dir))

      result = self.run_installer(repo_root, Path(temp_dir), "copilot\n\n\n")

      self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
      self.assertIn("No optional platform packs were discovered", result.stdout)
      self.assertIn("Optional packs:  none (framework-only core)", result.stdout)
      self.assertEqual(
        self.installed_skills(Path(temp_dir)),
        skill_names(repo_root, "base"),
      )

  def test_installs_selected_synthetic_optional_pack(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      shutil.copytree(
        OPTIONAL_PACK_FIXTURE,
        repo_root / "platform-packs" / "example-pack",
        symlinks=True,
      )
      self.prepare_agent_homes(Path(temp_dir))

      result = self.run_installer(repo_root, Path(temp_dir), "copilot\nexample-pack\n\n")

      self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
      self.assertIn("Available optional platform packs:", result.stdout)
      self.assertIn("Example Pack (example-pack)", result.stdout)
      self.assertEqual(
        self.installed_skills(Path(temp_dir)),
        skill_names(repo_root, "base") | skill_names(repo_root, "example-pack"),
      )

  def test_installs_manifest_declared_pack_skill_names_and_display_name(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      shutil.copytree(
        VALID_PACK_FIXTURE,
        repo_root / "platform-packs" / "valid_pack",
        symlinks=True,
      )
      self.prepare_agent_homes(Path(temp_dir))

      result = self.run_installer(repo_root, Path(temp_dir), "copilot\nvalid_pack\n\n")

      self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
      self.assertIn("Valid Fixture Pack (valid_pack)", result.stdout)
      self.assertIn("Optional packs:  Valid Fixture Pack", result.stdout)
      installed = self.installed_skills(Path(temp_dir))
      self.assertIn("bill-valid_pack-code-review", installed)
      self.assertNotIn("code-review", installed)

  def test_discovery_accepts_valid_optional_pack_manifest_with_noncanonical_indentation(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      pack_root = repo_root / "platform-packs" / "valid_pack"
      shutil.copytree(
        VALID_PACK_FIXTURE,
        pack_root,
        symlinks=True,
      )
      (pack_root / "platform.yaml").write_text(
        "platform: valid_pack\n"
        "contract_version: \"1.0\"\n"
        "display_name: Valid Fixture Pack\n"
        "routing_signals:\n"
        "  strong:\n"
        "    - \".fixture\"\n"
        "  tie_breakers:\n"
        "    - \"Prefer valid_pack when generic markers dominate.\"\n"
        "  addon_signals: []\n"
        "declared_code_review_areas:\n"
        "  - architecture\n"
        "declared_files:\n"
        "    baseline: code-review/SKILL.md\n"
        "    areas:\n"
        "      architecture: code-review/architecture.md\n",
        encoding="utf-8",
      )

      self.assertEqual(
        skill_names(repo_root, "valid_pack"),
        {"bill-valid_pack-code-review"},
      )

  def test_discovery_raises_for_invalid_optional_pack_manifest(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      shutil.copytree(
        INVALID_PACK_FIXTURE,
        repo_root / "platform-packs" / "invalid_schema",
        symlinks=True,
      )

      from skill_bill.install import discover_installable_skills
      from skill_bill.shell_content_contract import InvalidManifestSchemaError

      with self.assertRaises(InvalidManifestSchemaError):
        discover_installable_skills(repo_root)

  def test_installer_writes_default_telemetry_config_for_core_only_install(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      repo_root = self.copy_repo(Path(temp_dir))
      shutil.rmtree(repo_root / "platform-packs", ignore_errors=True)
      self.prepare_agent_homes(Path(temp_dir))

      result = self.run_installer(repo_root, Path(temp_dir), "copilot\n\n\n")

      self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
      config = json.loads((Path(temp_dir) / ".skill-bill" / "config.json").read_text(encoding="utf-8"))
      self.assertEqual(config["telemetry"]["level"], "anonymous")

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

  def run_installer(self, repo_root: Path, home: Path, user_input: str) -> subprocess.CompletedProcess[str]:
    env = {**dict(os.environ), "HOME": str(home)}
    return subprocess.run(
      ["bash", str(repo_root / "install.sh")],
      cwd=repo_root,
      input=user_input,
      capture_output=True,
      text=True,
      check=False,
      env=env,
    )

  def installed_skills(self, home: Path) -> set[str]:
    install_dir = home / ".copilot" / "skills"
    if not install_dir.exists():
      return set()
    return {path.name for path in install_dir.iterdir() if not path.name.startswith(".")}

  def prepare_agent_homes(self, home: Path) -> None:
    (home / ".copilot").mkdir(parents=True, exist_ok=True)


if __name__ == "__main__":
  unittest.main()
