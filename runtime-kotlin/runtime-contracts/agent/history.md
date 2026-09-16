## [2026-09-16] SKILL-349 preserve contract input meaning
Areas: runtime-contracts, runtime-application/updatecheck, runtime-infra-sqlite telemetry/review/workflow, orchestration contract schemas, runtime-kotlin architecture
- Preserved exact numeric and JSON values at canonical contract boundaries, with typed failures for wrong roots, types, and strict arrays.
- Migrated decomposition, scaffold, update-check, telemetry, review, and workflow consumers so malformed content is explicit while omitted data retains its meaning.
- Followed lightweight packaged-resource validation and owning wire-key/error vocabulary; documented numeric and failure-policy boundaries.
- Reusable: shared packaged YAML number helpers and a strict JSON array entry point.
- Known limitation: issue-key schema retains scalar minLength/maxLength while its loader enforces Int-backed bounds.
Feature flag: N/A
Acceptance criteria: 8/8 implemented
