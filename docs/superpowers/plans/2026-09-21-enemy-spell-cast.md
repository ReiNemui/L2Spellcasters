# Enemy Spell Cast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Minecraft 1.21.1 / NeoForge 上で、L2 Hostility の「魔導」特性を持つ敵対 Mob が Iron's Spells 'n Spellbooks の安全な魔法を状況判断しながら詠唱する、設定可能で保守しやすいアドオン MOD を構築する。

**Architecture:** L2 Hostility の公開Traitレジストリとライフサイクルへ `SpellCasterTrait` を追加し、Traitを持つ `Mob` にだけ独立した `SpellCasterGoal` を装着する。魔法候補はデータパックで読み込み、永続データ、抽選、対象判定、詠唱制御、Iron連携を小さなサービスへ分離するため、個別の魔法や判定規則を後から交換できる。Ironの `IMagicEntity` は任意Mobへ注入せず、公開 `AbstractSpell` ライフサイクルと `MagicData` を薄い互換層から呼び出す。

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.251, ModDevGradle 2.0.147, L2 Hostility 3.0.18, Iron's Spells 'n Spellbooks 1.21.1-3.16.3, JUnit 5, NeoForge GameTest, JSON data packs, NeoForge common config.

**Spec:** `docs/superpowers/specs/2026-09-21-enemy-spell-cast-design.md`

## Global Constraints

- MOD名は `Enemy Spell Cast`、MOD ID は `enemyspellcast`、Javaパッケージは `com.reist.enemyspellcast` とする。
- Minecraftは厳密に1.21.1、NeoForgeは21.1系、Javaは21とする。
- L2 Hostility 3.0.18 と Iron's Spells 'n Spellbooks 1.21.1-3.16系を必須依存にする。
- 特性IDは `enemyspellcast:spell_caster`、英語名は `Spell Caster`、日本語名は `魔導`、最大ランクは5とする。
- 自然付与の初期値は `min_level=100`、`cost=80`、`weight=30`、`max_rank=5` とする。
- ランク別スロット数は `1, 1, 2, 2, 3`、最大レアリティは `Common, Uncommon, Rare, Epic, Legendary` とする。
- ランク5では魔法本来の最大レベルを使用し、魔法レベル式は `1 + floor((maxLevel - 1) * (rank - 1) / 4)` とする。
- 魔法威力、最大マナ、マナ回復は無制限に段階上昇し、詠唱速度は3倍、クールタイム倍率は25%を初期上限・下限とする。
- 攻撃・デバフ魔法は敵側同士へ作用させず、回復・バフは自分および敵側の味方へ作用できるようにする。
- 一回の実ダメージが最大体力の5%以上なら詠唱を中断し、予定マナの25%と20 Tickの共通クールタイムを課す。
- ボス、召喚Mob、Iron側の既存詠唱Mobは初期状態で除外し、タグとconfigで上書き可能にする。
- 一般Mobへ `IMagicEntity` をMixin注入しない。クライアントに戦闘判断をさせない。
- `.reference/` はAPI確認専用で、製品JAR、ソースセット、配布物へ含めない。
- 現在のディレクトリにはGitリポジトリがない。各タスク末尾のcommitは、ユーザーがGit初期化または既存リポジトリへの移管を明示した後だけ実行し、自動で `git init` しない。

## Review Focus

- データパックに存在しない魔法ID、無効化済み魔法、継続詠唱魔法が混ざっても、そのエントリだけを警告して無視しサーバーを継続すること（Task 4とTask 9で固定する）。
- ランク低下、再上昇、`/reload`、チャンク再読込が続いても、既存ロードアウトの順序と余剰スロットを壊さず必要な差し替えだけを行うこと（Task 5で固定する）。
- 魔法発動中に対象死亡、ディメンション変更、Trait削除、Mob死亡が起きた場合、Ironの詠唱状態を一度だけcancel完了し一時状態を残さないこと（Task 7とTask 8で固定する）。
- 投射物の直接Entityと実所有者が異なる場合や、スコアボードチーム・OwnableEntityが関係する場合も、実所有者基準で同士討ちを止めること（Task 6で固定する）。
- configの段階幅0、負数、NaN相当の不正入力、極端なMobレベルでもゼロ除算・オーバーフロー・負クールタイムを起こさず安全値へ補正すること（Task 2で固定する）。

---

## File Structure

製品コードは以下の責務で分割する。1ファイルに複数の変更理由を持たせない。

```text
src/main/java/com/reist/enemyspellcast/
  EnemySpellCast.java                    MOD起点、レジストリ・config・イベント登録
  config/EnemySpellCastConfig.java       NeoForge common configと検証済みSnapshot
  progression/ProgressionRules.java      ランク、魔法レベル、段階式の純粋計算
  trait/SpellCasterTrait.java             L2 Trait本体と対象可否
  trait/ModTraits.java                    L2 Traitレジストリ
  trait/ModItems.java                     TraitSymbolアイテム
  compat/l2/L2HostilityBridge.java        L2 cap参照を隔離
  compat/iron/IronSpellBridge.java        Iron Spell/MagicData呼出しを隔離
  catalog/SpellDefinition.java            データパック1エントリ
  catalog/SpellPoolFile.java              JSONファイル構造
  catalog/SpellCatalog.java               不変の解決済み候補集合
  catalog/SpellCatalogReloadListener.java reload処理
  loadout/SpellCasterData.java             永続化するロードアウト・マナ・CD
  loadout/SpellCasterDataStore.java        Entity persistent NBT入出力
  loadout/WeightedPicker.java              重複なし加重抽選
  loadout/LoadoutService.java              ランク変更・reload時の整合
  allegiance/Allegiance.java               陣営結果enum
  allegiance/AllegianceResolver.java       Entityから敵味方を判定
  allegiance/FriendlyFireEvents.java       Iron SpellDamageEventの防御網
  targeting/SpellTarget.java               対象とスコア候補
  targeting/SpellScorer.java               状況スコアと乱数補正
  casting/CastSession.java                 保存しない進行中詠唱
  casting/SpellBehavior.java               魔法別差分の拡張点
  casting/DirectSpellBehavior.java         標準攻撃・自己魔法
  casting/AllySelfCastBehavior.java        味方を受益者にする自己回復・buff
  casting/SpellBehaviorRegistry.java       behavior IDから実装を解決
  casting/SpellCastController.java         マナ、CD、Iron lifecycle、中断
  casting/CastRuntimeRegistry.java          Mobごとの一時Controller参照
  casting/CastingTelegraph.java             汎用の音・魔法色パーティクル予告
  ai/SpellCasterGoal.java                  判断周期とGoal制御
  ai/SpellGoalInstaller.java               多重登録を防ぐWeakHashMap
  event/CommonEvents.java                  reload、被ダメージ、死亡イベント
  gametest/EnemySpellCastGameTests.java    MOD間統合テスト
```

```text
src/main/resources/
  assets/enemyspellcast/lang/en_us.json
  assets/enemyspellcast/lang/ja_jp.json
  assets/enemyspellcast/models/item/spell_caster.json
  assets/enemyspellcast/textures/item/trait/spell_caster.png
  data/enemyspellcast/data_maps/l2hostility/trait/trait_data.json
  data/l2hostility/tags/item/trait_item.json
  data/enemyspellcast/tags/entity_type/spell_caster_blacklist.json
  data/enemyspellcast/tags/entity_type/spell_caster_force_allow.json
  data/enemyspellcast/tags/entity_type/spell_support_blacklist.json
  data/enemyspellcast/enemyspellcast/spell_pool/core.json
```

```text
src/test/java/com/reist/enemyspellcast/
  progression/ProgressionRulesTest.java
  catalog/SpellDefinitionTest.java
  loadout/WeightedPickerTest.java
  loadout/LoadoutServiceTest.java
  allegiance/AllegianceResolverTest.java
  casting/SpellCastControllerTest.java
  resources/PackResourcesTest.java
```

### Task 1: Clean MOD Scaffold and Resolve Both Required MODs

**Files:**
- Modify: `.gitignore`
- Modify: `build.gradle`
- Modify: `gradle.properties`
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Delete during execution: `src/main/java/com/example/examplemod/Config.java`
- Delete during execution: `src/main/java/com/example/examplemod/ExampleMod.java`
- Delete during execution: `src/main/java/com/example/examplemod/ExampleModClient.java`
- Delete during execution: `src/main/resources/assets/examplemod/lang/en_us.json`
- Create: `src/main/java/com/reist/enemyspellcast/EnemySpellCast.java`

**Interfaces:**
- Consumes: NeoForge MDK already present in the workspace.
- Produces: `EnemySpellCast.MOD_ID`, a compilable empty addon, JUnit 5 execution, and a runtime classpath containing L2 Hostility and Iron's Spells.

- [ ] **Step 1: Add a dependency-resolution smoke test before changing the source tree**

Create `src/test/java/com/reist/enemyspellcast/DependencySmokeTest.java`:

