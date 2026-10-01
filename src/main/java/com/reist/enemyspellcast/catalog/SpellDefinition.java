package com.reist.enemyspellcast.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable datapack schema for one candidate spell. */
public record SpellDefinition(
        ResourceLocation spellId,
        int weight,
        int minTraitRank,
        TargetMode targetMode,
        SpellBehaviorId behavior,
        double minRange,
        double maxRange,
        boolean requiresLineOfSight,
        SpellSafetyCategory safetyCategory
) {
    public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("spell").forGetter(SpellDefinition::spellId),
            Codec.INT.fieldOf("weight").forGetter(SpellDefinition::weight),
            Codec.INT.optionalFieldOf("min_trait_rank", 1).forGetter(SpellDefinition::minTraitRank),
            TargetMode.CODEC.fieldOf("target").forGetter(SpellDefinition::targetMode),
            SpellBehaviorId.CODEC.optionalFieldOf("behavior", SpellBehaviorId.DIRECT)
                    .forGetter(SpellDefinition::behavior),
            Codec.DOUBLE.optionalFieldOf("min_range", 0.0).forGetter(SpellDefinition::minRange),
            Codec.DOUBLE.optionalFieldOf("max_range", 24.0).forGetter(SpellDefinition::maxRange),
            Codec.BOOL.optionalFieldOf("requires_line_of_sight", true)
                    .forGetter(SpellDefinition::requiresLineOfSight),
            SpellSafetyCategory.CODEC.optionalFieldOf("safety_category", SpellSafetyCategory.STANDARD)
                    .forGetter(SpellDefinition::safetyCategory)
    ).apply(instance, SpellDefinition::new));

    public SpellDefinition {
        Objects.requireNonNull(spellId, "spellId");
        Objects.requireNonNull(targetMode, "targetMode");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(safetyCategory, "safetyCategory");
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be positive");
        }
        if (minTraitRank < 1 || minTraitRank > 5) {
            throw new IllegalArgumentException("minTraitRank must be between 1 and 5");
        }
        if (!Double.isFinite(minRange) || !Double.isFinite(maxRange)
                || minRange < 0.0 || maxRange < minRange) {
            throw new IllegalArgumentException("invalid spell range");
        }
    }
}
