# Enemy Spell Cast Performance, Stability, and Spell Variety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the current combat feel while eliminating unchanged per-tick work, fixing runtime-state leaks, adding configurable spell admission, and expanding the default safe pool to 45–55 varied spells.

**Architecture:** Cache one immutable config snapshot and one immutable, rank-indexed spell catalog, then use generation-based fingerprints to make Trait ticks a constant-time no-op when nothing changed. Keep risky spell categories as datapack definitions behind a double opt-in policy, and isolate Iron's spell-specific target preparation behind small `SpellBehavior` implementations.

**Tech Stack:** Java 21, NeoForge 21.1, Minecraft 1.21.1, L2 Hostility 3.0.18-compatible API, Iron's Spells 'n Spellbooks 3.16-compatible API, Mojang codecs, JUnit 5, NeoForge GameTest.

**Spec:** `docs/superpowers/specs/2026-09-22-performance-stability-spell-variety-design.md`

## Global Constraints

- Default gameplay uses the bundled safe spell pool and keeps `general.enabled=true`.
- Existing slot counts remain 1, 1, 2, 2, and 3 for Trait ranks 1–5.
- Existing L2 level scaling, 10-tick default decision interval, cast interruption, mana, cooldown, and saved-loadout semantics remain unchanged.
- Spell power remains uncapped; cast speed remains capped at 3×; cooldown remains floored at 25%.
- `forceDenySpellIds` always wins over every allow mechanism.
- Summoning, terrain-changing, and portal spells require both their category exclusion to be `false` and an explicit ID in the mode-appropriate allow list.
- Risky-category execution is experimental and unsupported; the addon must still reject malformed IDs/definitions and avoid startup failure.
- Existing valid saved spell IDs remain in their slots; only newly forbidden or missing IDs are replaced.
- Do not initialize Git in this directory without user direction. Where a task names a commit, skip only the commit command while keeping the task boundary and verification result in `.superpowers/sdd/2026-09-21-enemy-spell-cast/progress.md`.

## Review Focus

- Config reload before or after datapack reload must produce one coherent snapshot/catalog pair; Task 2 tests both orders.
- Empty allowlists, malformed IDs, duplicated IDs, and deny/allow conflicts must yield a safe empty or filtered pool without repeated log spam; Tasks 1 and 2 pin these cases.
- Trait rank removal or master-disable during a cast must cancel exactly once and remove the spell-power modifier; Tasks 3 and 4 cover both transitions.
- Delayed projectiles and AoE debuffs must not damage or debuff hostile allies; Tasks 7 and 8 audit source behavior and run integration tests.
- Player-only or custom-data Iron spells must fail closed without consuming mana or crashing a server; Tasks 6 and 8 exercise incompatible preconditions.

---

### Task 1: Cache Config State and Add Operator Spell Controls

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/config/SpellSelectionMode.java`
- Modify: `src/main/java/com/reist/enemyspellcast/config/EnemySpellCastConfig.java`
- Modify: `src/main/java/com/reist/enemyspellcast/EnemySpellCast.java`
- Create: `src/test/java/com/reist/enemyspellcast/config/EnemySpellCastConfigTest.java`
- Modify: `src/test/java/com/reist/enemyspellcast/progression/ProgressionRulesTest.java`

**Interfaces:**
- Consumes: NeoForge `ModConfigEvent.Loading` and `ModConfigEvent.Reloading` for this mod's common config.
- Produces: `EnemySpellCastConfig.snapshot(): Settings`, `EnemySpellCastConfig.generation(): long`, and `EnemySpellCastConfig.reloadFromSpec(): Settings`.
- Produces: immutable-copy helpers `Settings.withEnabled(boolean)`, `Settings.withSpellOverrides(SpellSelectionMode,List<String>,List<String>,List<String>)`, and `Settings.withCategoryExclusions(boolean,boolean,boolean)` for event tests and policy composition.
- Produces: `SpellSelectionMode.DEFAULT_POOL` and `SpellSelectionMode.ALLOWLIST_ONLY`.

- [ ] **Step 1: Write failing config-cache and sanitization tests**

Add tests that use a package-private `publishForTest(Settings)` seam and restore defaults in `@AfterEach`:

```java
@Test
void snapshotIsReusedUntilAConfigPublication() {
    Settings before = EnemySpellCastConfig.snapshot();
    long generation = EnemySpellCastConfig.generation();

    assertSame(before, EnemySpellCastConfig.snapshot());
    assertEquals(generation, EnemySpellCastConfig.generation());

    EnemySpellCastConfig.publishForTest(Settings.defaults());
    assertNotSame(before, EnemySpellCastConfig.snapshot());
    assertEquals(generation + 1, EnemySpellCastConfig.generation());
}

