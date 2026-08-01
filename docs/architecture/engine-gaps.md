# Clean-room engine: what the rewrite left behind

Commit `728b0d5` ("Relicense to BSD-2: replace the GPL calculator with a clean-room DPS engine") rebuilt the
damage maths from the public wiki formulas. The maths came over. The **context layer** — the target-specific
and buff-specific effects the old calculator applied by name — largely did not.

The failure mode is always the same and always silent: the weapon or buff is still a candidate
(`EffectItems` protects it from dominance pruning), but it is scored at raw stats, so it never wins and
never appears. Nothing errors. The number is just quietly wrong or the item is quietly absent.

This file is the gap list, so the gaps get prioritised rather than discovered one at a time in-game.

_Last audited: 2026-07-16._

## How to find these

Two greps do most of the work:

- An effect keyed on the target must read its attribute: `grep -c "MonsterAttribute.X" engine/`
- An effect keyed on a buff must read the field: `grep -rl "isX\|getX" engine/`

A field that exists on the model but has no reader is a dropped effect, not a design choice.

## Monster attributes — 9 of 16 read

An earlier revision of this file said "4 of 13". That was wrong twice over: the count came from a regex
(`[A-Z_]+\("[a-z]+"\)`) that silently skipped `VAMPYRE_1/2/3`, whose wire names contain digits. There are 16
real attributes, not 13. Count them with `grep -c '^\t[A-Z_0-9]*("' MonsterAttribute.java`, not by eye.

| Attribute | Status | What is missing |
|---|---|---|
| `DRAGON` | read | — (DH wand path fixed 2026-07-16) |
| `FLYING` | read | — |
| `UNDEAD` | read | salve only |
| `XERICIAN` | read | — (twisted bow cap) |
| `DEMON` | **done 2026-07-16** | melee + ranged demonbane; **Purging staff + demonbane spells still missing** |
| `KALPHITE` | **done 2026-07-16** | keris +33%; **1/51 triple-damage proc not modelled** (~4% understated) |
| `LEAFY` | **done 2026-07-16** | immunity + battleaxe 17.5%; Magic Dart now scored |
| `GOLEM` | **done 2026-07-16** | Barronite mace +15% |
| `RAT` | **done 2026-07-16** | ratbane flat +10 |
| `VAMPYRE_1/2/3` | **never read** | Blisterwood flail (+25% dmg / +5% acc), sickle, stake, Ivandis flail, Rod of ivandis, Wolfbane, silver — and the **tiered immunity**, which is the bigger half: higher-tier vampyres cannot be hurt by the wrong weapon at all |
| `SHADE` | **never read** | shade-specific weapons |
| `FIERY` | **never read** | — |
| `SPECTRAL` | **never read** | — |
| `PENANCE` | **never read** | — |

Deferred deliberately, with reasons:

- **Vampyres** are three attributes with a tiered immunity and six-plus weapons. That is its own verification
  pass, not a footnote to this one.
