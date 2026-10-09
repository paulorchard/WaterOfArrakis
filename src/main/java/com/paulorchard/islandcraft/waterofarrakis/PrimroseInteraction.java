package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Interaction type {@code Arrakis_Primrose}, the block's {@code Use} interaction. In the evening window
 * (EveningStartHour..EveningEndHour on the in-game clock) a drink gives PrimroseWater, but never takes water above
 * PrimroseCap. At or above the cap nothing happens and nothing is said. Outside the evening the player is told the
 * flowers are closed. The flowers are not used up.
 */
public class PrimroseInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Primrose";

    public static final BuilderCodec<PrimroseInteraction> CODEC = BuilderCodec
            .builder(PrimroseInteraction.class, PrimroseInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

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
        Store<EntityStore> store = buffer.getExternalData().getWorld().getEntityStore().getStore();
        if (!cfg.isEvening(GameClock.hour(store))) {
            player.sendMessage(Message.translation("server.waterOfArrakis.primroseClosed"));
            return;
        }
        WaterService service = plugin.service();
        double water = service.getWater(player);
        if (water >= cfg.getPrimroseCap()) {
            return;
        }
        service.setWater(player, Math.min(cfg.getPrimroseCap(), water + cfg.getPrimroseWater()));
        player.sendMessage(Message.translation("server.waterOfArrakis.primroseDrink"));
    }
}
