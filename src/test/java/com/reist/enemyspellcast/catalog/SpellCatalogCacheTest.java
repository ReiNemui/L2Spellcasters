package com.reist.enemyspellcast.catalog;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCatalogCacheTest {
    private static final ResourceLocation SPELL_ID = ResourceLocation.parse("test:cached_spell");

    @Test
    void eligibleListsAndIdLookupsArePrecomputedPerRank() {
        SpellCatalog catalog = catalog(Settings.defaults());

        assertSame(catalog.eligible(3), catalog.eligible(3));
        assertEquals(SPELL_ID, catalog.find(3, SPELL_ID).orElseThrow().definition().spellId());
        assertTrue(catalog.find(2, SPELL_ID).isEmpty());
    }

    @Test
    void publishingAssignsAMonotonicGeneration() {
        long before = SpellCatalog.snapshot().generation();

        SpellCatalog.publish(catalog(Settings.defaults()));
        long first = SpellCatalog.snapshot().generation();
        SpellCatalog.publish(catalog(Settings.defaults()));
        long second = SpellCatalog.snapshot().generation();

        assertTrue(first > before);
        assertTrue(second > first);
    }

    @Test
    void unavailableIronRarityConfigDefersIndexingInsteadOfCrashingReload() {
        SpellDefinition definition = new SpellDefinition(SPELL_ID, 10, 1, TargetMode.ENEMY,
                SpellBehaviorId.DIRECT, 0, 24, true, SpellSafetyCategory.STANDARD);

        SpellCatalog catalog = SpellCatalog.build(List.of(definition), DeferredRaritySpell::new,
                Settings.defaults(), ignored -> { });

        assertEquals(List.of(SPELL_ID), catalog.ids());
        assertTrue(catalog.eligible(5).isEmpty());
    }

    @Test
    void unrelatedIllegalStateIsNotMistakenForAnEarlyConfigLoad() {
        SpellDefinition definition = new SpellDefinition(SPELL_ID, 10, 1, TargetMode.ENEMY,
                SpellBehaviorId.DIRECT, 0, 24, true, SpellSafetyCategory.STANDARD);

        assertThrows(IllegalStateException.class, () -> SpellCatalog.build(List.of(definition),
                BrokenRaritySpell::new, Settings.defaults(), ignored -> { }));
    }

    private static SpellCatalog catalog(Settings settings) {
        SpellDefinition definition = new SpellDefinition(SPELL_ID, 10, 3, TargetMode.ENEMY,
                SpellBehaviorId.DIRECT, 0, 24, true, SpellSafetyCategory.STANDARD);
        return SpellCatalog.build(List.of(definition), FakeSpell::new, settings, ignored -> { });
    }

    private static class FakeSpell extends AbstractSpell {
        private final ResourceLocation id;

        private FakeSpell(ResourceLocation id) { this.id = id; }
        @Override public ResourceLocation getSpellResource() { return id; }
        @Override public DefaultConfig getDefaultConfig() { return null; }
        @Override public CastType getCastType() { return CastType.LONG; }
        @Override public int getMaxLevel() { return 5; }
        @Override public SpellRarity getRarity(int level) { return SpellRarity.RARE; }
        @Override public boolean isEnabled() { return true; }
    }

    private static final class DeferredRaritySpell extends FakeSpell {
        private DeferredRaritySpell(ResourceLocation id) { super(id); }
        @Override public SpellRarity getRarity(int level) {
            throw new IllegalStateException("rarity config not loaded");
        }
    }

    private static final class BrokenRaritySpell extends FakeSpell {
        private BrokenRaritySpell(ResourceLocation id) { super(id); }
        @Override public SpellRarity getRarity(int level) {
            throw new IllegalStateException("broken rarity implementation");
        }
    }
}
