# SKILL-367 - Jev navigation experiment

## Mode

single_spec

## Intended outcome

Determine whether Jev can find the source evidence needed for an acceptance
criterion at useful quality, cost, and latency compared with a normal reasoning
model using its existing discovery tools. Build a bounded read-only prototype
under SKILL-366's navigation experiment mode. Register its behavior as
`jev-navigation`; the shared TypeSafe client remains ordinary infrastructure.

There is no measured discovery baseline yet. Earlier Jev AC checks received
human-selected source and excerpts, so they cannot establish autonomous retrieval
quality, navigation cost, or an advantage over ordinary search. Existing normal
models can use directory listings, text search, and direct source reads. The
experiment must test whether replacing some model decisions pays for its extra
requests and navigation machinery. A result with no useful advantage is valid.

Start by capturing a normal-model baseline through the shared runner. Keep the
Jev implementation to the smallest bounded prototype needed for the comparison.
Production audit integration requires a later decision based on measured results.
This spec implements the reproducible experiment; it does not require paid runs
or a favorable benchmark outcome to satisfy implementation acceptance.

## Dependency notes

Complete [SKILL-366](../SKILL-366-experiment-support/spec.md) first. Consume its
navigation runner, descriptors, snapshot policy, isolation, durable observations,
and reports. Use the existing TypeSafe `SystemOneService` and
`SystemOneEvaluationPort` for model requests. Their availability is a prerequisite;
this spec does not duplicate the HTTP client or move it into experiment ownership.

SKILL-365 and CodeGraph are not required. This prototype uses deterministic file
and search tools with Jev decisions. Cross-spec dependencies remain documented
here rather than invented fields in the decomposition manifest.

## Experiment entry and boundaries

Run the prototype through
`skill-bill experiments run jev-navigation --repo <path> --spec <path> --revision <commit>`.
This read-only command selects its descriptor explicitly and checks configured
availability. Config enablement does not start it implicitly or add experiments
to a request. `bill-feature` parameters resolve only goal-compatible descriptors,
so this prototype remains unavailable there until a separately specified goal
integration exists.

The original spec and immutable code tree are sufficient inputs. No implementation
phase emits extra structure, AC-to-code maps, summaries, or evidence receipts.
The main model neither selects nor summarizes files for the Jev arm.

Both arms receive the same original AC text, source policy, and root listing.
The normal-model control retains its normal supported read-only tools, including
search and direct file jumps. It chooses queries and exploration independently.
Do not impose a directory walk on the control. Record any unavailable native tool
and classify a restricted baseline separately. Freeze model/provider identities,
instructions, tool versions, resource budgets, and cache policy for the pair.

Use the common read-only isolation boundary. Both arms may read eligible source,
configuration, interfaces, callers, and tests from the frozen revision. Hidden
reference labels, prior experiment outcomes, other-arm evidence, credentials,
and mutable workspace artifacts are inaccessible. The treatment cannot ask a
normal model to resolve uncertainty or silently fall back to its discovery.

This mode only retrieves evidence. It does not return binding AC fulfillment
verdicts, repair code, replace the audit phase, or change normal feature execution.
Experiment-owned structured decisions and observations stay inside the read-only
runner. They add no production implementation-output contract.

## Navigation algorithm

Begin with each original AC and the root's bounded directory/file listing.
Runtime code constructs candidate IDs, paths, basic file metadata, and permitted
previews from the snapshot. Jev receives these observations and makes typed
relevance decisions. Do not preselect an AC-specific source shortlist with a
normal model or include a hidden reference map in state.

Judge candidates independently because one AC can require many locations. Use
Noul-style relevance questions or an equivalent validated independent-decision
primitive. An exclusive Choice over all children is insufficient. Batch compatible
questions across candidates or ACs within the verified API context limits. Keep
AC identities separate and never reuse one criterion's relevance score as another's.

Runtime code owns the frontier, deterministic tie-breaking, branching, backtracking,
and budgets. Persist the traversal policy and thresholds as experiment inputs.
Several likely branches can be active, but a concurrency limit such as three is
not a limit of three relevant paths. Defer lower-ranked branches for later
exploration instead of silently discarding them. Record any budget-driven pruning.
Collapse single-child directory chains without a model call at each level.

