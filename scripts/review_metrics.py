#!/usr/bin/env python3

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import argparse
import json
import os
import re
import sqlite3
import sys


DEFAULT_DB_PATH = Path.home() / ".skill-bill" / "review-metrics.db"
DB_ENVIRONMENT_KEY = "SKILL_BILL_REVIEW_DB"
EVENT_TYPES = ("accepted", "dismissed", "fix_requested")
LEARNING_SCOPES = ("global", "repo", "skill")
LEARNING_STATUSES = ("active", "disabled")
LEARNING_SCOPE_PRECEDENCE = ("skill", "repo", "global")
MEANINGFUL_NOTE_PATTERN = re.compile(r"[A-Za-z0-9]")

REVIEW_RUN_ID_PATTERN = re.compile(r"^Review run ID:\s*(?P<value>[A-Za-z0-9._:-]+)\s*$", re.MULTILINE)
SUMMARY_PATTERNS = {
  "routed_skill": re.compile(r"^Routed to:\s*(?P<value>.+?)\s*$", re.MULTILINE),
  "detected_scope": re.compile(r"^Detected review scope:\s*(?P<value>.+?)\s*$", re.MULTILINE),
  "detected_stack": re.compile(r"^Detected stack:\s*(?P<value>.+?)\s*$", re.MULTILINE),
  "execution_mode": re.compile(r"^Execution mode:\s*(?P<value>inline|delegated)\s*$", re.MULTILINE),
}
FINDING_PATTERN = re.compile(
  r"^\s*-\s+\[(?P<finding_id>F-\d{3})\]\s+"
  r"(?P<severity>Blocker|Major|Minor)\s+\|\s+"
  r"(?P<confidence>High|Medium|Low)\s+\|\s+"
  r"(?P<location>[^|]+?)\s+\|\s+"
  r"(?P<description>.+)$",
  re.MULTILINE,
)
TRIAGE_DECISION_PATTERN = re.compile(
  r"^\s*(?P<number>\d+)\s+(?P<action>fix|accept|accepted|dismiss|skip)"
  r"(?:\s*(?:[:-]\s*|\s+)(?P<note>.+))?\s*$",
  re.IGNORECASE,
)


@dataclass(frozen=True)
class ImportedFinding:
  finding_id: str
  severity: str
  confidence: str
  location: str
  description: str
  finding_text: str


@dataclass(frozen=True)
class ImportedReview:
  review_run_id: str
  raw_text: str
  routed_skill: str | None
  detected_scope: str | None
  detected_stack: str | None
  execution_mode: str | None
  findings: tuple[ImportedFinding, ...]


@dataclass(frozen=True)
class TriageDecision:
  number: int
  finding_id: str
  event_type: str
  note: str


def resolve_db_path(cli_value: str | None) -> Path:
  candidate = cli_value or os.environ.get(DB_ENVIRONMENT_KEY)
  if candidate:
    return Path(candidate).expanduser().resolve()
  return DEFAULT_DB_PATH.expanduser().resolve()


def ensure_database(path: Path) -> sqlite3.Connection:
  path.parent.mkdir(parents=True, exist_ok=True)
  connection = sqlite3.connect(path)
  connection.execute("PRAGMA foreign_keys = ON")
  connection.row_factory = sqlite3.Row
  connection.executescript(
    """
    CREATE TABLE IF NOT EXISTS review_runs (
      review_run_id TEXT PRIMARY KEY,
      routed_skill TEXT,
      detected_scope TEXT,
      detected_stack TEXT,
      execution_mode TEXT,
      source_path TEXT,
      raw_text TEXT NOT NULL,
      imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS findings (
      review_run_id TEXT NOT NULL,
      finding_id TEXT NOT NULL,
      severity TEXT NOT NULL,
      confidence TEXT NOT NULL,
      location TEXT NOT NULL,
      description TEXT NOT NULL,
      finding_text TEXT NOT NULL,
      PRIMARY KEY (review_run_id, finding_id),
      FOREIGN KEY (review_run_id) REFERENCES review_runs(review_run_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS feedback_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      review_run_id TEXT NOT NULL,
      finding_id TEXT NOT NULL,
      event_type TEXT NOT NULL CHECK (event_type IN ('accepted', 'dismissed', 'fix_requested')),
      note TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (review_run_id, finding_id) REFERENCES findings(review_run_id, finding_id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_feedback_events_run
      ON feedback_events(review_run_id, finding_id, event_type);

    CREATE TABLE IF NOT EXISTS learnings (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      scope TEXT NOT NULL CHECK (scope IN ('global', 'repo', 'skill')),
      scope_key TEXT NOT NULL DEFAULT '',
      title TEXT NOT NULL,
      rule_text TEXT NOT NULL,
      rationale TEXT NOT NULL DEFAULT '',
      status TEXT NOT NULL CHECK (status IN ('active', 'disabled')) DEFAULT 'active',
      source_review_run_id TEXT,
      source_finding_id TEXT,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CHECK ((source_review_run_id IS NULL) = (source_finding_id IS NULL)),
      FOREIGN KEY (source_review_run_id, source_finding_id)
        REFERENCES findings(review_run_id, finding_id)
        ON DELETE SET NULL
    );

    CREATE INDEX IF NOT EXISTS idx_learnings_scope
      ON learnings(scope, scope_key, status);
    """
  )
  return connection


