package com.atomicsky.world;

import com.atomicsky.ASConfig;
import com.atomicsky.AtomicSky;
import com.atomicsky.block.FalloutLayerBlock;
import com.atomicsky.net.DetonationSyncPayload;
import com.atomicsky.net.FlashPayload;
import com.atomicsky.weather.ShockwaveField;
import com.atomicsky.weather.WeatherBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Per-level record of detonations; drives every phase of the aftermath each tick and syncs the clients. */
public class DetonationManager extends SavedData {
    private static final String NAME = "atomicsky_detonations";
    private static final SavedData.Factory<DetonationManager> FACTORY =
            new SavedData.Factory<>(DetonationManager::new, DetonationManager::load, null);

    private final List<Detonation> detonations = new ArrayList<>();
    private int nextId = 1;

    /** Chunks that loaded since last tick and may need the crater or dead zone caught up (never saved). */
    private final transient Deque<Long> pendingChunks = new ArrayDeque<>();
    private transient ServerLevel boundLevel;

    public static DetonationManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public List<Detonation> all() {
        return detonations;
    }

    /** The flash: starts a new detonation at the given centre. Everything else follows from the tick loop. */
    public Detonation detonate(ServerLevel level, Vec3 center, int yield, UUID armedBy) {
        long now = level.getGameTime();
        Detonation d = new Detonation(nextId++, center, yield, now, armedBy, level.random.nextLong());
        if (ASConfig.WINTER_ENABLED.get()) {
            double day = WeatherBridge.calendarDay(level);
            d.winterStartDay = day + ASConfig.WINTER_DELAY_HOURS.get() / 24.0;
            d.winterEndDay = d.winterStartDay + ASConfig.WINTER_DAYS.get();
        }
        detonations.add(d);
        setDirty();

        // Flash to everyone in range (the client decides how bright it is from distance, facing and line of sight).
        double range = ASConfig.FLASH_RANGE.get();
        FlashPayload flash = new FlashPayload(d.id, center, yield, (float) range);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(center) <= range * 1.5) PacketDistributor.sendToPlayer(p, flash);
        }
        // No server-side sound: every client plays its own boom, delayed by its distance (ClientEffects), so the sound
        // arrives after the flash the way it should, and nobody hears it twice.
        // Immediate lethal zone so nothing standing on the bomb survives the first tick.
        Crater.killInside(level, d);
        WeatherBridge.onDetonation(level, d);
        sync(level);
        return d;
    }

    /** Convenience used by the bomb block (it only knows the level through the block entity). */
    public void detonate(Vec3 center, int yield, UUID armedBy) {
        if (boundLevel != null) detonate(boundLevel, center, yield, armedBy);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getDataStorage() == null) return;
        DetonationManager m = get(level);
        m.boundLevel = level;
        m.tick(level);
    }

    private void tick(ServerLevel level) {
        if (detonations.isEmpty()) {
            pendingChunks.clear();
            if (level.getGameTime() % 100 == 0) WeatherBridge.setServerWinter(level, 0);
            return;
        }
        long now = level.getGameTime();
        Iterator<Detonation> it = detonations.iterator();
        boolean dirty = false;
        while (it.hasNext()) {
            Detonation d = it.next();
            if (!d.craterDone) Crater.tick(level, d);
            if (!d.heatDone) HeatPulse.tick(level, d, now);
            if (!d.shockDone) Shockwave.tick(level, d, now);
            if (!d.falloutDone) FalloutPlume.tick(level, d, now);
            if (!d.winterDone) NuclearWinter.tick(level, d);
            if (d.finished()) {
                it.remove();
                dirty = true;
            }
        }
        catchUpChunks(level);
        if (dirty || now % 20 == 0) {
            // A detonation that just ended must reach the clients at once, or they keep drawing it until the next relog.
            WeatherBridge.setServerWinter(level, winterFactor(level));
            sync(level);
            setDirty();
        }
    }

    /** One freshly loaded chunk per tick: dig and bake whatever part of a crater or dead zone the sweeps missed. */
    private void catchUpChunks(ServerLevel level) {
        while (!pendingChunks.isEmpty()) {
            long key = pendingChunks.poll();
            ChunkPos chunk = new ChunkPos(key);
            if (!level.hasChunk(chunk.x, chunk.z)) continue;
            boolean worked = false;
            for (Detonation d : detonations) {
                if (d.touchedChunks.contains(key)) continue;
                double r = d.groundWorkRadius() + 16;
                double dx = chunk.getMiddleBlockX() - d.center.x, dz = chunk.getMiddleBlockZ() - d.center.z;
                if (dx * dx + dz * dz > r * r) continue;
                boolean complete = Crater.catchUpChunk(level, d, chunk);
                complete &= HeatPulse.catchUpChunk(level, d, chunk);
                if (complete) {
                    d.touchedChunks.add(key);
                    setDirty();
                }
                worked = true;
            }
            if (worked) return;   // one chunk's worth of block work per tick is plenty
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getDataStorage() == null) return;
        DetonationManager m = get(level);
        if (m.detonations.isEmpty()) return;
        m.pendingChunks.add(event.getChunk().getPos().toLong());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        WeatherBridge.clearServer();
    }

    public void sync(ServerLevel level) {
        DetonationSyncPayload payload = DetonationSyncPayload.of(level, this);
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(p, DetonationSyncPayload.of(level, get(level)));
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(p, DetonationSyncPayload.of(level, get(level)));
        }
    }

    /** Strongest nuclear-winter factor (0..1) across all detonations in this level today. */
    public double winterFactor(Level level) {
        if (detonations.isEmpty()) return 0;
        double day = WeatherBridge.calendarDay(level);
        double f = 0;
        for (Detonation d : detonations) f = Math.max(f, d.winterFactor(day));
        return f;
    }

    /** Fallout plume strength (0..1) over a point, across all detonations. */
    public double falloutAt(double x, double z) {
        double f = 0;
        for (Detonation d : detonations) f = Math.max(f, d.falloutAt(x, z));
        return f;
    }

    public record Reading(double fallout, int groundLayers, double winter) {
        public Component describeFallout() {
            double total = Math.min(1.0, fallout + groundLayers / 8.0);
            String key = total <= 0.001 ? "clear" : total < 0.25 ? "low" : total < 0.6 ? "moderate" : "severe";
            return Component.translatable("message.atomicsky.fallout." + key);
        }

        public Component describeWinter() {
            String key = winter <= 0.001 ? "none" : winter < 0.5 ? "setting_in" : "full";
            return Component.translatable("message.atomicsky.winter." + key);
        }
    }

    public Reading readingAt(Vec3 pos) {
        int layers = 0;
        if (boundLevel != null) {
            BlockPos bp = BlockPos.containing(pos);
            for (BlockPos p : new BlockPos[]{bp, bp.below()}) {
                var state = boundLevel.getBlockState(p);
                if (state.is(AtomicSky.FALLOUT.get())) layers = Math.max(layers, state.getValue(FalloutLayerBlock.LAYERS));
            }
        }
        return new Reading(falloutAt(pos.x, pos.z), layers, boundLevel == null ? 0 : winterFactor(boundLevel));
    }

    public void clearAll() {
        detonations.clear();
        pendingChunks.clear();
        if (boundLevel != null) {
            WeatherBridge.setServerWinter(boundLevel, 0);
            ShockwaveField.setServerFronts(boundLevel.dimension(), List.of());
        }
        setDirty();
    }

    public void endWinter() {
        for (Detonation d : detonations) d.winterDone = true;
        if (boundLevel != null) WeatherBridge.setServerWinter(boundLevel, 0);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Detonation d : detonations) list.add(d.save());
        tag.put("Detonations", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    public static DetonationManager load(CompoundTag tag, HolderLookup.Provider registries) {
        DetonationManager m = new DetonationManager();
        ListTag list = tag.getList("Detonations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) m.detonations.add(Detonation.load(list.getCompound(i)));
        m.nextId = Math.max(1, tag.getInt("NextId"));
        return m;
    }
}
