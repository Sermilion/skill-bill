# SKILL-367 subtask 1 - Autonomous navigation comparison

## Scope

Implement the bounded read-only prototype and normal-model baseline described by
the parent. All its behavior sections are normative. Own the `jev-navigation`
descriptor, deterministic navigation tools, Jev decision policy and adapter,
benchmark fixtures, scoring integration, and documentation. Use SKILL-366's pair
owner and report projection, and the existing TypeSafe client.

Start with baseline observation wiring and build only the prototype needed for
the comparison. Do not wire it into production audit or require new outputs from
implementation. Completing this subtask means the experiment can run reproducibly;
it does not mean Jev has demonstrated an advantage.


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

## Dependency notes

Complete [SKILL-366](../SKILL-366-experiment-support/spec.md) first. Consume its
navigation runner, descriptors, snapshot policy, isolation, durable observations,
and reports. Use the existing TypeSafe `SystemOneService` and
`SystemOneEvaluationPort` for model requests. Their availability is a prerequisite;
this spec does not duplicate the HTTP client or move it into experiment ownership.

SKILL-365 and CodeGraph are not required. This prototype uses deterministic file
and search tools with Jev decisions. Cross-spec dependencies remain documented
here rather than invented fields in the decomposition manifest.

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

## Next path

After SKILL-366 and the existing TypeSafe client are available, run
`skill-bill goal SKILL-367`. Collect live baseline and prototype observations
only after the runner exists. Use their results to decide whether another spec
for production integration is justified.
