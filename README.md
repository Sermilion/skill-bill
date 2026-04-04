# sKill Bill

Treat your AI skills like software — with stable interfaces, platform overrides, and validation that prevents the repo from rotting.

sKill Bill is a portable collection of 46 AI skills for code review, feature implementation, and developer tooling. One repo, synced to every supported agent. Currently strongest for Kotlin, Android/KMP, Kotlin backend/server, PHP backends, Go backends/services, and governed skill/agent-config repositories.

## Why this exists

Most prompt or skill repos degrade over time:

- names drift
- overlapping skills appear
- stack-specific behavior leaks into generic prompts
- different agents get different copies

sKill Bill treats skills more like software:

- stable base capabilities
- platform-specific overrides
- shared routing logic
- CI-enforced naming and structure
- one repo synced to every supported agent

## What it looks like

You interact through a handful of stable base commands. They auto-detect your stack and route to the right specialists.

**Code review** — one command, stack-aware specialist reviews:

```
/bill-code-review

Review session ID: rvs-20260402-221530
Review run ID: rvw-20260402-221530
Detected stack: kotlin
Routed to: bill-kotlin-code-review
Execution mode: inline
Applied learnings: none
Specialist reviews: architecture, platform-correctness, testing

### 2. Risk Register
- [F-001] Major | High | app/src/main/java/...:42 | Shared state mutation is not protected by synchronization.
- [F-002] Major | Medium | app/src/main/java/...:88 | ViewModel scope is used from the wrong thread context.
- [F-003] Minor | High | app/src/test/...:17 | Error-path coverage is missing for the new branch.
```

**Feature implementation** — end-to-end from design doc to PR:

```
/bill-feature-implement

1. Collects design doc, creates acceptance criteria
2. Creates branch, plans implementation tasks
3. Implements each task atomically
4. Runs /bill-code-review (auto-routed to your stack)
5. Completeness audit against acceptance criteria
6. Runs /bill-quality-check (auto-routed)
7. Generates PR description
```

**Quality check** — auto-routed to your stack's toolchain:

```
/bill-quality-check

Detected stack: kotlin
Routed to: bill-kotlin-quality-check

Running ./gradlew check...
Build: PASS
Tests: 247 passed
Lint: PASS
```

## How routing works

A single `feature-implement` run chains 10-12 skill invocations:

```
/bill-feature-implement
├── plan + acceptance criteria
├── implementation (atomic tasks)
├── /bill-code-review (auto-routed)
│   └── e.g. bill-kotlin-code-review
│       ├── execution mode: inline or delegated
│       ├── architecture (inline pass or subagent)
│       ├── platform-correctness (inline pass or subagent)
│       ├── security (inline pass or subagent, if applicable)
│       └── testing (inline pass or subagent, if applicable)
├── /bill-quality-check (auto-routed)
│   └── e.g. bill-kotlin-quality-check
├── completeness audit
└── /bill-pr-description
```

Small, low-risk review scopes may stay inline in one thread. Larger or higher-risk scopes use delegated review passes and report the chosen execution mode explicitly.

Base entry points stay stable for users:

- `/bill-code-review` routes to `bill-agent-config-code-review` | `bill-kotlin-code-review` | `bill-backend-kotlin-code-review` | `bill-kmp-code-review` | `bill-php-code-review` | `bill-go-code-review`
- `/bill-quality-check` routes to the matching stack-specific quality checker
- `/bill-feature-implement` orchestrates the full workflow

## Review telemetry

Skill Bill can record a measurement loop for code-review usefulness, but telemetry is now an explicit opt-in runtime path.

- each top-level review session should expose a `Review session ID: ...` using `rvs-YYYYMMDD-HHMMSS`
- each concrete review output should expose a `Review run ID: ...` using `rvw-YYYYMMDD-HHMMSS`
- each finding in `### 2. Risk Register` should use `- [F-001] Severity | Confidence | file:line | description`
- feedback history and learnings stay local in SQLite regardless of telemetry state

The helper lives in this repo:

```bash
python3 scripts/review_metrics.py --help
```

Default database path:

```text
~/.skill-bill/review-metrics.db
```

Default config path:

```text
~/.skill-bill/config.json
```

You can override the database path with `--db` or `SKILL_BILL_REVIEW_DB`.

Typical workflow:

