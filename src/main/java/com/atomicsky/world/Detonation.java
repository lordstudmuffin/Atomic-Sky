package com.atomicsky.world;

import com.atomicsky.ASConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Everything the server remembers about one detonation: where, when, how big, and where its aftermath has got to. */
public final class Detonation {
    public int id;
    public Vec3 center;
    public int yield;             // crater radius in blocks; every other distance scales from it
    public long startTick;        // level game time of the flash

    public UUID armedBy;
    public long seed;

    // Crater carving progress (shell by shell, a few thousand blocks per tick).
    public int craterShell;
    public int craterR;           // crater bowl radius in blocks
    public int craterDepth;       // crater depth at the centre
    public boolean craterDone;

    // Heat pulse
    public boolean heatDone;
    public int heatRing = -1;     // last ring of the dead zone that was swept (deterministic pass)

    // Shockwave
    public boolean shockDone;

    // Fallout plume
    public double plumeX, plumeZ;
    public double plumeRadius;
    public double plumeIntensity;
    public double windX, windZ;   // last sampled wind at the plume, mph
    public long falloutStart, falloutEnd;
    public boolean falloutDone;

    // Nuclear winter, on the weather mod's calendar (in-game days, fractional)
    public double winterStartDay, winterEndDay;
    public boolean winterDone;

    /**
     * Chunks (as ChunkPos longs) the crater dig and the dead-zone bake have already visited. A chunk that was not
     * loaded while the sweeps ran (outside the view distance, or nobody nearby) is caught up when it next loads, so
     * the crater and the dead zone are complete however small the server's view distance is.
     */
    public final java.util.Set<Long> touchedChunks = new java.util.HashSet<>();

    /** Outer radius in blocks of the ground work (crater and dead zone) that chunk catch-up has to cover. */
    public double groundWorkRadius() {
        return Math.max(craterR, deadRadius());
    }

    public Detonation() {}

    public Detonation(int id, Vec3 center, int yield, long startTick, UUID armedBy, long seed) {
        this.id = id;
        this.center = center;
        this.yield = yield;
        this.startTick = startTick;
        this.armedBy = armedBy;
        this.seed = seed;
        this.plumeX = center.x;
        this.plumeZ = center.z;
        this.plumeRadius = yield * 1.5;
        this.craterR = Math.max(2, (int) Math.round(ASConfig.CRATER_RADIUS.get() * (double) yield / ASConfig.BLAST_RADIUS.get()));
        this.craterDepth = Math.max(2, (int) Math.round(ASConfig.CRATER_DEPTH.get() * Math.sqrt((double) yield / ASConfig.BLAST_RADIUS.get())));
        this.plumeIntensity = 0;
        this.falloutStart = startTick + ASConfig.FALLOUT_DELAY_SECONDS.get() * 20L;
        this.falloutEnd = falloutStart + ASConfig.FALLOUT_MINUTES.get() * 1200L;
        this.winterDone = !ASConfig.WINTER_ENABLED.get();
    }

    public long age(long now) {
        return now - startTick;
    }

    /** Out to here every column is baked dead; the ring beyond the crater rim is (factor - 1) crater radii wide. */
    public double deadRadius() {
        return craterR * ASConfig.HEAT_RADIUS_FACTOR.get();
    }

    /** The heat fades from full at the dead radius to nothing here. */
    public double heatRadius() {
        return deadRadius() + ASConfig.HEAT_FADE_BEYOND.get();
    }

    /** 0..1 heat at a distance from the centre, before the time fade: full inside the dead zone, then falling off. */
    public double heatRel(double dist) {
        double dead = deadRadius(), heat = heatRadius();
        if (dist <= dead) return 1.0;
        if (dist >= heat) return 0.0;
        return 1.0 - (dist - dead) / (heat - dead);
    }

    public double shockRange() {
        return yield * ASConfig.SHOCKWAVE_RANGE_FACTOR.get();
    }

    /** Radius of the shock front at the given time, in blocks. */
    public double shockRadius(long now) {
        return age(now) * ASConfig.SHOCKWAVE_SPEED.get() / 20.0;
    }

    public double falloutMaxRadius() {
        return yield * ASConfig.FALLOUT_RADIUS_FACTOR.get();
    }

    /** 0..1 heat pulse strength at a time: full for the first fifth, then fading linearly to zero. */
    public double heatIntensity(long now) {
        double dur = ASConfig.HEAT_PULSE_SECONDS.get() * 20.0;
        double t = age(now) / dur;
        if (t >= 1) return 0;
        if (t < 0.2) return 1;
        return 1 - (t - 0.2) / 0.8;
    }

    /** Fallout strength of the plume at a point (0..1), falling off toward the plume edge. */
    public double falloutAt(double x, double z) {
        return falloutAt(x, z, center.x, center.z, plumeX, plumeZ, plumeRadius, plumeIntensity);
    }

