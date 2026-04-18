"""Fixture-based accept/reject coverage for the shell+content contract loader.

Mirrors the fixture pattern used by ``test_validate_agent_configs_e2e.py`` so
acceptance and rejection paths are first-class. Every rejection asserts the
specific named exception and that the offending artifact is referenced in the
error message.
"""

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from skill_bill import shell_content_contract  # noqa: E402
from skill_bill.shell_content_contract import (  # noqa: E402
  ContractVersionMismatchError,
  discover_installable_pack_skills,
  InvalidContentFrontmatterError,
  InvalidManifestSchemaError,
  MissingContentFileError,
  MissingManifestError,
  MissingRequiredSectionError,
  PlatformPack,
  PyYAMLMissingError,
  SHELL_CONTRACT_VERSION,
  describe_zero_platform_packs_state,
  discover_platform_packs,
  load_platform_pack,
  load_quality_check_content,
)


FIXTURES_ROOT = ROOT / "tests" / "fixtures" / "shell_content_contract"


def _copy_pack_fixture(source: Path, destination: Path) -> None:
  for path in source.rglob("*"):
    relative = path.relative_to(source)
    target = destination / relative
    if path.is_dir():
      target.mkdir(parents=True, exist_ok=True)
      continue
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")


def _convert_skill_to_split_layout(skill_dir: Path, *, write_implementation: bool = True) -> None:
  skill_file = skill_dir / "SKILL.md"
  implementation_file = skill_dir / "implementation.md"
  original = skill_file.read_text(encoding="utf-8")
  frontmatter, separator, body = original.partition("---\n\n")
  if not separator:
    raise AssertionError(f"{skill_file} fixture is missing the expected frontmatter separator")
  skill_file.write_text(
    f"{frontmatter}{separator}"
    "# Skill Bootstrap\n\n"
    "This `SKILL.md` file stays canonical for install and discovery.\n"
    "Read and follow the active implementation in [implementation.md](implementation.md).\n",
    encoding="utf-8",
  )
  if write_implementation:
    implementation_file.write_text(body, encoding="utf-8")


