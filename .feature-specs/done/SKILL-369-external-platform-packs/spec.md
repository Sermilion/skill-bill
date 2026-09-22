# SKILL-369 - External platform packs

## Mode

single_spec

## Intended outcome

Let users create and maintain platform review packs outside Skill Bill's
repository, register them on their machine, and use them through normal
installation and review workflows. Users do not need an upstream change to
support a new platform or replace the shipped review guidance.

An external pack named kotlin always takes precedence over Skill Bill's
bundled kotlin pack. Replacement covers the entire pack. Invalid user-owned
content must produce an actionable failure, not silently run bundled guidance.

## Source and authoring contract

Add external_platform_pack_sources to the existing durable machine-global
config, using the same config-path resolution as external_addon_sources.
Each entry has a path to one pack root, for example
~/dev/company-packs/kotlin/platform.yaml belongs to the registered path
~/dev/company-packs/kotlin. The platform field in platform.yaml is the slug
authority and must match the root directory name under the current contract.
Expand ~ and resolve relative inputs as existing external add-ons do; persist
canonical absolute paths on registration. Preserve unrelated config fields.

Extend the existing platform-pack wizard and payload path with an external
destination. Dry run reports planned source/config changes without writing.
Creation uses the existing full-pack scaffold, including supported baseline
composition and neutral specialist declarations. Authors fill content through
the governed authoring flow. Successful creation registers the source; failure
rolls back owned source/config changes. Refuse to overwrite an occupied source.
Also support registering an existing conforming pack without scaffolding it.
Unregistering removes registration only, never the author's source directory.

Expose read-only resolution through skill-bill config resolve-external-platform-packs.
Choose the registration and scaffold option spelling during implementation,
then document and test guided and scripted parity.

## Effective pack contract

Resolve registered sources and bundled manifests into one effective catalog
before downstream pack selection. A matching external slug replaces the
bundled entry as a whole. The shadowed entry cannot contribute routing signals
or missing files. Resolve declared cross-pack composition against this catalog,
including dependencies on an overridden Kotlin pack.

Install the effective pack into the existing managed pack layout before
applying external add-on overlays and staging skills. Preserve authored
sources. All runtime pack consumers use this effective installed definition;
do not independently rescan bundled sources and resurrect a shadowed pack.
Source-tree authoring commands validate the explicitly selected source using
the same contracts and effective dependency resolution.

Validate the complete selected installation before publishing it. Preserve the
last usable installed generation on failure and record actionable recovery.
Digest/cache inputs account for effective source identity and content.
Reconciliation removes obsolete managed artifacts after a source switch while
preserving unmanaged files. Config registration survives upstream updates.

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

## Executable scope

One subtask delivers creation, registration, resolution, installation, runtime
consumption, tests, documentation, and the CodeGraph observation report. These
parts establish one user-visible workflow and should ship in one reviewable
commit.

## Dependency notes

External add-ons provide the existing config registration and overlay pattern.
SKILL-368 supplies the existing managed CodeGraph integration. No new external
service is required. Keep the current platform schema unless implementation
requires a governed shape change; external location alone does not justify a
second pack manifest format.

Useful inspected entry points are FileExternalAddonSourceConfigStore,
ExternalAddonSourceConfigPort, buildPack in ShellContentLoaderPackBuild.kt,
and InstallNativeAgentPlatformPackLoader. Follow their consumers rather than
assuming these are the complete change set.

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

## References

- docs/external-addons.md
- docs/skill-source-generation.md
- docs/getting-started-for-teams.md
- orchestration/contracts/platform-pack-schema.yaml
- orchestration/shell-content-contract/SCAFFOLD_PAYLOAD.md
- docs/observability-policy.md

## Next path

Run skill-bill goal SKILL-369 to execute the prepared subtask.
