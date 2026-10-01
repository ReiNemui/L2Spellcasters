package com.reist.enemyspellcast.ai;

import com.reist.enemyspellcast.catalog.SpellCatalog;
import com.reist.enemyspellcast.casting.CastRuntimeRegistry;
import com.reist.enemyspellcast.casting.SpellCastController;
import com.reist.enemyspellcast.compat.l2.L2HostilityBridge;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.loadout.LoadoutService;
import com.reist.enemyspellcast.loadout.SpellCasterDataStore;
import com.reist.enemyspellcast.progression.ProgressionRules;
import com.reist.enemyspellcast.targeting.SpellScorer;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.WeakHashMap;

public final class SpellGoalInstaller {
    private static final WeakHashMap<Mob, Installed> INSTALLED = new WeakHashMap<>();
    private static final LoadoutService LOADOUTS = new LoadoutService();

    private SpellGoalInstaller() {
    }

    public static synchronized void sync(Mob mob, int rank) {
        Settings settings = EnemySpellCastConfig.snapshot();
        if (rank <= 0 || !settings.enabled() || mob.isRemoved()) {
            remove(mob);
            return;
        }

        int mobLevel = L2HostilityBridge.mobLevel(mob);
        SpellCatalog.ensureReady();
        SpellCatalog catalog = SpellCatalog.snapshot();
        var next = new InstallFingerprint(rank, mobLevel, EnemySpellCastConfig.generation(),
                catalog.generation());
        Installed existing = INSTALLED.get(mob);
        if (existing != null && existing.fingerprint().equals(next)) {
            return;
        }

        var scaling = ProgressionRules.forMobLevel(mobLevel, settings);
        if (existing != null) {
            existing.goal().controller().update(scaling, settings);
            InstallFingerprint previous = existing.fingerprint();
            if (previous.traitRank() != rank || previous.catalogGeneration() != catalog.generation()) {
                var reconciled = LOADOUTS.reconcile(existing.goal().controller().data(), rank,
                        catalog, mob.getRandom()::nextDouble);
                existing.goal().controller().replaceData(reconciled);
                SpellCasterDataStore.save(mob, reconciled, scaling.maxMana());
            }
            INSTALLED.put(mob, new Installed(existing.goal(), next));
            return;
        }

        var loaded = SpellCasterDataStore.load(mob, scaling.maxMana());
        var data = LOADOUTS.reconcile(loaded, rank, catalog, mob.getRandom()::nextDouble);
        SpellCasterDataStore.save(mob, data, scaling.maxMana());
        var controller = CastRuntimeRegistry.getOrCreate(mob,
                () -> new SpellCastController(mob, data, scaling, settings));
        var goal = new SpellCasterGoal(mob, controller, new SpellScorer());
        mob.goalSelector.addGoal(2, goal);
        INSTALLED.put(mob, new Installed(goal, next));
    }

    public static synchronized void remove(Mob mob) {
        Installed installed = INSTALLED.remove(mob);
        if (installed != null) {
            mob.goalSelector.removeGoal(installed.goal());
        }
        CastRuntimeRegistry.remove(mob);
    }

    public static synchronized void onConfigReload(Settings settings) {
        if (!settings.enabled()) {
            List.copyOf(INSTALLED.keySet()).forEach(SpellGoalInstaller::remove);
        }
    }

    public static synchronized boolean isInstalled(Mob mob) {
        return INSTALLED.containsKey(mob);
    }

    private record Installed(SpellCasterGoal goal, InstallFingerprint fingerprint) {
    }
}
