# SKILL-366 - Experiment support

## Mode

single_spec

## Intended outcome

Provide shared experiment selection, isolated paired execution, durable recovery,
measurement, and reports. Extract this responsibility from SKILL-365 so CodeGraph
and Jev navigation use the same experiment accounting and lifecycle.

Support two concrete execution modes. A goal pair performs the same prepared
feature twice through the existing workflow. A navigation pair performs two
read-only evidence searches against the same frozen source and acceptance
criteria. Both have control and treatment arms. They share lifecycle and report
contracts but retain different execution boundaries and quality measures.

SKILL-365 registers the CodeGraph goal treatment. SKILL-367 registers the Jev
navigation prototype. This spec ships no production no-op experiment. Exercise
both modes through injected descriptors and deterministic fixture agents.
Ordinary feature execution remains the default.

## Ownership and descriptor contract

Discover a validated orchestration-owned descriptor catalog dynamically. Each
descriptor declares its stable name and version, one supported execution mode,
required launcher capabilities, compatibility constraints, treatment capability,
and setup and measurement requirements. Executable bindings belong at the
composition root. A descriptor cannot supply arbitrary downloaded commands.

Implement only goal pairs and navigation pairs. Reuse the existing goal runner
for goal arms and a bounded read-only session runner for navigation arms. Do not
build a general workflow engine, plugin loader, or configurable arbitrary arm DAG.
The registry can be empty before consumer specs land; unknown selections fail
with available names for the requested mode. Test descriptors never appear in
installed production discovery.

The goal owner owns feature delivery. Navigation has no delivery operation and
cannot satisfy or mutate a goal's audit gate. Experiment reports own observations,
not production acceptance decisions. No mode adds implementation-phase output,
AC-to-file maps, evidence receipts, or any other structured handoff requirement.


## Goal-pair user contract

Support experiment enablement in the existing machine config and repository
config, and explicit per-run selection on `bill-feature`. Config controls which
registered experiments are available for selection. It never selects experiments
for a new run. Without an `experiments` parameter, the selection is always empty,
even when config enables experiments.

Use an optional `experiments` array of enabled registered names in both config
formats. Example machine config at the existing resolved config path, normally
`~/.config/skill-bill/config.json`:

```json
{
  "experiments": ["codegraph"]
}
```

The optional repository override in `../../../.skill-bill/config.yaml` uses the same key:

```yaml
experiments: [codegraph]
```

Use the existing machine-config path resolver, including its supported environment
override. Preserve unrelated fields and the existing install persistence boundary.
Configuring experiments does not change telemetry consent or credentials.

A present repository array replaces the machine array rather than merging with
it. An empty array disables all experiments for new selections. An absent key
inherits the machine policy. If neither config declares the key, registered
experiments remain available for explicit selection, subject to mode and launcher
capability checks. This permits one-off opt-in without editing config. Config
cannot force an experiment into a run or expand the requested list.

Nulls, scalar strings, non-string entries, and empty names are malformed config.
Validate present values through the owning config boundary; a higher-priority
value cannot hide a malformed config document or invalid experiment name. Report
the config path and offending key in typed errors. A request for an experiment
disabled by the effective config fails before provisioning or launch.

Accept at most one `experiments:<comma-separated names>` parameter. For example,
`experiments:codegraph,typesafe` requests those two names together if both have
registered compatible goal-pair descriptors. Names come from the registry; the
parser must not hard-code CodeGraph, TypeSafe, or a fixed number of experiments.
The example does not register a `typesafe` descriptor or make the navigation-only
prototype compatible with feature goals. Report unsupported names or combinations
with the available compatible choices.

Omission on a new run and `experiments:none` both choose no experiments. Reserve
`none` as a disable token, never a descriptor name, and reject mixtures such as
`experiments:none,codegraph`. Empty parameters remain invalid. Trim surrounding
whitespace per name, require lowercase kebab-case, and reject duplicates, repeated
options, unknown names, incompatible combinations, and names unsupported in the
requested mode. Config arrays use the same name validation; config disablement
uses `[]`, not `[none]`. Normalize selections as sets with a stable stored order;
reordering names does not request a different experiment combination.

Forward the explicit parameter unchanged to both goal preflight and launch as
`--experiments <names>` or `--experiments none`. Preserve absence as an empty new-run
selection. Runtime code owns parsing and config validation; skill prose does not
resolve defaults or populate the parameter from config. Record requested and
effective names plus the applicable config policy in preflight and durable input.
The gate shows the complete selected list and both arms. Recheck relevant input
identities at launch so a config change after confirmation cannot silently alter
selection or bypass a newly disabled experiment.

