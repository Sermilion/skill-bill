# SKILL-364 - CI validation parity

## Mode

single_spec

## Intended outcome

Make feature-task validation authoritative for the exact final tree that is
about to be committed and opened as a pull request, while covering every
applicable repository check. A completed validation phase must not coexist
with a later runtime quality failure or an unvalidated path-specific check such
as IntelliJ Plugin CI.

## Scope

The change covers final-tree identity and dirty-tree invalidation after
validation, dynamic discovery of applicable PR checks from repository workflow
path filters and platform declarations, execution of the Kotlin runtime and
IntelliJ plugin checks when their paths are affected, durable readiness evidence,
base-ref freshness checks, and focused regression tests.

## Acceptance Criteria

1. The readiness contract records the exact source tree identity, base-ref
   identity, applicable check set, and check result for the tree that proceeds
   to commit and PR creation.
2. Any non-history change to a path covered by an applicable check after
   validation invalidates the prior validation evidence and prevents commit or
   PR progression until the affected check is rerun.
3. A runtime Kotlin change selects the declared Kotlin quality gate, and an
   `intellij-plugin/**` change selects the plugin check equivalent to
   `intellij-plugin/gradlew clean check --no-build-cache`; unrelated workflows
   are not executed as part of readiness.
4. Readiness rejects stale evidence when the PR base ref changes after the
   validated tree was captured, and reports the base/head identities needed to
   reproduce the decision.
5. The readiness result preserves the existing CI checks and failure semantics;
   it cannot mark a pull request ready when a selected check fails, is missing,
   or has not produced durable evidence.
6. Regression tests cover a runtime lint finding added after validation, a
   plugin-only check selected by path, a changed base ref, an unchanged
   history-only path, and a failed or incomplete selected check.
7. Existing feature-task phase ordering, commit ownership, observability
   records, and platform-pack routing remain valid.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md`, `docs/code-principles.md`,
  `docs/observability-policy.md`, and AGENTS.md.
- Keep check selection manifest- and workflow-driven; do not hard-code a
  permanent platform list or duplicate workflow path tables in prompts.
- Reuse the existing quality-gate and plugin Gradle commands as the source of
  truth instead of weakening or replacing CI checks.
- Preserve loud failure and recorded degradation when a check cannot be
  discovered, executed, or persisted.
- Keep authored Kotlin free of non-KDoc comments.

## Non-goals

- No removal of GitHub Actions or the post-PR CI workflows.
- No execution of every repository workflow for every pull request.
- No change to the correctness of the IntelliJ status behavior itself beyond
  fixing coverage of the failing test path.
- No attempt to hide or quarantine Detekt, test, compiler, or plugin failures.
- No general repair of unrelated historical macOS test flakes.

## Validation strategy

Name the regression before each test: a validation receipt survives a later
runtime edit; a plugin change is omitted from the selected check set; a moved
base ref is treated as fresh; history-only changes cause unnecessary reruns; a
failed plugin or runtime check is treated as ready; or a missing check result
is silently accepted. Run focused readiness, check-selection, identity,
invalidation, persistence, and PR-boundary tests, then the declared quality
gates, plugin check, repository validation, and installation flow.

## Next path

Run `skill-bill goal SKILL-364` when implementation is intended. The prepared
manifest is the goal runner's input.
