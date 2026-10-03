# L2 Spellcasters

NeoForge 1.21.1 addon for **L2 Hostility** and **Iron's Spells 'n Spellbooks**.
It adds the L2 trait `enemyspellcast:spell_caster` (`Spellcaster` / `魔導`) and gives
eligible hostile mobs a persistent, random spell loadout.

## Behavior

- Trait ranks 1–5 unlock 1, 1, 2, 2, and 3 active spell slots.
- Spell level and rarity rise with trait rank; rank 5 may use a spell's maximum level.
- L2 mob level scaling begins at level 100 and advances every 50 levels.
- Spell power, mana, and mana regeneration have no hard progression cap. Cast speed is
  capped at 3× and cooldown duration bottoms out at 25%.
- Damage and harmful spells cannot hit hostile allies. Healing and buff spells select
  injured hostile allies using situation-aware scoring with bounded randomness.
- A single post-mitigation hit of at least 5% maximum health interrupts a cast by default.
- Bosses, summons/minions, ownable mobs, and Iron's existing caster mobs are excluded by
  default. Entity tags and the common config can override the defaults.

The common config is generated as `enemyspellcast-common.toml`. It controls level scaling,
AI decision timing, interruption, movement while casting, continuous-spell admission, and
spell selection/allow/deny ID lists. Deny always wins over allow.

## Configuration reference

Common-config changes are read while the game is running; a restart is not required after
NeoForge reloads the file. Ticks are 1/20 second and fractions use `0.05 = 5%`.

| Key | Default | Unit / accepted range |
| --- | ---: | --- |
| `general.enabled` | `true` | master switch |
| `general.friendlyFireProtection` | `true` | hostile ally damage/debuff protection |
| `general.allowContinuousSpells` | `false` | continuous spell admission |
| `levelScaling.baseMobLevel` | `100` | L2 level, 0–2147483647 |
| `levelScaling.levelStep` | `50` | L2 levels per tier, at least 1 |
| `levelScaling.baseSpellPower` | `1.0` | final spell-power multiplier including tier growth, 0.01–1000 |
| `levelScaling.spellPowerPerTier` | `0.10` | additive multiplier, 0–1000 |
| `levelScaling.baseMaxMana` | `100.0` | mana, at least 1 |
| `levelScaling.maxManaPerTier` | `20.0` | mana, at least 0 |
| `levelScaling.baseManaRegenPerSecond` | `2.0` | mana/second, at least 0 |
| `levelScaling.manaRegenPerTier` | `0.05` | additive multiplier, 0–1000 |
| `levelScaling.castSpeedPerTier` | `0.05` | additive multiplier, 0–1000 |
| `levelScaling.castSpeedCap` | `3.0` | multiplier, at least 1 |
| `levelScaling.cooldownReductionPerTier` | `0.05` | fraction, 0–1 |
| `levelScaling.cooldownFloor` | `0.25` | multiplier, 0.01–1 |
| `behavior.decisionIntervalTicks` | `10` | ticks, 1–1200 |
| `behavior.randomScoreJitter` | `0.15` | score variation, 0–10 |
| `behavior.interruptDamageFraction` | `0.05` | max-health fraction, 0–1 |
| `behavior.interruptManaFraction` | `0.25` | spell-cost fraction, 0–1 |
| `behavior.interruptGlobalCooldownTicks` | `20` | ticks, 0–12000 |
| `behavior.castingMoveSpeed` | `0.25` | normal-speed multiplier, 0–1 |
| `behavior.minimumCastScore` | `0.20` | utility score, at least 0 |
| `behavior.supportSearchRange` | `16.0` | blocks, 0–256 |
| `behavior.excludeSummoningSpells` | `true` | experimental summon safety gate |
| `behavior.excludeTerrainChangingSpells` | `true` | experimental terrain safety gate |
| `behavior.excludePortalSpells` | `true` | experimental portal safety gate |
| `spellOverrides.selectionMode` | `DEFAULT_POOL` | `DEFAULT_POOL` or `ALLOWLIST_ONLY` |
| `spellOverrides.allowedSpellIds` | `[]` | exact candidates in `ALLOWLIST_ONLY` |
| `spellOverrides.forceAllowSpellIds` | `[]` | `DEFAULT_POOL` additions; bypass rank/rarity, but not risky-category gates |
| `spellOverrides.forceDenySpellIds` | `[]` | namespaced spell ID list; wins over allow |

