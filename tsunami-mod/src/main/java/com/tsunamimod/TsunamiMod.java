package com.tsunamimod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class TsunamiMod implements ModInitializer {
    public static final String MOD_ID = "tsunami";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(WaveOverlayPayload.ID, WaveOverlayPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TsunamiCommand.register(dispatcher));

        ServerTickEvents.END_WORLD_TICK.register(TsunamiManager::tick);

        // never leave wave entities behind in the world save
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TsunamiManager.clearAll());
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(WaveVisuals.TAG)) {
                world.getServer().execute(() -> TsunamiManager.cleanupIfOrphan(entity));
            }
        });
    }
}
