package com.reist.enemyspellcast.event;

import com.reist.enemyspellcast.ai.SpellGoalInstaller;
import com.reist.enemyspellcast.allegiance.FriendlyFireEvents;
import com.reist.enemyspellcast.catalog.SpellCatalogReloadListener;
import com.reist.enemyspellcast.casting.CastRuntimeRegistry;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(CommonEvents::addReloadListeners);
        gameBus.addListener(CommonEvents::onDamageTaken);
        gameBus.addListener(CommonEvents::onEntityLeaveLevel);
        FriendlyFireEvents.register(gameBus);
    }

    private static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(SpellCatalogReloadListener.INSTANCE);
    }

    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getNewDamage() <= 0.0F) {
            return;
        }
        double threshold = mob.getMaxHealth()
                * EnemySpellCastConfig.snapshot().interruptDamageFraction();
        if (event.getNewDamage() >= threshold) {
            CastRuntimeRegistry.interrupt(mob);
        }
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) {
            SpellGoalInstaller.remove(mob);
        }
    }
}
