# SKILL-365 subtask 2 - Managed CodeGraph treatment

## Scope

Register `codegraph` as the sole production experiment and make
`/bill-feature <issue-key> experiments:codegraph` execute the complete paired
workflow. Add managed optional installation, bounded graph retrieval, treatment
capability enforcement, and CodeGraph-specific measurements. Use the first
subtask's runtime, persistence, and comparison interfaces.

## Required behavior

The parent spec's CodeGraph integration section is normative. The control performs
the existing discovery path with CodeGraph inaccessible; the treatment gains graph
retrieval during supported discovery/planning/implementation/repair phases and
through the evidence broker during review. Both retain the same configured
acceptance, review, and quality requirements. CodeGraph is not a test selector
that can remove required checks and is not a source of review clearance.

Choose and verify an exact upstream release during implementation. Record its
version, supported platform assets, expected digests, license, and CLI/output
compatibility in one governed dependency declaration. Validate this declaration
through its owning runtime schema before use. Skill Bill installs the upstream
self-contained executable as an optional component under a versioned private
tools directory. A corrupt or unsupported asset is a typed failure. Atomic install
and locking handle concurrent invocations; resume retains its original version.
Managed invocations disable CodeGraph's own telemetry and cannot self-update.

Feature preflight inspects availability without downloading or writing. If setup
is needed, the existing confirmation includes it. Launch provisions after that
confirmation, before running either arm. Record actual download/verification/
installation cost as treatment setup even though the setup precedes arm order.
Offline cached reuse works; missing assets fail explicitly. A documented local
override requires explicit configuration, version/identity validation, and report
provenance. Avoid implicit PATH discovery or rewriting global agent configuration.

Implement a narrow process adapter for the release's structured CLI query
surface. Prefer candidate symbol/path relationships and serve authoritative code
through Skill Bill's existing source/evidence readers. Do not forward an unbounded
`explore` text dump or expose an unrestricted upstream MCP server to reviewers.
Parse external output through typed contracts, reject malformed/oversized output,
and enforce time, byte, and call budgets. Use argv, not shell interpolation.

Index the treatment worktree alone. Index creation and synchronization are owned
resources with bounded cancellation/cleanup. Record both initial build and
incremental work. Check source/index identity before and after retrieval, including
dirty implementation edits, deleted files, and committed review revisions. Refuse
or retry within a bounded policy when concurrent edits invalidate evidence;
every retry is measured. Review expansion still requires authorization and consumes
the existing evidence budget. Keep platform routing and heading-only boundary
memory discovery unchanged.

Missing symbols are ordinary query results, not proof that there are no callers.
Known unavailable/stale retrieval may record a degradation and use ordinary
discovery where that workflow permits it. Scope/identity failures remain loud.
Retain any degraded treatment's outcome and cost, but exclude it from clean
comparison aggregates. Distinguish enabled, queried, evidence-consumed, degraded,
and not-exercised treatment states without inventing savings from tool availability.

Wire setup/query counters and receipts into the first subtask's measurement owner.
The same report shows phase usage, graph overhead, actual graph consumption,
ordinary discovery counts when observable, quality evidence, and total pair cost.
Document what each launcher can measure. Refuse unsupported isolation rather than
claiming an uncontaminated control based only on absent MCP registration.

Document optional installation, the public feature invocation, resume behavior,
fixed control delivery, report/stat commands, supported harness/host capabilities,
fallback semantics, and the cost of running both arms. The example command uses
an ordinary prepared feature key, not SKILL-365 while the option is unimplemented.
Retain the default single-run path and do not activate CodeGraph from global state.

## Acceptance Criteria

1. Dynamic production discovery exposes only `codegraph`; its declared capability, phase, dependency, and compatibility requirements resolve through the first subtask's registry and gate.
2. Optional install and post-confirmation provisioning use a pinned verified asset or explicit validated override. Cached offline reuse, corrupt downloads, unsupported hosts, concurrent installation, active-version retention, and owned cleanup have defined observable outcomes.
3. A feature invocation produces a control with CodeGraph inaccessible and a treatment with usable bounded graph retrieval, while both retain the original source/spec/settings snapshot and existing quality authority.
4. Treatment discovery and implementation can consume graph candidates, and review receives only revision-bound authorized evidence through the broker. Neither source races nor out-of-scope graph paths bypass evidence validation.
5. Retrieval failure, timeout, output caps, and ordinary-discovery fallback emit attributable records and preserve real costs. Degraded or not-exercised treatment cannot appear as a clean CodeGraph gain.
6. The pair report includes installation/index/sync/query overhead, actual treatment use, available token/cost/discovery metrics, and comparable quality evidence. Ordinary runs incur no graph install/index/query work.
7. A pinned-binary integration fixture demonstrates cross-file Kotlin retrieval and explicitly records unresolved constructs. An automated full pair with fixture agents proves treatment-only invocation and the resulting comparison report without paid model access.
8. Documentation and installed skill output describe `experiments:codegraph` as two executions with fixed control delivery and explain how to inspect both results. No global agent configuration or upstream telemetry preference is changed by managed invocation.

## Dependency notes

Requires subtask 1. Use its pair/arm identities, descriptor loader, capability
policies, local measurement ledger, and report projections. Do not add a separate
CodeGraph experiment state machine, usage database, or reporting implementation.

Upstream CLI schemas and binaries are external dependencies. Inspect the exact
pinned release before choosing commands, and surface unsupported capability rather
than assuming documentation for a different version describes its behavior.

## Non-goals

- Vendoring the CodeGraph source, embedding its Node library into the JVM, or running its interactive agent installer.
- Mandatory installation, automatic dependency upgrades, or experiment activation without an explicit persisted selection.
- Substituting graph reachability for compiler correctness, complete test selection, or authorization to read broader evidence.
- A second production experiment, an IDE dashboard, or any guaranteed speed/cost improvement.

## Validation strategy

Name and reproduce these bugs before adding coverage:

- A checksum mismatch or interrupted install leaves an executable that later launches, or a concurrent upgrade changes a resumable pair's version.
- CodeGraph runs in the control arm through an inherited server, shell, or index path, or runs at all in ordinary feature mode.
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

## Next path

Complete SKILL-365 through the normal goal flow. Later feature work may opt into
`/bill-feature <issue-key> experiments:codegraph`; inspect its pair with
`skill-bill experiments report <pair-id> --format text`.
