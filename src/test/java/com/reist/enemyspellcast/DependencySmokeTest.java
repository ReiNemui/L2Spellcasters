package com.reist.enemyspellcast;

import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DependencySmokeTest {
    @Test
    void requiredApisAreOnTheTestClasspath() {
        assertNotNull(MobTrait.class);
        assertNotNull(SpellRegistry.class);
    }
}
