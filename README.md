# Skill Bill

Skill Bill is a framework-first, governed collection of 26 AI skills for portable agent workflows. The product is the framework: stable base commands, routing/orchestration contracts, scaffolding, validation, telemetry, and cross-agent installation.

The framework supports zero-pack checkouts as a first-class state, and this repo currently also includes the reference `kotlin`, `backend-kotlin`, and `kmp` packs to demonstrate the shell+content contract, layered routing, and governed add-on patterns.

Rolling out to a team? Start with [Getting Started for Teams](docs/getting-started-for-teams.md).

## Why this exists

Most prompt repos drift over time:

- names drift
- runtime behavior diverges across agents
- stack-specific logic leaks into generic commands
- nobody knows which rules are real contracts vs folklore

Skill Bill treats skills more like software:

- stable base capabilities
- shared routing and orchestration contracts
- validator-backed repository rules
- one source of truth synced across supported agents

## What the core product looks like

The main entry points are generic:

- `/bill-code-review`
- `/bill-feature-implement`
- `/bill-feature-verify`
- `/bill-quality-check`
- `/bill-pr-description`

When optional packs are present, the code-review and quality-check shells discover them from `platform-packs/<slug>/platform.yaml`. When no packs are installed, both shells report the explicit framework-only core state instead of assuming bundled coverage exists.

## Installation

```bash
git clone https://github.com/Sermilion/skill-bill.git ~/Development/skill-bill
cd ~/Development/skill-bill
chmod +x install.sh
./install.sh
```

The installer always installs the core base skills. If optional packs are present in your checkout, it offers them as optional selections using each pack's manifest `display_name` when provided. If no packs are present, it reports that the framework-only core is being installed.

Installed skills are symlinks back to the repo, so updates stay in sync.

## Optional packs

`platform-packs/` is an extension point, not a required shipped catalog.

- Missing or empty `platform-packs/` is valid.
- Optional packs are discovered from manifests; core shells never hardcode pack names.
- This checkout currently includes the reference `kotlin`, `backend-kotlin`, and `kmp` packs.
- Pack authors must follow the contract in `orchestration/shell-content-contract/PLAYBOOK.md`.
- Use `skill-bill new-skill` to scaffold new base skills, optional-pack skills, and add-ons.

## Review telemetry

Skill Bill records workflow telemetry locally and can optionally sync through a relay or custom proxy. See [docs/review-telemetry.md](docs/review-telemetry.md).

## Supported agents

| Agent | Install path |
|-------|--------------|
| GitHub Copilot | `~/.copilot/skills/` |
| Claude Code | `~/.claude/commands/` |
| GLM | `~/.glm/commands/` |
| OpenAI Codex | `~/.codex/skills/` or `~/.agents/skills/` |
| OpenCode | `~/.config/opencode/skills/` |

## Uninstallation

```bash
chmod +x uninstall.sh
./uninstall.sh
```

## Reference skill catalog

The repo currently ships the governed base catalog plus the bundled reference packs below.

### Core skills (12 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-boundary-decisions` | Capture and curate persistent technical decisions for a boundary |
| `/bill-boundary-history` | Record implementation history for a boundary |
| `/bill-code-review` | Generic code-review shell that routes through optional packs when present |
| `/bill-feature-guard` | Define a feature-flag rollout pattern |
| `/bill-feature-guard-cleanup` | Remove a feature flag and its dead paths after rollout |
| `/bill-feature-implement` | End-to-end feature implementation workflow |
| `/bill-feature-verify` | Verify implemented work against the requested contract |
| `/bill-grill-plan` | Critique and improve an implementation plan |
| `/bill-new-skill-all-agents` | Scaffold a new governed skill and install it into detected agents |
| `/bill-pr-description` | Generate a pull request description |
| `/bill-quality-check` | Generic quality-check shell that routes through optional packs when present |
| `/bill-unit-test-value-check` | Evaluate whether unit tests add meaningful value |

### Kotlin reference pack (7 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-kotlin-code-review` | Baseline Kotlin code-review pack with delegated specialist routing |
| `/bill-kotlin-code-review-architecture` | Kotlin specialist review for architecture risks |
| `/bill-kotlin-code-review-performance` | Kotlin specialist review for performance and scaling risks |
| `/bill-kotlin-code-review-platform-correctness` | Kotlin specialist review for platform and correctness risks |
| `/bill-kotlin-code-review-security` | Kotlin specialist review for security risks |
| `/bill-kotlin-code-review-testing` | Kotlin specialist review for testing strategy and coverage risks |
| `/bill-kotlin-quality-check` | Kotlin quality-check pack for the Gradle/Kotlin toolchain |

### Backend-Kotlin reference pack (4 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-backend-kotlin-code-review` | Backend-Kotlin baseline review layered on the Kotlin baseline |
| `/bill-backend-kotlin-code-review-api-contracts` | Backend-Kotlin specialist review for API contract risks |
| `/bill-backend-kotlin-code-review-persistence` | Backend-Kotlin specialist review for persistence and data-layer risks |
| `/bill-backend-kotlin-code-review-reliability` | Backend-Kotlin specialist review for reliability and operational risks |

### KMP reference pack (3 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-kmp-code-review` | Baseline KMP code-review pack that layers the shared Kotlin baseline and governed Android add-ons |
| `/bill-kmp-code-review-ui` | KMP specialist review for Compose/UI implementation concerns |
| `/bill-kmp-code-review-ux-accessibility` | KMP specialist review for user-facing UX and accessibility concerns |
