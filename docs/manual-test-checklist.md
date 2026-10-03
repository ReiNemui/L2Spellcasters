# L2 Spellcasters 1.0.0 manual compatibility checklist

Use NeoForge 1.21.1 with the exact L2 Hostility and Iron's Spells versions declared by the
mod. Record the mob ID, spell ID, Trait rank, L2 level, and relevant stack trace for every
failure. Automated tests do not replace these client-visible and load/performance checks.

- [ ] Vanilla zombie: rank 1 / level 100, one Common-or-lower spell.
- [ ] Vanilla skeleton: rank 3 / level 240, two distinct spells.
- [ ] Vanilla hostile group: no spell damage/debuff friendly fire.
- [ ] Injured hostile ally: heal or buff lands; nearby player is not helped.
- [ ] Rank 5 / level 400: three spells and maximum spell level.
- [ ] One post-mitigation hit equal to 5% max health interrupts the cast, consumes 25% of
  spell mana cost, and applies a 20-tick lockout.
- [ ] Save, unload the chunk, and restart the server: the identical loadout remains.
- [ ] `/reload` after removing one pool entry: only the removed spell is replaced.
- [ ] Iron pyromancer and a configured boss: no added casting Goal.
- [ ] 50 nearby Trait mobs: no repeated exception, unbounded log spam, or sustained tick lag.
- [ ] Default config: no summon, terrain-changing, or portal spell is selected.
- [ ] `ALLOWLIST_ONLY` with `root` and `teleport`: only those IDs appear, subject to rank rarity.
- [ ] Risk category set to `false` without an explicit ID: the risky spell remains absent.
- [ ] Explicit risky ID while its exclusion is `true`: the risky spell remains absent.
- [ ] Both risky opt-ins: the addon admits the spell and prints one unsupported warning.
- [ ] `root`/`slow` hits the chosen player even when the caster initially looks away.
- [ ] `teleport` lands outside solid blocks and above the floor.
- [ ] Firecracker/shock/fang area effects do not harm or debuff hostile allies.
- [ ] Trait removal and `general.enabled=false` remove the controller and spell-power modifier.
- [ ] 50 rank-3 casters run for several minutes without repeated warnings or sustained tick stalls.

Suggested final smoke test:

1. Run `./gradlew.bat runClient`.
2. Create a disposable world and use L2 commands or the Trait Symbol item to configure mobs.
3. Check spell telegraphs, targeting, interruption, reload, chunk unload, and world restart.
4. Repeat the 50-mob case on a dedicated server and compare tick time before and after spawn.
