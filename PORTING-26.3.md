# Forgified Fabric API: 26.3 port notes

Unofficial community port of Sinytra's Forgified Fabric API to Minecraft 26.3 / NeoForge 26.3.0.89,
made for the Zoompack 5 modpack. All credit for FFAPI goes to Sinytra and its contributors; Fabric API
is by FabricMC. Licences are unchanged (Apache 2.0, see `LICENSE`). This branch is not published and
not affiliated with Sinytra.

| | 26.2.x (upstream) | 26.3.x (this branch) |
|---|---|---|
| Minecraft | 26.2 | 26.3 |
| NeoForge | 26.2.0.88 (FML 11.0.16) | 26.3.0.89 (FML 12.0.12, Mixin 0.17.4) |
| Fabric API base | 0.161.0+26.2 | 0.163.0+26.3 (`fabric/26.3` @ 33b369075, 2026-10-14) |
| Forgified Fabric Loader | 2.5.83+0.19.3 | 2.5.90+0.19.5 (Fabric 26.3 mods need loader >= 0.19.5) |
| implementationVersion | 4.0.1 | 5.0.0 |

Local builds are versioned `0.163.0+26.3+5.0.0+local` (the `+local` suffix is added whenever
`GITHUB_RUN_NUMBER` is unset).

## How the port was done

Upstream went 26.1 -> 26.2 by bumping `ffapi.gradle.properties` ("Update to 26.2") and then fixing
compile and mixin errors. FFAPI keeps upstream Fabric API commits as cherry-picks on its branch, so its
26.2.x branch contained everything up to `fabric/26.2` 6a8ed6bc5.

For 26.3 the 134 upstream commits `fabric/26.2..fabric/26.3` were applied as **one 3-way merge**: a
synthetic commit with tree `fabric/26.3` and parent `fabric/26.2` was cherry-picked onto 26.2.x
(`git cherry-pick --no-commit`). Conflicts were resolved per module, keeping FFAPI's NeoForge bridging.
See commit `c82935096` and the commits after it.

