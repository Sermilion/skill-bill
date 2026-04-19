from __future__ import annotations

from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from skill_bill.shell_content_contract import CEREMONY_FREE_FORM_H2S  # noqa: E402


FRAMEWORK_DUPLICATION_LINES = (
  "## Additional Resources",
  "## Output Rules",
  "## Review Output",
  "## Output Format",
  "### Telemetry",
  "### Implementation Mode Notes",
)


class ContentMdHygieneTest(unittest.TestCase):
  def test_governed_content_files_do_not_reintroduce_ceremony_headings(self) -> None:
    for content_file in sorted((ROOT / "platform-packs").rglob("content.md")):
      text = content_file.read_text(encoding="utf-8")
      lines = text.splitlines()
      for heading in CEREMONY_FREE_FORM_H2S:
        with self.subTest(content_file=content_file, heading=heading):
          self.assertNotIn(
            heading,
            lines,
            f"{content_file} must not reintroduce scaffolder-owned ceremony heading '{heading}'.",
          )

  def test_governed_content_files_do_not_inline_shared_review_contract_blocks(self) -> None:
    for content_file in sorted((ROOT / "platform-packs").rglob("content.md")):
      lines = content_file.read_text(encoding="utf-8").splitlines()
      for marker in FRAMEWORK_DUPLICATION_LINES:
        with self.subTest(content_file=content_file, marker=marker):
          self.assertNotIn(
            marker,
            lines,
            f"{content_file} must not inline shared review-contract block '{marker}'.",
          )


if __name__ == "__main__":
  unittest.main()
