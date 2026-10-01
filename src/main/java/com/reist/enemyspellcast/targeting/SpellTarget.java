package com.reist.enemyspellcast.targeting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import net.minecraft.world.entity.LivingEntity;

public record SpellTarget(ResolvedSpell spell, LivingEntity target, double score) {
}
