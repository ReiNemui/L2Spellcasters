package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.catalog.TargetMode;
import com.reist.enemyspellcast.compat.iron.IronSpellBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Executes a self-targeted support spell on the selected ally. */
public final class AllySelfCastBehavior implements SpellBehavior {
    @Override
    public SpellInvocation createInvocation(Mob caster, LivingEntity target, IronSpellBridge iron) {
        return new SpellInvocation(target, iron.magicData(target));
    }

    @Override
    public boolean canStart(LivingEntity target, ResolvedSpell spell, SpellInvocation invocation,
                            IronSpellBridge iron) {
        return spell.definition().targetMode() == TargetMode.ALLY
                && target != null
                && iron.checkPreCast(spell, invocation.executionEntity(), invocation.magicData())
                && invocation.magicData().getAdditionalCastData() == null;
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
