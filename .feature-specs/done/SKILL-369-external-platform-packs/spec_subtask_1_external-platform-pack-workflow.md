# SKILL-369 subtask 1 - External platform pack workflow

## Scope

Deliver the external platform pack workflow end to end. Extend existing
platform-pack scaffolding and authoring to user-owned destinations, register
sources in machine-global external_platform_pack_sources, and support
registering existing packs and unregistering without deleting source files.

Each configured path denotes one pack directory with platform.yaml. Use the
manifest platform slug and enforce the existing directory-name contract.
Preserve existing config path precedence, path expansion, and unrelated keys.
Persist canonical absolute registration paths. Provide a read-only
skill-bill config resolve-external-platform-packs command and dry-run creation.

Create one effective pack catalog. External sources replace same-slug bundled
packs before selection and routing. Apply external add-on overlays to the
effective installed pack, then stage sidecars and native agents. Wire review
composition, dependency resolution, catalogs, quality gates, and build gates
to the same definition. No consumer may recover missing content from the
shadowed bundled pack.

Validate before publication, preserve the prior usable installation on failure,
and reconcile managed output when sources change or registration disappears.
Keep source directories untouched by install/update. Ship author documentation,
boundary tests, and docs/observations/SKILL-369-codegraph.md in this subtask.

## Acceptance Criteria

1. Users can scaffold a complete external platform review pack through the existing guided and payload authoring flows, fill its authored content, validate it, and install it without modifying Skill Bill's repository or maintaining a fork.
2. Machine-global external_platform_pack_sources accepts entries with a path to one pack root containing platform.yaml. Registration preserves unrelated config and existing external add-on sources, uses existing config-path precedence and path expansion, and provides a read-only resolution command.
3. External packs with new slugs participate in manifest-driven discovery and normal pack selection. For the same slug, the external pack always replaces the bundled definition before routing, composition, selection checks, or rendering. Config order and routing scores cannot restore the shadowed bundled definition.
4. Each effective pack has one source. Missing specialists, pointers, add-ons, native-agent declarations, or validation_gate fields are never filled from its shadowed bundled pack. Explicit manifest composition resolves dependencies through the effective catalog and retains required-baseline checks.
5. Duplicate registrations of the same canonical source are idempotent. Different external roots declaring the same slug fail with a typed ambiguity error. Missing roots, malformed config, invalid manifests, unsupported contract versions, slug mismatches, and missing required content fail before promotion, with no silent bundled fallback.
6. Install planning, staged review sidecars, routing catalogs, native-agent generation and inventory, runtime review composition, and quality/build gate selection agree on the effective source. Existing platform-selection behavior remains intact. An external Kotlin pack does not force Kotlin routing for unrelated stacks.
7. External add-on overlays apply after pack precedence resolves and target the selected effective pack. Existing collision checks and not-installed warnings remain. Overlays never mutate the external authored pack, and incompatible consumer paths fail rather than consulting the bundled copy.
8. Repeated installation and upstream updates retain registered overrides and refresh changed external content. Removing registration and reinstalling restores the bundled pack if present, or removes obsolete managed output for an external-only slug. Stale sidecars, catalog entries, and native-agent links cannot survive a source switch.
9. External sources retain governed content.md, platform.yaml, authorized companions, and provider-neutral native-agent source shapes. Rendering produces generated artifacts only in managed output. Source traversal and symlink escapes outside allowed pack/shared-support roots fail, and failures preserve user files and the previous usable install.
10. Resolution and installation diagnostics identify the effective slug, source kind, and shadowed bundled source. Typed failures and any recovery or degradation emit existing-policy records without publishing private guidance or unredacted source paths to remote telemetry.
11. Documentation provides reproducible creation, registration, authoring, install, override inspection, update, and unregister examples for a new slug and a Kotlin replacement. A pack without a validation_gate retains the existing typed missing-gate failure.
12. Implementation produces a CodeGraph observation report with concrete helpful, unhelpful, and unavailable results, fallback reasons, and evidence references. It distinguishes observed navigation utility from unmeasured time or token savings and records limitations honestly.

