package com.reist.enemyspellcast.targeting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.catalog.SpellCatalog;
import com.reist.enemyspellcast.catalog.TargetMode;
import com.reist.enemyspellcast.casting.SpellCastController;
import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.loadout.RollSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SpellScorer {
    private final TargetFinder targetFinder;

    public SpellScorer() {
        this(new TargetFinder.Native());
    }

    SpellScorer(TargetFinder targetFinder) {
        this.targetFinder = targetFinder;
    }

    public Optional<SpellTarget> choose(Mob caster, SpellCastController controller, int traitRank,
                                        Settings settings, RollSource rolls) {
        SpellCatalog catalog = SpellCatalog.snapshot();
        SpellTarget best = null;
        List<LivingEntity> allies = null;
        for (var id : controller.data().activeLoadout(traitRank)) {
            ResolvedSpell spell = catalog.find(traitRank, id).orElse(null);
            if (spell == null || !controller.isAvailable(spell)) {
                continue;
            }
            if (spell.definition().targetMode() == TargetMode.ALLY && allies == null) {
                allies = targetFinder.findAllies(caster, settings.supportSearchRange());
            }
            LivingEntity target = selectTarget(caster, spell, allies);
            if (target == null) {
                continue;
            }
            double score = baseScore(caster, target, spell);
            score = withJitter(score, settings.randomScoreJitter(), rolls);
            if (score < settings.minimumCastScore()) {
                continue;
            }
            if (best == null || score > best.score()) {
                best = new SpellTarget(spell, target, score);
            }
        }
        return Optional.ofNullable(best);
    }

    public static double attackScore(boolean hasTarget, double rangeProximity, double healthFraction) {
        if (!hasTarget) {
            return 0.0;
        }
        return 0.45 + 0.25 * clamp01(rangeProximity) + 0.10 * (1.0 - clamp01(healthFraction));
    }

    public static double healScore(double healthFraction) {
        return 0.20 + 0.80 * (1.0 - clamp01(healthFraction));
    }

    public static double withJitter(double base, double fraction, RollSource rolls) {
        double safeFraction = Math.max(0.0, Double.isFinite(fraction) ? fraction : 0.0);
        double roll = rolls.nextDouble();
        if (!Double.isFinite(roll)) {
            roll = 0.5;
        }
        roll = Math.max(0.0, Math.min(Math.nextDown(1.0), roll));
        return base * (1.0 - safeFraction + 2.0 * safeFraction * roll);
    }

    private LivingEntity selectTarget(Mob caster, ResolvedSpell spell, List<LivingEntity> allies) {
        return switch (spell.definition().targetMode()) {
            case SELF -> caster;
            case ENEMY -> targetFinder.findEnemy(caster);
            case ALLY -> allies == null ? null : allies.stream()
                    .min(Comparator.comparingDouble(SpellScorer::healthFraction)).orElse(null);
        };
    }

    private static double baseScore(Mob caster, LivingEntity target, ResolvedSpell spell) {
        TargetMode mode = spell.definition().targetMode();
        if (mode == TargetMode.ALLY) {
            return healScore(healthFraction(target));
        }
        if (mode == TargetMode.SELF) {
            return 0.25 + 0.60 * (1.0 - healthFraction(caster));
        }
        double distance = caster.distanceTo(target);
        double minimum = spell.definition().minRange();
        double maximum = spell.definition().maxRange();
        double preferred = (minimum + maximum) * 0.5;
        double span = Math.max(1.0, maximum - minimum);
        double proximity = 1.0 - Math.min(1.0, Math.abs(distance - preferred) / span);
        double score = attackScore(true, proximity, healthFraction(target));
        if (!spell.definition().requiresLineOfSight() || caster.hasLineOfSight(target)) {
            score += 0.20;
        }
        return score;
    }

    private static double healthFraction(LivingEntity entity) {
        return entity.getMaxHealth() <= 0.0F ? 0.0 : clamp01(entity.getHealth() / entity.getMaxHealth());
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
