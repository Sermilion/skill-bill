# SKILL-381 subtask 1 - Runtime delivery integrity

## Scope

Fix the runtime side of audit findings F1, F2, F3, F5 (duration), F7, and F8
([audit.md](audit.md)).

### Event identity PostHog honours (F3)

`TelemetryProxyPayloadMappers` sends each row's `event_uuid` as the top-level event `uuid`,
the field PostHog uses for event identity. Declare the key in `TelemetryProxyPayloadKeys`.
Keep `$insert_id` on the properties for installed relays and older readers. A row with no
`event_uuid` omits `uuid` and does not mint one.

### Bounded unconfirmed retries (F2)

Once a resend overwrites instead of duplicating, retrying an unknown outcome is safe, and it
still needs a bound. `TelemetryDeliveryOutcome.UNKNOWN` consumes a delivery attempt, like
`REJECTED`, so a batch whose outcome is never confirmed reaches
`TELEMETRY_DELIVERY_ATTEMPT_BUDGET` and is blocked. `markUnconfirmed` keeps its distinct
`last_error` wording so `telemetry status` still separates "may already be delivered" from
"rejected". Delete the `consumesAttempt = false` path if nothing else uses it.

### Tests cannot reach the hosted relay (F1)

Two independent guards:

1. **Runtime refusal.** The reserved test identity (`test-install-id`, declared once as a
   constant) never syncs to the hosted relay. `syncTelemetry` returns a refusal result for it
   and emits a `record_kind: refusal` record (seam `telemetry_sync`, value used `skipped`,
   expected `hosted_relay_delivery`, cause `reserved_test_install_id`). A custom proxy URL
   still receives it, so tests that exercise delivery keep working against a local sink.
2. **Shared fixture and guard.** The 15 test files that hand-write telemetry configs use one
   shared test fixture that points delivery at an in-process recording sink or a loopback
   URL. An architecture test fails when any test source writes a telemetry config with a
   non-`off` level and a blank or hosted `proxy_url` outside that fixture.

### Closed outbox event registry (F7)

One enum in `runtime-contracts`, e.g. `TelemetryOutboxEvent`, owns every outbox event name as
its `wireValue`. `TelemetryOutboxStore.enqueue` and every `enqueueTelemetry` /
`enqueueTelemetryEvent` caller take the enum, not a `String`. Retired names
(`skillbill_feature_implement_*`, `skillbill_feature_task_prose_*`, `skillbill_goal_prose_*`)
are absent from it. Add the registry to `WireVocabularyGovernedSeamInventory` so a literal
event name at an enqueue site fails `WireVocabularyArchitectureTest`.

A parity test reads `docs/telemetry-privacy.md` and fails when a registered event has no
section there. This subtask adds the missing section for
`skillbill_feature_task_runtime_rejection`.

### Verify duration truthfulness (F5)

`featureVerifyFinishedPayload` stops reporting an unobserved duration as a number:

- `completion_status: stale` (the reconciler closed it) → `duration_seconds: null`,
  `duration_seconds_availability: unavailable_incomplete`.
- missing or unparseable `started_at` / `finished_at` → `duration_seconds: null`,
  `duration_seconds_availability: unavailable_no_durable_state`.
- otherwise the measured value with `duration_seconds_availability: measured`.

Declare the new key beside the existing lifecycle keys.

### Swallowed auto-sync (F8)

`TelemetrySyncRuntime.autoSyncTelemetry` keeps not failing the caller, and it records the
swallowed exception (seam `telemetry_auto_sync`, value used `skipped`, expected `synced`,
cause the exception class simple name) per `docs/observability-policy.md`.

## Acceptance Criteria

1. A delivered batch carries each row's `event_uuid` as the top-level `uuid` of that event,
   and a row without `event_uuid` carries no `uuid`.
2. A batch whose send ends in `TelemetryDeliveryOutcome.UNKNOWN` five times is counted in
   `blockedCount` and is not claimed by a sixth drain.
3. `syncTelemetry` for install id `test-install-id` with no custom proxy URL sends nothing,
   returns a refusal result, and emits the refusal record; the same install with a custom
   proxy URL delivers to that URL.
4. An architecture test fails on a test source that writes a telemetry-enabled config with a
   blank `proxy_url` outside the shared fixture, and passes on the current tree after the
   15 files migrate.
5. `enqueue` and every outbox enqueue helper accept only the registry enum, and no outbox
   event-name string literal remains at an enqueue call site.
6. A parity test fails when a registered outbox event has no section in
   `docs/telemetry-privacy.md`, and passes on the current tree.
7. A stale verify-finished payload has `duration_seconds: null` with availability
   `unavailable_incomplete`; one with a missing timestamp has `duration_seconds: null` with
   availability `unavailable_no_durable_state`; a completed one has a measured value with
   availability `measured`.
8. An exception thrown inside `autoSyncTelemetry` returns `null` to the caller and emits one
   record naming seam `telemetry_auto_sync` and the exception class.

## Non-goals

- Relay changes (subtask 2).
- Changing `off`-level queuing, redaction, or what `full` collects.
- Deduplicating rows already in PostHog (operator runbook in [spec.md](spec.md)).
- Converting other callers of the `0`-on-missing `durationSeconds` helpers outside the
  verify-finished payload.

## Dependency notes

None. The top-level `uuid` passes through both the deployed relay and the repo relay
unchanged, because both forward events as-is apart from the exception rewrite.

## Validation Strategy

- Unit tests for the payload mapper (`uuid` present/absent), the drain budget on repeated
  `UNKNOWN`, the reserved-identity refusal, the verify duration branches, and the
  auto-sync record. Each names the audit finding it guards.
- The architecture guard and privacy-doc parity test run in `./gradlew check`.
- The dominant pack gate through `bill-code-check`.
- Manual, after release: resend one outbox row twice through the hosted relay and confirm
  PostHog stores one row for its `uuid`.

## Next path

Subtask 2: [Relay and stats](spec_subtask_2_relay-and-stats.md).