def parse_review(text: str) -> ImportedReview:
  review_run_match = REVIEW_RUN_ID_PATTERN.search(text)
  if not review_run_match:
    raise ValueError("Review output is missing 'Review run ID: <review-run-id>'.")

  findings: list[ImportedFinding] = []
  seen_ids: set[str] = set()
  for match in FINDING_PATTERN.finditer(text):
    finding_id = match.group("finding_id")
    if finding_id in seen_ids:
      raise ValueError(f"Review output contains duplicate finding id '{finding_id}'.")
    seen_ids.add(finding_id)
    findings.append(
      ImportedFinding(
        finding_id=finding_id,
        severity=match.group("severity"),
        confidence=match.group("confidence"),
        location=match.group("location").strip(),
        description=match.group("description").strip(),
        finding_text=match.group(0).strip(),
      )
    )

  if not findings:
    raise ValueError(
      "Review output is missing machine-readable findings. Expected lines like "
      "'- [F-001] Major | High | file:line | description'."
    )

  return ImportedReview(
    review_run_id=review_run_match.group("value"),
    raw_text=text,
    routed_skill=extract_summary_value(text, "routed_skill"),
    detected_scope=extract_summary_value(text, "detected_scope"),
    detected_stack=extract_summary_value(text, "detected_stack"),
    execution_mode=extract_summary_value(text, "execution_mode"),
    findings=tuple(findings),
  )


def extract_summary_value(text: str, key: str) -> str | None:
  match = SUMMARY_PATTERNS[key].search(text)
  if not match:
    return None
  return match.group("value").strip()


def read_input(input_path: str) -> tuple[str, str | None]:
  if input_path == "-":
    return (sys.stdin.read(), None)
  path = Path(input_path).expanduser().resolve()
  return (path.read_text(encoding="utf-8"), str(path))


def save_imported_review(
  connection: sqlite3.Connection,
  review: ImportedReview,
  *,
  source_path: str | None,
) -> None:
  with connection:
    connection.execute(
      """
      INSERT INTO review_runs (
        review_run_id,
        routed_skill,
        detected_scope,
        detected_stack,
        execution_mode,
        source_path,
        raw_text
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(review_run_id) DO UPDATE SET
        routed_skill = excluded.routed_skill,
        detected_scope = excluded.detected_scope,
        detected_stack = excluded.detected_stack,
        execution_mode = excluded.execution_mode,
        source_path = excluded.source_path,
        raw_text = excluded.raw_text
      """,
      (
        review.review_run_id,
        review.routed_skill,
        review.detected_scope,
        review.detected_stack,
        review.execution_mode,
        source_path,
        review.raw_text,
      ),
    )

    for finding in review.findings:
      connection.execute(
        """
        INSERT INTO findings (
          review_run_id,
          finding_id,
          severity,
          confidence,
          location,
          description,
          finding_text
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(review_run_id, finding_id) DO UPDATE SET
          severity = excluded.severity,
          confidence = excluded.confidence,
          location = excluded.location,
          description = excluded.description,
          finding_text = excluded.finding_text
        """,
        (
          review.review_run_id,
          finding.finding_id,
          finding.severity,
          finding.confidence,
          finding.location,
          finding.description,
          finding.finding_text,
        ),
      )


def record_feedback(
  connection: sqlite3.Connection,
  *,
  review_run_id: str,
  finding_ids: list[str],
  event_type: str,
  note: str,
) -> None:
  if not review_exists(connection, review_run_id):
    raise ValueError(f"Unknown review run id '{review_run_id}'. Import the review first.")

  missing_findings = [
    finding_id
    for finding_id in finding_ids
    if not finding_exists(connection, review_run_id, finding_id)
  ]
  if missing_findings:
    raise ValueError(
      "Unknown finding ids for review run "
      f"'{review_run_id}': {', '.join(sorted(missing_findings))}"
    )

  with connection:
    for finding_id in finding_ids:
      connection.execute(
        """
        INSERT INTO feedback_events (review_run_id, finding_id, event_type, note)
        VALUES (?, ?, ?, ?)
        """,
        (review_run_id, finding_id, event_type, note),
      )


