# SKILL-381 audit evidence

PostHog project "SkillBill" (id 152739, EU cloud), queried 2026-09-24 over the
full retained history (first event 2026-04-03). Every number below comes from a
HogQL query against `events` unless marked as a code reference.

## Event inventory (365 days)

| Event | Rows | Installs | Notes |
|---|---|---|---|
| `skillbill_feature_task_runtime_projection_measurement` | 900,492 | 8 | 85% of all rows; 848k from one install on 2026-08-15/16 |
| `skillbill_goal_started` | 67,156 | 33 | 65,269 from one install on 2026-08-15 |
| `skillbill_feature_task_runtime_shared_evidence` | 34,246 | 8 | |
| `skillbill_feature_task_runtime_started` | 33,509 | 42 | 32,635 from one install on 2026-08-15 |
| `skillbill_quality_check_finished` | 4,831 | 34 | 93.7% test traffic |
| `skillbill_review_finished` | 3,729 | 41 | 94.7% test traffic |
| `skillbill_quality_check_started` | 2,596 | 36 | 88.5% test traffic |
| `skillbill_goal_finished` | 1,522 | 33 | |
| `skillbill_feature_task_runtime_rejection` | 1,090 | 7 | not in `../../../docs/telemetry-privacy.md` |
| `skillbill_feature_task_runtime_finished` | 887 | 40 | |
| `skillbill_review_stage_degradation` | 501 | 6 | |
| `skillbill_feature_verify_started` / `_finished` | 156 / 119 | 34 / 21 | |
| `skillbill_runtime_exception` | 34 | 5 | last seen 2026-08-14; never arrived as `$exception` |
| `skillbill_audit_repair_transition` | 19 | 1 | no emitter on `main`; not documented |
| `skillbill_debug_ping` | 1 | 1 | manual probe left in production data |
| Retired: `skillbill_feature_implement_*`, `skillbill_feature_task_prose_*`, `skillbill_review_run_summary`, `skillbill_learning_*` | ~2,800 | | still ingested; relay accepts them by design |

No `$exception` event exists in the project.

## F1. Test runs upload to production

- `distinct_id = 'test-install-id'`: 11,919 events, 2026-04-05 through 2026-09-24 (today).
- `session_id = 'qck-blank-routing'`: 2,250 `skillbill_quality_check_finished` rows with no
  matching start. The id is a literal in
  `runtime-mcp/src/test/kotlin/skillbill/mcp/McpQualityCheckTelemetryNormalizationTest.kt:119`.
- Mechanism (code): 15 test files write a config with `"install_id": "test-install-id"`,
  `"level": "anonymous"`, `"proxy_url": ""`. `telemetryProxyUrl("")`
  (`runtime-domain/.../telemetry/TelemetryProxyUrl.kt`) resolves a blank URL to
  `DEFAULT_TELEMETRY_PROXY_URL`, and `McpRuntime` / `McpRuntimeLifecycle` call
  `telemetryService.autoSync()` after tool calls. Every `./gradlew check` on a networked
  machine uploads its fixtures to the hosted relay.
- The relay's `/stats` excludes `test-install-id` (`PRODUCTION_INSTALL_FILTER`). Raw
  PostHog insights, dashboards, and any HogQL do not.

## F2. Retry storm: one batch stored ~32,630 times

- Install `ddd1bdbc…` (0.2.2-SNAPSHOT), 2026-08-15: two `skillbill_goal_started` rows,
  each with one `workflow_id` and one enqueue timestamp, stored 32,630 times each.
  `skillbill_feature_task_runtime_started` 32,635 and each projection contract 32,630–32,631
  in the same window. Same row, resent.
- Current code resends unconfirmed batches without limit, by design (it relies on the
  receiver to deduplicate, which F3 shows fails): `attemptDelivery` maps a thrown send or a
  relay 502/timeout to `TelemetryDeliveryOutcome.UNKNOWN`; `settleFailedDelivery` calls
  `markUnconfirmed`, which is `recordFailure(..., consumesAttempt = false)`
  (`TelemetryOutboxStore.kt:222`). `TELEMETRY_DELIVERY_ATTEMPT_BUDGET = 5` never applies
  to unknown outcomes.

## F3. The deduplication key is one PostHog does not use

- The client sends `$insert_id` (`TelemetryProxyPayloadKeys.EVENT_DEDUPLICATION_ID`), and
  the relay reports `supports_event_deduplication: true`.
- Since 2026-09-01, rows sharing one `$insert_id` are stored more than once:
  projection 3,836 rows / 3,709 distinct ids; shared_evidence 97 / 92; goal_started 150 / 147;
  goal_subtask_finished 129 / 122; goal_issue_finished 65 / 64. PostHog keys event identity
  on the top-level event `uuid`, which the client does not send.
- `../../../docs/telemetry-privacy.md` and the relay comment both state that retries deduplicate.
  They do not.

## F4. Deployed relay differs from the repo relay