```java
package com.reist.enemyspellcast;

import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DependencySmokeTest {
    @Test
    void requiredApisAreOnTheTestClasspath() {
        assertNotNull(MobTrait.class);
        assertNotNull(SpellRegistry.class);
    }
}
```

- [ ] **Step 2: Configure exact repositories, dependencies, and JUnit**

Add these repositories and dependencies to `build.gradle`:

```groovy
repositories {
    maven { url = "https://code.redspace.io/releases" }
    maven { url = "https://code.redspace.io/snapshots" }
    maven { url = "https://maven.theillusivec4.top" }
    maven { url = "https://maven.blamejared.com" }
    exclusiveContent {
        forRepository {
            maven { url = "https://api.modrinth.com/maven" }
        }
        filter { includeGroup "maven.modrinth" }
    }
}

dependencies {
    implementation "maven.modrinth:CbV689EN:hsGoGiGk"
    implementation "maven.modrinth:l2library:enkPVvxo"
    implementation "maven.modrinth:l2-complements:cXczjZfn"
    implementation "io.redspace:irons_spellbooks:${irons_spellbooks_version}"

    testImplementation platform("org.junit:junit-bom:5.11.4")
    testImplementation "org.junit.jupiter:junit-jupiter"
}

tasks.named('test', Test).configure {
    useJUnitPlatform()
}
```

Add to `gradle.properties`:

```properties
mod_id=enemyspellcast
mod_name=Enemy Spell Cast
mod_license=All Rights Reserved
mod_version=0.1.0
mod_group_id=com.reist.enemyspellcast
irons_spellbooks_version=1.21.1-3.16.3
```

Retain `minecraft_version=1.21.1`, `neo_version=21.1.251`, and Java 21 from the MDK.

- [ ] **Step 3: Run dependency resolution and verify the smoke test initially fails only because the old example source still exists**

Run: `./gradlew.bat test --tests "com.reist.enemyspellcast.DependencySmokeTest" --stacktrace`

Expected: Gradle resolves both required mods and the smoke test passes. If a concrete transitive artifact is missing, use the exact coordinate and version named in the downloaded mod's `META-INF/jarjar/metadata.json`, add it to `implementation`, and rerun this same command.

- [ ] **Step 4: Replace the example entrypoint with the real MOD entrypoint**

Create `EnemySpellCast.java`:

```java
package com.reist.enemyspellcast;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(EnemySpellCast.MOD_ID)
public final class EnemySpellCast {
    public static final String MOD_ID = "enemyspellcast";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public EnemySpellCast(IEventBus modBus, ModContainer container) {
    }
}
```

Remove the four example files listed above, and add `/.reference/` to `.gitignore`.

- [ ] **Step 5: Make metadata require both mods on both sides**

Replace the template description and append:

```toml
[[dependencies.${mod_id}]]
modId="l2hostility"
type="required"
versionRange="[3.0.18,4)"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="irons_spellbooks"
type="required"
versionRange="[1.21.1-3.16,1.21.2)"
ordering="AFTER"
side="BOTH"
```

Use this description:

```toml
description='''
Adds an L2 Hostility trait that lets hostile mobs cast curated Iron's Spells with scalable AI.
'''
```

- [ ] **Step 6: Verify clean compile, unit discovery, and dependency metadata**

Run: `./gradlew.bat clean test compileJava generateModMetadata`

Expected: `BUILD SUCCESSFUL`, one passing `DependencySmokeTest`, and generated metadata containing required `l2hostility` and `irons_spellbooks` entries.

- [ ] **Step 7: Commit after Git is explicitly available**

```powershell
git add .gitignore build.gradle gradle.properties src/main docs/superpowers
git commit -m "build: configure Enemy Spell Cast dependencies"
```

