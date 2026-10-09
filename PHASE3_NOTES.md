# Phase 3 notes: plants on the islands (investigation and what was built)

Status: builds, all assets load on the headless server with no validation errors, 8 rules tests pass. **Nothing has been seen in game.**

## Investigation

- **Dunes of Arrakis has no placement hooks to reuse.** Its biome (`Arrakis_Terrain.json`) ends in `"Props": []`; the terrain is a data-only density graph, and the Java side only wraps the generator to change the spawn provider. A feature placed by the generator itself would be a HytaleGenerator prop (Locator + Scanner + Pattern) written into the Dunes biome JSON, which would make Dunes depend on this mod's blocks (a missing block fails asset loading). So the plants are placed by this mod from `ChunkPreLoadProcessEvent` when a chunk is newly generated, and only in worlds whose generator type is in `GeneratorTypes` (default `Dunes_of_Arrakis`). The generator is untouched.
- **Axes:** east is +X, north is -Z (also what the Weather and Worms logs found). Not checked against the in-game compass.
- **Island side / edge faces:** there is no island-centre query at placement time, so "side" is the local slope. A column is north-facing when the ground falls toward -Z (height one block north is lower than one block south by at least 2 x `MinSlope`, default 0.5 per block), south-facing the opposite. Island rock is a surface block whose id starts with `Rock_` (the Dunes spawn search uses the same rule). Rows at the chunk edge are skipped so no neighbouring chunk is needed. Placement is deterministic from the world seed and the column.
- **Time of day:** `WorldTimeResource.getGameDateTime()` gives the in-game clock; evening is `[EveningStartHour, EveningEndHour)` = 17:00 to 20:00 (config). Not checked how this lines up with sunset. The world starts at 05:30.
- **Day rollover:** the in-game epoch day (`getGameDateTime().toLocalDate().toEpochDay()`); a bush used on an earlier day is reset.
- **Unbreakable:** a block with no `Gathering` section has nothing to break it with and no drop list (the same trick as the undiggable `Arrakis_Sand`). `PlantProtection` also cancels `BreakBlockEvent` and `DamageBlockEvent` on these blocks. Support loss is not a player break, so the plant still breaks, with no loot.
- **Tool hook:** `PlantProtection.addBypass(Predicate<ItemStack>)`; if any predicate accepts the held item the break is allowed. The future tool mod also has to give the blocks a `Gathering` section (or do the pick-up itself).
- **Block state and persistence:** a block can define `State.Definitions` (vanilla crops do), switched with `WorldChunk.setBlockInteractionState`. The burrowbush's `Depleted` state is saved in the chunk. The used-today record is a world resource (`BurrowbushUsage`, saved like the other IslandCraft world resources), so it survives chunk unload and restart; `BurrowbushReset` restores bushes every 2 s once their day has passed, and waits for unloaded chunks.
- **Block interaction:** the block's `Interactions.Use` runs the new interaction types `Arrakis_Primrose` and `Arrakis_Burrowbush` (the pattern vanilla crops use for `HarvestCrop`). The target position comes from `InteractionContext.getTargetBlock()`.

## Primrose: five on one block

Feasible, done. `tools/assets/make_primrose.js` copies the vanilla Cactus Flower model five times (its node tree, with a different offset and yaw each) into `Common/Blocks/Water_of_Arrakis/Arrakis_Primrose.blockymodel`; the block asset scales the whole model by 0.38 (`CustomModelScale`) so all five fit on one block. It uses the vanilla cactus flower texture. How it looks (spacing, size, overlap) is *in game: not yet seen*; the offsets and scale are at the top of the script and the block JSON.

## Behaviour

| | Primrose | Burrowbush |
| - | - | - |
| Where | north-facing island rock slopes, `PrimroseChance` 0.015 per qualifying column | south-facing, `BurrowbushChance` 0.002 (rare) |
| Use | in the evening: +10 water, never above 25; at or above 25: nothing, no message. Outside the evening: "The primrose is closed." | 1 Burrow Bulb, once per in-game day; used bush shows the Depleted state (smaller) and a second try says so |
| Break | not breakable; breaks with no loot when the block under it is removed | same |

## Test in game

1. Deploy and make a **new** Dunes world (plants are placed when a chunk is first generated; existing chunks in a world like "Arrakis v26" are already generated). For an existing world, stand on island rock and run `/waterplants 3`.
2. `/give <you> Arrakis_Primrose` and `Arrakis_Burrowbush` to place one by hand. `/water set 10`. Use the primrose in the evening (17:00-20:00, `/time` to set it) and at other times.
3. Use a burrowbush twice: second time gives the message; wait for midnight (or `/time`) and it resets. Restart the server in between to check it survives.
4. Try to break both with a pick; break the block under one.
