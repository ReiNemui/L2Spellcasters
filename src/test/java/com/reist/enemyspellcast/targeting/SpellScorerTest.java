package com.reist.enemyspellcast.targeting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellScorerTest {
    @Test
    void badlyInjuredAllyMakesHealScoreHigherThanIdleAttack() {
        double heal = SpellScorer.healScore(0.20);
        double attackWithoutTarget = SpellScorer.attackScore(false, 0.0, 1.0);
        assertTrue(heal > attackWithoutTarget);
    }

    @Test
    void jitterNeverExceedsConfiguredFraction() {
        double base = 10.0;
        assertEquals(8.5, SpellScorer.withJitter(base, 0.15, () -> 0.0), 1e-9);
        assertEquals(11.5, SpellScorer.withJitter(base, 0.15,
                () -> Math.nextDown(1.0)), 1e-9);
    }
}
