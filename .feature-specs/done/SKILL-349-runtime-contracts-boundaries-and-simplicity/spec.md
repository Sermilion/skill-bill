# SKILL-349 - runtime-contracts-boundaries-and-simplicity

## Mode

decomposed

## Intended outcome

Make runtime-contracts reject malformed governed input without losing evidence or accepted values, and remove redundant shared helpers while preserving the existing module graph and successful wire behavior.

## Scope

The investigation covers every production Kotlin file in runtime-contracts and its boundary consumers. See [investigation.md](investigation.md) for eight findings, SOLID and architecture assessment, rejected refactors, public engineering references, and scope limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001 and F-002 | Exact canonical values and explicit field validation | 1 |
| F-003 and F-004 | Distinguishable JSON failures and lossless supported values | 1 |
| F-005 through F-008 | Correct clock semantics, one mapper and failure owner, unused code removed | 2 |

Two subtasks can ship independently. The first changes input interpretation and caller failure handling. The second preserves successful payload behavior while simplifying shared implementation. Neither adds an unused intermediate layer for the other. Tests and documentation stay with each change.

Prepared in local mode on 2026-09-16. SKILL-349 follows SKILL-348, the highest existing local spec key and matching branch key found during preparation. Baseline HEAD is `97c48855dae9777e95c172502b6a1687e25f0d79`. This bundle prepares work only; both subtasks remain pending.

## Acceptance Criteria

1. Canonical cap and issue-key readers reject fractional, non-finite, and out-of-range numeric values through typed schema errors. Discovery exclusion parsing rejects schema-forbidden duplicate entries. Supported values retain their exact meaning.
2. Planning and scaffold decoders distinguish absent fields from present wrong-type fields. Malformed collections, descriptions, and content bodies fail before mutation while documented omission, null, blank, aliases, and non-decomposition behavior remain compatible.
3. Array parse failure is distinguishable from a valid empty array. Update checks accept whitespace-formatted empty arrays, and retained telemetry or metadata fallbacks emit bounded diagnostics without claiming malformed data was absent.
4. JSON conversion preserves supported integer values and exact BigInteger/BigDecimal encoding. Unsupported keys or values fail explicitly instead of being dropped or stringified. Existing ordinary scalar, null, collection, and open payload behavior remains compatible for supported values.
5. The ambient clock remains live after zone changes and retains current millisecond precision. Existing injected Clock use and the domain ambient-effect restrictions remain intact.
6. Workflow payloads have one application-owned mapping pass. Existing field names, ordering, null-mode omission, continuation mode, and extra-field precedence remain unchanged.
7. One contract-owned phase-output failure enum defines wire values and coarse classification. Domain and exceptions consume it without an upward dependency or wire/error behavior change.
8. Remove the unreachable diagnostic binding and the unused functions named in F-008. Keep used wrappers, schema constants, typed errors, parity tests, and required recovery behavior.
9. Documentation describes actual classpath resource ownership and check coverage. Existing architecture guards retain their protection without broader baselines or suppressions.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles and docs/code-principles.md. Keep the current modules, the composition root, current serialization libraries, and manifest-driven extension behavior.
- Canonical schemas own wire shape. Add required representable bounds there before matching parser restrictions. If accepted governed schema semantics change, follow the repository's version, parity, typed-error, and recovery rules.
- Keep the lightweight packaged-document design. No generic schema engine, parser registry, universal result framework, new DI graph, or port per helper.
- Preserve successful CLI/MCP shapes and supported payload values. Default only when the contract permits it. Retained recovery belongs to the caller and emits a bounded payload-free record; malformed payload text must not enter diagnostics.
- Do not close pack-authored extension vocabularies or replace every raw map with a DTO. Type closed state where the findings justify it. Reuse existing wire-key owners.
- Keep database failures distinguishable from terminal domain failures. Preserve cancellation and primary error identity at affected catches. Do not remove required schema validation or legacy recovery.
- Re-read the current owning documents before implementation. Unrelated build and CI work in the shared checkout is outside this spec.

## Non-goals

- A rewrite, more Gradle modules, microservices, new persistence, or certification against private company standards.
- Moving all packaged loaders into infrastructure, replacing every primitive with a wrapper, or generating every DTO and key from schemas.
- A global migration of every tolerant JSON object probe, new product capabilities, an unrelated vulnerability audit, or a benchmark project.
- Removing test coverage to improve line counts or repairing unused functions that should be deleted.

## Validation strategy

Exercise the actual parsers and caller boundaries identified in the investigation. Cover valid and invalid inputs, exact values, error types, and observable wire output. Use the current canonical schemas as the independent validation authority. Run module tests, affected consumer suites, failure conformance tests, and the existing architecture guards. Use the governed Kotlin quality gate during implementation and bill-unit-test-value-check for changed tests. Preparation test results are evidence about the baseline, not future review clearance.

## Next path

Run `skill-bill goal SKILL-349` when implementation is intended. The prepared manifest is the goal runner's input.
