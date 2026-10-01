package com.reist.enemyspellcast.progression;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;

/** Pure progression math shared by the trait, AI, and tests. */
public final class ProgressionRules {
    public static final int MAX_TRAIT_RANK = 5;

    private ProgressionRules() {
    }

    public static Scaling forMobLevel(int mobLevel, Settings settings) {
        var safe = settings.sanitized();
        long levelsAboveBase = Math.max(0L, (long) mobLevel - safe.baseMobLevel());
        int tier = (int) Math.min(Integer.MAX_VALUE, levelsAboveBase / safe.levelStep());

        double spellPowerBonus = addPerTier(0.0, safe.spellPowerPerTier(), tier);
        double maxMana = addPerTier(safe.baseMaxMana(), safe.maxManaPerTier(), tier);
        double manaRegenMultiplier = addPerTier(1.0, safe.manaRegenPerTier(), tier);
        double castSpeed = Math.min(safe.castSpeedCap(), addPerTier(1.0, safe.castSpeedPerTier(), tier));
        double cooldownMultiplier = Math.max(safe.cooldownFloor(),
                subtractPerTier(1.0, safe.cooldownReductionPerTier(), tier));

        return new Scaling(tier, safe.baseSpellPower(), spellPowerBonus, maxMana,
                manaRegenMultiplier, castSpeed, cooldownMultiplier);
    }

    public static int slotCount(int traitRank) {
        return switch (clampRank(traitRank)) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }

    public static int spellLevel(int maximumSpellLevel, int traitRank) {
        int maximum = Math.max(1, maximumSpellLevel);
        int rank = clampRank(traitRank);
        long levelOffset = ((long) maximum - 1L) * (rank - 1L) / (MAX_TRAIT_RANK - 1L);
        return (int) Math.min(maximum, 1L + levelOffset);
    }

    public static SpellRarity rarityCap(int traitRank) {
        return switch (clampRank(traitRank)) {
            case 1 -> SpellRarity.COMMON;
            case 2 -> SpellRarity.UNCOMMON;
            case 3 -> SpellRarity.RARE;
            case 4 -> SpellRarity.EPIC;
            default -> SpellRarity.LEGENDARY;
        };
    }

    private static int clampRank(int traitRank) {
        return Math.max(1, Math.min(MAX_TRAIT_RANK, traitRank));
    }

    private static double addPerTier(double base, double perTier, int tier) {
        double result = Math.fma(perTier, tier, base);
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double subtractPerTier(double base, double perTier, int tier) {
        double result = Math.fma(-perTier, tier, base);
        return Double.isFinite(result) ? result : -Double.MAX_VALUE;
    }

    public record Scaling(
            int tier,
            double baseSpellPower,
            double spellPowerBonus,
            double maxMana,
            double manaRegenMultiplier,
            double castSpeed,
            double cooldownMultiplier
    ) {
        public double spellPower() {
            return baseSpellPower * (1.0 + spellPowerBonus);
        }
    }
}
