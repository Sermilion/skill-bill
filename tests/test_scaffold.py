from __future__ import annotations

from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from skill_bill import install as install_module  # noqa: E402
from skill_bill.scaffold import scaffold  # noqa: E402
from skill_bill.scaffold_exceptions import (  # noqa: E402
  InvalidScaffoldPayloadError,
  MissingPlatformPackError,
)
from skill_bill.shell_content_contract import (  # noqa: E402
  SHELL_CONTRACT_VERSION,
  load_platform_pack,
)


OPTIONAL_PACK_FIXTURE = ROOT / "tests" / "fixtures" / "optional_pack" / "example-pack"
PLAYBOOKS = (
  "stack-routing",
  "review-orchestrator",
  "review-delegation",
  "telemetry-contract",
  "shell-content-contract",
)


def build_repo(tmp_root: Path) -> Path:
  repo = tmp_root / "repo"
  (repo / "skills" / "base").mkdir(parents=True)
  for playbook in PLAYBOOKS:
    playbook_dir = repo / "orchestration" / playbook
    playbook_dir.mkdir(parents=True, exist_ok=True)
    (playbook_dir / "PLAYBOOK.md").write_text(f"# {playbook}\n", encoding="utf-8")
  return repo


class ScaffoldTest(unittest.TestCase):
  def setUp(self) -> None:
    self.temp_dir = tempfile.TemporaryDirectory()
    self.addCleanup(self.temp_dir.cleanup)
    self.repo = build_repo(Path(self.temp_dir.name))
    self.detect_agents = mock.patch.object(install_module, "detect_agents", return_value=[])
    self.detect_agents.start()
    self.addCleanup(self.detect_agents.stop)

  def payload(self, **overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
      "scaffold_payload_version": "1.0",
      "repo_root": str(self.repo),
    }
    payload.update(overrides)
    return payload

  def test_horizontal_scaffold_succeeds_in_zero_pack_repo(self) -> None:
    result = scaffold(self.payload(kind="horizontal", name="bill-framework-helper"))

    skill_file = self.repo / "skills" / "base" / "bill-framework-helper" / "SKILL.md"
    implementation_file = self.repo / "skills" / "base" / "bill-framework-helper" / "implementation.md"
    self.assertEqual(result.kind, "horizontal")
    self.assertTrue(skill_file.is_file())
    self.assertTrue(implementation_file.is_file())
    self.assertIn("[implementation.md](implementation.md)", skill_file.read_text(encoding="utf-8"))
    self.assertIn("## Project Overrides", implementation_file.read_text(encoding="utf-8"))

  def test_scaffold_writes_supplied_implementation_text_verbatim(self) -> None:
    implementation_text = (
      "## Project Overrides\n\n"
      "See .agents/skill-overrides.md.\n\n"
      "## Description\n\n"
      "Custom implementation body.\n"
    )

    scaffold(
      self.payload(
        kind="horizontal",
        name="bill-framework-custom",
        implementation_text=implementation_text,
      )
    )

    implementation_file = self.repo / "skills" / "base" / "bill-framework-custom" / "implementation.md"
    self.assertEqual(implementation_file.read_text(encoding="utf-8"), implementation_text)

  def test_pack_scoped_scaffold_requires_optional_pack_to_exist(self) -> None:
    with self.assertRaises(MissingPlatformPackError):
      scaffold(
        self.payload(
          kind="platform-override-piloted",
          name="bill-example-pack-quality-check-extra",
          platform="example-pack",
          family="quality-check",
        )
      )

  def test_new_baseline_code_review_pack_scaffold_creates_platform_manifest(self) -> None:
    result = scaffold(
      self.payload(
        kind="platform-override-piloted",
        name="bill-kmp-code-review",
        platform="kmp",
        family="code-review",
        platform_manifest={
          "platform": "kmp",
          "contract_version": SHELL_CONTRACT_VERSION,
          "display_name": "Kmp",
          "governs_addons": True,
          "routing_signals": {
            "strong": ["@Composable", "kmp"],
            "tie_breakers": ["Prefer the kmp pack when Android/KMP markers are present."],
            "addon_signals": ["android-compose"],
          },
          "declared_code_review_areas": [],
          "declared_files": {
            "baseline": "code-review/bill-kmp-code-review/SKILL.md",
            "areas": {},
          },
        },
      )
    )

    pack_root = self.repo / "platform-packs" / "kmp"
    manifest_path = pack_root / "platform.yaml"
    skill_dir = pack_root / "code-review" / "bill-kmp-code-review"
    implementation_file = skill_dir / "implementation.md"

    self.assertEqual(result.kind, "platform-override-piloted")
    self.assertTrue(manifest_path.is_file())
    self.assertTrue((skill_dir / "SKILL.md").is_file())
    self.assertTrue(implementation_file.is_file())
    created_files = {path.resolve() for path in result.created_files}
    manifest_edits = {path.resolve() for path in result.manifest_edits}
    self.assertIn(manifest_path.resolve(), created_files)
    self.assertIn(manifest_path.resolve(), manifest_edits)

    pack = load_platform_pack(pack_root)
    self.assertEqual(pack.slug, "kmp")
    self.assertEqual(pack.contract_version, SHELL_CONTRACT_VERSION)
    self.assertEqual(pack.routed_skill_name, "bill-kmp-code-review")
    self.assertTrue(pack.governs_addons)
    self.assertEqual(pack.routing_signals.addon_signals, ("android-compose",))

    bootstrap_text = (skill_dir / "SKILL.md").read_text(encoding="utf-8")
    implementation_text = implementation_file.read_text(encoding="utf-8")
    self.assertIn("[implementation.md](implementation.md)", bootstrap_text)
    self.assertIn("[stack-routing.md](stack-routing.md)", bootstrap_text)
    self.assertIn("[review-orchestrator.md](review-orchestrator.md)", bootstrap_text)
    self.assertIn("[review-delegation.md](review-delegation.md)", bootstrap_text)
    self.assertIn("[telemetry-contract.md](telemetry-contract.md)", bootstrap_text)
    self.assertIn("stack-routing.md", implementation_text)
    self.assertIn("review-orchestrator.md", implementation_text)
    self.assertIn("review-delegation.md", implementation_text)
    self.assertIn("telemetry-contract.md", implementation_text)
    self.assertIn("Review session ID: <review-session-id>", implementation_text)
    self.assertIn("Review run ID: <review-run-id>", implementation_text)
    self.assertIn("Applied learnings: none | <learning references>", implementation_text)
    self.assertIn("review that area inline", implementation_text)

  def test_new_baseline_code_review_pack_requires_platform_manifest_payload(self) -> None:
    with self.assertRaises(InvalidScaffoldPayloadError):
      scaffold(
        self.payload(
          kind="platform-override-piloted",
          name="bill-kmp-code-review",
          platform="kmp",
          family="code-review",
        )
      )

  def test_quality_check_scaffold_updates_optional_pack_manifest_and_sidecars(self) -> None:
    shutil.copytree(
      OPTIONAL_PACK_FIXTURE,
      self.repo / "platform-packs" / "example-pack",
      symlinks=True,
    )

    result = scaffold(
      self.payload(
        kind="platform-override-piloted",
        name="bill-example-pack-quality-check-extra",
        platform="example-pack",
        family="quality-check",
      )
    )

    skill_dir = self.repo / "platform-packs" / "example-pack" / "quality-check" / "bill-example-pack-quality-check-extra"
    implementation_file = skill_dir / "implementation.md"
    manifest = (self.repo / "platform-packs" / "example-pack" / "platform.yaml").read_text(encoding="utf-8")
    self.assertEqual(result.kind, "platform-override-piloted")
    self.assertTrue((skill_dir / "SKILL.md").is_file())
    self.assertTrue(implementation_file.is_file())
    self.assertIn(
      "declared_quality_check_file: quality-check/bill-example-pack-quality-check-extra/SKILL.md",
      manifest,
    )
    self.assertTrue((skill_dir / "stack-routing.md").is_symlink())
    self.assertTrue((skill_dir / "telemetry-contract.md").is_symlink())
    bootstrap_text = (skill_dir / "SKILL.md").read_text(encoding="utf-8")
    implementation_text = implementation_file.read_text(encoding="utf-8")
    self.assertIn("[implementation.md](implementation.md)", bootstrap_text)
    self.assertIn("[stack-routing.md](stack-routing.md)", bootstrap_text)
    self.assertIn("[telemetry-contract.md](telemetry-contract.md)", bootstrap_text)
    self.assertIn("stack-routing.md", implementation_text)
    self.assertIn("telemetry-contract.md", implementation_text)

  def test_code_review_area_scaffold_updates_manifest_for_optional_pack(self) -> None:
    shutil.copytree(
      OPTIONAL_PACK_FIXTURE,
      self.repo / "platform-packs" / "example-pack",
      symlinks=True,
    )
    manifest_path = self.repo / "platform-packs" / "example-pack" / "platform.yaml"

    scaffold(
      self.payload(
        kind="code-review-area",
        name="bill-example-pack-code-review-architecture",
        platform="example-pack",
        area="architecture",
      )
    )

    manifest = manifest_path.read_text(encoding="utf-8")
    self.assertIn("- architecture", manifest)
    self.assertIn(
      "architecture: code-review/bill-example-pack-code-review-architecture/SKILL.md",
      manifest,
    )
    self.assertTrue(
      (
        self.repo
        / "platform-packs"
        / "example-pack"
        / "code-review"
        / "bill-example-pack-code-review-architecture"
        / "implementation.md"
      ).is_file()
    )


if __name__ == "__main__":
  unittest.main()