Not taken from upstream:
- the `build-logic` rewrite (fabric-api#5416), `gradle/libs.versions.toml`, the new per-module
  `build.gradle` DSL, checkstyle and CI changes. FFAPI keeps `buildSrc`. Module dependency changes
  from upstream were copied into FFAPI's `moduleDependencies`/`testDependencies` lists by hand
  (content-registries -> item-api, object-builder -> TAW, datagen test -> loot, registry-sync test ->
  client-gametest, resource-loader-v1 test -> resource-conditions).
- `fabric-crash-report-info-v1` stays removed.

## What changed for 26.3

NeoForge / FML 12:
- `DataPackRegistryEvent.NewRegistry` is gone; registry sync now uses `NewDatapackRegistryEvent`
  (`worldRegistry`/`reloadableRegistry`). `DynamicRegistriesImpl` forces `DataPackRegistriesHooks`
  to initialize before `RegistryDataLoader` (otherwise NeoForge's static init NPEs).
- NeoForge redirects `Biome.attributes` to `getModifiedEnvironmentAttributes` and requires the field to be
  private. The biome classtweaker no longer widens it. On 26.2 this was fine; on 26.3 it crashed every launch.
- `DataMapHooks.getNextOxidizedStage` / `getPreviousOxidizedStage` / `getBlockWaxed` / `getBlockUnwaxed`
  no longer fall back to the vanilla maps that `OxidizableBlocksRegistry` writes. A new
  `content-registries` `DataMapHooksMixin` restores the fallback.
- `RegistryDataLoader.load` in `ReloadableServerRegistries` is NeoForge's 5-arg overload
  (advancement and loot mixins).
- `ServerboundSwingPacket` was removed (fake player packet listener).
- `BiomeModifier.modify` takes `RegistryAccess`. The biome modification context is rebuilt on NeoForge's
  `ModifiableBiomeInfo.BiomeInfo.Builder`, including the new upstream read methods.
- NeoForge 26.3 removed several sleep/bed patches; entity-events sleep mixins follow upstream again.
- MC 26.3: blaze3d pipeline classes moved to `com.mojang.renderpearl.api.pipeline`;
  `EntityFluidInteraction` was rewritten (current tracking in `CurrentAccumulator`);
  `ItemInHandRenderer` became `FirstPersonHandsAndItemsRenderer`.

Upstream Fabric API 26.3 API changes carried over (public API matches upstream 26.3):
- content-registries removed `CompostableRegistry`, `StrippableBlockRegistry`, `TillableBlockRegistry`,
  `FlattenableBlockRegistry`, `FuelValueEvents`, `DecoratedPotPatternRegistry` and `FabricPotionBrewingBuilder`.
  FFAPI's NeoForge bridges for them were dropped with them.
- new Block Transformer API (item-api) uses upstream's mixins. NeoForge's data-map transformers are
  appended on top by NeoForge, so both work.
- new: advancement API v1, enchantment power, fluid flow, HUD info bar / status-bar heights,
  `FabricTagBuilder` (extends NeoForge's `ITagBuilderExtension` like `FabricTagAppender`),
  `FabricBrewingProvider`, reloadable dynamic registries in datagen, registry "missing content"
  world validation (client screen), tooltip-provider getters (bridged to NeoForge's
  `createComponentAppender`).

Build and dev runs:
- `fabric-biome-api-v1` and `fabric-data-generation-api-v1` testmods used `srcDirs += [...]`, which dropped
  the `generateTestmodModMetadata` task dependency. Their testmods had no `neoforge.mods.toml` and the
  root `runGametestServer` refused to start. This was already broken on 26.2.x; they now use `srcDir`.
- The deprecated `fabric-resource-loader-v0` testmod shared the package `net.fabricmc.fabric.test.resource.loader`
  with the v1 testmod (split package, rejected by the module layer). It moved to `...loader.v0`.
- The root project no longer applies the modules' interface injection data to its own non-recompiled
  Minecraft jar. NeoForge's binary injector (installertools 4.0.12) writes generic arguments into the
  interface class name (`FabricEntityType$Builder<T>`), so root dev runs died with `NoClassDefFoundError`.
  This also happens on 26.2. Recompiling the root jar instead fails, because JST can't import two
  injected interfaces with the same simple name. The data is still merged and published (`mergeInterfaces`).

Datagen on NeoForge 26.3:
- `EntityLootSubProviderMixin` / `BlockLootSubProviderMixin` (new upstream) target NeoForge's
  overridable `getKnownEntityTypes()` / `getKnownBlocks()` instead of `run()`.
- `FabricTagAppender.removeTag`/`removeAll`/varargs `remove` delegate to NeoForge's
  `ITagAppenderExtension` (NeoForge's `TagAppender` already implements `remove(ResourceKey)`/`remove(TagKey)`).
- `fabric-data-generation-api-v1` uses recompilation (`ENABLE_RECOMP_FOR`), because binary interface
  injection breaks the generic `FabricTagAppender<T>` at runtime.

Bug fixed along the way: the mob-effect removal bridge called `ALLOW_EARLY_REMOVE` for effects the
entity doesn't have (null instance), like upstream fabric-api#5121.

## Test status

- `./gradlew build publishToMavenLocal`: passes. This is what upstream CI runs; unit tests are disabled
  in `ffapi.neo-setup` as on 26.2.
- `./gradlew :runGametestServer` (root, all modules + testmods): **110 game tests, 109 pass.**
  - `fabric_resource_loader_v1_testmod:builtin_pack_sorting_test_builtin_pack_sorting` ("enabled_by_c
    recipe should have been loaded") fails, and **fails on 26.2.x too**.
  - `LanguageTestMod.onInitializeServer` asserts that server-side translations of mod lang files load.
    It fails during mod construction and aborts the whole run, also on 26.2.x. To reach the game tests,
    temporarily replace its body with `if (false) testTranslationLoaded();` (not committed).
  - With the same workarounds, 26.2.x runs 112 tests with 2 failures (the one above, plus the effect
    NPE fixed here). 26.3 has 2 fewer tests because upstream removed the deleted registries' tests.
- `:fabric-data-generation-api-v1:runDatagen`: all providers run and write their output
  (54 files). Afterwards FML 12's `Server.main` throws "Couldn't find Minecraft server thread", because
  FFAPI's datagen path returns without starting a server, so the Gradle task exits non-zero. Fixing that
  (e.g. calling `System.exit(0)` after datagen, or switching the run to NeoForge's data run type) is
  still open. A server-only run deletes the client assets in `src/testmod/generated`; run
  `runDatagenClient` too, or restore them with git, before committing regenerated files.
- Client: a NeoForge 26.3 client dev run (Connector + Launchpad + FFAPI + AppleSkin + Krypton + The Sift +
  NeoForge GeckoLib) loads and quick-plays into a world. The first launch crashed on
  `ClientPacketListenerMixin.invokeTagsLoaded`: upstream re-targeted it to `CreativeModeTabs.searchTab()`,
  which NeoForge's `handleUpdateTags` doesn't call, so it now targets `allTabs()`. Client game tests
  (`runGametestClient`) were not run.

## Known gaps / still broken

- Server-side mod translations (see above, pre-existing).
- Builtin pack sorting test (pre-existing).
- Client only smoke-tested (title -> world). Not verified: rendering details (Indigo/FRAPI on
  renderpearl), HUD, keybinds, creative tabs, client game tests.
- `Missing FluidModel for fluid 'the_sift:ichor'` (NeoForge `ClientHooks.gatherFluidModels`): fluids
  registered through Fabric's `FluidRenderHandlerRegistry` may not reach NeoForge 26.3's new
  `RegisterFluidModelsEvent` / `FluidModel` map (FFAPI uses `FluidStateModelSetMixin`). Check Ichor
  in-game; it probably needs a bridge to `RegisterFluidModelsEvent`.
- `runDatagen` exits non-zero after generating (see above).
- Biome spawn emulation: a full override of `NATURAL_MOB_SPAWNS` can't remove inherited spawn costs
  (NeoForge applies spawns as an overlay).
- Fabric's new registry "missing content" world validation is untested at runtime.
- `VillagerInteractionRegistries.registerCompostable` may be overridden by NeoForge's
  `VILLAGER_COMPOSTABLES` data map (pre-existing).

## Keeping it rebased on upstream

```bash
git remote add fabric https://github.com/FabricMC/fabric-api.git   # once
git fetch fabric
# list new upstream commits since the last sync (33b369075 for this port)
git log --oneline 33b369075..fabric/26.3
# FFAPI's usual way: cherry-pick them one by one, resolving against the NeoForge bridging
git cherry-pick -x <commit>...
# or, for a big jump, the 3-way delta used for this port:
C=$(git commit-tree 'fabric/26.3^{tree}' -p 33b369075 -m "Upstream delta")
git cherry-pick --no-commit $C
#   then restore FFAPI's build files: buildSrc, build.gradle.kts, settings.gradle.kts, gradle/,
#   */build.gradle, gradle.properties (take upstream's module versions), .github
```

When moving to a newer NeoForge build, bump `versionNeoForge` in `ffapi.gradle.properties`, then run
`build`, `:runGametestServer` and `:fabric-data-generation-api-v1:runDatagen`. For upstream's own
26.3 port, compare with Sinytra's branch once it exists and prefer theirs.
