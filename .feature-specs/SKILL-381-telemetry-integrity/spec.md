# SKILL-381 - Telemetry integrity

## Mode

decomposed

## Intended outcome

PostHog holds one row per real event from real installs, and the numbers the relay reports
match what happened.

A 2026-09-24 audit of the production project ([audit.md](audit.md)) found the data does not
meet that bar today:

- Test runs upload fixtures to production. `test-install-id` has sent 11,919 events since
  April, and it produced 94% of `skillbill_review_finished` and quality-check rows.
- One batch was stored about 32,630 times on 2026-08-15. Current code still resends
  unconfirmed batches with no limit.
- The client's deduplication key, `$insert_id`, is ignored by PostHog. Retried rows land
  twice today.
- The deployed relay is an older build than the repo: contract 1, no `$exception` rewrite.
- Verify stats drop `completion_status: stale` (27 of 80 finished runs), and stale durations
  of up to 117 days inflate the average to 3.2 days.
- Nothing governs outbox event names. The privacy doc claims a validator guard that does not
  exist, and two emitted events are undocumented.

After this bundle:

- No test, on any machine, can reach the hosted relay, and the relay drops the reserved test
  identity on its own.
- Every delivery carries PostHog's native event identity, so a resend overwrites the earlier
  row instead of adding one. Unconfirmed batches stop after a bounded number of attempts.
- The deployed relay is built from the repo, and a capabilities check detects drift.
- Verify stats count every emitted status, and no measurement reports an unobserved
  duration as a number.
- Outbox event names come from one closed registry, and the privacy doc covers every one.

## Acceptance Criteria

1. Subtask 1 acceptance criteria hold: runtime delivery integrity, test isolation, event
   registry, and verify-duration truthfulness.
2. Subtask 2 acceptance criteria hold: relay identity passthrough, test-identity drop,
   exception mapping, stats vocabulary, stats auth, and drift detection.
3. `docs/telemetry-privacy.md` describes the shipped deduplication key, the unconfirmed-retry
   budget, the relay test-identity drop, and every registered outbox event, and no longer
   claims `$insert_id` deduplicates or that a validator rejects retired outbox names.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md#design-principles` and `docs/code-principles.md`.
  New wire keys go in `TelemetryProxyPayloadKeys`; event names go in the registry this bundle
  adds, never restated as literals.
- `docs/observability-policy.md`: every swallowed or degraded path emits a record.
- Existing outbox rows keep their `event_uuid`. The identity change reuses that column; it
  mints nothing new for already-queued rows.
- Relay changes stay backward compatible with installed clients: older clients that send no
  top-level `uuid` still get a success response.
- Availability semantics follow `TelemetryMeasurementAvailability`: an unmeasured value is
  null with a non-`measured` availability, never `0`.

## Non-goals

- Reducing projection-measurement volume in steady state (~1,300 rows per active install per
  day). The 848k spike was resends (F2), not emission. Revisit after resends stop.
- Hashing `workflow_id` on projection, shared-evidence, and diagnostic events. Current ids
  carry no issue key; the privacy doc keeps stating they are raw.
- Changing what `full` level collects, including path-shaped `feature_name`.
- Automated retention in the relay.
- A `feature-task-runtime` remote stats endpoint. The client already refuses it with a
  capability error.

## Operator runbook (not code; run once after both subtasks ship)

1. Deploy the relay from `docs/cloudflare-telemetry-proxy/` with `POSTHOG_HOST` /
   `POSTHOG_APP_HOST` set to EU cloud. Confirm `/capabilities` reports the repo
   `CONTRACT_VERSION`.
2. Set `PROXY_STATS_BEARER_TOKEN` in the Worker secret store.
3. In PostHog (project 152739), delete or exclude:
   - `distinct_id = 'test-install-id'`
   - `event = 'skillbill_debug_ping'`
   - the 2026-08-15/16 duplicate storm from install `ddd1bdbc…`, keeping one row per
     identical `(event, workflow_id, timestamp, consumer_phase_id, projection_contract_id)`
   - pre-attribution rows carrying a raw `issue_key` from installs with no full-level field
     (audit F6)

   Where row deletion is unavailable, add a project-level filter so insights exclude them.
4. Turn off GeoIP enrichment for the project, or have the relay send `$geoip_disable: true`
   (subtask 2 does the latter).

## Subtasks

1. [Runtime delivery integrity](spec_subtask_1_runtime-delivery-integrity.md): identity,
   bounded retries, test isolation, event registry, duration truthfulness, swallowed-sync
   record.
2. [Relay and stats](spec_subtask_2_relay-and-stats.md): relay passthrough, test drop,
   exception mapping, stats vocabulary and auth, drift detection, docs.

Split condition: the relay deploys to Cloudflare separately from the runtime release. Each
commit is usable on its own.

## Next path

```bash
skill-bill goal SKILL-381
```
