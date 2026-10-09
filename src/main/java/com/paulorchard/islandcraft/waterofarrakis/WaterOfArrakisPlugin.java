package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.nio.file.Files;
import java.util.logging.Level;

/**
 * IslandCraft - Water of Arrakis. Water and sun exposure per player.
 *
 * <p>Other mods reach the API with {@code WaterOfArrakisPlugin.service()}. Declare this mod as a dependency in your
 * manifest so it loads first.
 */
public class WaterOfArrakisPlugin extends JavaPlugin {

    private static WaterOfArrakisPlugin instance;

    // Must be created before setup(); the server loads it from disk in between.
    private final Config<WaterOfArrakisConfig> config =
            withConfig(WaterOfArrakisConfig.FILE_NAME, WaterOfArrakisConfig.CODEC);

    private final WaterService service = new WaterService(() -> config.get());
    private final WaterSystem system = new WaterSystem(() -> config.get(), service);

    public WaterOfArrakisPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /** The public API. Never null once this mod has loaded. */
    public static WaterService service() {
        return instance.service;
    }

    public static WaterOfArrakisPlugin get() {
        return instance;
    }

    WaterSystem system() {
        return system;
    }

    WaterOfArrakisConfig config() {
        return config.get();
    }

    @Override
    protected void setup() {
        writeConfig();
        WaterState.setComponentType(getEntityStoreRegistry().registerComponent(WaterState.class,
                "WaterOfArrakis_State", WaterState.CODEC));
        getEntityStoreRegistry().registerSystem(system);

        getCodecRegistry(Interaction.CODEC)
                .register(ConsumeInteraction.TYPE, ConsumeInteraction.class, ConsumeInteraction.CODEC)
                .register(FlaskInteraction.TYPE, FlaskInteraction.class, FlaskInteraction.CODEC)
                .register(PrimroseInteraction.TYPE, PrimroseInteraction.class, PrimroseInteraction.CODEC)
                .register(BurrowbushInteraction.TYPE, BurrowbushInteraction.class, BurrowbushInteraction.CODEC);

        BurrowbushUsage.setResourceType(getEntityStoreRegistry().registerResource(BurrowbushUsage.class,
                BurrowbushUsage.ID, BurrowbushUsage.CODEC));
        getEntityStoreRegistry().registerSystem(new BurrowbushReset());
        getEntityStoreRegistry().registerSystem(new PlantProtection.Break());
        getEntityStoreRegistry().registerSystem(new PlantProtection.Damage());

        // Plants go onto island rock when a chunk is first generated.
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, event -> {
            if (event.isNewlyGenerated() && config.get().isGeneratePlants()
                    && PlantGenerator.appliesTo(event.getChunk().getWorld(), config.get())) {
                PlantGenerator.populate(event.getChunk().getWorld(), event.getChunk(), config.get());
            }
        });

        getCommandRegistry().registerCommand(new WaterCommands.ValueCommand("water", service,
                (s, p, v) -> s.setWater(p, v), (s, p, v) -> s.addWater(p, v)));
        getCommandRegistry().registerCommand(new WaterCommands.ValueCommand("exposure", service,
                (s, p, v) -> s.setExposure(p, v), (s, p, v) -> s.addExposure(p, v)));
        getCommandRegistry().registerCommand(WaterCommands.debug(service, system, () -> config.get()));
        getCommandRegistry().registerCommand(WaterCommands.plants(() -> config.get()));

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> system.forget(event.getPlayerRef().getUuid()));
    }

    /** Assets are loaded by now: say which of this mod's items and which water fluids this game has. */
    @Override
    protected void start() {
        StringBuilder missing = new StringBuilder();
        for (String id : new String[] {Litrejon.ITEM_ID, "Arrakis_Burrow_Bulb", "Arrakis_Spicebread", "Arrakis_Desert_Game",
                PlantProtection.PRIMROSE, PlantProtection.BURROWBUSH}) {
            if (Item.getAssetMap().getAsset(id) == null) {
                missing.append(' ').append(id);
            }
        }
        StringBuilder fluids = new StringBuilder();
        for (String name : config.get().getWaterFluidIds()) {
            fluids.append(' ').append(name).append('=')
                    .append(Fluid.getAssetMap().getIndex(name) != Integer.MIN_VALUE ? "ok" : "missing");
        }
        getLogger().at(Level.INFO).log("Items missing:%s; water fluids:%s", missing.length() == 0 ? " none" : missing, fluids);
    }

    /** The server reads the config file but never creates it, so write it back on every start. */
    private void writeConfig() {
        try {
            Files.createDirectories(getDataDirectory());
            config.save().join();
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Could not write config to %s", getDataDirectory());
        }
    }
}