### Task 2: Lock Progression and Config Semantics with Pure Tests

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/config/EnemySpellCastConfig.java`
- Create: `src/main/java/com/reist/enemyspellcast/progression/ProgressionRules.java`
- Create: `src/test/java/com/reist/enemyspellcast/progression/ProgressionRulesTest.java`
- Modify: `src/main/java/com/reist/enemyspellcast/EnemySpellCast.java`

**Interfaces:**
- Consumes: `ModContainer.registerConfig` from Task 1.
- Produces: `EnemySpellCastConfig.snapshot(): Settings`, `ProgressionRules.forMobLevel(int, Settings): Scaling`, `slotCount(int)`, `spellLevel(int,int)`, and `rarityCap(int)`.

- [ ] **Step 1: Write progression tests, including malformed and extreme values**

```java
package com.reist.enemyspellcast.progression;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionRulesTest {
    private static final Settings DEFAULTS = Settings.defaults();

    @Test void level100UsesBaseStats() {
        var value = ProgressionRules.forMobLevel(100, DEFAULTS);
        assertAll(
                () -> assertEquals(0, value.tier()),
                () -> assertEquals(1.0, value.spellPower(), 1e-9),
                () -> assertEquals(100.0, value.maxMana(), 1e-9),
                () -> assertEquals(1.0, value.castSpeed(), 1e-9),
                () -> assertEquals(1.0, value.cooldownMultiplier(), 1e-9));
    }

    @Test void scalingIsSteppedAndCapsOnlySpeedAndCooldown() {
        var value = ProgressionRules.forMobLevel(1100, DEFAULTS);
        assertEquals(20, value.tier());
        assertEquals(3.0, value.spellPower(), 1e-9);
        assertEquals(500.0, value.maxMana(), 1e-9);
        assertEquals(2.0, value.manaRegenMultiplier(), 1e-9);
        assertEquals(2.0, value.castSpeed(), 1e-9);
        assertEquals(0.25, value.cooldownMultiplier(), 1e-9);
    }

    @Test void rankTableAndMaxLevelAreExact() {
        assertArrayEquals(new int[]{1, 1, 2, 2, 3},
                java.util.stream.IntStream.rangeClosed(1, 5).map(ProgressionRules::slotCount).toArray());
        assertEquals(1, ProgressionRules.spellLevel(10, 1));
        assertEquals(10, ProgressionRules.spellLevel(10, 5));
        assertEquals(SpellRarity.RARE, ProgressionRules.rarityCap(3));
    }

    @Test void invalidSettingsAreSanitizedWithoutOverflow() {
        var invalid = new Settings(true, 100, 0, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0, -1, -1, -1, -1, true, -1, -1, -1, true,
                java.util.List.of(), java.util.List.of());
        var safe = invalid.sanitized();
        assertTrue(safe.levelStep() >= 1);
        assertTrue(safe.castSpeedCap() >= 1);
        assertTrue(safe.cooldownFloor() > 0 && safe.cooldownFloor() <= 1);
        assertDoesNotThrow(() -> ProgressionRules.forMobLevel(Integer.MAX_VALUE, safe));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails on missing classes**

Run: `./gradlew.bat test --tests "*.ProgressionRulesTest"`

Expected: FAIL because `Settings` and `ProgressionRules` do not exist.

- [ ] **Step 3: Implement immutable settings and pure progression calculations**

Use these public records and signatures:

```java
public record Settings(
        boolean enabled,
        int baseMobLevel,
        int levelStep,
        double spellPowerPerTier,
        double baseMaxMana,
        double maxManaPerTier,
        double baseManaRegenPerSecond,
        double manaRegenPerTier,
        double castSpeedPerTier,
        double castSpeedCap,
        double cooldownReductionPerTier,
        double cooldownFloor,
        int decisionIntervalTicks,
        double randomScoreJitter,
        double interruptDamageFraction,
        double interruptManaFraction,
        int interruptGlobalCooldownTicks,
        boolean friendlyFireProtection,
        double castingMoveSpeed,
        double minimumCastScore,
        double supportSearchRange,
        boolean allowContinuousSpells,
        List<? extends String> forceAllowSpellIds,
        List<? extends String> forceDenySpellIds) {
    public static Settings defaults() {
        return new Settings(true, 100, 50, .10, 100, 20, 2, .05, .05, 3,
                .05, .25, 10, .15, .05, .25, 20, true, .25, .20, 16,
                false, List.of(), List.of());
    }

    public Settings sanitized() {
        return new Settings(enabled, Math.max(0, baseMobLevel), Math.max(1, levelStep),
                Math.max(0, spellPowerPerTier), Math.max(1, baseMaxMana), Math.max(0, maxManaPerTier),
                Math.max(0, baseManaRegenPerSecond), Math.max(0, manaRegenPerTier),
                Math.max(0, castSpeedPerTier), Math.max(1, castSpeedCap),
                Math.max(0, cooldownReductionPerTier), Math.clamp(cooldownFloor, .01, 1),
                Math.max(1, decisionIntervalTicks), Math.max(0, randomScoreJitter),
                Math.clamp(interruptDamageFraction, 0, 1), Math.clamp(interruptManaFraction, 0, 1),
                Math.max(0, interruptGlobalCooldownTicks), friendlyFireProtection,
                Math.clamp(castingMoveSpeed, 0, 1), Math.max(0, minimumCastScore),
                Math.max(1, supportSearchRange), allowContinuousSpells,
                List.copyOf(forceAllowSpellIds), List.copyOf(forceDenySpellIds));
    }
}
```

`ProgressionRules` must calculate with `long`/`double` intermediates and expose:

```java
public record Scaling(int tier, double spellPower, double maxMana,
                      double manaRegenMultiplier, double castSpeed,
                      double cooldownMultiplier) {}

public static Scaling forMobLevel(int mobLevel, Settings raw) {
    Settings s = raw.sanitized();
    int tier = (int) Math.min(Integer.MAX_VALUE,
            Math.max(0L, (long) mobLevel - s.baseMobLevel()) / s.levelStep());
    return new Scaling(tier,
            1 + tier * s.spellPowerPerTier(),
            s.baseMaxMana() + tier * s.maxManaPerTier(),
            1 + tier * s.manaRegenPerTier(),
            Math.min(s.castSpeedCap(), 1 + tier * s.castSpeedPerTier()),
            Math.max(s.cooldownFloor(), 1 - tier * s.cooldownReductionPerTier()));
}

public static int slotCount(int rank) {
    return switch (Math.clamp(rank, 1, 5)) { case 1, 2 -> 1; case 3, 4 -> 2; default -> 3; };
}

public static int spellLevel(int maxLevel, int rank) {
    int safeMax = Math.max(1, maxLevel);
    int safeRank = Math.clamp(rank, 1, 5);
    return 1 + (safeMax - 1) * (safeRank - 1) / 4;
}

public static SpellRarity rarityCap(int rank) {
    return SpellRarity.values()[Math.clamp(rank, 1, 5) - 1];
}
```

Define all config fields with the values from `Settings.defaults()`, then return only `snapshot().sanitized()` to gameplay code.

- [ ] **Step 4: Register the common config and pass all progression tests**

In `EnemySpellCast`:

```java
container.registerConfig(ModConfig.Type.COMMON, EnemySpellCastConfig.SPEC,
        EnemySpellCast.MOD_ID + "-common.toml");
```

Run: `./gradlew.bat test --tests "*.ProgressionRulesTest"`

Expected: PASS, including the zero-step and `Integer.MAX_VALUE` cases.

- [ ] **Step 5: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/config src/main/java/com/reist/enemyspellcast/progression src/test
git commit -m "feat: define spell caster progression rules"
```

### Task 3: Register the L2 Trait, Symbol Item, Data Map, Tags, and Localization

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/trait/ModTraits.java`
- Create: `src/main/java/com/reist/enemyspellcast/trait/ModItems.java`
- Create: `src/main/java/com/reist/enemyspellcast/trait/SpellCasterTrait.java`
- Create: `src/main/java/com/reist/enemyspellcast/compat/l2/L2HostilityBridge.java`
- Modify: `src/main/java/com/reist/enemyspellcast/EnemySpellCast.java`
- Create: `src/main/resources/data/enemyspellcast/data_maps/l2hostility/trait/trait_data.json`
- Create: `src/main/resources/data/l2hostility/tags/item/trait_item.json`
- Create: `src/main/resources/data/enemyspellcast/tags/entity_type/spell_caster_blacklist.json`
- Create: `src/main/resources/data/enemyspellcast/tags/entity_type/spell_caster_force_allow.json`
- Create: `src/main/resources/data/enemyspellcast/tags/entity_type/spell_support_blacklist.json`
- Create: `src/main/resources/assets/enemyspellcast/lang/en_us.json`
- Create: `src/main/resources/assets/enemyspellcast/lang/ja_jp.json`
- Create: `src/main/resources/assets/enemyspellcast/models/item/spell_caster.json`
- Create: `src/main/resources/assets/enemyspellcast/textures/item/trait/spell_caster.png`
- Create: `src/test/java/com/reist/enemyspellcast/resources/PackResourcesTest.java`

**Interfaces:**
- Consumes: `LHTraits.TRAITS.key()`, `MobTrait`, `TraitSymbol`, and config from Tasks 1–2.
- Produces: `ModTraits.SPELL_CASTER`, `L2HostilityBridge.traitRank(LivingEntity)`, `mobLevel(LivingEntity)`, and the visible L2 trait resource set.

- [ ] **Step 1: Write classpath resource tests for exact L2 paths and values**

```java
class PackResourcesTest {
    @Test void traitDataMapUsesL2RegistryPath() throws Exception {
        String path = "/data/enemyspellcast/data_maps/l2hostility/trait/trait_data.json";
        try (var in = getClass().getResourceAsStream(path)) {
            assertNotNull(in);
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            var trait = json.getAsJsonObject().getAsJsonObject("values")
                    .getAsJsonObject("enemyspellcast:spell_caster");
            assertEquals(80, trait.get("cost").getAsInt());
            assertEquals(30, trait.get("weight").getAsInt());
            assertEquals(5, trait.get("max_rank").getAsInt());
            assertEquals(100, trait.get("min_level").getAsInt());
        }
    }

    @Test void modelAndBothTranslationsExist() {
        assertNotNull(getClass().getResource("/assets/enemyspellcast/models/item/spell_caster.json"));
        assertNotNull(getClass().getResource("/assets/enemyspellcast/lang/en_us.json"));
        assertNotNull(getClass().getResource("/assets/enemyspellcast/lang/ja_jp.json"));
    }
}
```

- [ ] **Step 2: Run tests and verify missing-resource failure**

Run: `./gradlew.bat test --tests "*.PackResourcesTest"`

Expected: FAIL because trait resources do not exist.

- [ ] **Step 3: Register trait and matching TraitSymbol under the same resource location**

```java
public final class ModTraits {
    public static final DeferredRegister<MobTrait> TRAITS =
            DeferredRegister.create(LHTraits.TRAITS.key(), EnemySpellCast.MOD_ID);
    public static final DeferredHolder<MobTrait, SpellCasterTrait> SPELL_CASTER =
            TRAITS.register("spell_caster", SpellCasterTrait::new);

    public static void register(IEventBus bus) { TRAITS.register(bus); }
}

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnemySpellCast.MOD_ID);
    public static final DeferredItem<TraitSymbol> SPELL_CASTER = ITEMS.register("spell_caster",
            () -> new TraitSymbol(new Item.Properties()));

    public static void register(IEventBus bus) { ITEMS.register(bus); }
}

public final class SpellCasterTrait extends MobTrait {
    public SpellCasterTrait() { super(ChatFormatting.LIGHT_PURPLE); }

    @Override
    public boolean allow(LivingEntity entity, int difficulty, int maxModLevel) {
        if (!(entity instanceof Mob mob) || !(entity instanceof Enemy) || mob.isNoAi()) return false;
        if (entity.getType().is(ModEntityTags.SPELL_CASTER_FORCE_ALLOW)) {
            return super.allow(entity, difficulty, maxModLevel);
        }
        return !L2HostilityBridge.isSummonedOrMinion(entity)
                && !(entity instanceof OwnableEntity)
                && !entity.getType().is(ModEntityTags.SPELL_CASTER_BLACKLIST)
                && super.allow(entity, difficulty, maxModLevel);
    }
}
```

Create `ModEntityTags` beside `SpellCasterTrait` with `TagKey.create(Registries.ENTITY_TYPE, EnemySpellCast.id(name))` for all three tags.

Implement `L2HostilityBridge` with no gameplay policy:

```java
public static int traitRank(LivingEntity entity) {
    return LHMiscs.MOB.type().getExisting(entity)
            .map(cap -> cap.getTraitLevel(ModTraits.SPELL_CASTER.get())).orElse(0);
}

public static int mobLevel(LivingEntity entity) {
    return LHMiscs.MOB.type().getExisting(entity).map(MobTraitCap::getLevel).orElse(0);
}

public static boolean isSummonedOrMinion(LivingEntity entity) {
    return LHMiscs.MOB.type().getExisting(entity)
            .map(cap -> cap.summoned || cap.minion).orElse(false);
}
```

- [ ] **Step 4: Add the exact L2 data map and initial entity exclusions**

`trait_data.json`:

```json
{
  "values": {
    "enemyspellcast:spell_caster": {
      "cost": 80,
      "weight": 30,
      "max_rank": 5,
      "min_level": 100
    }
  }
}
```

`spell_caster_blacklist.json` starts with:

```json
{
  "replace": false,
  "values": [
    "minecraft:ender_dragon",
    "minecraft:wither",
    "minecraft:warden",
    "irons_spellbooks:pyromancer",
    "irons_spellbooks:cryomancer",
    "irons_spellbooks:necromancer",
    "irons_spellbooks:archevoker",
    "irons_spellbooks:citadel_keeper",
    "irons_spellbooks:fire_boss",
    "irons_spellbooks:priest",
    "irons_spellbooks:apothecarist",
    "irons_spellbooks:cultist",
    "irons_spellbooks:cursed_armor_stand",
    "irons_spellbooks:dead_king"
  ]
}
```

The force-allow and support-blacklist tags start as empty `replace:false` lists. Add `enemyspellcast:spell_caster` to `data/l2hostility/tags/item/trait_item.json`.

- [ ] **Step 5: Add visible model, translations, and a 16×16 transparent trait icon**

Model:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "l2hostility:item/bg",
    "layer1": "enemyspellcast:item/trait/spell_caster"
  }
}
```

Translations:

```json
{
  "trait.enemyspellcast.spell_caster": "Spell Caster",
  "trait.enemyspellcast.spell_caster.desc": "Casts a persistent random loadout of Iron's Spells. Rank controls spell count, rarity, and level."
}
```

```json
{
  "trait.enemyspellcast.spell_caster": "魔導",
  "trait.enemyspellcast.spell_caster.desc": "固定されたランダム構成のIron's Spellsを詠唱する。ランクに応じて魔法数、レアリティ、レベルが上昇する。"
}
```

During execution, use the `imagegen` skill to create a high-contrast violet rune/casting-circle icon with transparent background, then downscale with nearest-neighbor to exactly 16×16 PNG. Verify alpha and dimensions with ImageMagick `identify` or Pillow before adding it.

- [ ] **Step 6: Register both deferred registers and pass resource/build tests**

```java
ModTraits.register(modBus);
ModItems.register(modBus);
```

Run: `./gradlew.bat test --tests "*.PackResourcesTest" build`

Expected: PASS and the built JAR contains the trait data map, item model, icon, and both language files.

- [ ] **Step 7: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/trait src/main/java/com/reist/enemyspellcast/compat/l2 src/main/resources src/test
git commit -m "feat: register spell caster L2 trait"
```

