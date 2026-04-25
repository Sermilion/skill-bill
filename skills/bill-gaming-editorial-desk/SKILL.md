---
name: bill-gaming-editorial-desk
description: Use when running a governed daily editorial assignment desk for gaming journalism from Readian recommendations through candidate selection and source-backed story packs.
---

# Gaming Editorial Desk

## Project Overrides

Follow the shell ceremony in [shell-ceremony.md](shell-ceremony.md).

If `.agents/skill-overrides.md` exists in the project root and contains a matching section, read that section and apply it as the highest-priority instruction for this skill.

## Execution

Follow the detailed step instructions in [content.md](content.md).

This skill is a skill-only editorial workflow MVP. It uses stable step ids and stable artifact names so a later durable workflow runtime can adopt the same contract, but it does not open or update durable workflow state in this version.

Read [reference.md](reference.md) for the source verification, candidate ranking, social signal, ethics/risk, and story pack output contracts.

## Readian MCP Boundary

Use Readian only through MCP tools:

- `readian_auth_status`
- `readian_get_today_feed`
- `readian_get_recommendations`
- `readian_get_article`
- `readian_save_candidate`
- `readian_mark_story_status`

Do not ask the user for Readian credentials, tokens, cookies, auth headers, refresh tokens, session ids, or browser storage. If any Readian MCP tool returns `auth_required`, pause and tell the user that Readian authentication must be completed in the Readian MCP boundary before this workflow can continue.

Never copy refresh/session material into responses, logs, artifacts, telemetry notes, or story packs. If source material includes secret-looking fields, redact them before writing any artifact.

## Stable Step Ids

1. `collect_editorial_profile`
2. `fetch_feed_candidates`
3. `cluster_stories`
4. `rank_candidates`
5. `verify_sources`
6. `social_signal_check`
7. `ethics_risk_check`
8. `present_candidate_board`
9. `build_selected_story_pack`
10. `finish`

## Stable Artifact Names

- `editorial_profile`
- `raw_feed_digest`
- `story_clusters`
- `ranked_candidates`
- `source_verification_report`
- `social_signal_report`
- `ethics_risk_report`
- `candidate_board`
- `selected_story_pack`

## Step 1: Collect Editorial Profile

Step id: `collect_editorial_profile`

Primary artifact: `editorial_profile`

Capture the journalist's beat constraints, target audience, region/timezone, preferred article types, excluded topics, source standards, and deadline. Keep the artifact concise and avoid storing private credentials or account material.

## Step 2: Fetch Feed Candidates

Step id: `fetch_feed_candidates`

Primary artifact: `raw_feed_digest`

Call `readian_auth_status` first. If authentication is available, call `readian_get_today_feed` and, when useful, `readian_get_recommendations`. If Readian returns `auth_required`, pause instead of falling back to direct API access or credential handling.

## Step 3: Cluster Stories

Step id: `cluster_stories`

Primary artifact: `story_clusters`

Cluster related Readian items into story candidates. Keep duplicate items, source variants, and unresolved merge assumptions visible so later verification can inspect the source trail.

## Step 4: Rank Candidates

Step id: `rank_candidates`

Primary artifact: `ranked_candidates`

Rank candidates using the Candidate Ranking Output Contract in [reference.md](reference.md). Every candidate must include the full rubric: newsworthiness, timeliness, source confidence, audience fit, angle strength, coverage gap, social signal, effort, and risk.

## Step 5: Verify Sources

Step id: `verify_sources`

Primary artifact: `source_verification_report`

Verify the shortlisted candidates using the Source Verification Output Contract in [reference.md](reference.md). Unsupported claims and missing primary sources must remain visible. Do not convert rumor, leaks, speculation, community claims, or reputable reporting into confirmed facts.

## Step 6: Social Signal Check

Step id: `social_signal_check`

Primary artifact: `social_signal_report`

Assess public/community signal only as editorial context. Separate sentiment from evidence, include breadth and confidence caveats, and avoid treating isolated posts as consensus or fact.

## Step 7: Ethics Risk Check

Step id: `ethics_risk_check`

Primary artifact: `ethics_risk_report`

Review blockers, warnings, and clear candidates using the Ethics Risk Output Contract in [reference.md](reference.md). A blocked candidate may appear on the board, but it must not be treated as ready for a story pack until the blocker is resolved.

## Step 8: Present Candidate Board

Step id: `present_candidate_board`

Primary artifact: `candidate_board`

Build the candidate board from the ranked, verified, social-signal, and ethics/risk artifacts. Include why each candidate matters, the recommended angle, source confidence, unsupported claims, missing primary sources, social caveats, risk status, estimated effort, and suggested next action.

## Candidate Selection Pause

Pause after presenting `candidate_board`. Ask the journalist to select one or more candidates, reject candidates, or request another feed pass. Do not build a story pack until the journalist explicitly chooses a candidate.

## Step 9: Build Selected Story Pack

Step id: `build_selected_story_pack`

Primary artifact: `selected_story_pack`

For each selected candidate, build a source-backed story pack using the Selected Story Pack Output Contract in [reference.md](reference.md). Include verified facts, source links, key points, unanswered questions, risk notes, and suggested structure.

## Story Pack Boundary

The selected story pack is preparation material, not a full article draft. Do not write a complete article, lede-to-close draft, review verdict, headline package as final copy, or publish-ready story unless a later workflow explicitly asks for drafting.

## Step 10: Finish

Step id: `finish`

Primary artifact: none

Summarize the selected candidates, produced story packs, unresolved verification gaps, blocked/warning risk decisions, and any Readian follow-up actions such as `readian_save_candidate` or `readian_mark_story_status`.
