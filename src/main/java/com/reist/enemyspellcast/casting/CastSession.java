package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import net.minecraft.world.entity.LivingEntity;

public record CastSession(
        ResolvedSpell spell,
        LivingEntity target,
        SpellBehavior behavior,
        SpellInvocation invocation,
        int manaCost,
        int initialDuration
) {
}
