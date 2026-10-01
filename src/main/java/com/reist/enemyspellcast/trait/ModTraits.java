package com.reist.enemyspellcast.trait;

import com.reist.enemyspellcast.EnemySpellCast;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.registrate.LHTraits;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTraits {
    private static final DeferredRegister<MobTrait> TRAITS =
            DeferredRegister.create(LHTraits.TRAITS.key(), EnemySpellCast.MOD_ID);

    public static final DeferredHolder<MobTrait, SpellCasterTrait> SPELL_CASTER =
            TRAITS.register("spell_caster", SpellCasterTrait::new);

    private ModTraits() {
    }

    public static void register(IEventBus bus) {
        TRAITS.register(bus);
    }
}
