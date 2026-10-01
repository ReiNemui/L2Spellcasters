package com.reist.enemyspellcast.allegiance;

import com.reist.enemyspellcast.compat.l2.L2HostilityBridge;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import com.reist.enemyspellcast.casting.CastRuntimeRegistry;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

public final class FriendlyFireEvents {
    private FriendlyFireEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.register(FriendlyFireEvents.class);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellDamage(SpellDamageEvent event) {
        if (!EnemySpellCastConfig.snapshot().friendlyFireProtection()) {
            return;
        }
        Entity causing = event.getSpellDamageSource().getEntity();
        LivingEntity caster = AllegianceResolver.resolveLivingOwner(causing);
        if (!isManagedCaster(caster)) {
            caster = AllegianceResolver.resolveLivingOwner(
                    event.getSpellDamageSource().getDirectEntity());
        }
        if (!isManagedCaster(caster)) {
            return;
        }
        if (!AllegianceResolver.isHostileTarget(caster, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isManagedCaster(LivingEntity caster) {
        return caster instanceof net.minecraft.world.entity.Mob mob
                && (L2HostilityBridge.traitRank(caster) > 0
                || CastRuntimeRegistry.get(mob).isPresent());
    }
}
