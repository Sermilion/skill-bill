# KMP Android Compose Review Add-On

Use this governed add-on only after stack routing has already selected `kmp` and the review scope clearly contains Android Compose UI.

This file supplements `bill-kmp-code-review` and `bill-kmp-code-review-ui`. It is not a standalone review command. The guidance adapts transferable Android/Compose review concerns from Google's public `android/skills` repository and keeps migration or upgrade workflows out of scope.

## Activation signals

Select `android-compose` when the scoped diff includes:

- `@Composable` functions or screen composables
- Compose previews, `remember*`, `LaunchedEffect`, or other side-effect APIs
- `Scaffold`, `LazyColumn`, `LazyRow`, insets, or IME handling
- Android-specific Compose resources or edge-to-edge behavior

## Review focus

### 1. Insets and edge-to-edge

- Verify inset handling is deliberate and applied once.
- Prefer `Scaffold` `innerPadding` or material inset APIs over parent-container padding.
- For lazy lists, prefer `contentPadding` rather than padding a wrapper that clips scroll behavior.
- Flag parent-level padding on app bars or shared containers when it prevents content or backgrounds from drawing correctly into system bar areas.

### 2. IME behavior

- Check text-input screens for keyboard overlap risk.
- Flag patterns that apply IME insets twice, especially when `contentWindowInsets` and `imePadding()` are both active on the same path.
- When vertical scrolling is required, ensure the modifier order still lets the focused field remain reachable.

### 3. Lifecycle-aware state and side effects

- Verify UI-facing `Flow` collection uses lifecycle-aware collection in wrappers.
- Flag work launched directly from a composable body instead of `LaunchedEffect`, `DisposableEffect`, or another explicit side-effect API.
- Check that route-level state assembly stays above reusable leaf composables.

### 4. Stable inputs and recomposition

- Prefer immutable UI models, stable collections, and event lambdas that do not churn unnecessarily.
- Flag avoidable object recreation or unstable inputs on hot UI paths when the diff makes the risk concrete.

### 5. Theme, resources, and previews

- Flag hardcoded user-facing strings, colors, spacing, or content descriptions when theme tokens or resources should be used.
- Expect previews or another cheap verification path for meaningful UI states introduced in the diff.
- Check loading, empty, content, and error states when the screen contract implies them.

## Review boundary

- Keep this add-on subordinate to the routed `kmp` review.
- Use it to extend the existing Compose review rubric with Android-specific Compose risks.
- Do not turn review comments into migration plans, AGP/Gradle/Kotlin upgrade advice, or product-specific Android rollout instructions.
