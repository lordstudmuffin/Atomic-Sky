package com.atomicsky.net;

import com.atomicsky.AtomicSky;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server to client: a bomb just went off here. The client works out how bright the flash is for its own camera. */
public record FlashPayload(int id, Vec3 center, int yield, float range) implements CustomPacketPayload {
    public static final Type<FlashPayload> TYPE = new Type<>(AtomicSky.id("flash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id);
                buf.writeDouble(p.center.x);
                buf.writeDouble(p.center.y);
                buf.writeDouble(p.center.z);
                buf.writeVarInt(p.yield);
                buf.writeFloat(p.range);
            },
            buf -> new FlashPayload(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
