package com.atomicsky.net;

import com.atomicsky.ASConfig;
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

/**
 * Server to client, once a second: everything the client needs to draw the aftermath of every active detonation.
 * The server's own settings ride along (shock speed and wind, winter drop, how long the cloud stays), so a client
 * never has to guess them from its local config file.
 */
public record DetonationSyncPayload(long serverTick, double shockSpeed, double windMph, double winterDropC, int cloudLifetime,
                                    List<Entry> entries) implements CustomPacketPayload {
    public static final Type<DetonationSyncPayload> TYPE = new Type<>(AtomicSky.id("sync"));

    public record Entry(int id, Vec3 center, int yield, long startTick, double plumeX, double plumeZ, double plumeRadius,
                        double plumeIntensity, double windX, double windZ, double heatRadius, double deadRadius, double heatIntensity,
                        double shockRadius, double shockRange, boolean shockDone, boolean falloutDone, double winterFactor) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, DetonationSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarLong(p.serverTick);
                buf.writeDouble(p.shockSpeed);
                buf.writeDouble(p.windMph);
                buf.writeDouble(p.winterDropC);
                buf.writeVarInt(p.cloudLifetime);
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
                double shockSpeed = buf.readDouble();
                double windMph = buf.readDouble();
                double winterDropC = buf.readDouble();
                int cloudLifetime = buf.readVarInt();
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Entry(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(),
                            buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readDouble()));
                }
                return new DetonationSyncPayload(tick, shockSpeed, windMph, winterDropC, cloudLifetime, list);
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
        // The cloud stands for the fallout delay, the fallout itself and the server's minute of thinning, plus a margin.
        int cloudLifetime = ASConfig.FALLOUT_DELAY_SECONDS.get() * 20 + ASConfig.FALLOUT_MINUTES.get() * 1200 + 2400;
        double winterDrop = ASConfig.WINTER_ENABLED.get() ? ASConfig.WINTER_TEMPERATURE_DROP.get() : 0;
        return new DetonationSyncPayload(now, ASConfig.SHOCKWAVE_SPEED.get(), ASConfig.SHOCKWAVE_WIND_MPH.get(), winterDrop, cloudLifetime, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
