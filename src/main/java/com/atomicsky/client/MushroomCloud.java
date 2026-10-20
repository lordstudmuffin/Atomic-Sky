package com.atomicsky.client;

import com.atomicsky.weather.WeatherBridge;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.weather.VolumetricSmokeParticle;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The mushroom cloud, built from the weather mod's own volumetric smoke (the raymarched puffs its wildfires make).
 * A few dozen big puffs: a glowing fireball that cools, a stem that rises, and a cap that spreads out and flattens.
 * Positions are animated here each tick; the weather mod renders them and drifts them with its wind.
 */
public final class MushroomCloud {
    private static final RandomSource RAND = RandomSource.create();
    private static final List<Puff> puffs = new ArrayList<>();
    private static final int FADE_TICKS = 1200;    // a minute of thinning once the fallout has stopped
    private static int lifetime = 7200;            // set at start: the cloud stays up until fallout ends, then thins
    private static int detonationId = -1;
    private static int fadeStart = -1;             // age at which the fallout was seen to be done
    private static Vec3 origin;
    private static int age;
    private static double driftX, driftZ;
    private static double stemHeight, capRadius;

    private static final class Puff {
        VolumetricSmokeParticle p;
        final int kind;
        final double angle, radial, heightFrac, jitter;
        final float size, opacity, brightness;
        Puff(VolumetricSmokeParticle p, int kind, double angle, double radial, double heightFrac, double jitter, float size, float opacity, float brightness) {
            this.p = p; this.kind = kind; this.angle = angle; this.radial = radial; this.heightFrac = heightFrac; this.jitter = jitter;
            this.size = size; this.opacity = opacity; this.brightness = brightness;
        }
    }
    private static WeatherHandlerClient weatherRef;

    public static void start(Vec3 center, int yield) {
        start(center, yield, -1);
    }

    public static void start(Vec3 center, int yield, int id) {
        clear();
        detonationId = id;
        fadeStart = -1;
        try {
            // Up for the whole fallout: delay + duration + the server's minute of thinning, plus a margin.
            lifetime = (com.atomicsky.ASConfig.FALLOUT_DELAY_SECONDS.get() * 20 + com.atomicsky.ASConfig.FALLOUT_MINUTES.get() * 1200 + 2400);
        } catch (Throwable t) {
            lifetime = 30000;
        }
        WeatherHandlerClient weather;
        try {
            weather = GameBusClientEvents.getClientWeather();
        } catch (Throwable t) {
            return;
        }
        if (weather == null) return;
        weatherRef = weather;
        origin = center;
        age = 0;
        driftX = driftZ = 0;
        stemHeight = yield * 4.5 + 40;
        capRadius = yield * 2.6;
        float stemSize = (float) (yield * 1.8);
        float capSize = (float) (yield * 3.4);
        try {
            // Fireball
            for (int i = 0; i < 3; i++) {
                float sz = (float) (yield * 2.4);
                VolumetricSmokeParticle p = weather.spawnSmoke(center, sz, 0.95F, 5.0F, lifetime);
                puffs.add(new Puff(p, 0, RAND.nextDouble() * Math.PI * 2, RAND.nextDouble() * 0.4, 0, RAND.nextDouble(), sz, 0.95F, 5.0F));
            }
            // Stem
            for (int i = 0; i < 8; i++) {
                VolumetricSmokeParticle p = weather.spawnSmoke(center, stemSize, 0.85F, 2.5F, lifetime);
                puffs.add(new Puff(p, 1, RAND.nextDouble() * Math.PI * 2, RAND.nextDouble() * 0.35, (i + 0.5) / 8.0, RAND.nextDouble(), stemSize, 0.85F, 2.5F));
            }
            // Cap
            for (int i = 0; i < 18; i++) {
                VolumetricSmokeParticle p = weather.spawnSmoke(center, capSize, 0.9F, 2.0F, lifetime);
                double radial = i < 4 ? RAND.nextDouble() * 0.3 : 0.45 + RAND.nextDouble() * 0.55;
                puffs.add(new Puff(p, 2, RAND.nextDouble() * Math.PI * 2, radial, 1.0, RAND.nextDouble(), capSize, 0.9F, 2.0F));
            }
        } catch (Throwable t) {
            clear();
        }
    }