`DEFAULT_POOL` uses the bundled 48-entry safe pool plus compatible datapack entries.
`ALLOWLIST_ONLY` admits only IDs in `allowedSpellIds`, still subject to Trait-rank rarity.
An empty allowlist safely produces an empty pool. Existing saved slots are retained when
their IDs remain valid; forbidden or missing IDs alone are replaced after reload.

Summoning, terrain-changing, and portal definitions are unsupported experimental options.
They require two deliberate opt-ins: set the matching `exclude...Spells` value to `false`,
then list the exact ID in `forceAllowSpellIds` (`DEFAULT_POOL`) or `allowedSpellIds`
(`ALLOWLIST_ONLY`). Iron may still reject spells that require a player or other custom state.

Datapack extension points reload with `/reload`:

- `data/<namespace>/enemyspellcast/spell_pool/*.json` adds spell candidates.
- `data/l2hostility/data_maps/l2hostility/trait/trait_data.json` overrides Trait cost,
  weight, maximum rank, and minimum L2 level. Preserve other packs' values when replacing it.
- `#enemyspellcast:spell_caster_blacklist` excludes entity types.
- `#enemyspellcast:spell_caster_force_allow` admits an otherwise excluded entity type, but
  never ownable mobs or L2 summons/minions.
- `#enemyspellcast:spell_support_blacklist` prevents an entity type from being selected as
  a heal/buff target.

For spell lists, force-deny wins over force-allow. For entity eligibility, the blacklist and
hard summon/ownership exclusions win over force-allow. Force-allow is intended to admit a
deliberate non-`Enemy` mob type, not to disable safety exclusions.

## Adding compatible spells with a datapack

Place JSON files anywhere below
`data/<your_namespace>/enemyspellcast/spell_pool/`. Files are additive and reload with
`/reload`.

```json
{
  "spells": [
    {
      "spell": "another_spell_mod:arcane_bolt",
      "weight": 10,
      "min_trait_rank": 2,
      "target": "enemy",
      "behavior": "direct",
      "min_range": 2.0,
      "max_range": 24.0,
      "requires_line_of_sight": true,
      "safety_category": "standard"
    }
  ]
}
```

Supported targets are `enemy`, `self`, and `ally`. Supported behaviors are `direct`,
`ally_self_cast`, and `target_entity`; the latter passes the AI-selected entity through
Iron's target-data API. Safety categories are `standard`, `summoning`, `terrain_change`,
and `portal`.

An experimental definition can be declared as follows, but remains inactive until both
config opt-ins described above are enabled:

```json
{
  "spells": [
    {
      "spell": "irons_spellbooks:portal",
      "weight": 5,
      "min_trait_rank": 3,
      "target": "self",
      "behavior": "direct",
      "safety_category": "portal"
    }
  ]
}
```

Direct compatibility is limited to spells safe for an arbitrary `Mob` caster. Continuous
spells are excluded by default. Detailed weight, rank, targeting, and category data belongs
in JSON; config controls which definitions may enter the runtime catalog.

Unknown, disabled, or unsupported entries are logged and skipped without preventing other
pool files from loading. Existing mobs retain saved IDs that remain valid; removed IDs are
replaced in place without rerolling the rest of their loadout.

## Development

Build with Java 21:

```powershell
.\gradlew.bat build
```

Run the native cross-mod integration tests with:

```powershell
.\gradlew.bat runGameTestServer
```

The code keeps L2 and Iron integration behind small bridge classes and progression,
selection, and allegiance rules in independently tested logic.
