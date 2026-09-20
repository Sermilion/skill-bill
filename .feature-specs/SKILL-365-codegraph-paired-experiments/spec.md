# SKILL-365 - CodeGraph paired experiments

## Mode

decomposed

## Intended outcome

Let a user run `/bill-feature <issue-key> experiments:codegraph` to perform the
same feature work twice, once with CodeGraph disabled and once with it enabled,
then inspect the measured differences. Both runs start from the same source and
spec snapshot. Each performs its own planning, implementation, and existing
quality phases. The experiment costs include both runs.

Ship the paired-run infrastructure as the first subtask and CodeGraph as the
first production experiment in the second. CodeGraph is an optional, pinned
external executable managed by Skill Bill. Do not copy or fork its implementation.
Ordinary feature runs retain their existing behavior.

## User contract

Accept at most one `experiments:<comma-separated names>` token on `bill-feature`.
Forward its value to both goal preflight and launch as `--experiments <names>`.
Expose the same input through governed entry points that create or resume goals.
Parse and validate it once in the runtime. Trim surrounding whitespace per name,
require lowercase kebab-case names, and reject empty values, empty elements,
duplicates, repeated options, and unknown names before any launch or provisioning.
The only production name in this release is `codegraph`.

Omission on a new goal means ordinary execution with no experiment. Omission on
resume preserves the stored selection. An explicit different selection on an
existing pair fails with an actionable typed conflict. An already-started ordinary
goal cannot become an experiment on resume. Legacy ordinary records resolve to
ordinary execution without rewriting historical measurements.

For future supported combinations, a comma-separated list means exactly two arms:
the control has all selected experiments disabled, and the treatment has all of
them enabled. It does not request one pair per name or a factorial study. Reject
incompatible combinations from descriptor declarations. Do not attribute a
combined result to an individual experiment.

The existing single confirmation gate shows the selected experiment, the two
runs, the delivery arm, CodeGraph download/index work if needed, and the expected
extra time and model spend. Say that two runs are required; do not promise exactly
twice the cost. Preflight stays read-only. Provisioning and execution begin only
after the existing confirmation. Do not introduce a second confirmation ceremony.

## Paired execution

The experiment unit is the whole prepared goal, including all its subtasks. The
parent spec and decomposition are inputs shared by both arms. Runtime discovery,
preplan, and plan execute independently inside each arm. Never give the treatment
the control's generated plans, edits, findings, or conversation, or vice versa.
Spec preparation and the initial confirmation occur once and are excluded from
the arm comparison.

Capture an immutable input identity before either arm starts: canonical source
repository identity, starting commit/tree, exact prepared spec bundle hashes,
effective configuration and guidance hashes, selected pack/add-on versions,
Skill Bill version, provider/model/effort assignments, phase routes, and budgets.
Both arms receive byte-identical input snapshots apart from the declared
experiment capability and arm-specific operational identities.

Prepared specs may be untracked. Copy the exact frozen bundle to both worktrees.
Require the remaining source tree to be clean before launch; report dirty paths
without stashing, committing, discarding, or silently excluding them. Recheck the
captured identities after confirmation. Record submodule revisions when present;
refuse a source layout that cannot be reproduced faithfully in both worktrees.

Run the arms sequentially to avoid concurrent build and model contention. Choose
and persist the first arm before execution using an injected random source;
record the order and seed so resume never redraws it. This balances order effects
across repeated pairs but does not make a single pair statistically conclusive.
Use separate worktrees, arm branches, agent sessions, runtime databases, mutable
learning stores, graph indexes, build outputs, and per-arm writable caches.
Namespace Git checkpoint refs and other common-directory resources by pair and
arm because worktrees share a Git directory. Snapshot any read-only dependency
cache input equally; record cache policy and unavoidable shared host caches.

