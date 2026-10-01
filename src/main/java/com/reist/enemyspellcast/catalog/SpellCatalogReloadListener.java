package com.reist.enemyspellcast.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.reist.enemyspellcast.EnemySpellCast;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public final class SpellCatalogReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final SpellCatalogReloadListener INSTANCE = new SpellCatalogReloadListener();

    private SpellCatalogReloadListener() {
        super(GSON, "enemyspellcast/spell_pool");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
                         ProfilerFiller profiler) {
        var definitions = new ArrayList<SpellDefinition>();
        resources.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> SpellPoolFile.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(error -> EnemySpellCast.LOGGER.error(
                                "Could not load spell pool {}: {}", entry.getKey(), error))
                        .ifPresent(file -> definitions.addAll(file.spells())));

        SpellCatalog catalog = SpellCatalog.build(definitions, SpellCatalog.registryResolver(),
                EnemySpellCastConfig.snapshot());
        SpellCatalog.publish(catalog);
        EnemySpellCast.LOGGER.info("Loaded {} safe enemy spell candidates from {} files",
                catalog.ids().size(), resources.size());
    }
}
