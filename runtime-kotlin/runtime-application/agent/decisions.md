# runtime-application decisions

## [2026-09-21] TypeSafe client opt-in is the experiments allowlist
Context: The System One client had a private `typesafe.enabled` flag after experiment availability already lived on machine and repo `experiments` arrays.
Decision: Gate evaluate/probe on an explicit `typesafe` entry in that array. `--enable` writes the name; credentials stay in the `typesafe` object.
Reason: A second experimental switch would drift from SKILL-366. Inherit (absent key) still means all registered names are selectable for goal pairs, but the HTTP client stays off until `typesafe` is listed so an API key alone cannot call TypeSafe.
Alternatives considered: Treat inherit as client-on (rejected: accidental external calls). Register a typesafe goal-pair descriptor (rejected: SKILL-366/367 keep the client as infrastructure, not a pair treatment).
Revisit when: a production descriptor needs TypeSafe as a treatment capability during paired execution.

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
