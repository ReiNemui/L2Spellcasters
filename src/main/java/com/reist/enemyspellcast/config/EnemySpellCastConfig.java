package com.reist.enemyspellcast.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Common configuration for spell-casting hostile mobs.
 *
 * <p>Callers should consume {@link #snapshot()} rather than reading individual
 * Forge config values. This keeps validation in one place and makes the rules
 * straightforward to exercise without starting Minecraft.</p>
 */
public final class EnemySpellCastConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue BASE_MOB_LEVEL;
    private static final ModConfigSpec.IntValue LEVEL_STEP;
    private static final ModConfigSpec.DoubleValue BASE_SPELL_POWER;
    private static final ModConfigSpec.DoubleValue SPELL_POWER_PER_TIER;
    private static final ModConfigSpec.DoubleValue BASE_MAX_MANA;
    private static final ModConfigSpec.DoubleValue MAX_MANA_PER_TIER;
    private static final ModConfigSpec.DoubleValue BASE_MANA_REGEN_PER_SECOND;
    private static final ModConfigSpec.DoubleValue MANA_REGEN_PER_TIER;
    private static final ModConfigSpec.DoubleValue CAST_SPEED_PER_TIER;
    private static final ModConfigSpec.DoubleValue CAST_SPEED_CAP;
    private static final ModConfigSpec.DoubleValue COOLDOWN_REDUCTION_PER_TIER;
    private static final ModConfigSpec.DoubleValue COOLDOWN_FLOOR;
    private static final ModConfigSpec.IntValue DECISION_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue RANDOM_SCORE_JITTER;
    private static final ModConfigSpec.DoubleValue INTERRUPT_DAMAGE_FRACTION;
    private static final ModConfigSpec.DoubleValue INTERRUPT_MANA_FRACTION;
    private static final ModConfigSpec.IntValue INTERRUPT_GLOBAL_COOLDOWN_TICKS;
    private static final ModConfigSpec.BooleanValue FRIENDLY_FIRE_PROTECTION;
    private static final ModConfigSpec.DoubleValue CASTING_MOVE_SPEED;
    private static final ModConfigSpec.DoubleValue MINIMUM_CAST_SCORE;
    private static final ModConfigSpec.DoubleValue SUPPORT_SEARCH_RANGE;
    private static final ModConfigSpec.BooleanValue ALLOW_CONTINUOUS_SPELLS;
    private static final ModConfigSpec.BooleanValue EXCLUDE_SUMMONING_SPELLS;
    private static final ModConfigSpec.BooleanValue EXCLUDE_TERRAIN_CHANGING_SPELLS;
    private static final ModConfigSpec.BooleanValue EXCLUDE_PORTAL_SPELLS;
    private static final ModConfigSpec.EnumValue<SpellSelectionMode> SELECTION_MODE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_SPELL_IDS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_ALLOW_SPELL_IDS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_DENY_SPELL_IDS;
    private static volatile ConfigState state;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.push("general");
        ENABLED = builder.comment("Master switch for the Spell Caster trait behavior.")
                .define("enabled", true);
        FRIENDLY_FIRE_PROTECTION = builder.comment("Prevent damage and debuffs from affecting hostile allies.")
                .define("friendlyFireProtection", true);
        ALLOW_CONTINUOUS_SPELLS = builder.comment("Allow continuous/channelled spells in the automatic safe pool.")
                .define("allowContinuousSpells", false);
        builder.pop();

        builder.push("levelScaling");
        BASE_MOB_LEVEL = builder.comment("L2 Hostility level at which spell scaling starts.")
                .defineInRange("baseMobLevel", 100, 0, Integer.MAX_VALUE);
        LEVEL_STEP = builder.comment("Mob levels per scaling tier.")
                .defineInRange("levelStep", 50, 1, Integer.MAX_VALUE);
        BASE_SPELL_POWER = builder.comment("Enemy spell-power multiplier at the base mob level (1.0 = normal damage; 0.70 = 70%).")
                .defineInRange("baseSpellPower", 1.0, 0.01, 1000.0);
        SPELL_POWER_PER_TIER = builder.comment("Additive spell-power multiplier gained per tier (0.10 = +10%).")
                .defineInRange("spellPowerPerTier", 0.10, 0.0, 1000.0);
        BASE_MAX_MANA = builder.defineInRange("baseMaxMana", 100.0, 1.0, Double.MAX_VALUE);
        MAX_MANA_PER_TIER = builder.defineInRange("maxManaPerTier", 20.0, 0.0, Double.MAX_VALUE);
        BASE_MANA_REGEN_PER_SECOND = builder.defineInRange("baseManaRegenPerSecond", 2.0, 0.0, Double.MAX_VALUE);
        MANA_REGEN_PER_TIER = builder.comment("Additive mana-regeneration multiplier gained per tier.")
                .defineInRange("manaRegenPerTier", 0.05, 0.0, 1000.0);
        CAST_SPEED_PER_TIER = builder.comment("Additive cast-speed multiplier gained per tier.")
                .defineInRange("castSpeedPerTier", 0.05, 0.0, 1000.0);
        CAST_SPEED_CAP = builder.comment("Maximum cast-speed multiplier.")
                .defineInRange("castSpeedCap", 3.0, 1.0, 1000.0);
        COOLDOWN_REDUCTION_PER_TIER = builder.comment("Cooldown multiplier removed per tier.")
                .defineInRange("cooldownReductionPerTier", 0.05, 0.0, 1.0);
        COOLDOWN_FLOOR = builder.comment("Minimum cooldown multiplier (0.25 = 25% of normal cooldown).")
                .defineInRange("cooldownFloor", 0.25, 0.01, 1.0);
        builder.pop();

        builder.push("behavior");
        DECISION_INTERVAL_TICKS = builder.defineInRange("decisionIntervalTicks", 10, 1, 1200);
        RANDOM_SCORE_JITTER = builder.comment("Random variation applied to spell utility scores.")
                .defineInRange("randomScoreJitter", 0.15, 0.0, 10.0);
        INTERRUPT_DAMAGE_FRACTION = builder.comment("A single post-mitigation hit at this fraction of max health interrupts casting.")
                .defineInRange("interruptDamageFraction", 0.05, 0.0, 1.0);
        INTERRUPT_MANA_FRACTION = builder.comment("Fraction of mana cost consumed when casting is interrupted.")
                .defineInRange("interruptManaFraction", 0.25, 0.0, 1.0);
        INTERRUPT_GLOBAL_COOLDOWN_TICKS = builder.defineInRange("interruptGlobalCooldownTicks", 20, 0, 12000);
        CASTING_MOVE_SPEED = builder.comment("Navigation speed while casting, relative to normal movement.")
                .defineInRange("castingMoveSpeed", 0.25, 0.0, 1.0);
        MINIMUM_CAST_SCORE = builder.comment("Minimum utility score required before a mob begins casting.")
                .defineInRange("minimumCastScore", 0.20, 0.0, 1000.0);
        SUPPORT_SEARCH_RANGE = builder.comment("Maximum range used when looking for hostile allies to heal or buff.")
                .defineInRange("supportSearchRange", 16.0, 0.0, 256.0);
        EXCLUDE_SUMMONING_SPELLS = builder.comment("Exclude summoning spells, including explicitly allowed ids.")
                .define("excludeSummoningSpells", true);
        EXCLUDE_TERRAIN_CHANGING_SPELLS = builder.comment("Exclude spells that change blocks or terrain.")
                .define("excludeTerrainChangingSpells", true);
        EXCLUDE_PORTAL_SPELLS = builder.comment("Exclude portal, recall, and dimension-travel spells.")
                .define("excludePortalSpells", true);
        builder.pop();

        builder.push("spellOverrides");
        SELECTION_MODE = builder.comment("DEFAULT_POOL uses datapack defaults; ALLOWLIST_ONLY uses only allowedSpellIds.")
                .defineEnum("selectionMode", SpellSelectionMode.DEFAULT_POOL);
        ALLOWED_SPELL_IDS = builder.comment("Exact candidate ids used by ALLOWLIST_ONLY mode.")
                .defineListAllowEmpty("allowedSpellIds", ArrayList::new, EnemySpellCastConfig::isString);
        FORCE_ALLOW_SPELL_IDS = builder.comment("DEFAULT_POOL-only explicit additions; these ids bypass rank and rarity progression. Risky categories still require their exclusion switch to be false.")
                .defineListAllowEmpty("forceAllowSpellIds", ArrayList::new, EnemySpellCastConfig::isString);
        FORCE_DENY_SPELL_IDS = builder.comment("Spell ids forcibly excluded from the candidate pool; deny wins over allow.")
                .defineListAllowEmpty("forceDenySpellIds", ArrayList::new, EnemySpellCastConfig::isString);
        builder.pop();

        SPEC = builder.build();
        state = new ConfigState(Settings.defaults(), 0L);
    }

    private EnemySpellCastConfig() {
    }

    private static boolean isString(Object value) {
        return value instanceof String;
    }

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

    private static Settings readFromSpec() {
        return new Settings(
                ENABLED.get(), BASE_MOB_LEVEL.get(), LEVEL_STEP.get(), BASE_SPELL_POWER.get(),
                SPELL_POWER_PER_TIER.get(),
                BASE_MAX_MANA.get(), MAX_MANA_PER_TIER.get(), BASE_MANA_REGEN_PER_SECOND.get(),
                MANA_REGEN_PER_TIER.get(), CAST_SPEED_PER_TIER.get(), CAST_SPEED_CAP.get(),
                COOLDOWN_REDUCTION_PER_TIER.get(), COOLDOWN_FLOOR.get(), DECISION_INTERVAL_TICKS.get(),
                RANDOM_SCORE_JITTER.get(), INTERRUPT_DAMAGE_FRACTION.get(), INTERRUPT_MANA_FRACTION.get(),
                INTERRUPT_GLOBAL_COOLDOWN_TICKS.get(), FRIENDLY_FIRE_PROTECTION.get(), CASTING_MOVE_SPEED.get(),
                MINIMUM_CAST_SCORE.get(), SUPPORT_SEARCH_RANGE.get(), ALLOW_CONTINUOUS_SPELLS.get(),
                copyStrings(FORCE_ALLOW_SPELL_IDS.get()), copyStrings(FORCE_DENY_SPELL_IDS.get()),
                SELECTION_MODE.get(), copyStrings(ALLOWED_SPELL_IDS.get()), EXCLUDE_SUMMONING_SPELLS.get(),
                EXCLUDE_TERRAIN_CHANGING_SPELLS.get(), EXCLUDE_PORTAL_SPELLS.get()
        ).sanitized();
    }

    private static List<String> copyStrings(List<? extends String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new LinkedHashSet<String>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    public record Settings(
            boolean enabled,
            int baseMobLevel,
            int levelStep,
            double baseSpellPower,
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
            List<String> forceAllowSpellIds,
            List<String> forceDenySpellIds,
            SpellSelectionMode selectionMode,
            List<String> allowedSpellIds,
            boolean excludeSummoningSpells,
            boolean excludeTerrainChangingSpells,
            boolean excludePortalSpells
    ) {
        public static Settings defaults() {
            return new Settings(true, 100, 50, 1.0, 0.10, 100.0, 20.0, 2.0, 0.05,
                    0.05, 3.0, 0.05, 0.25, 10, 0.15, 0.05, 0.25, 20,
                    true, 0.25, 0.20, 16.0, false, List.of(), List.of(),
                    SpellSelectionMode.DEFAULT_POOL, List.of(), true, true, true);
        }

        public Settings sanitized() {
            return new Settings(
                    enabled,
                    Math.max(0, baseMobLevel),
                    Math.max(1, levelStep),
                    atLeast(baseSpellPower, 0.01),
                    nonNegative(spellPowerPerTier),
                    atLeast(baseMaxMana, 1.0),
                    nonNegative(maxManaPerTier),
                    nonNegative(baseManaRegenPerSecond),
                    nonNegative(manaRegenPerTier),
                    nonNegative(castSpeedPerTier),
                    atLeast(castSpeedCap, 1.0),
                    nonNegative(cooldownReductionPerTier),
                    clamp(finiteOr(cooldownFloor, 0.25), 0.01, 1.0),
                    Math.max(1, decisionIntervalTicks),
                    nonNegative(randomScoreJitter),
                    clamp(finiteOr(interruptDamageFraction, 0.05), 0.0, 1.0),
                    clamp(finiteOr(interruptManaFraction, 0.25), 0.0, 1.0),
                    Math.max(0, interruptGlobalCooldownTicks),
                    friendlyFireProtection,
                    clamp(finiteOr(castingMoveSpeed, 0.25), 0.0, 1.0),
                    nonNegative(minimumCastScore),
                    nonNegative(supportSearchRange),
                    allowContinuousSpells,
                    copyStrings(forceAllowSpellIds),
                    copyStrings(forceDenySpellIds),
                    selectionMode == null ? SpellSelectionMode.DEFAULT_POOL : selectionMode,
                    copyStrings(allowedSpellIds),
                    excludeSummoningSpells,
                    excludeTerrainChangingSpells,
                    excludePortalSpells);
        }

        public Settings withEnabled(boolean value) {
            return new Settings(value, baseMobLevel, levelStep, baseSpellPower, spellPowerPerTier, baseMaxMana,
                    maxManaPerTier, baseManaRegenPerSecond, manaRegenPerTier, castSpeedPerTier,
                    castSpeedCap, cooldownReductionPerTier, cooldownFloor, decisionIntervalTicks,
                    randomScoreJitter, interruptDamageFraction, interruptManaFraction,
                    interruptGlobalCooldownTicks, friendlyFireProtection, castingMoveSpeed,
                    minimumCastScore, supportSearchRange, allowContinuousSpells,
                    forceAllowSpellIds, forceDenySpellIds, selectionMode, allowedSpellIds,
                    excludeSummoningSpells, excludeTerrainChangingSpells, excludePortalSpells);
        }

        public Settings withSpellOverrides(SpellSelectionMode mode, List<String> allowed,
                                           List<String> forced, List<String> denied) {
            return new Settings(enabled, baseMobLevel, levelStep, baseSpellPower, spellPowerPerTier, baseMaxMana,
                    maxManaPerTier, baseManaRegenPerSecond, manaRegenPerTier, castSpeedPerTier,
                    castSpeedCap, cooldownReductionPerTier, cooldownFloor, decisionIntervalTicks,
                    randomScoreJitter, interruptDamageFraction, interruptManaFraction,
                    interruptGlobalCooldownTicks, friendlyFireProtection, castingMoveSpeed,
                    minimumCastScore, supportSearchRange, allowContinuousSpells,
                    forced, denied, mode, allowed, excludeSummoningSpells,
                    excludeTerrainChangingSpells, excludePortalSpells);
        }

        public Settings withCategoryExclusions(boolean summoning, boolean terrainChanging,
                                               boolean portal) {
            return new Settings(enabled, baseMobLevel, levelStep, baseSpellPower, spellPowerPerTier, baseMaxMana,
                    maxManaPerTier, baseManaRegenPerSecond, manaRegenPerTier, castSpeedPerTier,
                    castSpeedCap, cooldownReductionPerTier, cooldownFloor, decisionIntervalTicks,
                    randomScoreJitter, interruptDamageFraction, interruptManaFraction,
                    interruptGlobalCooldownTicks, friendlyFireProtection, castingMoveSpeed,
                    minimumCastScore, supportSearchRange, allowContinuousSpells,
                    forceAllowSpellIds, forceDenySpellIds, selectionMode, allowedSpellIds,
                    summoning, terrainChanging, portal);
        }

        private static double nonNegative(double value) {
            return Math.max(0.0, finiteOr(value, 0.0));
        }

        private static double atLeast(double value, double minimum) {
            return Math.max(minimum, finiteOr(value, minimum));
        }

        private static double finiteOr(double value, double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }

    private record ConfigState(Settings settings, long generation) {
    }
}
