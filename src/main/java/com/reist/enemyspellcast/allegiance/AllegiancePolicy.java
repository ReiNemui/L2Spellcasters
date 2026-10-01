package com.reist.enemyspellcast.allegiance;

public final class AllegiancePolicy {
    private AllegiancePolicy() {
    }

    public static Allegiance resolve(FactionView caster, FactionView target) {
        if (caster.id().equals(target.id())) {
            return Allegiance.SELF;
        }
        if (caster.team() != null && caster.team().equals(target.team())) {
            return Allegiance.ALLY;
        }
        if (caster.owner() != null && (caster.owner().equals(target.owner())
                || caster.owner().equals(target.id()))) {
            return Allegiance.ALLY;
        }
        if (target.owner() != null && target.owner().equals(caster.id())) {
            return Allegiance.ALLY;
        }
        if (caster.hostile() && target.hostile()) {
            return Allegiance.ALLY;
        }
        if (target.id().equals(caster.currentTarget()) || caster.id().equals(target.currentTarget())) {
            return Allegiance.ENEMY;
        }
        if ((caster.hostile() && target.playerAligned())
                || (target.hostile() && caster.playerAligned())) {
            return Allegiance.ENEMY;
        }
        return Allegiance.NEUTRAL;
    }
}
