## [2026-09-13] Issue 341 — Validate evidence integrity
Areas: orchestration/contracts, runtime-kotlin/{runtime-domain,runtime-engine,runtime-infra-fs,runtime-infra-sqlite}
- Validation settlement now consumes schema-backed command and exit-code evidence, requires the required command to exit `0`, and keeps red or missing evidence incomplete across persistence and resume.
- The existing repair path receives remaining validation findings after a red result; repeated unchanged findings and exhausted repair budget remain durable blockers.
- Goal status projects valid command/exit evidence and reports an integrity problem when completed evidence is absent or invalid.
- Pattern: keep one minimal validation-evidence projection across contract, domain, settlement, persistence, and status seams; provider metadata remains opaque. reusable
- Limitation: platform validation routing and review, build, and planning evidence contracts are unchanged.
Feature flag: N/A
Acceptance criteria: 11/11 implemented

## [2026-09-11] SKILL-233 subtask 8 — Engine module and package roots
Areas: runtime-kotlin/{runtime-engine,runtime-application,runtime-infra-fs,runtime-infra-sqlite,runtime-contracts,runtime-core,runtime-cli,runtime-mcp,runtime-domain,runtime-ports,agent,ARCHITECTURE.md}
- Extracted feature-task, goal-runner, goal-planning, and planning-projection runtime behavior into the `runtime-engine` module under one `skillbill.engine` root, with a pinned inbound API and no reverse application dependency. reusable
- Moved infrastructure and application packages to their owning roots, using explicit schema-path constants at validator seams so resource lookup remains stable after package relocation. reusable
- Pattern: record module edges and package ownership in one catalog plus architecture tests, and keep empty per-module baselines as proof that new root violations are rejected rather than suppressed. reusable
- Limitation: the extraction and package moves preserve behavior and signatures; further engine subdivision remains outside this change.
Feature flag: N/A
Acceptance criteria: 12/12 implemented
