# SKILL-368 subtask 1 - Default CodeGraph session

## Scope

Add CodeGraph to the shared runtime agent-session seam as an automatic local
capability. Detect the installed CLI, initialize or reuse the repository graph,
start a scoped MCP server, pass its configuration to the child, and clean it up
at session end. Record typed setup and runtime degradations, then keep the
child on ordinary tools when CodeGraph cannot be used.

Apply the same behavior to goals, feature tasks, and delegated agent skills.
Do not add experiment selection, a pair runner, or global agent configuration.

## Acceptance Criteria

1. Runtime-launched agent sessions automatically attempt CodeGraph when the CLI is installed, with no experiment flag or manual agent configuration.
2. Missing CLI, missing graph, initialization failure, unsupported command, and MCP startup failure produce typed, redacted degradation records and leave the child running with ordinary tools.
3. A prepared or successfully initialized repository starts one scoped CodeGraph MCP process for the child and passes only the declared session configuration.
4. The managed process is cleaned up after normal exit, failure, timeout, cancellation, crash, and cleanup failure, with the final lifecycle state persisted.
5. Skill Bill never edits global agent configuration, stages or commits generated graph data, or changes its own telemetry preference while managing CodeGraph.
6. Pending synchronization and CodeGraph query failures remain visible. The child can use ordinary file tools for affected files, and fallback is never reported as successful CodeGraph use.
7. Goals, feature tasks, and delegated agent skills use the same default session seam. Deterministic validation and ordinary shell commands do not start CodeGraph.
8. Boundary tests cover installed and missing CLIs, prepared and unprepared graphs, initialization and MCP failures, pending sync, cancellation, timeout, crash cleanup, telemetry preservation, and ordinary-tool fallback.
9. Documentation covers installation prerequisites, automatic setup, fallback, local graph data, telemetry, and cleanup.

## Dependency notes

Reuse the existing runtime process, MCP, telemetry, typed failure, and agent
session boundaries. The external dependency is the installed CodeGraph CLI
from https://github.com/colbymchenry/codegraph. Verify its command and
capability surface during implementation. CI must use fakes at process and MCP
boundaries.

## Non-goals

- CodeGraph installation, upgrade, source vendoring, or global agent setup.
- Blocking a child when CodeGraph is absent or unavailable.
- CodeGraph access for deterministic validators or ordinary shell commands.
- Experiment selection, pair comparison, production retrieval policy, acceptance adjudication, review, or repair.
- Claims about CodeGraph quality, cost, latency, or token savings.

## Validation strategy

Test observable process and durable-state boundaries with fake executable and
MCP responses. Include prepared and missing graphs, initialization failure,
pending sync, invalid capability, process crash, timeout, cancellation, and
cleanup failure. Assert that every case leaves ordinary tools usable and
settles with the correct degradation or active-session outcome.

Verify the shared seam through each managed agent entry path, control global
configuration writes, preserve Skill Bill telemetry settings, and check
redaction. Keep tests deterministic and independent of the real CodeGraph
binary, network, and credentials.

## Next path

Run the focused lifecycle and session-contract tests through the owning
validation gate. Then run `skill-bill goal SKILL-368` in an environment with
CodeGraph installed to verify the managed path and without it to verify the
ordinary-tool fallback.
