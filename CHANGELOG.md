# Changelog

## 1.0.0

- Added the L2 Hostility `Spell Caster` / `魔導` Trait with ranks 1–5.
- Added persistent random loadouts with 1–3 distinct Iron's Spells spells.
- Linked spell rarity and spell level to Trait rank, including maximum spell level at rank 5.
- Added uncapped tiered spell-power, mana, and mana-regeneration scaling from L2 mob level.
- Added `levelScaling.baseSpellPower` so packs can reduce enemy spell damage without changing
  the existing default damage or per-tier growth curve.
- Added cast-speed scaling capped at 3× and cooldown scaling floored at 25%.
- Added situation-aware offensive/support selection with bounded randomness.
- Prevented hostile spell damage and debuffs from causing friendly fire while permitting
  healing and buffs on hostile allies.
- Added damage-based cast interruption with partial mana cost and a configurable lockout.
- Added common-config controls plus datapack spell pools, entity tags, and L2 Trait data-map
  extension points.
- Added native NeoForge GameTests for L2 Trait data, Iron caster exclusion, rank-5 loadout
  persistence, zero-mana fallback, and Trait removal.
- Raised the Spell Caster Trait's natural-selection weight from 30 to the common L2
  Hostility baseline of 100.
- Expanded the bundled standard spell pool to 48 candidates, including teleportation,
  restraints, self buffs, barrages, and additional area attacks.
- Added `DEFAULT_POOL` and `ALLOWLIST_ONLY` selection modes plus double-opt-in experimental
  summon, terrain-changing, and portal categories.
- Cached config and rank-indexed spell catalogs, added generation-based Mob synchronization,
  reused ally searches, and stopped rewriting unchanged spell-power modifiers every tick.
- Added complete cleanup on Trait removal, config disable, and entity unload.
- Added consistent ally casting and explicit target-data behavior for control spells.
- Fixed initial server reload ordering and generic-Mob Iron casting-state initialization.
- Fixed immediate facing for ray-targeted control spells and protected delayed owned spell
  entities from hostile-Mob friendly fire.
- Replaced `frostwave` with `firecracker` in the default pool because Frostwave applies a
  harmful effect directly without a source-aware cancellation hook.
- Removed `spectral_hammer` from the bundled default pool.