def review_exists(connection: sqlite3.Connection, review_run_id: str) -> bool:
  row = connection.execute(
    "SELECT 1 FROM review_runs WHERE review_run_id = ?",
    (review_run_id,),
  ).fetchone()
  return row is not None


def finding_exists(connection: sqlite3.Connection, review_run_id: str, finding_id: str) -> bool:
  row = connection.execute(
    """
    SELECT 1
    FROM findings
    WHERE review_run_id = ? AND finding_id = ?
    """,
    (review_run_id, finding_id),
  ).fetchone()
  return row is not None


def fetch_numbered_findings(connection: sqlite3.Connection, review_run_id: str) -> list[dict[str, object]]:
  if not review_exists(connection, review_run_id):
    raise ValueError(f"Unknown review run id '{review_run_id}'.")

  rows = connection.execute(
    """
    SELECT finding_id, severity, confidence, location, description
    FROM findings
    WHERE review_run_id = ?
    ORDER BY finding_id
    """,
    (review_run_id,),
  ).fetchall()

  numbered_findings: list[dict[str, object]] = []
  for index, row in enumerate(rows, start=1):
    numbered_findings.append(
      {
        "number": index,
        "finding_id": row["finding_id"],
        "severity": row["severity"],
        "confidence": row["confidence"],
        "location": row["location"],
        "description": row["description"],
      }
    )
  return numbered_findings


def parse_triage_decisions(
  raw_decisions: list[str],
  numbered_findings: list[dict[str, object]],
) -> list[TriageDecision]:
  number_to_finding = {
    int(entry["number"]): str(entry["finding_id"])
    for entry in numbered_findings
  }
  decisions: list[TriageDecision] = []
  seen_numbers: set[int] = set()

  for raw_decision in raw_decisions:
    match = TRIAGE_DECISION_PATTERN.fullmatch(raw_decision.strip())
    if not match:
      raise ValueError(
        "Invalid triage decision format. Use entries like '1 fix', "
        "'2 skip - intentional', or '3 accept - good catch'."
      )

    number = int(match.group("number"))
    if number not in number_to_finding:
      raise ValueError(f"Unknown finding number '{number}' for the current review run.")
    if number in seen_numbers:
      raise ValueError(f"Duplicate triage decision for finding number '{number}'.")
    seen_numbers.add(number)

    decisions.append(
      TriageDecision(
        number=number,
        finding_id=number_to_finding[number],
        event_type=normalize_triage_action(match.group("action")),
        note=normalize_triage_note(match.group("note")),
      )
    )

  return decisions


def normalize_triage_action(raw_action: str) -> str:
  action = raw_action.strip().lower()
  if action == "fix":
    return "fix_requested"
  if action in ("accept", "accepted"):
    return "accepted"
  if action in ("dismiss", "skip"):
    return "dismissed"
  raise ValueError(f"Unsupported triage action '{raw_action}'.")


def normalize_triage_note(raw_note: str | None) -> str:
  note = (raw_note or "").strip()
  if note and not MEANINGFUL_NOTE_PATTERN.search(note):
    return ""
  return note


def stats_payload(connection: sqlite3.Connection, review_run_id: str | None) -> dict[str, object]:
  if review_run_id and not review_exists(connection, review_run_id):
    raise ValueError(f"Unknown review run id '{review_run_id}'.")

  total_findings = count_rows(
    connection,
    "SELECT COUNT(*) FROM findings",
    review_run_id=review_run_id,
  )
  accepted_findings = count_distinct_feedback(
    connection,
    event_types=("accepted",),
    review_run_id=review_run_id,
  )
  dismissed_findings = count_distinct_feedback(
    connection,
    event_types=("dismissed",),
    review_run_id=review_run_id,
  )
  fix_requested_findings = count_distinct_feedback(
    connection,
    event_types=("fix_requested",),
    review_run_id=review_run_id,
  )
  actionable_findings = count_distinct_feedback(
    connection,
    event_types=("accepted", "fix_requested"),
    review_run_id=review_run_id,
  )

  acceptance_rate = 0.0
  if total_findings:
    acceptance_rate = round(actionable_findings / total_findings, 3)

  return {
    "review_run_id": review_run_id,
    "total_findings": total_findings,
    "accepted_findings": accepted_findings,
    "dismissed_findings": dismissed_findings,
    "fix_requested_findings": fix_requested_findings,
    "actionable_findings": actionable_findings,
    "acceptance_rate": acceptance_rate,
  }


