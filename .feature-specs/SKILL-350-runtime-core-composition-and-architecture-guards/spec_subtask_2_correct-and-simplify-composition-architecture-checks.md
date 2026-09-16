# SKILL-350 Subtask 2 - Correct and simplify composition architecture checks

Parent spec: [.feature-specs/SKILL-350-runtime-core-composition-and-architecture-guards/spec.md](./spec.md)
Issue key: SKILL-350

## Scope

Address F-003, F-004, and F-005 in [investigation.md](investigation.md).

RuntimeComponentInboundApiArchitectureTest pins abstract property names only. Keep that useful curated service inventory, but state its actual scope. Cover unclassified public callables on the component and inherited provider mixins. Required Kotlin-Inject wiring methods are a separate, constrained integration surface. Generated CLI and MCP code currently needs methods such as runtimeContext, optionalCallbacks, runtimeClock, and databaseSessionFactory. Preserve those consumers. Do not hide all providers or copy 120 method signatures into a second manual table. Enforce an explicit rule for legitimate provider methods and reject ordinary public helpers outside that rule, including inherited helpers. Document unavoidable concrete types on the wiring surface instead of claiming the service property list proves an infrastructure-free complete ABI.

ArchitectureScanGuardSupport currently identifies binding parameter types through a list of parameter names and looks for constructor calls by unqualified class name. Replace those assumptions with a bounded census based on declared provider type information and import-aware construction matching. Preserve concrete binding identity through alias imports. Add only the syntax handling needed for these boundaries and test positive and negative fixtures through the real entry points.

Consolidate the duplicated expected dependency graph in RuntimeCoreCompositionOnlyTest and RuntimeAdapterDependencyAllowlistTest. Derive the expected module-name set used by RuntimeGradleModuleLayeringTest from the same test-owned authority. RuntimeModuleCatalog may remain the owner if its responsibilities stay clear. Actual Gradle declarations remain independent evidence. Keep API, implementation, and test-fixture edges separate, and preserve the public ABI closure and layer-direction assertions.

Move the runtime-core serialization JSON dependency to testImplementation if the current source census still shows only test-side access through JsonCodec.json. Preserve compilation rather than deleting a needed test dependency. Update architecture documentation and affected enforcement inventory entries without adding exemptions.

## Acceptance Criteria

1. A newly added ordinary public function on RuntimeComponent or a provider mixin fails the composition API check even when the abstract property set is unchanged. Existing required generated provider calls remain allowed under an explicit integration rule.
2. The service-property check continues to reject added or removed service properties. Documentation distinguishes this logical API from inherited provider methods and generated integration members.
3. Renaming a binding parameter from adapter to implementation preserves the bound class census. The real census entry point has acceptance and rejection coverage for the provider forms used by this module.
4. The same outside-composition constructor call is rejected with a direct import or alias import. A same-named function unrelated to a bound type, a string containing constructor-like text, and comments do not cause false positives. Sanctioned composition entrypoints remain explicit.
5. One expected module graph owns configuration-specific edges. Exact-edge, aggregate dependency, module-set, and layer checks consume that authority without deriving expected policy from actual build files. Test-fixture expectations remain separate from production edges.
6. Added, removed, and configuration-reclassified project edges still fail through the real check path. Consolidation removes duplicate expected topology tables without deleting those rejection behaviors.
7. The JSON serialization dependency is test-scoped where no authored or generated production use remains. Production compilation, test compilation, and generated CLI/MCP integration still have the dependencies they require.
8. Guard documentation names checked forms and limitations. No architecture baseline grows, no warning is suppressed, and required schema, parity, and transaction checks remain.

## Non-Goals

- Runtime context and executable lookup fixes, which belong to subtask 1.
- A new compiler plugin, general Kotlin parser, architecture framework, public component facade, or module split.
- Removing a provider, port, or service solely because handwritten source has no reference to its property name.
- Broad test deduplication or test deletion based on line counts.

## Dependency Notes

Depends on: none
No dependency on subtask 1. Enforcement corrections and topology simplification can ship independently. Accommodate any provider signature changes from subtask 1 without hardcoding the pre-fix graph.

## Validation Strategy

Run the real scanner against the alias, renamed-parameter, inherited-helper, and negative syntax fixtures described in evidence/guard-results.txt. Preserve actual-versus-expected Gradle checks and their added, removed, and reclassified-edge rejection cases. Run runtime-core tests and regenerate/compile CLI and MCP components to protect parent-provider integration. Confirm dependency scopes through production and test compilation. Apply bill-unit-test-value-check, then the governed quality gate for the phase.

## Next Path

Complete SKILL-350 through the goal runtime after both subtasks finish.

## Spec Path

.feature-specs/SKILL-350-runtime-core-composition-and-architecture-guards/spec_subtask_2_correct-and-simplify-composition-architecture-checks.md