One parent owns the pair lifecycle, lease, cancellation, and recovery. Its durable
records link pair id, arm id, workflow and attempt ids, worktree identity, and
terminal outcome. Each arm retains the existing goal runner and phase machinery.
An arm cannot recursively launch another experiment. Nested goal children inherit
the arm identity and capability policy rather than the public experiments option.

Pause or cancellation stops the current arm and prevents the other from starting.
Failure or timeout in one arm is retained as an outcome; the other may still run
unless the failure affects shared prerequisites. Resume reuses completed arms and
settled measurements, restarts only eligible unfinished work, and never changes
inputs, order, model settings, or dependency versions in place. Changed inputs
require a new pair identity through the established reset/new-run path. Process
takeover and cleanup retain the runtime's lease and resource-ownership rules.

## Isolation and delivery

The initial delivery policy selects the control arm before either run begins.
Retain both implementations and receipts for inspection. Do not select a winner
by speed, cost, changed-line count, or a model's opinion. A later product change
may add explicit delivery selection; it is not needed for this release.

During comparison, both arms may create local commits needed for review and the
one-commit-per-subtask contract. They must not push, create PRs, update external
issues, deploy, or publish shared learnings. Defer those actions to the parent
delivery path. Mark each arm's local finalization as deferred publication; never
pretend it pushed or fabricate remote completion evidence. Preserve checkpoint
refs until the existing push-and-manifest prerequisites are actually satisfied.

The parent may publish only the predetermined control result after its normal
completion and readiness requirements pass. Treatment failure does not conceal
control failure or promote treatment automatically. Control success may still
deliver when the comparison is incomplete, but report the incomplete comparison.
Promotion preserves the control's reviewed commit/tree identities, one commit per
subtask, and existing finalization rules. Changed source/base identities follow
the current readiness policy and may block delivery. Never overwrite concurrent
user edits. Publication is idempotent across a crash after push or PR creation.
Record delivery overhead separately from the measured arm work.

Enforce arm tool, filesystem, and external-write restrictions through supported
launcher capabilities, not prompt wording alone. The control cannot query a
globally installed CodeGraph MCP server or executable, read the treatment index,
reuse CodeGraph results, or install its own copy through an alternate command.
Both arms lack external publication capability until parent delivery. A harness
that cannot enforce the declared isolation is unsupported for this experiment;
preflight reports a typed capability refusal before either arm runs. Ordinary
feature execution on that harness remains available. Discover support through
injected strategies/capability declarations, not provider identity branches in
the process runner. Detect and record any observed policy breach and invalidate
the pair's affected comparisons.

## CodeGraph integration

Declare `codegraph` through one validated experiment descriptor under an
orchestration-owned catalog, with its version, treatment capability, required
launcher capabilities, supported phases, and compatibility constraints. Discover
the catalog dynamically. Keep executable implementation bindings at the
composition root; descriptors cannot introduce arbitrary downloaded commands.
Do not create a general plugin framework or duplicate platform routing tables.

The Skill Bill installer offers CodeGraph as an optional component. Store its
verified executable in a versioned Skill Bill tools directory, outside staged
skill folders. Resolve platform assets from a governed dependency declaration
with an exact upstream version and trusted expected digests. Verify before
execution, install atomically under a lock, and retain license notices. Reuse a
matching installation, fail on mismatched bytes or unsupported hosts, and keep
the executable pinned for active/resumable pairs. Never use `latest`, an implicit
global PATH match, `npx` downloading at query time, or upstream self-update.

If the optional component is absent, the feature confirmation includes its
provisioning and launch installs it after confirmation. A verified cached copy
works offline. Missing offline assets fail explicitly. Provide a documented
explicit local executable override for development, with identity/version checks
and provenance in the report. Do not run CodeGraph's agent-configuration
installer or modify users' global agent configuration. Disable CodeGraph's own
telemetry for managed invocations; Skill Bill's telemetry preference is separate.
Uninstall removes only Skill Bill-owned tool artifacts and respects active runs.

