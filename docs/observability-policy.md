# Observability Policy

Every fallback, degradation, and swallowed failure emits a record. A silent
fallback is a defect: it produces the symptom of a bug with none of the evidence.

Emit an observability event, structured log line, or telemetry field at each of
these seams:

- a `?:`, `takeIf`, `getOrNull`, `orEmpty`, or default-value path that
  substitutes a narrower or wider scope than the caller asked for
- a `runCatching` or `catch` that continues instead of rethrowing
- a retry, timeout, cap, truncation, or sampling decision
- a capability that resolves to absent and is skipped
- spec-intent resolution that records `spec_context: none` (`no_spec_found`, `ambiguous_match`, `not_applicable_scope`) or falls through from an unreadable decomposition manifest to branch-derived glob search; records carry reason, rung, and resolved path only, never spec body
- a skipped adjudication stage, a verification or adjudication worker that failed to launch or return, and a stage that ended without a reached boundary; each emits `skillbill_review_stage_degradation` with seam, expected, actual, and a closed reason, carrying `review_run_id` only
- a legacy-record migration, quarantine, or regeneration
- a runtime refusal or migration normalization; records use `record_kind: refusal` or
  `record_kind: migration` and carry the seam, value used, expected value, and bounded cause
- a reconciliation that repairs drift between durable state and disk
- checkpoint-ref prune: `FeatureTaskRuntimeCheckpointRefPrune.pruneSubtaskCheckpointRefs`
  when listing or deleting a ref under `refs/skill-bill/checkpoints/` fails, or when
  pruning is skipped because `commit_sha` is still blank
- gate JVM resolution: `GateJvmResolver.resolve` records the guard branch taken
  (`skill_bill_java_home`, `inherited_java_home`, `path_java`, `scan`, `unresolved`), the
  `JAVA_HOME` handed to the child, and every `JAVA_HOME`, `SKILL_BILL_JAVA_HOME`, or `PATH`
  entry dropped because it pointed inside the runtime image root.
  `unresolved` is a typed error at the runtime-run gate and a recorded
  degradation with `JAVA_HOME` left absent at the agent-run launch surface; the `unresolved`
  record names the rejected candidate and the required major so the degraded launch carries the
  same attribution as the typed error
- gate JVM startup failure: a gate command that exits non-zero, parses no findings, and reports a
  JVM-initialization failure raises `GateJvmStartupFailureException` naming the resolved Java home
  instead of minting an `unparseable_gate_failure` finding, so an unusable gate JVM surfaces as an
  environment defect rather than as a repair turn no source edit can clear
- platform-pack `contract_version` leniency: `PlatformPackSchemaValidator.validate`
  when a caller enumerates with `enforceContractVersion=false` (reconcile's LOCAL side and
  installed-workspace baseline status) and a stale `const` violation is tolerated instead of
  raising `ContractVersionMismatchError`

Each record names the seam, the value actually used, the value that was expected,
and why the substitution happened. A fallback that cannot be attributed to a
specific cause is a loud-fail, not a log line.

Absent platform-pack `validation_gate` declarations degrade validate to agent-run
behavior at seam `ValidationGateResolver.resolve` / `feature-task.validate.validation_gate.absent`
with a surfaced record; a malformed declaration loud-fails and never degrades to
"no gate".

Prefer loud-fail over log-and-continue whenever the substituted value changes a
contract the caller depends on — scope bounds, review deltas, staged path
inventories, and durable identity are contracts. Log-and-continue is for
degradations the caller can still trust.

Bounded output rules still apply: records carry counts, identities, and sanitized
labels, never raw payloads, diff hunks, or unbounded child output.

## Telemetry delivery health records on the row, never in the queue

Telemetry delivery degradation is the one seam where emitting a telemetry event is
forbidden. The outbox drain is what failed; enqueuing a diagnostic into the same
store feeds the drain it cannot complete, and `autoSync` runs at every CLI
completion and every MCP tool call, so the queue grows faster than it drains.

The record lives on the failing row instead:

- `last_error` carries a bounded, actionable message that distinguishes *rejected*
  (the receiver will not accept this payload, recorded with the relay's status code
  and its returned reason, truncated to a bounded length), *unconfirmed* (the receiver may
  already hold the batch — a timeout, an aborted upstream, or a relay 502 after
  possible acceptance), and *local acknowledgement failed* (the batch was accepted
  but the local mark-synced write did not land)
- `delivery_attempts` counts the failures the receiver confirmed — a rejection, and
  an accepted send whose local acknowledgement did not land. A row past the budget
  stays queued but is no longer claimed, so a poison row cannot replay forever
- an *unconfirmed* failure records `last_error` and releases the claim without
  spending budget. It is a transport verdict, not a receiver verdict, and `autoSync`
  fires often enough that charging it would block the whole queue permanently after
  a few offline minutes, with no way back once connectivity returned
- `skill-bill telemetry status` reports `pending_events`, `latest_error`, and
  `blocked_events`; a drain that ends with blocked rows reports `failed`, never
  `synced`, so budget exhaustion is never silent
- a blocked row has no redelivery path. There is no retry command, so the failure
  message names what an operator can actually do — read the row through
  `telemetry status`, or discard it with `skill-bill telemetry clear` — instead of
  promising an automatic retry that never arrives
- a drain that claimed nothing because a concurrent drain holds every queued row
  reports `noop` with those rows still counted in `pending_events`, never `synced`
  with zero delivered events, so a stalled drain does not read as a clean one
- a client failure of any type, not only a transport `IOException`, records
  `last_error` and releases the claim. An escaping failure left the batch claimed
  with no recorded cause until the lease expired

### Reproduced cause and what stayed unconfirmed

The reproduction fixtures cover three candidate causes of whole-batch resend. Two
reproduce deterministically against a real SQLite file and a fake transport:

1. **Unclaimed concurrent drain.** `listPending` handed identical unclaimed rows to
   every drainer, so two drains sent the same batch. Repaired by an explicit claim
   with an expiring lease.
2. **Accepted send, failed local acknowledgement.** The drain looped on
   `listPending` and re-read the same still-pending rows, resending them on the next
   pass without bound. Repaired by claiming before sending, counting progress only
   after a durable mark-synced, and bounding the invocation by the pending snapshot.

The third — the receiver accepting a batch while the sender loses the
acknowledgement — is covered by a fake-transport fixture that proves the *sender*
keeps one recoverable entry with its original identity. It does **not** prove what
the receiver did with the retry: that depends on the provider's `$insert_id`
deduplication window, which is finite. Whether any specific historical duplicate
originated from this path rather than from cause 1 or 2 stays unconfirmed; the
fixtures pin the sender-side behavior, not a retrospective attribution.

### Counting historical telemetry

Historical analysis of already-ingested events is read-only. Do not delete,
rewrite, or reclassify past events to make a count look clean.

- Events ingested before durable identity existed carry no `$insert_id`. They cannot
  be deduplicated retroactively and must be reported with that caveat attached.
- After the change, duplicate suppression is bounded by the receiver's finite
  deduplication window. A retry outside that window is a real second row upstream.
  Report deduplicated counts as best-effort, never as exactly-once.
- Stale or missing data is stale or missing. A gap in a series is not a proven zero,
  and an absent event is not a proven non-occurrence; say which it is rather than
  converting either into an outcome.
