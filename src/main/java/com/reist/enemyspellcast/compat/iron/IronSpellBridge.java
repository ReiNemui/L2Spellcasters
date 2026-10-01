package com.reist.enemyspellcast.compat.iron;

import com.reist.enemyspellcast.catalog.ResolvedSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.LivingEntity;

/** All direct calls into Iron's Spells' casting lifecycle live behind this boundary. */
public interface IronSpellBridge {
    MagicData magicData(LivingEntity entity);

    boolean checkPreCast(ResolvedSpell spell, LivingEntity caster, MagicData data);

    void initiate(ResolvedSpell spell, LivingEntity caster, MagicData data, int duration);

    void preCast(ResolvedSpell spell, LivingEntity caster, MagicData data);

    void castTick(ResolvedSpell spell, LivingEntity caster, MagicData data);

    void cast(ResolvedSpell spell, LivingEntity caster, MagicData data);

    void complete(ResolvedSpell spell, LivingEntity caster, MagicData data, boolean cancelled);

    static IronSpellBridge nativeBridge() {
        return Native.INSTANCE;
    }

    enum Native implements IronSpellBridge {
        INSTANCE;

        @Override
        public MagicData magicData(LivingEntity entity) {
            return MagicData.getPlayerMagicData(entity);
        }

        @Override
        public boolean checkPreCast(ResolvedSpell spell, LivingEntity caster, MagicData data) {
            return spell.spell().checkPreCastConditions(caster.level(), spell.castLevel(), caster, data);
        }

        @Override
        public void initiate(ResolvedSpell spell, LivingEntity caster, MagicData data, int duration) {
            // Iron's generic LivingEntity attachment creates MagicData without the
            // SyncedSpellData that its own caster Mob constructor installs.
            data.getSyncedData();
            data.initiateCast(spell.spell(), spell.castLevel(), duration,
                    CastSource.MOB, SpellSelectionManager.MAINHAND);
        }

        @Override
        public void preCast(ResolvedSpell spell, LivingEntity caster, MagicData data) {
            spell.spell().onServerPreCast(caster.level(), spell.castLevel(), caster, data);
        }

        @Override
        public void castTick(ResolvedSpell spell, LivingEntity caster, MagicData data) {
            spell.spell().onServerCastTick(caster.level(), spell.castLevel(), caster, data);
        }

        @Override
        public void cast(ResolvedSpell spell, LivingEntity caster, MagicData data) {
            spell.spell().onCast(caster.level(), spell.castLevel(), caster, CastSource.MOB, data);
        }

        @Override
        public void complete(ResolvedSpell spell, LivingEntity caster, MagicData data, boolean cancelled) {
            try {
                spell.spell().onServerCastComplete(caster.level(), spell.castLevel(), caster, data, cancelled);
            } finally {
                data.resetCastingState();
            }
        }
    }
}
