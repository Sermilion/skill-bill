# SKILL-247 Subtask 1 - Bound telemetry delivery and fence settlement

Parent spec: [.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec.md](spec.md)
Issue key: SKILL-247

## Scope

Resolve F-001, F-002, and F-003 in investigation.md. Own runtime-application telemetry sync and TelemetryService, runtime-ports telemetry contracts and fixtures, runtime-infra-sqlite TelemetryOutboxStore, runtime-infra-http JdkHttpRequester, their DI wiring, and affected tests. Treat one claimed delivery as the unit of ownership. Complete the port, adapter, caller, and test changes together.

## Acceptance Criteria

1. Pass claim identity through markSynced, markFailed, and markUnconfirmed, or replace those methods with an equally small owned-settlement API. SQL updates require that identity and an unsynced row. Report a lost claim explicitly using the existing boundary vocabulary or a minimal typed result. Do not fake success on zero owned rows.
2. An A-claim, B-reclaim, late-A-settlement sequence leaves every B-owned field unchanged for each settlement outcome. A third claimant cannot acquire B's live claim. A late failure cannot add an error or consume an attempt on an already acknowledged row.
3. Use the injected clock when claiming each batch. A later batch after a slow earlier request receives a fresh lease timestamp. Do not extend a claim by changing event_uuid. Preserve receiver deduplication identity across retries.
4. Configure finite connect and response deadlines below the claim lease through the existing transport owner. Prefer fixed justified defaults and an injectable test seam over new user configuration. Reuse a JDK HttpClient with an explicit runtime lifetime, rather than constructing one for every request.
5. Rethrow CancellationException and preserve InterruptedException and the thread interrupt signal through delivery, acknowledgement, reconciliation, manual sync, and auto sync. Release a cancelled claim only under its current identity or let its lease expire. Cancellation must not consume an attempt or be converted to an UNKNOWN delivery report.
6. Ordinary background sync failure remains non-fatal to its caller but records a bounded payload-free diagnostic even when the outbox itself fails. Preserve accepted, rejected, and unknown delivery distinctions and the existing finite rejection budget.
7. Prove stale settlement rejection using real SQLite operations. Prove request deadlines with a loopback server that withholds a response and always cleans up its threads and sockets. Use fakes for cancellation at claim, transport, and acknowledgement; assert outcome and durable state, not call counts.

## Non-Goals

- No remote telemetry service changes or exactly-once guarantee.
- No generic lease framework, background scheduler, retry daemon, or new HTTP dependency.

## Dependency Notes

Depends on: none
Independent of the other subtasks. This is one cross-module protocol change and must ship with all consumers adapted.

## Validation Strategy

Run the focused telemetry/outbox tests in application, SQLite, and HTTP modules, plus their affected DI compilation. Extend the existing outbox tests for expired-owner settlement. Use an injected clock for batch timing. Follow the routed quality gate for implementation; retain current UUID, attempt-budget, and delivery-identity migration tests.

## Next Path

Continue with subtask 2 after this independently shippable delivery change passes review.

## Spec Path

.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec_subtask_1_bound-telemetry-delivery-and-fence-settlement.md
