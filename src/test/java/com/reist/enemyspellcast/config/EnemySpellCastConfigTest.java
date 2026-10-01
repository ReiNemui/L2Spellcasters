package com.reist.enemyspellcast.config;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemySpellCastConfigTest {
    @AfterEach
    void restoreDefaults() {
        EnemySpellCastConfig.publishForTest(Settings.defaults());
    }

    @Test
    void snapshotIsReusedUntilAConfigPublication() {
        Settings before = EnemySpellCastConfig.snapshot();
        long generation = EnemySpellCastConfig.generation();

        assertSame(before, EnemySpellCastConfig.snapshot());
        assertEquals(generation, EnemySpellCastConfig.generation());

        EnemySpellCastConfig.publishForTest(Settings.defaults());

        assertNotSame(before, EnemySpellCastConfig.snapshot());
        assertEquals(generation + 1, EnemySpellCastConfig.generation());
    }

    @Test
    void spellListsAreTrimmedDeduplicatedAndImmutable() {
        Settings raw = Settings.defaults().withSpellOverrides(
                SpellSelectionMode.ALLOWLIST_ONLY,
                List.of(" irons_spellbooks:root ", "irons_spellbooks:root", " "),
                List.of(" irons_spellbooks:teleport ", "irons_spellbooks:teleport"),
                List.of(" irons_spellbooks:portal ", "irons_spellbooks:portal"));

        Settings clean = raw.sanitized();

        assertEquals(List.of("irons_spellbooks:root"), clean.allowedSpellIds());
        assertEquals(List.of("irons_spellbooks:teleport"), clean.forceAllowSpellIds());
        assertEquals(List.of("irons_spellbooks:portal"), clean.forceDenySpellIds());
        assertThrows(UnsupportedOperationException.class,
                () -> clean.allowedSpellIds().add("test:mutate"));
    }

    @Test
    void defaultsKeepCurrentSafePoolBehavior() {
        Settings defaults = Settings.defaults();

        assertEquals(SpellSelectionMode.DEFAULT_POOL, defaults.selectionMode());
        assertTrue(defaults.allowedSpellIds().isEmpty());
        assertTrue(defaults.excludeSummoningSpells());
        assertTrue(defaults.excludeTerrainChangingSpells());
        assertTrue(defaults.excludePortalSpells());
    }
}