    public static void tick(ClientLevel level) {
        if (puffs.isEmpty() || origin == null) return;
        age++;
        // The server says when this detonation's fallout is over; the cloud then thins out over a minute and goes.
        if (fadeStart < 0 && detonationId >= 0) {
            boolean found = false, done = false;
            for (com.atomicsky.net.DetonationSyncPayload.Entry e : ClientState.entries) {
                if (e.id() == detonationId) { found = true; done = e.falloutDone(); }
            }
            if (ClientState.lastSyncServerTick > 0 && (done || (!found && age > 200))) fadeStart = age;
        }
        if (fadeStart >= 0 && age > fadeStart + FADE_TICKS) {
            clear();
            return;
        }
        if (fadeStart < 0 && age > lifetime + 100) fadeStart = age;
        // Drift the whole cloud with the weather mod's wind at cap height.
        if (age % 20 == 1) {
            Vec3 wind = WeatherBridge.windMph(level, new Vec3(origin.x + driftX, origin.y + stemHeight, origin.z + driftZ));
            // A slight lean with the wind only: the column stays over the crater.
            driftX += wind.x * WeatherBridge.MPH_TO_BLOCKS_PER_TICK * 20 * 0.06;
            driftZ += wind.z * WeatherBridge.MPH_TO_BLOCKS_PER_TICK * 20 * 0.06;
            double lean = Math.hypot(driftX, driftZ), maxLean = capRadius * 0.5;
            if (lean > maxLean) { driftX *= maxLean / lean; driftZ *= maxLean / lean; }
        } else {
            // Smooth the once-a-second step over the following ticks is not needed: the puffs lag behind anyway.
        }
        keepAlive(level);
        double rise = ease(Math.min(1.0, age / 700.0));          // 35 s to full height
        double spread = ease(Math.min(1.0, Math.max(0, age - 300) / 900.0));   // cap spreads after the stem is up
        double sag = Math.min(1.0, Math.max(0, age - 2400) / 9600.0);          // then slowly flattens and lowers, and holds
        double wobble = Math.sin(age * 0.02);
        for (Puff puff : puffs) {
            VolumetricSmokeParticle p = puff.p;
            if (p == null || p.dead) continue;
            double x, y, z;
            switch (puff.kind) {
                case 0 -> { // fireball: grows and lifts into the bottom of the cap
                    double h = stemHeight * 0.75 * rise + sag * -20;
                    double r = puff.radial * capRadius * 0.5 * (0.3 + rise);
                    x = Math.cos(puff.angle + wobble * 0.3) * r;
                    z = Math.sin(puff.angle + wobble * 0.3) * r;
                    y = h + Math.sin(age * 0.05 + puff.jitter * 6) * 3;
                }
                case 1 -> { // stem: a column that rises in order, bottom puffs stay low
                    double top = stemHeight * rise;
                    double h = top * puff.heightFrac + 6;
                    double r = puff.radial * capRadius * 0.45 * (0.6 + 0.4 * puff.heightFrac);
                    double a = puff.angle + age * 0.004 * (puff.jitter - 0.5);
                    x = Math.cos(a) * r;
                    z = Math.sin(a) * r;
                    y = h - sag * 12 * puff.heightFrac;
                }
                default -> { // cap: lifts with the column then spreads outward and droops at the rim
                    double h = stemHeight * rise + capRadius * 0.35 * (1 - puff.radial) - sag * 30;
                    double r = puff.radial * capRadius * (0.25 + 0.75 * spread) * (1 + sag * 0.6);
                    double a = puff.angle + age * 0.003 * (puff.jitter - 0.5);
                    x = Math.cos(a) * r;
                    z = Math.sin(a) * r;
                    y = h - puff.radial * capRadius * 0.25 * spread + Math.sin(age * 0.03 + puff.jitter * 7) * 2;
                }
            }
            Vec3 target = new Vec3(origin.x + driftX + x, origin.y + y, origin.z + driftZ + z);
            // Move toward the target rather than snapping, so the once-a-second wind step and any jitter stay smooth.
            Vec3 cur = p.position;
            Vec3 next = cur.add(target.subtract(cur).scale(0.12));
            p.position = next;
            p.velocity = Vec3.ZERO;
        }
    }

    /**
     * The weather mod keeps at most 55 volumetric puffs and retires the oldest when a new one is spawned, so a few
     * seconds of wildfire smoke would wipe the cloud out. Every tick our puffs are moved to the back of its list (so
     * the wildfire smoke is what gets retired), and any puff that still died (or was never spawned) is replaced.
     */
    private static void keepAlive(ClientLevel level) {
        WeatherHandlerClient weather = weatherRef;
        if (weather == null) return;
        try {
            List<VolumetricSmokeParticle> list = weather.smokeParticles;
            if (list == null) return;
            boolean fading = fadeStart >= 0;
            if (fading && age % 40 == 0) {
                // Thin out: retire one puff every two seconds, cap first.
                for (int i = puffs.size() - 1; i >= 0; i--) {
                    VolumetricSmokeParticle p = puffs.get(i).p;
                    if (p != null && !p.dead) { p.startDeath(); break; }
                }
            }
            for (Puff puff : puffs) {
                VolumetricSmokeParticle p = puff.p;
                if (p != null && !p.dead) {
                    if (list.remove(p)) list.add(p);
                } else if (!fading && age % 10 == 0) {
                    Vec3 at = p != null && p.position != null ? p.position : origin;
                    int life = Math.max(600, lifetime - age);
                    VolumetricSmokeParticle np = weather.spawnSmoke(at, puff.size, puff.opacity, puff.brightness, life);
                    puff.p = np;
                    if (np != null && list.remove(np)) list.add(np);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static double ease(double t) {
        return t < 0 ? 0 : t > 1 ? 1 : 1 - (1 - t) * (1 - t) * (1 - t);
    }

    public static void clear() {
        for (Puff puff : puffs) {
            try {
                if (puff.p != null && !puff.p.dead) puff.p.startDeath();
            } catch (Throwable ignored) {
            }
        }
        puffs.clear();
        origin = null;
        weatherRef = null;
        detonationId = -1;
        fadeStart = -1;
    }

    private MushroomCloud() {}
}