A process adapter behind a consumer-owned retrieval port invokes CodeGraph's
documented structured CLI queries with bounded time, output, and cancellation.
Use the self-contained distribution; do not add a JVM-to-Node embedding layer.
The treatment's index belongs only to its worktree. Provision before either arm
starts so missing dependencies do not waste a control run, but charge download,
verification, and installation once to treatment setup. Initial indexing and
later syncs also count toward treatment overhead.

Enable retrieval for discovery, preplan, plan, implement, and existing repair
phases. For review, query through the existing evidence broker. Enforce its
assignment, revision identity, expansion authorization, and byte/call budgets.
Use graph results to identify candidate symbols and paths; deliver authoritative
source from the exact permitted revision or measured working-tree snapshot.
An external graph never expands review scope or replaces required diff evidence.
Keep the existing heading-only boundary-memory discovery and platform-pack
exclusions. Graph retrieval does not authorize extra documentation bodies.

Synchronize before queries and verify source/index identity around each read.
Record a bounded query receipt with arm, phase, source identity, dependency
version, index identity, latency, result bytes, and outcome. Detect edits racing
with a query. Stale, malformed, oversized, or out-of-scope results cannot become
valid evidence. A known retrieval failure may use the existing discovery path
only with a recorded degradation. Preserve its cost, mark the treatment degraded,
and exclude that pair from clean gain aggregates. Scope or identity violations
fail loudly. No CodeGraph usage produces a `not_exercised` result rather than a
claimed graph benefit.

Build and validation use their existing phase-specific commands and authority.
CodeGraph never substitutes affected-test selection for required checks, grants
review clearance, changes acceptance criteria, or widens the compile-only build
session. A supported language claim is not proof that Kotlin extension functions,
overloads, injected interfaces, or callbacks resolve correctly in this repository.

## Measurement and comparison

Persist raw observations locally even when remote telemetry is disabled. Reuse
existing usage, review, and workflow measurements where available. Extend the
owning provider strategy where a reliable measurement is missing; do not infer
tokens, cost, tool calls, or correctness from an agent's narrative. Every optional
quantity carries the existing measurement-availability vocabulary. Unsupported
or incomplete measurements are null/absent with a reason, never zero.

| Measure | Required interpretation |
| --- | --- |
| Arm duration | Active execution through the same terminal boundary, including retries and local review commits; report pause/wait time separately. |
| Pair duration | Actual end-to-end experiment time, both arms and setup; identify delivery and report-generation time separately. |
| Tokens | Provider-reported input, output, cache read/write categories and totals under a recorded accounting definition; retain category availability. |
| Cost | Provider-reported billed usage where available; any estimate names its pricing source/version and remains distinct from billed cost. |
| Discovery | Graph query count, ordinary search/read calls when observable, delivered evidence bytes, failures, and fallbacks per phase. |
| Setup | Download, verification, installation, initial index, incremental sync time, and cache state; cached setup is measured as reuse, not guessed savings. |
| Quality evidence | Acceptance outcomes, review dispositions and findings by severity, selected validation commands and results, repair attempts, and terminal completion status. |

Compare identical prepared acceptance criteria and record the actual validation
set for each arm. Use the same external acceptance checks when such checks exist;
arm-authored tests are additional evidence and their counts are not a quality
score. Mark differing or missing quality checks explicitly. Review finding counts
are observations, not ground truth or proof that either implementation is better.

Deduplicate measurements by pair/arm/workflow/phase/attempt/event identity. Sum
actual attempts once, including failures and repair work; exclude reused planning
receipts from new usage. Record collection/version differences and provider model
resolution when known. Fresh agent sessions avoid transcript sharing, but report
provider cache usage and unresolved model identity rather than claiming perfect
environmental equivalence.

