package com.reist.enemyspellcast.gametest;

import com.reist.enemyspellcast.EnemySpellCast;
import com.reist.enemyspellcast.ai.SpellGoalInstaller;
import com.reist.enemyspellcast.allegiance.AllegianceResolver;
import com.reist.enemyspellcast.catalog.SpellCatalog;
import com.reist.enemyspellcast.casting.CastRuntimeRegistry;
import com.reist.enemyspellcast.casting.SpellPowerService;
import com.reist.enemyspellcast.casting.SpellCastController;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import com.reist.enemyspellcast.config.SpellSelectionMode;
import com.reist.enemyspellcast.loadout.SpellCasterDataStore;
import com.reist.enemyspellcast.progression.ProgressionRules.Scaling;
import com.reist.enemyspellcast.trait.ModTraits;
import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(EnemySpellCast.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnemySpellCastGameTests {
    private EnemySpellCastGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void rankFiveGetsThreePersistentSpells(GameTestHelper helper) {
        Zombie zombie = spawnCaster(helper, 400, 5);
        helper.runAfterDelay(20, () -> {
            var before = SpellCasterDataStore.load(zombie, 220.0);
            helper.assertValueEqual(before.activeLoadout(5).size(), 3, "rank 5 active spell slots");

            CompoundTag saved = new CompoundTag();
            zombie.saveWithoutId(saved);
            Zombie copy = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertValueEqual(copy != null, true, "zombie copy creation");
            copy.load(saved);
            helper.assertValueEqual(SpellCasterDataStore.load(copy, 220.0).loadout(),
                    before.loadout(), "loadout NBT round trip");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void configuredBaseSpellPowerScalesExistingModifiers(GameTestHelper helper) {
        Zombie control = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie reduced = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ResourceLocation externalModifierId = EnemySpellCast.id("gametest_external_spell_power");
        control.getAttribute(AttributeRegistry.SPELL_POWER).addTransientModifier(
                new AttributeModifier(externalModifierId, 0.50,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        reduced.getAttribute(AttributeRegistry.SPELL_POWER).addTransientModifier(
                new AttributeModifier(externalModifierId, 0.50,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        double existingPower = control.getAttributeValue(AttributeRegistry.SPELL_POWER);

        SpellPowerService.apply(reduced, new Scaling(0, 0.70, 0.0,
                100.0, 1.0, 1.0, 1.0));

        double actual = reduced.getAttributeValue(AttributeRegistry.SPELL_POWER);
        helper.assertValueEqual(actual, existingPower * 0.70,
                "configured base spell power scales the complete cross-mod attribute");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void traitDataAndIronCasterExclusionAreLoaded(GameTestHelper helper) {
        helper.assertValueEqual(ModTraits.SPELL_CASTER.get()
                .getMaxLevel(helper.getLevel().registryAccess()), 5, "trait max rank");
        Mob pyromancer = EntityRegistry.PYROMANCER.get().create(helper.getLevel());
        helper.assertValueEqual(pyromancer != null, true, "pyromancer creation");
        helper.assertValueEqual(ModTraits.SPELL_CASTER.get().allow(pyromancer, 400, 5),
                false, "Iron caster blacklist");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void zeroManaLeavesNormalTargetingAvailable(GameTestHelper helper) {
        Zombie zombie = spawnCaster(helper, 100, 1);
        var controller = CastRuntimeRegistry.get(zombie).orElseThrow();
        controller.replaceData(controller.data().withMana(0.0));

        IronGolem target = enemyTarget(helper, new BlockPos(2, 1, 4));
        zombie.setTarget(target);

        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(controller.isCasting(), false, "zero mana prevents casting");
            helper.assertValueEqual(zombie.getTarget(), target, "vanilla target remains available");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void removingTraitDisablesInstalledGoal(GameTestHelper helper) {
        Zombie zombie = spawnCaster(helper, 240, 3);
        var controller = CastRuntimeRegistry.get(zombie).orElseThrow();
        MobTraitCap cap = LHMiscs.MOB.type().getOrCreate(zombie);
        cap.setTrait(ModTraits.SPELL_CASTER.get(), 0);
        cap.tick(zombie);

        helper.assertValueEqual(cap.getTraitLevel(ModTraits.SPELL_CASTER.get()), 0,
                "removed trait rank");
        helper.assertValueEqual(controller.isCasting(), false, "removed trait has no active cast");
        helper.assertValueEqual(CastRuntimeRegistry.get(zombie).isEmpty(), true,
                "controller removed");
        helper.assertValueEqual(SpellGoalInstaller.isInstalled(zombie), false,
                "goal state removed");
        helper.assertValueEqual(SpellPowerService.hasModifier(zombie), false,
                "spell power removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realIronOffensiveCastCompletes(GameTestHelper helper) {
        Zombie zombie = spawnCaster(helper, 400, 5);
        IronGolem target = enemyTarget(helper, new BlockPos(2, 1, 4));
        ResourceLocation firebolt = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
        ForcedCast cast = forceStart(helper, zombie, target, 5, firebolt);
        completeCast(helper, cast.controller());
        helper.assertValueEqual(cast.controller().data().mana() < cast.manaBefore(), true,
                "successful cast consumes mana");
        helper.assertValueEqual(cast.controller().data().cooldown(firebolt) > 0, true,
                "successful cast applies cooldown");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void targetEntitySpellUsesSelectedTarget(GameTestHelper helper) {
        Zombie caster = spawnCaster(helper, new BlockPos(10, 1, 6), 400, 5);
        caster.setYRot(180.0F);
        caster.setYHeadRot(180.0F);
        caster.setYBodyRot(180.0F);
        IronGolem selected = enemyTarget(helper, new BlockPos(10, 1, 10));
        Zombie bystander = helper.spawn(EntityType.ZOMBIE, new BlockPos(11, 1, 10));
        bystander.setNoAi(true);

        ForcedCast cast = forceStart(helper, caster, selected, 5, spellId("root"));
        completeCast(helper, cast.controller());
        helper.assertValueEqual(selected.isPassenger(), true, "selected target is rooted");
        helper.assertValueEqual(bystander.isPassenger(), false, "nearby hostile ally is not rooted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void allySupportUsesAllyCastingContext(GameTestHelper helper) {
        Zombie caster = spawnCaster(helper, 400, 5);
        Zombie ally = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        ally.setNoAi(true);
        ally.setHealth(ally.getMaxHealth() * 0.25F);
        float allyBefore = ally.getHealth();
        IronGolem enemy = enemyTarget(helper, new BlockPos(4, 1, 2));
        enemy.setHealth(enemy.getMaxHealth() * 0.25F);
        float enemyBefore = enemy.getHealth();

        ForcedCast cast = forceStart(helper, caster, ally, 5, spellId("heal"));
        completeCast(helper, cast.controller());
        helper.assertValueEqual(ally.getHealth() > allyBefore, true, "selected ally is healed");
        helper.assertValueEqual(enemy.getHealth(), enemyBefore, "enemy is not healed by ally support");
        helper.assertValueEqual(MagicData.getPlayerMagicData(ally).isCasting(), false,
                "ally casting state resets");
        helper.assertValueEqual(MagicData.getPlayerMagicData(caster).isCasting(), false,
                "caster casting state remains reset");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void teleportEndsInCollisionFreeSpace(GameTestHelper helper) {
        Zombie caster = spawnCaster(helper, new BlockPos(10, 1, 4), 400, 5);
        caster.setNoAi(true);
        IronGolem target = enemyTarget(helper, new BlockPos(10, 1, 10));
        var before = caster.position();

        ForcedCast cast = forceStart(helper, caster, target, 5, spellId("teleport"));
        completeCast(helper, cast.controller());
        helper.assertValueEqual(caster.position().distanceToSqr(before) > 1.0, true,
                "teleport changes caster position");
        helper.assertValueEqual(helper.getLevel().noCollision(caster), true,
                "teleport destination has no block collision");
        helper.assertValueEqual(caster.getY() > helper.absolutePos(BlockPos.ZERO).getY(), true,
                "teleport destination remains above the floor");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void shockwaveDoesNotDamageHostileAllies(GameTestHelper helper) {
        Zombie caster = spawnCaster(helper, new BlockPos(10, 1, 10), 400, 5);
        caster.setNoAi(true);
        Zombie ally = helper.spawn(EntityType.ZOMBIE, new BlockPos(11, 1, 10));
        ally.setNoAi(true);
        IronGolem target = enemyTarget(helper, new BlockPos(10, 1, 12));
        caster.setTarget(target);
        float allyBefore = ally.getHealth();
        float targetBefore = target.getHealth();
        helper.assertValueEqual(target.isAlive() && !target.isRemoved(), true, "shockwave target valid");
        helper.assertValueEqual(caster.hasLineOfSight(target), true, "shockwave target visible");
        helper.assertValueEqual(AllegianceResolver.isHostileTarget(caster, target), true,
                "shockwave victim is hostile target");

        ForcedCast cast = forceStart(helper, caster, caster, 5, spellId("shockwave"));
        completeCast(helper, cast.controller());
        helper.assertValueEqual(ally.getHealth(), allyBefore, "hostile ally health is protected");
        helper.assertValueEqual(target.getHealth() < targetBefore, true, "enemy receives shockwave damage");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void delayedFangsDoNotDamageHostileAllies(GameTestHelper helper) {
        Zombie caster = spawnCaster(helper, new BlockPos(10, 1, 10), 400, 5);
        caster.setNoAi(true);
        Zombie ally = helper.spawn(EntityType.ZOMBIE, new BlockPos(9, 1, 11));
        ally.setNoAi(true);
        IronGolem target = enemyTarget(helper, new BlockPos(10, 1, 12));
        caster.setTarget(target);
        float allyBefore = ally.getHealth();
        float targetBefore = target.getHealth();

        ForcedCast cast = forceStart(helper, caster, caster, 5, spellId("fang_ward"));
        completeCast(helper, cast.controller());
        helper.runAfterDelay(12, () -> {
            helper.assertValueEqual(CastRuntimeRegistry.get(caster).isPresent(), true,
                    "delayed spell owner remains managed");
            helper.assertValueEqual(allyBefore - ally.getHealth() < 2.0F, true,
                    "delayed fangs deal no spell-sized damage to hostile allies");
            helper.assertValueEqual(targetBefore - target.getHealth() > 2.0F, true,
                    "delayed fangs damage the hostile target");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "config_cleanup", timeoutTicks = 80)
    public static void disablingConfigCancelsAndCleansCaster(GameTestHelper helper) {
        Zombie caster = spawnCaster(helper, 400, 5);
        var controller = CastRuntimeRegistry.get(caster).orElseThrow();
        try {
            ForcedCast cast = forceStart(helper, caster, caster, 5, spellId("fang_ward"));
            helper.assertValueEqual(controller.isCasting(), true, "long spell begins casting");
            SpellGoalInstaller.onConfigReload(EnemySpellCastConfig.Settings.defaults().withEnabled(false));
            helper.assertValueEqual(controller.isCasting(), false, "config disable cancels cast");
            helper.assertValueEqual(controller.data().mana(), cast.manaBefore(),
                    "config cancellation does not charge mana");
            helper.assertValueEqual(CastRuntimeRegistry.get(caster).isEmpty(), true,
                    "config disable removes controller");
            helper.assertValueEqual(SpellPowerService.hasModifier(caster), false,
                    "config disable removes spell power");
            helper.succeed();
        } finally {
            SpellGoalInstaller.onConfigReload(EnemySpellCastConfig.Settings.defaults());
        }
    }

    @GameTest(template = "empty", batch = "experimental_failure", timeoutTicks = 80)
    public static void unsupportedRecallFailsWithoutCost(GameTestHelper helper) {
        ResourceLocation recall = spellId("recall");
        var experimental = EnemySpellCastConfig.Settings.defaults()
                .withSpellOverrides(SpellSelectionMode.DEFAULT_POOL, List.of(),
                        List.of(recall.toString()), List.of())
                .withCategoryExclusions(true, true, false);
        try {
            SpellCatalog.rebuildFromConfig(experimental);
            Zombie caster = spawnCaster(helper, 400, 5);
            var controller = CastRuntimeRegistry.get(caster).orElseThrow();
            var resolved = SpellCatalog.snapshot().find(5, recall).orElseThrow();
            controller.replaceData(controller.data().withMana(controller.maxMana()));
            double manaBefore = controller.data().mana();
            helper.assertValueEqual(controller.tryStart(resolved, caster), false,
                    "player-only recall is rejected for a zombie");
            helper.assertValueEqual(controller.data().mana(), manaBefore,
                    "rejected spell does not consume mana");
            helper.assertValueEqual(controller.data().cooldown(recall), 0,
                    "rejected spell does not apply cooldown");
            helper.succeed();
        } finally {
            SpellCatalog.rebuildFromConfig(EnemySpellCastConfig.Settings.defaults());
        }
    }

    private static Zombie spawnCaster(GameTestHelper helper, int mobLevel, int traitRank) {
        return spawnCaster(helper, new BlockPos(2, 1, 2), mobLevel, traitRank);
    }

    private static Zombie spawnCaster(GameTestHelper helper, BlockPos position,
                                      int mobLevel, int traitRank) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, position);
        MobTraitCap cap = LHMiscs.MOB.type().getOrCreate(zombie);
        cap.lv = mobLevel;
        cap.setTrait(ModTraits.SPELL_CASTER.get(), traitRank);
        cap.tick(zombie);
        return zombie;
    }

    private static ForcedCast forceStart(GameTestHelper helper, Zombie caster,
                                         net.minecraft.world.entity.LivingEntity target,
                                         int traitRank, ResourceLocation spellId) {
        SpellCatalog.ensureReady();
        var resolved = SpellCatalog.snapshot().find(traitRank, spellId).orElseThrow();
        var controller = CastRuntimeRegistry.get(caster).orElseThrow();
        var forcedData = controller.data().withLoadout(List.of(spellId)).withMana(controller.maxMana());
        controller.replaceData(forcedData);
        SpellCasterDataStore.save(caster, forcedData, controller.maxMana());
        if (target != caster) {
            caster.setTarget(target);
        }
        double manaBefore = controller.data().mana();
        helper.assertValueEqual(controller.tryStart(resolved, target), true,
                spellId + " starts through the real Iron lifecycle");
        return new ForcedCast(controller, manaBefore);
    }

    private static void completeCast(GameTestHelper helper, SpellCastController controller) {
        for (int tick = 0; tick < 200 && controller.isCasting(); tick++) {
            controller.tick();
        }
        helper.assertValueEqual(controller.isCasting(), false, "spell completes within 200 ticks");
    }

    private static ResourceLocation spellId(String path) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", path);
    }

    private static IronGolem enemyTarget(GameTestHelper helper, BlockPos position) {
        IronGolem target = helper.spawn(EntityType.IRON_GOLEM, position);
        target.setNoAi(true);
        return target;
    }

    private record ForcedCast(SpellCastController controller, double manaBefore) {
    }
}
