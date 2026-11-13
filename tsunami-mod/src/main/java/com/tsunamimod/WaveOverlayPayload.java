package com.tsunamimod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent to a player while they are inside the wave, so the client can tint the screen murky green. */
public record WaveOverlayPayload(float intensity) implements CustomPayload {
    public static final CustomPayload.Id<WaveOverlayPayload> ID =
            new CustomPayload.Id<>(Identifier.of("tsunami", "overlay"));

    public static final PacketCodec<RegistryByteBuf, WaveOverlayPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.FLOAT, WaveOverlayPayload::intensity, WaveOverlayPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
