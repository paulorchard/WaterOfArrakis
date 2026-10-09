# IslandCraft - Water of Arrakis

Server version: 0.6.8. Naming follows the other IslandCraft mods: family `IslandCraft - Water of Arrakis`, identifier form `Water_of_Arrakis`
(config file, asset folders), code form `WaterOfArrakis`, package `com.paulorchard.islandcraft.waterofarrakis`, jar
`IslandCraft-WaterOfArrakis-<version>.jar`. In-world content is prefixed `Arrakis_`.

## Iteration loop

- `gradlew deployMod` builds the jar and replaces this mod's jar in `%APPDATA%\Hytale\UserData\Mods` (close the world first).
- `gradlew test` runs the rules tests (tiers, drain multiplier, modifiers, colour blend).
- Headless boot, no client: `tools/headless/run.sh "wait 25" "help water"`. It cannot run anything that needs a player.

## Phase 1: investigation results

Status key: *verified* = read from the 0.6.8 jar or assets, or run on the headless server. *In game: not yet seen* = needs a client.

### HUD: how a mod adds elements, and where the vanilla bars are

- A mod adds a HUD with a `CustomUIHud` subclass (`build(UICommandBuilder)` appends a `.ui` document) and `player.getHudManager().addCustomHud(playerRef, hud)`. Later changes go through `hud.update(false, builder)` (partial) with `builder.set("#Id.Property", value)` / `setObject("#Id.Anchor", anchor)`. The `.ui` file lives in the asset pack under `Common/UI/Custom/...` and textures are referenced relative to it. *verified* (classes), *in game: not yet seen* (that this document loads and renders).
- Vanilla health and stamina are **client side** (`HudComponent.Health` / `.Stamina`; `HudManager.hideHudComponents` can hide them). Their layout is in the game's own `Client/Data/Game/Interface/InGame/Hud/Health/Health.ui` and `Stamina/StaminaPanel.ui`: both are a 12 px tall group anchored `Bottom = 36 + 78 + 6 = 120`, `Width = 702` (the hotbar width: 9 slots x (74 + 4)). Health runs from the left edge (318 px bar, icon on its left), Stamina from the right edge. *verified*
- So the new bars are at `Bottom = 139` (12 px bar + 4 px gap above), same width and same left/right structure (a 7 px gap, so the 24 px icons clear the vanilla ones): water above Health, exposure above Stamina. `Arrakis_Bars.ui` mirrors the two vanilla layouts.
- **Colour:** the client `ProgressBar` has no colour property (colour comes from its texture), so each fill is a plain coloured `Group` whose `Anchor.Width` is set from the value and whose `Background` is set to a hex colour. That is how the orange to red blend is done. The property paths `#X.Anchor` and `#X.Background` are the likely form but *in game: not yet seen*. If the Background string is refused, the fallback is a few pre-coloured texture steps.
- Icons are generated 48 px placeholder PNGs (`Arrakis_Water_Icon.png`, `Arrakis_Sun_Icon.png`); replace the files and keep the names.

### Sunlight

- `WorldTimeResource.getSunlightFactor()` (0 night, 1 midday), `getSunDirection()`, `getDayProgress()`, `isDayTimeWithinRange()` exist. *verified*
- Stored sky light (`BlockSection.getGlobalLight().getSkyLight`, 0..15) is the per-block exposure to the sky; it does not change with time of day, and a section has none until lit. Not used.
- Used: sun is up (`getSunlightFactor() >= MinSunlightFactor`) AND the head is above the chunk height map (`WorldChunk.getHeight`, the y of the top block of the column). One lookup, no raycast. Leaves and any solid roof shade; a column that is not loaded counts as shade. *Whether the height map counts grass and flowers as cover is not verified*; if it does, standing in grass would read as shade (fix: raycast up and ignore non-`Solid` blocks).
- Weather and clouds: the server exposes no cloud coverage. Not considered.

### Stamina: how costs and regeneration are applied

