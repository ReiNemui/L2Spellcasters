package com.reist.enemyspellcast.catalog;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.config.SpellSelectionMode;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellSelectionPolicyTest {
    private static final ResourceLocation ROOT = id("root");
    private static final ResourceLocation RAISE_DEAD = id("raise_dead");
    private static final ResourceLocation PORTAL = id("portal");

    @Test
    void defaultPoolContainsOnlyStandardDefinitions() {
        SpellCatalog catalog = build(Settings.defaults(), definitions());

        assertEquals(List.of(ROOT), catalog.ids());
    }

    @Test
    void allowlistModeContainsOnlyListedStandardDefinitions() {
        Settings settings = Settings.defaults().withSpellOverrides(
                SpellSelectionMode.ALLOWLIST_ONLY, List.of(ROOT.toString()), List.of(), List.of());

        assertEquals(List.of(ROOT), build(settings, definitions()).ids());
    }

    @Test
    void unsafeSpellRequiresCategoryOptInAndExplicitId() {
        Settings explicitOnly = Settings.defaults().withSpellOverrides(
                SpellSelectionMode.DEFAULT_POOL, List.of(), List.of(PORTAL.toString()), List.of());
        Settings categoryOnly = Settings.defaults().withCategoryExclusions(true, true, false);
        Settings both = explicitOnly.withCategoryExclusions(true, true, false);

        assertTrue(build(explicitOnly, List.of(definition(PORTAL, SpellSafetyCategory.PORTAL))).ids().isEmpty());
        assertTrue(build(categoryOnly, List.of(definition(PORTAL, SpellSafetyCategory.PORTAL))).ids().isEmpty());
        assertEquals(List.of(PORTAL),
                build(both, List.of(definition(PORTAL, SpellSafetyCategory.PORTAL))).ids());
    }

    @Test
    void denyWinsOverEveryAllowMechanism() {
        Settings settings = Settings.defaults().withSpellOverrides(
                SpellSelectionMode.DEFAULT_POOL, List.of(), List.of(ROOT.toString()), List.of(ROOT.toString()));

        assertTrue(build(settings, List.of(definition(ROOT, SpellSafetyCategory.STANDARD))).ids().isEmpty());
    }

    @Test
    void malformedIdsWarnOncePerValueWithoutDroppingValidDefinitions() {
        var warnings = new ArrayList<String>();
        Settings settings = Settings.defaults().withSpellOverrides(
                SpellSelectionMode.DEFAULT_POOL, List.of(), List.of("not an id"), List.of("still not an id"));

        SpellCatalog catalog = SpellCatalog.build(definitions(), FakeSpell::new, settings, warnings::add);

        assertEquals(List.of(ROOT), catalog.ids());
        assertEquals(2, warnings.stream().filter(message -> message.contains("invalid spell id")).count());
    }

    private static SpellCatalog build(Settings settings, List<SpellDefinition> definitions) {
        return SpellCatalog.build(definitions, FakeSpell::new, settings, ignored -> { });
    }

    private static List<SpellDefinition> definitions() {
        return List.of(
                definition(ROOT, SpellSafetyCategory.STANDARD),
                definition(RAISE_DEAD, SpellSafetyCategory.SUMMONING),
                definition(PORTAL, SpellSafetyCategory.PORTAL));
    }

    private static SpellDefinition definition(ResourceLocation id, SpellSafetyCategory category) {
        return new SpellDefinition(id, 10, 1, TargetMode.ENEMY, SpellBehaviorId.DIRECT,
                0, 24, true, category);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", path);
    }

    private static final class FakeSpell extends AbstractSpell {
        private final ResourceLocation id;

        private FakeSpell(ResourceLocation id) {
            this.id = id;
        }

        @Override public ResourceLocation getSpellResource() { return id; }
        @Override public DefaultConfig getDefaultConfig() { return null; }
        @Override public CastType getCastType() { return CastType.LONG; }
        @Override public int getMaxLevel() { return 10; }
        @Override public SpellRarity getRarity(int level) { return SpellRarity.COMMON; }
        @Override public boolean isEnabled() { return true; }
    }
}
