package com.reist.enemyspellcast.targeting;

import com.reist.enemyspellcast.allegiance.AllegianceResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** World-query boundary used by one spell-scoring decision. */
public interface TargetFinder {
    LivingEntity findEnemy(Mob caster);

    List<LivingEntity> findAllies(Mob caster, double range);

    final class Native implements TargetFinder {
        @Override
        public LivingEntity findEnemy(Mob caster) {
            LivingEntity target = caster.getTarget();
            return target != null && AllegianceResolver.isHostileTarget(caster, target) ? target : null;
        }

        @Override
        public List<LivingEntity> findAllies(Mob caster, double range) {
            return List.copyOf(caster.level().getEntitiesOfClass(LivingEntity.class,
                    caster.getBoundingBox().inflate(range),
                    target -> target != caster && target.isAlive()
                            && AllegianceResolver.isSupportTarget(caster, target)));
        }
    }
}
