package com.reist.enemyspellcast.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record SpellPoolFile(List<SpellDefinition> spells) {
    public static final Codec<SpellPoolFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SpellDefinition.CODEC.listOf().fieldOf("spells").forGetter(SpellPoolFile::spells)
    ).apply(instance, SpellPoolFile::new));

    public SpellPoolFile {
        spells = List.copyOf(spells);
    }
}
