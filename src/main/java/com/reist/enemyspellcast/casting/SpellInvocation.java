package com.reist.enemyspellcast.casting;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
/** Entity and Iron casting state used consistently throughout one cast lifecycle. */
public record SpellInvocation(@Nullable LivingEntity executionEntity, @Nullable MagicData magicData) {
}
