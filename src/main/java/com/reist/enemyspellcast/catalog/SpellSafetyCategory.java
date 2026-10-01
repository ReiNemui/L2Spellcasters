package com.reist.enemyspellcast.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Safety gate applied before a spell is resolved or made available to mob AI. */
public enum SpellSafetyCategory implements StringRepresentable {
    STANDARD,
    SUMMONING,
    TERRAIN_CHANGE,
    PORTAL;

    public static final Codec<SpellSafetyCategory> CODEC =
            StringRepresentable.fromEnum(SpellSafetyCategory::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