1. Save a review output to a text file.
2. Import the review so the run and findings are stored locally.
3. Use numbered triage to respond with issue numbers instead of raw finding ids.
4. Optionally store reusable learnings separately from raw feedback history.
5. Resolve active learnings for the next review context when you want that feedback to influence future reviews explicitly.
6. Query summary stats for one run or for all imported runs.

Example:

```bash
python3 scripts/review_metrics.py import-review review.txt
python3 scripts/review_metrics.py triage --run-id rvw-20260402-001
python3 scripts/review_metrics.py triage --run-id rvw-20260402-001 --decision "1 fix - keep current terminology" --decision "2 skip - intentional"
python3 scripts/review_metrics.py triage --run-id rvw-20260402-001 --decision "all fix"
python3 scripts/review_metrics.py learnings resolve --repo Sermilion/skill-bill --skill bill-agent-config-code-review --review-session-id rvs-20260402-001
python3 scripts/review_metrics.py stats --run-id rvw-20260402-001 --format json
```

The `triage` command maps the visible numbers back to the stable `F-001` ids internally. Use `all <action>` to apply the same action to every finding. Supported triage actions are:

- `fix` -> records `fix_applied`
- `accept` -> records `finding_accepted`
- `edit` -> records `finding_edited`
- `skip`, `dismiss`, or `reject` -> records `fix_rejected`
- `false positive` -> records `false_positive`

You can still use the low-level command when you want direct control:

```bash
python3 scripts/review_metrics.py record-feedback --run-id rvw-20260402-001 --event fix_applied --finding F-001 --note "keep current terminology"
```

Learnings are actionable domain-specific knowledge derived from **rejected** review findings. When you reject a finding and explain why, you can promote that rejection into a reusable learning so future reviews avoid the same mistake.

```bash
# First reject a finding during triage:
python3 scripts/review_metrics.py triage --run-id rvw-20260402-001 --decision "2 reject - installer wording is intentionally informal"

# Then promote the rejection into a learning:
python3 scripts/review_metrics.py learnings add --scope repo --scope-key Sermilion/skill-bill --title "Installer wording is intentionally informal" --rule "Do not flag installer prompt wording as inconsistent — the informal tone is a deliberate UX choice for CLI tools." --from-run rvw-20260402-001 --from-finding F-002

# Manage learnings:
python3 scripts/review_metrics.py learnings list
python3 scripts/review_metrics.py learnings show --id 1
python3 scripts/review_metrics.py learnings edit --id 1 --reason "Confirmed by repeated skip feedback."
python3 scripts/review_metrics.py learnings disable --id 1
python3 scripts/review_metrics.py learnings delete --id 1
```

Both `--from-run` and `--from-finding` are required — learnings must trace back to a rejected finding. When `--reason` is omitted, the rationale is auto-populated from the rejection note.

Raw finding-outcome history and learnings are stored separately. That means you can wipe or disable reusable learnings without losing the original review-feedback history.

When you want future reviews to use those learnings explicitly, resolve the active learnings for the current review context:

```bash
python3 scripts/review_metrics.py learnings resolve --repo Sermilion/skill-bill --skill bill-agent-config-code-review --review-session-id rvs-20260402-001 --format json
```

Resolution stays local-first and explicit:

- only `active` learnings apply
- precedence is `skill > repo > global`
- the helper returns stable learning references such as `L-003`
- `--review-session-id` is required when telemetry is enabled so the resolved-learning event can be grouped with the matching review session
- the top-level code-review caller owns learnings resolution and passes the applied references through routed/delegated reviews
- review output should surface `Applied learnings: ...` so the behavior is auditable

This is intentionally not hidden auto-learning. The learnings layer remains inspectable, editable, disable-able, and deletable by the user.

The core review telemetry model is:

- one `skillbill_review_finished` event when a review lifecycle becomes fully resolved

The terminal payload includes the final run-level totals, the latest per-finding outcome rollup for that completed lifecycle, and a distinct canonical `review_session_id` field so related telemetry can be grouped together in PostHog without collapsing session identity into the run id. If a later import materially changes the review and reopens unresolved findings, Skill Bill clears the finish marker and emits a fresh `skillbill_review_finished` event the next time that review becomes fully resolved. The most useful metrics to watch first are accepted vs rejected counts, rejected severity mix, and rejected finding details grouped by routed skill / review platform.

