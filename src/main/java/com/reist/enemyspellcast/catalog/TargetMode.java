package com.reist.enemyspellcast.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum TargetMode implements StringRepresentable {
    ENEMY,
    SELF,
    ALLY;

    public static final Codec<TargetMode> CODEC = StringRepresentable.fromEnum(TargetMode::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