### Task 4: Build a Reloadable, Validated Spell Catalog

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/catalog/TargetMode.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellBehaviorId.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellDefinition.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellPoolFile.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/ResolvedSpell.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellCatalog.java`
- Create: `src/main/java/com/reist/enemyspellcast/catalog/SpellCatalogReloadListener.java`
- Create: `src/test/java/com/reist/enemyspellcast/catalog/SpellDefinitionTest.java`
- Modify: `src/main/java/com/reist/enemyspellcast/event/CommonEvents.java`

**Interfaces:**
- Consumes: Iron `SpellRegistry`, `AbstractSpell`, `CastType`, config allow/deny lists, and progression rarity cap.
- Produces: `SpellCatalog.snapshot()`, `eligible(int traitRank)`, and resolved `ResolvedSpell(definition, spell, castLevel)` values.

- [ ] **Step 1: Write codec and eligibility tests**

```java
class SpellDefinitionTest {
    @Test void validEntryRoundTripsThroughCodec() {
        var json = JsonParser.parseString("""
                {"spell":"irons_spellbooks:firebolt","weight":10,"min_trait_rank":1,
                 "target":"enemy","behavior":"direct","min_range":2.0,"max_range":24.0,
                 "requires_line_of_sight":true}
                """);
        var parsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(ResourceLocation.parse("irons_spellbooks:firebolt"), parsed.spellId());
        assertEquals(TargetMode.ENEMY, parsed.targetMode());
        assertEquals(10, parsed.weight());
    }

    @Test void invalidWeightRankAndRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SpellDefinition(
                ResourceLocation.parse("irons_spellbooks:firebolt"), 0, 6,
                TargetMode.ENEMY, SpellBehaviorId.DIRECT, 20, 2, true));
    }
}
```

- [ ] **Step 2: Run tests to verify missing codec types**

Run: `./gradlew.bat test --tests "*.SpellDefinitionTest"`

Expected: FAIL because catalog types do not exist.

- [ ] **Step 3: Implement the stable JSON schema**

```java
public enum TargetMode implements StringRepresentable {
    ENEMY, SELF, ALLY;
    public static final Codec<TargetMode> CODEC = StringRepresentable.fromEnum(TargetMode::values);
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}

public enum SpellBehaviorId implements StringRepresentable {
    DIRECT, ALLY_SELF_CAST;
    public static final Codec<SpellBehaviorId> CODEC = StringRepresentable.fromEnum(SpellBehaviorId::values);
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}

public record SpellDefinition(ResourceLocation spellId, int weight, int minTraitRank,
                              TargetMode targetMode, SpellBehaviorId behavior,
                              double minRange, double maxRange,
                              boolean requiresLineOfSight) {
    public SpellDefinition {
        if (weight < 1) throw new IllegalArgumentException("weight must be positive");
        if (minTraitRank < 1 || minTraitRank > 5) throw new IllegalArgumentException("rank must be 1..5");
        if (!Double.isFinite(minRange) || !Double.isFinite(maxRange)
                || minRange < 0 || maxRange < minRange) throw new IllegalArgumentException("invalid range");
    }

    public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("spell").forGetter(SpellDefinition::spellId),
            Codec.INT.fieldOf("weight").forGetter(SpellDefinition::weight),
            Codec.INT.optionalFieldOf("min_trait_rank", 1).forGetter(SpellDefinition::minTraitRank),
            TargetMode.CODEC.fieldOf("target").forGetter(SpellDefinition::targetMode),
            SpellBehaviorId.CODEC.optionalFieldOf("behavior", SpellBehaviorId.DIRECT).forGetter(SpellDefinition::behavior),
            Codec.DOUBLE.optionalFieldOf("min_range", 0d).forGetter(SpellDefinition::minRange),
            Codec.DOUBLE.optionalFieldOf("max_range", 24d).forGetter(SpellDefinition::maxRange),
            Codec.BOOL.optionalFieldOf("requires_line_of_sight", true).forGetter(SpellDefinition::requiresLineOfSight)
    ).apply(i, SpellDefinition::new));
}

public record SpellPoolFile(List<SpellDefinition> spells) {
    public static final Codec<SpellPoolFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            SpellDefinition.CODEC.listOf().fieldOf("spells").forGetter(SpellPoolFile::spells)
    ).apply(i, SpellPoolFile::new));
}
```

- [ ] **Step 4: Resolve entries defensively and filter by actual cast-level rarity**

`SpellCatalog.build` must:

1. Apply config force-deny first.
2. Resolve `SpellRegistry.getSpell(id)` and reject `SpellRegistry.none()`.
3. Reject disabled spells.
4. Reject `CastType.CONTINUOUS` unless `allowContinuousSpells` is true; initial config is false.
5. Calculate level with `ProgressionRules.spellLevel(spell.getMaxLevel(), rank)`.
6. Retain only entries where `definition.minTraitRank() <= rank` and `spell.getRarity(level).getValue() <= ProgressionRules.rarityCap(rank).getValue()`.
7. Apply force-allow only to catalog entries already present; it bypasses rank rarity filtering but never bypasses missing/disabled/continuous safety.
8. Log each rejected unknown ID once per reload, not once per Mob.

Expose an immutable list:

```java
public record ResolvedSpell(SpellDefinition definition, AbstractSpell spell, int castLevel) {}

public List<ResolvedSpell> eligible(int traitRank) { /* immutable filtered copy */ }
```

- [ ] **Step 5: Implement `/reload` integration using the exact folder**

`SpellCatalogReloadListener` extends `SimpleJsonResourceReloadListener` with folder `"enemyspellcast/spell_pool"`. Parse every file with `SpellPoolFile.CODEC` and `JsonOps.INSTANCE`; publish a new immutable `SpellCatalog` only after all valid files are collected. Invalid files log their resource ID and error, while other files remain available.

Register on the NeoForge bus:

```java
@SubscribeEvent
public static void addReloadListeners(AddReloadListenerEvent event) {
    event.addListener(SpellCatalogReloadListener.INSTANCE);
}
```

- [ ] **Step 6: Test unknown, disabled, and continuous entries without crashing**

Add tests with a resolver seam:

```java
@Test void invalidEntriesAreSkippedWhileValidEntriesRemain() {
    var catalog = SpellCatalog.build(List.of(validFirebolt, missingSpell, continuousSpell),
            fakeResolver(Map.of(validFirebolt.spellId(), fakeLongSpell)), Settings.defaults());
    assertEquals(List.of(validFirebolt.spellId()), catalog.ids());
}
```

Run: `./gradlew.bat test --tests "*.SpellDefinitionTest"`

Expected: PASS; the invalid-entry test retains the valid entry.

- [ ] **Step 7: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/catalog src/main/java/com/reist/enemyspellcast/event src/test
git commit -m "feat: add reloadable spell catalog"
```

### Task 5: Persist Mana, Cooldowns, and Stable Random Loadouts

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/loadout/SpellCasterData.java`
- Create: `src/main/java/com/reist/enemyspellcast/loadout/SpellCasterDataStore.java`
- Create: `src/main/java/com/reist/enemyspellcast/loadout/RollSource.java`
- Create: `src/main/java/com/reist/enemyspellcast/loadout/WeightedPicker.java`
- Create: `src/main/java/com/reist/enemyspellcast/loadout/LoadoutService.java`
- Create: `src/test/java/com/reist/enemyspellcast/loadout/WeightedPickerTest.java`
- Create: `src/test/java/com/reist/enemyspellcast/loadout/LoadoutServiceTest.java`

**Interfaces:**
- Consumes: `SpellCatalog.eligible(rank)` and `ProgressionRules.slotCount(rank)`.
- Produces: versioned NBT data, `LoadoutService.reconcile`, `activeSpells`, mana mutation, per-spell cooldowns, and global cooldown.

- [ ] **Step 1: Write weighted-without-replacement tests**

```java
class WeightedPickerTest {
    @Test void picksWithoutDuplicatesAndHonorsRequestedCount() {
        var entries = List.of(weighted("a", 1), weighted("b", 1), weighted("c", 1));
        var picked = WeightedPicker.pick(entries, 3, WeightedEntry::weight,
                new SequenceRolls(.0, .0, .0));
        assertEquals(3, picked.size());
        assertEquals(3, new HashSet<>(picked).size());
    }