Resuming an existing run continues its immutable saved selection, including an
empty selection. Omission does not create a new selection or restart a pair as
ordinary work. An explicit parameter is compatible only when its normalized set
matches the saved selection. A different set, including `none` for an experimental
pair, fails with a typed conflict and requires a new run. Config changes apply to
new runs and do not mutate a resumed run's frozen policy. Legacy ordinary records
remain ordinary without rewriting historical measurements.

Explicit selection requests paired execution, not treatment-only execution, and
uses the existing confirmation gate. A navigation-only name cannot enter a
feature goal. The read-only navigation command selects its descriptor explicitly
and checks the same availability policy without adding config-enabled experiments
to that request.

A supported comma-separated combination means exactly two arms:
the control has all selected experiments disabled, and the treatment has all of
them enabled. It does not request one pair per name or a factorial study. Reject
incompatible combinations from descriptor declarations. Do not attribute a
combined result to an individual experiment.

The existing single confirmation gate shows the selected experiment, the two
runs, the delivery arm, descriptor-declared setup work if needed, and the expected
extra time and model spend. Say that two runs are required; do not promise exactly
twice the cost. Preflight stays read-only. Provisioning and execution begin only
after the existing confirmation. Do not introduce a second confirmation ceremony.

## Goal-pair execution

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

## Goal-pair isolation and delivery

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
launcher capabilities, not prompt wording alone. The control cannot access the selected treatment through inherited servers,
global executables, treatment artifacts, or an alternate installation command.
Each descriptor supplies the concrete exclusions its supported launchers enforce.
Both arms lack external publication capability until parent delivery. A harness
that cannot enforce the declared isolation is unsupported for this experiment;
preflight reports a typed capability refusal before either arm runs. Ordinary
feature execution on that harness remains available. Discover support through
injected strategies/capability declarations, not provider identity branches in
the process runner. Detect and record any observed policy breach and invalidate
the pair's affected comparisons.

## Navigation-pair execution

Expose `skill-bill experiments run <name> --repo <path> --spec <path> --revision <commit>`
for descriptors that declare navigation mode. The explicit command requests both
read-only arms and prints their report reference. It does not start feature work
or create a publication confirmation. Help and preflight disclose the two runs,
selected providers, configured budgets, and any known usage estimate. Validate
inputs and launcher support before agent calls or durable execution state.

Resolve the revision to a commit and freeze its source tree, original spec bytes,
and numbered acceptance criteria. Require at least one criterion through the
existing governed spec parser. Source comes from that commit, including pinned
submodule identities when reproducible. Ignore later working-tree edits without
modifying them and state the selected revision in status and reports. Copy an
untracked spec as a separate immutable input. Hash effective instructions, model
identities, budgets, descriptor version, and observation-tool policy.

Each arm starts with the exact AC text and root directory listing in its own
fresh session. The control is the configured normal reasoning model with its
ordinary read-only discovery tools. The treatment receives its declared decision
adapter. Both can obtain the same eligible source through enforced read-only
listing, search, and source-reading tools. The control may choose its own search
queries and jump directly to files; do not force it to follow the treatment's
directory traversal algorithm. Record tool differences and any enforced
restrictions. An unsupported normal discovery capability makes the baseline
restricted and excludes it from claims about normal workflow performance.

The source policy declares eligible tracked files, bounded file/chunk sizes,
symlink handling, and exclusions before either arm runs. Deny escapes from the
snapshot and access to credentials, Git internals, runtime-private artifacts,
benchmark labels, and the other arm's state. Record excluded or unreadable sources
and unresolved references. Keep existing governed guidance access rules. If
isolation cannot hide labels and mutable workspace files from an agent, refuse
that launcher before execution. A prompt-only prohibition is insufficient.

Curated reference labels live outside the agent-visible snapshot. The scorer
receives them only after arm evidence is settled. A generated implementation
plan, the other arm's result, or a human-selected AC-to-code shortlist is never an
arm input. A generic collection of files selected by deterministic tools is
allowed only when the active arm requested that observation.

Persist randomized sequential arm order, immutable inputs, leases, cancellation,
and attempt receipts through the common pair owner. Keep tool caches and agent
state separate between arms; identical preexisting read-only cache policy is
allowed when recorded. Pauses and report reconstruction do not replay completed
searches. Interrupted requests with uncertain remote usage retain that uncertainty
and any retry cost. No source edits, commits, goal transitions, publication,
shared learning writes, or automatic repairs occur in either navigation arm.

