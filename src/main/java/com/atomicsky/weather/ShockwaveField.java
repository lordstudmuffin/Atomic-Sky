package com.atomicsky.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blast wind that the shockwave adds to the weather mod's wind field. The server keeps a per-tick snapshot of
 * the active shock fronts per dimension here, the client one for the level it is in;
 * {@link com.atomicsky.mixin.WindEngineMixin} adds the result to every {@code WindEngine.getWind} call, so the
 * weather mod's own entity pushing, debris, smoke and particles all feel it.
 */
public final class ShockwaveField {
    /** One expanding ring of wind. All distances in blocks, speeds in mph (the weather mod's wind unit). */
    public record Front(Vec3 center, double radius, double band, double peakMph, double blastRadius, double range) {
        /** Wind vector at a point, or null when the point is outside the band. */
        public Vec3 windAt(Vec3 p) {
            double dx = p.x - center.x, dz = p.z - center.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 0.5) return null;
            double off = Math.abs(d - radius);
            if (off > band) return null;
            double shape = 1.0 - off / band;                       // triangular profile across the band
            double falloff = Math.min(1.0, blastRadius / Math.max(d, blastRadius)); // 1/r beyond the crater edge
            double tail = Math.max(0.0, 1.0 - radius / range);      // dies out at the configured range
            double mph = peakMph * shape * falloff * tail;
            if (mph < 1.0) return null;
            double inv = 1.0 / d;
            // Mostly outward, a little upward: the front lifts things as it passes.
            return new Vec3(dx * inv * mph, mph * 0.15, dz * inv * mph);
        }
    }

    private static final Map<ResourceKey<Level>, List<Front>> SERVER_FRONTS = new ConcurrentHashMap<>();
    private static volatile List<Front> clientFronts = List.of();

    /** Server side: the fronts active in one dimension (a nuke in the Nether must not blow wind in the Overworld). */
    public static void setServerFronts(ResourceKey<Level> dimension, List<Front> fronts) {
        if (fronts.isEmpty()) SERVER_FRONTS.remove(dimension);
        else SERVER_FRONTS.put(dimension, new ArrayList<>(fronts));
    }

    /** Forget every server front (server stopped, or a different world is being opened). */
    public static void clearServer() {
        SERVER_FRONTS.clear();
    }

    public static void setClientFronts(List<Front> fronts) {
        clientFronts = fronts.isEmpty() ? List.of() : new ArrayList<>(fronts);
    }

    public static List<Front> fronts(Level level) {
        if (level.isClientSide) return clientFronts;
        List<Front> f = SERVER_FRONTS.get(level.dimension());
        return f == null ? List.of() : f;
    }

    /** Total blast wind at a point for the given level; Vec3.ZERO when no front covers it. Cheap when nothing is active. */
    public static Vec3 windAt(Vec3 pos, Level level) {
        List<Front> fronts = fronts(level);
        if (fronts.isEmpty()) return Vec3.ZERO;
        Vec3 total = Vec3.ZERO;
        for (Front f : fronts) {
            Vec3 w = f.windAt(pos);
            if (w != null) total = total.add(w);
        }
        if (total == Vec3.ZERO) return total;
        // Under a roof the front is muffled rather than stopped: blast wind still finds its way in.
        try {
            if (!level.canSeeSky(BlockPos.containing(pos))) total = total.scale(0.35);
        } catch (Throwable ignored) {
            // Called from a render or worker thread while a chunk is going away: keep the open-air value.
        }
        return total;
    }

    private ShockwaveField() {}
}