Learning telemetry stays low-noise as well:

- one `skillbill_learnings_resolved` event when resolved learnings are applied for a review context
- the event includes the applied learning references, counts, and the readable learning content (`title`, `rule_text`, `rationale`) so the resolved guidance is visible in PostHog without restoring per-learning event spam
- when `learnings resolve` is called with `--review-session-id`, the event also carries `review_session_id` so it can be grouped with the matching `skillbill_review_finished` event

### Remote sync defaults

Fresh installs still default telemetry to enabled, with an opt-out prompt during `./install.sh`. When telemetry is enabled, Skill Bill generates an install id, writes telemetry config to `~/.skill-bill/config.json`, and can batch-sync queued telemetry to the hosted Skill Bill relay. If you configure a custom proxy, Skill Bill sends telemetry to that proxy only.

- enabled telemetry can enqueue local telemetry events in SQLite before sync
- the helper can batch-sync pending events automatically after local writes to the hosted relay, or to a configured custom proxy override
- if the remote destination is missing or unavailable, local workflows still succeed and the enabled telemetry outbox stays pending
- disabled telemetry is a no-op: no telemetry config is required, no telemetry events are queued locally, and telemetry payload-building is skipped
- `python3 scripts/review_metrics.py telemetry disable` removes local telemetry config and clears any queued telemetry events without deleting non-telemetry review data

Default hosted relay:

- `https://skill-bill-telemetry-proxy.skillbill.workers.dev`
- used automatically when no custom proxy is configured

Custom proxy setup for your own deployment:

- deploy the example Worker in `docs/cloudflare-telemetry-proxy/`
- set it with `SKILL_BILL_TELEMETRY_PROXY_URL`
- keep the backend credential only in the Worker secret store
- when set, the custom proxy becomes the only remote telemetry destination

Telemetry commands:

```bash
python3 scripts/review_metrics.py telemetry status
python3 scripts/review_metrics.py telemetry enable
python3 scripts/review_metrics.py telemetry disable
python3 scripts/review_metrics.py telemetry sync
```

What gets sent:

- import-time review run snapshots with aggregate finding counts, accepted/rejected totals, severity buckets, routed skill/platform context, and rejected-finding details
- finding outcome events such as `finding_accepted`, `fix_applied`, `finding_edited`, `fix_rejected`, and `false_positive`
- applied-learning resolution metadata such as count, references, and scope mix
- the readable learning content (`title`, `rule_text`, `rationale`) for the specific learnings included in a `skillbill_learnings_resolved` event

What does not get sent:

- repository identity
- raw review text
- local-only learning bookkeeping events such as add, edit, disable, and delete

Proxy configuration:

```bash
export SKILL_BILL_TELEMETRY_PROXY_URL="https://your-worker.your-subdomain.workers.dev"
export SKILL_BILL_TELEMETRY_ENABLED="true"                  # optional override
export SKILL_BILL_TELEMETRY_BATCH_SIZE="50"                # optional override
export SKILL_BILL_CONFIG_PATH="$HOME/.skill-bill/config.json"  # optional override
```

When telemetry is enabled, the local config stores the generated install id used as the anonymous event `distinct_id`. You can edit `~/.skill-bill/config.json` directly if you want to keep the hosted relay or replace it with your own proxy target, but the supported way to opt out is `python3 scripts/review_metrics.py telemetry disable`.

## Supported agents

| Agent | Install path |
|-------|--------------|
| GitHub Copilot | `~/.copilot/skills/` |
| Claude Code | `~/.claude/commands/` |
| GLM | `~/.glm/commands/` |
| OpenAI Codex | `~/.codex/skills/` or `~/.agents/skills/` |

The installer links all selected agents to the same repo so updates stay in sync.

## Installation

```bash
git clone https://github.com/Sermilion/skill-bill.git ~/Development/skill-bill
cd ~/Development/skill-bill
chmod +x install.sh
./install.sh
```

If you want a stable install target instead of tracking `main`, clone a release tag and install from that checkout:

```bash
TAG=v0.x.y
git clone --branch "$TAG" --depth 1 https://github.com/Sermilion/skill-bill.git ~/Development/skill-bill
cd ~/Development/skill-bill
./install.sh
```

The installer first asks which agent targets to install to. You can choose one or more entries, including `all`:

```text
all
```

