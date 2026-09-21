# SKILL-368 - Default CodeGraph integration

## Mode

single_spec

## Intended outcome

Make CodeGraph the default local code-navigation aid for Skill Bill's
runtime-launched agent sessions. When the CodeGraph CLI is installed and the
project graph is available, Skill Bill starts a managed CodeGraph MCP session
and gives the child agent its declared tools automatically. The caller does
not pass an experiment flag or edit an agent configuration.

Skill Bill must keep working when CodeGraph is absent, cannot initialize a
project graph, or fails during a session. In those cases the child continues
with its ordinary tools and Skill Bill records the exact degradation reason.

## Dependency notes

Use the existing runtime process, MCP, telemetry, and typed failure boundaries.
The external integration target is
[CodeGraph](https://github.com/colbymchenry/codegraph). Its documented local
workflow uses `codegraph init` to create a project graph and
`codegraph serve --mcp` to expose it to an agent. Verify command and capability
details at implementation time.

The integration applies to runtime-launched agent sessions used by goals,
feature tasks, and delegated agent skills. Deterministic validation commands
and ordinary local shell work do not need a CodeGraph server.

## Integration contract

At session startup, Skill Bill checks for the CodeGraph executable and verifies
that it can serve the current repository. If the project has no graph, the
managed setup may run `codegraph init` in the repository before the child
starts. Project graph files remain local runtime data and are never staged or
committed by Skill Bill.

When setup succeeds, Skill Bill starts one repository-scoped `codegraph serve
--mcp` process for the child session, passes the session-scoped MCP
configuration, and owns cleanup. It does not edit global Claude, Codex,
Cursor, or other agent configuration. CodeGraph is available by default to the
managed child, while the ordinary tools and task inputs remain available.

When setup or runtime use fails, Skill Bill records a typed degradation and
continues the same child session with ordinary tools. The fallback cannot hide
the failure or claim that CodeGraph supplied evidence. A child must not stall
because CodeGraph is missing.

Managed invocation disables CodeGraph telemetry for that process. This setting
does not change Skill Bill's telemetry preference. Skill Bill records bounded,
redacted lifecycle, availability, query, failure, and cleanup observations
through its existing telemetry contracts. It never records source contents,
credentials, or unredacted MCP payloads.

The graph's pending-sync status is visible to the child. When CodeGraph says a
file is pending or a query is unavailable, the child can read that file through
ordinary tools and Skill Bill does not present the graph response as current
source evidence.

## Acceptance Criteria

1. Runtime-launched agent sessions automatically attempt to use CodeGraph when its CLI is installed. No experiment flag, pair selection, or manual agent configuration is required.
2. A session with no CodeGraph executable continues with ordinary tools and records a typed `unavailable` degradation with the executable check reason.
3. A repository without a prepared graph is initialized through the managed repository-scoped setup path, or the child continues with ordinary tools when initialization fails. Skill Bill never stages or commits generated graph files.
4. A successful setup starts one repository-scoped CodeGraph MCP process, passes only the declared session configuration to the child, and cleans the process up on normal exit, failure, timeout, cancellation, and crash.
5. Skill Bill does not edit global agent configuration. The child receives the same task inputs and ordinary tools regardless of CodeGraph availability, plus CodeGraph tools only when the managed session is ready.
6. Pending synchronization, unsupported capability, query failure, process failure, and cleanup failure remain visible in durable state and reports. Fallback never appears as successful CodeGraph use.
7. Managed CodeGraph telemetry is disabled without changing Skill Bill's telemetry preference. Skill Bill records redacted lifecycle, availability, bounded query, failure, and cleanup observations through existing contracts.
8. Goals, feature tasks, and delegated agent skills use the same default integration seam. Deterministic validation and ordinary local shell commands do not start CodeGraph.
9. Boundary tests cover installed and missing CLIs, prepared and unprepared graphs, initialization failure, MCP startup failure, pending sync, session cancellation, process crash, cleanup failure, and ordinary-tool fallback.
10. Documentation explains installation prerequisites, automatic setup, session behavior, fallback behavior, generated graph data, telemetry controls, and how to inspect or remove the local CodeGraph state.

## Executable scope

One subtask adds the default CodeGraph capability to the shared runtime agent
session seam, including executable and graph setup, managed MCP lifecycle,
fallback outcomes, telemetry, focused tests, and operator documentation. It
leaves Skill Bill usable without CodeGraph and does not add an experiment path.

## Non-goals

- Making CodeGraph a required dependency or blocking a child when it is absent.
- Installing or upgrading the CodeGraph CLI from Skill Bill.
- Editing global agent configuration or registering CodeGraph for every local agent.
- Reimplementing CodeGraph's parser, index, watcher, MCP server, or query tools.
- Exposing CodeGraph to deterministic validators or ordinary shell commands.
- Treating CodeGraph output as an acceptance verdict, review decision, repair instruction, or source-of-truth replacement.
- Publishing source code, graph contents, credentials, or CodeGraph telemetry outside the local managed session.

## Validation strategy

Use fake executable and MCP-process boundaries to test command construction,
repository scoping, graph initialization, startup, pending synchronization,
normal exit, crash, timeout, cancellation, and cleanup failure. Assert durable
degradation and fallback outcomes at the process and child-session boundaries.

Test the same seam through goal, feature-task, and delegated-agent entry paths.
Verify that missing or failed CodeGraph setup leaves ordinary tools usable and
does not mutate global agent configuration or Skill Bill telemetry preference.

Use fixture repositories with and without a graph. Keep tests deterministic and
independent of the real CodeGraph binary, network, credentials, and a full
repository index. Run focused contract and lifecycle tests through the owning
validation gate.

## References

- [CodeGraph repository and CLI documentation](https://github.com/colbymchenry/codegraph).
- `../../../runtime-kotlin/ARCHITECTURE.md`.
- `../../../docs/observability-policy.md`.

## Next path

After the shared session seam, managed lifecycle, fallback, and tests are
available, run `skill-bill goal SKILL-368`. Install CodeGraph separately when
you want the managed capability; omit it when ordinary tools are sufficient.
