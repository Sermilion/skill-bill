# SKILL-365 - CodeGraph paired experiments

## Mode

single_spec

## Intended outcome

Let a user run `/bill-feature <issue-key> experiments:codegraph` to perform the
same prepared feature twice, once with CodeGraph disabled and once enabled.
Measure the difference with SKILL-366's goal-pair runner and reports. Both arms
start from the same source/spec/config snapshot and perform their own planning,
implementation, and existing quality phases.

The shared experiment support formerly assigned to subtask 1 now belongs to
SKILL-366. This goal contains one executable subtask for the managed CodeGraph
treatment. CodeGraph remains an optional pinned external executable. Do not copy
or fork its implementation, and do not activate it in ordinary feature runs.

## Dependency notes

Complete [SKILL-366](../SKILL-366-experiment-support/spec.md) before starting this
goal. Its selection, immutable input, lifecycle, isolation, delivery, measurement,
reporting, persistence, and recovery contracts are prerequisites. Consume those
interfaces without maintaining another pair runner or measurement ledger.

SKILL-367 is an independent consumer of the same support and is not a prerequisite.
`codegraph` supports goal pairs. `jev-navigation` supports navigation pairs; reject
attempts to combine modes through the shared descriptor compatibility checks.
The dependency on SKILL-366 is cross-spec documentation, not a local subtask ID.

## User and execution contract

Select CodeGraph for a new run with `experiments:codegraph` on `bill-feature`.
A compatible comma-separated list may include CodeGraph with other registered
goal treatments. Config can enable or disable its availability through SKILL-366's
policy, but config alone never selects it. Omitting the parameter or passing
`experiments:none` runs ordinary feature work. Resumes retain the saved selection.

Use the shared `experiments:<names>` input and single confirmation gate. Preflight
is read-only. The gate discloses two executions, fixed control delivery, optional
CodeGraph provisioning, and extra cost. Provision only after confirmation and
before either arm starts. Include both arm costs and treatment setup in reports.

The experiment covers the whole prepared goal, including all subtasks. Both arms
use independent model sessions, plans, worktrees, writable caches, learning state,
and revision-bound evidence. Enforce CodeGraph exclusion in control through the
launcher boundary, including inherited servers and alternate shell invocations.
Refuse unsupported isolation before launch and invalidate observed contamination.

Both arms retain local review commits and defer publication to the shared parent.
Only ready control work can enter normal delivery; treatment never becomes the
winner automatically. Shared pause/resume and idempotent receipts retain failed
arms and do not repeat completed work. Changing the descriptor, binary version,
inputs, or model settings requires a new pair.


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

## Measurement and reporting

Use SKILL-366's local ledger, text/JSON report, and cohort statistics. Add actual
install, verification, index, sync, query, source-delivery, and degradation
observations through those interfaces. Separate enabled, queried, evidence-consumed,
degraded, and not-exercised states. Record ordinary discovery counters only when
the launcher can observe them. Missing usage remains unavailable with a reason.

Compare the same ACs and actual quality checks for both implementations. Review
finding counts and arm-authored test counts are observations, not correctness
scores. Show execution-only and setup-inclusive deltas, actual total pair spend,
cache policy, and failed/incomplete outcomes. Degraded and unused treatments
cannot claim clean graph gains. Local reports work with remote telemetry disabled.

Inspect a pair with `skill-bill experiments report <pair-id> --format text|json`
and comparable cohorts with
`skill-bill experiments stats --name codegraph --format text|json`.
No speed or cost improvement is assumed.

## Acceptance Criteria

