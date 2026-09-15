# Skill Bill Teams Roadmap

Status: Product direction

Nothing below is shipped. Skill Bill Teams is a direction, not a product
surface: there is no team bundle, no admin editing, no hosted control plane,
and no team telemetry in the runtime today. Read the positioning and principles
as constraints on a future build, and the checklist as what has to be answered
before that build starts.

## Positioning

Skill Bill Teams is the control plane for standardizing, tuning, distributing,
and measuring AI-agent workflows across an engineering team.

The product promise is:

> Admins define how AI agents should work; developers get the same governed
> setup; telemetry shows whether the setup is helping; admins iterate without
> each developer hand-editing local skills.

This is not a separate coding agent. Skill Bill Teams sits above the agents a
team already uses and makes their behavior repeatable: governed skills,
platform packs, validation, versioned distribution, permissions, and usage
feedback.

The wedge is enablement as much as governance. In most teams the developers
getting the least from the AI seats the company already pays for are not the
enthusiasts — they are the majority for whom raw agents produced inconsistent
results and who fell back to using AI as a question-answering tool. A governed,
process-backed setup is what converts those developers into shipping real
features with agents; Skill Bill's earliest adopters followed exactly that
pattern, alongside heavy agent users who kept the setup for durable resume,
per-repo memory, and tuned packs. Skill Bill Teams sells that conversion first;
governance, distribution, and telemetry are how a team sustains and measures
it.

## Product Principles

- Start local-first. Prove that an admin-published setup can move cleanly to
  multiple developers before building a hosted service.
- Keep the runtime contract authoritative. Team features publish and sync
  governed Skill Bill source; they must not bypass `content.md`, platform
  manifests, generated-output boundaries, validation, or install staging.
- Make every rollout reversible. Team changes need versioning, validation,
  preview, publish, and rollback.
- Treat telemetry as evidence, not surveillance. Anonymous aggregate telemetry
  is the default; code, prompt text, file contents, and finding descriptions
  stay out unless an organization explicitly opts in.
- Keep admin power scoped. Admins can tune skills and packs for their team, but
  normal members should get a stable use-only setup.

## Discovery Checklist

Before building a hosted control plane, collect proof from real users:

- Which commands become habitual?
- Which team-specific changes do admins want first?
- How often do users need rollback?
- Which telemetry would change an admin decision?
- Does a bundle sync reduce onboarding friction?
- Do teams tune bundled packs, or mostly add overrides?
- What privacy setting is acceptable by default?

The hosted product should follow the answers to those questions, not precede
them.
