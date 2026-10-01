package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.catalog.SpellBehaviorId;
import com.reist.enemyspellcast.catalog.SpellDefinition;
import com.reist.enemyspellcast.catalog.SpellSafetyCategory;
import com.reist.enemyspellcast.catalog.TargetMode;
import com.reist.enemyspellcast.compat.iron.IronSpellBridge;
import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.loadout.SpellCasterData;
import com.reist.enemyspellcast.progression.ProgressionRules.Scaling;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCastControllerTest {
    @Test
    void successfulCastConsumesFullManaAndSetsScaledCooldown() {
        var iron = new FakeIronBridge();
        var controller = controller(iron, new AtomicBoolean(true), scaling(0.5));

        assertTrue(controller.tryStart(candidate(), null));
        controller.tick();
        controller.tick();

        assertEquals(60, controller.data().mana(), 1e-9);
        assertEquals(50, controller.data().cooldown(id()));
        assertEquals(List.of("pre", "tick", "cast", "complete:false"), iron.calls);
    }

    @Test
    void interruptUsesPartialManaAndGlobalCooldownOnce() {
        var iron = new FakeIronBridge();
        var controller = controller(iron, new AtomicBoolean(true), scaling(1.0));
        controller.tryStart(candidate(), null);

        assertTrue(controller.interrupt());
        assertFalse(controller.interrupt());

        assertEquals(90, controller.data().mana(), 1e-9);
        assertEquals(20, controller.data().globalCooldown());
        assertEquals(1, iron.calls.stream().filter("complete:true"::equals).count());
    }

    @Test
    void disappearingTargetCancelsWithoutSuccessfulCastAndCompletesOnce() {
        var targetAlive = new AtomicBoolean(true);
        var iron = new FakeIronBridge();
        var controller = controller(iron, targetAlive, scaling(1.0));
        controller.tryStart(candidate(), null);

        targetAlive.set(false);
        controller.tick();
        controller.cancelIfActive();

        assertFalse(controller.isCasting());
        assertFalse(iron.calls.contains("cast"));
        assertEquals(1, iron.calls.stream().filter("complete:true"::equals).count());
        assertEquals(100, controller.data().mana(), 1e-9);
    }

    @Test
    void updateAndCloseAreIdempotent() {
        var iron = new FakeIronBridge();
        Scaling original = scaling(1.0);
        var controller = controller(iron, new AtomicBoolean(true), original);

        assertFalse(controller.update(original, Settings.defaults()));
        assertTrue(controller.update(scaling(0.5), Settings.defaults()));

        assertTrue(controller.tryStart(candidate(), null));
        controller.close();
        controller.close();
        assertEquals(1, iron.calls.stream().filter("complete:true"::equals).count());
    }

    @Test
    void rejectedPreCastConsumesNothingAndCreatesNoSession() {
        var iron = new FakeIronBridge();
        iron.preCastAllowed = false;
        var controller = controller(iron, new AtomicBoolean(true), scaling(1.0));

        assertFalse(controller.tryStart(candidate(), null));
        assertFalse(controller.isCasting());
        assertEquals(100, controller.data().mana(), 1e-9);
        assertTrue(iron.calls.isEmpty());
    }

    @Test
    void targetEntityBehaviorIsRegistered() {
        assertTrue(SpellBehaviorRegistry.defaults().get(SpellBehaviorId.TARGET_ENTITY) != null);
    }

    private static SpellCastController controller(FakeIronBridge iron, AtomicBoolean targetAlive,
                                                   Scaling scaling) {
        return new SpellCastController(null, SpellCasterData.fresh(100), scaling,
                Settings.defaults(), iron, SpellBehaviorRegistry.defaults(),
                new FakeEnvironment(targetAlive), CastingTelegraph.NONE);
    }

    private static Scaling scaling(double cooldownMultiplier) {
        return new Scaling(0, 1, 0, 100, 1, 1, cooldownMultiplier);
    }

    private static ResolvedSpell candidate() {
        var definition = new SpellDefinition(id(), 1, 1, TargetMode.ENEMY,
                SpellBehaviorId.DIRECT, 0, 24, true, SpellSafetyCategory.STANDARD);
        return new ResolvedSpell(definition, new FakeSpell(), 1);
    }

    private static ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
    }

    private static final class FakeEnvironment implements CastEnvironment {
        private final AtomicBoolean targetAlive;

        private FakeEnvironment(AtomicBoolean targetAlive) {
            this.targetAlive = targetAlive;
        }

        @Override public boolean casterValid(Mob caster) { return true; }
        @Override public boolean targetValid(Mob caster, LivingEntity target) { return targetAlive.get(); }
        @Override public boolean inRangeAndVisible(Mob caster, LivingEntity target, ResolvedSpell spell) { return true; }
        @Override public void faceTarget(Mob caster, LivingEntity target) { }
    }

    private static final class FakeIronBridge implements IronSpellBridge {
        private final List<String> calls = new ArrayList<>();
        private boolean preCastAllowed = true;

        @Override public MagicData magicData(LivingEntity entity) { return null; }
        @Override public boolean checkPreCast(ResolvedSpell spell, LivingEntity caster, MagicData data) {
            return preCastAllowed;
        }
        @Override public void initiate(ResolvedSpell spell, LivingEntity caster, MagicData data, int duration) { }
        @Override public void preCast(ResolvedSpell spell, LivingEntity caster, MagicData data) { calls.add("pre"); }
        @Override public void castTick(ResolvedSpell spell, LivingEntity caster, MagicData data) { calls.add("tick"); }
        @Override public void cast(ResolvedSpell spell, LivingEntity caster, MagicData data) { calls.add("cast"); }
        @Override public void complete(ResolvedSpell spell, LivingEntity caster, MagicData data, boolean cancelled) {
            calls.add("complete:" + cancelled);
        }
    }

    private static final class FakeSpell extends AbstractSpell {
        @Override public ResourceLocation getSpellResource() { return id(); }
        @Override public DefaultConfig getDefaultConfig() { return null; }
        @Override public CastType getCastType() { return CastType.LONG; }
        @Override public int getManaCost(int level) { return 40; }
        @Override public int getSpellCooldown() { return 100; }
        @Override public int getCastTime(int level) { return 2; }
        @Override public int getMaxLevel() { return 10; }
        @Override public SpellRarity getRarity(int level) { return SpellRarity.COMMON; }
        @Override public boolean isEnabled() { return true; }
    }
}
