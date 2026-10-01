package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.EnemySpellCast;
import com.reist.enemyspellcast.catalog.ResolvedSpell;
import com.reist.enemyspellcast.compat.iron.IronSpellBridge;
import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.loadout.SpellCasterData;
import com.reist.enemyspellcast.progression.ProgressionRules.Scaling;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

public final class SpellCastController {
    private static final int MINIMUM_TELEGRAPH_TICKS = 1;

    private final Mob caster;
    private final IronSpellBridge iron;
    private final SpellBehaviorRegistry behaviors;
    private final CastEnvironment environment;
    private final CastingTelegraph telegraph;
    private SpellCasterData data;
    private Scaling scaling;
    private Settings settings;
    private CastSession session;
    private int remainingTicks;

    public SpellCastController(Mob caster, SpellCasterData data, Scaling scaling, Settings settings) {
        this(caster, data, scaling, settings, IronSpellBridge.nativeBridge(),
                SpellBehaviorRegistry.defaults(), CastEnvironment.nativeEnvironment(), CastingTelegraph.SERVER);
    }

    SpellCastController(Mob caster, SpellCasterData data, Scaling scaling, Settings settings,
                        IronSpellBridge iron, SpellBehaviorRegistry behaviors,
                        CastEnvironment environment, CastingTelegraph telegraph) {
        this.caster = caster;
        this.scaling = scaling;
        this.settings = settings.sanitized();
        this.data = data.sanitized(scaling.maxMana());
        this.iron = iron;
        this.behaviors = behaviors;
        this.environment = environment;
        this.telegraph = telegraph;
        if (caster != null) {
            SpellPowerService.apply(caster, scaling);
        }
    }

    public boolean tryStart(ResolvedSpell spell, LivingEntity target) {
        if (session != null || data.globalCooldown() > 0
                || data.cooldown(spell.definition().spellId()) > 0
                || spell.spell().getCastType() == CastType.CONTINUOUS
                || !environment.casterValid(caster)
                || !environment.targetValid(caster, target)
                || !environment.inRangeAndVisible(caster, target, spell)) {
            return false;
        }
        int manaCost = Math.max(0, spell.spell().getManaCost(spell.castLevel()));
        if (data.mana() < manaCost) {
            return false;
        }
        SpellBehavior behavior = behaviors.get(spell.definition().behavior());
        if (behavior == null) {
            return false;
        }
        if (spell.definition().targetMode() == com.reist.enemyspellcast.catalog.TargetMode.ENEMY) {
            environment.faceTarget(caster, target);
        }
        SpellInvocation invocation;
        try {
            invocation = behavior.createInvocation(caster, target, iron);
            if (!behavior.canStart(target, spell, invocation, iron)) {
                return false;
            }
        } catch (RuntimeException incompatible) {
            return false;
        }
        int duration = Math.max(MINIMUM_TELEGRAPH_TICKS,
                (int) Math.ceil(spell.spell().getCastTime(spell.castLevel()) / scaling.castSpeed()));
        session = new CastSession(spell, target, behavior, invocation, manaCost, duration);
        remainingTicks = duration;
        try {
            iron.initiate(spell, invocation.executionEntity(), invocation.magicData(), duration);
            behavior.prepare(target, spell, invocation, iron);
            telegraph.emit(caster, spell);
            return true;
        } catch (RuntimeException exception) {
            EnemySpellCast.LOGGER.warn("Could not start mob spell {}", spell.definition().spellId(), exception);
            finishCancelled(false);
            return false;
        }
    }