When a file becomes a candidate, read bounded revision-bound source chunks before
counting it as delivered evidence. Record file identity, range, content hash, and
which AC requested it. Distinguish relevance scores, queued files, actual reads,
and selected evidence. Cache immutable reads across ACs within an arm and deduplicate
scheduled work. Decision caches also include AC text, model, prompt, and observation
identity. Never share treatment decisions with the control.

Allow jumps beyond the current subtree. Code can extract identifier candidates
from AC text and observed source, run bounded literal/symbol searches, and offer
result paths or reference candidates for Jev to inspect. Jev can select candidate
IDs or declared actions. It cannot generate free-text queries, shell commands,
source code, or explanations through the typed decision interface. Search hits
are candidates, not proof of a semantic call relationship. Do not build a new
compiler or require complete Kotlin reference resolution for this prototype.

A missing local match can return exploration to the deferred frontier. Report
unresolved references and excluded files. No relevant-looking name, high score,
or exhausted local directory establishes that all relevant source has been found.
The fixed policy distinguishes search completion from insufficient evidence,
budget exhaustion, cancellation, and failure. A completed search describes the
algorithm's stopping condition; only independent labels measure its known recall.

## Bounds and failure behavior

Set finite per-AC and per-pair limits for elapsed time, model requests including
retries, tokens when observable, source bytes, unique files, queued candidates,
and reference expansions. Validate them before execution and enforce limits in
runtime code. Record their effective values in every report. A finite queue must
retain an explicit count and reason for dropped candidates rather than claim an
exhaustive search. Cancellation stops owned calls and outstanding file work.

Verify the current TypeSafe API and selected Jev version during implementation.
Fit request state and questions to their documented limits and validate response
cardinality, candidate IDs, primitive types, and probability ranges. Missing,
malformed, or mismatched responses are typed failures, never a false relevance
judgment. Inherit client auth and redaction behavior; keep credentials out of
prompts, fixtures, artifacts, and telemetry. Do not read a key file into source.

Use bounded retries only for declared retryable failures. Every attempt consumes
budget and remains measurable, including uncertain remote usage after interruption.
Do not substitute another model or classify an API failure as an unfulfilled AC.
Any recovery uses the common pair owner and preserves immutable input identity.

## Baseline and evaluation

Measure ordinary navigation before interpreting prototype speed or cost. Count
all discovery requests, listings, searches, reads, repeated context, setup, and
retries through the same observable boundaries used by treatment. Include provider
input/output/cache categories and billed or explicitly estimated cost where
available. Never compare Jev request latency alone with a complete normal-model
search, or treat model price per token as total search cost.

Use a small versioned corpus of finished specs paired with frozen repository
revisions. Independent human-reviewed labels identify the files or source ranges
needed to assess each AC and explain the annotation scope. Finished status is
not evidence that every AC is fulfilled, and a commit diff alone is not a complete
relevance oracle. Labels stay outside both agent-visible snapshots. Freeze the
benchmark cohort and annotation version before comparing results.

Add focused fixtures for evidence spread across directories, a misleading file
name, an implementation behind an interface, a caller outside the selected
subtree, a deferred branch that becomes useful, and an AC with no adequate source.
Exercise oversized source and budget exhaustion. These cases test retrieval
behavior and honest partial outcomes, not production audit judgments.

Report coverage of required labeled files/ranges, misses, extra reads, selected
but unread paths, unique bytes, model/tool calls, elapsed time, cost, and failure
states per AC and pair. Do not label every unannotated file irrelevant. Report
precision only for exhaustively annotated cases. Keep quality beside cost and
latency; retrieving less evidence must not become an unqualified efficiency win.

Use repeated sequential pairs with persisted randomized order when collecting
live observations. Declare the same practical source/time/byte constraints for
both arms and disclose model-specific request limits. Report samples, provider
caches, unresolved model identities, and tool restrictions. Separate tuning cases
from held-out evaluation cases. Do not tune thresholds on held-out outcomes and
present those same cases as an independent result.

The report supports a later continue/stop decision. Before a live evaluation,
record the acceptable missed-evidence tolerance and the minimum cost or latency
improvement that would justify further work for that cohort. Do not invent those
thresholds after seeing results. If coverage falls short, discovery is already
cheap enough, or savings do not justify the added machinery, stop at the
experiment. No measured improvement is an acceptable conclusion. Production
integration or default activation requires a separate spec.

## Acceptance Criteria

