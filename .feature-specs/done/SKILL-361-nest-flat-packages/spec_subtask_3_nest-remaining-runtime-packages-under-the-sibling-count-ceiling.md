# SKILL-361 Subtask 3 - Nest remaining runtime packages under the sibling-count ceiling

Parent spec: [.feature-specs/SKILL-361-nest-flat-packages/spec.md](spec.md)
Issue key: SKILL-361

## Scope

Resolve F-003, F-004, F-005, and F-007 in [investigation.md](investigation.md).

Own every remaining production package under `runtime-kotlin` and `intellij-plugin` that the sibling-count scanner reports, including application `review` / `workflow` / `telemetry`, domain `workflow.taskruntime` and `taskruntime.model` and `review`, infra `workflow` and sqlite area stores and `skills`, cli `scaffold` and `goal`, `skillbill.contracts.workflow`, `skillbill.error`, and `skillbill.di` unless a recorded decision names `di` as the sole composition root. Own matching tests and imports. Do not reopen engine `featuretask` or `goalrunner` packages except leftover import lines.

Nest mixed-responsibility packages by noun family. Keep public inputs and results in area `model` packages and split a `model` package that mixes unrelated noun families until it is at most 20 files. Relocate tests with production types. Empty the remainder inventory. Re-run the census after the moves; the recorded table in the investigation is evidence, not authority.

## Acceptance Criteria

1. The sibling-count architecture test reports no production package under `runtime-kotlin` or `intellij-plugin` above 12 sibling `.kt` files for a non-model package or 20 for a `model` package, except packages named in a new `../../../runtime-kotlin/agent/decisions.md` entry as a homogeneous taxonomy with a reason.
2. `PrincipleEnforcementInventory` remainder inventory is empty.
3. Application `review`, domain `taskruntime` (including `model`), infra `workflow`, sqlite area stores that exceeded the ceiling, and skills packages named in F-003 through F-005 are nested by noun family. Area `model` packages do not import their parent area package and do not hold `@Inject` services.
4. Matching tests live in the same packages as the production types they exercise. Suites that compiled before the move still run against the moved types.
5. `intellij-plugin` still uses `domain`, `application`, `presentation`, `ui`, and `infrastructure` and stays under the ceiling. Engine packages from subtasks 1 and 2 stay under the ceiling.
6. All modules compile. Package-cycle baselines stay empty or shrink. No type crosses a Gradle module boundary.

## Non-goals

No new Gradle modules. No `domain`/`data`/`ui`/`presentation` packages inside runtime-kotlin areas. No behaviour change. No rewrite of review, workflow, or sqlite adapters beyond package moves. No engine feature-task or goal-runner re-nesting.

## Dependency notes

Depends on: subtask 2, so the engine is under the ceiling and the remainder inventory only names non-engine packages when this subtask starts.

## Validation strategy

Name the regression before each test: a remainder row left behind, a model package still mixing handoff and repair above 20 files, a plugin layer renamed, an engine package pushed back above the ceiling, a sqlite store moved into the wrong module. Rely on compilation of all modules, `runtime-core` architecture tests, domain/application/infra/cli/plugin suites, package-cycle tests, and `./gradlew check` on runtime-kotlin. Apply bill-unit-test-value-check only if a test assertion changes.

## Next path

None. This subtask completes the goal.

## Spec Path

.feature-specs/SKILL-361-nest-flat-packages/spec_subtask_3_nest-remaining-runtime-packages-under-the-sibling-count-ceiling.md