Provide read-only `skill-bill experiments report <pair-id> --format text|json`
and `skill-bill experiments stats --name codegraph --format text|json`. Goal
status and the terminal relay include pair status and the report reference. The
report shows arm identities, delivery arm, actual treatment use, source/settings
fingerprints, completeness, raw measurements, and explicit comparability reasons.
Both JSON and text derive from the same validated durable projection. Regenerate
a failed report projection without rerunning either arm.

For each comparable lower-is-better metric, report absolute savings as control
minus treatment and percent savings as `100 * (control - treatment) / control`.
When control is zero or either value is unavailable, omit the percentage with a
reason. Negative savings remain negative. Show execution-only and setup-inclusive
treatment comparisons, plus total experiment spend for both arms. Do not present
the combined experiment spend as a saving or hide the setup cost.

A single pair is a measured observation, not a causal or statistical conclusion.
Aggregate only comparable completed pairs, grouped by experiment/version,
runtime/provider/model settings, budget policy, repository/input cohort, and
cache policy. Report sample counts, medians of per-pair deltas, failures, degraded
and not-exercised pairs, and every exclusion reason. Do not silently drop bad
treatment outcomes or claim significance from a fixed small sample. Quality
regressions stay visible beside speed/cost changes; do not emit an automatic
overall winner. Telemetry uses bounded identifiers and aggregates with no source,
queries, paths, or code bodies, and follows the existing outbox consent policy.
Keep ordinary goal statistics from counting the two experiment arms as two
independently delivered features or double-counting parent and child cost.

## Architecture and persistence

Extend the current goal owner and phase runner through explicit ports and narrow
request values. Keep filesystem/process adapters in infrastructure, orchestration
in the engine/application owners, typed identities in their owning contract/domain
families, and composition in runtime-core. Do not implement a second phase engine.

The parent database owns pair identity, immutable settings, arm outcome receipts,
measurement ledger, and delivery status. Arm databases own their workflow state.
There is no cross-database transaction: publish idempotent, identity-checked arm
receipts and reconcile them into the parent. A filesystem report is a projection,
not continuation authority. A receipt conflict fails loudly. Recovery cannot
turn an incomplete arm into success or rerun a completed arm to rebuild a report.

Define new runtime contracts as Draft 2020-12 YAML schemas first, with Kotlin
version/key owners, parity tests, typed parse errors, and loud failures at every
producer and consumer seam. Extend existing envelopes through their contract
owners. Preserve existing no-experiment records and quarantine incompatible
experiment records through the runtime's governed recovery path. Account for
experiments in status, resume, reset, purge, retained artifacts, and telemetry.
Purge refuses live leases and removes only owned resources; retain failed arm
evidence until explicit cleanup. No generated outputs belong in authored source.

## Acceptance Criteria

1. `experiments:codegraph` reaches preflight and launch as the same validated selection, creates one control/treatment pair, and omission on new work retains ordinary single-run behavior. Invalid input fails before side effects.
2. The single gate describes paired execution, delivery policy, setup, and extra spend; read-only preflight creates no processes, indexes, worktrees, or experiment state.
3. Both arms use the same frozen source/spec/config inputs and independent planning and execution state. The stored order, inputs, and selection survive pause, crash, and resume without rerunning completed arms.
4. Supported launchers enforce CodeGraph exclusion in control and deferred publication for both arms. Unsupported isolation fails before launch; detected contamination invalidates comparison.
5. Only the predetermined control result can enter normal delivery, with existing readiness and commit identity requirements. Reconciliation cannot publish twice or overwrite user changes.
6. CodeGraph installs as an optional pinned verified executable, works from cache offline, preserves active versions, and never rewrites global agent configuration or runs when experiments are omitted.
7. Treatment retrieval reaches planning and implementation through a bounded port and review through the existing evidence broker. Stale or unauthorized results cannot count as evidence, and every fallback records a degradation.
8. Durable measurements attribute actual time, usage, setup, query activity, retries, and quality evidence to the right pair, arm, phase, and attempt without duplicates. Missing quantities retain availability reasons.
9. Text/JSON reports and cohort statistics expose raw values, defined savings, total experiment cost, correctness evidence, and all incomplete/degraded/not-exercised outcomes without an automatic winner or unsupported causal claim.
10. Local reports work with remote telemetry disabled; optional telemetry remains bounded and consent-controlled. Existing goal statistics count experimental work without inflating delivered-feature totals.
11. Contract validation, receipt recovery, resource cleanup, and recorded degradation obey the repository's architecture and observability requirements. Existing ordinary goal, build, review, and install behavior remains covered.