def count_rows(
  connection: sqlite3.Connection,
  base_query: str,
  *,
  review_run_id: str | None = None,
) -> int:
  query = base_query
  parameters: list[str] = []
  if review_run_id:
    query += " WHERE review_run_id = ?"
    parameters.append(review_run_id)
  row = connection.execute(query, tuple(parameters)).fetchone()
  if row is None:
    return 0
  return int(row[0])


def count_distinct_feedback(
  connection: sqlite3.Connection,
  *,
  event_types: tuple[str, ...],
  review_run_id: str | None = None,
) -> int:
  clauses: list[str] = []
  parameters: list[str] = []
  if review_run_id:
    clauses.append("review_run_id = ?")
    parameters.append(review_run_id)

  if len(event_types) == 1:
    clauses.append("event_type = ?")
    parameters.append(event_types[0])
  else:
    placeholders = ", ".join("?" for _ in event_types)
    clauses.append(f"event_type IN ({placeholders})")
    parameters.extend(event_types)

  where_clause = ""
  if clauses:
    where_clause = " WHERE " + " AND ".join(clauses)

  row = connection.execute(
    """
    SELECT COUNT(DISTINCT review_run_id || ':' || finding_id)
    FROM feedback_events
    """
    + where_clause,
    tuple(parameters),
  ).fetchone()
  if row is None:
    return 0
  return int(row[0])


def validate_learning_scope(scope: str, scope_key: str) -> tuple[str, str]:
  if scope not in LEARNING_SCOPES:
    raise ValueError(f"Learning scope must be one of {', '.join(LEARNING_SCOPES)}.")

  normalized_scope_key = scope_key.strip()
  if scope == "global":
    return (scope, "")
  if not normalized_scope_key:
    raise ValueError(f"Learning scope '{scope}' requires a non-empty --scope-key.")
  return (scope, normalized_scope_key)


def validate_learning_source(
  connection: sqlite3.Connection,
  *,
  source_review_run_id: str | None,
  source_finding_id: str | None,
) -> tuple[str | None, str | None]:
  if bool(source_review_run_id) != bool(source_finding_id):
    raise ValueError("Learning source requires both --from-run and --from-finding, or neither.")

  if not source_review_run_id:
    return (None, None)

  if not finding_exists(connection, source_review_run_id, source_finding_id or ""):
    raise ValueError(
      "Unknown learning source "
      f"'{source_review_run_id}:{source_finding_id}'. Import the review and finding first."
  )
  return (source_review_run_id, source_finding_id)


def normalize_optional_lookup_value(raw_value: str | None, argument_name: str) -> str | None:
  if raw_value is None:
    return None
  normalized = raw_value.strip()
  if not normalized:
    raise ValueError(f"{argument_name} must not be empty when provided.")
  return normalized


def add_learning(
  connection: sqlite3.Connection,
  *,
  scope: str,
  scope_key: str,
  title: str,
  rule_text: str,
  rationale: str,
  source_review_run_id: str | None,
  source_finding_id: str | None,
) -> int:
  scope, scope_key = validate_learning_scope(scope, scope_key)
  source_review_run_id, source_finding_id = validate_learning_source(
    connection,
    source_review_run_id=source_review_run_id,
    source_finding_id=source_finding_id,
  )

  if not title.strip():
    raise ValueError("Learning title must not be empty.")
  if not rule_text.strip():
    raise ValueError("Learning rule text must not be empty.")

  with connection:
    cursor = connection.execute(
      """
      INSERT INTO learnings (
        scope,
        scope_key,
        title,
        rule_text,
        rationale,
        status,
        source_review_run_id,
        source_finding_id
      ) VALUES (?, ?, ?, ?, ?, 'active', ?, ?)
      """,
      (
        scope,
        scope_key,
        title.strip(),
        rule_text.strip(),
        rationale.strip(),
        source_review_run_id,
        source_finding_id,
      ),
    )
  return int(cursor.lastrowid)


def get_learning(connection: sqlite3.Connection, learning_id: int) -> sqlite3.Row:
  row = connection.execute(
    """
    SELECT
      id,
      scope,
      scope_key,
      title,
      rule_text,
      rationale,
      status,
      source_review_run_id,
      source_finding_id,
      created_at,
      updated_at
    FROM learnings
    WHERE id = ?
    """,
    (learning_id,),
  ).fetchone()
  if row is None:
    raise ValueError(f"Unknown learning id '{learning_id}'.")
  return row


