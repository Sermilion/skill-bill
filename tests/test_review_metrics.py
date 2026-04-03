from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
import io
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import review_metrics  # noqa: E402


SAMPLE_REVIEW = """\
Routed to: bill-agent-config-code-review
Review run ID: rvw-20260402-001
Detected review scope: unstaged changes
Detected stack: agent-config
Signals: README.md, install.sh
Execution mode: inline
Reason: agent-config signals dominate

### 2. Risk Register
- [F-001] Major | High | README.md:12 | README wording is stale after the routing change.
- [F-002] Minor | Medium | install.sh:88 | Installer prompt wording is inconsistent with the new flow.
"""


class ReviewMetricsTest(unittest.TestCase):
  def test_import_review_creates_run_and_findings(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      review_path = Path(temp_dir) / "review.txt"
      review_path.write_text(SAMPLE_REVIEW, encoding="utf-8")

      result = self.run_cli(
        ["--db", str(db_path), "import-review", str(review_path), "--format", "json"]
      )

      self.assertEqual(result["exit_code"], 0, result["stderr"])
      payload = json.loads(result["stdout"])
      self.assertEqual(payload["review_run_id"], "rvw-20260402-001")
      self.assertEqual(payload["finding_count"], 2)
      self.assertEqual(payload["routed_skill"], "bill-agent-config-code-review")

  def test_record_feedback_and_stats_report_acceptance_rate(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      self.import_sample_review(db_path, temp_dir)

      accepted = self.run_cli(
        [
          "--db",
          str(db_path),
          "record-feedback",
          "--run-id",
          "rvw-20260402-001",
          "--event",
          "accepted",
          "--finding",
          "F-001",
          "--format",
          "json",
        ]
      )
      self.assertEqual(accepted["exit_code"], 0, accepted["stderr"])

      fix_requested = self.run_cli(
        [
          "--db",
          str(db_path),
          "record-feedback",
          "--run-id",
          "rvw-20260402-001",
          "--event",
          "fix_requested",
          "--finding",
          "F-001",
          "--finding",
          "F-002",
          "--format",
          "json",
        ]
      )
      self.assertEqual(fix_requested["exit_code"], 0, fix_requested["stderr"])

      stats = self.run_cli(
        ["--db", str(db_path), "stats", "--run-id", "rvw-20260402-001", "--format", "json"]
      )
      self.assertEqual(stats["exit_code"], 0, stats["stderr"])

      payload = json.loads(stats["stdout"])
      self.assertEqual(payload["total_findings"], 2)
      self.assertEqual(payload["accepted_findings"], 1)
      self.assertEqual(payload["fix_requested_findings"], 2)
      self.assertEqual(payload["actionable_findings"], 2)
      self.assertEqual(payload["acceptance_rate"], 1.0)

  def test_import_review_rejects_missing_review_run_id(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      review_path = Path(temp_dir) / "review.txt"
      review_path.write_text(
        SAMPLE_REVIEW.replace("Review run ID: rvw-20260402-001\n", ""),
        encoding="utf-8",
      )

      result = self.run_cli(
        ["--db", str(db_path), "import-review", str(review_path), "--format", "json"]
      )

      self.assertEqual(result["exit_code"], 1)
      self.assertIn("Review output is missing 'Review run ID", result["stderr"])

  def test_triage_maps_numbers_to_findings_and_records_notes(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      self.import_sample_review(db_path, temp_dir)

      result = self.run_cli(
        [
          "--db",
          str(db_path),
          "triage",
          "--run-id",
          "rvw-20260402-001",
          "--decision",
          "1 fix - keep current terminology",
          "--decision",
          "2 skip - wording is intentional",
          "--format",
          "json",
        ]
      )

      self.assertEqual(result["exit_code"], 0, result["stderr"])
      payload = json.loads(result["stdout"])
      self.assertEqual(len(payload["recorded"]), 2)
      self.assertEqual(payload["recorded"][0]["finding_id"], "F-001")
      self.assertEqual(payload["recorded"][0]["event_type"], "fix_requested")
      self.assertEqual(payload["recorded"][0]["note"], "keep current terminology")
      self.assertEqual(payload["recorded"][1]["finding_id"], "F-002")
      self.assertEqual(payload["recorded"][1]["event_type"], "dismissed")
      self.assertEqual(payload["recorded"][1]["note"], "wording is intentional")

      with sqlite3.connect(db_path) as connection:
        rows = connection.execute(
          """
          SELECT finding_id, event_type, note
          FROM feedback_events
          ORDER BY id
          """
        ).fetchall()
      self.assertEqual(
        rows,
        [
          ("F-001", "fix_requested", "keep current terminology"),
          ("F-002", "dismissed", "wording is intentional"),
        ],
      )

  def test_triage_rejects_unknown_number(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      self.import_sample_review(db_path, temp_dir)

      result = self.run_cli(
        [
          "--db",
          str(db_path),
          "triage",
          "--run-id",
          "rvw-20260402-001",
          "--decision",
          "3 fix",
          "--format",
          "json",
        ]
      )

      self.assertEqual(result["exit_code"], 1)
      self.assertIn("Unknown finding number '3'", result["stderr"])

  def test_triage_ignores_separator_only_notes(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      self.import_sample_review(db_path, temp_dir)

      result = self.run_cli(
        [
          "--db",
          str(db_path),
          "triage",
          "--run-id",
          "rvw-20260402-001",
          "--decision",
          "1 fix -",
          "--format",
          "json",
        ]
      )

      self.assertEqual(result["exit_code"], 0, result["stderr"])
      payload = json.loads(result["stdout"])
      self.assertEqual(payload["recorded"][0]["note"], "")

      with sqlite3.connect(db_path) as connection:
        note = connection.execute(
          "SELECT note FROM feedback_events WHERE finding_id = 'F-001'"
        ).fetchone()[0]
      self.assertEqual(note, "")

  def test_learnings_crud_keeps_history_separate_from_feedback(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      self.import_sample_review(db_path, temp_dir)

      triage = self.run_cli(
        [
          "--db",
          str(db_path),
          "triage",
          "--run-id",
          "rvw-20260402-001",
          "--decision",
          "2 skip - intentional wording",
          "--format",
          "json",
        ]
      )
      self.assertEqual(triage["exit_code"], 0, triage["stderr"])

      created = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "add",
          "--scope",
          "repo",
          "--scope-key",
          "Sermilion/skill-bill",
          "--title",
          "Ignore minor README wording",
          "--rule",
          "Do not flag minor README wording issues in this repo unless they change behavior.",
          "--reason",
          "Repeated dismissals show this is usually intentional.",
          "--from-run",
          "rvw-20260402-001",
          "--from-finding",
          "F-002",
          "--format",
          "json",
        ]
      )
      self.assertEqual(created["exit_code"], 0, created["stderr"])
      created_payload = json.loads(created["stdout"])
      learning_id = created_payload["id"]
      self.assertEqual(created_payload["status"], "active")
      self.assertEqual(created_payload["source_finding_id"], "F-002")

      listed = self.run_cli(
        ["--db", str(db_path), "learnings", "list", "--format", "json"]
      )
      self.assertEqual(listed["exit_code"], 0, listed["stderr"])
      listed_payload = json.loads(listed["stdout"])
      self.assertEqual(len(listed_payload["learnings"]), 1)

      shown = self.run_cli(
        ["--db", str(db_path), "learnings", "show", "--id", str(learning_id), "--format", "json"]
      )
      self.assertEqual(shown["exit_code"], 0, shown["stderr"])
      self.assertEqual(json.loads(shown["stdout"])["title"], "Ignore minor README wording")

      edited = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "edit",
          "--id",
          str(learning_id),
          "--title",
          "Ignore minor wording churn",
          "--reason",
          "Confirmed twice by explicit skip feedback.",
          "--format",
          "json",
        ]
      )
      self.assertEqual(edited["exit_code"], 0, edited["stderr"])
      edited_payload = json.loads(edited["stdout"])
      self.assertEqual(edited_payload["title"], "Ignore minor wording churn")

      disabled = self.run_cli(
        ["--db", str(db_path), "learnings", "disable", "--id", str(learning_id), "--format", "json"]
      )
      self.assertEqual(disabled["exit_code"], 0, disabled["stderr"])
      self.assertEqual(json.loads(disabled["stdout"])["status"], "disabled")

      deleted = self.run_cli(
        ["--db", str(db_path), "learnings", "delete", "--id", str(learning_id), "--format", "json"]
      )
      self.assertEqual(deleted["exit_code"], 0, deleted["stderr"])

      with sqlite3.connect(db_path) as connection:
        feedback_count = connection.execute("SELECT COUNT(*) FROM feedback_events").fetchone()[0]
        learning_count = connection.execute("SELECT COUNT(*) FROM learnings").fetchone()[0]
      self.assertEqual(feedback_count, 1)
      self.assertEqual(learning_count, 0)

  def test_learnings_resolve_returns_active_entries_in_scope_precedence_order(self) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
      db_path = Path(temp_dir) / "metrics.db"
      self.import_sample_review(db_path, temp_dir)

      global_learning = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "add",
          "--scope",
          "global",
          "--title",
          "Always require evidence",
          "--rule",
          "Do not keep a finding unless evidence is concrete.",
          "--format",
          "json",
        ]
      )
      repo_learning = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "add",
          "--scope",
          "repo",
          "--scope-key",
          "Sermilion/skill-bill",
          "--title",
          "Ignore wording churn",
          "--rule",
          "Do not flag wording churn unless behavior changes.",
          "--format",
          "json",
        ]
      )
      skill_learning = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "add",
          "--scope",
          "skill",
          "--scope-key",
          "bill-agent-config-code-review",
          "--title",
          "Prefer validator-backed claims",
          "--rule",
          "In agent-config repos, prefer findings backed by validator or contract evidence.",
          "--format",
          "json",
        ]
      )
      disabled_learning = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "add",
          "--scope",
          "repo",
          "--scope-key",
          "Sermilion/skill-bill",
          "--title",
          "Disabled learning",
          "--rule",
          "This should never show up in resolve output.",
          "--format",
          "json",
        ]
      )

      self.assertEqual(global_learning["exit_code"], 0, global_learning["stderr"])
      self.assertEqual(repo_learning["exit_code"], 0, repo_learning["stderr"])
      self.assertEqual(skill_learning["exit_code"], 0, skill_learning["stderr"])
      self.assertEqual(disabled_learning["exit_code"], 0, disabled_learning["stderr"])

      disabled_id = json.loads(disabled_learning["stdout"])["id"]
      disable_result = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "disable",
          "--id",
          str(disabled_id),
          "--format",
          "json",
        ]
      )
      self.assertEqual(disable_result["exit_code"], 0, disable_result["stderr"])

      resolved = self.run_cli(
        [
          "--db",
          str(db_path),
          "learnings",
          "resolve",
          "--repo",
          "Sermilion/skill-bill",
          "--skill",
          "bill-agent-config-code-review",
          "--format",
          "json",
        ]
      )
      self.assertEqual(resolved["exit_code"], 0, resolved["stderr"])

      payload = json.loads(resolved["stdout"])
      self.assertEqual(payload["scope_precedence"], ["skill", "repo", "global"])
      self.assertEqual(payload["repo_scope_key"], "Sermilion/skill-bill")
      self.assertEqual(payload["skill_name"], "bill-agent-config-code-review")
      self.assertEqual(
        [entry["scope"] for entry in payload["learnings"]],
        ["skill", "repo", "global"],
      )
      self.assertEqual(
        [entry["reference"] for entry in payload["learnings"]],
        ["L-003", "L-002", "L-001"],
      )
      self.assertEqual(payload["applied_learnings"], "L-003, L-002, L-001")

  def import_sample_review(self, db_path: Path, temp_dir: str) -> None:
    review_path = Path(temp_dir) / "review.txt"
    review_path.write_text(SAMPLE_REVIEW, encoding="utf-8")
    result = self.run_cli(
      ["--db", str(db_path), "import-review", str(review_path), "--format", "json"]
    )
    self.assertEqual(result["exit_code"], 0, result["stderr"])

  def run_cli(self, argv: list[str]) -> dict[str, str | int]:
    stdout = io.StringIO()
    stderr = io.StringIO()
    with redirect_stdout(stdout), redirect_stderr(stderr):
      exit_code = review_metrics.main(argv)
    return {
      "exit_code": exit_code,
      "stdout": stdout.getvalue(),
      "stderr": stderr.getvalue(),
    }


if __name__ == "__main__":
  unittest.main()
