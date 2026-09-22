# SKILL-369 CodeGraph observations

## Availability

CodeGraph reported `pending_synchronization` during implement. Navigation fell back to ripgrep and direct file reads.

## Query: external platform pack config and catalog loaders

- **Phase:** implement (task 1–2)
- **Question:** Where are external addon config store and platform pack discovery entry points?
- **CodeGraph result:** Unavailable (pending sync).
- **Supported next decision:** No.
- **Fallback:** `rg ExternalAddon` and `rg discoverPlatformPackManifests` in `runtime-kotlin/`.
- **Evidence:** `FileExternalAddonSourceConfigStore.kt`, new `FileExternalPlatformPackSourceConfigStore.kt`, `PlatformPackCatalogLoader.kt`, `InstallPlanSkillDiscovery.kt`.

## Query: install and review consumers

- **Phase:** implement (task 5)
- **Question:** Which files call `discoverPlatformManifests` for install apply and native-agent catalog staging?
- **CodeGraph result:** Unavailable (pending sync).
- **Supported next decision:** No.
- **Fallback:** ripgrep on `discoverPlatformManifests`, `publishInstalledReviewCatalog`, `InstallApplyPlatformPackView.kt`.
- **Evidence:** `InstallPlanBuilder.kt`, `InstallApply.kt`, `InstallNativeAgentOperationsLinkCatalog.kt`.

## Preplan qualitative notes (not re-measured)

- A broad pack-loader query mixed `buildPack` hits with substance-audit symbols.
- An `ExternalAddonSource` query hit the tool output cap before catalog consumers.

## Query: effective catalog, config store, and quality routing

- **Phase:** audit
- **Question:** Where does an external slug replace a bundled pack, and which quality-check path reads that catalog?
- **CodeGraph result:** Helpful in part. The result included `EffectivePlatformPackCatalog`, `QualityCheckRoute.routeQualityCheck` calling `loadEffectiveManifests`, `PlatformPackCatalogLoader`, and `FileExternalPlatformPackSourceConfigStore`. That was enough to treat replacement as whole-pack and to confirm quality routing already reads the effective catalog.
- **Unhelpful parts:** The same result mixed in unrelated review-lane routing and an MCP dispatcher, and it truncated the loader and config-store bodies.
- **Supported next decision:** Yes, for the replacement and routing seam. No, for the truncated function bodies.
- **Fallback:** Read and ripgrep, because the truncated bodies were not usable as the implementation.
- **Evidence:** `EffectivePlatformPackCatalog.kt`, `QualityCheckRoute.kt`, `PlatformPackCatalogLoader.kt`, `FileExternalPlatformPackSourceConfigStore.kt`.

## Query: scaffold registration, overlay, and publish recovery

- **Phase:** audit
- **Question:** Where do scaffold register and dry-run, addon overlay, telemetry redaction, reconcile, and symlink checks live?
- **CodeGraph result:** Unavailable. The call returned `pending_synchronization` and no symbols.
- **Supported next decision:** No.
- **Fallback:** Direct reads of `ScaffoldServicePlanning.kt`, `ScaffoldWizardPayloads.kt`, `InstallReconcilePolicy.kt`, `InstallContentHash.kt`, and the overlay apply path, because the index was not ready.
- **Evidence:** those files, plus `ExternalPlatformPackTelemetryPolicy.kt`. The hash read showed pointer targets were resolved from the pack root's grandparent, which is the checkout only for bundled `platform-packs/<slug>` trees.

## Query: native-agent catalog publish and authoring discovery

- **Phase:** audit
- **Question:** Does review-catalog publish and show/fill/validate use the same config path and effective pack as install planning?
- **CodeGraph result:** The catalog query named `QualityCheckRoute` and `PlatformPackCatalogLoader`, then truncated the native-agent link body, so it did not show whether publish forwarded `SKILL_BILL_CONFIG_PATH`.
- **Supported next decision:** No, for the truncated link body.
- **Fallback:** Direct read of `InstallNativeAgentOperationsLinkTargetResolve.kt` and `AuthoringDiscovery.kt`.
- **Evidence:** publish loads pack roots with the link request environment; authoring discovery uses the effective catalog when external sources are registered. Elapsed time and token counts were not measured.

## Availability during audit

The first audit query returned symbols and was mixed and truncated. The second returned `pending_synchronization` and no body. Elapsed time, call counts, and token or output size were not measured, so this report does not claim time or token savings.

## Recommendation

Narrow CodeGraph to symbol-scoped queries. Keep ripgrep when a result mixes unrelated symbols or truncates the body you need. Do not claim time or token savings without measured data.
