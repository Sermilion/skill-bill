---
name: bill-php-quality-check
description: Run PHP project quality checks and systematically fix issues in changed files without suppressions, including tests, linting, static analysis, architecture checks, and dependency audits.
---

# PHP Quality Check

## Execution Steps

1. Determine changed files using `git diff --name-only` against the relevant base.
2. Run the project's quality-check commands and capture complete output.
3. Filter the results to issues in changed files only.
4. Categorize issues by type: structural, formatting, lint, static analysis, architecture checks, tests, security or audit failures.
5. Fix systematically by category in priority order.
6. Re-run the quality-check commands after all fixes.
7. Iterate if new issues appear.

## Fix Strategy

### Always Fix, Never Suppress

- Never add suppressions, baseline entries, or ignore rules as the default fix
- Never add `TODO` or `FIXME` comments to defer issues
- Never skip required project scripts silently
- Implement proper solutions that address the root cause
- Refactor code to eliminate warnings
- Add missing tests or fix failing ones

### Priority Order

0. Structural issues such as PSR-4 autoload, class/file location, or namespace mismatch
1. Formatting issues
2. Lint errors
3. Static analysis issues such as `phpstan`, `psalm`, type errors, or dead code
4. Architecture or boundary issues such as `deptrac`
5. Test failures
6. Security or dependency audit failures

### Structural Fixes

- PSR-4 or autoload mismatch:
  Move the file to match the declared namespace, or fix the namespace to match the intended path.
- File name does not match the top-level class, interface, trait, or enum:
  Rename the file to match the declaration and fix broken imports or usages afterwards.
- After moving or renaming files:
  Verify namespaces, rebuild autoload metadata if the project requires it, and re-run checks.

### When to Ask the User

- Architectural decisions with meaningful trade-offs
- Breaking API changes that affect multiple modules
- Test failures where the business logic is unclear
- Security-related issues requiring policy decisions
- Cases where multiple valid fix approaches exist and the repo does not make the preference obvious

### PHP-Specific Guidance

- Follow the project's formatter and coding-standard rules
- Prefer the project's wrapper command over bare tools when one exists
- In Laravel projects, prefer Sail commands when the repo is clearly Sail-based
- If the repo defines both fixer and verifier commands, run fixers before read-only analyzers when that reduces churn
- Common PHP quality commands may include tests, lint or lint-fix, `phpstan`, `psalm`, `deptrac`, and dependency audit
- If a required command cannot be run, report that explicitly with the reason
