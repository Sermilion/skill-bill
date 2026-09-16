# SKILL-247 Subtask 4 - Consolidate governed resource copying

Parent spec: [.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec.md](spec.md)
Issue key: SKILL-247

## Scope

Resolve F-006 in investigation.md. Own runtime-infra-fs/build.gradle.kts and only the existing build-logic or architecture tests whose resource ownership assertions require adaptation. The current file contains 32 Copy task registrations and 914 lines. Consolidate the repeated registration bodies into a module-local resource list and small registration function.

## Acceptance Criteria

1. A single declaration per governed resource supplies its task name, canonical source, destination, and required error attribution. Common registration owns inputs, Copy configuration, and processResources wiring. Keep special non-schema resources explicit.
2. Preserve every existing copy task name, resource destination, runtime consumer, and generated-output exclusion. Missing canonical source still fails with its source path before a usable distribution can be produced.
3. Derive main/test resource task dependencies from the same registration results wherever their contracts match. Preserve intentional differences explicitly and explain them. No independent hand-maintained parallel task list remains for identical resource sets.
4. Compare the resource tree before and after by relative path and content hash. The packaged governed schemas and support resources are identical. A clean build and a cached repeat both provide all resources.
5. Keep architecture assertions about resource ownership and required schema availability. If a test pins an obsolete Gradle text fragment, replace only that assertion with a check of the same build contract.
6. Delete repeated registration code without adding a plugin DSL, reflection, classpath scanning, a new manifest schema, or broad build-logic refactoring. Record actual net line reduction after implementation; the investigation estimate is not a required quota.

## Non-Goals

- No dependency upgrades, schema relocation, generated resource commits, or changes to area compilation rules.

## Dependency Notes

Depends on: none
Independently shippable build-maintenance change. Placed last so behavioral fixes take priority. No predecessor artifact or schema is required.

## Validation Strategy

Run processResources and inspect packaged resources against the before snapshot. Run relevant schema parity/resource loading and RuntimeArchitectureTest checks, then the repository full quality gate because build logic changed. Follow AGENTS.md if execution is specifically the bounded build phase. Run ./install.sh if the final change affects rendering, installation, or support pointer generation.

## Next Path

All planned work is complete when each subtask passes its acceptance criteria and the goal runtime records its commit.

## Spec Path

.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec_subtask_4_consolidate-governed-resource-copying.md
