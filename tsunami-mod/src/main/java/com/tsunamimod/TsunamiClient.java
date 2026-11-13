package com.tsunamimod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client side: while the server says we are inside the wave, fade a murky green-brown
 * tint over the whole screen (like the clip), and fade it out again afterwards.
 */
public class TsunamiClient implements ClientModInitializer {
    private static final int SIGNAL_TIMEOUT_TICKS = 10;

    private static int ticksSinceSignal = 1000;
    private static float tint = 0.0f; // 0..1, smoothed

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WaveOverlayPayload.ID, (payload, context) -> {
            ticksSinceSignal = 0;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticksSinceSignal++;
            float target = ticksSinceSignal < SIGNAL_TIMEOUT_TICKS ? 1.0f : 0.0f;
            tint += (target - tint) * (target > tint ? 0.25f : 0.08f); // quick fade in, slow fade out
            if (tint < 0.002f) tint = 0.0f;
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (tint <= 0.0f) return;
            int alpha = (int) (tint * 200.0f);
            int argb = (alpha << 24) | 0x4A5A2E; // murky olive green
            drawContext.fill(0, 0, drawContext.getScaledWindowWidth(), drawContext.getScaledWindowHeight(), argb);
        });
    }
}
