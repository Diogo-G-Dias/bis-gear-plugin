# BiS Gear

Pick a boss and see the highest-DPS setup you can actually build **from the items in your bank** — not
the theoretical best-in-slot you do not own.

![icon](src/main/resources/icon.png)

## What it does

- Search any of the ~2,850 monsters in the game.
- Get the best setup for **each** style — melee, ranged and magic — shown as tabs, with the DPS, max hit,
  accuracy and prayer bonus for each, and the gear laid out as the worn-equipment grid.
- Only items you own are suggested: your bank (last time you opened it), your inventory and what you are
  wearing.
- Slots that make no difference to DPS are filled with your best **prayer** gear instead, at zero DPS cost.
- Right-click any suggested item to stop it being suggested (persists between sessions).

## Options

Prayer and boost default to the best for the style and are shown on the result, so a DPS figure is never
silently a boosted one — both can be overridden, including "no prayer" and raid boosts.

Advanced options cover the state of the fight: defence reductions already landed (Dragon warhammer, Elder
maul, Arclight, Emberlight, Bandos godsword, Tonalztics of ralos, Seercull, Eye of ayak), Vulnerability,
Accursed sceptre, soul stacks on a Soulreaper axe, on a slayer task, and in the wilderness. The result
states which of these it assumed.

## Where the numbers come from

The DPS engine is an independent, clean-room implementation written from publicly documented Old School
RuneScape combat formulas — the OSRS Wiki's combat pages and Bitterkoekje's combat guide. Its output is
built to match [dps.osrs.wiki](https://dps.osrs.wiki) for the setups it covers.

Item and monster data comes from the OSRS Wiki, fetched once from its published JSON (distributed via the
weirdgloop data CDN) and cached on disk under `.runelite/bis-finder/`. Item IDs are real in-game IDs, so
bank items map directly.

## Licence

BSD 2-Clause. See `LICENSE` and `NOTICE.md`.
