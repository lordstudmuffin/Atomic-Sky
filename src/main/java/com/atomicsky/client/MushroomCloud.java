package com.atomicsky.client;

import com.atomicsky.weather.WeatherBridge;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import dev.protomanly.pmweather.weather.VolumetricSmokeParticle;
import dev.protomanly.pmweather.weather.WeatherHandlerClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A mushroom cloud, built from the weather mod's own volumetric smoke (the raymarched puffs its wildfires make).
 * A few dozen big puffs: a glowing fireball that cools, a stem that rises, and a cap that spreads out and flattens.
 * Positions are animated here each tick; the weather mod renders them and drifts them with its wind. One cloud per
 * detonation; a cloud can also be started part-way through its life for a player who missed the flash.
 */
public final class MushroomCloud {
    private static final RandomSource RAND = RandomSource.create();
    private static final List<MushroomCloud> CLOUDS = new ArrayList<>();
    private static final int FADE_TICKS = 1200;    // a minute of thinning once the fallout has stopped
    /**
     * The weather mod keeps at most 55 puffs and retires the oldest past that, so every cloud standing at once has to
     * fit under the cap together: the first cloud gets the full set, later ones are scaled down to what is left, and
     * once fewer than six puffs are free a new detonation gets no cloud rather than tearing holes in the others.
     */
    private static final int PUFF_CAP = 55;
    private static final int[] FULL = {3, 8, 18};

    private final List<Puff> puffs = new ArrayList<>();
    private final int detonationId;
    private final int lifetime;                    // ticks the cloud stays up before thinning on its own
    private final Vec3 origin;
    private final double stemHeight, capRadius;
    private final WeatherHandlerClient weather;
    private int age;
    private int fadeStart = -1;                    // age at which the fallout was seen to be done
    private double driftX, driftZ;

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

    private MushroomCloud(WeatherHandlerClient weather, Vec3 center, int yield, int id, int lifetime, int[] counts) {
        this.weather = weather;
        this.origin = center;
        this.detonationId = id;
        this.lifetime = lifetime;
        this.stemHeight = yield * 4.5 + 40;
        this.capRadius = yield * 2.6;
        float stemSize = (float) (yield * 1.8);
        float capSize = (float) (yield * 3.4);
        // Fireball
        for (int i = 0; i < counts[0]; i++) {
            float sz = (float) (yield * 2.4);
            VolumetricSmokeParticle p = weather.spawnSmoke(center, sz, 0.95F, 5.0F, lifetime);
            puffs.add(new Puff(p, 0, RAND.nextDouble() * Math.PI * 2, RAND.nextDouble() * 0.4, 0, RAND.nextDouble(), sz, 0.95F, 5.0F));
        }
        // Stem
        for (int i = 0; i < counts[1]; i++) {
            VolumetricSmokeParticle p = weather.spawnSmoke(center, stemSize, 0.85F, 2.5F, lifetime);
            puffs.add(new Puff(p, 1, RAND.nextDouble() * Math.PI * 2, RAND.nextDouble() * 0.35, (i + 0.5) / counts[1], RAND.nextDouble(), stemSize, 0.85F, 2.5F));
        }
        // Cap
        for (int i = 0; i < counts[2]; i++) {
            VolumetricSmokeParticle p = weather.spawnSmoke(center, capSize, 0.9F, 2.0F, lifetime);
            double radial = i < counts[2] / 4 ? RAND.nextDouble() * 0.3 : 0.45 + RAND.nextDouble() * 0.55;
            puffs.add(new Puff(p, 2, RAND.nextDouble() * Math.PI * 2, radial, 1.0, RAND.nextDouble(), capSize, 0.9F, 2.0F));
        }
    }

    public static void start(Vec3 center, int yield, int id) {
        startAged(center, yield, id, 0);
    }

    /** Start a cloud that is already {@code age} ticks old (a player who joined after the flash sees it fully grown). */
    public static void startAged(Vec3 center, int yield, int id, int age) {
        if (id >= 0 && has(id)) return;
        WeatherHandlerClient weather;
        try {
            weather = GameBusClientEvents.getClientWeather();
        } catch (Throwable t) {
            return;
        }
        if (weather == null) return;
        int lifetime = Math.max(600, ClientState.cloudLifetime - age);
        int[] counts = countsFor(puffsInUse());
        if (counts == null) return;
        try {
            MushroomCloud cloud = new MushroomCloud(weather, center, yield, id, lifetime, counts);
            cloud.age = Math.max(0, age);
            CLOUDS.add(cloud);
            // Puffs ease toward their targets, so an aged cloud needs a few ticks to take shape; snap it there.
            for (int i = 0; i < 40; i++) cloud.place(1.0);
        } catch (Throwable t) {
            // The weather mod refused to spawn smoke (no renderer yet, or a shader problem): no cloud this time.
        }
    }

