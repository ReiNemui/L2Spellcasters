package com.reist.enemyspellcast.trait;

import com.reist.enemyspellcast.EnemySpellCast;
import dev.xkmc.l2hostility.content.item.traits.TraitSymbol;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnemySpellCast.MOD_ID);

    public static final DeferredItem<TraitSymbol> SPELL_CASTER = ITEMS.register("spell_caster",
            () -> new TraitSymbol(new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
