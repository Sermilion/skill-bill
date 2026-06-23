---
name: bill-release
description: Cut a new Skill Bill release: generate a user-facing changelog from commits since the last tag, confirm with the user, then create and push the annotated semver tag to trigger the GitHub Release workflow. Use when user mentions cut a release, new release, release skill-bill, create release tag, or bump version.
---

# Release Skill Content

## Overview

This skill produces a curated user-facing changelog from commits since the last release, presents it for review, then creates and pushes an annotated semver tag. Pushing the tag kicks off the GitHub Release workflow that builds per-OS installer assets and publishes the GitHub Release.

## Steps

### 1. Pre-flight checks

Confirm the working tree is clean and the local `main` branch is up to date:

```bash
git status --short
git fetch origin
git log HEAD..origin/main --oneline
```

If uncommitted changes exist or commits remain to pull, surface them and ask the user how to proceed before continuing.

### 2. Find the previous release tag

```bash
git tag --sort=-version:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1
```

If no stable tag exists yet, use the root commit as the base (first commit SHA).

### 3. Gather commits since the previous tag

```bash
git log <prev-tag>..HEAD --oneline
```

Also pull richer context for merge commits:

```bash
git log <prev-tag>..HEAD --merges --format="PR: %s%n%b"
```

### 4. Generate the changelog draft

Categorize the commits using editorial judgment. Read RELEASING.md (versioning policy) and the commit messages to understand what is user-facing, important, or internal.

**Categories:**

- **New Features** — new user-visible capabilities (new skills, new commands, new runtime modes, new UX flows). Each gets its own bullet.
- **Bug Fixes** — notable, user-impacting fixes worth naming individually. Threshold: would a user notice or care? If yes, name it. Examples: broken command behavior, incorrect output, crashes, data loss, wrong state handling.
- **Other bug fixes and stability improvements** — one grouped bullet covering everything else (internal refactors, minor telemetry tweaks, test-only changes, doc cleanups, minor infra changes, dependency bumps). Do **not** itemize these.

Format:

```markdown
## What's New in vX.Y.Z

### New Features
- <Feature name>: <one-sentence description of the user-visible change>

### Bug Fixes
- <Fix name>: <one-sentence description of what was broken and is now fixed>

### Other
- Other bug fixes and stability improvements.
```

Omit any section that has no entries. If there are only minor changes, the entire changelog may collapse to the "Other" bullet alone.

Do not include:
- Internal implementation details
- Test-only changes
- Commit SHAs or PR numbers (unless the user prefers them)
- Passive voice or marketing language

### 5. Present the changelog inline

Show the draft changelog to the user. Ask for any edits or corrections before proceeding.

### 6. Determine the next version

Suggest a version bump based on RELEASING.md versioning policy:
- `patch` — docs-only, validator fixes, non-breaking tooling fixes
- `minor` — new skills, new commands, new runtime behavior, new user-visible capability
- `major` — intentional breaking changes to taxonomy, install behavior, or stable entry points

Skill Bill stays pre-1.0 until the install surface and taxonomy feel settled, so most releases are `minor` or `patch`.

Present the suggested version and ask the user to confirm or override.

### 7. Create the annotated tag

Ask the user to confirm before creating the tag: "Ready to create tag vX.Y.Z — shall I proceed?" Wait for an explicit yes before running the tag command.

```bash
git tag -a vX.Y.Z -m "Release vX.Y.Z"
```

### 8. Push the tag

Confirm with the user before pushing (this is irreversible and triggers the release workflow):

```bash
git push origin vX.Y.Z
```

After pushing, tell the user to watch the `Release` GitHub Actions workflow — it builds per-OS installer assets and publishes the GitHub Release automatically.

## Rules

- Never push the tag without explicit user confirmation.
- Never create the tag if the working tree has uncommitted changes.
- Never skip the user review of the changelog draft.
- If `git fetch` fails due to network issues, warn the user but let them decide whether to continue.
- Prerelease tags (`v0.5.0-rc.1`) are valid — the workflow publishes them as GitHub prereleases.
- The annotated tag message should be exactly `Release vX.Y.Z` — no changelog body in the tag message itself.
