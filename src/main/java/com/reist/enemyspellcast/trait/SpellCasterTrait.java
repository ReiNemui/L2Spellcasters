package com.reist.enemyspellcast.trait;

import com.reist.enemyspellcast.ai.SpellGoalInstaller;
import com.reist.enemyspellcast.compat.l2.L2HostilityBridge;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;

/** L2 Hostility trait marker. Spell behavior is installed separately by the AI integration. */
public final class SpellCasterTrait extends MobTrait {
    public SpellCasterTrait() {
        super(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public boolean allow(LivingEntity entity, int difficulty, int maxModLevel) {
        if (!(entity instanceof Mob mob) || mob.isNoAi()) {
            return false;
        }
        // Hard safety exclusions always win, including when a datapack puts the
        // same entity type in the force-allow tag.
        if (L2HostilityBridge.isSummonedOrMinion(entity)
                || entity instanceof OwnableEntity
                || entity.getType().is(ModEntityTags.SPELL_CASTER_BLACKLIST)) {
            return false;
        }
        boolean normallyEligible = entity instanceof Enemy;
        boolean explicitlyAllowed = entity.getType().is(ModEntityTags.SPELL_CASTER_FORCE_ALLOW);
        return (normallyEligible || explicitlyAllowed)
                && super.allow(entity, difficulty, maxModLevel);
    }

    @Override
    public void initialize(LivingEntity entity, int rank) {
        ensureGoal(entity, rank);
    }

    @Override
    public void postInit(LivingEntity entity, int rank) {
        ensureGoal(entity, rank);
    }

    @Override
    public void tick(LivingEntity entity, int rank) {
        ensureGoal(entity, rank);
    }

    private static void ensureGoal(LivingEntity entity, int rank) {
        if (!entity.level().isClientSide() && entity instanceof Mob mob) {
            SpellGoalInstaller.sync(mob, rank);
        }
    }
}