    public void tick() {
        tickPassiveState();
        CastSession active = session;
        if (active == null) {
            return;
        }
        if (!environment.casterValid(caster)
                || !environment.targetValid(caster, active.target())
                || !environment.inRangeAndVisible(caster, active.target(), active.spell())) {
            finishCancelled(false);
            return;
        }
        environment.faceTarget(caster, active.target());
        remainingTicks--;
        if (remainingTicks > 0) {
            iron.castTick(active.spell(), active.invocation().executionEntity(),
                    active.invocation().magicData());
            int elapsed = active.initialDuration() - remainingTicks;
            if (elapsed % 5 == 0) {
                telegraph.emit(caster, active.spell());
            }
            return;
        }
        finishSuccessful();
    }

    public void maintenanceTick() {
        if (session == null) {
            tickPassiveState();
        }
    }

    public boolean interrupt() {
        if (session == null) {
            return false;
        }
        finishCancelled(true);
        return true;
    }

    public void cancelIfActive() {
        if (session != null) {
            finishCancelled(false);
        }
    }

    public boolean isCasting() {
        return session != null;
    }

    public Optional<LivingEntity> currentTarget() {
        return session == null ? Optional.empty() : Optional.ofNullable(session.target());
    }

    public SpellCasterData data() {
        return data;
    }

    public boolean isAvailable(ResolvedSpell spell) {
        int manaCost = Math.max(0, spell.spell().getManaCost(spell.castLevel()));
        return session == null && data.globalCooldown() == 0
                && data.cooldown(spell.definition().spellId()) == 0
                && data.mana() >= manaCost
                && spell.spell().getCastType() != CastType.CONTINUOUS;
    }

    public double maxMana() {
        return scaling.maxMana();
    }

    public void replaceData(SpellCasterData replacement) {
        this.data = replacement.sanitized(scaling.maxMana());
    }

    public boolean update(Scaling newScaling, Settings newSettings) {
        Settings cleanSettings = newSettings.sanitized();
        if (scaling.equals(newScaling) && settings.equals(cleanSettings)) {
            return false;
        }
        this.scaling = newScaling;
        this.settings = cleanSettings;
        this.data = data.sanitized(newScaling.maxMana());
        if (caster != null) {
            SpellPowerService.apply(caster, newScaling);
        }
        return true;
    }

    public void close() {
        cancelIfActive();
        if (caster != null) {
            SpellPowerService.remove(caster);
        }
    }

    private void tickPassiveState() {
        double manaPerTick = settings.baseManaRegenPerSecond()
                * scaling.manaRegenMultiplier() / 20.0;
        data = data.tick(manaPerTick, scaling.maxMana());
    }

    private void finishSuccessful() {
        CastSession completed = detachSession();
        boolean castSucceeded = false;
        try {
            completed.behavior().cast(completed.target(), completed.spell(),
                    completed.invocation(), iron);
            castSucceeded = true;
        } catch (RuntimeException exception) {
            EnemySpellCast.LOGGER.warn("Mob spell {} failed during cast",
                    completed.spell().definition().spellId(), exception);
        } finally {
            iron.complete(completed.spell(), completed.invocation().executionEntity(),
                    completed.invocation().magicData(), !castSucceeded);
        }
        if (castSucceeded) {
            data = data.withMana(data.mana() - completed.manaCost())
                    .withCooldown(completed.spell().definition().spellId(),
                            (int) Math.ceil(completed.spell().spell().getSpellCooldown()
                                    * scaling.cooldownMultiplier()))
                    .sanitized(scaling.maxMana());
        }
    }

    private void finishCancelled(boolean interrupted) {
        CastSession cancelled = detachSession();
        iron.complete(cancelled.spell(), cancelled.invocation().executionEntity(),
                cancelled.invocation().magicData(), true);
        if (interrupted) {
            int partialMana = (int) Math.ceil(cancelled.manaCost() * settings.interruptManaFraction());
            data = data.withMana(data.mana() - partialMana)
                    .withGlobalCooldown(settings.interruptGlobalCooldownTicks())
                    .sanitized(scaling.maxMana());
        }
    }

    private CastSession detachSession() {
        CastSession detached = session;
        session = null;
        remainingTicks = 0;
        return detached;
    }
}
