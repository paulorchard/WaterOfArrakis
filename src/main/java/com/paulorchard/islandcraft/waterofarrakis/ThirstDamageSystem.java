package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Level;

/**
 * Deals the thirst damage that {@link WaterSystem} has decided on. It is a per-entity system because the damage
 * path (the same call vanilla drowning and the Weather of Arrakis storm use, {@code DamageSystems.executeDamage})
 * needs a command buffer, which only a per-entity system gets. The amount is queued by the water simulation and
 * dealt here on the next pass.
 */
final class ThirstDamageSystem extends EntityTickingSystem<EntityStore> {

    /** Damage cause asset: no armour or resistance reduction, no durability loss. */
    static final String CAUSE_ID = "Arrakis_Thirst";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The source of the damage, recognisable by other code with {@code damage.getSource() instanceof Source}. */
    static final class Source implements Damage.Source {
        static final Source INSTANCE = new Source();

        private Source() {
        }

        @Override
        public Message getDeathMessage(Damage damage, Ref<EntityStore> victim, ComponentAccessor<EntityStore> accessor) {
            PlayerRef player = accessor.getComponent(victim, PlayerRef.getComponentType());
            return Message.translation("server.waterOfArrakis.thirstDeath")
                    .param("player", player != null ? player.getUsername() : "?");
        }
    }

    private final WaterSystem water;
    private final Query<EntityStore> query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType());
    private boolean missingCauseReported;

    ThirstDamageSystem(WaterSystem water) {
        this.water = water;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        float amount = water.takePendingDamage(player.getUuid());
        if (amount <= 0f || chunk.getArchetype().contains(DeathComponent.getComponentType())) {
            return;
        }
        DamageCause cause = DamageCause.getAssetMap().getAsset(CAUSE_ID);
        if (cause == null) {
            if (!missingCauseReported) {
                missingCauseReported = true;
                LOGGER.at(Level.SEVERE).log("Damage cause '%s' is not loaded, so thirst cannot hurt anyone", CAUSE_ID);
            }
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        DamageSystems.executeDamage(ref, commandBuffer, new Damage(Source.INSTANCE, cause, amount));
    }
}
