# SKILL-366 subtask 1 - Experiment execution and reports

## Scope

Implement the complete parent spec as one support capability. Own dynamic
selection through machine/repository config and per-run parameters, goal-pair and
navigation-pair runners, enforced isolation, durable
recovery, observations, and report/stat commands. Wire both modes to the same
pair owner and exercise them through injected descriptors and fixture agents.
All behavioral sections of the parent are normative for this subtask.

Preserve the existing goal phase engine and production implementation output.
Provide consumer-owned setup, treatment, observation, and scoring ports for
SKILL-365 and SKILL-367. Keep their implementations out of this commit.


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

## Next path

Complete SKILL-366 through the normal goal flow. SKILL-365 and SKILL-367 are separate prepared goals, not automatically launched child subtasks.