A settled result distinguishes search completion under the declared policy,
budget exhaustion, insufficient evidence, cancellation, and failure. Search
completion does not assert exhaustive relevance or AC fulfillment. Persist paths
and revision-bound ranges actually delivered to the arm, separately from paths
merely shortlisted. The shared observation boundary records reads directly; a
model's final narrative cannot fabricate read receipts. Control output may use
existing source citations; only this read-only experiment may normalize them
into evidence records, with no new production phase-output requirement.

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
| Discovery | Treatment query count, ordinary search/read calls when observable, delivered evidence bytes, failures, and fallbacks per phase. |
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
and `skill-bill experiments stats --name <registered-name> --format text|json`. For goal pairs, goal
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
Aggregate only comparable completed pairs, grouped by execution mode, experiment/version,
runtime/provider/model settings, budget policy, repository/input cohort, and
cache policy. Report sample counts, medians of per-pair deltas, failures, degraded
and not-exercised pairs, and every exclusion reason. Do not silently drop bad
treatment outcomes or claim significance from a fixed small sample. Quality
regressions stay visible beside speed/cost changes; do not emit an automatic
overall winner. Telemetry uses bounded identifiers and aggregates with no source,
queries, paths, or code bodies, and follows the existing outbox consent policy.
Keep ordinary goal statistics from counting the two experiment arms as two
independently delivered features or double-counting parent and child cost.

## Navigation measurement boundaries

For navigation pairs, active duration ends at settled evidence or a terminal
failure. Goal review, implementation, delivery, and validation metrics are not
applicable. Record time to first useful evidence only when an independent scorer
can identify it. Count model requests and retries, search/list/read calls, unique
files and source bytes delivered, repeated reads, setup, cache state, and available
provider usage and cost. Keep file delivery distinct from relevant evidence
selection and from correctness of an AC judgment.

Reference labels identify required source files or ranges per AC and document
annotation provenance and limitations. Report coverage of those labels and misses.
Report precision only when annotations are exhaustive enough to label extra
retrieval as irrelevant. Otherwise show additional reads without inventing a
precision value. Unlabeled cases have unavailable retrieval-quality metrics.

Keep goal and navigation aggregates separate. Group navigation comparisons by
snapshot/spec cohort, source policy, tool capabilities, models, budgets, and cache
policy. The treatment's different decision model is a declared experimental
variable, not accidental configuration drift. Never present faster failure or
lower cost from missed evidence as an unqualified improvement. Preserve failed,
exhausted, and restricted-baseline pairs in the report with exclusion reasons.
No automatic winner or default activation follows from a comparison.

## Architecture and persistence

Extend the current goal owner and phase runner through explicit ports and narrow
request values. Keep filesystem/process adapters in infrastructure, orchestration
in the engine/application owners, typed identities in their owning contract/domain
families, and composition in runtime-core. Do not implement a second phase engine.

The parent database owns pair identity, immutable settings, arm outcome receipts,
measurement ledger, and delivery status. Goal-arm databases own their workflow state. Navigation session state belongs
to the navigation runner and settles receipts through the same parent boundary.
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

1. Dynamic descriptor discovery validates names, modes, versions, capabilities, and compatibility. New goals with no `experiments` parameter or with `experiments:none` select no experiments regardless of config. A comma-separated parameter selects exactly its registered compatible names, subject to config availability; neither config nor parsing adds names. Invalid config, disabled names, and invalid combinations fail before side effects, and preflight shows the complete selection.
2. Goal pairs freeze source/spec/config inputs and execute the existing workflow twice with independent planning, writable state, persisted arm order, and enforced treatment exclusion. Dirty-source refusal preserves user files.
3. Goal arms defer external publication while preserving local review/commit identities. Only ready control work enters the existing parent delivery path, which reconciles publication idempotently and respects concurrent user changes.
4. Navigation pairs run through the public read-only command against one immutable revision and exact ACs. Each arm discovers its own evidence, and the normal-model control can use search and direct file access without following treatment traversal.
5. Enforced navigation boundaries prevent source mutation, label leakage, cross-arm reads, and snapshot escapes. Unsupported capabilities fail before launch; excluded source and restricted baselines remain visible.
6. The common owner preserves input identities, saved experiment selection, order, outcomes, and settled measurements through cancellation, crash, resume, and report reconstruction. Changed config cannot alter a resumed selection; conflicting explicit parameters fail. Completed arms never replay to regenerate a report.
7. Reliable observations record actual reads, attempts, setup, usage, and cost with durable identities. Duplicate receipt imports cannot double-count; unavailable or uncertain quantities retain reasons rather than becoming zero.
8. Text and JSON reports derive from the same durable projection and show raw metrics, defined deltas, total spend, failure/exclusion reasons, and mode-specific quality evidence. Goal and navigation cohorts remain separate.
9. Navigation scoring uses hidden independent labels after results settle, distinguishes read evidence from shortlist candidates, and reports label coverage without inventing precision or AC-fulfillment judgments.
10. Canonical runtime schemas, version/key owners, and typed producer/consumer failures cover new durable contracts. Existing ordinary records remain valid, and experiment reset/purge respects leases and ownership.
11. Local reports work with remote telemetry disabled. Optional telemetry obeys consent and excludes source, queries, paths, and secrets; experimental arms do not inflate delivered-feature counts.
12. Neither experiment mode adds implementation-phase structure, changes production audit/fix authority, or activates a treatment in ordinary runs.

