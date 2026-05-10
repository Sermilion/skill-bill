---
name: bill-php-code-review-architecture
description: Use when reviewing PHP architecture, module boundaries, dependency direction, source-of-truth ownership, and framework coupling.
---

# Architecture Review Specialist

Review only high-signal architectural issues.

Focus on layer boundaries, dependency direction, module ownership, source-of-truth consistency, sync/async boundaries, and architectural drift. Ignore formatting/style-only and naming preferences without architectural impact.

Apply shared architecture rules to every review. Apply deeper concern-specific checks only when the changed code touches those areas.

## Project-Specific Rules

### Shared Architecture

- Keep business logic independent from transport and storage concerns unless the project intentionally uses a simpler shape
- Dependencies must point inward toward stable business rules, not outward toward frameworks or concrete infrastructure details
- Preserve a single source of truth for each important piece of business state; avoid duplicated ownership across layers or modules
- Do not leak framework-specific models or persistence-shaped data across unrelated boundaries when that creates tight coupling
- External systems should sit behind explicit adapters, repositories, clients, gateways, or other boundary types
- Prefer explicit dependencies and visible wiring over service locators, hidden globals, or framework magic that obscures ownership

### Architectural Pattern Checks

- When the project uses modular monolith, bounded contexts, or strong module ownership, each module should own its own data and writes; cross-module writes are architectural smells unless the project explicitly chooses a simpler structure
- When the project uses DDD, layered, hexagonal, or clean-architecture boundaries, domain should not depend on infrastructure or transport details
- Application/use-case code should orchestrate behavior, not hide business workflows inside controllers, request objects, ORM models, or infrastructure listeners
- One business operation should have one clear use-case owner and one clear transaction owner unless the project explicitly models partial success or eventual consistency
- When the project uses CQRS-style read boundaries, read paths should use explicit read gateways or other declared query boundaries rather than leaking persistence access into higher layers
- Cross-module or cross-boundary composition should happen in consumers or dedicated coordinators, not by reaching directly into another boundary's internals
- When the project uses ports/adapters, consumers should define thin, explicit contracts and providers should implement them without pulling consumer orchestration logic back into the provider

### Data Ownership / Read Composition / Persistence Boundaries

- Repository and gateway boundaries must not leak query-builder details, ORM sessions, transaction handles, or persistence records into higher layers unless that is an explicit architectural choice
- Direct cross-boundary reads, cross-module joins, and cross-module ORM leakage are high-severity smells when the architecture expects ports, projections, or single-source read gateways
- Cross-boundary enrichment should happen in higher-level handlers/services with explicit ports, not inside low-level gateways
- Hot or repeated cross-boundary reads should prefer explicit projections, read models, caches, or batched boundary calls over convenience coupling
- Query/read boundaries should stay single-purpose and single-source; low-level read abstractions should not quietly become multi-source orchestration hubs

### Events / Outbox / Integration Architecture

- Domain events, integration events, and projections should have distinct roles; do not blur cross-module messaging with in-module business logic
- Cross-module or replayable reactions should use explicit integration-event or messaging boundaries rather than hidden synchronous coupling
- When the project uses CQRS without event sourcing, keep write-side business logic separate from read-side projections and derived views
- Reliable publish-after-commit flows should keep event persistence and business state changes in the same transaction when the architecture expects an outbox pattern
- Projectors and read-model updaters should update derived state only, safely and atomically; they must not contain business workflows or rely on read-modify-write convenience flows
- Event handler type should match intent: business-operation listeners trigger use cases, while projectors update derived state only
- Event-triggered business work should converge on the same application/use-case boundaries instead of duplicating business logic in multiple places

### Transport / Entry-Point Orchestration

- Controllers, routes, RPC handlers, actions, and server-rendered entry points should stay thin: derive context, validate input, call a use case, and map the response
- Entry points should not become hidden composition roots for cross-module reads, transaction management, or business workflows when the architecture expects dedicated application boundaries