    @Test void countLargerThanPoolReturnsWholePool() {
        var entries = List.of(weighted("a", 1), weighted("b", 4));
        assertEquals(2, WeightedPicker.pick(entries, 8, WeightedEntry::weight, () -> .99).size());
    }
}
```

- [ ] **Step 2: Write loadout reconciliation tests before implementation**

```java
class LoadoutServiceTest {
    @Test void rankIncreaseKeepsExistingAndOnlyFillsMissingSlots() {
        var data = SpellCasterData.fresh(100).withLoadout(List.of(id("firebolt")));
        var result = service.reconcile(data, 5, catalogOf("firebolt", "icicle", "magic_missile"), () -> 0);
        assertEquals(id("firebolt"), result.loadout().getFirst());
        assertEquals(3, result.activeLoadout(5).size());
    }

    @Test void rankDecreaseHidesButDoesNotDeleteExtraSlots() {
        var data = SpellCasterData.fresh(100).withLoadout(ids("firebolt", "icicle", "magic_missile"));
        var result = service.reconcile(data, 1, catalogOf("firebolt", "icicle", "magic_missile"), () -> 0);
        assertEquals(3, result.loadout().size());
        assertEquals(List.of(id("firebolt")), result.activeLoadout(1));
    }

    @Test void missingSpellIsRemovedAndReplacedInPlace() {
        var data = SpellCasterData.fresh(100).withLoadout(ids("removed", "icicle"));
        var result = service.reconcile(data, 3, catalogOf("firebolt", "icicle"), () -> 0);
        assertEquals(ids("firebolt", "icicle"), result.loadout());
    }
}
```

- [ ] **Step 3: Run loadout tests and verify missing implementation**

Run: `./gradlew.bat test --tests "*.WeightedPickerTest" --tests "*.LoadoutServiceTest"`

Expected: FAIL on missing loadout classes.

- [ ] **Step 4: Implement versioned data with a single persistent NBT key**

```java
public record SpellCasterData(int version, List<ResourceLocation> loadout, double mana,
                              Map<ResourceLocation, Integer> cooldowns, int globalCooldown) {
    public static final int CURRENT_VERSION = 1;
    public static final String NBT_KEY = "enemyspellcast:spell_caster";

    public static final Codec<SpellCasterData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(SpellCasterData::version),
            ResourceLocation.CODEC.listOf().optionalFieldOf("loadout", List.of()).forGetter(SpellCasterData::loadout),
            Codec.DOUBLE.optionalFieldOf("mana", 0d).forGetter(SpellCasterData::mana),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("cooldowns", Map.of()).forGetter(SpellCasterData::cooldowns),
            Codec.INT.optionalFieldOf("global_cooldown", 0).forGetter(SpellCasterData::globalCooldown)
    ).apply(i, SpellCasterData::new));

    public static SpellCasterData fresh(double maxMana) {
        return new SpellCasterData(CURRENT_VERSION, List.of(), maxMana, Map.of(), 0);
    }

    public SpellCasterData withLoadout(List<ResourceLocation> ids) {
        return new SpellCasterData(version, List.copyOf(ids), mana, cooldowns, globalCooldown);
    }

    public List<ResourceLocation> activeLoadout(int rank) {
        return loadout.subList(0, Math.min(loadout.size(), ProgressionRules.slotCount(rank)));
    }

    public int cooldown(ResourceLocation spellId) {
        return Math.max(0, cooldowns.getOrDefault(spellId, 0));
    }
}
```

`SpellCasterDataStore.load(Mob,double)` parses `mob.getPersistentData().getCompound(NBT_KEY)` through `NbtOps.INSTANCE`. On any parse error it logs the Mob UUID once and returns `fresh(maxMana)`. `save` encodes a copied immutable value and writes only under `NBT_KEY`; it never clears other mods' persistent data.

- [ ] **Step 5: Implement deterministic weighted selection and reconciliation**

```java
@FunctionalInterface
public interface RollSource { double nextDouble(); }

public static <T> List<T> pick(List<T> source, int count,
                               ToIntFunction<T> weight, RollSource rolls) {
    List<T> remaining = new ArrayList<>(source);
    List<T> result = new ArrayList<>();
    while (!remaining.isEmpty() && result.size() < count) {
        long total = remaining.stream().mapToLong(x -> Math.max(1, weight.applyAsInt(x))).sum();
        double cursor = Math.clamp(rolls.nextDouble(), 0, Math.nextDown(1d)) * total;
        int selected = 0;
        for (int i = 0; i < remaining.size(); i++) {
            cursor -= Math.max(1, weight.applyAsInt(remaining.get(i)));
            if (cursor < 0) { selected = i; break; }
        }
        result.add(remaining.remove(selected));
    }
    return List.copyOf(result);
}
```

`LoadoutService.reconcile` must preserve valid IDs in their existing order, remove unavailable IDs, then append weighted picks excluding already retained IDs until the largest previously saved slot count or current required slot count is filled. `activeLoadout(rank)` returns only the first `slotCount(rank)` IDs.

- [ ] **Step 6: Add codec corruption and cooldown sanitization tests**

Test that negative cooldowns become zero, non-finite mana becomes zero, mana above the calculated maximum clamps to maximum, and malformed NBT returns a fresh data value without throwing.

Run: `./gradlew.bat test --tests "*.WeightedPickerTest" --tests "*.LoadoutServiceTest"`

Expected: PASS for stable ordering, hidden extra slots, replacement, and malformed data.

- [ ] **Step 7: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/loadout src/test/java/com/reist/enemyspellcast/loadout
git commit -m "feat: persist random spell loadouts"
```

### Task 6: Centralize Allegiance, Support Targeting, and Friendly-Fire Protection

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/allegiance/Allegiance.java`
- Create: `src/main/java/com/reist/enemyspellcast/allegiance/FactionView.java`
- Create: `src/main/java/com/reist/enemyspellcast/allegiance/AllegiancePolicy.java`
- Create: `src/main/java/com/reist/enemyspellcast/allegiance/AllegianceResolver.java`
- Create: `src/main/java/com/reist/enemyspellcast/allegiance/FriendlyFireEvents.java`
- Create: `src/test/java/com/reist/enemyspellcast/allegiance/AllegianceResolverTest.java`
- Modify: `src/main/java/com/reist/enemyspellcast/event/CommonEvents.java`

**Interfaces:**
- Consumes: L2 trait lookup, `OwnableEntity`, scoreboard teams, `Enemy`, and Iron `SpellDamageEvent`.
- Produces: `isHostileTarget(caster,target)`, `isSupportTarget(caster,target)`, and a damage cancellation guard based on the actual spell owner.

- [ ] **Step 1: Pin precedence rules in pure tests**

```java
class AllegianceResolverTest {
    @Test void explicitSameTeamWinsOverEnemyClassification() {
        var caster = view("mob-a", "red", null, true, "player");
        var target = view("mob-b", "red", null, true, "player");
        assertEquals(Allegiance.ALLY, AllegiancePolicy.resolve(caster, target));
    }

    @Test void sameOwnerMakesOwnablesAllies() {
        assertEquals(Allegiance.ALLY, AllegiancePolicy.resolve(
                view("wolf-a", null, "owner", false, null),
                view("wolf-b", null, "owner", false, null)));
    }

    @Test void hostileMobAndPlayerAreEnemiesWithoutTeamOverride() {
        assertEquals(Allegiance.ENEMY, AllegiancePolicy.resolve(
                view("zombie", null, null, true, "player"),
                view("player", null, null, false, null)));
    }
}
```

- [ ] **Step 2: Run the test and verify missing policy classes**

Run: `./gradlew.bat test --tests "*.AllegianceResolverTest"`

Expected: FAIL on missing allegiance types.

- [ ] **Step 3: Implement pure precedence, then adapt Minecraft entities**

```java
public enum Allegiance { SELF, ALLY, ENEMY, NEUTRAL }

public record FactionView(UUID id, String team, UUID owner, boolean hostile,
                          UUID currentTarget, boolean playerAligned) {}

