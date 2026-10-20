package com.atomicsky.client;

import com.atomicsky.ASClientConfig;
import com.atomicsky.net.DetonationSyncPayload;
import com.atomicsky.net.FlashPayload;
import com.atomicsky.weather.ShockwaveField;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** What the client knows about active detonations (from the server's once-a-second sync) and the current flash. */
public final class ClientState {
    public static volatile List<DetonationSyncPayload.Entry> entries = List.of();
    public static long lastSyncServerTick;
    public static long lastSyncClientTick;

    // Flash
    public static float flashPeak;
    public static int flashTicks;
    public static int flashDuration;
    public static Vec3 flashCenter;
    public static int flashId;

    public static void onFlash(FlashPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        flashId = p.id();
        flashCenter = p.center();
        ClientEffects.onFlash(p);
        if (ASClientConfig.MUSHROOM_CLOUD.get()) MushroomCloud.start(p.center(), p.yield(), p.id());
    }

    public static void onSync(DetonationSyncPayload p) {
        entries = p.entries();
        lastSyncServerTick = p.serverTick();
        Minecraft mc = Minecraft.getInstance();
        lastSyncClientTick = mc.level == null ? 0 : mc.level.getGameTime();
        publishFronts();
    }

    /** Estimated current server tick: the last synced tick plus the client ticks since. */
    public static long estimatedServerTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return lastSyncServerTick;
        return lastSyncServerTick + (mc.level.getGameTime() - lastSyncClientTick);
    }

    public static void publishFronts() {
        List<ShockwaveField.Front> fronts = new ArrayList<>();
        long now = estimatedServerTick();
        for (DetonationSyncPayload.Entry e : entries) {
            if (e.shockDone()) continue;
            double radius = e.shockRadius() + (now - lastSyncServerTick) * com.atomicsky.ASConfig.SHOCKWAVE_SPEED.get() / 20.0;
            if (radius - 8 > e.shockRange()) continue;
            fronts.add(new ShockwaveField.Front(e.center(), radius, 8.0, com.atomicsky.ASConfig.SHOCKWAVE_WIND_MPH.get(), e.yield(), e.shockRange()));
        }
        ShockwaveField.setClientFronts(fronts);
    }

    public static double falloutAt(double x, double z) {
        double f = 0;
        for (DetonationSyncPayload.Entry e : entries) {
            f = Math.max(f, com.atomicsky.world.Detonation.falloutAt(x, z, e.center().x, e.center().z, e.plumeX(), e.plumeZ(),
                    e.plumeRadius(), e.plumeIntensity()));
        }
        return f;
    }

    public static double heatAt(double x, double z) {
        double f = 0;
        for (DetonationSyncPayload.Entry e : entries) {
            if (e.heatIntensity() <= 0) continue;
            double d = Math.hypot(x - e.center().x, z - e.center().z);
            if (d >= e.heatRadius()) continue;
            double rel = d <= e.deadRadius() ? 1.0 : 1.0 - (d - e.deadRadius()) / Math.max(1.0, e.heatRadius() - e.deadRadius());
            f = Math.max(f, e.heatIntensity() * rel);
        }
        return f;
    }

    public static Vec3 windAt(double x, double z) {
        Vec3 best = Vec3.ZERO;
        double bestF = 0;
        for (DetonationSyncPayload.Entry e : entries) {
            double d = Math.hypot(x - e.plumeX(), z - e.plumeZ());
            double d0 = Math.hypot(x - e.center().x, z - e.center().z);
            if (Math.min(d, d0) >= e.plumeRadius() * 1.5) continue;
            if (e.plumeIntensity() > bestF) {
                bestF = e.plumeIntensity();
                best = new Vec3(e.windX(), 0, e.windZ());
            }
        }
        return best;
    }

    public static double winterFactor() {
        double f = 0;
        for (DetonationSyncPayload.Entry e : entries) f = Math.max(f, e.winterFactor());
        return f;
    }

    public static void reset() {
        entries = List.of();
        flashTicks = 0;
        flashPeak = 0;
        ShockwaveField.setClientFronts(List.of());
        MushroomCloud.clear();
    }

    private ClientState() {}
}
