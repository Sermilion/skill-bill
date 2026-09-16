# SKILL-350 - runtime-core-composition-and-architecture-guards

## Mode

decomposed

## Intended Outcome

Give each runtime component stable invocation inputs, route executable discovery through the injected port, and make composition checks accurate with fewer duplicated architecture declarations.

## Overview

The runtime-core module has a sound role as the outer composition root. It contains 21 production Kotlin files and 967 lines, with 120 provider declarations and 47 abstract service or port properties. Its concrete adapter dependencies belong here. The investigation found two executable wiring defects, two gaps in architecture enforcement, and duplicated architecture declarations.

See [investigation.md](investigation.md) for source evidence, the Clean Architecture and SOLID assessment, public engineering references, rejected refactors, and scope limits. See [evidence/validation.md](evidence/validation.md) for the 442-test run and isolated defect probes.

| Finding | Required outcome | Subtask |
| --- | --- | --- |
| F-001 | One stable resolved context and transport per component | 1 |
| F-002 | One executable lookup decision across preflight and default launch | 1 |
| F-003 | Accurate service API and generated wiring API enforcement | 2 |
| F-004 | Composition guard survives aliases and provider parameter renames | 2 |
| F-005 | One expected module graph and test-scoped serialization dependency | 2 |

Two subtasks can ship independently. The first repairs production composition behavior. The second changes architecture enforcement and removes duplicate declarations without depending on those runtime repairs. Each includes its own regression coverage and documentation. These are separate deliverables, not a split by layer or file count.

Prepared on 2026-09-16 in local spec mode. SKILL-350 follows the highest local key, SKILL-349. The user authorized selecting the next available key. The investigation began at 0a69235455abaf55780da1d7d039ad0a59c50277 while SKILL-349 work continued in the shared checkout. The core production source hashes remained unchanged during investigation. Concurrent work later changed an unrelated failure-code architecture test, recorded in evidence/final-source-check.txt. Evidence records the actual file census and test baseline. All subtasks start pending.

## Acceptance Criteria

1. Every service and generated child component built from one RuntimeComponent observes the same resolved invocation context. Resolving another service cannot change the component repository, home, environment, or selected requester.
2. A non-default connect timeout creates one component-owned requester for that component. Supplied requesters and callbacks retain their identity. Independent components retain independent configuration and state.
3. The default FileSystemAgentRunLauncher consumes the ExecutableLookup selected by RuntimeComponent. A supplied refusal prevents the controlled executable from starting, even when that executable exists on the host PATH.
4. The component API check distinguishes the curated service properties from required generated wiring callables. It rejects an unclassified public helper, including an inherited helper, and does not mistake JvmSynthetic for Kotlin access control.
5. The composition construction check detects direct and aliased construction of the same bound class. Renaming a provider parameter cannot remove its concrete class from the checked set. Ordinary same-named functions, comments, and string literals do not become false constructor findings.
6. One test-owned expectation defines module names and configuration-specific project dependency edges. Other architecture assertions derive their expected views from it and still compare those expectations against independently read Gradle declarations. Test fixture edges remain distinct.
7. Production runtime-core no longer declares the serialization JSON library solely for test access. Required test compilation and the existing generated DI integration remain supported.
8. Documentation describes actual guard scope and the reproduced limits. Required schema checks, typed failures, lease fencing, transaction tests, and architecture rejection coverage remain intact without broader baselines or suppressions.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles, docs/code-principles.md, and AGENTS.md. Keep runtime-core as the composition root and keep the existing module graph and Kotlin-Inject.
- Keep generated providers and native-agent outputs out of committed source. Change authored providers and regenerate through KSP. Do not hand-edit InjectRuntimeComponent, InjectCliComponent, or InjectMcpComponent.
- Use the existing component lifetime and ExecutableLookup port. Do not add a global context cache, service locator, second DI graph, new module, generic lifetime manager, or an interface for each provider.
- Preserve real CLI and MCP consumers of generated parent providers. Names without handwritten call sites can still have generated consumers. Do not delete them based only on source text searches.
- Fix only the identified syntax gaps in architecture checks. Preserve independent actual-versus-expected evidence. Do not build a general Kotlin parser or adopt a large architecture framework for these cases.
- Keep the production compatibility surface and defaults unless a criterion explicitly corrects the demonstrated mismatch. OptionalCallbacks.executableLookup stays authoritative when supplied. This port is availability policy, not a process sandbox.
- Tests must name the concrete regression and exercise the generated graph or the real scanner entry point. Use temporary homes and controlled executable fixtures. Do not depend on whether an agent CLI happens to be installed.
- This bundle prepares work only. Do not implement, commit, push, launch a goal, or modify concurrent SKILL-349 work while preparing it. Implementation must recheck the recorded source hashes and current contracts.
- Update owning architecture documentation with implemented behavior. Use bill-unit-test-value-check for new or changed tests and the pack-declared bill-code-check gate during implementation.

## Non-Goals

- A rewrite of runtime-core or a claim to satisfy private Reddit, Microsoft, or Meta engineering standards.
- Replacing Kotlin-Inject, moving business policy into runtime-core, deleting adapter ports because they have one implementation, or merging all provider mixins into one file.
- Scoping every service as a singleton, caching mutable telemetry settings, adding HTTP pooling, or redesigning process and database resource ownership outside the demonstrated composition defects.
- A blanket rewrite or relocation of the 79 test and support files, a replacement scanner framework, or removal of governed schema and parity coverage.
- Fixing unrelated runtime-contracts inline-reference violations recorded in the baseline test run.

## Validation Strategy

During implementation, run behavior tests through the generated component for context stability, requester reuse, separate-component isolation, and refusal through the supplied executable lookup. Regenerate and compile CLI and MCP child graphs. For architecture changes, exercise the real census and scan entry points with direct and aliased imports, renamed provider parameters, inherited public helpers, and permitted generated providers. Compare Gradle declarations against one independent expected topology and retain rejection tests for added, removed, and reclassified edges.

Run the runtime-core suite, affected launcher coverage, and the pack-declared quality gate appropriate to the implementation phase. Apply bill-unit-test-value-check to changed tests. Preparation evidence is not an implementation quality-gate receipt. The initial audit run completed 442 tests with one out-of-scope InlineFqnArchitectureTest failure, detailed in evidence/validation.md.
