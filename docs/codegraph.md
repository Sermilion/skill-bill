# CodeGraph sessions

Skill Bill treats CodeGraph as an optional, local capability for managed agent
sessions. It does not install or upgrade the CLI, run the CodeGraph installer, or
edit Claude, Codex, Cursor, Junie, or other global agent configuration.

## Prerequisites

Install the `codegraph` CLI separately and put it on `PATH`. CodeGraph publishes
the CLI and its installation instructions in its [installation guide](https://github.com/colbymchenry/codegraph/blob/main/site/src/content/docs/getting-started/installation.md).
Skill Bill checks for that executable when it starts a goal, feature-task, or
delegated-agent session.

## Automatic setup

When the CLI is available, Skill Bill checks the repository's `.codegraph`
directory. If the graph is missing, it runs `codegraph init <repository> --yes`.
It then runs `codegraph status <repository>` and starts one owned
`codegraph serve --mcp` process in that repository. Skill Bill completes the MCP
handshake and checks that `codegraph_explore` is available before exposing a
local HTTP endpoint to the child. The endpoint accepts only that tool and pins
its `projectPath` to the session repository. It does not start another CodeGraph
process for each query.

The child configuration contains only the local endpoint and declared tool.
CodeGraph receives the repository working directory and
`CODEGRAPH_MCP_TOOLS=explore`. Skill Bill passes `CODEGRAPH_TELEMETRY=0`, `DO_NOT_TRACK=1`, and
`CODEGRAPH_NO_DAEMON=1` to that server. Skill Bill does not change the user's
stored CodeGraph choice or Skill Bill's own telemetry setting.

## Fallback and pending sync

The child keeps its ordinary file and shell tools when the CLI is absent, the
graph is missing, initialization fails, a command is unsupported, the MCP server
cannot start, synchronization is pending, or a CodeGraph query fails. Each
degradation has a typed reason and a bounded redacted observation. Fallback is
never recorded as successful CodeGraph use. A pending file remains readable by
ordinary tools. The endpoint returns an error result without the stale source
when CodeGraph reports pending sync or disabled auto-sync. Query exceptions and
MCP errors also return explicit ordinary-tool fallback. These responses never
claim successful CodeGraph use.

## Local data and cleanup

CodeGraph writes its generated index under the repository's `.codegraph`
directory. Skill Bill never adds that directory to a commit. Session MCP files
live in a temporary directory, except for providers that require a project MCP
file. Those files are restored or removed after the child exits. Existing unrelated
project MCP entries remain intact. The runtime does not write global agent
configuration. The final session lifecycle observation is retained under
`.skill-bill/runtime/codegraph-sessions/` with repository paths redacted.
That runtime state is local and excluded from Skill Bill staging as well.

Normal exit, child failure, timeout, cancellation, and CodeGraph process failure
all close the session lease. Cleanup terminates the owned process and its observed
descendants, stops the local endpoint, and removes or restores session configuration.
A process monitor detects a CodeGraph crash and reaps the process. The local
endpoint remains available to return fallback until the child exits, so a crash
cannot remove configuration during child startup. A JVM shutdown hook also
attempts cleanup. Cleanup failures do not replace the child's primary failure. The final record names the terminal
outcome and any cleanup failure; separate event records retain earlier degradations.

Inspect the final JSON files under `.skill-bill/runtime/codegraph-sessions/` for
lifecycle state and degradation reasons. After all sessions have stopped, remove
`.codegraph/` to discard the local index. The next managed session attempts setup
again. Remove the CodeGraph session-record directory separately to discard its
local observations. Skill Bill excludes both directories from staging and refuses
to commit an index that already contains either directory.

Goal planning, feature-task phases, and delegated agent launches use the same
session launcher. A goal-continuation `skill-bill` command leaves setup to the
agent sessions it launches. Deterministic validation and ordinary shell commands
do not check for CodeGraph or start a server.
