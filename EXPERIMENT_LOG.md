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
- (Superseded by the vertical edge bars, section "Change round 2, Part 1" below.) The first bars were at `Bottom = 139` (12 px bar + 4 px gap above), same width and same left/right structure (a 7 px gap, so the 24 px icons clear the vanilla ones): water above Health, exposure above Stamina. `Arrakis_Bars.ui` mirrors the two vanilla layouts.
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

## Change round 2, Part 1: vertical bars on the screen edges

Target: `reference/hud-target.webp` (the owner's mock-up, 1640 x 1040). Water on the left edge, exposure on the right edge, each a thin vertical bar filling from the bottom up, icon in the bottom corner, the old bars above Health and Stamina gone.

**Measured from the screenshot** (1640 x 1040): water bar x 18 to 30, y 28 to 960; exposure bar x 1600 to 1612 (mirror: the exposure bar is about 28 px from the right edge in the mock-up, the brief asked for 15 to 20, so both use 18); water drop at about (28, 1005); sun icon in the bottom right. Chat panel starts at x 42, the Inventory/Map hints end at about x 1590.

**Findings** (*verified* means read from the client files or binary; none of it has been run in a client):
- No percentage unit in `.ui` (the brief's finding, confirmed again). A column with `Top: 0, Bottom: 0` and no Height spans the screen height; `FlexWeight` splits it in proportion.
- `FlexWeight` is a property of the base element class in the client binary, listed next to `Anchor`, `Padding` and `Visible`. `Anchor` and `Background.Color` set from the server worked in game (the old bars were coloured and sized), so `builder.set("#Id.FlexWeight", float)` is expected to work the same way. **Not seen in game.**
- Method used (the first of the brief's list, "percent"): each bar is a column with three parts weighted 3 : 89 : 8 (space above, the bar, space below), and inside the bar an empty part and a fill part weighted (1 - v) : v, so the fill is a true share of the bar at any screen size. The fill keeps the tinted-texture technique; the sheen is a child of the fill so it follows its size. Icons are separate groups anchored to the bottom corner (24 px, 20 px up), centred on the bar.
- Fallbacks are config switches, not code changes: `HudMode` "margins" (bar stretches between `HudTopMargin` and `HudBottomMargin` pixels, so only the margins are fixed), "fixed" (`HudFixedHeight` 624 px, about 60% of 1040). The vertical `ProgressBar` fallback was not built: it cannot be coloured (colour only from its texture), which the orange to red blend needs.
- Thickness 12, icon size 24 and the margins are fixed virtual pixels, which the client's UI scale setting scales.
- Textures: `tools/assets/make_bars.ps1` now produces vertical versions (the horizontal ones, rotated 90 degrees counter-clockwise so the slanted end is at the bottom): `Arrakis_Bar_V_Background/Fill/Sheen@2x.png`, 24 x 636. `VerticalBorder: 8` keeps the slant undistorted. The horizontal textures were deleted; the icon files keep their names.
- Ids changed: `#WaterBar`/`#ExposureBar` are gone; now `#WaterColumn/Top/Track/Empty/Fill/Sheen/Bottom/Icon` and the `Exposure...` equivalents. Updated in `WaterHud`; no other code used them.
- Config: `HudBarWidth` and `HudMinPixelChange` are gone; added `HudMode`, `HudEdgeMargin` 18, `HudBarThickness` 12, `HudTopWeight` 3, `HudBarWeight` 89, `HudBottomWeight` 8, `HudTopMargin` 30, `HudBottomMargin` 80, `HudFixedHeight` 624, `HudIconSize` 24, `HudIconBottomMargin` 20, `HudMinPercentChange` 0.25.

**Not done / not seen:** the check at two window sizes and aspect ratios could not be made (no client in this session), so **no size was tried**. The weights, the icon position, the slanted ends and the colours are all unseen. Things most likely to need a look: whether `FlexWeight` is accepted at runtime (if the bars do not move, set `HudMode` to "margins" or "fixed" to see if the rest of the layout is right); whether the sheen child fills its parent; whether the icons clear the chat panel and the key hints at a narrow window.

**In game: HUD at two window sizes** (not yet seen in game): bars on the edges at 1640 x 1040 and at a tall or narrow window, bars keep their proportions, icons in the corners and clear of the chat panel and hints, old bars gone, water fills from the bottom with `/water set 25`, exposure colour blend with `/exposure set 90`.

### Fix after the first run in game (world "Arrakis v26")

The client refused the HUD with `Failed to apply CustomUI HUD commands (CustomUI Set command couldn't set value. Selector: #WaterTop.FlexWeight -> An element of type 'Number' cannot be converted to a 'System.Int32'.)` and the player was disconnected during loading. **`FlexWeight` is an integer.** The first version sent floats (0.0001, 1.0). Now `FlexWeight` values are ints: the bar is split into `SCALE` = 1000 parts (0.1% steps), the empty and fill parts always add up to 1000, and a part with weight 0 has no length. Property paths `#Id.FlexWeight`, `#Id.Anchor` and `#Id.Background.Color` were accepted as selectors (the error is about the value type, so the selector resolved). Lesson: check the client's property types before sending; the client log (`UserData/Logs/*_client.log`, "Failed to apply CustomUI HUD commands") names the selector and the type.

### Bar polish (visual fixes after Part 1 worked in game)

1. **Cap at the top, bottom flat.** `tools/assets/make_bars.ps1` now draws the bars upright: the angled cap is at the TOP, the bottom is square. Separate Left and Right textures (`Arrakis_Bar_V_{Background,Fill,Sheen}_{Left,Right}@2x.png`): the left bar's top-left corner is cut and the right bar's top-right corner, so both caps slope toward the screen edge and mirror each other. The old single vertical textures were replaced.
2. **Exactly 45 degrees.** The cap is 24 texture pixels tall and the bar 24 wide (the @2x of 12 on screen), so the diagonal is 1 pixel across per pixel down (checked on the generated file: first solid pixel at x = 23 - y for the first 24 rows). Border units in `PatchStyle` are 1x pixels, half the @2x texture (checked against vanilla: `ContainerPanelPatch@2x.png` is 24 x 24 with `Border: 4`), so `VerticalBorder: @CapSize` with `@CapSize = 12` is the cap. The background, fill and sheen all use `VerticalBorder`, so only the straight middle stretches. `@CapSize` is in the .ui; the cap in the texture is `$CAP` in the script; they and the bar thickness (12) must stay equal.
   Consequence: the fill is also 9-sliced, so its top edge is the 45 degree cap at every fill level, not only when full (a slanted waterline). Below 24 px of fill (2 caps) the slices overlap and the cap squashes.
3. **Icon gap 4 px.** The column now ends at a fixed distance from the bottom of the screen: icon bottom margin 20 + icon 24 + `HudIconGap` 4 = 48 px, instead of a percentage of the height. The bar's bottom is therefore always 4 px above the top of its icon.
4. **Top of the bar about 6.5% down.** `HudTopPercent` 6.5 is the share of the column (screen height less the 48 px strip) above the bar, as weights 65 : 935. At 1040 high that is 64 px (6.2% of the screen), at 700 high 42 px (6.0%). The bottom end stays just above the icon, so the bar is shorter from the top.
- Config: `HudTopWeight`, `HudBarWeight`, `HudBottomWeight` and `HudBottomMargin` are gone; added `HudTopPercent` 6.5 and `HudIconGap` 4. `HudTopMargin` (60) is now the top space for modes "margins" and "fixed". Named values in the .ui: `@CapSize`, `@EdgeMargin`, `@Thickness`, `@IconSize`, `@IconBottom`, `@IconGap`.
- **Not seen in game; no window size tried** (no client in this session). To check at two sizes: the cap angle, the 4 px gap, the top at about 6.5%, the slant direction on both bars, the fill's slanted top edge at 50%, and that a `Left` or `Right` texture is not missing (a wrong file name would show as an empty bar; the client log names it).

## Change round 2, Part 2: jumping and vaulting cost stamina

**Findings** (*verified* = jar, assets or the headless server; nothing here has been seen in game):
- Sprint: `Stamina.json` has an entry with a `Sprinting` condition and amount -0.1 every 0.1 s: **1.0 stamina per second** of a maximum 10. The mod reads it at startup (`WaterOfArrakisPlugin.logStaminaCosts`) and logs it: `Sprint costs 1.00 stamina per second in Stamina.json (max 10.0). Jump costs 1.00 and vault 1.00 at full water (1.0 and 1.0 seconds of sprint).` *verified headless*.
- Jump and vault costs are therefore configured as **seconds of sprint** (`JumpCostSprintSeconds` 1.0, `VaultCostSprintSeconds` 1.0), and follow the asset if its sprint cost changes. The old `JumpStaminaCost` key is gone (its saved value was 0, which would have kept the cost off in existing worlds). `ClimbStaminaCostPerSecond` is unchanged and still 0.
- `MovementStates` has `jumping` and `mantling`, both read each tick. A rising edge (false to true) is one jump or one vault (`ActionCosts`, unit tested). The ledge pull-up is the engine's mantling state; whether `jumping` is also true while mantling is not known, so the rule is built to be correct either way.
- **Jump into a vault:** a vault that starts within `JumpToVaultWindowSeconds` (0.8) of a charged jump is the same action: it is charged only what the jump did not cover, so the pair costs the higher of the two (with the defaults, 1.0 in total, the vault free). Not both.
- Costs scale with the water tier exactly as sprint does (`TierActionStaminaCost`: free at 75% and above, half below 75 down to 50, full below 50). Attack costs are never touched. The deduction stops at 0: stamina already overdrawn by vanilla (its minimum is -4) is left alone.
- **Blocking at 0 stamina.** Vanilla: the `Stamina_Broken` effect is only a flag and an icon (`Infinite`, `Debuff`); the sprint stop at 0 is not a server rule. The engine does have `MovementEffects` on entity effects (`DisableSprint`, `DisableJump`, `DisableAll`, `SpeedMultiplier`), used by Freeze, Root and Stun, so jump can be blocked by a short effect. Built: `Arrakis_No_Jump` (0.6 s, `DisableJump`), renewed every 0.25 s while stamina is 0 or less, only when `BlockJumpAtZeroStamina` is true. **Default false**, because with Part 3 a player at 0 water cannot recover stamina and could be left unable to leave a pit. Unknown: whether `DisableJump` also stops a vault; not tested.
- Climbing is not changed.

**Assumptions:** 1.0 for both costs (one second of sprint, 10% of the bar); the 0.8 s window; vault free after a jump of equal cost; jump and vault allowed at 0 stamina (they cost nothing more, as there is nothing left).

**In game (not yet seen):** `/water set 100`: jumps and vaults are free. `/water set 60`: a jump takes 0.5 of the stamina bar's 10 (5%). `/water set 40`: a jump takes 1.0 (10%), a vault the same, and a jump into a ledge pull-up takes 1.0 in total, not 2. Sprint for a second beside it to compare. `/waterdebug` shows `jumps N (last cost X), vaults N (last cost X)`. With `BlockJumpAtZeroStamina` true, jumping at 0 stamina should do nothing.

## Change round 2, Part 3: at 0 water

Nothing here has been seen in game. Verified = jar, assets, headless server (assets load, no errors; `Items missing: none` now also checks the new effects and damage cause) and unit tests (36).

**A bug found and fixed on the way (affects Phase 1):** the stamina regeneration interception used `rise / tick length <= RegenDeltaCapPerSecond (4)`. Vanilla regenerates in steps of 0.3 every 0.1 s, so one tick sees a rise of 0.3, which is 8 to 9 per second at 28 ticks a second: **above the cap, so the "half regeneration below 25% water" was never applied**. It is now `RegenStepCap` 0.6 stamina per tick (`RegenDeltaCapPerSecond` is gone): a rise up to 0.6 in one tick is regeneration and is reduced by the tier; a bigger rise is an item (a potion) and is left alone. Checked in a unit test that 0.3 is below the cap.

**Zero tier.** `WaterOfArrakisConfig.waterTier`: exactly 0 water (inclusive at 0) is the zero tier (`zeroTier()`, index 4 with the default bounds); 0.001 is still tier 3. The zero tier's values are separate scalar keys, not a fifth array entry, so worlds with a saved config get them: `ZeroWaterStaminaRegen` 0.0 and `ZeroWaterActionStaminaCost` 1.0. Below 25 (but above 0) the half regeneration is unchanged. Items that restore stamina in one go are still allowed (a rise above `RegenStepCap`); a stamina effect that regenerates in small steps (the food regen effects) is treated as regeneration and reduced to 0 at 0 water.

**Thirst damage.** `ThirstAccumulator` (unit tested) + `ThirstDamageSystem`. Condition: water is 0 after the tick's drain AND stamina is 0. The water that was due this tick and could not be taken (the baseline, action costs and the exposure multiplier, exactly as `WaterSystem` computes them, including the part of a drain that overshoots to 0) times `ZeroWaterDamagePerUnit` (1.0) is collected. With stamina above 0 nothing is collected (stamina is spent first) and as soon as the player has water the accumulator is cleared.
- **Batching (assumption):** "apply in whole steps at most once every interval" is read as: at most once per `ZeroWaterDamageIntervalSeconds` (1.0) and in whole HP steps (`ZeroWaterMinHit` 1.0), the fraction carrying over. At the idle rate (about 0.02 HP per second, 0.04 in full sun) that is one 1 HP hit about every 50 seconds, faster when running or jumping. `ZeroWaterMinHit` 0 deals the accumulated fraction every interval instead (a hurt flash every second).
- **Path:** the same call vanilla drowning and the storm use (`DamageSystems.executeDamage`), through a per-entity system because that call needs a command buffer. Cause asset `Arrakis_Thirst` (no armour reduction, no durability loss), source `ThirstDamageSystem.Source` with the death message "{player} died of thirst" (`general.damageCauses.arrakis_thirst` = "thirst").
- **Logout:** the accumulator is **dropped** (runtime only), so nobody logs in to a hit that was owed. Creative and dead players are not simulated, so no damage.
- First time per episode a chat line says you are out of water, and another when you are also out of stamina.

**Slow.** `ZeroWaterSpeedMultiplier` 0.9. **Assumption: "make them 10%" read as 10% slower (x0.9).** If 10% of normal speed is meant, set the key to 0.1: no code change.
- Method (investigated): `MovementManager.getSettings()` edits do work as a mechanism, but the Worms of Arrakis mod found that route did not slow the player in game and any other writer (game mode, mount, model change) resets it. An entity effect with `ApplicationEffects.HorizontalSpeedMultiplier` is the field vanilla Slow uses and composes with other effects. So: 19 generated effects `Arrakis_Thirst_Slow_05` to `_95` (`tools/assets/make_thirst.js`), the multiplier snapped to the nearest 5% (so 0.9 gives `_90`, 0.1 gives `_10`).
- The effect is 2 s long and renewed every 0.5 s with `OVERWRITE`, so it can never stack, ends by itself if the mod stops, and is removed the tick water is above 0 (or the simulation freezes: creative). Survives respawn and relog because it is re-derived from water every tick.
- **Modifier API:** new `ModifierType.MOVEMENT_SPEED_MULTIPLIER`. This mod sets it under the id `Arrakis:ZeroWater` while water is 0 and removes it after; other mods can add their own (the product is used) or read it. Setting it twice just replaces it.
- Not known: whether `HorizontalSpeedMultiplier` also slows climbing and swimming (no setting says so); jump height is not touched (not seen whether the slower run makes jumps feel wrong).

**Death and respawn.** What the code did before: a dead player was not simulated, and nothing was reset, so a player who died at 0 water respawned at 0 water and starved again at once. Now `WaterSystem` notices the dead-to-alive change and sets `RespawnWater` (50) and `RespawnExposure` (0), clears the accumulator, the messages and the slow. Not known: whether a respawn keeps the same entity (the dead flag clears) or makes a new one; the water and exposure are saved on the player either way, and the reset runs on the first live tick after a death the server saw. A player who logs out while dead and comes back is not reset by this (not handled).

**Testing aids.** `/water set 0`, `/exposure set`, `/waterstamina set|add <n> [player]` (new). `/waterdebug` shows the tier (and which is the zero tier), the stamina regeneration multiplier in effect, whether out of water, the slow percent and speed modifier, the thirst damage owed, and the jump and vault counts and last charges.

**In game (not yet seen):**
1. `/water set 0`, then sprint until stamina is empty: stamina stays at 0 and does not recover; speed is visibly slower (x0.9; `/waterdebug` says slow 90%); a stamina potion still works.
2. Drink (Litrejon or Burrow Bulb): the speed returns at once, regeneration returns.
3. `/water set 0` and `/waterstamina set 0`: HP drops 1 about every 50 s idle, faster running or jumping, faster in full sun; `/waterstamina set 5` stops the damage.
4. Die of thirst: the death message says "died of thirst"; after respawning water is 50 and speed is normal.
5. Creative: no damage, no slow.
6. Optional: with `ZeroWaterMinHit` 0 a small hit every second.

### Retune after the first feel test of Part 3: more damage, actions cost HP

- **Idle bleed 1 HP per 10 s.** The key `ZeroWaterDamagePerUnit` (1.0) is replaced by `ZeroWaterHpPerWaterUnit` **5.0** (a new name, because a saved config would have kept the old key's 1.0). Baseline drain 0.02 water per second x 5 = 0.1 HP per second = 1 HP every 10 s idle in the shade; full sun doubles it (the exposure multiplier still applies, 0.2 HP per second); running and jumping add their water drain x 5. Unit tested.
- **Stamina actions cost HP.** While water and stamina are both 0, stamina an action could not take is taken as HP: `ZeroStaminaHpPerStamina` 1.0. So a jump or a vault is 1 HP, one second of sprint 1 HP, climbing its configured cost (0 by default). "Could not take" is the charge beyond the stamina left (a jump at 0.4 stamina takes the 0.4 and owes 0.6), plus, for sprinting with stamina at 0, the sprint rate (1.0 per second) x the zero tier's action cost (1.0) x time. The sprint part assumes the sprint flag stays true while stamina is 0 (not known: the client may stop the sprint itself).
- Everything still goes through the same batching: at most once per `ZeroWaterDamageIntervalSeconds`, in whole `ZeroWaterMinHit` HP steps. So sprinting at 0/0 is about 1 HP per second, a jump 1 HP at once, idle 1 HP per 10 s. Not yet seen in game.

## How stamina regeneration pauses (researched), and the same pause for jump, vault and climb

**What vanilla does** (*verified* in the 0.6.8 jar and assets):
- Stamina regenerates +0.3 every 0.1 s (3 per second) only while a list of conditions holds (`Stamina.json`): the stat **`StaminaRegenDelay` is at 0**, stamina is not below 0 (the overdrawn state has its own rule), not wielding (guarding), not sprinting, not gliding.
- `StaminaRegenDelay` (`Entity/Stats/StaminaRegenDelay.json`) is a stat from -60 to 0 that climbs back toward 0 by 0.1 every 0.1 s, **1 per second**. Setting it to -0.75 means regeneration resumes 0.75 s later. That is the pause you noticed.
- Sprint: while sprinting, regeneration is off (the `Sprinting` condition). When sprinting **stops**, `StaminaSystems$SprintStaminaEffectSystem` (a server system, one per world tick for every player) sets the delay stat to `Plugin.Stamina.SprintRegenDelay` of the gameplay config (`EntityStatId StaminaRegenDelay`, `Value -0.75` in `GameplayConfigs/Default.json`), unless it is already lower. So: no regeneration during the sprint, then 0.75 s more.
- Other actions use the same stat from their item interactions: a `ChangeStat` with `Behaviour: Set` on `StaminaRegenDelay`: dodge -0.7, double jump -0.3, most weapon attacks -1, heavier ones -1.5 to -10. Overdrawn stamina sets -0.5 (the `MinValueEffects` of the stamina stat).
- Vanilla jumping and climbing (no stamina cost) never set the delay, so they never paused regeneration.

**What the mod now does** (`WaterSystem.pauseRegen`; unit test for the edge flag; not seen in game):
- On the tick a jump or a vault starts, the delay stat is set to the **sprint value** (read from the sprint-delay resource the engine keeps for that config, -0.75), unless it is already lower: exactly vanilla's rule. Done whether or not the action costs stamina at the current water level (at 75% water and above they are free, but sprint also pauses regeneration then).
- Climbing keeps the delay down every tick while it lasts, so regeneration stays off during the climb (as it does while sprinting) and resumes 0.75 s after the last climbing tick.
- `ActionRegenDelaySeconds` (0 = same as sprint, 0.75) overrides the length; `ActionsPauseRegen` false turns the whole thing off. The pause after sprinting is vanilla's and is not touched.
- `/waterdebug` shows how many pauses the mod made and the length of the last.
- Not known: whether a mantle (vault) leaves the player "jumping" for long enough that the pause is set twice (harmless: the second is skipped when the stat is already lower).

## Stamina rework (breath costs water, thirst and heat stretch the pause), Part 1: investigation and the curves

**No behaviour has changed yet.** Added: the curve constants in the config (`PauseDryScale` 7, `PauseDryExponent` 2, `PauseExposureScale` 0.5, `RegenDryScale` 1.5, `RegenDryExponent` 2, `RegenExposureScale` 0.25, `RegenWaterCostPerPoint` 0.05, `MaxPauseSeconds` 60), `StaminaCurves` (the formulas, pure and unit tested: P and R are exactly 1.0 at 100% water and 0 exposure, both monotonic, the 60 s cap, the regen water cost follows the exposure drain multiplier) and `/staminacurve` (works from the console). The simulation does not use them yet.

### Findings (verified in the 0.6.8 jar, assets and the headless server; nothing seen in game)

**How natural regeneration is applied.** `EntityStatsSystems$Regenerate` (`PlayerRegenerateStatsSystem` for players) runs once per tick for every entity with stats. For each stat it asks each `Regenerating` entry whether it fires (`RegeneratingValue.shouldRegenerate`: counts the entry's `Interval` down, then checks every condition with `Condition.allConditionsMet`), computes the amount (`RegeneratingValue.regenerate`: the amount times any asset `Modifiers`, clamped), adds up the entries that fired, and then calls **`EntityStatMap.addStatValue(stat, sum)` once**. So stamina regeneration is a lump of +0.3 on the tick where the 0.1 s interval fires (vanilla 3 per second). Armour can add its own regenerating entries (`ItemArmor.getRegeneratingValues`).

**What a plugin can do about it** (there is no hook inside that system):
1. *Watch the stat after the engine wrote it and correct it.* This is what the mod does. Catch: the mod's `WaterSystem` is a plain world `TickingSystem` with no ordering, so it may run before or after `Regenerate` within a tick. To see exactly this tick's regeneration it should be an entity system declared `AFTER` `EntityStatsModule$PlayerRegenerateStatsSystem` (`SystemDependency(Order.AFTER, ...)`; the engine's own `SprintStaminaEffectSystem` declares `BEFORE` that system and `BEFORE` the movement-state system, so the delay it writes is seen by the regeneration).
2. *Suppress a stat direction* (`EntityStatMap.setStatChangeSuppressed(stat, RAISE|LOWER|BOTH, true)`): the engine drops suppressed changes. All or nothing: it drops potions and food too, so it cannot slow regeneration.
3. *Asset-side hooks* (`Regenerating.Modifiers`, custom `Condition` types a plugin can register): only useful if `Stamina.json` is changed, i.e. a vanilla override that would clash with other mods. Not recommended.

**Telling natural regeneration from restores.** Natural regeneration is +0.3 (or 0.6, 0.9 if a tick covers several intervals) on a tick where the conditions held: the `StaminaRegenDelay` stat is 0, not sprinting, not wielding (guarding), not gliding. Restores differ in size and timing: `Food_Stamina_Restore_*` and `Potion_Stamina_Instant*` add 10 to 80 (or 45 to 60 percent) at once, `Food_Stamina_Regen_*` adds 0.5 to 1.5 every 5 seconds, `Potion_Stamina_Regen` 5 per second, `Boost` changes the maximum. Rule: a rise that is a whole multiple (1 to 3) of the asset's regeneration amount, on a tick where the natural conditions held, is natural regeneration; everything else is a restore and is left alone. The current cap `RegenStepCap` 0.6 is **not enough** (a Tiny regeneration food, +0.5, would count as natural; the exact-multiple test rejects it, and 1.5 = 5 steps is rejected by allowing at most 3). One residue: a restore on the same tick as a natural step reads as a restore, so that one 0.3 step is neither slowed nor charged (harmless). The natural amount should be read from the stat asset (the entry whose conditions include the delay stat: 0.3 every 0.1 s) rather than hard-coded.

**Stretching the pause: recommendation (b), take back part of the engine's refill each tick.** The delay stat `StaminaRegenDelay` (-60 to 0) is refilled by its own regenerating entry, +0.1 per 0.1 s (1 per second); it is a stat like any other, so the same watch-and-correct applies: each tick, if it rose, keep only `rise / P` of the rise. Why (b) over (a) (scaling the amount an action writes):
- It treats every writer the same without a list: item interactions (`ChangeStat Set StaminaRegenDelay`: dodge -0.7, double jump -0.3, sword thrust, charged mace, battleaxe downstrike -3, bow shot -10), the engine's `SprintStaminaEffectSystem`, the stamina asset's own "reached zero: -0.5", gliding (-2), and the mod's own pauses. (a) must detect each write as a sudden drop and cannot tell a write from a hit; the writes use `Behaviour: Set`, which can also shorten a longer pause, so "the amount added" is not even well defined.
- It is live: the pause left is `|stat| x P` at the current water and exposure, so drinking in the middle of a pause shortens the rest and a step into the sun lengthens it, with no bookkeeping.
- Water 0 is the same code: pin the stat to -60 every tick.
- The cap: if the remaining pause `|stat| x P` would exceed `MaxPauseSeconds`, the stat is set to `-MaxPauseSeconds / P`.
- Edge cases. *Two actions back to back:* the second `Set` overwrites the first with its own number (vanilla behaviour, unchanged: a second action can shorten the pause); the mod's own base pauses use "at least". *Water changing mid-pause:* handled live. *Relog:* the stat is saved with the player's stats, so a pause survives a relog, scaled by the water the player returns with. *Rounding:* the stat steps 0.1 every 0.1 s, so the take-back lands on about every third tick; the average is exactly 1/P.

**What else writes `StaminaRegenDelay` or moves stamina.** `Stamina.json` (0.5 s when stamina reaches 0), `GlidingActive.json` (-2 when gliding ends), `SprintStaminaEffectSystem` (-0.75 on the tick sprinting ends, from `Plugin.Stamina.SprintRegenDelay` in the gameplay config), and about 100 item interaction files (dodge, double jump, every weapon, guard start, guard bash and shove, shortbow). **The brief says sprinting sets no pause; in 0.6.8 it does** (-0.75 when sprinting stops): the pause you noticed. See question 1. Readers of the stat: only `Stamina.json` (its three natural-regeneration entries have the condition `Stat StaminaRegenDelay Amount 0`). Stamina otherwise moves through `DamageSystems$DamageStamina` (a blocked hit subtracts stamina, scaled by the guard's `StaminaCost`), item `ChangeStat`, and stat modifiers from effects.

### The curves with the default constants (`/staminacurve` prints these and more)

Pause multiplier P (columns: exposure):

| water | 0% | 25% | 50% | 75% | 100% |
| --- | --- | --- | --- | --- | --- |
| 100% | 1.00 | 1.13 | 1.25 | 1.38 | 1.50 |
| 75% | 1.44 | 1.62 | 1.79 | 1.97 | 2.16 |
| 50% | 2.75 | 3.09 | 3.44 | 3.78 | 4.13 |
| 25% | 4.94 | 5.55 | 6.17 | 6.79 | 7.41 |
| 0% | 8.00 | 9.00 | 10.00 | 11.00 | 12.00 |

Regen speed R (vanilla 3.0 per second is 1.00): 100% water 1.00 / 0.94 / 0.89 / 0.84 / 0.80; 75% 0.87 / 0.82 / 0.77 / 0.73 / 0.69; 50% 0.73 / 0.68 / 0.65 / 0.61 / 0.58; 25% **0.54** / 0.51 / 0.48 / 0.46 / 0.43; 0% 0.40 / 0.38 / 0.36 / 0.34 / 0.32. (The brief says R at 25% water is "about 0.52"; the given formula gives 0.54. The rest of the brief's numbers match: P 1.44 / 2.75 / 4.94 / 8, R 0.73 at 50%, 0.4 near 0.)

A 0.5 s pause (the vanilla "stamina hit zero" pause) as lived, in seconds, no exposure: 100% water 0.50, 75% 0.72, 50% 1.38, 25% 2.47, 0% 4.0; at full exposure 0.75 / 1.08 / 2.06 / 3.70 / 6.0. A 3 s battleaxe pause at 25% water and 50% exposure: 18.5 s. The 10 s bow pause at 0 water and 50% exposure: 100 s, capped at 60. Time to refill the whole bar once regeneration starts: vanilla 3.3 s; 50% water 4.6 s; 0% water 8.3 s; up to 10.4 s with full exposure. Water per full refill (10 points): 0.50 with no exposure, 0.60 at 25%, 0.75 at 50%, 0.85 at 75%, 1.00 at 100% (it follows the exposure drain multiplier only, not the water level); an idle player drains 0.02 per second, so one full refill is worth 25 s of standing still.

### Questions and assumptions for review before Part 2

1. **Sprint-end pause.** The engine already pauses 0.75 s when sprinting stops. With the rework P multiplies it at the delay stat. Proposal: default `SprintEndPauseSeconds` 0 (keep the engine's 0.75, which P then stretches) and use the new base pauses (0.5 s) only for jump and vault. Say if you want 0.5 for sprint as well, replacing the 0.75. The earlier change this session gave jump, vault and climb a 0.75 s pause (the "sprint value"); under the rework they become `JumpPauseSeconds` and `VaultPauseSeconds` 0.5 as asked, and climbing keeps a pause too (0.5?).
2. **Scheduling.** The take-back needs `WaterSystem` split into an entity system ordered after the engine's regeneration. That is a change in how the simulation is scheduled, not only new constants.
3. The natural amount is read from the stat asset, not hard-coded 0.3.
4. Delay values are capped at 60 s: only the 10 s bow pause and charged weapons reach it, at low water.
5. Natural regeneration does not run while sprinting or guarding (vanilla conditions), so nothing is charged then; the water cost appears only when the bar actually refills.

In game: not applicable yet, Part 1 changed no behaviour.

## Stamina rework, Part 2: built

Decisions from the review of Part 1: all base pauses 0.5 s (sprint end, jump, vault, climb); `WaterSystem` rewritten as an entity system ordered after the engine's regeneration. **Not seen in game.** 53 unit tests pass; the plugin loads on the headless server (the new ordering dependency resolved; no player, so the per-player code has only been compiled and unit tested at the rule level).

**What now happens** (formulas and constants in the log section above; every number is in `Water_of_Arrakis.json`):
- **Pause stretched by P.** Each tick, if the pause stat (`StaminaRegenDelay`) was refilled by the engine, only `1 / P` of that refill is kept (`StaminaBreath.stretchRefill`). The stat stays in "vanilla seconds"; the pause lived is `|stat| x P`. P comes from water and exposure (`StaminaCurves.pauseMultiplier`) times the `STAMINA_PAUSE_MULTIPLIER` modifiers. **Found while writing it:** scaling the clamped last step makes the stat approach 0 forever (each step keeps a fraction of what is left), so a pause would never end. When the engine reaches the maximum, a full nominal step (0.1, read from `StaminaRegenDelay.json`) is scaled instead and the result clamped at 0, so a pause ends after exactly P times the vanilla time. Unit tested (a 1 s pause at P = 4 takes 40 steps; at P = 1 it takes 10).
- **Pause cap and water-0 pin.** The remaining pause `|stat| x P` is capped at `MaxPauseSeconds` (60) by moving the stat to `-60 / P`. At water 0 the stat is set to its minimum (-60) every tick, so stamina never moves; above 0 the pin is released and the cap applies, so the first pause after drinking is long (60 s at most, shorter as P falls). **Consequence to be aware of:** at low but non-zero water P is 5 to 8, so any pause longer than about 8 to 12 vanilla seconds is cut to 60 s; the 10 s bow pause at 25% water is 49 s.
- **Regeneration speed R and the water it costs.** A rise in stamina that is 1 to 3 whole regeneration steps (0.3, read from the stat asset) on a tick where the player was not sprinting or gliding is natural regeneration: it is multiplied by R (times the `STAMINA_REGEN_MULTIPLIER` modifiers) and the stamina that actually came back costs `RegenWaterCostPerPoint` x the exposure drain multiplier x the `STAMINA_REGEN_WATER_COST_MULTIPLIER` modifiers in water. Never charged for stamina clamped at the maximum (the engine already clamped it before the mod reads it). If the water cannot pay, only the share it can pay is given and the water goes to 0 (`StaminaBreath.regen`); then the pin holds. Any other rise (potions, restore foods, regenerating foods that add 0.5 or 1.5) is a restore: untouched, free and instant. Guarding: the guard (wielding) is not detected on the server side here; guarding blocks natural regeneration through the engine, so no lump arrives to be charged. Creative players are not simulated (as before).
- **Base pauses** (additions to vanilla): `JumpPauseSeconds`, `VaultPauseSeconds`, `ClimbPauseSeconds` 0.5 (jump and vault on the tick they start, climbing held while it lasts): at least that value, never shortening a longer running pause. `SprintEndPauseSeconds` 0.5 **replaces** the engine's 0.75: on the tick sprinting stops, if the stat is the engine's -0.75 (or one refill step above it) it becomes -0.5; a longer pause already running is kept; 0 keeps the engine's 0.75. All are then multiplied by P like every other pause.
- **Tier discounts removed.** Deleted: `WaterTierLowerBounds`, `TierActionStaminaCost`, `TierStaminaRegen`, the zero tier and its two keys (`ZeroWaterStaminaRegen`, `ZeroWaterActionStaminaCost`), `ActionRegenDelaySeconds`, `ActionsPauseRegen`, `RegenStepCap`, the sprint-refund code, the per-tick tier lookup, `WaterOfArrakisConfig.waterTier/zeroTier/waterTierCount/actionStaminaCost/staminaRegen`, `WaterService.getWaterTier` and `WaterListener.onWaterTierChanged` (nothing else used them, and without bounds they have no meaning). `onExposureStepChanged` stays. Costs are vanilla (sprint 1.0 per second, attacks, guard, dodge untouched) plus the mod's own: jump and vault 1.0 each (one second of sprint) at every water level, climbing as configured (0). There is no sprint refund code left, so no per-tick work for it.
- **Kept from Part 3 of the previous round:** the damage when water and stamina are both 0 (with the stamina actions turned into HP), the movement slow at 0 water, respawn values. The zero tier's "regeneration 0" is replaced by the pin.
- **Other mods:** `ModifierType.STAMINA_PAUSE_MULTIPLIER`, `STAMINA_REGEN_MULTIPLIER`, `STAMINA_REGEN_WATER_COST_MULTIPLIER` (multiply across ids), and read-only `WaterService.getStaminaPauseMultiplier(player)`, `getStaminaRegenMultiplier(player)`, `getStaminaRegenWaterCost(player)` (per stamina point).
- **Debug:** `/waterdebug` shows P with its parts (water, heat, modifiers), R, the water per point, the current value of the pause stat, whether it is pinned, the water regeneration cost over the last second and over the current stretch. `/staminacurve` as before.
- **Scheduling.** `WaterSystem` is now an `EntityTickingSystem` with `SystemDependency(Order.AFTER, EntityStatsModule.PlayerRegenerateStatsSystem)`. Its world-level pass first makes sure every player has a `WaterState` (a component cannot be added while entities are processed), then runs the per-player pass. The query is built on first use because the component types do not exist when the plugin object is created (it crashed the plugin load once; fixed).

**Assumptions and things that could not be done as described:**
1. The old "vanilla sets no pause after sprinting" is wrong (see Part 1); `SprintEndPauseSeconds` 0.5 therefore replaces vanilla's 0.75 instead of adding one.
2. Natural regeneration is recognised by size and by "not sprinting or gliding"; guarding is not read separately (the engine does not regenerate while guarding, so nothing is charged then). A restore that lands on the same tick as a natural step is read as a restore and that one 0.3 step is neither slowed nor charged.
3. The pause stat can only be corrected after the engine has written it, so the cap and the pin act from the tick after a write. A player who stops sprinting sees the pause take effect on the same tick (the system runs after the engine's regeneration, and the stat is what the next tick's regeneration reads).
4. A second action that overwrites the pause stat with `Behaviour: Set` can shorten a longer pause (vanilla behaviour, not changed).
5. No HUD feedback, as asked.

### In game: not yet seen (every item)
1. At full water and no exposure stamina feels vanilla (sprint stop pause 0.5 s instead of 0.75 s; jump and vault now pause 0.5 s; nothing else differs; 3.3 s to refill).
2. Sprint to empty and stop: at 50% water the wait before regeneration is about 1.4 s, at 25% about 2.5 s (a 0.5 s pause; `/staminacurve`), and the refill slower.
3. In full sun at the same water level the wait is longer again (x1.5 at full exposure).
4. Each full refill takes water: 0.5 at no exposure, 1.0 at full exposure (`/waterdebug` shows the water cost of the last second and of the stretch).
5. At 0 water the stamina bar never moves; drinking releases it, and the first wait afterwards is long.
6. Potions and food that restore stamina work instantly and cost no water.
7. Attacks, guard, dodge cost stamina exactly as vanilla.
8. No tier discounts anywhere: sprint at 100% water costs stamina again.
9. The damage and the slow at 0 water and 0 stamina still work; `/staminacurve` prints the tables.