public static Allegiance resolve(FactionView a, FactionView b) {
    if (a.id().equals(b.id())) return Allegiance.SELF;
    if (a.team() != null && a.team().equals(b.team())) return Allegiance.ALLY;
    if (a.owner() != null && a.owner().equals(b.owner())) return Allegiance.ALLY;
    if (b.id().equals(a.currentTarget()) || a.id().equals(b.currentTarget())) return Allegiance.ENEMY;
    if (a.hostile() && b.playerAligned() || b.hostile() && a.playerAligned()) return Allegiance.ENEMY;
    if (a.hostile() && b.hostile()) return Allegiance.ALLY;
    return Allegiance.NEUTRAL;
}
```

The test helper used above is exact and deterministic:

```java
private static FactionView view(String id, String team, String owner,
                                boolean hostile, String currentTarget) {
    return new FactionView(uuid(id), team, owner == null ? null : uuid(owner), hostile,
            currentTarget == null ? null : uuid(currentTarget), id.equals("player"));
}

private static UUID uuid(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
}
```

`AllegianceResolver` converts `LivingEntity` to `FactionView`; recursively unwrap owners only to a maximum depth of 4 and use a visited UUID set to prevent owner cycles. A force-allow caster does not alter allegiance. `spell_support_blacklist` excludes only support recipients.

- [ ] **Step 4: Cancel Iron spell damage caused by a trait caster against allies**

```java
@SubscribeEvent(priority = EventPriority.HIGHEST)
public static void onSpellDamage(SpellDamageEvent event) {
    if (!EnemySpellCastConfig.snapshot().friendlyFireProtection()) return;
    Entity causing = event.getSpellDamageSource().getEntity();
    LivingEntity caster = AllegianceResolver.resolveLivingOwner(causing);
    if (caster == null || L2HostilityBridge.traitRank(caster) == 0) return;
    if (!AllegianceResolver.isHostileTarget(caster, event.getEntity())) {
        event.setCanceled(true);
    }
}
```

This event occurs before Iron calls `LivingEntity.hurt`, so canceling also prevents Iron's post-hit fire/freeze/lifesteal effects. The curated pool must continue excluding spells that apply harmful effects without passing through `SpellDamageEvent`.

- [ ] **Step 5: Add owner-cycle and projectile-owner tests**

Add pure owner-chain tests where `A -> B -> A` returns safely, and an adapter test proving a projectile's causing owner is used instead of the projectile entity itself.

Run: `./gradlew.bat test --tests "*.AllegianceResolverTest"`

Expected: PASS and no recursion error.

- [ ] **Step 6: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/allegiance src/test/java/com/reist/enemyspellcast/allegiance
git commit -m "feat: enforce spell allegiance rules"
```

### Task 7: Implement the Iron Spell Lifecycle Controller

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/compat/iron/IronSpellBridge.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/CastSession.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/SpellBehavior.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/DirectSpellBehavior.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/AllySelfCastBehavior.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/SpellBehaviorRegistry.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/SpellCastController.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/CastRuntimeRegistry.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/SpellPowerService.java`
- Create: `src/main/java/com/reist/enemyspellcast/casting/CastingTelegraph.java`
- Create: `src/test/java/com/reist/enemyspellcast/casting/SpellCastControllerTest.java`

**Interfaces:**
- Consumes: resolved spell, selected target, `SpellCasterData`, scaling, Iron `MagicData`, and behavior registry.
- Produces: `tryStart`, `tick`, `interrupt`, `cancel`, `isCasting`, correct mana/CD accounting, and an extensible behavior seam.

- [ ] **Step 1: Write controller state-machine tests using a fake Iron bridge**

```java
class SpellCastControllerTest {
    @Test void successfulCastConsumesFullManaAndSetsScaledCooldown() {
        var iron = new FakeIronBridge(40, 100, 20);
        var controller = controllerWith(iron, dataWithMana(100), scaling(1, 1, .5));
        assertTrue(controller.tryStart(candidate()));
        controller.tick();
        controller.tick();
        assertEquals(60, controller.data().mana(), 1e-9);
        assertEquals(50, controller.data().cooldown(id("firebolt")));
        assertEquals(List.of("pre", "tick", "cast", "complete:false"), iron.calls());
    }

    @Test void interruptUsesPartialManaAndGlobalCooldownOnce() {
        var iron = new FakeIronBridge(40, 100, 20);
        var controller = controllerWith(iron, dataWithMana(100), scaling(1, 1, 1));
        controller.tryStart(candidate());
        assertTrue(controller.interrupt());
        assertFalse(controller.interrupt());
        assertEquals(90, controller.data().mana(), 1e-9);
        assertEquals(20, controller.data().globalCooldown());
        assertEquals(1, iron.calls().stream().filter("complete:true"::equals).count());
    }

    @Test void disappearingTargetCancelsWithoutSuccessfulCast() {
        var controller = controllerWithTargetThatDies();
        controller.tryStart(candidate());
        target.setAlive(false);
        controller.tick();
        assertFalse(controller.isCasting());
        assertFalse(controller.ironCalls().contains("cast"));
    }
}
```

- [ ] **Step 2: Run the controller tests and verify missing state machine**

Run: `./gradlew.bat test --tests "*.SpellCastControllerTest"`

Expected: FAIL because controller interfaces do not exist.

- [ ] **Step 3: Define the behavior seam and runtime session**

```java
public interface SpellBehavior {
    boolean canStart(Mob caster, LivingEntity target, ResolvedSpell spell, MagicData magicData);
    void prepare(Mob caster, LivingEntity target, ResolvedSpell spell, MagicData magicData);
    void cast(Mob caster, LivingEntity target, ResolvedSpell spell, MagicData magicData);
}

public record CastSession(ResolvedSpell spell, LivingEntity target, SpellBehavior behavior,
                          int manaCost, int initialDuration) {}
```

`DirectSpellBehavior.cast` invokes the spell with the original caster. `AllySelfCastBehavior` is allowed only for `TargetMode.ALLY`; it invokes the self-targeted spell effect with the selected ally as the effective entity while retaining the original caster's `MagicData` for lifecycle completion. It rejects spells whose `checkPreCastConditions` requires non-empty custom cast data after preparation.

`SpellBehaviorRegistry` is an immutable `EnumMap<SpellBehaviorId, SpellBehavior>` so future special cases can be added without modifying the controller.

- [ ] **Step 4: Wrap exact Iron calls in one bridge**

```java
public interface IronSpellBridge {
    MagicData magicData(LivingEntity entity);
    boolean checkPreCast(ResolvedSpell spell, LivingEntity caster, MagicData data);
    void initiate(ResolvedSpell spell, LivingEntity caster, MagicData data, int duration);
    void preCast(ResolvedSpell spell, LivingEntity caster, MagicData data);
    void castTick(ResolvedSpell spell, LivingEntity caster, MagicData data);
    void cast(ResolvedSpell spell, LivingEntity caster, MagicData data);
    void complete(ResolvedSpell spell, LivingEntity caster, MagicData data, boolean cancelled);
}
```

Production implementation uses:

```java
data.initiateCast(spell.spell(), spell.castLevel(), duration,
        CastSource.MOB, SpellSelectionManager.MAINHAND);
spell.spell().onServerPreCast(caster.level(), spell.castLevel(), caster, data);
spell.spell().onServerCastTick(caster.level(), spell.castLevel(), caster, data);
spell.spell().onCast(caster.level(), spell.castLevel(), caster, CastSource.MOB, data);
spell.spell().onServerCastComplete(caster.level(), spell.castLevel(), caster, data, cancelled);
```

`complete` is guarded by the controller so it executes exactly once for each session.

- [ ] **Step 5: Implement mana, cooldown, regen, cast speed, power, and cancellation**

Rules:

- Start fails if global CD, spell CD, mana, target, range, line of sight, behavior, or Iron precondition fails.
- Duration is `ceil(spell.getCastTime(level) / scaling.castSpeed())`; instant spells still get a minimum one-tick telegraph controlled by `minimumTelegraphTicks=1` in controller constants.
- Each tick decrements cooldowns, regenerates `baseManaRegenPerSecond * manaRegenMultiplier / 20`, clamps to max mana, faces the target, and calls Iron cast tick.
- Start and every fifth cast tick call `CastingTelegraph.emit`: play the spell's normal Iron start sound through `onServerPreCast`, then send a small `DustParticleOptions` ring using `spell.getTargetingColor()` from the server level. This supplies a visible generic telegraph without requiring a humanoid animation or custom packet.
- Success consumes full mana and sets `ceil(spell.getSpellCooldown() * cooldownMultiplier)`.
- Interrupt consumes `ceil(manaCost * interruptManaFraction)` and sets global CD.
- Target invalidation, Trait removal, death, dimension mismatch, and unload call canceled completion but do not charge full mana.
- `CastType.CONTINUOUS` is rejected until continuous lifecycle support is explicitly enabled and tested.

`SpellPowerService` maintains one transient modifier on `AttributeRegistry.SPELL_POWER` with ID `enemyspellcast:mob_level_spell_power`, amount `scaling.spellPower()-1`, and operation `ADD_MULTIPLIED_BASE`. Remove the previous ID before adding a changed modifier; do not stack modifiers each tick.

- [ ] **Step 6: Publish runtime controllers without persistent references**

`CastRuntimeRegistry` uses `WeakHashMap<Mob, SpellCastController>` guarded to the server thread. It provides `get`, `getOrCreate`, `remove`, and `interrupt`. It never keys by UUID alone, preventing a stale controller from attaching to a reloaded entity instance.

- [ ] **Step 7: Pass success, interruption, invalid-target, and exact-once cleanup tests**

Run: `./gradlew.bat test --tests "*.SpellCastControllerTest"`

Expected: PASS with the exact lifecycle call sequences asserted above.

- [ ] **Step 8: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/compat/iron src/main/java/com/reist/enemyspellcast/casting src/test/java/com/reist/enemyspellcast/casting
git commit -m "feat: control mob spell casting lifecycle"
```

