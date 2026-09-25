# SKILL-381 subtask 1 - Runtime delivery integrity

## Scope

Fix the runtime side of audit findings F1, F2 (through F3), F3, F5 (duration), and F7
([audit.md](audit.md)).

### Event identity PostHog honours (F3)

`TelemetryProxyPayloadMappers` sends each row's `event_uuid` as the top-level event `uuid`,
the field PostHog uses for event identity. Declare the key in `TelemetryProxyPayloadKeys`.
Keep `$insert_id` on the properties for installed relays and older readers. A row with no
`event_uuid` omits `uuid` and does not mint one.

### Unconfirmed retries stay unbounded (F2)

The drain keeps its three-way outcome: `UNKNOWN` does not consume an attempt, so an offline
machine never blocks its own events (`../../../agent/history.md`, the outbox drain entry). That
design assumes the receiver collapses a resend onto the earlier row. It did not, because
PostHog ignores `$insert_id`; the event identity above is what makes the assumption true.
No budget change. A test pins that a batch resent after `UNKNOWN` carries the same `uuid`.

### Tests cannot reach the hosted relay (F1)

Two independent guards:

1. **Runtime refusal.** The reserved test identity (`test-install-id`, declared once as a
   constant) never syncs to the hosted relay. `syncTelemetry` returns a refusal result for it
   and emits a `record_kind: refusal` record (seam `telemetry_sync`, value used `skipped`,
   expected `hosted_relay_delivery`, cause `reserved_test_install_id`). A custom proxy URL
   still receives it, so tests that exercise delivery keep working against a local sink.
2. **Guard.** `TelemetryTestIsolationArchitectureTest` fails when a test that builds a real
   runtime context (`CliRuntimeContext` / `McpRuntimeContext`, the only test entry points wired to
   the HTTP client) enables telemetry without the reserved identity or a custom proxy URL. The
   existing reserved-id tests already satisfy it; `CliRuntimeShellCommandsTest` moves from
   `doctor-install-id` to the reserved id.

### Closed outbox event registry (F7)

One enum in `runtime-contracts`, e.g. `TelemetryOutboxEvent`, owns every outbox event name as
its `wireValue`. `TelemetryOutboxStore.enqueue` and every `enqueueTelemetry` /
`enqueueTelemetryEvent` caller take the enum, not a `String`. Retired names
(`skillbill_feature_implement_*`, `skillbill_feature_task_prose_*`, `skillbill_goal_prose_*`)
are absent from it. The typed parameter makes a literal event name at an enqueue site a compile
error, so no separate wire-vocabulary guard is needed.

A parity test reads `../../../docs/telemetry-privacy.md` and fails when a registered event has no
section there. This subtask adds the missing sections for
`skillbill_feature_task_runtime_rejection`, `skillbill_review_finished_legacy_regenerated`, and
`experiment.completed`, and corrects the list of events queued at `off` (seven, not five).

### Verify duration truthfulness (F5)

`featureVerifyFinishedPayload` stops reporting an unobserved duration as a number:

- `completion_status: stale` (the reconciler closed it) → `duration_seconds: null`,
  `duration_seconds_availability: unavailable_incomplete`.
- missing `started_at` / `finished_at` → `duration_seconds: null`,
  `duration_seconds_availability: unavailable_no_durable_state`.
- otherwise the measured value with `duration_seconds_availability: measured`.

Declare the new key beside the existing lifecycle keys.

## Acceptance Criteria

1. A delivered batch carries each row's `event_uuid` as the top-level `uuid` of that event,
   and a row without `event_uuid` carries no `uuid`.
2. A batch resent after a `TelemetryDeliveryOutcome.UNKNOWN` outcome carries the same
   top-level `uuid` on every resend, and `UNKNOWN` still consumes no delivery attempt.
3. `syncTelemetry` for install id `test-install-id` with no custom proxy URL sends nothing,
   returns a refusal result, and emits the refusal record; the same install with a custom
   proxy URL delivers to that URL.
4. An architecture test fails on a test that builds a real runtime context with telemetry
   enabled under a non-reserved install id and no custom proxy URL, and passes on the current
   tree.
5. `enqueue` and every outbox enqueue helper accept only the registry enum, and no outbox
   event-name string literal remains at an enqueue call site.
6. A parity test fails when a registered outbox event has no section in
   `../../../docs/telemetry-privacy.md`, and passes on the current tree.
7. A stale verify-finished payload has `duration_seconds: null` with availability
   `unavailable_incomplete`; one with a missing timestamp has `duration_seconds: null` with
   availability `unavailable_no_durable_state`; a completed one has a measured value with
   availability `measured`.

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

- Unit tests for the payload mapper (`uuid` present/absent), `uuid` stability across
  `UNKNOWN` resends, the reserved-identity refusal, and the verify duration branches. Each names the audit finding it guards.
- The architecture guard and privacy-doc parity test run in `./gradlew check`.
- The dominant pack gate through `bill-code-check`.
- Manual, after release: resend one outbox row twice through the hosted relay and confirm
  PostHog stores one row for its `uuid`.

## Next path

Subtask 2: [Relay and stats](spec_subtask_2_relay-and-stats.md).
