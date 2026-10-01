package com.reist.enemyspellcast.loadout;

import com.reist.enemyspellcast.catalog.SpellCatalog;
import com.reist.enemyspellcast.catalog.SpellDefinition;
import com.reist.enemyspellcast.progression.ProgressionRules;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class LoadoutService {
    public SpellCasterData reconcile(SpellCasterData data, int traitRank, SpellCatalog catalog,
                                     RollSource rolls) {
        List<SpellDefinition> eligible = catalog.eligible(traitRank).stream()
                .map(resolved -> resolved.definition()).toList();
        return reconcile(data, traitRank, catalog.definitions(), eligible, rolls);
    }

    SpellCasterData reconcileDefinitions(SpellCasterData data, int traitRank,
                                         List<SpellDefinition> definitions, RollSource rolls) {
        return reconcile(data, traitRank, definitions, definitions, rolls);
    }

    private SpellCasterData reconcile(SpellCasterData data, int traitRank,
                                      List<SpellDefinition> validDefinitions,
                                      List<SpellDefinition> fillDefinitions,
                                      RollSource rolls) {
        var validById = indexById(validDefinitions);
        var fillById = indexById(fillDefinitions);
        int targetSize = Math.max(data.loadout().size(), ProgressionRules.slotCount(traitRank));
        var slots = new ArrayList<ResourceLocation>(targetSize);
        var retained = new HashSet<ResourceLocation>();

        for (ResourceLocation id : data.loadout()) {
            if (validById.containsKey(id) && retained.add(id)) {
                slots.add(id);
            } else {
                slots.add(null);
            }
        }
        while (slots.size() < targetSize) {
            slots.add(null);
        }

        List<SpellDefinition> choices = fillById.values().stream()
                .filter(definition -> !retained.contains(definition.spellId()))
                .toList();
        int missing = (int) slots.stream().filter(java.util.Objects::isNull).count();
        List<SpellDefinition> picked = WeightedPicker.pick(choices, missing,
                SpellDefinition::weight, rolls);
        int pickIndex = 0;
        for (int slot = 0; slot < slots.size() && pickIndex < picked.size(); slot++) {
            if (slots.get(slot) == null) {
                slots.set(slot, picked.get(pickIndex++).spellId());
            }
        }
        slots.removeIf(java.util.Objects::isNull);
        return data.withLoadout(slots);
    }

    private static LinkedHashMap<ResourceLocation, SpellDefinition> indexById(
            List<SpellDefinition> definitions) {
        var result = new LinkedHashMap<ResourceLocation, SpellDefinition>();
        definitions.forEach(definition -> result.putIfAbsent(definition.spellId(), definition));
        return result;
    }
}