- Stamina is an entity stat defined in `Server/Entity/Stats/Stamina.json` (max 10). **Sprinting and gliding drain it as `Regenerating` entries** (-0.1 every 0.1 s with a `Sprinting` condition); regeneration is +0.3 every 0.1 s with conditions (no wielding, not sprinting, `StaminaRegenDelay` at 0). Attacks and blocking take stamina through item interactions (`WieldingInteraction$StaminaCost`, `DamageSystems$DamageStamina`). *verified*
- **Jumping and climbing have no stamina cost in 0.6.8.** No condition class, movement setting or interaction charges for them; only sprinting drains stamina. This conflicts with the brief ("running, jumping, climbing cost 50%"): there is nothing to halve for jump and climb.
- The regen entries are asset data and not modifiable from a plugin (stat *modifiers* only change min/max). So the mod scales by watching the stat each tick: a drop while sprinting gets `(1 - costMultiplier)` of it handed back; a slow rise gets `(1 - regenMultiplier)` of it taken away (rises faster than `RegenDeltaCapPerSecond` are items, not regen, and are left alone). Attacks are untouched except for one overlap case: an attack on the same tick as a sprint is refunded too.
- Running at the normal pace (the `running` flag) costs no stamina in vanilla either, so "running" in the stamina tiers means sprinting. Water drain counts `running || sprinting`.
- To give jump and climb a stamina cost that water then scales, set `JumpStaminaCost` and `ClimbStaminaCostPerSecond` in the config (both 0, which keeps vanilla).

### Persistence

- `WaterState` is a component on the player entity, registered with `registerComponent(class, "WaterOfArrakis_State", CODEC)` (the same mechanism as `PersistentGameModeType`), so it should be written with the player. *verified* that it registers; relog persistence *in game: not yet seen*.

## Phase 1: what was built

- **Config** `Water_of_Arrakis.json` (all tunables; written with defaults on every start). Tiers are data: `WaterTierLowerBounds` {75, 50, 25} with `TierActionStaminaCost` {0, 0.5, 1, 1} and `TierStaminaRegen` {1, 1, 1, 0.5}. **Boundary rule:** a tier includes its lower bound and excludes its upper bound, tier 0 also includes 100. So exactly 75 is tier 0, 50 is tier 1, 25 is tier 2 (unit tested).
- **Simulation** (`WaterSystem`, one world tick): exposure +1.0/s in direct sun, no decay for 5 s out of the sun, then -1.0/s, nothing gained at night. Water drain 0.02/s idle, +0.03/s running or climbing, 0.05 per jump, all times `1 + floor(exposure/10) x 0.10`. Water clamps at 0 with no penalty. Creative players are frozen unless `SimulateInCreative`.
- **API** (`WaterOfArrakisPlugin.service()` returns `WaterService`): `get/set/add` for water and exposure (clamped), `getWaterTier`, `getExposureStep`, `getExposureDrainMultiplier`; named modifiers `setModifier(player, id, ModifierType, value)`, `removeModifier`, `removeModifiers(id)`, `getModifier`; `ModifierType` is `EXPOSURE_GAIN_MULTIPLIER`, `EXPOSURE_DECAY_MULTIPLIER`, `WATER_DRAIN_MULTIPLIER` (baseline), `WATER_ACTION_DRAIN_MULTIPLIER`, `EXPOSURE_OFFSET` (percent per second, negative cools); multipliers multiply across ids, the offset adds. `WaterListener`: `onWaterChanged`, `onExposureChanged`, `onWaterTierChanged`, `onExposureStepChanged`. Modifiers are not saved.
- **Commands** (operators): `/water set|add <n> [player]`, `/exposure set|add <n> [player]`, `/waterdebug [player]`.

## Test in game (Phase 1)

1. `gradlew deployMod`, start a world with the mod. Two bars should sit above Health (blue) and Stamina (orange).
2. `/exposure set 80`: the bar should be orange-red; `/exposure set 100`: solid red; `/exposure set 40`: orange.
3. `/water set 100`, `/waterdebug`; sprint on level ground: stamina should not drop (tier 0). `/water set 60`: sprint drain at half. `/water set 40`: normal. `/water set 10`: normal drain, slow regen.
4. Stand in open sun at day: `/waterdebug` says in direct sun yes and exposure climbs 1 per second. Step under a roof or wait for night: 5 s later it falls.
5. Relog: `/waterdebug` values persist.