It then shows the available **optional** platform packages and asks which ones to install. Base skills in `skills/base/` and the governed `agent-config` package are always installed; the remaining platform packages are installed only when selected. The primary input path is **comma-separated numbers**, though platform names still work too.

Available options are shown as separate entries:

```text
1. Kotlin backend
2. Kotlin
3. KMP
4. PHP
5. Go
6. all
```

Example platform selections:

```text
1,2,3
4
5
6
```

Finally, the installer asks for the **user-facing command prefix**. Press Enter to keep the default `bill` prefix, or enter your own team/org prefix:

```text
bill
acme
platform
```

Canonical in-repo skill names stay `bill-*`. A custom prefix changes only the installed command names (for example `acme-code-review`) and rewrites installed skill references so routed workflows still resolve under that namespace.

Each installer run replaces the existing Skill Bill installs and reinstalls only the agent and platform selections from that run.

The installer always removes existing Skill Bill installs before reinstalling the selected agents and platforms. The default `bill` prefix keeps the current symlink-based install behavior. Custom prefixes install generated alias copies, so re-run `./install.sh` after editing skills in the repo.

## Uninstallation

To remove Skill Bill skill installs from the supported agent install paths:

```bash
chmod +x uninstall.sh
./uninstall.sh
```

The uninstaller is idempotent. It removes current Skill Bill installs, generated alias installs, and known legacy install names when they are present, and skips unrelated non-symlink paths.

## Skills Included

### Code Review (33 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-code-review` | Shared review router |
| `/bill-agent-config-code-review` | Review skill/agent-config repositories |
| `/bill-kotlin-code-review` | Kotlin baseline review orchestrator |
| `/bill-backend-kotlin-code-review` | Backend Kotlin review override |
| `/bill-kmp-code-review` | Android/KMP review override |
| `/bill-kotlin-code-review-architecture` | Kotlin architecture and boundaries review |
| `/bill-kotlin-code-review-platform-correctness` | Kotlin lifecycle, coroutine, threading, and logic review |
| `/bill-kotlin-code-review-performance` | Kotlin performance review |
| `/bill-kotlin-code-review-security` | Kotlin security review |
| `/bill-kotlin-code-review-testing` | Kotlin test quality review |
| `/bill-kmp-code-review-ui` | KMP UI review |
| `/bill-kmp-code-review-ux-accessibility` | KMP UX and accessibility review |
| `/bill-backend-kotlin-code-review-api-contracts` | Backend API contract review |
| `/bill-backend-kotlin-code-review-persistence` | Backend persistence and migration review |
| `/bill-backend-kotlin-code-review-reliability` | Backend reliability and observability review |
| `/bill-php-code-review` | PHP backend review orchestrator |
| `/bill-php-code-review-architecture` | PHP architecture and boundary review |
| `/bill-php-code-review-platform-correctness` | PHP correctness, ordering, retry, and stale-state review |
| `/bill-php-code-review-api-contracts` | PHP API contract and serialization review |
| `/bill-php-code-review-persistence` | PHP persistence, transaction, and migration review |
| `/bill-php-code-review-reliability` | PHP reliability, retry, and observability review |
| `/bill-php-code-review-security` | PHP security review |
| `/bill-php-code-review-performance` | PHP performance review |
| `/bill-php-code-review-testing` | PHP test quality review |
| `/bill-go-code-review` | Go backend/service review orchestrator |
| `/bill-go-code-review-architecture` | Go architecture and package-boundary review |
| `/bill-go-code-review-platform-correctness` | Go correctness, goroutine safety, and context review |
| `/bill-go-code-review-api-contracts` | Go API contract and serialization review |
| `/bill-go-code-review-persistence` | Go persistence, transaction, and migration review |
| `/bill-go-code-review-reliability` | Go reliability, timeout, and observability review |
| `/bill-go-code-review-security` | Go security review |
| `/bill-go-code-review-performance` | Go performance review |
| `/bill-go-code-review-testing` | Go test quality review |

### Feature Lifecycle (4 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-feature-implement` | Spec-to-verified implementation workflow |
| `/bill-feature-verify` | Verify a PR against a task spec |
| `/bill-feature-guard` | Add feature-flag rollout safety |
| `/bill-feature-guard-cleanup` | Remove feature flags after rollout |

### Utilities (9 skills)

