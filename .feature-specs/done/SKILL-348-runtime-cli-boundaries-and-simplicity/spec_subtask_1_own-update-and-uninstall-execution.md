# SKILL-348 Subtask 1 - Own update and uninstall execution

Parent spec: [.feature-specs/SKILL-348-runtime-cli-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-348

## Scope

Own findings F-001 and F-002. Refactor system/SystemCliCommands.kt, model/ExternalCommandRunner.kt, system/UninstallCommand.kt, system/UninstallCommandApply.kt, and system/UninstallMutationRecorder.kt with their application, port, infrastructure, and runtime-core bindings.

Use the existing update application area for the update decision and install request. Download the installer completely before execution, preserve download and process failures, and establish bounded process ownership. Reuse a current process primitive only if its contract fits installer stdin, output, environment, and teardown needs. Do not force installer behavior through a Git-specific API. The subprocess implementation belongs outside the CLI model package.

Move uninstall planning and mutation sequencing into a cohesive application operation using existing ports. Keep CLI prompting and rendering at the entry adapter. Make failure policy explicit: cancellation and interruption abort, while the currently supported ordinary per-target failures record degradation and continue. Keep managed-file ownership checks and partial-success reporting. Do not add a transaction across filesystem and configuration stores.

Delete the installer result copy as part of this change. Add the bounded tests needed for these operations and update the documented composition boundary in the same commit.

## Acceptance Criteria

1. An installer download failure produces a nonzero update result and cannot run a partial downloaded script. A successful download followed by installer failure preserves the installer exit code and useful bounded output. A successful installer retains the existing completed result.
2. The updater owns every process and stream it starts until settlement. Normal completion, launch failure, output failure, interruption, and deadline expiry close streams and settle owned child processes and drains within documented limits. Cleanup never kills unrelated processes or replaces the primary failure.
3. The installer receives an explicit stdin policy so a child waiting for input cannot hang on an accidentally open unused pipe. Output capture is bounded, with explicit truncation evidence where needed. The default deadline is documented and testable through an injected boundary, without adding an arbitrary public option.
4. Update orchestration belongs to the application update area and concrete process execution belongs to an existing infrastructure module. runtime-core wires the production adapter. No ProcessBuilder implementation remains in skillbill.cli.model and no application API imports CLI types.
5. Uninstall plan and apply policy have one application owner. The CLI parses options, enforces its confirmation interaction, and renders typed results. Existing install, native-agent, MCP, host-platform, and filesystem ports remain the mutation boundaries.
6. Cancellation or interruption at any uninstall mutation seam stops subsequent mutations. Cancellation propagates as the same signal; interruption follows the existing interrupt contract. Ordinary recoverable mutation failures still produce diagnostics and failed_with_degradations with a nonzero exit code while following the established best-effort policy.
7. Uninstall dry-run, rejection during goal continuation, managed symlink checks, legacy cleanup rules, user-selected home, MCP partial-success accounting, and existing confirmation behavior remain intact.
8. Delete the duplicate InstallerRunResult copy and avoid replacing it with a forwarding-only service or dependency bag. Keep one result owner for installer execution.

## Non-Goals

- No installer UX redesign, new download service framework, cross-store transaction, or general process-runner rewrite.
- No changes to feature-task worker leases, Git process execution, review scheduling, or the concurrent SKILL-248 recovery work.

## Dependency Notes

Depends on: none
No prerequisite. This commit can ship independently of the CLI invocation changes. It owns the update and uninstall files so the second subtask does not need to revisit them.

## Validation Strategy

Before authoring each regression, state its failure trigger. Exercise update with a fake curl or download adapter that emits a partial script and then fails; verify the script never executes. Test success and installer exit-code propagation. Use owned local processes to test stdin EOF, interruption, timeout, and bounded output; always clean up test children. Inject cancellation into uninstall cleanup and assert that later targets and state-root deletion do not run. Retain ordinary mutation-failure and managed-link tests. Run relevant application, infrastructure, and CLI suites plus dependency and composition guards, then the governed implementation quality gate.

## Next Path

Continue to subtask 2 through the goal runtime.

## Spec Path

.feature-specs/SKILL-348-runtime-cli-boundaries-and-simplicity/spec_subtask_1_own-update-and-uninstall-execution.md