    private static int puffsInUse() {
        int n = 0;
        for (MushroomCloud c : CLOUDS) n += c.puffs.size();
        return n;
    }

    /** Puff counts (fireball, stem, cap) that fit in what the cap leaves free, or null when that is fewer than six. */
    private static int[] countsFor(int inUse) {
        int free = PUFF_CAP - inUse;
        int full = FULL[0] + FULL[1] + FULL[2];
        if (free >= full) return FULL;
        if (free < 6) return null;
        double scale = (double) free / full;
        int[] c = new int[]{Math.max(1, (int) (FULL[0] * scale)), Math.max(1, (int) (FULL[1] * scale)), Math.max(2, (int) (FULL[2] * scale))};
        while (c[0] + c[1] + c[2] > free) c[2]--;
        return c;
    }

    public static boolean has(int id) {
        for (MushroomCloud c : CLOUDS) if (c.detonationId == id) return true;
        return false;
    }

    public static void tick(ClientLevel level) {
        if (CLOUDS.isEmpty()) return;
        Iterator<MushroomCloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            MushroomCloud c = it.next();
            if (!c.tickOne(level)) {
                c.kill();
                it.remove();
            }
        }
    }

    /** One tick of animation; false once the cloud has thinned away. */
    private boolean tickOne(ClientLevel level) {
        age++;
        // The server says when this detonation's fallout is over; the cloud then thins out over a minute and goes.
        if (fadeStart < 0 && detonationId >= 0) {
            boolean found = false, done = false;
            for (com.atomicsky.net.DetonationSyncPayload.Entry e : ClientState.entries) {
                if (e.id() == detonationId) { found = true; done = e.falloutDone(); }
            }
            if (ClientState.lastSyncServerTick > 0 && (done || (!found && age > 200))) fadeStart = age;
        }
        if (fadeStart >= 0 && age > fadeStart + FADE_TICKS) return false;
        if (fadeStart < 0 && age > lifetime + 100) fadeStart = age;
        // Drift the whole cloud with the weather mod's wind at cap height.
        if (age % 20 == 1) {
            Vec3 wind = WeatherBridge.windMph(level, new Vec3(origin.x + driftX, origin.y + stemHeight, origin.z + driftZ));
            // A slight lean with the wind only: the column stays over the crater.
            driftX += wind.x * WeatherBridge.MPH_TO_BLOCKS_PER_TICK * 20 * 0.06;
            driftZ += wind.z * WeatherBridge.MPH_TO_BLOCKS_PER_TICK * 20 * 0.06;
            double lean = Math.hypot(driftX, driftZ), maxLean = capRadius * 0.5;
            if (lean > maxLean) { driftX *= maxLean / lean; driftZ *= maxLean / lean; }
        }
        keepAlive();
        place(0.12);
        return true;
    }

    /** Moves every puff toward where it belongs at the current age; {@code ease} 1.0 snaps, 0.12 glides. */
    private void place(double ease) {
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
            Vec3 cur = p.position == null ? origin : p.position;
            p.position = cur.add(target.subtract(cur).scale(ease));
            p.velocity = Vec3.ZERO;
        }
    }

    /**
     * The weather mod keeps at most 55 volumetric puffs and retires the oldest when a new one is spawned, so a few
     * seconds of wildfire smoke would wipe the cloud out. Every tick our puffs are moved to the back of its list (so
     * the wildfire smoke is what gets retired), and any puff that still died (or was never spawned) is replaced.
     */
    private void keepAlive() {
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

    private void kill() {
        for (Puff puff : puffs) {
            try {
                if (puff.p != null && !puff.p.dead) puff.p.startDeath();
            } catch (Throwable ignored) {
            }
        }
        puffs.clear();
    }

    public static void clear() {
        for (MushroomCloud c : CLOUDS) c.kill();
        CLOUDS.clear();
    }
}
