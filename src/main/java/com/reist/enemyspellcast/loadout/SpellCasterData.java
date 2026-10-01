package com.reist.enemyspellcast.loadout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reist.enemyspellcast.progression.ProgressionRules;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Versioned persistent state owned exclusively by the Spell Caster trait. */
public record SpellCasterData(
        int version,
        List<ResourceLocation> loadout,
        double mana,
        Map<ResourceLocation, Integer> cooldowns,
        int globalCooldown
) {
    public static final int CURRENT_VERSION = 1;
    public static final String NBT_KEY = "enemyspellcast:spell_caster";

    public static final Codec<SpellCasterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(SpellCasterData::version),
            ResourceLocation.CODEC.listOf().optionalFieldOf("loadout", List.of())
                    .forGetter(SpellCasterData::loadout),
            Codec.DOUBLE.optionalFieldOf("mana", 0.0).forGetter(SpellCasterData::mana),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(SpellCasterData::cooldowns),
            Codec.INT.optionalFieldOf("global_cooldown", 0).forGetter(SpellCasterData::globalCooldown)
    ).apply(instance, SpellCasterData::new));

    public SpellCasterData {
        loadout = loadout == null ? List.of() : List.copyOf(loadout);
        cooldowns = cooldowns == null ? Map.of() : Map.copyOf(cooldowns);
    }

    public static SpellCasterData fresh(double maxMana) {
        double safeMaximum = finiteNonNegative(maxMana);
        return new SpellCasterData(CURRENT_VERSION, List.of(), safeMaximum, Map.of(), 0);
    }

    public SpellCasterData sanitized(double maxMana) {
        double safeMaximum = finiteNonNegative(maxMana);
        double safeMana = Double.isFinite(mana) ? Math.max(0.0, Math.min(safeMaximum, mana)) : 0.0;
        var uniqueLoadout = new LinkedHashSet<ResourceLocation>();
        loadout.stream().filter(java.util.Objects::nonNull).forEach(uniqueLoadout::add);
        var safeCooldowns = new LinkedHashMap<ResourceLocation, Integer>();
        cooldowns.forEach((id, ticks) -> {
            if (id != null && ticks != null) {
                safeCooldowns.put(id, Math.max(0, ticks));
            }
        });
        return new SpellCasterData(CURRENT_VERSION, List.copyOf(uniqueLoadout), safeMana,
                safeCooldowns, Math.max(0, globalCooldown));
    }

    public SpellCasterData withLoadout(List<ResourceLocation> ids) {
        return new SpellCasterData(version, ids, mana, cooldowns, globalCooldown);
    }

    public SpellCasterData withMana(double value) {
        return new SpellCasterData(version, loadout, value, cooldowns, globalCooldown);
    }

    public SpellCasterData withCooldown(ResourceLocation spellId, int ticks) {
        var updated = new LinkedHashMap<>(cooldowns);
        updated.put(spellId, Math.max(0, ticks));
        return new SpellCasterData(version, loadout, mana, updated, globalCooldown);
    }

    public SpellCasterData withGlobalCooldown(int ticks) {
        return new SpellCasterData(version, loadout, mana, cooldowns, Math.max(0, ticks));
    }

    public List<ResourceLocation> activeLoadout(int traitRank) {
        int end = Math.min(loadout.size(), ProgressionRules.slotCount(traitRank));
        return List.copyOf(loadout.subList(0, end));
    }

    public int cooldown(ResourceLocation spellId) {
        return Math.max(0, cooldowns.getOrDefault(spellId, 0));
    }

    public SpellCasterData tick(double manaGain, double maxMana) {
        var updatedCooldowns = new LinkedHashMap<ResourceLocation, Integer>();
        cooldowns.forEach((id, ticks) -> {
            int remaining = Math.max(0, ticks - 1);
            if (remaining > 0) {
                updatedCooldowns.put(id, remaining);
            }
        });
        double updatedMana = Double.isFinite(manaGain) ? mana + Math.max(0.0, manaGain) : mana;
        return new SpellCasterData(version, loadout, updatedMana, updatedCooldowns,
                Math.max(0, globalCooldown - 1)).sanitized(maxMana);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