@Test
void spellListsAreTrimmedDeduplicatedAndImmutable() {
    Settings raw = Settings.defaults().withSpellOverrides(
            SpellSelectionMode.ALLOWLIST_ONLY,
            List.of(" irons_spellbooks:root ", "irons_spellbooks:root", " "),
            List.of("irons_spellbooks:teleport"),
            List.of("irons_spellbooks:portal"));
    Settings clean = raw.sanitized();

    assertEquals(List.of("irons_spellbooks:root"), clean.allowedSpellIds());
    assertThrows(UnsupportedOperationException.class,
            () -> clean.allowedSpellIds().add("test:mutate"));
}
```

Add three assertions for the new defaults: `DEFAULT_POOL`, all three category exclusions `true`, and `allowedSpellIds` empty.

- [ ] **Step 2: Run the focused tests and observe the expected compile failure**

Run:

```powershell
.\gradlew.bat test --tests "*.EnemySpellCastConfigTest" --tests "*.ProgressionRulesTest"
```

Expected: compilation fails because the new enum, fields, accessors, and test publication seam do not exist.

- [ ] **Step 3: Define the config enum and append exact settings fields**

Create:

```java
public enum SpellSelectionMode {
    DEFAULT_POOL,
    ALLOWLIST_ONLY
}
```

Append these fields to `Settings` and `Settings.defaults()`:

```java
SpellSelectionMode selectionMode,
List<String> allowedSpellIds,
boolean excludeSummoningSpells,
boolean excludeTerrainChangingSpells,
boolean excludePortalSpells
```

Implement the three copy helpers named in **Interfaces** by returning a new `Settings` that changes only the named fields and preserves every other record component. `withCategoryExclusions` parameter order is summoning, terrain-changing, portal.

Define the corresponding common config entries:

```java
SELECTION_MODE = builder.defineEnum("selectionMode", SpellSelectionMode.DEFAULT_POOL);
ALLOWED_SPELL_IDS = builder.defineListAllowEmpty("allowedSpellIds", ArrayList::new,
        EnemySpellCastConfig::isString);
EXCLUDE_SUMMONING_SPELLS = builder.define("excludeSummoningSpells", true);
EXCLUDE_TERRAIN_CHANGING_SPELLS = builder.define("excludeTerrainChangingSpells", true);
EXCLUDE_PORTAL_SPELLS = builder.define("excludePortalSpells", true);
```

Keep the three exclusion keys under `behavior` and selection/list keys under `spellOverrides`. Change `copyStrings` to `trim`, discard blank strings, preserve first-occurrence order, deduplicate, and return `List.copyOf(...)`.

- [ ] **Step 4: Replace per-call construction with one published immutable snapshot**

Use one volatile state and synchronized publication:

```java
private static volatile ConfigState state = new ConfigState(Settings.defaults(), 0L);

public static Settings snapshot() {
    return state.settings();
}

public static long generation() {
    return state.generation();
}

public static synchronized Settings reloadFromSpec() {
    return publish(readFromSpec());
}

static synchronized void publishForTest(Settings settings) {
    publish(settings);
}

private static Settings publish(Settings settings) {
    Settings clean = settings.sanitized();
    state = new ConfigState(clean, state.generation() + 1L);
    return clean;
}

private record ConfigState(Settings settings, long generation) {}
```

Move the existing `new Settings(...)` expression into `readFromSpec()`. `snapshot()` must not allocate, sanitize, or copy lists.

- [ ] **Step 5: Refresh only for this mod's common config events**

Register listeners on `modBus` in `EnemySpellCast` for both config event types. Both call one method that verifies:

```java
event.getConfig().getType() == ModConfig.Type.COMMON
        && event.getConfig().getSpec() == EnemySpellCastConfig.SPEC
```

Then call `EnemySpellCastConfig.reloadFromSpec()`. Task 2 will add catalog rebuilding to the same handler after the new settings are published.

- [ ] **Step 6: Run focused and full unit tests**

Run:

```powershell
.\gradlew.bat test --tests "*.EnemySpellCastConfigTest" --tests "*.ProgressionRulesTest"
.\gradlew.bat test
```

Expected: both commands pass; repeated `snapshot()` calls return the identical `Settings` instance.

- [ ] **Step 7: Record the task checkpoint**

Append the tests run and changed files to the progress ledger. If a Git repository has been provided by execution time, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/config src/main/java/com/reist/enemyspellcast/EnemySpellCast.java src/test
git commit -m "feat: cache config and add spell admission controls"
```

### Task 2: Classify Spells and Precompute the Reloadable Catalog

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellSafetyCategory.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellSelectionPolicy.java`
- Modify: `src/main/java/com/reist/enemyspellcast/catalog/SpellDefinition.java`
- Modify: `src/main/java/com/reist/enemyspellcast/catalog/SpellCatalog.java`
- Modify: `src/main/java/com/reist/enemyspellcast/catalog/SpellCatalogReloadListener.java`
- Modify: `src/main/java/com/reist/enemyspellcast/EnemySpellCast.java`
- Modify: `src/test/java/com/reist/enemyspellcast/catalog/SpellDefinitionTest.java`
- Create: `src/test/java/com/reist/enemyspellcast/catalog/SpellSelectionPolicyTest.java`
- Create: `src/test/java/com/reist/enemyspellcast/catalog/SpellCatalogCacheTest.java`

**Interfaces:**
- Consumes: `Settings`, spell-pool definitions, and `SpellCatalog.SpellResolver`.
- Produces: `SpellSafetyCategory.STANDARD`, `SUMMONING`, `TERRAIN_CHANGE`, and `PORTAL` with codec names `standard`, `summoning`, `terrain_change`, and `portal`.
- Produces: `SpellCatalog.generation()`, `SpellCatalog.find(int, ResourceLocation)`, cached `eligible(int)`, and `SpellCatalog.rebuildFromConfig(Settings)`.

- [ ] **Step 1: Add failing policy tests for default, allowlist, deny precedence, and double opt-in**

Construct definitions for one spell per category and assert this matrix:

```java
assertEquals(Set.of(SAFE), ids(build(defaults, allDefinitions)));
assertEquals(Set.of(ROOT), ids(build(allowlist(ROOT), allDefinitions)));
assertTrue(ids(build(forceAllow(PORTAL, defaults), allDefinitions)).isEmpty());
assertEquals(Set.of(PORTAL), ids(build(forceAllow(PORTAL,
        defaults.withCategoryExclusions(true, true, false)), allDefinitions)));
