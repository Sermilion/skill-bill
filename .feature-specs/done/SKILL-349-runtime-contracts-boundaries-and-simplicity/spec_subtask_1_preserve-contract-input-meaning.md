# SKILL-349 subtask 1 - Preserve contract input meaning

## Scope

Resolve F-001 through F-004. Own runtime-contracts canonical readers, JSON conversion, decomposition planning parsing, and scaffold wire helpers. Update their directly affected application, CLI/MCP, and SQLite callers in the same commit. Include the associated canonical schema constraints, error/key owners, tests, and architecture documentation.

Use the smallest implementation that gives callers exact values or a typed failure. Retain lightweight packaged-document parsing. Keep the current libraries. Add one strict array entry point using the existing JSON error approach, then migrate current array consumers so failure policy belongs to the consumer. Preserve optional object probing where intentional.

## Acceptance Criteria

1. Cap and issue-key parsing rejects `1.9`, overflow such as `4294967297` for Int-backed fields, non-finite values, and invalid positive bounds. Long-backed boundary bytes use the declared Long range without an Int conversion. Canonical schemas and consumed numeric limits agree.
2. Discovery exclusion documents reject duplicate roots and directory names as their schema requires. Missing resources, wrong versions, malformed roots, and unknown governed keys retain typed failures. YAML parse catches do not convert cancellation or VM failures into schema errors.
3. A present wrong-type `stack_branches`, scaffold description, or content body raises the existing typed field error before writing. Preserve contract-defined omitted/null/blank cases, planning aliases, and non-decomposition handling. Consumed governed wire fields reference owning constants.
4. A strict array boundary distinguishes malformed text, a wrong root, and a valid empty array. `[ ]` reaches the same empty-release behavior as `[]` in UpdateCheckService. Telemetry parsing does not emit false malformed-array warnings for valid whitespace.
5. Current array consumers and the named metadata/telemetry consumers in F-003 explicitly fail or record degradation for malformed content. They do not classify corrupt agent/model arrays as absent durable evidence. Legitimate missing data keeps its existing meaning.
6. Encoding `BigInteger("9007199254740993")` preserves that integer. Decoding integers above Long preserves the value in an exact supported representation or raises an explicit typed range failure before conversion; it never rounds into a different accepted number. Exact BigDecimal encoding avoids a Double intermediate.
7. Non-string map keys and unsupported value types fail explicitly at conversion instead of silently deleting entries or calling arbitrary `toString`. Callers that intentionally emit text convert it before reaching the codec. Ordinary scalar, null, map, array, and supported floating-point behavior remains compatible.
8. Tests exercise the actual parser and caller seams for the listed regressions. Documentation names the three packaged resources, lightweight validation ownership, numeric representation, and failure-policy boundaries without claiming universal purity or schema coverage.

## Non-goals

No generic parsing DSL, global logger binding, serialization library replacement, new dependency cycle, blanket DTO migration, or global change to tolerant object probing. Do not repair the unused scaffold requireInt helper. Remove it with subtask 2.

## Dependency notes

No prerequisite subtask. This commit must include every consumer change needed by its new failure behavior. It can ship without the mapper, clock, and dead-code cleanup in subtask 2.

## Validation strategy

Before authoring each test, name its concrete regression. Use canonical invalid YAML fixtures, the real planning/scaffold request entry points, JsonCodec round trips, and UpdateCheckService with an injected transport response. Exercise telemetry/metadata degradation with malformed content and a valid whitespace-formatted empty array. Assert bounded error metadata without pinning incidental prose. Keep existing acceptance and rejection tests. Run runtime-contracts and affected consumer tests, schema parity, typed parse-boundary and wire-vocabulary checks, then the governed implementation quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_2_simplify-contract-implementation.md` through the goal runtime after this subtask settles.
