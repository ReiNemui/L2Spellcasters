package com.reist.enemyspellcast;

import com.mojang.logging.LogUtils;
import com.reist.enemyspellcast.catalog.SpellCatalog;
import com.reist.enemyspellcast.ai.SpellGoalInstaller;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import com.reist.enemyspellcast.event.CommonEvents;
import com.reist.enemyspellcast.trait.ModItems;
import com.reist.enemyspellcast.trait.ModTraits;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(EnemySpellCast.MOD_ID)
public final class EnemySpellCast {
    public static final String MOD_ID = "enemyspellcast";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public EnemySpellCast(IEventBus modBus, ModContainer container) {
        ModTraits.register(modBus);
        ModItems.register(modBus);
        CommonEvents.register(NeoForge.EVENT_BUS);
        container.registerConfig(ModConfig.Type.COMMON, EnemySpellCastConfig.SPEC,
                MOD_ID + "-common.toml");
        modBus.addListener(this::onConfigLoading);
        modBus.addListener(this::onConfigReloading);
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        refreshConfig(event);
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        refreshConfig(event);
    }

    private static void refreshConfig(ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON
                && event.getConfig().getSpec() == EnemySpellCastConfig.SPEC) {
            var settings = EnemySpellCastConfig.reloadFromSpec();
            SpellCatalog.rebuildFromConfig(settings);
            SpellGoalInstaller.onConfigReload(settings);
        }
    }
}
