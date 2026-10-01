package com.reist.enemyspellcast.ai;

import com.reist.enemyspellcast.compat.l2.L2HostilityBridge;
import com.reist.enemyspellcast.config.EnemySpellCastConfig;
import com.reist.enemyspellcast.casting.SpellCastController;
import com.reist.enemyspellcast.loadout.SpellCasterData;
import com.reist.enemyspellcast.loadout.SpellCasterDataStore;
import com.reist.enemyspellcast.targeting.SpellScorer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public final class SpellCasterGoal extends Goal {
    private final Mob mob;
    private final SpellCastController controller;
    private final SpellScorer scorer;
    private int decisionCooldown;
    private int saveCooldown;
    private SpellCasterData lastSaved;

    public SpellCasterGoal(Mob mob, SpellCastController controller, SpellScorer scorer) {
        this.mob = mob;
        this.controller = controller;
        this.scorer = scorer;
        this.lastSaved = controller.data();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        controller.maintenanceTick();
        persist(false);
        int rank = L2HostilityBridge.traitRank(mob);
        if (rank <= 0 || !EnemySpellCastConfig.snapshot().enabled()) {
            controller.cancelIfActive();
            return false;
        }
        if (decisionCooldown-- > 0) {
            return false;
        }
        var settings = EnemySpellCastConfig.snapshot();
        decisionCooldown = settings.decisionIntervalTicks();
        return scorer.choose(mob, controller, rank, settings, mob.getRandom()::nextDouble)
                .map(candidate -> controller.tryStart(candidate.spell(), candidate.target()))
                .orElse(false);
    }

    @Override
    public boolean canContinueToUse() {
        boolean valid = controller.isCasting() && L2HostilityBridge.traitRank(mob) > 0
                && EnemySpellCastConfig.snapshot().enabled();
        if (!valid) {
            controller.cancelIfActive();
        }
        return valid;
    }

    @Override
    public void tick() {
        controller.tick();
        controller.currentTarget().ifPresent(target ->
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F));
        mob.getNavigation().setSpeedModifier(EnemySpellCastConfig.snapshot().castingMoveSpeed());
        persist(!controller.isCasting());
    }

    @Override
    public void stop() {
        controller.cancelIfActive();
        persist(true);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public SpellCastController controller() {
        return controller;
    }

    private void persist(boolean force) {
        SpellCasterData current = controller.data();
        if (!current.equals(lastSaved) && (force || saveCooldown-- <= 0)) {
            SpellCasterDataStore.save(mob, current, controller.maxMana());
            lastSaved = current;
            saveCooldown = 20;
        }
    }
}
