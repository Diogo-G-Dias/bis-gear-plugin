# Third-party attribution

## Combat formulas

BiS Gear's DPS engine (`com.github.diogogdias.bisfinder.engine`) is an independent, clean-room
implementation written from publicly documented Old School RuneScape combat formulas — the OSRS
Wiki's DPS/combat pages and Bitterkoekje's combat guide. It is not derived from, and contains no
code from, any GPL-licensed calculator.

## Game data

The item, monster and spell stats the plugin uses are fetched at runtime from the Old School
RuneScape Wiki data, distributed by Weird Gloop through the osrs-dps-calc data CDN:

- https://github.com/weirdgloop/osrs-dps-calc (`cdn/json/equipment.json`, `monsters.json`,
  `spells.json`)

This is factual game data (item and monster statistics), used only to look up stats. OSRS Wiki
content is licensed CC BY-NC-SA 3.0.

## RuneLite

Built against the RuneLite client API (https://github.com/runelite/runelite).
