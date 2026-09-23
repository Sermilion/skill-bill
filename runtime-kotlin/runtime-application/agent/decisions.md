# runtime-application decisions

## 2026-09-23 — Type-surface declarations stay public when only their names are module-local (SKILL-370)

**Context.** The visibility sweep narrowed every runtime-application top-level declaration whose
name no other module mentions. A name census alone overstates what is safe: a result or request
type can be absent from every other module's source and still sit in the signature of a
cross-module entry point, because callers bind it through inference (`val result = service.launch(...)`).

**Decision.** Top-level functions and properties with no out-of-module name reference are
`internal`. Types reached only through inference from a cross-module signature stay public until a
compiler-verified pass can narrow them together with their exposing members; Kotlin rejects a
public member that exposes an internal type, so narrowing them by name alone breaks the build.
Types whose every exposer is itself internal or private are narrowed. `@Inject` classes and their
constructor parameter types stay public because runtime-core's generated component constructs them.

**Alternatives considered.** Narrowing every name-local type and letting the build phase revert the
failures (rejected: churn against the same criterion). Widening the census to treat any signature
mention as external (rejected: it would keep module-local helper functions public too).

## 2026-08-10 — Validate-phase gate execution is runtime-owned (SKILL-180)

**Context.** Validate previously instructed the agent to invoke `bill-code-check`, so gate-run
count, batching, and terminal cache-bypass evidence were unobservable and unenforceable.

**Decision.** The runtime resolves a pack-declared `validation_gate`, runs argv in the repository
root, projects findings for agent repair, reruns the gate to verify, and persists measured
`gate_run_count` / `gate_runs`. The validate agent receives bounded findings and must not invoke
the gate or any quality-check skill. Audit and repair evidence stay read-only repository facts.

**Alternatives considered.** Agent-reported gate_run_count (rejected: self-reporting cannot detect
runaway reruns). Hardcoded Gradle `--no-build-cache` in runtime (rejected: stack-specific; packs
declare cache-bypass argv).