def list_learnings(
  connection: sqlite3.Connection,
  *,
  status: str,
) -> list[sqlite3.Row]:
  parameters: list[str] = []
  query = """
    SELECT
      id,
      scope,
      scope_key,
      title,
      rule_text,
      rationale,
      status,
      source_review_run_id,
      source_finding_id,
      created_at,
      updated_at
    FROM learnings
  """
  if status != "all":
    query += " WHERE status = ?"
    parameters.append(status)
  query += " ORDER BY id"
  return connection.execute(query, tuple(parameters)).fetchall()


def resolve_learnings(
  connection: sqlite3.Connection,
  *,
  repo_scope_key: str | None,
  skill_name: str | None,
) -> tuple[str | None, str | None, list[sqlite3.Row]]:
  repo_scope_key = normalize_optional_lookup_value(repo_scope_key, "--repo")
  skill_name = normalize_optional_lookup_value(skill_name, "--skill")

  scope_clauses = ["scope = 'global'"]
  parameters: list[str] = []
  if repo_scope_key is not None:
    scope_clauses.append("(scope = 'repo' AND scope_key = ?)")
    parameters.append(repo_scope_key)
  if skill_name is not None:
    scope_clauses.append("(scope = 'skill' AND scope_key = ?)")
    parameters.append(skill_name)

  rows = connection.execute(
    f"""
    SELECT
      id,
      scope,
      scope_key,
      title,
      rule_text,
      rationale,
      status,
      source_review_run_id,
      source_finding_id,
      created_at,
      updated_at
    FROM learnings
    WHERE status = 'active'
      AND ({' OR '.join(scope_clauses)})
    ORDER BY
      CASE scope
        WHEN 'skill' THEN 0
        WHEN 'repo' THEN 1
        ELSE 2
      END,
      id
    """,
    tuple(parameters),
  ).fetchall()
  return (repo_scope_key, skill_name, list(rows))


def edit_learning(
  connection: sqlite3.Connection,
  *,
  learning_id: int,
  scope: str | None,
  scope_key: str | None,
  title: str | None,
  rule_text: str | None,
  rationale: str | None,
) -> sqlite3.Row:
  current = get_learning(connection, learning_id)

  next_scope = current["scope"] if scope is None else scope
  next_scope_key = current["scope_key"] if scope_key is None else scope_key
  next_scope, next_scope_key = validate_learning_scope(next_scope, next_scope_key)
  next_title = current["title"] if title is None else title.strip()
  next_rule_text = current["rule_text"] if rule_text is None else rule_text.strip()
  next_rationale = current["rationale"] if rationale is None else rationale.strip()

  if not next_title:
    raise ValueError("Learning title must not be empty.")
  if not next_rule_text:
    raise ValueError("Learning rule text must not be empty.")

  with connection:
    connection.execute(
      """
      UPDATE learnings
      SET scope = ?,
          scope_key = ?,
          title = ?,
          rule_text = ?,
          rationale = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
      """,
      (
        next_scope,
        next_scope_key,
        next_title,
        next_rule_text,
        next_rationale,
        learning_id,
      ),
    )
  return get_learning(connection, learning_id)


def set_learning_status(
  connection: sqlite3.Connection,
  *,
  learning_id: int,
  status: str,
) -> sqlite3.Row:
  if status not in LEARNING_STATUSES:
    raise ValueError(f"Learning status must be one of {', '.join(LEARNING_STATUSES)}.")
  get_learning(connection, learning_id)
  with connection:
    connection.execute(
      """
      UPDATE learnings
      SET status = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
      """,
      (status, learning_id),
    )
  return get_learning(connection, learning_id)


def delete_learning(connection: sqlite3.Connection, learning_id: int) -> None:
  get_learning(connection, learning_id)
  with connection:
    connection.execute("DELETE FROM learnings WHERE id = ?", (learning_id,))


def learning_reference(learning_id: int) -> str:
  return f"L-{learning_id:03d}"


def learning_payload(row: sqlite3.Row) -> dict[str, object]:
  return {
    "id": row["id"],
    "reference": learning_reference(int(row["id"])),
    "scope": row["scope"],
    "scope_key": row["scope_key"],
    "title": row["title"],
    "rule_text": row["rule_text"],
    "rationale": row["rationale"],
    "status": row["status"],
    "source_review_run_id": row["source_review_run_id"],
    "source_finding_id": row["source_finding_id"],
    "created_at": row["created_at"],
    "updated_at": row["updated_at"],
  }