class ShellContentContractLoaderTest(unittest.TestCase):
  maxDiff = None

  def test_discovery_allows_zero_pack_state(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      self.assertEqual(discover_platform_packs(Path(temp_dir) / "platform-packs"), [])

  def test_zero_pack_state_message_is_explicit(self) -> None:
    message = describe_zero_platform_packs_state(Path("/repo/platform-packs"))
    self.assertIn("No optional platform packs discovered", message)
    self.assertIn("Framework-only mode is active", message)

  def test_loads_valid_pack(self) -> None:
    pack = load_platform_pack(FIXTURES_ROOT / "valid_pack")
    self.assertIsInstance(pack, PlatformPack)
    self.assertEqual(pack.slug, "valid_pack")
    self.assertEqual(pack.contract_version, SHELL_CONTRACT_VERSION)
    self.assertEqual(pack.declared_code_review_areas, ("architecture",))
    self.assertEqual(pack.routing_signals.strong, (".fixture",))
    self.assertEqual(pack.routed_skill_name, "bill-valid_pack-code-review")

  def test_loads_split_layout_pack_and_preserves_skill_entrypoint(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      pack_root = Path(temp_dir) / "valid_pack"
      _copy_pack_fixture(FIXTURES_ROOT / "valid_pack", pack_root)
      _convert_skill_to_split_layout(pack_root / "code-review")

      pack = load_platform_pack(pack_root)
      installable = discover_installable_pack_skills(pack)

      self.assertEqual(pack.declared_files["baseline"].name, "SKILL.md")
      self.assertEqual(installable[0].source_file.name, "SKILL.md")
      self.assertEqual(installable[0].skill_dir.resolve(), (pack_root / "code-review").resolve())

  def test_rejects_missing_manifest(self) -> None:
    with self.assertRaises(MissingManifestError) as context:
      load_platform_pack(FIXTURES_ROOT / "missing_manifest")
    self.assertIn("missing_manifest", str(context.exception))
    self.assertIn("platform.yaml", str(context.exception))

  def test_rejects_missing_content_file(self) -> None:
    with self.assertRaises(MissingContentFileError) as context:
      load_platform_pack(FIXTURES_ROOT / "missing_content_file")
    message = str(context.exception)
    self.assertIn("missing_content_file", message)
    self.assertIn("baseline", message)
    self.assertIn("code-review/SKILL.md", message)

  def test_rejects_split_layout_when_bootstrap_target_is_missing(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      pack_root = Path(temp_dir) / "valid_pack"
      _copy_pack_fixture(FIXTURES_ROOT / "valid_pack", pack_root)
      _convert_skill_to_split_layout(pack_root / "code-review", write_implementation=False)

      with self.assertRaises(MissingContentFileError) as context:
        load_platform_pack(pack_root)
    self.assertIn("references missing implementation file", str(context.exception))

  def test_rejects_bad_version(self) -> None:
    with self.assertRaises(ContractVersionMismatchError) as context:
      load_platform_pack(FIXTURES_ROOT / "bad_version")
    message = str(context.exception)
    self.assertIn("bad_version", message)
    self.assertIn("9.99", message)
    self.assertIn(SHELL_CONTRACT_VERSION, message)

  def test_rejects_missing_section(self) -> None:
    with self.assertRaises(MissingRequiredSectionError) as context:
      load_platform_pack(FIXTURES_ROOT / "missing_section")
    message = str(context.exception)
    self.assertIn("missing_section", message)
    self.assertIn("## Telemetry Ceremony Hooks", message)

  def test_rejects_invalid_schema(self) -> None:
    with self.assertRaises(InvalidManifestSchemaError) as context:
      load_platform_pack(FIXTURES_ROOT / "invalid_schema")
    message = str(context.exception)
    self.assertIn("invalid_schema", message)
    self.assertIn("routing_signals", message)

  # --- Additional InvalidManifestSchemaError coverage (T-005) ------------

  def test_rejects_declared_code_review_areas_not_a_list(self) -> None:
    with self.assertRaises(InvalidManifestSchemaError) as context:
      load_platform_pack(FIXTURES_ROOT / "schema_areas_wrong_type")
    message = str(context.exception)
    self.assertIn("schema_areas_wrong_type", message)
    self.assertIn("declared_code_review_areas", message)

  def test_rejects_unapproved_area_in_declared_code_review_areas(self) -> None:
    with self.assertRaises(InvalidManifestSchemaError) as context:
      load_platform_pack(FIXTURES_ROOT / "schema_unapproved_area")
    message = str(context.exception)
    self.assertIn("schema_unapproved_area", message)
    self.assertIn("laravel", message)
    self.assertIn("declared area", message)

  def test_rejects_non_boolean_governs_addons(self) -> None:
    with self.assertRaises(InvalidManifestSchemaError) as context:
      load_platform_pack(FIXTURES_ROOT / "schema_governs_addons_wrong_type")
    message = str(context.exception)
    self.assertIn("schema_governs_addons_wrong_type", message)
    self.assertIn("governs_addons", message)

  # --- Additional contract-error coverage (A-003, P-001) -----------------

  def test_rejects_extra_area_in_declared_files(self) -> None:
    with self.assertRaises(InvalidManifestSchemaError) as context:
      load_platform_pack(FIXTURES_ROOT / "extra_area")
    message = str(context.exception)
    self.assertIn("extra_area", message)
    self.assertIn("declared_files.areas", message)
    self.assertIn("performance", message)

  def test_rejects_required_section_only_inside_fenced_code_block(self) -> None:
    with self.assertRaises(MissingRequiredSectionError) as context:
      load_platform_pack(FIXTURES_ROOT / "heading_in_fence")
    message = str(context.exception)
    self.assertIn("heading_in_fence", message)
    self.assertIn("## Specialist Scope", message)

  def test_rejects_invalid_declared_skill_frontmatter_name(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      pack_root = Path(temp_dir) / "valid_pack"
      source = FIXTURES_ROOT / "valid_pack"
      for path in source.rglob("*"):
        relative = path.relative_to(source)
        target = pack_root / relative
        if path.is_dir():
          target.mkdir(parents=True, exist_ok=True)
          continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

      baseline_file = pack_root / "code-review" / "SKILL.md"
      baseline_file.write_text(
        baseline_file.read_text(encoding="utf-8").replace(
          "name: bill-valid_pack-code-review",
          "name: wrong-name",
        ),
        encoding="utf-8",
      )

      with self.assertRaises(InvalidContentFrontmatterError) as context:
        load_platform_pack(pack_root)
    self.assertIn("wrong-name", str(context.exception))

  # --- PyYAML missing coverage (P-002) -----------------------------------

  def test_raises_pyyaml_missing_error_when_yaml_import_fails(self) -> None:
    with mock.patch.object(
      shell_content_contract,
      "_import_yaml",
      side_effect=PyYAMLMissingError(
        "PyYAML is required to load platform packs. Install it via the "
        "project venv (`./.venv/bin/pip install pyyaml>=6`) or run the "
        "validator through `.venv/bin/python3 scripts/validate_agent_configs.py`."
      ),
    ):
      with self.assertRaises(PyYAMLMissingError) as context:
        load_platform_pack(FIXTURES_ROOT / "valid_pack")
    message = str(context.exception)
    self.assertIn("PyYAML", message)
    self.assertIn(".venv/bin/pip install pyyaml", message)


class QualityCheckContentContractTest(unittest.TestCase):
  """SKILL-16: optional declared_quality_check_file loader coverage."""

  maxDiff = None

  def test_loads_quality_check_only_fixture(self) -> None:
    pack = load_platform_pack(FIXTURES_ROOT / "quality_check_only")
    self.assertIsNotNone(pack.declared_quality_check_file)
    resolved = load_quality_check_content(pack)
    self.assertEqual(resolved, pack.declared_quality_check_file)
    self.assertTrue(resolved.is_file())

  def test_loads_code_review_and_quality_check_fixture(self) -> None:
    pack = load_platform_pack(FIXTURES_ROOT / "code_review_and_quality_check")
    self.assertIsNotNone(pack.declared_quality_check_file)
    resolved = load_quality_check_content(pack)
    self.assertTrue(resolved.is_file())
    # Both code-review baseline and quality-check files must succeed.
    self.assertEqual(pack.declared_code_review_areas, ("architecture",))

  def test_loads_split_layout_quality_check_and_preserves_declared_skill_path(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      pack_root = Path(temp_dir) / "quality_check_only"
      _copy_pack_fixture(FIXTURES_ROOT / "quality_check_only", pack_root)
      _convert_skill_to_split_layout(pack_root / "quality-check")

      pack = load_platform_pack(pack_root)
      resolved = load_quality_check_content(pack)

      self.assertEqual(resolved.name, "SKILL.md")
      self.assertEqual(resolved, pack.declared_quality_check_file)

  def test_rejects_quality_check_missing_file(self) -> None:
    pack = load_platform_pack(FIXTURES_ROOT / "quality_check_missing_file")
    with self.assertRaises(MissingContentFileError) as context:
      load_quality_check_content(pack)
    message = str(context.exception)
    self.assertIn("quality_check_missing_file", message)
    self.assertIn("does-not-exist.md", message)

  def test_rejects_quality_check_missing_section(self) -> None:
    pack = load_platform_pack(FIXTURES_ROOT / "quality_check_missing_section")
    with self.assertRaises(MissingRequiredSectionError) as context:
      load_quality_check_content(pack)
    message = str(context.exception)
    self.assertIn("quality_check_missing_section", message)
    self.assertIn("## Fix Strategy", message)

  def test_valid_pack_without_quality_check_key_is_none(self) -> None:
    """A pack that does NOT declare the key has declared_quality_check_file=None.

    Calling load_quality_check_content on such a pack raises
    MissingContentFileError rather than silently returning nothing.
    """
    pack = load_platform_pack(FIXTURES_ROOT / "valid_pack")
    self.assertIsNone(pack.declared_quality_check_file)
    with self.assertRaises(MissingContentFileError) as context:
      load_quality_check_content(pack)
    self.assertIn("valid_pack", str(context.exception))


if __name__ == "__main__":
  unittest.main()
