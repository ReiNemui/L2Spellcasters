package com.reist.enemyspellcast.catalog;

import com.reist.enemyspellcast.EnemySpellCast;
import com.reist.enemyspellcast.config.EnemySpellCastConfig.Settings;
import com.reist.enemyspellcast.progression.ProgressionRules;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Immutable, validated and rank-indexed view of the reloadable spell pool. */
public final class SpellCatalog {
    private static volatile SpellCatalog current = empty();
    private static long nextGeneration;

    private final List<SpellDefinition> sourceDefinitions;
    private final List<ResourceLocation> ids;
    private final List<SpellDefinition> definitions;
    private final List<List<ResolvedSpell>> eligibleByRank;
    private final List<Map<ResourceLocation, ResolvedSpell>> resolvedByRank;
    private final Settings settings;
    private final boolean indexingDeferred;
    private final long generation;

    private SpellCatalog(List<SpellDefinition> sourceDefinitions, List<ResourceLocation> ids,
                         List<SpellDefinition> definitions, List<List<ResolvedSpell>> eligibleByRank,
                         List<Map<ResourceLocation, ResolvedSpell>> resolvedByRank, Settings settings,
                         boolean indexingDeferred, long generation) {
        this.sourceDefinitions = List.copyOf(sourceDefinitions);
        this.ids = List.copyOf(ids);
        this.definitions = List.copyOf(definitions);
        this.eligibleByRank = List.copyOf(eligibleByRank);
        this.resolvedByRank = List.copyOf(resolvedByRank);
        this.settings = settings;
        this.indexingDeferred = indexingDeferred;
        this.generation = generation;
    }

    public static SpellCatalog snapshot() {
        return current;
    }

    static synchronized void publish(SpellCatalog catalog) {
        current = catalog.withGeneration(++nextGeneration);
    }

    public static void rebuildFromConfig(Settings settings) {
        SpellCatalog before = current;
        try {
            publish(build(before.sourceDefinitions, registryResolver(), settings));
        } catch (RuntimeException exception) {
            EnemySpellCast.LOGGER.warn("Keeping the previous enemy spell catalog after config reload failed", exception);
        }
    }

    /** Completes rank indexing after Iron's server-side rarity config becomes available. */
    public static synchronized void ensureReady() {
        SpellCatalog before = current;
        if (!before.indexingDeferred) {
            return;
        }
        try {
            SpellCatalog rebuilt = build(before.sourceDefinitions, registryResolver(), before.settings);
            if (!rebuilt.indexingDeferred) {
                publish(rebuilt);
            }
        } catch (RuntimeException exception) {
            EnemySpellCast.LOGGER.debug("Enemy spell rarity index is not ready yet", exception);
        }
    }

    public static SpellCatalog build(List<SpellDefinition> definitions, SpellResolver resolver, Settings settings) {
        return build(definitions, resolver, settings,
                message -> EnemySpellCast.LOGGER.warn("{}", message));
    }

