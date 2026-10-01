package com.reist.enemyspellcast.allegiance;

import com.reist.enemyspellcast.trait.ModEntityTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AllegianceResolver {
    private static final int MAX_OWNER_DEPTH = 4;

    private AllegianceResolver() {
    }

    public static Allegiance resolve(LivingEntity caster, LivingEntity target) {
        return AllegiancePolicy.resolve(view(caster), view(target));
    }

    public static boolean isHostileTarget(LivingEntity caster, LivingEntity target) {
        return resolve(caster, target) == Allegiance.ENEMY;
    }

    public static boolean isSupportTarget(LivingEntity caster, LivingEntity target) {
        if (target.getType().is(ModEntityTags.SPELL_SUPPORT_BLACKLIST)) {
            return false;
        }
        Allegiance allegiance = resolve(caster, target);
        return allegiance == Allegiance.SELF || allegiance == Allegiance.ALLY;
    }

    public static LivingEntity resolveLivingOwner(Entity source) {
        Entity resolved = resolveOwnerChain(source, AllegianceResolver::ownerOf,
                LivingEntity.class::isInstance);
        return resolved instanceof LivingEntity living ? living : null;
    }

    static <T> T resolveOwnerChain(T source, Function<T, T> ownerLookup, Predicate<T> terminal) {
        if (source == null) {
            return null;
        }
        Set<T> visited = new HashSet<>();
        T current = source;
        for (int depth = 0; depth <= MAX_OWNER_DEPTH && current != null; depth++) {
            if (!visited.add(current)) {
                return null;
            }
            if (terminal.test(current)) {
                return current;
            }
            current = ownerLookup.apply(current);
        }
        return null;
    }

    private static Entity ownerOf(Entity entity) {
        if (entity instanceof TraceableEntity traceable) {
            return traceable.getOwner();
        }
        if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwner();
        }
        return null;
    }

    private static FactionView view(LivingEntity entity) {
        String team = entity.getTeam() == null ? null : entity.getTeam().getName();
        java.util.UUID owner = entity instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
        java.util.UUID currentTarget = entity instanceof Mob mob && mob.getTarget() != null
                ? mob.getTarget().getUUID() : null;
        boolean playerAligned = entity instanceof Player
                || entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player;
        return new FactionView(entity.getUUID(), team, owner, entity instanceof Enemy,
                currentTarget, playerAligned);
    }
}
