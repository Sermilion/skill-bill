package skillbill.boundary

/**
 * SKILL-52.1 — Marker annotation for public declarations on
 * `runtime-application`, `runtime-domain`, and `runtime-ports` that
 * intentionally accept or return a raw `Map<String, Any?>` /
 * `Map<String, *>` / `MutableMap<String, Any?>` because they sit on an
 * open boundary (schema custom fields, MCP input argument maps before
 * the parse seam, contract helper serializer internals, durable
 * artifacts passthrough, etc.).
 *
 * The architecture test
 * `RuntimeRawMapArchitectureTest.runtime architecture forbids raw map shapes
 * outside the open-boundary allowlist` will fail loudly when a public
 * runtime declaration uses one of these raw map shapes unless it is
 * either (a) listed by FQN in
 * `RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST` in
 * `runtime-core/src/test/kotlin/skillbill/architecture/RuntimeArchitectureTestSupport.kt`,
 * or (b) annotated with `@OpenBoundaryMap`.
 *
 * The annotation lives in `runtime-domain` (the leaf module that both
 * `runtime-application` and `runtime-ports` depend on) so all three
 * modules can apply it without creating a circular dependency. The
 * annotation sits in `skillbill.boundary` — its own package — so it
 * does not collide with the area-specific `*.model` packages and is
 * not split across modules.
 *
 * Do NOT introduce new uses of this annotation without first adding the
 * FQN to `RAW_MAP_OPEN_BOUNDARY_ALLOWLIST` and updating the SKILL-52.2
 * inventory resource when classification changes.
 */
@Target(
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.CLASS,
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class OpenBoundaryMap(val reason: String = "")
