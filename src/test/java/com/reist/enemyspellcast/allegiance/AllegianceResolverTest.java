package com.reist.enemyspellcast.allegiance;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllegianceResolverTest {
    @Test
    void explicitSameTeamWinsOverEnemyClassification() {
        var caster = view("mob-a", "red", null, true, "player");
        var target = view("mob-b", "red", null, true, "player");
        assertEquals(Allegiance.ALLY, AllegiancePolicy.resolve(caster, target));
    }

    @Test
    void sameOwnerMakesOwnablesAllies() {
        assertEquals(Allegiance.ALLY, AllegiancePolicy.resolve(
                view("wolf-a", null, "owner", false, null),
                view("wolf-b", null, "owner", false, null)));
    }

    @Test
    void hostileMobAndPlayerAreEnemiesWithoutTeamOverride() {
        assertEquals(Allegiance.ENEMY, AllegiancePolicy.resolve(
                view("zombie", null, null, true, "player"),
                view("player", null, null, false, null)));
    }

    @Test
    void hostileMobsRemainAlliesEvenDuringVanillaInfighting() {
        assertEquals(Allegiance.ALLY, AllegiancePolicy.resolve(
                view("zombie", null, null, true, "skeleton"),
                view("skeleton", null, null, true, "zombie")));
    }

    @Test
    void ownerChainUsesProjectileOwnerAndStopsCycles() {
        Map<String, String> projectile = Map.of("projectile", "caster");
        assertEquals("caster", AllegianceResolver.resolveOwnerChain("projectile", projectile::get,
                "caster"::equals));

        Map<String, String> cycle = Map.of("a", "b", "b", "a");
        assertDoesNotThrow(() -> AllegianceResolver.resolveOwnerChain("a", cycle::get, value -> false));
        assertNull(AllegianceResolver.resolveOwnerChain("a", cycle::get, value -> false));
    }

    private static FactionView view(String id, String team, String owner,
                                    boolean hostile, String currentTarget) {
        return new FactionView(uuid(id), team, owner == null ? null : uuid(owner), hostile,
                currentTarget == null ? null : uuid(currentTarget), id.equals("player"));
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
