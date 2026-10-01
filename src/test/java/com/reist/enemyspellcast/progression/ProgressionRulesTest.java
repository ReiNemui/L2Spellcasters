package com.reist.enemyspellcast.progression;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.config.SpellSelectionMode;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionRulesTest {
    private static final Settings DEFAULTS = Settings.defaults();

    @Test
    void level100UsesBaseStats() {
        var value = ProgressionRules.forMobLevel(100, DEFAULTS);

        assertAll(
                () -> assertEquals(0, value.tier()),
                () -> assertEquals(1.0, value.spellPower(), 1e-9),
                () -> assertEquals(100.0, value.maxMana(), 1e-9),
                () -> assertEquals(1.0, value.manaRegenMultiplier(), 1e-9),
                () -> assertEquals(1.0, value.castSpeed(), 1e-9),
                () -> assertEquals(1.0, value.cooldownMultiplier(), 1e-9));
    }

    @Test
    void level400KeepsTheOriginalCombatCurve() {
        var value = ProgressionRules.forMobLevel(400, DEFAULTS);

        assertAll(
                () -> assertEquals(6, value.tier()),
                () -> assertEquals(1.60, value.spellPower(), 1e-9),
                () -> assertEquals(1.30, value.castSpeed(), 1e-9),
                () -> assertEquals(0.70, value.cooldownMultiplier(), 1e-9));
    }

    @Test
    void configuredBaseSpellPowerScalesTheOriginalGrowthCurve() {
        var defaults = Settings.defaults();
        var reduced = new Settings(defaults.enabled(), defaults.baseMobLevel(), defaults.levelStep(),
                0.70, defaults.spellPowerPerTier(), defaults.baseMaxMana(), defaults.maxManaPerTier(),
                defaults.baseManaRegenPerSecond(), defaults.manaRegenPerTier(),
                defaults.castSpeedPerTier(), defaults.castSpeedCap(),
                defaults.cooldownReductionPerTier(), defaults.cooldownFloor(),
                defaults.decisionIntervalTicks(), defaults.randomScoreJitter(),
                defaults.interruptDamageFraction(), defaults.interruptManaFraction(),
                defaults.interruptGlobalCooldownTicks(), defaults.friendlyFireProtection(),
                defaults.castingMoveSpeed(), defaults.minimumCastScore(), defaults.supportSearchRange(),
                defaults.allowContinuousSpells(), defaults.forceAllowSpellIds(),
                defaults.forceDenySpellIds(), defaults.selectionMode(), defaults.allowedSpellIds(),
                defaults.excludeSummoningSpells(), defaults.excludeTerrainChangingSpells(),
                defaults.excludePortalSpells());

        assertAll(
                () -> assertEquals(0.70, ProgressionRules.forMobLevel(100, reduced).spellPower(), 1e-9),
                () -> assertEquals(1.12, ProgressionRules.forMobLevel(400, reduced).spellPower(), 1e-9));
    }

    @Test
    void scalingChangesOnlyAtConfiguredSteps() {
        assertEquals(0, ProgressionRules.forMobLevel(149, DEFAULTS).tier());
        assertEquals(1, ProgressionRules.forMobLevel(150, DEFAULTS).tier());
        assertEquals(2, ProgressionRules.forMobLevel(200, DEFAULTS).tier());
    }

    @Test
    void highLevelScalingCapsOnlySpeedAndCooldown() {
        var value = ProgressionRules.forMobLevel(1100, DEFAULTS);

        assertAll(
                () -> assertEquals(20, value.tier()),
                () -> assertEquals(3.0, value.spellPower(), 1e-9),
                () -> assertEquals(500.0, value.maxMana(), 1e-9),
                () -> assertEquals(2.0, value.manaRegenMultiplier(), 1e-9),
                () -> assertEquals(2.0, value.castSpeed(), 1e-9),
                () -> assertEquals(0.25, value.cooldownMultiplier(), 1e-9));
    }

    @Test
    void rankControlsSlotsSpellLevelAndRarity() {
        assertArrayEquals(new int[]{1, 1, 2, 2, 3},
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .map(ProgressionRules::slotCount).toArray());
        assertEquals(1, ProgressionRules.spellLevel(10, 1));
        assertEquals(3, ProgressionRules.spellLevel(10, 2));
        assertEquals(5, ProgressionRules.spellLevel(10, 3));
        assertEquals(7, ProgressionRules.spellLevel(10, 4));
        assertEquals(10, ProgressionRules.spellLevel(10, 5));
        assertEquals(SpellRarity.RARE, ProgressionRules.rarityCap(3));
    }

    @Test
    void invalidSettingsAreSanitizedWithoutOverflow() {
        var invalid = new Settings(true, 100, 0, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0, -1, -1, -1, -1, true, -1, -1, -1,
                true, List.of(), List.of(), SpellSelectionMode.DEFAULT_POOL, List.of(),
                true, true, true);
        var safe = invalid.sanitized();

        assertAll(
                () -> assertTrue(safe.levelStep() >= 1),
                () -> assertTrue(safe.castSpeedCap() >= 1),
                () -> assertTrue(safe.cooldownFloor() > 0 && safe.cooldownFloor() <= 1),
                () -> assertDoesNotThrow(() ->
                        ProgressionRules.forMobLevel(Integer.MAX_VALUE, safe)));
    }
}
