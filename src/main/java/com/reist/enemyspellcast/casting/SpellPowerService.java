package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.EnemySpellCast;
import com.reist.enemyspellcast.progression.ProgressionRules.Scaling;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class SpellPowerService {
    private static final double EPSILON = 1.0e-9;
    private static final net.minecraft.resources.ResourceLocation LEVEL_MODIFIER_ID =
            EnemySpellCast.id("mob_level_spell_power");
    private static final net.minecraft.resources.ResourceLocation BASE_MODIFIER_ID =
            EnemySpellCast.id("mob_base_spell_power");

    private SpellPowerService() {
    }

    public static void apply(Mob mob, Scaling scaling) {
        var attribute = mob.getAttribute(AttributeRegistry.SPELL_POWER);
        if (attribute == null) {
            return;
        }
        update(attribute, LEVEL_MODIFIER_ID, scaling.spellPowerBonus(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        update(attribute, BASE_MODIFIER_ID, scaling.baseSpellPower() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void update(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
                               net.minecraft.resources.ResourceLocation id, double amount,
                               AttributeModifier.Operation operation) {
        AttributeModifier current = attribute.getModifier(id);
        if (Math.abs(amount) <= EPSILON) {
            if (current != null) {
                attribute.removeModifier(id);
            }
            return;
        }
        if (current != null && current.operation() == operation
                && Math.abs(current.amount() - amount) <= EPSILON) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(id);
        }
        attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    public static void remove(Mob mob) {
        var attribute = mob.getAttribute(AttributeRegistry.SPELL_POWER);
        if (attribute != null) {
            attribute.removeModifier(LEVEL_MODIFIER_ID);
            attribute.removeModifier(BASE_MODIFIER_ID);
        }
    }

    public static boolean hasModifier(Mob mob) {
        var attribute = mob.getAttribute(AttributeRegistry.SPELL_POWER);
        return attribute != null && (attribute.getModifier(LEVEL_MODIFIER_ID) != null
                || attribute.getModifier(BASE_MODIFIER_ID) != null);
    }
}
