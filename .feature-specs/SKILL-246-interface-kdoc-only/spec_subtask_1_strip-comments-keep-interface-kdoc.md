# SKILL-246 Subtask 1 - Strip comments keep interface KDoc

Parent spec: [.feature-specs/SKILL-246-interface-kdoc-only/spec.md](./spec.md)
Issue key: SKILL-246

## Scope

Sweep authored `.kt` and `.kts` files under `runtime-kotlin`,
`intellij-plugin`, and `runtime-kotlin/build-logic`. Delete `//` line comments
and `/* */` block comments. Keep `/** */` KDoc only on `interface` types and
members declared on those interfaces.

Add an architecture test on those same roots that fails on leftover line
comments, block comments, or KDoc attached to non-interface declarations.
Move "Comment quality and density" off the review-only inventory for this
narrower, scanable rule.

Rewrite architecture-test fixtures that embed live comment syntax so the ban
can be total. Allow-list a fixture file only when live comment syntax is
physically required, with a one-line why.

Update AGENTS.md, CLAUDE.md, `docs/code-principles.md`, and the shared
shell-ceremony comment section so they match the enforced rule.

Do not rewrite Markdown, YAML, JSON, shell, or TypeScript. Do not scan
generated sources or build output. Do not add KDoc to interfaces that have
none.

## Acceptance Criteria

1. Scanned authored Kotlin under the three roots contains no `//` comments and
   no `/* */` block comments.
2. Remaining KDoc is only on interfaces and their members.
3. The architecture test fails when a scanned file reintroduces a forbidden
   comment or non-interface KDoc.
4. Policy docs listed in scope state the same rule as the test.

## Non-Goals

- Comment removal outside Kotlin.
- New interface KDoc.
- Detekt undocumented-public rules.
- Behavior or type changes unrelated to comment removal.
- Treating a later-phase quality-check receipt as an audit or implement
  criterion.

## Dependency Notes

Depends on: none
First subtask on `main`.

## Validation Strategy

Add the architecture test first so the sweep has a failing gate, then clear
every finding. Dominant-stack quality check runs in validate, not as an
audit criterion.

## Next Path

None. This is the only subtask.

## Spec Path

.feature-specs/SKILL-246-interface-kdoc-only/spec_subtask_1_strip-comments-keep-interface-kdoc.md
