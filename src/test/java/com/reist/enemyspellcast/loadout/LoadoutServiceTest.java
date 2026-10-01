package com.reist.enemyspellcast.loadout;

import com.reist.enemyspellcast.catalog.SpellBehaviorId;
import com.reist.enemyspellcast.catalog.SpellDefinition;
import com.reist.enemyspellcast.catalog.SpellSafetyCategory;
import com.reist.enemyspellcast.catalog.TargetMode;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadoutServiceTest {
    private final LoadoutService service = new LoadoutService();

    @Test
    void rankIncreaseKeepsExistingAndOnlyFillsMissingSlots() {
        var data = SpellCasterData.fresh(100).withLoadout(ids("firebolt"));

        var result = service.reconcileDefinitions(data, 5,
                definitions("firebolt", "icicle", "magic_missile"), () -> 0.0);

        assertEquals(id("firebolt"), result.loadout().getFirst());
        assertEquals(3, result.activeLoadout(5).size());
    }

    @Test
    void rankDecreaseHidesButDoesNotDeleteExtraSlots() {
        var original = ids("firebolt", "icicle", "magic_missile");
        var data = SpellCasterData.fresh(100).withLoadout(original);

        var result = service.reconcileDefinitions(data, 1,
                definitions("firebolt", "icicle", "magic_missile"), () -> 0.0);

        assertEquals(original, result.loadout());
        assertEquals(List.of(id("firebolt")), result.activeLoadout(1));
    }

    @Test
    void missingSpellIsRemovedAndReplacedInPlace() {
        var data = SpellCasterData.fresh(100).withLoadout(ids("removed", "icicle"));

        var result = service.reconcileDefinitions(data, 3,
                definitions("firebolt", "icicle"), () -> 0.0);

        assertEquals(ids("firebolt", "icicle"), result.loadout());
    }

    @Test
    void stateSanitizesManaCooldownsAndMalformedNbt() {
        var cooldowns = new LinkedHashMap<ResourceLocation, Integer>();
        cooldowns.put(id("firebolt"), -20);
        var unsafe = new SpellCasterData(1, ids("firebolt"), Double.NaN, cooldowns, -4);
        var safe = unsafe.sanitized(80);

        assertEquals(0.0, safe.mana());
        assertEquals(0, safe.cooldown(id("firebolt")));
        assertEquals(0, safe.globalCooldown());
        assertEquals(80.0, SpellCasterData.fresh(200).withMana(500).sanitized(80).mana());
        assertEquals(SpellCasterData.fresh(80),
                SpellCasterDataStore.decode(IntTag.valueOf(7), 80, message -> { }));
    }

    private static List<SpellDefinition> definitions(String... names) {
        return java.util.Arrays.stream(names).map(name -> new SpellDefinition(
                id(name), 1, 1, TargetMode.ENEMY, SpellBehaviorId.DIRECT,
                0, 24, true, SpellSafetyCategory.STANDARD)).toList();
    }

    private static List<ResourceLocation> ids(String... names) {
        return java.util.Arrays.stream(names).map(LoadoutServiceTest::id).toList();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", name);
    }
}