## Phase 2: items

### Investigation (0.6.8, all *verified* in the jar or assets; none seen in game)

- **Vanilla healing:** food runs `ApplyEffect` of an entity effect in the item's `Effect` interaction variable. The instant heals are `StatModifiers: {Health: N}` with `ValueType: Percent`, `Duration 0.1` (percent of max health, so N HP at 100 max): Food_Instant_Heal_T1 5 (Egg), T2 10 (Meat Kebab), Bread 15, T3 15. Regeneration is a second effect (`Food_Health_Regen_*`: Health +1 every 2 s for 120 s at the small size), chosen by `*_TierCheck_*` interactions.
- **How an item is used up:** `Template_Food` -> `Root_Secondary_Consume_Food_T1` -> `Condition_Consume_Food_T1` -> `Consume_Charge_Food_T1_Inner` (charge, remove one from the stack, then the item's `Effect` variable). Our foods inherit `Template_Food` and only replace `Effect`.
- **Water on top of that:** a plugin can register new interaction types (`getCodecRegistry(Interaction.CODEC).register(...)`), so `Arrakis_Consume` and `Arrakis_Flask` are Java interactions usable from item JSON. The amounts live in the config, not the JSON.
- **Per-stack data:** `ItemStack` has `withMetadata(key, codec, value)`, `withMaxDurability`, `withDurability`. The Litrejon saves units as metadata `Arrakis_Water_Units` and mirrors them to durability (max 500), so it shows a bar and the amount in its tooltip. Missing metadata (a spawned flask) counts as full (`LitrejonStartsFull`).
- **Filling:** vanilla `RefillContainer` swaps an item for a "Filled_Water" state (a different item), which cannot hold 500 units. `FlaskInteraction` does its own ray from the eyes (`FillReach` 5) and reads the fluid with `WorldChunk.getFluidId`, stopping at solid blocks. Fluids `Water_Source` and `Water` exist.
- **Extension hook:** `Litrejon.addFillSource(source)`; any source that offers units fills the flask first, then the built-in water check. `Litrejon.getAmount/withAmount/isLitrejon` for other mods.

### Items (all `Server/Item/Items/Water_of_Arrakis/`, placeholder models by `Parent`/copy of the vanilla definitions)

| Id | Model | Use | Effect (config key) |
| -- | ----- | --- | ------------------- |
| `Arrakis_Litrejon` | Energy Potion (`Potion_Signature`) | 1.5 s charge | looking at water with room: fill to full; else drink min(`LitrejonDrinkAmount` 50, room left, units left); empty or full-water player: message, flask kept |
| `Arrakis_Burrow_Bulb` | Egg | eat | +`BurrowBulbWater` 25 water |
| `Arrakis_Spicebread` | Bread Dough | eat | +10 water, +10 HP at once, then +2 HP/s for 5 s |
| `Arrakis_Desert_Game` | Meat Skewer | eat | +5 water, +30 HP at once |

HP numbers against vanilla: Egg 5, Kebab 10 (+ slow regen), Bread 15, so Spicebread (10 + 10 over 5 s) is a bit above Bread and Desert Game (30) is double the Kebab. All in the config (`SpicebreadHeal`, `SpicebreadRegenPerSecond`, `SpicebreadRegenSeconds`, `DesertGameHeal`, water keys). Regeneration is done in code (`WaterSystem`), not as an entity effect, so it can be configured.

### Test in game

`/give <you> Arrakis_Litrejon` (and the other three ids). Litrejon: right-click hold with a not-full flask while looking at water fills it; otherwise it drinks 50 (`/water set 60` first). Check the durability bar and a second drink at 100 water ("not thirsty"). Foods: `/water set 50`, `/damage` yourself, eat each, `/waterdebug`.
