package com.reist.enemyspellcast.compat.l2;

import com.reist.enemyspellcast.trait.ModTraits;
import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import net.minecraft.world.entity.LivingEntity;

/** Narrow boundary around L2 Hostility capability access. */
public final class L2HostilityBridge {
    private L2HostilityBridge() {
    }

    public static int traitRank(LivingEntity entity) {
        return LHMiscs.MOB.type().getExisting(entity)
                .map(cap -> cap.getTraitLevel(ModTraits.SPELL_CASTER.get()))
                .orElse(0);
    }

    public static int mobLevel(LivingEntity entity) {
        return LHMiscs.MOB.type().getExisting(entity)
                .map(MobTraitCap::getLevel)
                .orElse(0);
    }

    public static boolean isSummonedOrMinion(LivingEntity entity) {
        return LHMiscs.MOB.type().getExisting(entity)
                .map(cap -> cap.summoned || cap.minion)
                .orElse(false);
    }
}
