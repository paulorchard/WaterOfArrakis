package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Interaction type {@code Arrakis_Burrowbush}, the block's {@code Use} interaction. Gives BurrowbushBulbs Burrow
 * Bulbs once per bush per in-game day. A used bush is switched to the Depleted block state (saved in the chunk) and
 * recorded in {@link BurrowbushUsage} (saved with the world) so {@link BurrowbushReset} can restore it at the daily
 * rollover, even after a restart. A full bag does not use the bush up.
 */
public class BurrowbushInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Burrowbush";
    static final String BULB = "Arrakis_Burrow_Bulb";
    static final String DEPLETED = "Depleted";

    public static final BuilderCodec<BurrowbushInteraction> CODEC = BuilderCodec
            .builder(BurrowbushInteraction.class, BurrowbushInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        BlockPosition target = context.getTargetBlock();
        if (buffer == null || ref == null || target == null) {
            return;
        }
        PlayerRef player = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        World world = buffer.getExternalData().getWorld();
        Store<EntityStore> store = world.getEntityStore().getStore();
        BurrowbushUsage usage = BurrowbushUsage.of(store);
        String key = BurrowbushUsage.key(target.x, target.y, target.z);
        long today = GameClock.day(store);
        Long usedOn = usage.used.get(key);
        if (usedOn != null && usedOn >= today) {
            player.sendMessage(Message.translation("server.waterOfArrakis.bushDepleted"));
            return;
        }

        int bulbs = WaterOfArrakisPlugin.get().config().getBurrowbushBulbs();
        CombinedItemContainer bag = InventoryComponent.getCombined(buffer, ref,
                InventoryComponent.Hotbar.getComponentType(), InventoryComponent.Storage.getComponentType());
        if (!bag.addItemStack(new ItemStack(BULB, bulbs)).succeeded()) {
            player.sendMessage(Message.translation("server.waterOfArrakis.bagFull"));
            return;
        }
        usage.used.put(key, today);
        BurrowbushReset.setDepleted(world, target.x, target.y, target.z, true);
        player.sendMessage(Message.translation("server.waterOfArrakis.bushBulb"));
    }
}
