# SKILL-365 subtask 1 - Paired experiment infrastructure and reports

## Scope

Implement the reusable paired-run mechanism described by the parent spec. Own
experiment selection, immutable pair input, two isolated executions of the existing
goal workflow, recovery, deferred publication, durable measurements, and report
projection. Complete skill token forwarding and runtime entry parity in this
commit. Exercise the full mechanism through an injected test descriptor and agent;
do not expose a production no-op experiment.

## Required behavior

The parent spec's user contract, paired execution, isolation and delivery,
measurement, and persistence sections are normative for this subtask. CodeGraph
installation and queries belong to subtask 2. Provide the consumer-owned treatment
capability and setup/query measurement seams that subtask 2 will implement.

Add a validated orchestration-owned experiment descriptor catalog and registry.
Keep experiment membership out of skill prose parsers and command builders.
Unknown or malformed selections fail at the entry boundary. Every successful
resolution records descriptor versions and required capabilities. The test-only
descriptor enters through the same injected catalog interface without appearing
in installed production discovery.

Extend the current goal owner through explicit request and policy values. Do not
duplicate the phase runner. Arm mode executes all existing local planning,
implementation, review, and routed quality work, including local review commits,
but defers external publication to the parent. Phase routing must distinguish
deferred publication from a completed push. Neither arm may recurse into pair
creation. Both arms retain one local commit per completed subtask.

The parent pair record owns configuration, arm order, leases, receipt settlement,
measurement attribution, report status, and a fixed control-delivery policy. Arm
databases own their workflows. Identity-checked idempotent receipt transfer handles
the lack of a shared transaction. Namespace checkpoint refs and operational files
that Git worktrees would otherwise share. Resume, reset, status, and purge must
resolve the same pair identities, not choose the latest row by timestamp.

Snapshot the prepared bundle even when untracked and reject other dirty source.
Use independent model sessions, writable build/cache state, and learning stores.
Freeze effective model, guidance, pack, add-on, route, and budget settings. Enforce
tool and publication restrictions through the launcher capability boundary; deny
unsupported harnesses before creating a pair. A plain PATH edit or a prompt asking
the agent not to publish is insufficient evidence of isolation.

Measurements reuse reliable existing counters and capture additional provider
usage only through strategies that can prove its meaning. Deduplicate by durable
attempt/event identities and include failures and retries. Missing measurements
keep the existing availability vocabulary. Avoid introducing a new parallel cost
ledger with a different accounting authority.

Implement `skill-bill experiments report <pair-id> --format text|json` and
`skill-bill experiments stats --name <registered-name> --format text|json`.
The report projection contains both arms, measured usage and quality observations,
comparability/exclusion reasons, setup-inclusive and execution-only differences,
and total experiment spend. Parent status and the existing final relay expose the
report reference. Reports never mutate workflows or trigger new agent calls.
Include zero-denominator handling, negative savings, unavailable metrics, and
cohort grouping. Separate experimental arm accounting from delivered goal counts.

The user sees one existing confirmation gate. Show two runs, the selected
experiment, the fixed control delivery policy, and extra work/cost. Preflight
cannot provision or mutate experiment state. The ordinary path must not enumerate
tools, create worktrees, or incur experiment setup when experiments are absent.

## Acceptance Criteria

1. Skill forwarding, CLI, and governed goal entry parsing accept one validated comma-separated selection; reject duplicates, empty/unknown names, and conflicting resume requests before side effects. New ordinary goals and legacy records retain ordinary execution.
2. A deterministic injected descriptor drives two real isolated workflow executions from identical source/spec/config snapshots, with independently generated plans and durable arm order. Untracked prepared specs are captured; other dirty source is refused without modification.
3. Launcher capability checks enforce treatment exclusion in control, prevent cross-arm evidence reads and external publication, and reject unsupported isolation before launching either arm.
4. Pause, cancellation, crash, and lease takeover retain completed arm results and settled measurements. An interrupted arm alone resumes; a shared prerequisite failure cannot masquerade as a treatment outcome.
5. Local arm finalization preserves review and commit identities while deferring push/PR work. Only a completed ready control result can enter parent delivery, and retries cannot publish twice or replace concurrent user work.
6. Attempt-level measurements and receipts persist atomically within their owning database, reconcile idempotently across databases, and preserve failure and availability states. Projection failure does not cause execution replay.
7. Text and JSON reports agree on raw metrics, savings definitions, actual total cost, quality evidence, delivery status, and invalid/incomplete comparisons. Statistics disclose excluded outcomes and do not inflate delivered-feature counts.
8. New runtime contracts have canonical schemas, version/key owners, parity and rejection coverage, and typed parse failures. Local report generation works with remote telemetry disabled and emits no raw source or query content to telemetry.
9. Ordinary feature execution retains its existing phase routes, one-gate behavior, and publication rules. All changed authored skills render and install through the governed source path.

## Dependency notes

This is the first subtask. It requires no CodeGraph installation and introduces
no production experiment name. The pair executor, receipt transfer, and report
commands are complete and exercised through an injected fixture. Subtask 2 binds
the first production descriptor and consumes these settled interfaces.

Inspect the current goal continuation, readiness, and publication code before
changing it. Recent work includes SKILL-364 readiness behavior. Preserve whichever
rules the current runtime owns rather than reintroducing older finalization rules
from archived specs or the installed spec template.

## Non-goals

- CodeGraph provisioning, parsing its output, or adding a fake user-facing experiment.
- A second feature-task engine, provider identity branches, or mutable experiment selection on resume.
- Automatic winner selection, factorial runs, new remote dashboards, or statistical significance claims.
- Replacing existing quality gates, weakening review identity, or implementing a general plugin loader.

## Validation strategy

Name and reproduce these bugs with a small number of boundary tests:

- A resumed pair reruns an already completed arm or changes its selected order/settings.
- Worktrees share a checkpoint ref, learning result, build output, or planning receipt and contaminate each other.
- The control accesses a forbidden treatment tool through global configuration or a shell command; an arm attempts a remote push outside the parent delivery path.
- Dirty source or changed specs reach just one arm, or snapshot refusal modifies the user's files.
- A receipt imported twice doubles cost; a crash between receipt commit and projection loses attribution.
- A crash after remote publication creates a second PR, or failed control work promotes treatment instead.
- Missing usage becomes zero; a zero control value produces a percentage; setup/retry cost disappears from the report.
- An incomplete/degraded pair appears in clean aggregates, or experimental arms count as two delivered features.

Use real temporary Git worktrees and SQLite files with fixture agents, injected
clocks/randomness, and a recording publication port. Assert observable state and
external-write attempts. Use known values such as control 100 and treatment 80
for 20 absolute and 20 percent savings; add treatment setup 30 to produce minus
10 setup-inclusive savings. Include realistic malformed contract and legacy
ordinary-record cases. Keep stochastic model calls out of deterministic tests.

Run phase-appropriate focused tests and the repository's declared validation.
Run `./install.sh` after the skill forwarding change. Do not turn a compile-only
build phase into a test or full-check phase.

## Next path

Proceed to subtask 2 after this subtask completes. The goal runtime owns the
transition; do not launch CodeGraph experiments during this subtask.
