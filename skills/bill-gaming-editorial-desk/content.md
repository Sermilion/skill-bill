# Gaming Editorial Desk Content

## Step 1: Collect Editorial Profile

Ask only for editorial constraints needed for today's assignment desk: beat focus, target audience, preferred article types, excluded topics, deadline, region/timezone, and source standards. If the user provides a Readian account detail, tell them that authentication belongs inside the Readian MCP boundary and do not store it.

The `editorial_profile` artifact must include:

- beat_focus
- audience
- article_type_preferences
- exclusions
- deadline_context
- source_standard
- region_or_timezone

## Step 2: Fetch Feed Candidates

Call `readian_auth_status` before any feed tool. If it returns `auth_required`, stop the workflow and report the auth pause. Do not ask for credentials or use direct HTTP requests.

When authenticated, call `readian_get_today_feed` first. Use `readian_get_recommendations` only when the first feed is too thin, too broad, or the profile asks for a narrower recommendation pass.

The `raw_feed_digest` artifact must summarize source item ids, titles, source names, URLs when available, timestamps, and any Readian ranking metadata. Redact secret-looking keys or values before writing the artifact.

## Step 3: Cluster Stories

Cluster by story event, not by identical headline. Keep separate items visible when they disagree, add context, or come from materially different source types.

The `story_clusters` artifact must include:

- candidate_id
- cluster_title
- included_readian_items
- duplicate_or_related_items
- unresolved_merge_assumptions
- earliest_seen_at

## Step 4: Rank Candidates

Use the Candidate Ranking Output Contract from `reference.md`. Score each rubric field on a 1-5 scale and include prose rationale for every score.

Required ranking fields:

- newsworthiness
- timeliness
- source_confidence
- audience_fit
- angle_strength
- coverage_gap
- social_signal
- effort
- risk

The `ranked_candidates` artifact must include the full score breakdown, total score, ranking rationale, recommended angle, article type recommendation, estimated effort, and suggested next action.

## Step 5: Verify Sources

Use the Source Verification Output Contract from `reference.md`. Prefer primary sources such as publisher posts, developer blogs, patch notes, platform store pages, regulatory filings, official videos, and direct interviews.

Every meaningful claim must be classified as one of:

- confirmed_fact
- reputable_reporting
- community_claim
- rumor
- leak
- speculation

The `source_verification_report` artifact must show unsupported_claims, missing_primary_sources, contradictions, changed_or_withdrawn_claims, source_urls, access_dates, and confidence notes. A candidate with no usable source trail must fail loudly in the report.

## Step 6: Social Signal Check

Use public, accessible social/community sources only. Do not scrape private or inaccessible content. Social signal is audience context, not proof.

The `social_signal_report` artifact must separate:

- sentiment
- evidence
- breadth_caveats
- confidence_caveats
- notable_reactions
- brigading_or_harassment_risk

Do not present isolated posts as broad consensus. Label sample-size and source-breadth limits directly.

## Step 7: Ethics Risk Check

Use the Ethics Risk Output Contract from `reference.md`.

Each candidate must receive one risk status:

- blocked
- warning
- clear

Check embargo constraints, review-code disclosure, sponsored material, affiliate implications, conflicts of interest, rumor/leak handling, attribution quality, AI-assistance disclosure requirements, harassment-prone framing, and whether the headline angle could overstate verified facts.

The `ethics_risk_report` artifact must include blockers, warnings, notes, required mitigations, and the reason each candidate is blocked, warning, or clear.

## Step 8: Present Candidate Board

Build the `candidate_board` only after ranking, source verification, social signal, and ethics/risk checks are complete for the shortlisted candidates.

The board must include:

- candidate_id
- topic_or_title
- short_summary
- why_it_matters
- recommended_angle
- article_type_recommendation
- score_breakdown
- source_confidence
- unsupported_claims
- missing_primary_sources
- risk_status
- risk_notes
- primary_sources
- secondary_or_context_sources
- social_signal_summary
- social_signal_caveats
- estimated_effort
- suggested_next_action

After the board, pause and ask the journalist which candidate or candidates to pursue. Do not continue to `build_selected_story_pack` until the user chooses.

## Step 9: Build Selected Story Pack

For each selected candidate, produce `selected_story_pack` with:

- working_headline_options
- recommended_article_angle
- verified_fact_table
- source_links_grouped_by_primary_secondary_context
- key_points
- unanswered_questions
- suggested_structure
- copyright_safe_source_snippets
- risk_ethics_notes
- suggested_tags_categories
- optional_seo_social_packaging

Keep the story pack evidence-first. It may include outline structure and key points, but it must not become a full article draft.

## Step 10: Finish

Report what was produced, which candidates remain blocked or uncertain, which unsupported claims still need work, and any Readian status changes made through MCP tools.