def emit(payload: dict[str, object], output_format: str) -> None:
  if output_format == "json":
    print(json.dumps(payload, indent=2, sort_keys=True))
    return

  for key, value in payload.items():
    if value is None:
      continue
    if isinstance(value, (list, dict)):
      print(f"{key}:")
      print(json.dumps(value, indent=2, sort_keys=True))
      continue
    print(f"{key}: {value}")


def print_numbered_findings(review_run_id: str, numbered_findings: list[dict[str, object]]) -> None:
  print(f"review_run_id: {review_run_id}")
  for finding in numbered_findings:
    print(
      f"{finding['number']}. [{finding['finding_id']}] "
      f"{finding['severity']} | {finding['confidence']} | "
      f"{finding['location']} | {finding['description']}"
    )


def print_triage_result(review_run_id: str, decisions: list[TriageDecision]) -> None:
  print(f"review_run_id: {review_run_id}")
  for decision in decisions:
    line = f"{decision.number}. {decision.finding_id} -> {decision.event_type}"
    if decision.note:
      line += f" | note: {decision.note}"
    print(line)


def print_learnings(entries: list[dict[str, object]]) -> None:
  if not entries:
    print("No learnings found.")
    return

  for entry in entries:
    scope_label = entry["scope"]
    scope_key = entry["scope_key"]
    if scope_key:
      scope_label = f"{scope_label}:{scope_key}"
    print(f"{entry['reference']}. [{entry['status']}] {scope_label} | {entry['title']}")


def summarize_applied_learnings(entries: list[dict[str, object]]) -> str:
  if not entries:
    return "none"
  return ", ".join(str(entry["reference"]) for entry in entries)


def print_resolved_learnings(
  *,
  repo_scope_key: str | None,
  skill_name: str | None,
  entries: list[dict[str, object]],
) -> None:
  print(f"scope_precedence: {' > '.join(LEARNING_SCOPE_PRECEDENCE)}")
  if repo_scope_key is not None:
    print(f"repo_scope_key: {repo_scope_key}")
  if skill_name is not None:
    print(f"skill_name: {skill_name}")
  print(f"applied_learnings: {summarize_applied_learnings(entries)}")
  if not entries:
    print("No active learnings matched this review context.")
    return

  for entry in entries:
    scope_label = entry["scope"]
    scope_key = entry["scope_key"]
    if scope_key:
      scope_label = f"{scope_label}:{scope_key}"
    print(f"- [{entry['reference']}] {scope_label} | {entry['title']} | {entry['rule_text']}")


def import_review_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  text, source_path = read_input(args.input)
  review = parse_review(text)
  connection = ensure_database(db_path)
  try:
    save_imported_review(connection, review, source_path=source_path)
  finally:
    connection.close()

  emit(
    {
      "db_path": str(db_path),
      "review_run_id": review.review_run_id,
      "finding_count": len(review.findings),
      "routed_skill": review.routed_skill,
      "detected_scope": review.detected_scope,
      "detected_stack": review.detected_stack,
      "execution_mode": review.execution_mode,
    },
    args.format,
  )
  return 0


def record_feedback_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    record_feedback(
      connection,
      review_run_id=args.run_id,
      finding_ids=args.finding,
      event_type=args.event,
      note=args.note,
    )
  finally:
    connection.close()

  emit(
    {
      "db_path": str(db_path),
      "review_run_id": args.run_id,
      "event_type": args.event,
      "recorded_findings": len(args.finding),
    },
    args.format,
  )
  return 0


def triage_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    numbered_findings = fetch_numbered_findings(connection, args.run_id)
    if args.list or not args.decision:
      if args.format == "json":
        emit(
          {
            "db_path": str(db_path),
            "review_run_id": args.run_id,
            "findings": numbered_findings,
          },
          args.format,
        )
      else:
        print_numbered_findings(args.run_id, numbered_findings)
      return 0

    decisions = parse_triage_decisions(args.decision, numbered_findings)
    for decision in decisions:
      record_feedback(
        connection,
        review_run_id=args.run_id,
        finding_ids=[decision.finding_id],
        event_type=decision.event_type,
        note=decision.note,
      )
  finally:
    connection.close()

  if args.format == "json":
    emit(
      {
        "db_path": str(db_path),
        "review_run_id": args.run_id,
        "recorded": [
          {
            "number": decision.number,
            "finding_id": decision.finding_id,
            "event_type": decision.event_type,
            "note": decision.note,
          }
          for decision in decisions
        ],
      },
      args.format,
    )
  else:
    print_triage_result(args.run_id, decisions)
  return 0