- Live `/capabilities`: `contract_version "1"`,
  `supported_workflows ["bill-feature-verify","bill-feature-implement"]`.
  Repo `worker.js`: `CONTRACT_VERSION = "2"`, verify only.
- Repo `transformBatch` rewrites `skillbill_runtime_exception` to `$exception`. All 34
  exceptions arrived untransformed, so the deployed worker predates that code.
- Repo `transformBatch` sends `stacktrace: { frames: [] }` and drops the redacted
  `stack_trace` property from the exception list, so even the repo version loses frames.
- `/stats` is unauthenticated (`stats_auth_required: false`) and forwards the raw PostHog
  error body as `details` to any caller.
- The committed `wrangler.toml` targets EU cloud, matching the project. The code defaults
  (`DEFAULT_POSTHOG_*_HOST`) and `wrangler.toml.example` target US cloud, so a deploy that
  drops the `[vars]` block sends to the wrong region.
- `telemetry_remote_stats` accepts workflow `feature-task-runtime`; the relay does not serve
  it, and the client refuses with a clear capability error. Not a defect.

## F5. Verify stats: vocabulary and duration holes

- `skillbill_feature_verify_finished.completion_status` production values: `completed` 54,
  `stale` 27. The relay counts `completed`, `abandoned_at_review`, `abandoned_at_audit`,
  `error`; `stale` lands in no bucket, so status counts cover 54 of 80 finished runs and
  `abandonment_rate` has read 0 for four months.
- Stale rows carry `duration_seconds` up to 10,114,928 (117 days); mean 826,765. They drive
  the reported `average_duration_seconds` of 276,826 (3.2 days). Completed runs average
  1,242–3,453 seconds.
- `featureVerifyFinishedPayload` writes `durationSeconds(row)`, which returns `0` when a
  timestamp is missing. Quality-check already solved this with
  `final_failure_count_availability` and `completion`.
- Week of 2026-07-06: 19 finished vs 13 started. The stale reconciler closes sessions
  started in earlier weeks; `in_progress_runs` is clamped to 0 with `max(…, 0)`, hiding it.
- `buildVerifySeries` omits empty buckets (2026-07-13 has no entry) instead of
  zero-filling them.

## F6. Tracker keys and branch names in stored data

- Current anonymous clients hash `issue_key` (`iss_<hex>`). Pre-attribution clients (no
  `skill_bill_version`, July 2026) sent raw keys at anonymous, e.g. `RDN-30`, `ACC-100`,
  with no full-level field on the same install. Those rows remain in PostHog.
- The projection, shared-evidence, and diagnostic-degradation events send `workflow_id`
  unhashed at anonymous (documented). Current ids are `wftr-<date>-<rand>`, which carry no
  issue key; the documented example `SKILL-163:...` shows the shape is not guaranteed.
- `feature_name` on `skillbill_feature_task_runtime_started` contains absolute filesystem
  paths on 33,223 rows (full level).

## F7. No governed outbox event registry

- `../../../orchestration/contracts/telemetry-event-schema.yaml` governs MCP tool inputs. Outbox
  event names are string literals at each `enqueueTelemetry` call site.
- `../../../docs/telemetry-privacy.md` states an in-tree emitter of a retired name "loud-fails at the
  validator". No outbox-side check exists.
- Two emitted events (`skillbill_feature_task_runtime_rejection`,
  `skillbill_audit_repair_transition`) have no privacy-doc entry.

## F7b. Found during implementation

- `skillbill_review_finished_legacy_regenerated` and `experiment.completed` are also enqueued and
  undocumented; `experiment.completed` breaks the `skillbill_` naming convention.
- `skillbill_feature_task_runtime_rejection` is queued with no level gate, so the privacy doc's
  "five events queued at `off`" list was short by two.
- `ExperimentTelemetryOutboxRecorder` reads consent from a top-level `telemetry_level` key; real
  configs nest it as `telemetry.level`, so experiment telemetry never emits. Left as is and
  documented; fixing it would start a new upload stream and needs its own decision.
- `pointer_path` on rejection rows holds JSON pointers only (checked in PostHog, August onward).

## F8. Swallowed sync failure (withdrawn)

- `TelemetrySyncRuntime.autoSyncTelemetry` maps an `Exception` to `null`, but
  `TelemetryService.autoSync` turns `null` into a diagnostic warning and a
  `skillbill_runtime_exception` under `telemetry_background_sync`. Not a defect.

## F9. GeoIP is noise

- 1,051,609 rows geolocate to `US`, 104 to `DE`. The IP is the Cloudflare egress, not the
  user. The enrichment is meaningless and contradicts the "no machine property" stance.

## Clean areas

- `$process_person_profile: false` holds; no person profiles.
- No event timestamps in the future. Maximum arrival lag 30 days (outbox backlog), with 21
  rows over 7 days.
- `skill_bill_version` present on all events since release attribution shipped.
