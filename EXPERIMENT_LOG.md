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

### Sunlight (rewritten: shade rays toward the sun)

Superseded the first version (sun up AND nothing above the head by the height map), which ignored the sun's direction. It is still available with `UseShadeRays` false.

**Answers to the three unknowns** (measured on the headless server with `/time set <hour>` then `/sunprobe time`, which prints the raw value; *verified*, but not compared with the sun in game):

| Clock | Sunlight factor | `getSunDirection()` raw | Ray toward the sun | Elevation |
| ----- | --------------- | ----------------------- | ------------------ | --------- |
| 4.0 | 0.00 | 0.560 -0.645 -0.147 | -0.647 0.744 0.170 | 48 (night: the vector is the moon's) |
| 5.0 | 0.254 | -0.647 -0.409 -0.029 | 0.845 0.534 0.038 | 32 |
| 5.5 | 0.385 | -0.614 -0.541 -0.095 | 0.745 0.657 0.116 | 41 |
| 6.0 | 0.511 | -0.557 -0.650 -0.150 | 0.641 0.748 0.173 | 48 |
| 7.0 | 0.747 | -0.422 -0.792 -0.221 | 0.457 0.857 0.239 | 59 |
| 9.0 | 1.000 | -0.210 -0.900 -0.275 | 0.218 0.933 0.285 | 69 |
| 12.0 | 1.000 | 0.001 -0.931 -0.291 | -0.001 0.955 0.298 | 73 |
| 15.0 | 0.652 | 0.211 -0.900 -0.275 | -0.219 0.933 0.285 | 69 |
| 17.0 | 0.146 | 0.425 -0.790 -0.220 | -0.460 0.855 0.238 | 59 |
| 18.0 | 0.000 | 0.559 -0.647 -0.149 | -0.644 0.746 0.171 | 48 (night) |

1. **From or to?** The server's vector is the direction the light **travels**: it points down (y about -0.65 to -0.93) all day. The bytecode negates it when `y + 0.2 > 0` and then bends it toward straight down by 0.35. `SunShade.towardSun` flips it so y is positive and normalises it. **It is not normalised** (length 0.76 to 0.98), so the mod normalises it.
2. **East.** In the morning (clock 5 to 9) the ray toward the sun has x > 0: the sun is on the +X side at sunrise and on the -X side in the afternoon. East is +X, as the earlier logs said. At noon the ray leans toward +Z (z = +0.30), so the sun is toward the south (north is -Z). Not compared with the in-game compass or a sunrise.
3. **Elevation at the edges of the day.** The factor passes 0.25 at about clock 5.0 (elevation 32 degrees) and at about clock 16.3 (about 62 degrees; the factor reaches 0 at clock 18). The engine's 0.35 bend toward straight down makes the sun steeper than a real one: even at the first light it is above 30 degrees, so mornings cast the longest shadows (a 6-block wall shadows about 9 blocks) and evenings short ones (about 3 blocks). A `ShadeRayLength` of 48 is far more than needed; 24 would do.

**What blocks report** (`/sunprobe blocks`, 0.6.8, *verified*; fluids are not blocks and never appear):

| Block | Material | Opacity | Draw | A ray treats it as |
| ----- | -------- | ------- | ---- | ------------------ |
| Rock_Sandstone_Red, Soil_Sand, Soil_Grass, Wood_Oak_Trunk, Rock_Ice | Solid | Solid | Cube | SOLID (full shade) |
| Plant_Leaves_Oak | **Empty** | **Cutout** | Model | PARTIAL (leaves shade in part even though they report Material Empty, which is why the material alone cannot decide) |
| Plant_Grass_Arid, Plant_Grass_Lush, Plant_Flower_Common_Red, Plant_Bush_Arid | Empty | Transparent | Model | OPEN (grass and flowers never shade: the old open question is closed) |
| Plant_Cactus_1 | Solid | Transparent | Model | OPEN |
| Furniture_Crude_Window, Furniture_Ancient_Window, Furniture_Cybercity_Windows_Full | Solid | Transparent | Model | OPEN (windows let the sun through) |
| Furniture_Crude_Torch, Arrakis_Litrejon | Empty | Transparent | Model | OPEN |

Rule (`SunShade.classify`): Opacity Solid shades fully, Cutout shades `PartialShadeWeight`, Semitransparent shades partly only if the material is Solid, Transparent never. Unit tested against these combinations.

**Method.** Three sample points (feet + 1.6, 1.0, 0.3 by default, scaled by the model's eye height over 1.6 when it can be read, so a different pose samples lower) each follow a ray toward the sun for `ShadeRayLength` blocks with `BlockIterator.iterate` (the walk stops when the callback returns false; tested). Solid stops it with full shade, partial blocks add `PartialShadeWeight` until 1, the block the point is in is skipped, a chunk that is not loaded ends the ray as open sky, and so does the top of the world. Sun fraction = average of the three x the sunlight factor, 0 below `MinSunlightFactor`. The stored sky light is not used (it is shown in `/sunprobe` for comparison only). Weather: no cloud data on the server; other mods dim the sun with the `SUN_INTENSITY_MULTIPLIER` modifier (per player, or per world with `WaterService.setWorldSunIntensity`).

**Exposure rules** (`WaterSystem`). The check runs `ShadeChecksPerSecond` (4) times a second per player and the result is smoothed over `SunSmoothingSeconds` (0.5 s). Gain is `ExposureGainPerSecond x sunFraction x gain modifiers` (so full sun is still +1.0/s and the gain has no step at the shade threshold). The player counts as in shade while the fraction is below `ShadeThreshold` (0.5); after `ExposureGraceSeconds` (5 s) of continuous shade exposure falls at `ExposureDecayPerSecond` and nothing is gained. In shade before the grace is over the small gain (fraction below 0.5) continues. The one step in the rate is when the grace ends (a small gain becomes the full decay): that is the grace period, not a threshold artefact.

**Cost.** Measured in a unit test on a fake block grid, JIT warmed: **1.5 microseconds per 3-ray check** (48-block rays at 40 degrees, one leaf in the way). The real world adds a chunk lookup each time a ray crosses a chunk boundary (at most 2 to 3 per ray) and one cached block-class lookup per block, so expect a few times that; at 4 checks a second per player that is well under 0.1 ms per second per player. **Not measured in the real server**; there is no player in the headless run.

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

## Shade rays: what to check in game (NOT seen in game; everything above the cost line is from the jar, the headless server and unit tests)

Commands: `/time <hour>` sets the clock; `/waterdebug` shows the sun fraction, sample points, shade timer and the exposure rate; `/sunprobe` lists what blocks each of the three rays (or "open"); `/sunprobe ray` also puffs particles along the rays for 5 s (dust = head, sand = chest, hard dust = legs, dirt = the blocking block); `/sunprobe time` and `/sunprobe blocks [ids]` also work from the console.

1. `/time 12`, stand in the open: sun fraction about 1.00, exposure +1.0 per second. Step under a one-block overhang: the fraction drops to 0, and 5 s later exposure starts to fall.
2. `/time 7` and `/time 17`, stand just behind a tall rock, wall or island cliff: the shadow side is shade, the sunny side is full sun, and the shaded spot moves between 7 and 17 (the shadow lies to the west in the morning and the east in the afternoon). Compare with the shadows you can see: if they disagree the direction or the sign is wrong (`/sunprobe time`, `/sunprobe ray`).
3. Walk from a cave mouth inward; stand under one leaf block (about 0.5); stand in tall grass (must stay 1.0).
4. Walk back and forth across a shadow edge: the "in shade for N s" counter in `/waterdebug` must not keep resetting from flicker.
5. Set `UseShadeRays` false in `Water_of_Arrakis.json` and compare: the old behaviour (a roof is the only shade, 1 or 0).
6. With several players, watch the tick time; each check should be well under a millisecond.
7. Check the particles from `/sunprobe ray` actually line up with the sun. Particle ids are guesses (`Block_Break_Dust`, `Block_Break_Sand`, `Block_Land_Hard_Dust`, `Block_Break_Dirt`); if one does not exist nothing is drawn for it.