    /**
     * Fallout strength at a point for a plume that has drifted from (ox,oz) to (px,pz): the plume is a capsule along
     * that path, so ash keeps falling on everything between the blast and wherever the wind has carried the cloud.
     */
    public static double falloutAt(double x, double z, double ox, double oz, double px, double pz, double radius, double intensity) {
        if (intensity <= 0 || radius <= 0) return 0;
        double vx = px - ox, vz = pz - oz;
        double len2 = vx * vx + vz * vz;
        double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - ox) * vx + (z - oz) * vz) / len2));
        double cx = ox + vx * t, cz = oz + vz * t;
        double d = Math.hypot(x - cx, z - cz);
        if (d >= radius) return 0;
        double f = 1 - d / radius;
        return intensity * f * (0.4 + 0.6 * f);
    }

    /** A random point under the plume capsule, for depositing ash. */
    public double[] randomPlumePoint(net.minecraft.util.RandomSource rand) {
        double t = rand.nextDouble();
        double cx = center.x + (plumeX - center.x) * t, cz = center.z + (plumeZ - center.z) * t;
        double ang = rand.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(rand.nextDouble()) * plumeRadius;
        return new double[]{cx + Math.cos(ang) * dist, cz + Math.sin(ang) * dist};
    }

    /** 0..1 nuclear winter strength on a given calendar day (ramps in over a day, out over the last two). */
    public double winterFactor(double day) {
        if (winterDone || winterEndDay <= winterStartDay) return 0;
        if (day < winterStartDay || day > winterEndDay) return 0;
        double in = Math.min(1, (day - winterStartDay) / 1.0);
        double out = Math.min(1, (winterEndDay - day) / 2.0);
        return Math.max(0, Math.min(in, out));
    }

    public boolean finished() {
        return craterDone && heatDone && shockDone && falloutDone && winterDone;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.putDouble("X", center.x);
        t.putDouble("Y", center.y);
        t.putDouble("Z", center.z);
        t.putInt("Yield", yield);
        t.putLong("Start", startTick);
        if (armedBy != null) t.putUUID("ArmedBy", armedBy);
        t.putLong("Seed", seed);
        t.putInt("CraterShell", craterShell);
        t.putInt("CraterR", craterR);
        t.putInt("CraterDepth", craterDepth);
        t.putBoolean("CraterDone", craterDone);
        t.putBoolean("HeatDone", heatDone);
        t.putInt("HeatRing", heatRing);
        t.putBoolean("ShockDone", shockDone);
        t.putDouble("PlumeX", plumeX);
        t.putDouble("PlumeZ", plumeZ);
        t.putDouble("PlumeRadius", plumeRadius);
        t.putDouble("PlumeIntensity", plumeIntensity);
        t.putDouble("WindX", windX);
        t.putDouble("WindZ", windZ);
        t.putLong("FalloutStart", falloutStart);
        t.putLong("FalloutEnd", falloutEnd);
        t.putBoolean("FalloutDone", falloutDone);
        t.putDouble("WinterStartDay", winterStartDay);
        t.putDouble("WinterEndDay", winterEndDay);
        t.putBoolean("WinterDone", winterDone);
        long[] chunks = new long[touchedChunks.size()];
        int i = 0;
        for (long c : touchedChunks) chunks[i++] = c;
        t.putLongArray("TouchedChunks", chunks);
        return t;
    }

    public static Detonation load(CompoundTag t) {
        Detonation d = new Detonation();
        d.id = t.getInt("Id");
        d.center = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
        d.yield = t.getInt("Yield");
        d.startTick = t.getLong("Start");
        d.armedBy = t.hasUUID("ArmedBy") ? t.getUUID("ArmedBy") : null;
        d.seed = t.getLong("Seed");
        d.craterShell = t.getInt("CraterShell");
        d.craterR = t.contains("CraterR") ? t.getInt("CraterR") : d.yield;
        d.craterDepth = t.contains("CraterDepth") ? t.getInt("CraterDepth") : d.yield;
        d.craterDone = t.getBoolean("CraterDone");
        d.heatDone = t.getBoolean("HeatDone");
        d.heatRing = t.contains("HeatRing") ? t.getInt("HeatRing") : -1;
        d.shockDone = t.getBoolean("ShockDone");
        d.plumeX = t.getDouble("PlumeX");
        d.plumeZ = t.getDouble("PlumeZ");
        d.plumeRadius = t.getDouble("PlumeRadius");
        d.plumeIntensity = t.getDouble("PlumeIntensity");
        d.windX = t.getDouble("WindX");
        d.windZ = t.getDouble("WindZ");
        d.falloutStart = t.getLong("FalloutStart");
        d.falloutEnd = t.getLong("FalloutEnd");
        d.falloutDone = t.getBoolean("FalloutDone");
        d.winterStartDay = t.getDouble("WinterStartDay");
        d.winterEndDay = t.getDouble("WinterEndDay");
        d.winterDone = t.getBoolean("WinterDone");
        for (long c : t.getLongArray("TouchedChunks")) d.touchedChunks.add(c);
        return d;
    }
}
