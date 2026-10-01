package com.reist.enemyspellcast.resources;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.reist.enemyspellcast.catalog.SpellDefinition;
import com.reist.enemyspellcast.catalog.SpellPoolFile;
import com.reist.enemyspellcast.catalog.SpellSafetyCategory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackResourcesTest {
    @Test
    void traitDataMapUsesL2RegistryPathAndAgreedProgressionValues() throws Exception {
        // Data-map resources live under the data map id's namespace, not the
        // contributing mod's namespace. L2 Hostility owns l2hostility:trait_data.
        String path = "/data/l2hostility/data_maps/l2hostility/trait/trait_data.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", "");
            assertTrue(json.contains("\"enemyspellcast:spell_caster\":{"));
            assertTrue(json.contains("\"cost\":80"));
            assertTrue(json.contains("\"weight\":100"));
            assertTrue(json.contains("\"max_rank\":5"));
            assertTrue(json.contains("\"min_level\":100"));
        }
    }

    @Test
    void visibleTraitResourcesAndEntityPolicyTagsExist() {
        assertResource("/assets/enemyspellcast/models/item/spell_caster.json");
        assertResource("/assets/enemyspellcast/textures/item/trait/spell_caster.png");
        assertResource("/assets/enemyspellcast/lang/en_us.json");
        assertResource("/assets/enemyspellcast/lang/ja_jp.json");
        assertResource("/data/l2hostility/tags/item/trait_item.json");
        assertResource("/data/enemyspellcast/tags/entity_type/spell_caster_blacklist.json");
        assertResource("/data/enemyspellcast/tags/entity_type/spell_caster_force_allow.json");
        assertResource("/data/enemyspellcast/tags/entity_type/spell_support_blacklist.json");
    }

    @Test
    void corePoolHasSafeVarietyAndExperimentalPoolIsClassified() throws Exception {
        SpellPoolFile pool = readPool("core.json");

        assertTrue(pool.spells().size() >= 45 && pool.spells().size() <= 55);
        Set<ResourceLocation> ids = pool.spells().stream().map(SpellDefinition::spellId)
                .collect(Collectors.toSet());
        assertEquals(pool.spells().size(), ids.size());
        assertTrue(pool.spells().stream().allMatch(spell -> spell.safetyCategory() == SpellSafetyCategory.STANDARD));
        for (String required : Set.of("teleport", "blood_step", "evasion", "slow", "root",
                "arcane_shackle", "chain_lightning", "arrow_volley", "fang_ward", "firecracker",
                "ice_spikes", "shockwave", "ball_lightning", "echoing_strikes", "gravity_fissure")) {
            assertTrue(ids.contains(iron(required)), required);
        }
        for (String excluded : Set.of("starfall", "telekinesis", "raise_dead", "earthquake",
                "portal", "acid_orb", "poison_splash", "frostwave", "spectral_hammer")) {
            assertFalse(ids.contains(iron(excluded)), excluded);
        }

        SpellPoolFile experimental = readPool("experimental.json");
        Set<SpellSafetyCategory> categories = experimental.spells().stream()
                .map(SpellDefinition::safetyCategory).collect(Collectors.toSet());
        assertTrue(categories.contains(SpellSafetyCategory.SUMMONING));
        assertTrue(categories.contains(SpellSafetyCategory.TERRAIN_CHANGE));
        assertTrue(categories.contains(SpellSafetyCategory.PORTAL));
        assertFalse(categories.contains(SpellSafetyCategory.STANDARD));
    }

    private SpellPoolFile readPool(String name) throws Exception {
        String path = "/data/enemyspellcast/enemyspellcast/spell_pool/" + name;
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return SpellPoolFile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        }
    }

    private void assertResource(String path) {
        assertNotNull(getClass().getResource(path), path);
    }

    private static ResourceLocation iron(String path) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", path);
    }
}