### Task 8: Add Context-Aware AI, Goal Installation, and Damage Interruption

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/targeting/SpellTarget.java`
- Create: `src/main/java/com/reist/enemyspellcast/targeting/SpellScorer.java`
- Create: `src/main/java/com/reist/enemyspellcast/ai/SpellCasterGoal.java`
- Create: `src/main/java/com/reist/enemyspellcast/ai/SpellGoalInstaller.java`
- Create: `src/main/java/com/reist/enemyspellcast/event/CommonEvents.java`
- Modify: `src/main/java/com/reist/enemyspellcast/trait/SpellCasterTrait.java`
- Modify: `src/main/java/com/reist/enemyspellcast/EnemySpellCast.java`
- Modify: `src/test/java/com/reist/enemyspellcast/casting/SpellCastControllerTest.java`

**Interfaces:**
- Consumes: active loadout, catalog, controller, allegiance resolver, L2 rank/level, and config.
- Produces: one Goal per trait Mob, 10-tick default decisions, score+jitter selection, casting movement/look control, and actual-damage interruption.

- [ ] **Step 1: Add scorer tests for offense, healing urgency, cooldown, and jitter bounds**

```java
@Test void injuredAllyMakesHealScoreHigherThanIdleAttack() {
    var heal = scorer.score(allyHealCandidate(allyAtHealth(.20)), contextWithoutEnemy());
    var attack = scorer.score(attackCandidate(), contextWithoutEnemy());
    assertTrue(heal > attack);
}

@Test void jitterNeverExceedsConfiguredFraction() {
    double base = 10;
    assertEquals(8.5, scorer.withJitter(base, .15, () -> 0), 1e-9);
    assertEquals(11.5, scorer.withJitter(base, .15, () -> Math.nextDown(1d)), 1e-9);
}
```

- [ ] **Step 2: Implement candidate scoring and bounded searches**

```java
public record SpellTarget(ResolvedSpell spell, LivingEntity target, double score) {}
```

Scoring uses these normalized components:

- enemy spell: base `0.45`, line-of-sight `+0.20`, preferred-range proximity up to `+0.25`, target low health up to `+0.10`;
- ally heal: base `0.20`, missing-health fraction `*0.80`;
- ally buff: base `0.35`, reject if the target already has the spell's resulting effect;
- self defense: base `0.25`, own missing-health fraction `*0.60`;
- cooldown/mana/precondition failure: candidate omitted rather than scored zero.

Add multiplicative jitter in `[1-jitter, 1+jitter]`. Search only when the decision timer reaches zero, within `supportSearchRange`; do not scan every tick.

- [ ] **Step 3: Implement a Goal that only locks MOVE/LOOK while actually casting**

```java
public final class SpellCasterGoal extends Goal {
    public SpellCasterGoal(Mob mob, SpellCastController controller, SpellScorer scorer) {
        this.mob = mob;
        this.controller = controller;
        this.scorer = scorer;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (controller.isCasting()) return true;
        if (decisionCooldown-- > 0) return false;
        decisionCooldown = EnemySpellCastConfig.snapshot().decisionIntervalTicks();
        pending = scorer.choose(mob, controller).orElse(null);
        return pending != null && controller.tryStart(pending);
    }

    @Override public boolean canContinueToUse() { return controller.isCasting(); }

    @Override public void tick() {
        controller.tick();
        controller.currentTarget().ifPresent(target ->
                mob.getLookControl().setLookAt(target, 30, 30));
        mob.getNavigation().setSpeedModifier(EnemySpellCastConfig.snapshot().castingMoveSpeed());
    }

    @Override public void stop() { controller.cancelIfActive(); }
}
```

Priority is `2`, leaving high-priority float/panic controls available and preempting ordinary melee/ranged goals only during a cast.

- [ ] **Step 4: Make installation idempotent across init, load, and command-added Trait**

`SpellGoalInstaller` holds `WeakHashMap<Mob, SpellCasterGoal>`. `install(mob,rank)` returns the existing goal when present; otherwise it creates controller+goal and calls `mob.goalSelector.addGoal(2, goal)`.

Update `SpellCasterTrait`:

```java
@Override public void initialize(LivingEntity entity, int rank) { ensureGoal(entity, rank); }
@Override public void postInit(LivingEntity entity, int rank) { ensureGoal(entity, rank); }
@Override public void tick(LivingEntity entity, int rank) {
    if (!entity.level().isClientSide()) ensureGoal(entity, rank);
}

private static void ensureGoal(LivingEntity entity, int rank) {
    if (entity instanceof Mob mob && rank > 0) SpellGoalInstaller.install(mob, rank);
}
```

The per-tick fallback performs only a WeakHashMap lookup after installation; it does not rebuild loadouts or scan entities.

- [ ] **Step 5: Interrupt only from actual post-mitigation damage**

```java
@SubscribeEvent
public static void onDamageTaken(LivingDamageEvent.Post event) {
    LivingEntity entity = event.getEntity();
    if (!(entity instanceof Mob mob) || event.getNewDamage() <= 0) return;
    double threshold = mob.getMaxHealth() * EnemySpellCastConfig.snapshot().interruptDamageFraction();
    if (event.getNewDamage() >= threshold) CastRuntimeRegistry.interrupt(mob);
}

@SubscribeEvent
public static void onDeath(LivingDeathEvent event) {
    if (event.getEntity() instanceof Mob mob) CastRuntimeRegistry.remove(mob, true);
}
```

Because the threshold compares a single post-mitigation event, ordinary low-value poison/fire ticks do not repeatedly lock the caster.

- [ ] **Step 6: Add tests for duplicate install, Trait removal, and one-hit threshold**

Use an installer seam around `GoalSelector.addGoal` and assert two calls to `install` add one goal. Test that rank zero makes `canUse` false and cancels an active session. Feed 4.99% and 5.00% max-health damage to the event handler seam and assert only the latter interrupts.

Run: `./gradlew.bat test`

Expected: all unit tests PASS.

- [ ] **Step 7: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/ai src/main/java/com/reist/enemyspellcast/targeting src/main/java/com/reist/enemyspellcast/event src/main/java/com/reist/enemyspellcast/trait src/test
git commit -m "feat: add spell casting mob AI"
```

### Task 9: Ship the Broad Safe Pool and Reload/Config Overrides

