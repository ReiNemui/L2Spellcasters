package com.reist.enemyspellcast.catalog;

import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.config.SpellSelectionMode;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Compiled config policy used while constructing an immutable spell catalog. */
final class SpellSelectionPolicy {
    private final Settings settings;
    private final Set<ResourceLocation> allowed;
    private final Set<ResourceLocation> forced;
    private final Set<ResourceLocation> denied;

    private SpellSelectionPolicy(Settings settings, Set<ResourceLocation> allowed,
                                 Set<ResourceLocation> forced, Set<ResourceLocation> denied) {
        this.settings = settings;
        this.allowed = Set.copyOf(allowed);
        this.forced = Set.copyOf(forced);
        this.denied = Set.copyOf(denied);
    }

    static SpellSelectionPolicy compile(Settings rawSettings, Consumer<String> warningSink) {
        Settings settings = rawSettings.sanitized();
        Set<ResourceLocation> allowed = parseIds(settings.allowedSpellIds(), warningSink, "allowed-spell");
        Set<ResourceLocation> forced = parseIds(settings.forceAllowSpellIds(), warningSink, "force-allow");
        Set<ResourceLocation> denied = parseIds(settings.forceDenySpellIds(), warningSink, "force-deny");
        return new SpellSelectionPolicy(settings, allowed, forced, denied);
    }

    boolean includes(SpellDefinition definition) {
        ResourceLocation id = definition.spellId();
        if (denied.contains(id)) {
            return false;
        }

        boolean explicitlySelected = settings.selectionMode() == SpellSelectionMode.ALLOWLIST_ONLY
                ? allowed.contains(id)
                : forced.contains(id);
        if (definition.safetyCategory() != SpellSafetyCategory.STANDARD) {
            return explicitlySelected && categoryEnabled(definition.safetyCategory());
        }
        return settings.selectionMode() == SpellSelectionMode.DEFAULT_POOL || allowed.contains(id);
    }

    boolean bypassesProgression(ResourceLocation id) {
        return settings.selectionMode() == SpellSelectionMode.DEFAULT_POOL
                && forced.contains(id) && !denied.contains(id);
    }

    private boolean categoryEnabled(SpellSafetyCategory category) {
        return switch (category) {
            case STANDARD -> true;
            case SUMMONING -> !settings.excludeSummoningSpells();
            case TERRAIN_CHANGE -> !settings.excludeTerrainChangingSpells();
            case PORTAL -> !settings.excludePortalSpells();
        };
    }

    private static Set<ResourceLocation> parseIds(List<String> values, Consumer<String> warningSink,
                                                   String listName) {
        var result = new LinkedHashSet<ResourceLocation>();
        for (String value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                warningSink.accept("Ignoring invalid spell id in " + listName + " config: " + value);
            } else {
                result.add(id);
            }
        }
        return result;
    }
}
