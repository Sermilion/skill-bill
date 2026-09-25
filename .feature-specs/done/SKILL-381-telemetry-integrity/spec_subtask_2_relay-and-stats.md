# SKILL-381 subtask 2 - Relay and stats

## Scope

Fix the relay side of audit findings F1, F3, F4, F5, and F9, and correct
`../../../docs/telemetry-privacy.md` ([audit.md](audit.md)). All code lives in
`docs/cloudflare-telemetry-proxy/`, plus docs.

### Identity passthrough (F3)

`isValidEvent` and `transformBatch` keep a producer's top-level `uuid` unchanged on every
branch, including the `$exception` rewrite. A batch with no `uuid` is still accepted, for
installed clients. `supports_event_deduplication` stays `true`, and the comment beside it
names `uuid` as the key PostHog honours, not `$insert_id`.

### Test identity drop (F1, defense in depth)

The relay drops every event whose `distinct_id` or `properties.install_id` is blank or
`test-install-id` before forwarding, reusing the identity set behind
`PRODUCTION_INSTALL_FILTER`. A batch made only of dropped events returns success without
calling PostHog, so a test client never retries. The response reports the dropped count.

### Exception mapping (F4)

`$exception_list[0].stacktrace.frames` is built from the redacted `stack_trace` property
(one frame per line, capped at the 12 frames the client already sends) instead of an empty
array. An exception with no `stack_trace` keeps an empty frame list.

### GeoIP off (F9)

Every forwarded event carries `$geoip_disable: true`, because the only IP PostHog sees is
the Cloudflare egress.

### Stats vocabulary and truthfulness (F5)

- `completion_status_counts` gains `stale`, and `abandonment_rate` stays abandoned-only.
  The counts across all statuses sum to `finished_runs`; anything outside the known
  vocabulary goes in an `other` bucket, so the sum always closes.
- `average_duration_seconds` averages only rows with `completion_status = 'completed'` and a
  non-null duration. Rows from subtask 1 carry `duration_seconds_availability`; when it is
  present, only `measured` rows count.
- `in_progress_runs` is no longer clamped. Report `finished_without_start_runs` when the
  finished count exceeds the started count.
- `buildVerifySeries` zero-fills every bucket between `date_from` and `date_to`.

### Stats hardening (F4)

- `/stats` error responses no longer include the raw upstream body as `details`.
- `../../../README.md` states that `PROXY_STATS_BEARER_TOKEN` is required for a public deployment.

### Drift detection (F4)

A relay drift check fails when the deployed relay's `/capabilities` `contract_version`
differs from the repo's `CONTRACT_VERSION`. Ship it as a `node` script beside the worker
that runs in the release checklist (`../../../RELEASING.md`), not in unit tests, because it needs the
network. Bump `CONTRACT_VERSION` for this change.

### Privacy doc

`../../../docs/telemetry-privacy.md`:

- The envelope table lists top-level `uuid` as the delivery identity and keeps `$insert_id`
  as a legacy mirror.
- An unconfirmed delivery retries without consuming an attempt, and the top-level `uuid`
  collapses the resends.
- The relay drops the reserved test identity and disables GeoIP.
- Retired names: the runtime cannot emit them because the outbox registry
  (subtask 1) does not define them. Remove the claim that a validator rejects them.

## Acceptance Criteria

1. A worker test shows a batch event with top-level `uuid` reaches the forwarded body with
   the same `uuid`, both for a plain event and for a rewritten `$exception`.
2. A worker test shows a batch mixing `test-install-id`, blank-id, and real events forwards
   only the real events; a batch of only test events returns success and makes no upstream
   call.
3. A worker test shows a `skillbill_runtime_exception` with a three-line `stack_trace`
   forwards `$exception_list[0].stacktrace.frames` of length 3.
4. A worker test shows every forwarded event carries `$geoip_disable: true`.
5. `normalizeVerifyStats` on a row with 54 completed, 27 stale, and 0 other finished runs
   returns `completion_status_counts` summing to `finished_runs`, and an
   `average_duration_seconds` built from completed rows only.
6. `buildVerifySeries` over a three-week range with data in weeks 1 and 3 returns three
   buckets, with week 2 zero-filled.
7. A `/stats` upstream failure response contains no `details` field.
8. The drift-check script exits non-zero when fed a capabilities payload whose
   `contract_version` differs from `CONTRACT_VERSION`, and zero when they match.
9. `../../../docs/telemetry-privacy.md` names top-level `uuid` as the delivery identity, states that
   unconfirmed resends collapse on that `uuid`, states the relay test-identity drop, and contains no claim that
   `$insert_id` deduplicates.

## Non-goals

- Deploying the relay and cleaning PostHog history (operator runbook in [spec.md](spec.md)).
- A feature-task-runtime stats endpoint.
- Rejecting retired event names at the relay; installed clients still get success.

## Dependency notes

Depends on subtask 1 for `duration_seconds_availability` and for the privacy-doc sections
subtask 1 adds. The relay reads the new availability field when present and falls back to
`completion_status` when absent, so it stays correct for older clients.

## Validation Strategy

- `npm test` in `../../../docs/cloudflare-telemetry-proxy`.
- Run the drift script against the live relay after the operator deploys; before deploy it
  is expected to fail (live reports `"1"`).
- The dominant pack gate through `bill-code-check` for the doc change.

## Next path

Operator runbook in [spec.md](spec.md), then `skill-bill goal SKILL-381` reports the bundle
complete.
