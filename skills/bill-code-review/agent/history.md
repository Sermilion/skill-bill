# bill-code-review — Feature History

## [2026-06-06] SKILL-70-ad-hoc-parallel-review-agent
Areas: skills/bill-code-review, platform-packs/kotlin/code-review/bill-kotlin-code-review, platform-packs/kmp/code-review/bill-kmp-code-review, platform-packs/php/code-review/bill-php-code-review
- Added `parallel_review:<agent_id>` modifier: bill-code-review detects it, validates it, and forwards it to the resolved stack-specific skill
- Each stack skill (Kotlin, KMP, PHP) runs two independent review lanes (default + alternative agent) against the same diff and specialist briefing
- Findings from both lanes merged into one F-XXX [agent_id] risk register with per-finding provenance tags (reusable pattern)
- Agent ID validation is guard-first: blank, unknown, or duplicate IDs are rejected before any pass launches, with an error listing supported agents
- Supported agent IDs at time of writing: copilot, claude, codex, opencode, junie
- No specialist selection logic changed; both lanes use the same selected specialists
- No concrete provider dependency added to any skill content file (grep-verified)
Feature flag: N/A (modifier-gated: `parallel_review:<agent_id>`)
Acceptance criteria: 9/9 implemented
