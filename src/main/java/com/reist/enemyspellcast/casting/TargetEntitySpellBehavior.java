package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.compat.iron.IronSpellBridge;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Supplies Iron spells with the AI-selected target instead of a player ray trace. */
public final class TargetEntitySpellBehavior implements SpellBehavior {
    @Override
    public SpellInvocation createInvocation(Mob caster, LivingEntity target, IronSpellBridge iron) {
        return new SpellInvocation(caster, iron.magicData(caster));
    }

    @Override
    public boolean canStart(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                            IronSpellBridge iron) {
        return target != null && iron.checkPreCast(spell, invocation.executionEntity(), invocation.magicData());
    }

    @Override
    public void prepare(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                        IronSpellBridge iron) {
        iron.preCast(spell, invocation.executionEntity(), invocation.magicData());
        invocation.magicData().setAdditionalCastData(new TargetEntityCastData(target));
    }

    @Override
    public void cast(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                     IronSpellBridge iron) {
        iron.cast(spell, invocation.executionEntity(), invocation.magicData());
    }
}