assertTrue(ids(build(forceAllowAndDeny(ROOT), allDefinitions)).isEmpty());
```

Also test malformed ID warnings are emitted once per catalog build, duplicates retain the first datapack definition, and an empty `ALLOWLIST_ONLY` produces an empty catalog without throwing.

- [ ] **Step 2: Run catalog tests and verify failure**

Run:

```powershell
.\gradlew.bat test --tests "*.SpellDefinitionTest" --tests "*.SpellSelectionPolicyTest" --tests "*.SpellCatalogCacheTest"
```

Expected: compilation fails on missing category, policy, cached lookup, and generation APIs.

- [ ] **Step 3: Extend the spell-pool schema with an optional safety category**

Append `SpellSafetyCategory safetyCategory` to `SpellDefinition` and add:

```java
SpellSafetyCategory.CODEC.optionalFieldOf("safety_category", SpellSafetyCategory.STANDARD)
        .forGetter(SpellDefinition::safetyCategory)
```

Update all Java constructor calls to pass `STANDARD`. Existing datapacks without the field must decode identically.

- [ ] **Step 4: Implement the exact selection policy**

`SpellSelectionPolicy.compile(Settings, Consumer<String>)` parses the four configured ID lists once into unmodifiable `LinkedHashSet`s. Its inclusion logic is:

```java
if (denied.contains(id)) return false;
boolean explicit = settings.selectionMode() == ALLOWLIST_ONLY
        ? allowed.contains(id)
        : forced.contains(id);
if (definition.safetyCategory() != STANDARD) {
    return explicit && categoryEnabled(definition.safetyCategory(), settings);
}
return settings.selectionMode() == DEFAULT_POOL || allowed.contains(id);
```

`bypassesProgression(id)` returns `true` only for `forceAllowSpellIds` in `DEFAULT_POOL`; allowlist-only continues to respect rarity and `min_trait_rank`.

- [ ] **Step 5: Build immutable rank indexes and direct ID lookups**

During `SpellCatalog.build`, validate/resolve each accepted definition once, then precompute ranks 1–5:

```java
Map<Integer, List<ResolvedSpell>> eligibleByRank;
Map<Integer, Map<ResourceLocation, ResolvedSpell>> resolvedByRank;
```

`eligible(rank)` clamps 1–5 and returns the stored list object. `find(rank,id)` reads the stored map. `definitions()` and `ids()` are stored immutable views rather than rebuilt streams.

Retain the original deduplicated `sourceDefinitions` in the catalog. `publish` assigns a monotonically increasing generation. `rebuildFromConfig(settings)` rebuilds from `current.sourceDefinitions`; if resolution throws outside a single-entry validation block, log once and retain the current catalog.

- [ ] **Step 6: Make datapack and config reload order coherent**

`SpellCatalogReloadListener.apply` publishes a catalog from new definitions plus the current cached settings. After Task 1 refreshes settings in `EnemySpellCast`, call:

```java
SpellCatalog.rebuildFromConfig(settings);
SpellGoalInstaller.onConfigReload(settings);
```

The installer hook is introduced in Task 4; until then add a compilation-safe no-op method with the final signature and replace it in Task 4.

Add two tests:

```java
@Test void configThenDefinitionsUsesNewestSettings() { /* allowlist is applied */ }
@Test void definitionsThenConfigRebuildsTheSameSourceDefinitions() { /* generation increments */ }
```

Each test asserts `eligible(3)` and `find(3,id)` agree and repeated `eligible(3)` calls are `assertSame`.

- [ ] **Step 7: Run the catalog suite and full tests**

Run:

```powershell
.\gradlew.bat test --tests "*.SpellDefinitionTest" --tests "*.SpellSelectionPolicyTest" --tests "*.SpellCatalogCacheTest"
.\gradlew.bat test
```

Expected: all tests pass; invalid individual definitions do not discard valid definitions.

- [ ] **Step 8: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/catalog src/main/java/com/reist/enemyspellcast/EnemySpellCast.java src/test/java/com/reist/enemyspellcast/catalog
git commit -m "perf: precompute configurable spell catalog"
```

### Task 3: Add an Idempotent Installer Fingerprint and Attribute Diffing

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/ai/InstallFingerprint.java`
- Modify: `src/main/java/com/reist/enemyspellcast/ai/SpellGoalInstaller.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/SpellCastController.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/SpellPowerService.java`
- Create: `src/test/java/com/reist/enemyspellcast/ai/InstallFingerprintTest.java`
- Modify: `src/test/java/com/reist/enemyspellcast/casting/SpellCastControllerTest.java`

**Interfaces:**
- Consumes: Trait rank, L2 mob level, config generation, and catalog generation.
- Produces: `SpellGoalInstaller.sync(Mob,int): void`, `SpellPowerService.remove(Mob): void`, and `SpellCastController.close(): void`.

- [ ] **Step 1: Write a deterministic 50-Mob fingerprint test**

Use the real fingerprint record without Minecraft objects:

```java
@Test
void unchangedFiftyMobStateRefreshesOnlyOncePerMob() {
    Map<Integer, InstallFingerprint> installed = new HashMap<>();
    int refreshes = 0;
    for (int tick = 0; tick < 200; tick++) {
        for (int mob = 0; mob < 50; mob++) {
            InstallFingerprint next = new InstallFingerprint(3, 240, 7, 11);
            if (!next.equals(installed.put(mob, next))) refreshes++;
        }
    }
    assertEquals(50, refreshes);
}

