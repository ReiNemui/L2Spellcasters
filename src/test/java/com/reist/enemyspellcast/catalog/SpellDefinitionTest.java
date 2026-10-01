package com.reist.enemyspellcast.catalog;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellDefinitionTest {
    private static final ResourceLocation FIREBOLT = ResourceLocation.parse("irons_spellbooks:firebolt");

    @Test
    void validEntryRoundTripsThroughCodec() {
        var json = JsonParser.parseString("""
                {"spell":"irons_spellbooks:firebolt","weight":10,"min_trait_rank":1,
                 "target":"enemy","behavior":"direct","min_range":2.0,"max_range":24.0,
                 "requires_line_of_sight":true}
                """);

        var parsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(FIREBOLT, parsed.spellId());
        assertEquals(TargetMode.ENEMY, parsed.targetMode());
        assertEquals(SpellBehaviorId.DIRECT, parsed.behavior());
        assertEquals(SpellSafetyCategory.STANDARD, parsed.safetyCategory());
        assertEquals(10, parsed.weight());
    }

    @Test
    void invalidWeightRankAndRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SpellDefinition(
                FIREBOLT, 0, 6, TargetMode.ENEMY, SpellBehaviorId.DIRECT,
                20, 2, true, SpellSafetyCategory.STANDARD));
    }

    @Test
    void unknownDisabledAndContinuousEntriesAreSkippedWithoutDroppingValidEntries() {
        var valid = definition(FIREBOLT, 1);
        var missing = definition(ResourceLocation.parse("test:missing"), 1);
        var disabled = definition(ResourceLocation.parse("test:disabled"), 1);
        var continuous = definition(ResourceLocation.parse("test:continuous"), 1);
        Map<ResourceLocation, AbstractSpell> spells = Map.of(
                valid.spellId(), new FakeSpell(valid.spellId(), CastType.LONG, true, SpellRarity.COMMON),
                disabled.spellId(), new FakeSpell(disabled.spellId(), CastType.LONG, false, SpellRarity.COMMON),
                continuous.spellId(), new FakeSpell(continuous.spellId(), CastType.CONTINUOUS, true, SpellRarity.COMMON));

        var catalog = SpellCatalog.build(List.of(valid, missing, disabled, continuous),
                spells::get, Settings.defaults());

        assertEquals(List.of(FIREBOLT), catalog.ids());
    }

    @Test
    void eligibilityUsesTraitRankRarityAndComputedCastLevel() {
        var rare = definition(ResourceLocation.parse("test:rare_spell"), 3);
        var catalog = SpellCatalog.build(List.of(rare),
                id -> new FakeSpell(id, CastType.LONG, true, SpellRarity.RARE), Settings.defaults());

        assertTrue(catalog.eligible(2).isEmpty());
        var resolved = catalog.eligible(3);
        assertEquals(1, resolved.size());
        assertEquals(5, resolved.getFirst().castLevel());
        assertEquals(rare, resolved.getFirst().definition());
    }

    private static SpellDefinition definition(ResourceLocation id, int rank) {
        return new SpellDefinition(id, 10, rank, TargetMode.ENEMY, SpellBehaviorId.DIRECT,
                0, 24, true, SpellSafetyCategory.STANDARD);
    }

    private static final class FakeSpell extends AbstractSpell {
        private final ResourceLocation id;
        private final CastType castType;
        private final boolean enabled;
        private final SpellRarity rarity;

        private FakeSpell(ResourceLocation id, CastType castType, boolean enabled, SpellRarity rarity) {
            this.id = id;
            this.castType = castType;
            this.enabled = enabled;
            this.rarity = rarity;
        }

        @Override
        public ResourceLocation getSpellResource() {
            return id;
        }

        @Override
        public DefaultConfig getDefaultConfig() {
            return null;
        }

        @Override
        public CastType getCastType() {
            return castType;
        }

        @Override
        public int getMaxLevel() {
            return 10;
        }

        @Override
        public SpellRarity getRarity(int level) {
            return rarity;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
