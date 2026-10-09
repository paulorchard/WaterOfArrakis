package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Interaction type {@code Arrakis_Flask}: what using a Litrejon does once the drinking animation has run. If the
 * player is looking at water (or another registered {@link Litrejon.FillSource} offers some) and the flask has
 * room, it is filled. Otherwise the player drinks: min(LitrejonDrinkAmount, room left in the player, units left).
 * An empty flask, or a player already at 100 water, drinks nothing and keeps the flask.
 */
public class FlaskInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Flask";

    public static final BuilderCodec<FlaskInteraction> CODEC = BuilderCodec
            .builder(FlaskInteraction.class, FlaskInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        ItemStack held = context.getHeldItem();
        if (buffer == null || ref == null || !Litrejon.isLitrejon(held)) {
            return;
        }
        PlayerRef player = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        WaterOfArrakisPlugin plugin = WaterOfArrakisPlugin.get();
        WaterOfArrakisConfig cfg = plugin.config();
        World world = buffer.getExternalData().getWorld();
        double amount = Litrejon.getAmount(held);
        double room = Litrejon.capacity() - amount;

        if (room > 0) {
            double added = fillFrom(plugin, player, ref, buffer, world, cfg, room);
            if (added > 0) {
                store(context, Litrejon.withAmount(held, amount + added));
                player.sendMessage(Message.translation("server.waterOfArrakis.flaskFilled"));
                return;
            }
        }

        WaterService service = plugin.service();
        double water = service.getWater(player);
        if (amount <= 0) {
            player.sendMessage(Message.translation("server.waterOfArrakis.flaskEmpty"));
            return;
        }
        if (water >= WaterService.MAX) {
            player.sendMessage(Message.translation("server.waterOfArrakis.notThirsty"));
            return;
        }
        double drink = Math.min(cfg.getLitrejonDrinkAmount(), Math.min(WaterService.MAX - water, amount));
        service.addWater(player, drink);
        store(context, Litrejon.withAmount(held, amount - drink));
    }

    /** Units taken from the first fill source that offers any: registered sources first, then water in view. */
    private static double fillFrom(WaterOfArrakisPlugin plugin, PlayerRef player, Ref<EntityStore> ref,
                                   CommandBuffer<EntityStore> buffer, World world, WaterOfArrakisConfig cfg,
                                   double room) {
        for (Litrejon.FillSource source : Litrejon.fillSources()) {
            double offered = source.offer(player, ref, buffer, world, room);
            if (offered > 0) {
                return Math.min(room, offered);
            }
        }
        return lookingAtWater(buffer, ref, world, cfg) ? room : 0;
    }

    private static void store(InteractionContext context, ItemStack stack) {
        context.getHeldItemContainer().setItemStackForSlot(context.getHeldItemSlot(), stack);
    }

    /** True when a ray from the eyes reaches a water block, within FillReach, before a solid block. */
    static boolean lookingAtWater(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref, World world,
                                  WaterOfArrakisConfig cfg) {
        TransformComponent transform = buffer.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = buffer.getComponent(ref, HeadRotation.getComponentType());
        ModelComponent model = buffer.getComponent(ref, ModelComponent.getComponentType());
        if (transform == null || head == null) {
            return false;
        }
        double eye = model == null ? 1.6 : model.getModel().getEyeHeight(ref, buffer);
        Vector3d origin = new Vector3d(transform.getPosition()).add(0, eye, 0);
        Vector3d dir = new Vector3d(head.getDirection());
        Set<Integer> water = waterFluidIds(cfg);
        double step = 0.2;
        for (double d = 0; d <= cfg.getFillReach(); d += step) {
            int x = (int) Math.floor(origin.x + dir.x * d);
            int y = (int) Math.floor(origin.y + dir.y * d);
            int z = (int) Math.floor(origin.z + dir.z * d);
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) {
                return false;
            }
            if (water.contains(chunk.getFluidId(x, y, z))) {
                return true;
            }
            BlockType block = chunk.getBlockType(x, y, z);
            if (block != null && FluidTicker.isSolid(block)) {
                return false;
            }
        }
        return false;
    }

    private static Set<Integer> waterFluidIds(WaterOfArrakisConfig cfg) {
        Set<Integer> ids = new HashSet<>();
        for (String name : cfg.getWaterFluidIds()) {
            int index = Fluid.getAssetMap().getIndex(name);
            if (index != Integer.MIN_VALUE) {
                ids.add(index);
            }
        }
        return ids;
    }
}
