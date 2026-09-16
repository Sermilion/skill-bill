## [2026-09-16] SKILL-349 subtask 2 — Simplify contract implementation
Areas: runtime-contracts/{time,error,install,scaffold}, runtime-application/workflow, runtime-domain/taskruntime, runtime-infra-fs/phaseoutput, runtime-core/architecture, runtime-kotlin docs
- `JvmSystemClock` delegates to a live UTC millisecond JDK clock while domain consumers retain injected `java.time.Clock`.
- `WorkflowContracts` was folded into `WorkflowWireProjections`, preserving payload order, null omission, continuation mode, and extra-field precedence without another map pass.
- Phase-output failure tokens now have one contract-owned enum; validators and conformance consumers derive classification and unknown-token rejection from it.
- Unused diagnostics, helpers, install convenience declarations, and failure lookup residue were removed after caller checks; documentation records the resulting ownership and diagnostic-deletion decision.
- Pattern: keep wire vocabulary and failure classifications at their contract owner, with application mapping as the single projection seam. reusable
- Known limitation: no compatibility aliases or ambient-time domain access were added; existing external error behavior remains unchanged.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-09-16] SKILL-349 preserve contract input meaning
Areas: runtime-contracts, runtime-application/updatecheck, runtime-infra-sqlite telemetry/review/workflow, orchestration contract schemas, runtime-kotlin architecture
- Preserved exact numeric and JSON values at canonical contract boundaries, with typed failures for wrong roots, types, and strict arrays.
- Migrated decomposition, scaffold, update-check, telemetry, review, and workflow consumers so malformed content is explicit while omitted data retains its meaning.
- Followed lightweight packaged-resource validation and owning wire-key/error vocabulary; documented numeric and failure-policy boundaries.
- Reusable: shared packaged YAML number helpers and a strict JSON array entry point.
- Known limitation: issue-key schema retains scalar minLength/maxLength while its loader enforces Int-backed bounds.
Feature flag: N/A
Acceptance criteria: 8/8 implemented
