# SKILL-348 Subtask 2 - Make CLI invocation inputs and output authoritative

Parent spec: [.feature-specs/SKILL-348-runtime-cli-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-348

## Scope

Own findings F-003 through F-007. Update core/Main.kt, core/CliRuntime.kt, kernel/CliRunState.kt, model/CliExecutionResult.kt, model/CliRuntimeContext.kt, and model/CliRunInputs.kt only as required to give each invocation one input and output contract. Update command consumers in config, install, scaffold, skillremove, agentaddon, codereview, repovalidation, featuretask, workflow, and goal where they bypass that contract.

Resolve omitted roots from the supplied invocation context. Trace feature-task root selection through spec resolution, workflow opening, resume identity, and runner request construction. Prepare and validate one immutable set of execution facts before mutations. Keep these facts separate from the existing dependency graph; do not rename a dependency bag to context and call it resolved.

Provide the smallest output representation that supports ordinary text and exact diagnostic bytes. Main owns terminal emission; command execution marks completion explicitly. Move diagnostic transaction/service composition to an injected application operation, preserve retention writes, and emit output after its owned work has settled. Do not convert raw bytes through String.

Replace Main's raw argument scan with demand-driven stdin selection at the parsed input boundary. Preserve text content for authoring commands. Reject malformed step-updates before invoking the workflow service. Preserve JSON output on paths where that command promises JSON, and preserve framework help/usage behavior.

While changing invocation plumbing, remove the six unused workflow generalization pairs, the one-consumer code-review base class, the unreachable prelaunch parser, and unused input parameters. Keep the get/show aliases and production-used sharing. Add behavioral regression tests and update architecture documentation in this same commit.

## Acceptance Criteria

1. Every omitted repository-root option resolves through the invocation inputs. Explicit roots keep documented precedence. Feature-task spec resolution, immutable workflow identity, resume verification, and runner launch receive the same resolved root. Relative explicit roots preserve shell invocation semantics, and embedding behavior has an explicit tested base.
2. Feature-task run and resume prepare the effective request once before opening or mutating workflow state. Reuse the prepared root, agent/model settings, and verified add-on selection. Reject invalid option combinations and operator decisions before workflow creation or worker ownership acquisition.
3. Rejected-output metadata and cleanup complete through the CLI result path. Raw output preserves exact bytes, including invalid UTF-8, NUL, CRLF, and missing terminal newline. No command appends root help or a synthetic newline after an explicitly completed empty or raw result.
4. Diagnostic inspection and cleanup use an injected application entry point that owns database session and retention work. The CLI does not construct RejectedOutputDiagnosticService or obtain its repositories from UnitOfWork. Preserve metadata validation, permissions, expiry, digest checking, and ambiguous-selector refusal.
5. Stdin is consumed on demand for the parsed command. import-review without a path and with an explicit dash both consume stdin. Accepted --body-file=- and --payload=- forms work. Help and unrelated commands do not read stdin because some other argument happens to equal a dash. Preserve authored text line endings for fill and edit inputs.
6. A supplied --step-updates value must parse as an array of objects before calling WorkflowService.update. Malformed JSON and valid JSON of the wrong shape fail with a clear nonzero usage result and leave workflow status, steps, and artifacts unchanged. Omitted input and an explicitly empty array retain their distinct intended behavior.
7. Collapse the six single-consumer workflow base/subclass pairs for open, update, list, latest, resume, and continue into their actual verify command implementations. Keep shared get/show behavior and both public aliases. Remove the one-consumer CodeReviewDriverCommand inheritance layer while retaining its production parser semantics and command options.
8. Delete the unreferenced parseReviewPrelaunchExpansion helper and unused injected CliRunInputs parameters in the touched command paths. Do not turn command registration into reflection, a generic registry, or a family of new role interfaces.
9. Keep command-area imports isolated to kernel and model. Extend existing guards only where they can detect the actual process, transaction, or input-ownership violation. Document remaining scanner limits instead of declaring universal SOLID compliance.

## Non-Goals

- No generic command bus, reflection-based registration, automatic command discovery, replacement DI container, or interface per command.
- No global rewrite of raw presentation maps, all exception classes, or JsonCodec callers outside the observed CLI input boundary.
- No changes to update/uninstall internals owned by subtask 1. Do not delete deprecated public commands merely because they forward.

## Dependency Notes

Depends on: none
No prerequisite. This commit can ship independently of subtask 1. It owns invocation and command plumbing; normal goal order applies subtask 1 first to make the final composition checks cover both changes.

## Validation Strategy

Use real CliRuntime and process-level Main invocations with temporary homes, repositories, and databases. Assert exact byte equality for raw diagnostic output and no trailing help for metadata/cleanup. Verify default and explicit stdin forms with EOF, CRLF, and help without waiting for input. Use two distinct roots to compare implicit and explicit root behavior, plus a feature-task workflow identity/launch regression. For malformed step updates, compare durable before/after state rather than only an error string. Retain help, alias, shell-command, config, scaffold, install, goal-control, workflow, and work-status tests. Run the complete CLI suite and relevant application tests, existing area-isolation, dependency, composition, ambient-input, and injection guards, then the governed implementation quality gate.

## Next Path

All implementation subtasks are complete. Continue through the goal runtime completion path.

## Spec Path

.feature-specs/SKILL-348-runtime-cli-boundaries-and-simplicity/spec_subtask_2_make-cli-invocation-inputs-and-output-authoritative.md
