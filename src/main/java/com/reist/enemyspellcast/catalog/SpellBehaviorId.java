package com.reist.enemyspellcast.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum SpellBehaviorId implements StringRepresentable {
    DIRECT,
    ALLY_SELF_CAST,
    TARGET_ENTITY;

    public static final Codec<SpellBehaviorId> CODEC = StringRepresentable.fromEnum(SpellBehaviorId::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