## Executable scope

One subtask delivers both concrete modes, common lifecycle, selection, isolation,
measurement, and reports as one usable support capability. The two consumer specs
ship independently after this contract exists. Their dependency is documented in
specs because decomposition-manifest dependencies reference only local subtasks.

## Dependency notes

Extracted from the unstarted infrastructure subtask of SKILL-365. Implement
SKILL-366 first. SKILL-365 and SKILL-367 then consume its interfaces independently.
This spec does not require either production descriptor or a TypeSafe credential.
Preserve current goal readiness and finalization rules from the runtime, including
recent SKILL-364 work. Do not restore older rules from archived specs.

## Non-goals

- CodeGraph provisioning or queries, Jev traversal, and production fake experiments.
- A general workflow engine, arbitrary experiment plugins, factorial execution, or automatic winner selection.
- Replacing production audit, changing implementation output, or collecting required paid benchmark results during implementation.
- Remote dashboards, causal claims from a few pairs, or claims that ordinary discovery tools are inadequate.

## Validation strategy

Name and reproduce these bugs with a small number of boundary tests:

- Enabled config starts an experiment when the parameter is absent, adds unrequested names, or bypasses an explicit empty repository availability policy. A comma-separated request loses one name, launches one pair per name, or treats reordered names as a changed selection.
- A malformed config value, reserved `none` mixture, unknown name, or incompatible mode reaches launch; a config change after confirmation silently changes the selected experiments.
- A resumed pair reruns an already completed arm or changes its selected order/settings because config changed. An ordinary or legacy run acquires experiments on resume.
- Worktrees share a checkpoint ref, learning result, build output, or planning receipt and contaminate each other.
- The control accesses a forbidden treatment tool through global configuration or a shell command; an arm attempts a remote push outside the parent delivery path.
- Dirty source or changed specs reach just one arm, or snapshot refusal modifies the user's files.
- A receipt imported twice doubles cost; a crash between receipt commit and projection loses attribution.
- A crash after remote publication creates a second PR, or failed control work promotes treatment instead.
- Missing usage becomes zero; a zero control value produces a percentage; setup/retry cost disappears from the report.
- An incomplete/degraded pair appears in clean aggregates, or experimental arms count as two delivered features.

Exercise enabled config with no parameter, comma-separated selections, explicit
`none`, repository availability replacement, empty arrays, config absence, malformed
values, and resume with changed config. Use injected compatible descriptors to
prove multi-name selection without inventing installed production experiments.
Use injected config stores and record preflight/launch selections without touching
the developer's actual experiment settings.

Use real temporary Git worktrees and SQLite files with fixture agents, injected
clocks/randomness, and a recording publication port. Assert observable state and
external-write attempts. Use known values such as control 100 and treatment 80
for 20 absolute and 20 percent savings; add treatment setup 30 to produce minus
10 setup-inclusive savings. Include realistic malformed contract and legacy
ordinary-record cases. Keep stochastic model calls out of deterministic tests.

Run phase-appropriate focused tests and the repository's declared validation.
Run `./install.sh` after the skill forwarding change. Do not turn a compile-only
build phase into a test or full-check phase.

Add bounded navigation fixtures that catch label exposure, revision drift, source
mutation, and unequal evidence access. A control fixture must search and jump to
a file without traversing its parent directories through model calls. Assert
observed read receipts, partial/exhausted outcomes, unavailable label quality,
mode-separated cohorts, and reconstruction without new provider calls. Use fake
provider responses and real temporary source snapshots. Live model comparisons
are optional execution work after implementation, not acceptance prerequisites.

## References

- [Original CodeGraph consumer](../../SKILL-365-codegraph-paired-experiments/spec.md).
- [Jev navigation consumer](../../SKILL-367-jev-navigation-experiment/spec.md).
- `../../../runtime-kotlin/ARCHITECTURE.md`, `docs/code-principles.md`, and `docs/observability-policy.md`.
- `../../../skills/bill-feature/content.md` and `docs/skill-source-generation.md`.
- `../../../orchestration/contracts/decomposition-manifest-schema.yaml`.

## Next path

Run `skill-bill goal SKILL-366`. After it lands, SKILL-365 and SKILL-367 can proceed independently.
