package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

@FunctionalInterface
public interface CastingTelegraph {
    CastingTelegraph NONE = (caster, spell) -> { };
    CastingTelegraph SERVER = CastingTelegraph::emitServerParticles;

    void emit(Mob caster, ResolvedSpell spell);

    private static void emitServerParticles(Mob caster, ResolvedSpell spell) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        var particle = new DustParticleOptions(spell.spell().getTargetingColor(), 0.8F);
        double centerY = caster.getY() + caster.getBbHeight() * 0.65;
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2.0 * index / 8.0;
            level.sendParticles(particle,
                    caster.getX() + Math.cos(angle) * 0.6,
                    centerY,
                    caster.getZ() + Math.sin(angle) * 0.6,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
