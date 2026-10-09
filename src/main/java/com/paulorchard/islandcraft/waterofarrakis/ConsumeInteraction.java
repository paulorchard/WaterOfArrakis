package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Interaction type {@code Arrakis_Consume}: the effect of eating one of this mod's foods. It is run by the vanilla
 * food chain (the item's {@code Effect} interaction variable) after the item has been used up, so the animation,
 * sound and stack removal are the game's own. The numbers are read from the config on every use.
 *
 * <pre>{"Type": "Arrakis_Consume", "Item": "Arrakis_Spicebread"}</pre>
 */
public class ConsumeInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Consume";

    public static final BuilderCodec<ConsumeInteraction> CODEC = BuilderCodec
            .builder(ConsumeInteraction.class, ConsumeInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Item", Codec.STRING, true), (i, v) -> i.item = v, i -> i.item)
            .add()
            .build();

    private String item = "";

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        if (buffer == null || ref == null) {
            return;
        }
        PlayerRef player = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        WaterOfArrakisPlugin plugin = WaterOfArrakisPlugin.get();
        WaterOfArrakisConfig cfg = plugin.config();
        double water = 0;
        double heal = 0;
        double regenPerSecond = 0;
        double regenSeconds = 0;
        switch (item) {
            case "Arrakis_Burrow_Bulb" -> water = cfg.getBurrowBulbWater();
            case "Arrakis_Spicebread" -> {
                water = cfg.getSpicebreadWater();
                heal = cfg.getSpicebreadHeal();
                regenPerSecond = cfg.getSpicebreadRegenPerSecond();
                regenSeconds = cfg.getSpicebreadRegenSeconds();
            }
            case "Arrakis_Desert_Game" -> {
                water = cfg.getDesertGameWater();
                heal = cfg.getDesertGameHeal();
            }
            default -> {
                plugin.getLogger().at(java.util.logging.Level.WARNING).log("Arrakis_Consume: unknown item '%s'", item);
                return;
            }
        }
        if (water > 0) {
            plugin.service().addWater(player, water);
        }
        if (heal > 0) {
            EntityStatMap stats = buffer.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.addStatValue(DefaultEntityStatTypes.getHealth(), (float) heal);
            }
        }
        if (regenPerSecond > 0 && regenSeconds > 0) {
            plugin.system().addHealthRegen(player.getUuid(), regenPerSecond, regenSeconds);
        }
    }
}