def stats_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    payload = stats_payload(connection, args.run_id)
  finally:
    connection.close()

  payload["db_path"] = str(db_path)
  emit(payload, args.format)
  return 0


def learnings_add_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    learning_id = add_learning(
      connection,
      scope=args.scope,
      scope_key=args.scope_key,
      title=args.title,
      rule_text=args.rule,
      rationale=args.reason,
      source_review_run_id=args.from_run,
      source_finding_id=args.from_finding,
    )
    payload = learning_payload(get_learning(connection, learning_id))
  finally:
    connection.close()

  payload["db_path"] = str(db_path)
  emit(payload, args.format)
  return 0


def learnings_list_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    payload_entries = [learning_payload(row) for row in list_learnings(connection, status=args.status)]
  finally:
    connection.close()

  if args.format == "json":
    emit({"db_path": str(db_path), "learnings": payload_entries}, args.format)
  else:
    print_learnings(payload_entries)
  return 0


def learnings_show_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    payload = learning_payload(get_learning(connection, args.id))
  finally:
    connection.close()

  payload["db_path"] = str(db_path)
  emit(payload, args.format)
  return 0


def learnings_resolve_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    repo_scope_key, skill_name, rows = resolve_learnings(
      connection,
      repo_scope_key=args.repo,
      skill_name=args.skill,
    )
    payload_entries = [learning_payload(row) for row in rows]
  finally:
    connection.close()

  payload = {
    "db_path": str(db_path),
    "repo_scope_key": repo_scope_key,
    "skill_name": skill_name,
    "scope_precedence": list(LEARNING_SCOPE_PRECEDENCE),
    "applied_learnings": summarize_applied_learnings(payload_entries),
    "learnings": payload_entries,
  }
  if args.format == "json":
    emit(payload, args.format)
  else:
    print_resolved_learnings(
      repo_scope_key=repo_scope_key,
      skill_name=skill_name,
      entries=payload_entries,
    )
  return 0


def learnings_edit_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  if all(
    value is None
    for value in (args.scope, args.scope_key, args.title, args.rule, args.reason)
  ):
    raise ValueError("Learning edit requires at least one field to update.")

  connection = ensure_database(db_path)
  try:
    payload = learning_payload(
      edit_learning(
        connection,
        learning_id=args.id,
        scope=args.scope,
        scope_key=args.scope_key,
        title=args.title,
        rule_text=args.rule,
        rationale=args.reason,
      )
    )
  finally:
    connection.close()

  payload["db_path"] = str(db_path)
  emit(payload, args.format)
  return 0


def learnings_status_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    payload = learning_payload(
      set_learning_status(connection, learning_id=args.id, status=args.status_value)
    )
  finally:
    connection.close()

  payload["db_path"] = str(db_path)
  emit(payload, args.format)
  return 0


