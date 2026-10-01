package com.reist.enemyspellcast.casting;

import com.reist.enemyspellcast.catalog.SpellBehaviorId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class SpellBehaviorRegistry {
    private static final SpellBehaviorRegistry DEFAULTS = createDefaults();
    private final Map<SpellBehaviorId, SpellBehavior> behaviors;

    public SpellBehaviorRegistry(Map<SpellBehaviorId, SpellBehavior> behaviors) {
        var copy = new EnumMap<SpellBehaviorId, SpellBehavior>(SpellBehaviorId.class);
        copy.putAll(behaviors);
        this.behaviors = Collections.unmodifiableMap(copy);
    }

    public static SpellBehaviorRegistry defaults() {
        return DEFAULTS;
    }

    public SpellBehavior get(SpellBehaviorId id) {
        return behaviors.get(id);
    }

    private static SpellBehaviorRegistry createDefaults() {
        var defaults = new EnumMap<SpellBehaviorId, SpellBehavior>(SpellBehaviorId.class);
        defaults.put(SpellBehaviorId.DIRECT, new DirectSpellBehavior());
        defaults.put(SpellBehaviorId.ALLY_SELF_CAST, new AllySelfCastBehavior());
        defaults.put(SpellBehaviorId.TARGET_ENTITY, new TargetEntitySpellBehavior());
        return new SpellBehaviorRegistry(defaults);
    }
}