1. The `jev-navigation` descriptor resolves only for navigation mode and uses SKILL-366's runner, state, and reporting. Ordinary feature/audit execution makes no Jev navigation calls.
2. Both arms start from identical original ACs and frozen source. The control can search and jump directly to files; Jev independently chooses evidence without a main-model shortlist or implementation-generated structure.
3. Independent candidate decisions can explore multiple branches, collapse single-child chains, revisit deferred paths, and follow bounded cross-directory reference candidates. Their policy and identities persist in observations.
4. Actual revision-bound source reads are distinct from candidate scores and shortlist selections. Caching and deduplication preserve per-AC attribution and cannot reuse stale or cross-arm decisions.
5. Runtime bounds cover frontier size, source access, elapsed time, model attempts, and reference expansion. Stop outcomes disclose incomplete evidence, excluded paths, pruning, and failures without claiming AC fulfillment or exhaustive discovery.
6. The TypeSafe adapter uses existing client ports, validates request/response correspondence, and treats malformed output or API errors as failures. Bounded retries consume budget and preserve observed or uncertain cost.
7. The normal-model baseline retains supported ordinary read-only discovery behavior. Tool restrictions and unavailable metrics are visible and cannot silently support claims about an unrestricted baseline.
8. Versioned benchmark fixtures and hidden independent labels support per-AC required-evidence coverage and misses. Scoring cannot leak labels into either arm, equate finished specs with fulfilled ACs, or invent precision from incomplete labels.
9. Shared reports expose total discovery work, actual reads, quality, failures, cache state, and usage/cost availability for both arms. Jev request-only timing cannot stand in for end-to-end navigation cost.
10. Documentation includes baseline collection, a bounded prototype comparison, frozen evaluation thresholds, held-out cases, and a continue/stop decision. No favorable result, paid benchmark run, or production integration is required to complete this prototype.

## Executable scope

One subtask registers and implements the bounded navigation prototype, baseline
adapter, fixtures, and reports through the shared support. It is one reviewable
experiment capability. Future production audit integration is outside this goal.

## Non-goals

- A claim that normal model discovery tools are slow, expensive, or inadequate.
- Full AC adjudication, automatic repairs, or a three-stage production audit.
- Additional implementation-phase structure or human/main-model evidence selection for Jev.
- A new TypeSafe HTTP client, mandatory CodeGraph dependency, general code indexer, or arbitrary tool synthesis.
- Production rollout, automatic winner selection, guaranteed savings, or complete semantic reference resolution.

## Validation strategy

Use a small number of boundary tests with scripted typed responses and real
snapshot files. Reproduce a correct branch lost by exclusive selection, a needed
caller outside the initial subtree, a stale cache reused for another AC, and a
shortlisted-but-unread file counted as evidence. Verify read receipts and selected
ranges rather than asserting prompt wording or internal call sequences.

Exercise malformed response correspondence, timeout with bounded retries, a
frontier/source cap, cancellation, and crash recovery without replaying completed
arms. Test hidden-label isolation and a normal control that searches directly for
a symbol. Use known reference labels to demonstrate missed evidence beside lower
cost and unavailable precision for incomplete annotations. Keep tests deterministic
and free of paid API calls or real credentials.

Run focused contract/navigation tests and declared quality validation in their
owning phases. Optional live runs use configured credentials after implementation
and retain raw local measurements through the shared consent policy. They produce
observations for a later product decision, not CI pass/fail based on stochastic
model choices. Preserve compile-only build semantics.

## References

- [Shared experiment support](../SKILL-366-experiment-support/spec.md).
- `.agents/skills/typesafe-ai/SKILL.md`, TypeSafe integration guidance.
- [Hierarchical classification](https://docs.typesafe.ai/cookbooks/hierarchical_classification).
- [Typed function selection](https://docs.typesafe.ai/cookbooks/function_calling).
- [Noul](https://docs.typesafe.ai/primitives/noul) and [fan-out](https://docs.typesafe.ai/patterns/fan-out).
- [Models](https://docs.typesafe.ai/models) and [API](https://docs.typesafe.ai/api). Verify live limits at implementation time.
- `runtime-kotlin/ARCHITECTURE.md` and `docs/observability-policy.md`.

## Next path

After SKILL-366 and the existing TypeSafe client are available, run
`skill-bill goal SKILL-367`. Collect live baseline and prototype observations
only after the runner exists. Use their results to decide whether another spec
for production integration is justified.