**Files:**
- Create: `src/main/resources/data/enemyspellcast/enemyspellcast/spell_pool/core.json`
- Modify: `src/main/java/com/reist/enemyspellcast/catalog/SpellCatalog.java`
- Modify: `src/main/java/com/reist/enemyspellcast/loadout/LoadoutService.java`
- Modify: `src/test/java/com/reist/enemyspellcast/resources/PackResourcesTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: catalog schema, behaviors, target modes, and config override lists.
- Produces: an initial broad but controlled whitelist and documented addon format.

- [ ] **Step 1: Add a resource test that enforces pool safety and breadth**

```java
@Test void corePoolIsBroadAndContainsNoInitiallyUnsupportedSpell() throws Exception {
    SpellPoolFile pool = decode("/data/enemyspellcast/enemyspellcast/spell_pool/core.json");
    assertTrue(pool.spells().size() >= 24);
    Set<ResourceLocation> ids = pool.spells().stream().map(SpellDefinition::spellId).collect(toSet());
    assertFalse(ids.contains(id("raise_dead")));
    assertFalse(ids.contains(id("summon_vex")));
    assertFalse(ids.contains(id("earthquake")));
    assertFalse(ids.contains(id("portal")));
    assertFalse(ids.contains(id("telekinesis")));
    assertFalse(ids.contains(id("cloud_of_regeneration")));
}
```

- [ ] **Step 2: Create `core.json` with exact initial entries**

Use weight 10 unless noted. Offensive `target=enemy`, `behavior=direct` entries:

```text
firebolt, fireball, fire_arrow, magma_bomb,
magic_missile, magic_arrow, guiding_bolt, eldritch_blast,
icicle, snowball, lightning_bolt, lightning_lance,
acid_orb, poison_arrow, wither_skull, blood_slash,
fang_strike, spectral_hammer, sonic_boom, electrocute
```

Use `min_range=2`, `max_range=24`, line of sight true for projectiles; `sonic_boom` uses `max_range=16`; `blood_slash`, `fang_strike`, and `spectral_hammer` use `max_range=12`.

Support `target=ally`, `behavior=ally_self_cast`, `weight=6` entries:

```text
heal, greater_heal, shield, invisibility, haste, fortify, oakskin, blessing_of_life
```

Use `max_range=16`, line of sight true. Their actual Iron rarity at the computed cast level controls the earliest available Trait rank, so the JSON does not duplicate rarity metadata.

- [ ] **Step 3: Make config overrides deterministic**

Parse config IDs with `ResourceLocation.tryParse`. Invalid strings log once at config snapshot creation and are ignored. Deny always wins over allow. On `/reload`, each loaded Mob keeps valid saved IDs; `LoadoutService.reconcile` replaces only IDs absent from the new eligible pool.

- [ ] **Step 4: Document the additive datapack format**

In `README.md`, include a complete addon example:

```json
{
  "spells": [
    {
      "spell": "another_spell_mod:arcane_bolt",
      "weight": 10,
      "min_trait_rank": 2,
      "target": "enemy",
      "behavior": "direct",
      "min_range": 2.0,
      "max_range": 24.0,
      "requires_line_of_sight": true
    }
  ]
}
```

Document that direct compatibility is limited to spells safe for arbitrary `Mob` casters; continuous, terrain-changing, summon-heavy, player-control, and custom cast-data spells stay excluded until given a dedicated `SpellBehavior`.

- [ ] **Step 5: Run pool tests and full unit suite**

Run: `./gradlew.bat test`

Expected: PASS; pool has at least 24 entries and no unsupported IDs.

- [ ] **Step 6: Commit after Git is explicitly available**

```powershell
git add src/main/resources/data/enemyspellcast/enemyspellcast src/main/java/com/reist/enemyspellcast/catalog src/main/java/com/reist/enemyspellcast/loadout src/test README.md
git commit -m "feat: add curated enemy spell pool"
```

### Task 10: Add Cross-MOD GameTests

**Files:**
- Create: `src/main/java/com/reist/enemyspellcast/gametest/EnemySpellCastGameTests.java`
- Create: `src/main/resources/data/enemyspellcast/structure/empty.snbt`
- Modify: `build.gradle` only if the GameTest source set needs explicit inclusion.

**Interfaces:**
- Consumes: the complete runtime feature.
- Produces: automated proof for Trait/Goal integration, persistence, casting, interruption, and allegiance.

- [ ] **Step 1: Create a shared GameTest fixture that initializes an L2 zombie**

```java
@GameTestHolder(EnemySpellCast.MOD_ID)
public final class EnemySpellCastGameTests {
    private static Zombie spawnCaster(GameTestHelper helper, int mobLevel, int traitRank) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        MobTraitCap cap = LHMiscs.MOB.type().getOrCreate(zombie);
        cap.lv = mobLevel;
        cap.setTrait(ModTraits.SPELL_CASTER.get(), traitRank);
        cap.tick(zombie);
        return zombie;
    }
}
```

Use a minimal empty structure with a solid floor so line-of-sight and navigation are deterministic.

- [ ] **Step 2: Test rank slots and persistence round trip**

```java
@GameTest(template = "empty", timeoutTicks = 100)
public static void rankFiveGetsThreePersistentSpells(GameTestHelper helper) {
    Zombie zombie = spawnCaster(helper, 400, 5);
    helper.runAfterDelay(10, () -> {
        SpellCasterData before = SpellCasterDataStore.load(zombie, 220);
        helper.assertValueEqual(before.activeLoadout(5).size(), 3, "rank 5 slots");
        CompoundTag saved = new CompoundTag();
        zombie.saveWithoutId(saved);
        Zombie copy = EntityType.ZOMBIE.create(helper.getLevel());
        copy.load(saved);
        helper.assertValueEqual(SpellCasterDataStore.load(copy, 220).loadout(), before.loadout(), "loadout round trip");
        helper.succeed();
    });
}
```

- [ ] **Step 3: Test successful cast and interruption**

Use a deterministic test catalog containing `firebolt` only. Spawn a player target out of melee range, wait for cast completion, and assert mana falls by the spell cost and cooldown becomes positive. In a second test, hurt the caster once for exactly 5% max health during its telegraph and assert no projectile appears, partial mana is charged, and global cooldown is 20.

- [ ] **Step 4: Test normal AI fallback**

Give a rank-1 zombie zero mana, place a survival player two blocks away, and assert within 60 ticks that the zombie's navigation/attack behavior remains active while no cast session starts.

- [ ] **Step 5: Test hostile friendly-fire and ally support**

Place caster and ally zombie on the same scoreboard team. Fire a deterministic direct spell through the ally toward a player and assert ally health is unchanged. Damage the ally, force an `ally_self_cast` heal candidate, then assert ally health increases while a nearby player health does not.

- [ ] **Step 6: Test excluded entities and Trait removal**

Assert an excluded Iron pyromancer fails `SpellCasterTrait.allow`. For a zombie with an installed goal, set Trait rank to zero through `cap.setTrait(trait,0)`, tick the cap, and assert an active cast cancels and no new cast begins.

- [ ] **Step 7: Run the dedicated GameTest server**

Run: `./gradlew.bat runGameTestServer`

Expected: the server exits successfully with all Enemy Spell Cast tests passing and no data-pack, registry, or missing-structure errors.

- [ ] **Step 8: Commit after Git is explicitly available**

```powershell
git add src/main/java/com/reist/enemyspellcast/gametest src/main/resources/data/enemyspellcast/structure build.gradle
git commit -m "test: cover enemy spell casting integration"
```

### Task 11: Dedicated-Server Safety, Performance Check, and Release Verification

**Files:**
- Modify: `README.md`
- Create: `CHANGELOG.md`
- Create: `docs/manual-test-checklist.md`

**Interfaces:**
- Consumes: finished MOD and all automated tests.
- Produces: a verified distributable JAR and repeatable manual compatibility checklist.

- [ ] **Step 1: Add operator-facing configuration documentation**

Document every common config key with its default, unit, safe range, and restart/reload behavior. Document the three Entity Type tags, L2 `trait_data` override path, spell-pool path, and the rule that deny overrides allow.

- [ ] **Step 2: Write the exact manual matrix**

`docs/manual-test-checklist.md` must contain checkboxes for:

```text
- Vanilla zombie: rank 1 / level 100, one Common-or-lower spell
- Vanilla skeleton: rank 3 / level 240, two distinct spells
- Vanilla hostile group: no spell damage/debuff friendly fire
- Injured hostile ally: heal or buff lands; nearby player is not helped
- Rank 5 / level 400: three spells and maximum spell level
- 5% real damage: cast interrupted with partial mana and 20-tick lockout
- Save, unload chunk, restart server: identical loadout remains
- /reload after removing one pool entry: only removed spell is replaced
- Iron pyromancer and configured boss: no added Goal
- 50 nearby trait mobs: no repeated exception and no unbounded log spam
```

- [ ] **Step 3: Run all automated verification from a clean state**

Run:

```powershell
./gradlew.bat clean test runGameTestServer build
```

Expected: every command succeeds; `build/libs/enemyspellcast-0.1.0.jar` exists.

- [ ] **Step 4: Inspect the final JAR for accidental source/reference inclusion**

Run:

```powershell
jar tf build/libs/enemyspellcast-0.1.0.jar | Select-String -Pattern '^\.reference/|com/example/examplemod|examplemod'
```

Expected: no output.

Run:

```powershell
jar tf build/libs/enemyspellcast-0.1.0.jar | Select-String -Pattern 'spell_caster|spell_pool/core.json|neoforge.mods.toml'
```

Expected: trait classes/resources, `core.json`, and `META-INF/neoforge.mods.toml` are present.

- [ ] **Step 5: Launch a client and perform the manual matrix**

Run: `./gradlew.bat runClient`

Use `/summon`, L2 trait commands or the TraitSymbol item, and `/data get entity` to validate the checklist. Capture any incompatibility with the exact Mob ID, spell ID, rank, L2 level, and stack trace before changing the safe pool.

- [ ] **Step 6: Record the initial release notes**

`CHANGELOG.md` version `0.1.0` lists the `Spell Caster / 魔導` Trait, 1–3 persistent random spells, rank/rarity/level progression, L2 level scaling, config/data-pack/tag extension points, interruption, support targeting, and friendly-fire protection.

- [ ] **Step 7: Commit after Git is explicitly available**

```powershell
git add README.md CHANGELOG.md docs src build.gradle gradle.properties
git commit -m "docs: prepare Enemy Spell Cast 0.1.0"
```

## Execution Notes

- Read the design spec before Task 1 and keep this plan's checkboxes updated.
- Execute tasks in order because the interfaces deliberately build on one another.
- At the start of implementation, use `superpowers:test-driven-development` and follow every red/green cycle above.
- If a Gradle/API mismatch appears, use `superpowers:systematic-debugging`; compare first against `.reference/L2Hostility` branch `editor-1.21` and `.reference/Irons-Spells-n-Spellbooks` branch `1.21` rather than guessing method names.
- Before claiming completion, use `superpowers:verification-before-completion` and rerun Task 11 from a clean build.
- Because this directory is not currently a Git repository, do not claim commit history exists and do not initialize one without user direction.