@Test
void everyFingerprintInputInvalidatesTheFastPath() {
    InstallFingerprint base = new InstallFingerprint(3, 240, 7, 11);
    assertNotEquals(base, new InstallFingerprint(4, 240, 7, 11));
    assertNotEquals(base, new InstallFingerprint(3, 241, 7, 11));
    assertNotEquals(base, new InstallFingerprint(3, 240, 8, 11));
    assertNotEquals(base, new InstallFingerprint(3, 240, 7, 12));
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
.\gradlew.bat test --tests "*.InstallFingerprintTest" --tests "*.SpellCastControllerTest"
```

Expected: compilation fails because the fingerprint and close/update observability do not exist.

- [ ] **Step 3: Make installer synchronization return immediately for unchanged state**

Replace `install` with `sync`. Before computing scaling or reconciling a loadout, create:

```java
InstallFingerprint next = new InstallFingerprint(
        rank,
        L2HostilityBridge.mobLevel(mob),
        EnemySpellCastConfig.generation(),
        SpellCatalog.snapshot().generation());
```

Store `Installed(SpellCasterGoal goal, InstallFingerprint fingerprint)`. If the existing fingerprint equals `next`, return immediately. If only level/config changed, update controller scaling/settings without reconciling the loadout. If rank or catalog generation changed, reconcile once and save once.

- [ ] **Step 4: Diff the spell-power modifier instead of replacing it**

`SpellPowerService.apply` obtains the current modifier with `attribute.getModifier(MODIFIER_ID)`. Desired amount is `max(0, scaling.spellPower()-1)`. Use `1.0e-9` tolerance:

```java
if (desired == 0.0) remove only when present;
else if (current != null && abs(current.amount() - desired) <= EPSILON) return;
else remove old and add one transient ADD_MULTIPLIED_BASE modifier;
```

Add `remove(Mob)` and call it from controller `close()` after `cancelIfActive()`.

- [ ] **Step 5: Make controller updates idempotent**

`SpellCastController.update(Scaling,Settings)` returns `false` without mutation when both values equal current values. Otherwise sanitize data for the new maximum mana, assign the new values, apply spell power once, and return `true`. Extend the controller test with `assertFalse(controller.update(sameScaling,sameSettings))` followed by `assertTrue(controller.update(changedScaling,sameSettings))`; do not ship global performance counters.

- [ ] **Step 6: Run focused and full tests**

Run:

```powershell
.\gradlew.bat test --tests "*.InstallFingerprintTest" --tests "*.SpellCastControllerTest"
.\gradlew.bat test
```

Expected: 50-Mob simulation reports 50 refreshes rather than 10,000, and controller lifecycle tests still pass.

- [ ] **Step 7: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/ai src/main/java/com/reist/enemyspellcast/casting src/test
git commit -m "perf: skip unchanged trait installation work"
```

### Task 4: Guarantee Cleanup on Trait Removal, Config Disable, and Entity Leave

**Files:**
- Modify: `src/main/java/com/reist/enemyspellcast/trait/SpellCasterTrait.java`
- Modify: `src/main/java/com/reist/enemyspellcast/ai/SpellGoalInstaller.java`
- Modify: `src/main/java/com/reist/enemyspellcast/ai/SpellCasterGoal.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/CastRuntimeRegistry.java`
- Modify: `src/main/java/com/reist/enemyspellcast/event/CommonEvents.java`
- Modify: `src/main/java/com/reist/enemyspellcast/gametest/EnemySpellCastGameTests.java`

**Interfaces:**
- Consumes: L2's `initialize(entity,0)` callback, config reload hook, and `EntityLeaveLevelEvent`.
- Produces: one cleanup path, `SpellGoalInstaller.remove(Mob)`, that removes Goal, cancels exactly once, closes Controller, removes spell power, and clears both runtime maps.

- [ ] **Step 1: Strengthen the existing Trait-removal GameTest so it fails on leaked state**

After installing a level-240 rank-3 zombie, capture the spell-power attribute and assert all of these after `cap.setTrait(trait,0); cap.tick(zombie);`:

```java
helper.assertValueEqual(CastRuntimeRegistry.get(zombie).isEmpty(), true, "controller removed");
helper.assertValueEqual(SpellGoalInstaller.isInstalled(zombie), false, "goal state removed");
helper.assertValueEqual(SpellPowerService.hasModifier(zombie), false,
        "spell power removed");
```

Expose the modifier ID package-safely through `SpellPowerService.hasModifier(Mob)` rather than making the field public.

- [ ] **Step 2: Run the GameTest and verify the leak**

Run:

```powershell
.\gradlew.bat runGameTestServer
```

Expected: `removingTraitDisablesInstalledGoal` fails because the rank-zero callback currently does nothing and runtime state remains.

- [ ] **Step 3: Route rank zero through cleanup**

Change Trait integration to call sync for every server-side Mob rank:

```java
private static void ensureGoal(LivingEntity entity, int rank) {
    if (!entity.level().isClientSide() && entity instanceof Mob mob) {
        SpellGoalInstaller.sync(mob, rank);
    }
}
```

`sync` immediately calls `remove(mob)` when rank is zero, settings are disabled, or the Mob is no longer eligible.

- [ ] **Step 4: Make removal complete and repeat-safe**

`SpellGoalInstaller.remove` removes the Goal when present, then delegates to `CastRuntimeRegistry.remove`. Registry removal calls `controller.close()` once. Calling removal a second time does nothing. Keep `EntityLeaveLevelEvent` connected to this same path.

Add public read-only `SpellGoalInstaller.isInstalled(Mob)` and `SpellPowerService.hasModifier(Mob)` methods for deterministic GameTest observation; neither method may mutate runtime state.

- [ ] **Step 5: Cancel immediately when the master switch is reloaded off**

Implement the Task 2 hook:

```java
public static synchronized void onConfigReload(Settings settings) {
    if (!settings.enabled()) {
        List.copyOf(INSTALLED.keySet()).forEach(SpellGoalInstaller::remove);
    }
}
```

Also make `SpellCasterGoal.canContinueToUse()` require cached settings enabled, and cancel if it turns false. Config-driven cancellation does not charge interrupt mana or global cooldown.

- [ ] **Step 6: Add a config-disable integration test**

Spawn a caster, force a long cast through a deterministic test helper, invoke `SpellGoalInstaller.onConfigReload(Settings.defaults().withEnabled(false))`, and assert the session ends, mana is unchanged, modifier is gone, and registry lookup is empty. Restore the default settings in a `finally` block so later GameTests are isolated.

- [ ] **Step 7: Re-run GameTests and unit tests**

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
```

Expected: all tests pass; both removal triggers use the same cleanup path.

- [ ] **Step 8: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/trait src/main/java/com/reist/enemyspellcast/ai src/main/java/com/reist/enemyspellcast/casting src/main/java/com/reist/enemyspellcast/event src/main/java/com/reist/enemyspellcast/gametest
git commit -m "fix: clean up removed and disabled spell casters"
```

### Task 5: Remove Scoring Allocations and Share Ally Searches

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/targeting/TargetFinder.java`
- Modify: `src/main/java/com/reist/enemyspellcast/targeting/SpellScorer.java`
- Modify: `src/main/java/com/reist/enemyspellcast/catalog/SpellCatalog.java`
- Modify: `src/test/java/com/reist/enemyspellcast/targeting/SpellScorerTest.java`

**Interfaces:**
- Consumes: `SpellCatalog.find(rank,id)`, active loadout IDs, controller availability, and cached config.
- Produces: one `TargetFinder.findAllies(Mob,double)` call at most per decision and no per-decision ID Map.

- [ ] **Step 1: Add a failing shared-search test**

Provide a package-private `SpellScorer(TargetFinder)` constructor. A fake finder increments an `AtomicInteger`, returns no allies, and three active ALLY candidates are passed through the package-private scoring overload:

```java
assertTrue(scorer.choose(caster, controller, 5, settings, rolls).isEmpty());
assertEquals(1, allySearches.get());
```

Add a second case containing only ENEMY/SELF candidates and assert zero ally searches.

- [ ] **Step 2: Run the scorer tests and verify failure**

Run:

```powershell
.\gradlew.bat test --tests "*.SpellScorerTest"
```

Expected: compilation fails because `TargetFinder` and injectable construction do not exist.

- [ ] **Step 3: Implement native target lookup with one bounded ally query**

`TargetFinder.Native` returns the current hostile target and performs the existing bounded `getEntitiesOfClass` query. In `choose`, resolve active IDs using `catalog.find(traitRank,id)` instead of building `HashMap` from all eligible spells.

Lazily populate one local immutable ally list on the first ALLY candidate and reuse it for all remaining ALLY candidates. Select the lowest-health valid ally for each support candidate without another world query.

- [ ] **Step 4: Preserve RNG and tie semantics**

Call `withJitter` exactly once for every candidate that has a valid target and is available, in active-loadout order. Keep strict `score > best.score()` so the first equal-scoring slot still wins. Do not cache targets across decisions.

- [ ] **Step 5: Run scorer, loadout, and full unit tests**

Run:

```powershell
.\gradlew.bat test --tests "*.SpellScorerTest" --tests "*.LoadoutServiceTest"
.\gradlew.bat test
```

Expected: all pass; the ally-search counter is one for three support candidates.

- [ ] **Step 6: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/targeting src/main/java/com/reist/enemyspellcast/catalog src/test/java/com/reist/enemyspellcast/targeting
git commit -m "perf: reuse spell lookups and ally searches"
```

### Task 6: Add Correct Target-Data and Ally-Cast Behaviors

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/casting/SpellInvocation.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/TargetEntitySpellBehavior.java`
- Modify: `src/main/java/com/reist/enemyspellcast/catalog/SpellBehaviorId.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/SpellBehavior.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/DirectSpellBehavior.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/AllySelfCastBehavior.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/SpellBehaviorRegistry.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/CastSession.java`
- Modify: `src/main/java/com/reist/enemyspellcast/casting/SpellCastController.java`
- Modify: `src/main/java/com/reist/enemyspellcast/compat/iron/IronSpellBridge.java`
- Modify: `src/test/java/com/reist/enemyspellcast/casting/SpellCastControllerTest.java`

**Interfaces:**
- Consumes: an AI-selected target and Iron `MagicData`.
- Produces: `SpellBehaviorId.TARGET_ENTITY`, and `SpellInvocation(LivingEntity executionEntity, MagicData magicData)` used consistently for initiate/precast/tick/cast/complete.

- [ ] **Step 1: Write failing lifecycle-context tests**

Extend the fake bridge to record each lifecycle entity. Add tests asserting:

```java
// direct: every recorded entity is the original caster
// ally_self_cast: every recorded entity is the selected ally and magicData(ally) was requested
// target_entity: prepared MagicData contains TargetEntityCastData for the selected target
```

Because plain JUnit cannot safely instantiate arbitrary Minecraft living entities, keep direct state-machine assertions in JUnit and put real ally/target entity assertions in Task 8 GameTests. The JUnit fake must also prove that a failed `checkPreCast` consumes no mana and creates no session.

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```powershell
.\gradlew.bat test --tests "*.SpellCastControllerTest"
```

Expected: compilation fails on the new invocation and behavior ID.

- [ ] **Step 3: Introduce a per-session invocation context**

Add:

```java
public record SpellInvocation(@Nullable LivingEntity executionEntity, MagicData magicData) {
    public SpellInvocation {
        Objects.requireNonNull(magicData);
    }
}
```

The nullable annotation exists only for the existing world-free controller unit-test seam; native runtime behaviors receive a non-null caster/target. Update the fake bridge to return `new MagicData(true)` instead of `null`.

Change `SpellBehavior` to create the invocation before checking/starting. `DirectSpellBehavior` uses the original caster and its `MagicData`. `AllySelfCastBehavior` uses the selected ally and that ally's `MagicData`; the outer controller still owns and pays mana/cooldown from the Trait Mob.

Store the invocation in `CastSession` and pass its entity/data to every Iron lifecycle call, eliminating mixed target-caster data.

- [ ] **Step 4: Add a dedicated target-entity adapter**

`TargetEntitySpellBehavior` uses the caster invocation. Its prepare order is:

```java
iron.preCast(spell, caster, magicData);
magicData.setAdditionalCastData(new TargetEntityCastData(target));
```

This allows Iron's normal pre-cast sound/effects while replacing ray-selected data with the AI's validated hostile target. Register it as `target_entity`.

- [ ] **Step 5: Face enemy targets before pre-cast checks**

In `SpellCastController.tryStart`, call `environment.faceTarget(caster,target)` before `checkPreCast` for ENEMY spells. This makes instant forward/raycast spells such as `teleport`, `blood_step`, and aimed area spells use the chosen target direction rather than the Mob's prior look angle.

If pre-cast rejects a player-only or otherwise incompatible spell, return false with no mana/cooldown change and no warning on every AI decision. Catalog load already emits the experimental warning once.

- [ ] **Step 6: Run controller and codec tests**

Run:

```powershell
.\gradlew.bat test --tests "*.SpellCastControllerTest" --tests "*.SpellDefinitionTest"
.\gradlew.bat test
```

Expected: lifecycle entity/data remain consistent and `target_entity` round-trips through JSON.

- [ ] **Step 7: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/casting src/main/java/com/reist/enemyspellcast/catalog src/main/java/com/reist/enemyspellcast/compat src/test/java/com/reist/enemyspellcast/casting
git commit -m "fix: use explicit spell target contexts"
```

### Task 7: Expand the Safe Pool and Ship Opt-In Experimental Definitions

**Files:**
- Modify: `src/main/resources/data/enemyspellcast/enemyspellcast/spell_pool/core.json`
- Create: `src/main/resources/data/enemyspellcast/enemyspellcast/spell_pool/experimental.json`
- Modify: `src/test/java/com/reist/enemyspellcast/resources/PackResourcesTest.java`
- Modify: `src/test/java/com/reist/enemyspellcast/catalog/SpellSelectionPolicyTest.java`

**Interfaces:**
- Consumes: `direct`, `ally_self_cast`, `target_entity`, safety categories, rarity caps, and configured lists.
- Produces: 49 standard entries plus risky definitions that are inactive by default.

- [ ] **Step 1: Replace the broadness test with exact safety and variety assertions**

Decode both files. For `core.json`, assert size is between 45 and 55, IDs are unique, every entry is `STANDARD`, and these IDs exist:

```text
teleport, blood_step, evasion, slow, root, arcane_shackle,
chain_lightning, arrow_volley, fang_ward, firecracker, ice_spikes,
shockwave, ball_lightning, poison_splash, gravity_fissure
```

Assert `starfall`, `telekinesis`, `raise_dead`, `earthquake`, `portal`, and `acid_orb` are absent from core. `starfall` remains excluded because it is `CONTINUOUS`; `acid_orb` remains excluded because its AoE applies Rend without an allegiance check.

For `experimental.json`, assert at least one definition exists for each risky category and no entry is `STANDARD`.

- [ ] **Step 2: Run resource tests and verify failure**

Run:

```powershell
.\gradlew.bat test --tests "*.PackResourcesTest" --tests "*.SpellSelectionPolicyTest"
```

Expected: the pool-size and required-ID assertions fail.

- [ ] **Step 3: Expand core to these exact 49 IDs**

Keep the existing 28 except `acid_orb`, then add these 22 IDs:

```text
teleport, blood_step, evasion, slow, root, arcane_shackle,
chain_lightning, arrow_volley, fang_ward, firecracker, ice_spikes,
shockwave, ball_lightning, poison_splash, gravity_fissure,
fang_swirl, blood_needles, gust, flaming_barrage, gluttony,
frostbite, spider_aspect
```

Use these metadata rules:

- Basic projectile/short attack: weight 10, rank 1.
- `teleport`, `blood_step`, `evasion`: weight 7, rank 2; teleport spells target `enemy`, evasion targets `self`.
- `slow`, `root`, `arcane_shackle`: weight 7, rank 2 or their higher natural rarity; `slow` and `root` use `target_entity`.
- `chain_lightning`, `arrow_volley`, `firecracker`, `ice_spikes`, `shockwave`, `ball_lightning`, `poison_splash`: weight 7–8, rank 2; target-data users use `target_entity`.
- `gravity_fissure`, `fang_swirl`, `fang_ward`: weight 5–6, rank 3.
- `gluttony`, `frostbite`, `spider_aspect`: target `self`, weight 6–8, rank 1–2.
- Ranges are 2–24 for aimed attacks, 0–16 for self/support, and 2–32 only where the Iron source already supports that target range.

- [ ] **Step 4: Add exact experimental definitions behind safety categories**

Create entries for:

```text
summoning: raise_dead, summon_vex, summon_polar_bear, summon_horse,
           lob_creeper, chain_creeper, summon_swords
terrain_change: earthquake, touch_dig
portal: portal, recall, pocket_dimension
```

Give each entry its category, weight 5, and rank 3 or higher except `touch_dig` rank 1 and `summon_horse` rank 2. Use `target_entity` only for Iron spells that consume `TargetEntityCastData`; otherwise use `direct`.

Default settings must produce zero experimental IDs. Add policy assertions showing `exclude=false` alone and explicit allow alone both produce zero; their combination admits the requested definition unless Iron disables or rejects it.

- [ ] **Step 5: Audit every new standard spell against local Iron source**

For each new entry, record in the progress ledger: cast type, additional cast-data type, spawned Entity lifetime/owner, friendly-fire predicate, and whether it changes blocks or dimensions. Remove an entry from core if any of these are true:

- `CastType.CONTINUOUS`;
- requires `Player`/`ServerPlayer`;
- creates a Mob or long-lived pseudo-Mob;
- changes blocks/dimensions;
- applies harmful AoE to allies without `DamageSources.isFriendlyFireBetween` or damage-event gating.

Replace any removed entry with a self buff or ordinary projectile that passes the same audit, while retaining 45–55 total entries and all required safe IDs that pass their GameTests.

- [ ] **Step 6: Run resource, catalog, and loadout tests**

Run:

```powershell
.\gradlew.bat test --tests "*.PackResourcesTest" --tests "*.SpellSelectionPolicyTest" --tests "*.LoadoutServiceTest"
```

Expected: all pass; default pool contains 45–55 entries and no risky category.

- [ ] **Step 7: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/resources/data/enemyspellcast/enemyspellcast/spell_pool src/test/java/com/reist/enemyspellcast/resources src/test/java/com/reist/enemyspellcast/catalog
git commit -m "feat: expand and classify enemy spell pools"
```

### Task 8: Add Cross-Mod Casting, Safety, and Cleanup GameTests

**Files:**
- Modify: `src/main/java/com/reist/enemyspellcast/gametest/EnemySpellCastGameTests.java`
- Modify: `src/main/java/com/reist/enemyspellcast/allegiance/FriendlyFireEvents.java`
- Inspect: `src/main/java/com/reist/enemyspellcast/allegiance/AllegianceResolver.java`
- Inspect: `src/main/java/com/reist/enemyspellcast/casting/AllySelfCastBehavior.java`

**Interfaces:**
- Consumes: the complete config, catalog, behavior, controller, and cleanup implementation.
- Produces: native proof for successful offense, target-data control, ally support, teleport safety, AoE allegiance, and state removal.

- [ ] **Step 1: Add a deterministic GameTest spell-forcing helper**

Add a helper that saves a one-ID loadout into the spawned caster, obtains the catalog's resolved spell at the requested rank, restores full mana, sets the target, and starts via the real controller. It must fail the test immediately when the ID is not eligible rather than waiting for random selection.

- [ ] **Step 2: Test a real offensive cast**

Force `firebolt` on a zombie and a survival mock player 8 blocks away. After completion assert mana decreased, the spell cooldown is positive, and either player health decreased or a firebolt projectile was observed before impact.

- [ ] **Step 3: Test explicit target data for control magic**

Force `root` or `slow` with the caster initially looking away from the player. Assert the selected player becomes rooted/slowed and a nearby hostile ally does not. This proves `TargetEntityCastData` uses the AI target rather than look-direction ray selection.

- [ ] **Step 4: Test support execution context**

Spawn two allied zombies, damage one to 25% health, force `heal`, and assert the ally gains health while a nearby mock player does not. Assert caster mana/cooldown changed, the ally's `MagicData` casting state reset, and the caster's state also remains reset after completion.

- [ ] **Step 5: Test teleport safety**

Force `teleport` with a player target across a flat floor and a solid wall beyond the safe landing area. Assert the zombie changed position, its resulting bounding box has no block collision, and its Y coordinate remains above the structure floor.

- [ ] **Step 6: Test delayed AoE and debuff allegiance**

For each standard spell family that creates an AoE/delayed-damage Entity, select at least one representative: `firecracker`, `shockwave`, and `fang_ward`. Place an allied zombie and player in the effect. Assert the ally's health and harmful-effect set are unchanged while the player receives damage or the intended effect. `poison_splash` and `frostwave` are excluded because their direct harmful-effect application cannot be cancelled safely for hostile allies.

If an ally receives a harmful effect and Iron exposes no source-aware cancellable effect event, remove that spell from core instead of adding a global effect cancellation heuristic. Keep direct damage cancellation in `FriendlyFireEvents` and reuse `AllegianceResolver` for any source-aware event that does exist.

- [ ] **Step 7: Test cleanup and unsupported experimental failure**

Retain the strengthened rank-zero and config-disable tests from Task 4. Add one experimental definition whose Iron precondition is player-only (`portal` or `recall`), enable both opt-ins, and assert `tryStart` returns false without mana/cooldown change or server error.

- [ ] **Step 8: Run GameTests repeatedly**

Run twice to catch leaked static config/catalog state:

```powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat runGameTestServer
```

Expected: both runs pass with no missing structure, cast lifecycle, duplicate modifier, or repeated warning errors.

- [ ] **Step 9: Record the task checkpoint**

If Git exists, commit with:

```powershell
git add src/main/java/com/reist/enemyspellcast/gametest src/main/java/com/reist/enemyspellcast/allegiance src/main/java/com/reist/enemyspellcast/casting
git commit -m "test: verify spell variety and allegiance safety"
```

### Task 9: Document Configuration, Validate Load, and Build the Release JAR

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/manual-test-checklist.md`
- Modify: `.superpowers/sdd/2026-09-21-enemy-spell-cast/progress.md`

**Interfaces:**
- Consumes: final config keys, pool schema, behavior IDs, tests, and JAR.
- Produces: operator documentation, a repeatable manual test matrix, and `build/libs/enemyspellcast-0.1.0.jar`.

- [ ] **Step 1: Update the configuration table with exact defaults**

Document:

```text
spellOverrides.selectionMode = DEFAULT_POOL
spellOverrides.allowedSpellIds = []
spellOverrides.forceAllowSpellIds = []
spellOverrides.forceDenySpellIds = []
behavior.excludeSummoningSpells = true
behavior.excludeTerrainChangingSpells = true
behavior.excludePortalSpells = true
```

Explain `ALLOWLIST_ONLY`, deny precedence, valid-ID retention on reload, empty-pool behavior, and the two required opt-ins for risky categories. State clearly that risky spells are unsupported and player-only Iron spells can still reject Mob casting.

- [ ] **Step 2: Document the extended datapack schema**

Add `target_entity` to supported behaviors and `safety_category` values to the example/reference. Include one safe example and one experimental example. Explain that detailed weights/ranks/targeting belong in JSON while config controls admission.

- [ ] **Step 3: Expand the manual checklist**

Add checks for:

```text
- Default config: no summon, terrain, or portal spell is selected.
- ALLOWLIST_ONLY with root/teleport: only those IDs appear, subject to rank rarity.
- Risk category false without explicit ID: risky spell remains absent.
- Explicit risky ID while category true: risky spell remains absent.
- Both risky opt-ins: addon admits the spell and prints one unsupported warning.
- Root/slow hits the chosen player while the caster initially looks away.
- Teleport lands outside blocks and above the floor.
- Poison/frost/shock/fang representative does not harm or debuff hostile allies.
- Trait removal and config disable remove controller and spell-power modifier.
- 50 rank-3 casters for several minutes: no repeated warning or sustained tick stall.
```

- [ ] **Step 4: Update release notes**

Add bullets for cached config/catalog, generation-based installer fast path, complete lifecycle cleanup, shared ally lookup, target-data behavior, 45–55 safe candidates, and unsupported double-opt-in categories.

- [ ] **Step 5: Run final clean verification**

Run:

```powershell
.\gradlew.bat clean test runGameTestServer build --no-daemon --no-configuration-cache
```

Expected: exit code 0, all JUnit and GameTests pass, and `build/libs/enemyspellcast-0.1.0.jar` exists.

- [ ] **Step 6: Inspect the packaged data and metadata**

Run:

```powershell
jar tf build\libs\enemyspellcast-0.1.0.jar | Select-String -Pattern "spell_pool/core.json|spell_pool/experimental.json|trait_data.json|neoforge.mods.toml"
jar tf build\libs\enemyspellcast-0.1.0.jar | Select-String -Pattern "^\.reference/|com/example/examplemod|examplemod"
```

Expected: the first command lists all four required resources; the second prints nothing.

- [ ] **Step 7: Check final pool and Trait values from the JAR**

Decode packaged JSON with the existing `PackResourcesTest` and confirm: Trait weight 100, cost 80, minimum level 100, maximum rank 5, core count 45–55, default risky count 0 after catalog filtering.

- [ ] **Step 8: Record final evidence**

Append exact command results, JUnit count, GameTest count, core pool count, and final JAR path to the progress ledger. If Git exists, commit with:

```powershell
git add README.md CHANGELOG.md docs .superpowers src
git commit -m "docs: finalize performance and spell variety update"
```

## Execution Notes

- Read the linked design before Task 1 and keep these checkboxes synchronized with the progress ledger.
- Execute tasks in order because config generations feed the catalog, and both feed the installer fingerprint.
- Use `superpowers:test-driven-development` for every production change and retain each red/green command result.
- Use `superpowers:systematic-debugging` for API mismatches or failed tests; compare with `.reference/L2Hostility` and `.reference/Irons-Spells-n-Spellbooks` before changing behavior.
- Use `superpowers:verification-before-completion` before reporting completion.
- The previously selected execution method is Native, so implementation should use `superpowers:executing-plans` after this plan is approved.
