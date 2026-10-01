package com.reist.enemyspellcast.casting;

import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class CastRuntimeRegistry {
    private static final WeakHashMap<Mob, SpellCastController> CONTROLLERS = new WeakHashMap<>();

    private CastRuntimeRegistry() {
    }

    public static synchronized Optional<SpellCastController> get(Mob mob) {
        return Optional.ofNullable(CONTROLLERS.get(mob));
    }

    public static synchronized SpellCastController getOrCreate(Mob mob,
                                                                Supplier<SpellCastController> factory) {
        return CONTROLLERS.computeIfAbsent(mob, ignored -> factory.get());
    }

    public static synchronized void remove(Mob mob) {
        SpellCastController controller = CONTROLLERS.remove(mob);
        if (controller != null) {
            controller.close();
        }
    }

    public static synchronized boolean interrupt(Mob mob) {
        SpellCastController controller = CONTROLLERS.get(mob);
        return controller != null && controller.interrupt();
    }
}
