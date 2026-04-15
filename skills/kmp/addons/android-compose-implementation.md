# KMP Android Compose Add-On

Use this governed add-on only after stack routing has already selected `kmp` and the current work clearly touches Android Compose UI.

This file is implementation guidance, not a standalone skill or review rubric. It adapts transferable Android/Compose patterns from Google's public `android/skills` repository for use inside the governed KMP add-on model.

## Activation signals

Activate `android-compose` when the routed KMP work includes signals such as:

- `@Composable` screens or reusable composables
- Compose UI state models or `collectAsStateWithLifecycle()`
- `Modifier` chains, previews, `remember*`, or Compose side effects
- Android UI-safe-area concerns such as system bars, IME, or list inset handling

## Exclusions

Do not use this add-on for:

- XML/View-to-Compose migration workflows
- Android Studio, AGP, Gradle, Kotlin, or dependency upgrade playbooks
- Play Billing or other product-specific Android upgrade tasks
- Non-Compose KMP work

## Implementation guidance

### 1. Build stateless UI first

- Start with stateless composables that take plain UI state and event lambdas.
- Add route-level or screen-level stateful wrappers only where ownership is required.
- Hoist state to the lowest common ancestor that coordinates the interaction.

### 2. Keep reusable composables free of Android wiring

- Pass navigation and user actions as lambdas or an interface.
- Keep `NavController`, repositories, and platform setup out of reusable composables.
- Assemble screen state above the leaf UI layer so previews and tests stay cheap.

### 3. Use lifecycle-aware state and explicit side effects

- Prefer `collectAsStateWithLifecycle()` for `Flow` collection in UI-facing wrappers.
- Use `LaunchedEffect`, `DisposableEffect`, `rememberCoroutineScope`, and `rememberUpdatedState` for their intended side-effect shapes.
- Do not launch work directly from composable bodies.

### 4. Keep inputs stable and recomposition-friendly

- Pass primitives or immutable UI models into composables.
- Prefer immutable collections and stable state models where the stack supports them.
- Remember expensive derived values instead of recreating them on every recomposition.

### 5. Plan previews and state coverage as part of the implementation

- Include previews for the important UI states you expect to ship.
- Make content, loading, empty, and error states easy to render in previews.
- Keep public or reusable composables preview-friendly by avoiding hidden runtime dependencies.

### 6. Use theme tokens and resources consistently

- Pull colors, typography, spacing, and shapes from shared theme tokens.
- Use string resources for user-facing text and content descriptions.
- Apply `modifier: Modifier = Modifier` to reusable composables and consume it at the root element.

### 7. Handle Android insets deliberately

- Prefer `Scaffold` and pass `innerPadding` into the content instead of padding an outer parent container.
- For scrollable content such as `LazyColumn` or `LazyRow`, feed inset padding into `contentPadding` instead of wrapping the list in parent padding.
- When you are outside a `Scaffold`, use `safeDrawingPadding()` or `windowInsetsPadding(WindowInsets.safeDrawing)` rather than inventing ad hoc spacing.
- Apply inset values once. Avoid stacking parent padding and child inset modifiers on the same axis unless you have a concrete reason.

### 8. Keep IME handling explicit on Android

- For screens with text input, confirm the UI can stay visible when the keyboard opens.
- Prefer patterns that consume existing inset values before adding IME-specific padding.
- If `imePadding()` is needed, apply it to the scrolling content container in a way that avoids double-padding when parent `contentWindowInsets` already include the IME.

## Implementation boundary

This add-on should enrich KMP implementation work only after `kmp` routing. It must not be treated as a new top-level package, slash command, or default workflow outside the owning stack.
