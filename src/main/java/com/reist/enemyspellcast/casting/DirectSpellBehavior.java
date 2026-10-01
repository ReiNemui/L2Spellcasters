package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.compat.iron.IronSpellBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class DirectSpellBehavior implements SpellBehavior {
    @Override
    public SpellInvocation createInvocation(Mob caster, LivingEntity target, IronSpellBridge iron) {
        return new SpellInvocation(caster, iron.magicData(caster));
    }

    @Override
    public boolean canStart(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                            IronSpellBridge iron) {
        return iron.checkPreCast(spell, invocation.executionEntity(), invocation.magicData());
    }

    @Override
    public void prepare(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                        IronSpellBridge iron) {
        iron.preCast(spell, invocation.executionEntity(), invocation.magicData());
    }

    @Override
    public void cast(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                     IronSpellBridge iron) {
        iron.cast(spell, invocation.executionEntity(), invocation.magicData());
    }
}
