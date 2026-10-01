package com.reist.enemyspellcast.catalog;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;

import java.util.Objects;

public record ResolvedSpell(SpellDefinition definition, AbstractSpell spell, int castLevel) {
    public ResolvedSpell {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(spell, "spell");
        if (castLevel < 1) {
            throw new IllegalArgumentException("castLevel must be positive");
        }
    }
}
