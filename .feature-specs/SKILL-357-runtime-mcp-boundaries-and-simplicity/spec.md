# SKILL-357 - runtime-mcp boundaries and simplicity

Issue key: SKILL-357
Mode: decomposed (two dependency-ordered subtasks)
Investigation: [investigation.md](investigation.md)

## Intended outcome

`runtime-mcp` stays the thin MCP entry adapter it already is in direction, and becomes one in shape: the process composes its dependency graph once, each tool is declared once with its name, description, schema, and handler, the advertised input schema and the validating schema are one document, the governed evidence bridge uses the bounded socket reader that already exists, wire keys come from `runtime-contracts`, every degradation is recorded or refused, and everything but `main` is `internal`.

## Findings to subtasks

| Finding | Subtask |
| --- | --- |
| F-001 one tool, four tables | 1 |
| F-003 composition root per call | 1 |
| F-004 dead bounded reader, two framers | 1 |
| F-005 wire vocabulary in place | 1 |
| F-006 silent coercion, dropped input, swallowed capture, untyped input errors | 1 |
| F-007 constant reported as measurement, adapter-owned defaults | 1 |
| F-008 forwarding layers, count-split bags, aliases | 1 |
| F-009 public surface | 1 |
| F-010 test coupling and root-package squat (module-owned part) | 1 |
| F-002 two input schemas for one seam | 2 |

## Acceptance Criteria

1. `Main.kt` builds one `RuntimeComponent` and one `McpComponent` per process and hands them to the server; no production code path creates a component per tool call, and `services(...)`, `mcpClock(...)`, `McpRuntimeContext`, and every `= McpRuntimeContext()` default parameter are gone.
2. Review text reaches `ReviewService.previewImport` and `ReviewService.importReview` as an argument, not through `RuntimeContext.stdinText`.
3. Every MCP tool is declared exactly once as a value carrying its name, description, input schema, and handler in one ordered list; `tools/list` and dispatch both read that list; no tool name appears as a string literal outside its declaration and the YAML schema.
4. One JSON-RPC framer in `core` serves both the stdio server and the governed evidence bridge; parse errors answer `-32700` in both modes; `ping` answers an empty result in both modes; the protocol version is one constant.
5. The governed evidence bridge forwards through `GovernedReviewEvidenceConnection` with its byte caps; the bridge's private connection, socket, and initialize copies and `GovernedReviewEvidenceInitialization.kt` are deleted; a test proves a response frame over `RESPONSE_FRAME_BYTES` raises `GovernedReviewEvidenceTransportError`.
6. Every argument key read and every output key written in `src/main` is a `runtime-contracts` constant, except JSON-RPC and JSON-Schema structural keys which live in one `core` object; `WireVocabularyGovernedSeamInventory` registers every `skillbill.mcp` file that reads or writes tool payloads; `WireVocabularyArchitectureTest` passes.
7. A missing or mistyped argument for a key the tool declares raises a typed error under `ShellContentContractException` naming the tool and key; no argument is silently defaulted or silently discarded; the `quality_check_finished` input schema does not advertise keys the runtime owns; a failed exception capture emits one `RuntimeDiagnostics` record; `src/main` contains no `java.util.logging` import.
8. Scaffold telemetry payloads carry no fabricated duration; the adapter injects no policy default that an owning enum or definition already provides.
9. Every top-level declaration in `src/main` except `main` is `internal` or `private`.
10. The advertised `inputSchema` of every tool is projected from that tool's branch in `orchestration/contracts/telemetry-event-schema.yaml`; `McpInputSchemas.kt`, `McpInputSchemaPrimitives.kt`, the Kotlin `inputSchemas` table, the unknown-argument walker, `TelemetryEventInputSchemaParityTest`, and `FailureDispositionSchemaAdvertisementTest` are deleted; a golden `tools/list` fixture pins the advertised surface.
11. Every enum the Kotlin schemas carried is a constraint in the YAML branch, and a repo-contract test pins each YAML enum to the owning Kotlin `wireValue` list or definition; no production file in `runtime-mcp` restates an enum token.
12. `./gradlew :runtime-mcp:test :runtime-mcp:repoTest` and `./gradlew check` on `runtime-kotlin` pass; the four `runtime-mcp` architecture baselines stay empty.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md` design principles, `docs/code-principles.md`, `docs/observability-policy.md`, and AGENTS.md. No `//` comments, KDoc only on interfaces.
- Keep the recorded decisions: `Main.kt` is the one ambient environment read (2026-09-03); CLI/MCP mapper pairs stay distinct (2026-09-03); the telemetry-event validator and its Gradle copy task stay in this module (history L2531-2540, ARCHITECTURE.md L867-869).
- No new runtime dependency. No MCP SDK, JSON-RPC library, or code generator.
- Tool result payloads for well-formed input are byte-identical to today, proven by the existing golden fixtures and stdio tests; the advertised `tools/list` may gain constraints and descriptions the YAML already carries and loses nothing.
- Each subtask ships alone as one commit with its bindings, tests, documentation, and decision entries.

## Non-goals

- Merging CLI and MCP mappers or the three scaffold parser copy pairs.
- Moving `TelemetryEventSchemaValidator` out of the module.
- Changing application service behaviour beyond the additive `stdinText` parameter on `previewImport`.
- Moving `runtime-mcp` tests off SQLite internals; SKILL-356 subtask 1 owns that.
- Concurrency, streaming, or transport changes to the stdio server.

## Validation

Name the regression before each test. Run the module `test` and `repoTest` suites, `runtime-core` architecture guards (wire vocabulary, ambient recorders, layer boundaries, principle inventory), `runtime-application` for the `previewImport` change, and `./gradlew check` on `runtime-kotlin`. Run the pack-declared quality gate and `bill-unit-test-value-check` for changed tests. Exercise the built distribution once over stdio with `initialize`, `ping`, `tools/list`, one strict tool with an unknown argument, and one well-formed call.

## Next path

```bash
skill-bill goal SKILL-357
```