## Non-goals

- Copying, forking, or reimplementing CodeGraph's parser, resolver, or graph database.
- Making CodeGraph mandatory, adding another production experiment, or rolling treatment out by default.
- Factorial experiments, automatic traffic allocation, a remote experiment service, or automatic winner promotion.
- Duplicating feature-spec preparation, publishing both arms, or letting graph results replace acceptance and validation evidence.
- General support for arbitrary external tools through executable configuration, or unrestricted graph access by review workers.
- IDE dashboard work, comprehensive hardware benchmarking, or claiming savings before measured pairs exist.

## Executable subtasks

1. Paired experiment infrastructure and comparison reporting. Own the runtime contract, isolated execution, lifecycle recovery, publication boundary, measurements, reports, and skill/CLI forwarding. Prove the pair behavior with an injected deterministic experiment fixture; do not ship a fake public experiment name.
2. Managed CodeGraph treatment. Register the sole production experiment, add pinned provisioning and bounded retrieval, connect it to the first subtask's policies and measurements, and exercise a complete CodeGraph pair.

The split separates two independently reviewable systems: durable paired workflow
execution and a third-party executable's installation/retrieval lifecycle. The
second consumes the first's settled arm identity, capability, and measurement
contracts. Do not split schemas, consumers, and their tests into extra commits.

## Validation strategy

Use deterministic fixture agents and real temporary Git worktrees/databases for
lifecycle evidence. Name each regression before adding its test. Focus on input
drift, control contamination, duplicate receipt accounting, resumed publication,
stale graph evidence, and missing usage treated as zero. Subtask specs identify
the required acceptance and rejection cases. Exercise comparison math with known
values rather than stochastic model output.

Use the pinned real CodeGraph binary for a bounded integration fixture containing
Kotlin extension functions, an interface/implementation call, and cross-file
callers. Record what resolves and what does not. An optional live paired feature
run can populate the first report, but do not make paid agent access or a claimed
performance improvement a prerequisite for code acceptance. No live gain has been
measured during this spec preparation.

During implementation, use the repository's phase-appropriate checks. A routed
compile-only build session remains compile-only. Run focused contract/runtime
tests and the declared quality validation in their proper phases. Refresh install
output with `./install.sh` after authored skill, renderer, or pointer changes.
Spec preparation itself does not install tools or start implementation.

## References

- `skills/bill-feature/content.md`, token forwarding and the one-gate launch contract.
- `runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/goalrunner/preflight/GoalPreflightService.kt`, read-only preflight.
- `runtime-kotlin/runtime-ports/src/main/kotlin/skillbill/ports/review/evidence/ReviewEvidenceBroker.kt`, review evidence authority.
- `runtime-kotlin/ARCHITECTURE.md`, design principles and durable state ownership.
- `docs/code-principles.md`, `docs/observability-policy.md`, and `docs/skill-source-generation.md`.
- [CodeGraph CLI and integration documentation](https://github.com/colbymchenry/codegraph#cli-reference). Verify the selected release's actual CLI contract before binding the adapter.

## Next path

Run `skill-bill goal SKILL-365` to implement this prepared feature. The new
`experiments:codegraph` option applies to later feature runs after this work lands;
do not pass an unimplemented option while implementing the option itself.
