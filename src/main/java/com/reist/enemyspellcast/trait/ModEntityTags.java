package com.reist.enemyspellcast.trait;

import com.reist.enemyspellcast.EnemySpellCast;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ModEntityTags {
    public static final TagKey<EntityType<?>> SPELL_CASTER_BLACKLIST = create("spell_caster_blacklist");
    public static final TagKey<EntityType<?>> SPELL_CASTER_FORCE_ALLOW = create("spell_caster_force_allow");
    public static final TagKey<EntityType<?>> SPELL_SUPPORT_BLACKLIST = create("spell_support_blacklist");

    private ModEntityTags() {
    }

    private static TagKey<EntityType<?>> create(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, EnemySpellCast.id(name));
    }
}