## Dependency notes

No earlier subtask. Reuse current external add-on registration/overlay seams,
the platform pack manifest contract, install staging and native-agent inventory,
and SKILL-368 CodeGraph integration. Explicit pack dependencies resolve through
the effective catalog. Do not add a separate manifest format for external packs.

## Constraints and non-goals

Apply runtime-kotlin/ARCHITECTURE.md design principles and docs/code-principles.md.
Keep source selection in one owned policy used by existing consumers, with
filesystem/config adapters outside domain code. Declare governed keys and
versions in their contract owners. Any new runtime schema must follow the
schema-first, parity-test, typed-error requirements.

External registration gives precedence within a platform slug. Existing
routing still chooses between different platform slugs. A registered source
does not bypass the user's enabled-platform selection. Validate registered
sources before use and expose failures rather than treating them as absence.

This work excludes remote fetching, package registries, automatic downloads,
pack signing, a marketplace, arbitrary plugin execution, changes to external
agent add-on precedence, and a new IDE pack editor. Local directories may be
version-controlled by their owners. Do not add hidden fallback, partial pack
merging, or Kotlin-specific override branches.

## CodeGraph observation requirement

During implementation, use CodeGraph first for code-navigation questions when
the repository has a graph, as required by AGENTS.md. Keep normal source and
search tools available when the graph misses a path, reports pending sync,
returns irrelevant material, truncates output, or fails.

Write docs/observations/SKILL-369-codegraph.md as implementation evidence. For
each substantive navigation question, record the phase, question or query,
returned symbols or paths, whether the answer supported the next decision,
fallback tool and reason if needed, and a reference to the resulting source
evidence. Record availability and freshness when exposed. Record elapsed time,
call counts, or output size only when measured; do not invent counterfactual
savings. Use bounded summaries, not raw source dumps or private payloads.

Cover external-source configuration, pack resolution/install consumers, and
review composition/native-agent consumers. Report no useful result when that
is what happened. End with a recommendation to retain, narrow, or change
CodeGraph use based on those observations. Tool failure does not block pack
implementation; record it and continue. This is a task-specific observation
exercise using the existing SKILL-368 integration, not a new telemetry product.

Preparation already supplied two observations. A broad pack-loading query
located buildPack and native-agent loader callers but also returned unrelated
substance-audit code. An ExternalAddonSource query located the config store
and registration port, but included unrelated payload symbols and hit the
tool output limit. These are qualitative observations only, not performance
measurements or substitutes for implementation-time evidence.

## Validation strategy

Name the regression each test prevents before authoring it. Use temporary
source roots, isolated config/home directories, and distinguishable content
markers to assert observable results through existing entry points.

Cover a novel slug; bundled/external Kotlin collision; reversed registration
order; duplicate canonical registration; competing external roots; invalid
override with valid bundled copy; missing content; and path escapes. Exercise
an explicit dependent pack such as KMP against the effective Kotlin baseline.
Verify a missing inherited area fails without reading shadowed content.

Exercise scaffold dry run and rollback, existing-pack registration, repeated
install, external content changes, upstream refresh, registration removal,
add-on overlay success and collision, and staged/native output cleanup.
Inject a failure before promotion and during installation to verify that the
previous usable installation survives and recovery is recorded. Assert the
same source in review routing and quality/build gate selection.

Run focused contract and install/composition tests, the configured dominant
pack quality gate, and the required test-value review at their allowed phases.
Refresh generated outputs with ./install.sh when implementation changes source,
rendering, or pointers. Use isolated homes for smoke tests so fixture packs do
not alter personal configuration. Record unavailable provider checks as limits.
The CodeGraph report is available during implement/audit; later review or
validation receipts are not implement acceptance criteria.

## Next path

After implementation, continue through the runtime-owned review and validation
phases for this subtask. The prepared goal starts with skill-bill goal SKILL-369.