| Skill | Purpose |
|-------|---------|
| `/bill-quality-check` | Shared quality-check router |
| `/bill-agent-config-quality-check` | Agent-config repository quality-check implementation |
| `/bill-kotlin-quality-check` | Gradle/Kotlin quality-check implementation |
| `/bill-php-quality-check` | PHP quality-check implementation |
| `/bill-go-quality-check` | Go quality-check implementation |
| `/bill-boundary-history` | Maintain `agent/history.md` at module/package/area boundaries |
| `/bill-unit-test-value-check` | Audit unit tests for real value |
| `/bill-pr-description` | Generate PR title, description, and QA steps, preferring repo PR templates when present |
| `/bill-new-skill-all-agents` | Create a new skill and sync it to all agents |

## Project customization

Use `AGENTS.md` for repo-wide guidance.

Use `.agents/skill-overrides.md` for per-skill customization without editing this plugin. The file is intentionally strict:

- first line must be `# Skill Overrides`
- each section must be `## <existing-skill-name>`
- each section body must be a bullet list
- freeform text outside sections is invalid

Precedence:

1. matching `.agents/skill-overrides.md` section
2. `AGENTS.md`
3. built-in skill defaults

Example:

```md
# Skill Overrides

## bill-kotlin-quality-check
- Treat warnings as blocking work.

## bill-pr-description
- Keep QA steps concise.
```

## Architecture

### Core model

The repo is organized around a strict three-layer model:

- `skills/base/` — canonical, user-facing capabilities such as `bill-code-review`, `bill-quality-check`, and `bill-feature-implement`
- `skills/<platform>/` — platform-specific overrides and approved subskills
- `orchestration/` — maintainer-facing reference snapshots for shared routing, review, and delegation contracts

Think of it as markdown with inheritance:

- base skills define the stable contracts
- platform skills specialize them
- orchestration snapshots document the shared routing, review, and delegation logic that runtime-facing skills can reference via sibling supporting files in the same skill directory

### Fast mental model

If you only remember four things, remember these:

1. Users enter through stable skills in `skills/base/`.
2. Platform depth lives in `skills/<platform>/`.
3. Shared logic is documented in `orchestration/`, but runtimes consume it through sibling sidecars such as `stack-routing.md`, `review-orchestrator.md`, and `review-delegation.md`.
4. Topology changes should start in `scripts/skill_repo_contracts.py`, then flow into skills, tests, and docs.

That last file is the canonical map for:

- which shared playbook snapshots exist
- which runtime-facing skills require which sidecars
- which review skills are governed by the shared review/delegation contract

Current platform packages:

- `agent-config`
- `kotlin`
- `backend-kotlin`
- `kmp`
- `php`
- `go`

### Naming and enforcement

Naming is intentionally strict:

- base skills may use any neutral `bill-<capability>` name
- platform overrides must use `bill-<platform>-<base-capability>`
- deeper specialization is only allowed for code review:
  - `bill-<platform>-code-review-<area>`

Approved `code-review` areas:

- `architecture`
- `performance`
- `platform-correctness`
- `security`
- `testing`
- `api-contracts`
- `persistence`
- `reliability`
- `ui`
- `ux-accessibility`

That means new stacks can extend the system, but they cannot invent random new naming shapes without intentionally updating the validator and docs.

## Validation

This repo validates both content quality and taxonomy rules.

Local checks:

```bash
python3 -m unittest discover -s tests
npx --yes agnix --strict .
python3 scripts/validate_agent_configs.py
```

CI runs the same checks.

## Versioning and releases

Skill Bill uses tag-driven GitHub Releases.

- stable releases use SemVer tags such as `v0.4.0`
- prereleases use SemVer prerelease tags such as `v0.5.0-rc.1`
- pushing a release tag reruns validation and publishes a GitHub Release with generated notes

See `RELEASING.md` for the maintainer checklist and versioning policy.

The validator enforces:

- package location rules
- naming rules
- README catalog drift
- cross-skill references
- required routing playbook references
- plugin metadata

## Adding skills

Preferred path:

- run `/bill-new-skill-all-agents`

Manual path:

1. create `skills/<package>/<skill-name>/SKILL.md`
2. follow the naming rules above
3. run `./install.sh`
4. update docs and validation if you intentionally add a new package or naming shape

## License

MIT — free to use, copy, modify, merge, publish, distribute, sublicense, and sell, provided the license notice is retained.