1. Dynamic production discovery registers `codegraph` for goal pairs without excluding other registered experiments. A `bill-feature` parameter selects it through SKILL-366's resolver and gate, alone or in a compatible comma-separated combination. Config governs availability; omission or `experiments:none` performs no graph setup even when config enables CodeGraph.
2. Optional install and post-confirmation provisioning use a pinned verified asset or explicit validated override. Cached offline reuse, corrupt downloads, unsupported hosts, concurrent installation, active-version retention, and owned cleanup have defined observable outcomes.
3. A feature invocation produces a control with CodeGraph inaccessible and a treatment with usable bounded graph retrieval, while both retain the original source/spec/settings snapshot and existing quality authority.
4. Treatment discovery and implementation can consume graph candidates, and review receives only revision-bound authorized evidence through the broker. Neither source races nor out-of-scope graph paths bypass evidence validation.
5. Retrieval failure, timeout, output caps, and ordinary-discovery fallback emit attributable records and preserve real costs. Degraded or not-exercised treatment cannot appear as a clean CodeGraph gain.
6. The pair report includes installation/index/sync/query overhead, actual treatment use, available token/cost/discovery metrics, and comparable quality evidence. Ordinary runs incur no graph install/index/query work.
7. A pinned-binary integration fixture demonstrates cross-file Kotlin retrieval and explicitly records unresolved constructs. An automated full pair with fixture agents proves treatment-only invocation and the resulting comparison report without paid model access.
8. Documentation and installed skill output distinguish config availability from explicit per-run selection, show comma-separated combinations, and state that omission means none for new runs. Selected experiments use two executions with fixed control delivery; docs explain resume and inspection of both results. No global agent configuration or upstream telemetry preference is changed by managed invocation.

## Executable scope

One managed CodeGraph treatment subtask registers the descriptor, installs the
optional dependency, provides bounded retrieval, and completes its reports and
documentation through SKILL-366.

## Non-goals

- Vendoring the CodeGraph source, embedding its Node library into the JVM, or running its interactive agent installer.
- Mandatory installation, automatic dependency upgrades, or experiment activation without an explicit persisted per-run selection.
- Substituting graph reachability for compiler correctness, complete test selection, or authorization to read broader evidence.
- Implementing another treatment, an IDE dashboard, or any guaranteed speed/cost improvement.

## Validation strategy

Name and reproduce these bugs before adding coverage:

- A checksum mismatch or interrupted install leaves an executable that later launches, or a concurrent upgrade changes a resumable pair's version.
- CodeGraph runs in the control arm through an inherited server, shell, or index path, or runs when the parameter is absent or `none` despite enabled config. A compatible multi-name selection drops CodeGraph or creates separate pairs instead of one pair.
- A changed/deleted Kotlin file leaves a graph response that is accepted as current evidence.
- A graph result reaches an unauthorized path, exceeds broker budgets, or substitutes working-tree source for the reviewed commit.
- A failed query or unused graph still produces a clean claimed gain, or cold indexing is absent from setup-inclusive cost.
- Process cancellation leaves an owned child/index lock alive, or cleanup removes another pair's state.

Use a fake distribution server/process for fault and timeout tests and a bounded
real-binary integration fixture for CLI parsing and Kotlin retrieval. Include an
extension function, overloaded names, an injected interface with an implementation,
and a known cross-file caller. Assert only supported observed relationships and
document limitations; do not define complete call-graph recall as acceptance.

Execute an end-to-end fixture pair through the public entry path with actual
CodeGraph calls in treatment, control attempts denied, deterministic usage
receipts, and a recorded publication port. Check that both arms' receipts survive
resume and produce the expected report. A separately authorized live feature pair
may supply observational benchmark data, but is not required for deterministic
acceptance and must never publish two implementations.

Run the appropriate focused integration/contract tests and declared quality
validation in their owning phases. Run `./install.sh` after authored skills or
installation/rendering behavior changes. Preserve compile-only build semantics.

## References

- `skills/bill-feature/content.md`, token forwarding and the one-gate launch contract.
- `runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/goalrunner/preflight/GoalPreflightService.kt`, read-only preflight.
- `runtime-kotlin/runtime-ports/src/main/kotlin/skillbill/ports/review/evidence/ReviewEvidenceBroker.kt`, review evidence authority.
- `runtime-kotlin/ARCHITECTURE.md`, design principles and durable state ownership.
- `docs/code-principles.md`, `docs/observability-policy.md`, and `docs/skill-source-generation.md`.
- [CodeGraph CLI and integration documentation](https://github.com/colbymchenry/codegraph#cli-reference). Verify the selected release's actual CLI contract before binding the adapter.
- [Shared experiment support](../SKILL-366-experiment-support/spec.md).

## Next path

After SKILL-366 lands, run `skill-bill goal SKILL-365`. The
`experiments:codegraph` option applies to later prepared feature work; do not
pass the unimplemented option while implementing this goal.
