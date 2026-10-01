package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.compat.iron.IronSpellBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public interface SpellBehavior {
    SpellInvocation createInvocation(Mob caster, LivingEntity target, IronSpellBridge iron);

    boolean canStart(LivingEntity target, ResolvedSpell spell,
                     SpellInvocation invocation, IronSpellBridge iron);

    void prepare(LivingEntity target, ResolvedSpell spell,
                 SpellInvocation invocation, IronSpellBridge iron);

    void cast(LivingEntity target, ResolvedSpell spell,
              SpellInvocation invocation, IronSpellBridge iron);
}
