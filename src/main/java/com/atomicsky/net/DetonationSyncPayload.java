package com.atomicsky.net;

import com.atomicsky.AtomicSky;
import com.atomicsky.weather.WeatherBridge;
import com.atomicsky.world.Detonation;
import com.atomicsky.world.DetonationManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server to client, once a second: everything the client needs to draw the aftermath of every active detonation. */
public record DetonationSyncPayload(long serverTick, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<DetonationSyncPayload> TYPE = new Type<>(AtomicSky.id("sync"));

    public record Entry(int id, Vec3 center, int yield, long startTick, double plumeX, double plumeZ, double plumeRadius,
                        double plumeIntensity, double windX, double windZ, double heatRadius, double deadRadius, double heatIntensity,
                        double shockRadius, double shockRange, boolean shockDone, boolean falloutDone, double winterFactor) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, DetonationSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarLong(p.serverTick);
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) {
                    buf.writeVarInt(e.id);
                    buf.writeDouble(e.center.x);
                    buf.writeDouble(e.center.y);
                    buf.writeDouble(e.center.z);
                    buf.writeVarInt(e.yield);
                    buf.writeVarLong(e.startTick);
                    buf.writeDouble(e.plumeX);
                    buf.writeDouble(e.plumeZ);
                    buf.writeDouble(e.plumeRadius);
                    buf.writeDouble(e.plumeIntensity);
                    buf.writeDouble(e.windX);
                    buf.writeDouble(e.windZ);
                    buf.writeDouble(e.heatRadius);
                    buf.writeDouble(e.deadRadius);
                    buf.writeDouble(e.heatIntensity);
                    buf.writeDouble(e.shockRadius);
                    buf.writeDouble(e.shockRange);
                    buf.writeBoolean(e.shockDone);
                    buf.writeBoolean(e.falloutDone);
                    buf.writeDouble(e.winterFactor);
                }
            },
            buf -> {
                long tick = buf.readVarLong();
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Entry(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(),
                            buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readDouble()));
                }
                return new DetonationSyncPayload(tick, list);
            });

    public static DetonationSyncPayload of(ServerLevel level, DetonationManager manager) {
        long now = level.getGameTime();
        double day = WeatherBridge.calendarDay(level);
        List<Entry> list = new ArrayList<>();
        for (Detonation d : manager.all()) {
            list.add(new Entry(d.id, d.center, d.yield, d.startTick, d.plumeX, d.plumeZ, d.plumeRadius, d.plumeIntensity,
                    d.windX, d.windZ, d.heatRadius(), d.deadRadius(), d.heatIntensity(now), d.shockRadius(now), d.shockRange(), d.shockDone, d.falloutDone,
                    d.winterFactor(day)));
        }
        return new DetonationSyncPayload(now, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
