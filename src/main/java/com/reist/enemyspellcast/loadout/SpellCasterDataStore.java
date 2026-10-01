package com.reist.enemyspellcast.loadout;

import com.mojang.serialization.DataResult;
import com.reist.enemyspellcast.EnemySpellCast;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class SpellCasterDataStore {
    private static final Set<UUID> REPORTED_CORRUPT_MOBS = ConcurrentHashMap.newKeySet();

    private SpellCasterDataStore() {
    }

    public static SpellCasterData load(Mob mob, double maxMana) {
        Tag encoded = mob.getPersistentData().get(SpellCasterData.NBT_KEY);
        if (encoded == null) {
            return SpellCasterData.fresh(maxMana);
        }
        return decode(encoded, maxMana, message -> logCorruptOnce(mob, message));
    }

    public static void save(Mob mob, SpellCasterData value, double maxMana) {
        SpellCasterData safe = value.sanitized(maxMana);
        Tag encoded = SpellCasterData.CODEC.encodeStart(NbtOps.INSTANCE, safe).getOrThrow();
        mob.getPersistentData().put(SpellCasterData.NBT_KEY, encoded);
    }

    static SpellCasterData decode(Tag encoded, double maxMana, Consumer<String> warningSink) {
        var error = new AtomicReference<String>();
        Optional<SpellCasterData> parsed;
        try {
            DataResult<SpellCasterData> result = SpellCasterData.CODEC.parse(NbtOps.INSTANCE, encoded);
            parsed = result.resultOrPartial(error::set);
        } catch (RuntimeException exception) {
            error.set(exception.getMessage());
            parsed = Optional.empty();
        }
        if (error.get() != null || parsed.isEmpty()) {
            warningSink.accept("Could not decode Spell Caster state: " + error.get());
            return SpellCasterData.fresh(maxMana);
        }
        return parsed.get().sanitized(maxMana);
    }

    private static void logCorruptOnce(Mob mob, String message) {
        if (REPORTED_CORRUPT_MOBS.add(mob.getUUID())) {
            EnemySpellCast.LOGGER.warn("{} for mob {}", message, mob.getUUID());
        }
    }
}