- **Keris partisan of amascut** is 15%, not 33% ("this variant has a damage bonus of 15%, down from the base
  weapon's 33%") — found and fixed 2026-07-16 before release. Its Tombs of Amascut stat swing is already
  handled in `Equipment`.
- **The keris 1/51 triple-damage proc** needs a custom `AttackDistribution` (the `boltProcExpectedHit`
  pattern), not a `Modifier`.

## PlayerBuffs — 6 of 10 fields have no reader

`onSlayerTask`, `kandarinDiary`, `soulreaperStacks` and `inWilderness` (wired 2026-07-16) are read. These are
not:

- `forinthrySurge`
- `chargeSpell`
- `markOfDarknessSpell` — blocks the Purging staff, which doubles its bonus under Mark of Darkness
- `usingSunfireRunes`
- `baAttackerLevel`
- `chinchompaDistance`

Each is plumbed from the panel or the model and then dropped on the floor. `inWilderness` was the worst case:
the checkbox had a tooltip promising it "powers the wilderness weapons" and did nothing at all.

## Defence-reduction inputs — 5 of 10 have no reader

`DefenceReductions` (and the Advanced panel) expose ten drains; `effectiveDefenceLevel` applies only Elder
maul (-35%), DWH (-30%), Accursed (-15%), Vulnerability (-10%), BGS (flat) and Tonalztics (wired 2026-08-01,
-12.5% of Magic level per hit). Still dropped on the floor — the panel spinner does nothing:

- `arclight`, `emberlight` — the spec drains the target's defensive stats (demon-focused). Same target as the
  wired ones, so cheap to add once the exact per-hit percentage is verified.
- `seercull` — drains the target's **Magic level**, not its Defence. Affects magic accuracy and Twisted bow
  scaling, a different roll from the melee/ranged defence path.
- `ayak` — drains the target's **Magic defence**, a third roll again.

The last two are why this is not a one-liner: three different targets. Not touched by the 2026 Summer
Sweep-Up (only Tonalztics changed, 10% -> 12.5%), so they are a pre-existing gap, not a patch regression.

## Magic

- **`poweredStaffBaseMax` — mostly done 2026-07-16.** Modelled: both tridents (and their (e)/(o) variants),
  Sanguinesti (+holy), Thammaron's (+a), Accursed (+a), Warped sceptre, Eye of ayak, Tumeken's shadow. This
  is what finally made the wilderness sceptres scorable.
  Still `-1` (score zero, never suggested):
  - **Bone staff** — the scraped formula ("⌊MagicLevel3⌋+5" → 38 at 99) contradicts the same page's claim
    that it hits *lower* than a trident of the seas (28). Left out rather than guessed. Needs a human read.
  - **Dawnbringer** — only functions against Verzik P1; a special case, not a formula.
  - **Lithic sceptre**, **Starter staff** — not looked up.
  - **Crystal / Corrupted staff** (6 entries) — deliberately not needed: Gauntlet gear is in `BisOptimizer`'s
    `UNBANKABLE` set, so it never reaches the engine.

  **Read the formulas from the staff's own page, never from the `Powered staff` page** (its table is a JS
  calculator and scrapes to nothing), and **reconcile every formula against a worked example the page states
  outright** before trusting it — the wiki writes them as stacked fractions which flatten into ambiguous text
  when scraped ("⌊8(Magic Level)+9637⌋" is really ⌊(8·magic+96)/37⌋). `PoweredStaffTest` pins each formula to
  its example for exactly this reason.
- **A spell whose `max_hit` is 0 in the data is a computed one**, not a zero-damage one. Magic Dart is the
  only one found so far (modelled 2026-07-16: `floor(magic*0.1)+10`, or `floor(magic*0.166)+13` with a
  Slayer's staff (e) on task). Re-check the data before assuming any other spell is inert.
- **Spell cast speed is not modelled.** `Equipment` takes the attack speed from the weapon, so Ice Barrage
  (5t) and Ice Blitz (4t) are scored at the same speed. Relative comparisons within a tier hold; absolute
  magic DPS does not.
- **Spell level requirements are not in the data.** `spells.json` has only name/image/max_hit/spellbook/
  element. `Spell.maxHit`'s threshold table is the only level knowledge in the codebase, and it only covers
  elemental tiers — so non-elemental spells (Ice Barrage needs 94) are offered at any level.

## Specs

`BisOptimizer.Result.spec` is always null; the panel hides the block. `Equipment` still carries the spec-cost
table, which makes names like "Arclight" *look* referenced in a naive grep — they are not.

## Stale constants (the dangerous class)

`Constants` was written against pre-2025 game rules. Jagex spent 2025 loosening melee reach, so anything in
these sets is **suspect until re-checked against the wiki**. Enforcing them verbatim introduces bugs:

- `IMMUNE_TO_NON_SALAMANDER_MELEE_DAMAGE_NPC_IDS` — obsolete since **25 June 2025** ("All flying enemies,
  including Aviansies, can now be hit by halberds"). Deliberately not consulted; enforcing it would wrongly
  zero a halberd at aviansies.
- `ZULRAH_IDS` was inside `IMMUNE_TO_MELEE_DAMAGE_NPC_IDS` — obsolete since **7 May 2025** ("Zulrah is no
  longer immune to melee attacks, although only halberds can reach it"). Removed 2026-07-16.
- Still believed correct (re-verified 2026-07-16): Kraken and Leviathan take no melee even from a halberd.

`demonbaneVulnerability` on `MonsterInputs` is never populated — the code that filled it in was the deleted
`BaseCalc.sanitizeInputs`. Duke Sucellus's 30% demonbane resistance is hardcoded in `DpsEngine` instead.

## Verification note

Tests in this repo have repeatedly passed while the plugin was wrong. Every fix above was checked by
reverting the engine and re-running the new tests to prove they fail without it. A test that passes before
and after the change is worth nothing here.

Wiki values must be looked up, never recalled. Three separate multipliers were about to be committed from
memory this session and all three were wrong: Thammaron's sceptre (remembered 100%/25%, actually 50%/50%),
the aviansie salamander rule (superseded), and Zulrah's melee immunity (superseded).