def learnings_delete_command(args: argparse.Namespace) -> int:
  db_path = resolve_db_path(args.db)
  connection = ensure_database(db_path)
  try:
    delete_learning(connection, args.id)
  finally:
    connection.close()

  emit(
    {
      "db_path": str(db_path),
      "deleted_learning_id": args.id,
    },
    args.format,
  )
  return 0


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description="Import Skill Bill review output, triage numbered findings, and manage local review learnings."
  )
  parser.add_argument(
    "--db",
    help=f"Optional SQLite path. Defaults to ${DB_ENVIRONMENT_KEY} or {DEFAULT_DB_PATH}.",
  )
  subparsers = parser.add_subparsers(dest="command", required=True)

  import_parser = subparsers.add_parser(
    "import-review",
    help="Import a review output file or stdin into the local SQLite store.",
  )
  import_parser.add_argument("input", nargs="?", default="-", help="Path to review text, or '-' for stdin.")
  import_parser.add_argument("--format", choices=("text", "json"), default="text")
  import_parser.set_defaults(handler=import_review_command)

  feedback_parser = subparsers.add_parser(
    "record-feedback",
    help="Record explicit feedback events for one or more findings in an imported review run.",
  )
  feedback_parser.add_argument("--run-id", required=True, help="Imported review run id.")
  feedback_parser.add_argument(
    "--event",
    choices=EVENT_TYPES,
    required=True,
    help="Feedback event type to record.",
  )
  feedback_parser.add_argument(
    "--finding",
    action="append",
    required=True,
    help="Finding id to update. Repeat the flag to record multiple findings.",
  )
  feedback_parser.add_argument("--note", default="", help="Optional note for the recorded feedback event.")
  feedback_parser.add_argument("--format", choices=("text", "json"), default="text")
  feedback_parser.set_defaults(handler=record_feedback_command)

  triage_parser = subparsers.add_parser(
    "triage",
    help="Show numbered findings for a review run and record triage decisions by number.",
  )
  triage_parser.add_argument("--run-id", required=True, help="Imported review run id.")
  triage_parser.add_argument(
    "--decision",
    action="append",
    help="Triage entry like '1 fix', '2 skip - intentional', or '3 accept - good catch'.",
  )
  triage_parser.add_argument("--list", action="store_true", help="Show the numbered findings without recording decisions.")
  triage_parser.add_argument("--format", choices=("text", "json"), default="text")
  triage_parser.set_defaults(handler=triage_command)

  stats_parser = subparsers.add_parser(
    "stats",
    help="Show aggregate or per-run review acceptance metrics from the local SQLite store.",
  )
  stats_parser.add_argument("--run-id", help="Optional review run id to scope stats to one review.")
  stats_parser.add_argument("--format", choices=("text", "json"), default="text")
  stats_parser.set_defaults(handler=stats_command)

  learnings_parser = subparsers.add_parser(
    "learnings",
    help="List and manage local review learnings derived from explicit review feedback.",
  )
  learnings_subparsers = learnings_parser.add_subparsers(dest="learnings_command", required=True)

  learnings_add_parser = learnings_subparsers.add_parser("add", help="Create a new local learning entry.")
  learnings_add_parser.add_argument("--scope", choices=LEARNING_SCOPES, default="global")
  learnings_add_parser.add_argument("--scope-key", default="")
  learnings_add_parser.add_argument("--title", required=True)
  learnings_add_parser.add_argument("--rule", required=True)
  learnings_add_parser.add_argument("--reason", default="")
  learnings_add_parser.add_argument("--from-run")
  learnings_add_parser.add_argument("--from-finding")
  learnings_add_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_add_parser.set_defaults(handler=learnings_add_command)

  learnings_list_parser = learnings_subparsers.add_parser("list", help="List local learning entries.")
  learnings_list_parser.add_argument("--status", choices=("all",) + LEARNING_STATUSES, default="all")
  learnings_list_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_list_parser.set_defaults(handler=learnings_list_command)

  learnings_show_parser = learnings_subparsers.add_parser("show", help="Show a single learning entry.")
  learnings_show_parser.add_argument("--id", type=int, required=True)
  learnings_show_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_show_parser.set_defaults(handler=learnings_show_command)

  learnings_resolve_parser = learnings_subparsers.add_parser(
    "resolve",
    help="Resolve active learnings for a review context using global, repo, and skill scope.",
  )
  learnings_resolve_parser.add_argument("--repo", help="Optional repo scope key to match repo-scoped learnings.")
  learnings_resolve_parser.add_argument("--skill", help="Optional review skill name to match skill-scoped learnings.")
  learnings_resolve_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_resolve_parser.set_defaults(handler=learnings_resolve_command)

  learnings_edit_parser = learnings_subparsers.add_parser("edit", help="Edit a local learning entry.")
  learnings_edit_parser.add_argument("--id", type=int, required=True)
  learnings_edit_parser.add_argument("--scope", choices=LEARNING_SCOPES)
  learnings_edit_parser.add_argument("--scope-key")
  learnings_edit_parser.add_argument("--title")
  learnings_edit_parser.add_argument("--rule")
  learnings_edit_parser.add_argument("--reason")
  learnings_edit_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_edit_parser.set_defaults(handler=learnings_edit_command)

  learnings_disable_parser = learnings_subparsers.add_parser("disable", help="Disable a learning entry.")
  learnings_disable_parser.add_argument("--id", type=int, required=True)
  learnings_disable_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_disable_parser.set_defaults(handler=learnings_status_command, status_value="disabled")

  learnings_enable_parser = learnings_subparsers.add_parser("enable", help="Enable a disabled learning entry.")
  learnings_enable_parser.add_argument("--id", type=int, required=True)
  learnings_enable_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_enable_parser.set_defaults(handler=learnings_status_command, status_value="active")

  learnings_delete_parser = learnings_subparsers.add_parser("delete", help="Delete a learning entry.")
  learnings_delete_parser.add_argument("--id", type=int, required=True)
  learnings_delete_parser.add_argument("--format", choices=("text", "json"), default="text")
  learnings_delete_parser.set_defaults(handler=learnings_delete_command)

  return parser


def main(argv: list[str] | None = None) -> int:
  parser = build_parser()
  args = parser.parse_args(argv)

  try:
    return int(args.handler(args))
  except ValueError as error:
    print(str(error), file=sys.stderr)
    return 1


if __name__ == "__main__":
  raise SystemExit(main())
