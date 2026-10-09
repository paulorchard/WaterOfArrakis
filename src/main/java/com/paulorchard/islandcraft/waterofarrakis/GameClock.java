package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.LocalDateTime;

/** The in-game time of a world: the clock hour for "evening" and the epoch day for the daily rollover. */
final class GameClock {

    private GameClock() {
    }

    /** Clock hour 0..24 with decimals (17.5 is half past five in the afternoon). */
    static double hour(Store<EntityStore> store) {
        LocalDateTime now = store.getResource(WorldTimeResource.getResourceType()).getGameDateTime();
        return now.getHour() + now.getMinute() / 60.0 + now.getSecond() / 3600.0;
    }

    /** Days since the game epoch; changes when the in-game clock passes midnight. */
    static long day(Store<EntityStore> store) {
        return store.getResource(WorldTimeResource.getResourceType()).getGameDateTime().toLocalDate().toEpochDay();
    }
}
