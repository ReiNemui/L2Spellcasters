package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** World-dependent validation seam kept outside the casting state machine. */
public interface CastEnvironment {
    boolean casterValid(Mob caster);

    boolean targetValid(Mob caster, LivingEntity target);

    boolean inRangeAndVisible(Mob caster, LivingEntity target, ResolvedSpell spell);

    void faceTarget(Mob caster, LivingEntity target);

    static CastEnvironment nativeEnvironment() {
        return Native.INSTANCE;
    }

    enum Native implements CastEnvironment {
        INSTANCE;

        @Override
        public boolean casterValid(Mob caster) {
            return caster != null && caster.isAlive() && !caster.isRemoved();
        }

        @Override
        public boolean targetValid(Mob caster, LivingEntity target) {
            return target != null && target.isAlive() && !target.isRemoved()
                    && caster.level() == target.level();
        }

        @Override
        public boolean inRangeAndVisible(Mob caster, LivingEntity target, ResolvedSpell spell) {
            double distanceSquared = caster.distanceToSqr(target);
            double minimum = spell.definition().minRange();
            double maximum = spell.definition().maxRange();
            return distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum
                    && (!spell.definition().requiresLineOfSight() || caster.hasLineOfSight(target));
        }

        @Override
        public void faceTarget(Mob caster, LivingEntity target) {
            caster.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            caster.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }
}
