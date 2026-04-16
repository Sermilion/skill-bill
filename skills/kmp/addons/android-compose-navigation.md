# Android Compose Navigation

Use this topic file only after `android-compose` has already been selected for routed `kmp` work.

Read this file when the diff touches `NavHost`, route models, deep links, multiple back stacks, conditional navigation, or result handoff between destinations.

This guidance mainly adapts the official Android `navigation/navigation-3` skill into a governed KMP add-on shape.

## Source recipes

- Navigation 3 `multiple-backstacks`
  Read when top-level tabs, bars, or rails each need their own preserved back stack.
- Navigation 3 `deeplinks-basic` and `deeplinks-advanced`
  Read when URLs or intents must resolve into the same routed state used by in-app navigation.
- Navigation 3 `conditional`
  Read when auth, onboarding, or first-run state switches users between navigation flows.
- Navigation 3 `results-event` and `results-state`
  Read when dialogs, sheets, editors, or subflows return data to earlier content.
- Keep only the transferable route/state/result patterns; do not import Android-only migration setup or dependency instructions.

## Implementation guidance

- Keep navigation wiring above leaf composables. Reusable UI should still receive lambdas or abstract navigation actions, not Android navigation objects.
- Prefer type-safe destination models or typed route state over stringly typed route construction when the stack supports it.
- When the UI has multiple top-level destinations, preserve a distinct back stack per top-level route instead of rebuilding state on every tab switch.
- Treat deep links as navigation state construction, not as a special UI branch. Parse the intent into the same destination model the rest of the app uses.
- For auth, onboarding, or conditional flows, switch between navigation flows at a clear route boundary instead of hiding the condition inside a leaf screen.
- Persist the navigation state that users expect to survive configuration change or process death. Do not keep crucial back stack state only in transient composable memory.

## Returning results and one-off outcomes

- Prefer a single explicit result path per flow: either callback/event-based handoff or shared saved/stateful navigation state, not a mix of both.
- Keep one-off results close to the navigation boundary instead of teaching leaf composables how to inspect previous entries or platform state directly.
- Clear consumed results after they are handled so recomposition or state restoration does not replay a stale outcome as a new event.
- When a result can also arrive via deep link or restored state, normalize it into the same UI event/state path rather than inventing a second rendering branch.

## Review focus

- Check that deep links build the same routed state the in-app flow uses instead of bypassing the normal navigation model.
- Flag multiple-top-level-route UIs that lose per-tab back stack state on tab changes.
- Flag auth/onboarding conditions embedded inside leaf UI when they should switch at a higher navigation boundary.
- Check returned results from dialogs, bottom sheets, or editor flows for replay risk after recomposition or state restoration.

## Boundary

- Keep this topic focused on Android navigation state and flow ownership. Generic Compose API structure and preview rules still belong to `compose-guidelines.md`.