    static SpellCatalog build(List<SpellDefinition> definitions, SpellResolver resolver, Settings rawSettings,
                              Consumer<String> warningSink) {
        Settings settings = rawSettings.sanitized();
        SpellSelectionPolicy policy = SpellSelectionPolicy.compile(settings, warningSink);

        var unique = new LinkedHashMap<ResourceLocation, SpellDefinition>();
        for (SpellDefinition definition : definitions) {
            unique.putIfAbsent(definition.spellId(), definition);
        }

        var accepted = new ArrayList<Candidate>();
        for (SpellDefinition definition : unique.values()) {
            if (!policy.includes(definition)) {
                continue;
            }
            ResourceLocation id = definition.spellId();
            try {
                AbstractSpell spell = resolver.resolve(id);
                if (spell == null) {
                    warningSink.accept("Ignoring unknown spell in enemy spell pool: " + id);
                } else if (!spell.isEnabled()) {
                    warningSink.accept("Ignoring disabled spell in enemy spell pool: " + id);
                } else if (spell.getCastType() == CastType.CONTINUOUS && !settings.allowContinuousSpells()) {
                    warningSink.accept("Ignoring continuous spell in enemy spell pool: " + id);
                } else {
                    if (definition.safetyCategory() != SpellSafetyCategory.STANDARD) {
                        warningSink.accept("Admitting unsupported experimental enemy spell " + id
                                + " in category " + definition.safetyCategory().getSerializedName());
                    }
                    accepted.add(new Candidate(definition, spell));
                }
            } catch (RuntimeException exception) {
                warningSink.accept("Ignoring invalid spell in enemy spell pool: " + id + " ("
                        + exception.getMessage() + ")");
            }
        }

        var catalogIds = accepted.stream().map(candidate -> candidate.definition().spellId()).toList();
        var catalogDefinitions = accepted.stream().map(Candidate::definition).toList();
        var eligibleByRank = new ArrayList<List<ResolvedSpell>>(ProgressionRules.MAX_TRAIT_RANK + 1);
        var resolvedByRank = new ArrayList<Map<ResourceLocation, ResolvedSpell>>(ProgressionRules.MAX_TRAIT_RANK + 1);
        eligibleByRank.add(List.of());
        resolvedByRank.add(Map.of());
        boolean indexingDeferred = false;
        try {
            for (int rank = 1; rank <= ProgressionRules.MAX_TRAIT_RANK; rank++) {
                var eligible = new ArrayList<ResolvedSpell>();
                var byId = new LinkedHashMap<ResourceLocation, ResolvedSpell>();
                var rarityCap = ProgressionRules.rarityCap(rank);
                for (Candidate candidate : accepted) {
                    SpellDefinition definition = candidate.definition();
                    AbstractSpell spell = candidate.spell();
                    int castLevel = ProgressionRules.spellLevel(spell.getMaxLevel(), rank);
                    boolean forced = policy.bypassesProgression(definition.spellId());
                    if (forced || definition.minTraitRank() <= rank
                            && spell.getRarity(castLevel).getValue() <= rarityCap.getValue()) {
                        var resolved = new ResolvedSpell(definition, spell, castLevel);
                        eligible.add(resolved);
                        byId.put(definition.spellId(), resolved);
                    }
                }
                eligibleByRank.add(List.copyOf(eligible));
                resolvedByRank.add(Map.copyOf(byId));
            }
        } catch (IllegalStateException configNotReady) {
            if (!isConfigNotReady(configNotReady)) {
                throw configNotReady;
            }
            indexingDeferred = true;
            eligibleByRank.clear();
            resolvedByRank.clear();
            for (int rank = 0; rank <= ProgressionRules.MAX_TRAIT_RANK; rank++) {
                eligibleByRank.add(List.of());
                resolvedByRank.add(Map.of());
            }
            warningSink.accept("Deferring enemy spell rarity index until Iron's server config is loaded");
        }

        return new SpellCatalog(List.copyOf(unique.values()), catalogIds, catalogDefinitions,
                eligibleByRank, resolvedByRank, settings, indexingDeferred, 0L);
    }

    private static boolean isConfigNotReady(IllegalStateException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String normalized = message.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("config")
                    && (normalized.contains("not loaded")
                    || normalized.contains("before config is loaded"))) {
                return true;
            }
        }
        return false;
    }

    public List<ResourceLocation> ids() {
        return ids;
    }

    public List<SpellDefinition> definitions() {
        return definitions;
    }

    public List<ResolvedSpell> eligible(int traitRank) {
        if (traitRank < 1) {
            return List.of();
        }
        return eligibleByRank.get(Math.min(ProgressionRules.MAX_TRAIT_RANK, traitRank));
    }

    public Optional<ResolvedSpell> find(int traitRank, ResourceLocation id) {
        if (traitRank < 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolvedByRank
                .get(Math.min(ProgressionRules.MAX_TRAIT_RANK, traitRank)).get(id));
    }

    public long generation() {
        return generation;
    }

    public static SpellResolver registryResolver() {
        return id -> {
            AbstractSpell spell = SpellRegistry.getSpell(id);
            return spell == SpellRegistry.none() ? null : spell;
        };
    }

    private SpellCatalog withGeneration(long value) {
        return new SpellCatalog(sourceDefinitions, ids, definitions, eligibleByRank, resolvedByRank,
                settings, indexingDeferred, value);
    }

    private static SpellCatalog empty() {
        var emptyRanks = new ArrayList<List<ResolvedSpell>>(ProgressionRules.MAX_TRAIT_RANK + 1);
        var emptyIndexes = new ArrayList<Map<ResourceLocation, ResolvedSpell>>(ProgressionRules.MAX_TRAIT_RANK + 1);
        for (int rank = 0; rank <= ProgressionRules.MAX_TRAIT_RANK; rank++) {
            emptyRanks.add(List.of());
            emptyIndexes.add(Map.of());
        }
        return new SpellCatalog(List.of(), List.of(), List.of(), emptyRanks, emptyIndexes,
                Settings.defaults(), false, 0L);
    }

    private record Candidate(SpellDefinition definition, AbstractSpell spell) {
    }

    @FunctionalInterface
    public interface SpellResolver {
        AbstractSpell resolve(ResourceLocation id);
    }
}
