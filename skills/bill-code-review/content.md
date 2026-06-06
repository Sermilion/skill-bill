---
name: bill-code-review
description: Use when you want a generic code-review entry point that detects the dominant stack in scope and delegates to the matching stack-specific review skill. Use when user mentions code review, review my changes, review this PR, review staged changes, or asks to review code.
---

# Adaptive Code Review

## Modifier Intake

If args include `parallel_review:<agent_id>`:

1. Extract `<agent_id>` from the modifier.
2. Validate against the supported-agent list: `copilot`, `claude`, `codex`, `opencode`, `junie`.
   - If `<agent_id>` is blank, unknown, or duplicates the invoking agent, stop immediately and output:
     > Error: unsupported parallel_review agent id. Supported agents: copilot, claude, codex, opencode, junie.
   - Do not proceed with any review pass.
3. Retain the validated modifier for forwarding in the delegation step below.

## Stack Detection and Delegation

Inspect the changed files and repo markers to determine the dominant stack:

- **Kotlin / KMP**: `build.gradle*`, `settings.gradle*`, `.kt` files, KMP/Android imports → delegate to `bill-kotlin-code-review`, or `bill-kmp-code-review` when Android/KMP signals are strong.
- **PHP**: `composer.json`, `*.php` files → delegate to `bill-php-code-review`.
- **Other stacks**: apply file-extension and framework-marker heuristics to identify the best available stack-specific review skill.

When delegating:
- If the `parallel_review:<agent_id>` modifier is present, forward it in the invocation args to the resolved stack-specific skill.
- If no modifier is present, delegate identically to today — do not alter the downstream invocation.
