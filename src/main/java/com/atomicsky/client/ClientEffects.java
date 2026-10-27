package com.atomicsky.client;

import com.atomicsky.ASClientConfig;
import com.atomicsky.AtomicSky;
import com.atomicsky.net.DetonationSyncPayload;
import com.atomicsky.net.FlashPayload;
import com.atomicsky.weather.WeatherBridge;
import dev.protomanly.pmweather.event.GameBusClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Flash, fallout ash, darkened sky, heat shimmer embers, camera shake and sound: the client half of a detonation. */
public final class ClientEffects {
    private static final RandomSource RAND = RandomSource.create();
    /** Booms still on their way to the camera (two bombs close together each get theirs). */
    private static final java.util.List<Boom> BOOMS = new java.util.ArrayList<>();
    private static float shake;
    private static float shakeYaw, shakePitch;
    /** Shock fronts that have already passed the camera, so each one shakes and gusts exactly once. */
    private static final java.util.Set<Integer> SHOCK_PASSED = new java.util.HashSet<>();

    private static final class Boom {
        int delay;
        final float volume;
        final Vec3 at;
        Boom(int delay, float volume, Vec3 at) { this.delay = delay; this.volume = volume; this.at = at; }
    }

    public static void onFlash(FlashPayload p) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (cam == null || mc.level == null) return;
        Vec3 eye = cam.getEyePosition();
        double dist = eye.distanceTo(p.center());
        if (dist > p.range() * 1.5) return;

        // Sound: thunder-like boom delayed by distance (about 17 blocks per tick), then a long rumble.
        Boom boom = new Boom((int) (dist / 17.0), (float) Mth.clamp(1.5 - dist / p.range(), 0.3, 1.5), p.center());
        if (boom.delay <= 0) playBoom(boom);
        else BOOMS.add(boom);

        if (!ASClientConfig.FLASH.get()) return;
        double base = Mth.clamp(1.0 - dist / p.range(), 0.0, 1.0);
        base = Math.sqrt(base);
        Vec3 dir = p.center().subtract(eye).normalize();
        double facing = cam.getLookAngle().dot(dir);              // 1 = looking straight at it
        double facingFactor = facing > 0.2 ? 1.0 : Mth.lerp((facing + 1.0) / 1.2, 0.3, 1.0);
        // Line of sight to the fireball (aim at a point above the blast so a hill does not hide the whole thing).
        Vec3 target = p.center().add(0, p.yield() * 2.5, 0);
        HitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam));
        boolean blocked = hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(target) > p.yield() * 1.5;
        double los = blocked ? 0.15 : 1.0;
        float peak = (float) (base * facingFactor * los * ASClientConfig.FLASH_STRENGTH.get());
        if (peak < 0.02F) return;
        ClientState.flashPeak = peak;
        ClientState.flashDuration = (int) (40 + 100 * peak);
        ClientState.flashTicks = ClientState.flashDuration;
    }

    private static void playBoom(Boom b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, b.volume * 3.0F, 0.35F,
                RAND, b.at.x, b.at.y, b.at.z));
        mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, b.volume * 4.0F, 0.5F,
                RAND, b.at.x, b.at.y, b.at.z));
        mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WITHER_SPAWN, SoundSource.WEATHER, b.volume * 1.2F, 0.3F,
                RAND, b.at.x, b.at.y, b.at.z));
        shake = Math.max(shake, b.volume * 0.8F);
    }

    /** A new level (login, dimension change, disconnect): nothing from the last one is still on its way. */
    static void reset() {
        BOOMS.clear();
        SHOCK_PASSED.clear();
        shake = 0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        if (ClientState.flashTicks > 0) ClientState.flashTicks--;
        if (!BOOMS.isEmpty()) {
            java.util.Iterator<Boom> booms = BOOMS.iterator();
            while (booms.hasNext()) {
                Boom b = booms.next();
                if (--b.delay <= 0) {
                    playBoom(b);
                    booms.remove();
                }
            }
        }
        shake *= 0.86F;
        if (shake > 0.01F) {
            shakeYaw = (RAND.nextFloat() - 0.5F) * 2.0F * shake * 3.0F;
            shakePitch = (RAND.nextFloat() - 0.5F) * 2.0F * shake * 2.0F;
        } else {
            shakeYaw = shakePitch = 0;
        }
        ClientState.publishFronts();
        MushroomCloud.tick(level);

        Entity cam = mc.getCameraEntity();
        if (cam == null || ClientState.entries.isEmpty()) return;
        double x = cam.getX(), z = cam.getZ();
        double fallout = ClientState.falloutAt(x, z);
        double heat = ClientState.heatAt(x, z);

        // Darken the sky through the weather mod's own ash haze, and glow it through its fire haze.
        try {
            if (ASClientConfig.DARK_SKY.get() && fallout > 0) {
                GameBusClientEvents.AshTarget = Math.max(GameBusClientEvents.AshTarget, (float) Math.min(1.0, fallout * 1.1));
            }
            if (heat > 0) {
                GameBusClientEvents.FireTarget = Math.max(GameBusClientEvents.FireTarget, (float) (heat * 2.5));
            }
        } catch (Throwable ignored) {
        }

        // Shock front passing the camera: shake and a gust.
        for (DetonationSyncPayload.Entry e : ClientState.entries) {
            if (e.shockDone()) continue;
            double radius = ClientState.shockRadiusNow(e);
            double d = Math.hypot(x - e.center().x, z - e.center().z);
            if (Math.abs(d - radius) < 8 && SHOCK_PASSED.add(e.id())) {
                double strength = Math.min(1.0, e.yield() / Math.max(d, e.yield())) * Math.max(0, 1 - radius / e.shockRange());
                if (ASClientConfig.CAMERA_SHAKE.get()) shake = Math.max(shake, (float) (0.5 + strength * 2.0));
                mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.WEATHER,
                        (float) (1.0 + strength * 3.0), 0.5F, RAND, x, cam.getY(), z));
            }
        }

        double density = ASClientConfig.PARTICLE_DENSITY.get();
        if (density <= 0) return;
        // Fallout ash: flakes drifting down around the camera, pushed by the plume's wind.
        if (fallout > 0.02) {
            Vec3 wind = ClientState.windAt(x, z).scale(WeatherBridge.MPH_TO_BLOCKS_PER_TICK * 0.5);
            int count = (int) Math.ceil(fallout * 14 * density);
            for (int i = 0; i < count; i++) {
                double px = x + (RAND.nextDouble() - 0.5) * 24;
                double pz = z + (RAND.nextDouble() - 0.5) * 24;
                double py = cam.getY() + 4 + RAND.nextDouble() * 12;
                if (!com.atomicsky.util.Exposure.exposed(level, net.minecraft.core.BlockPos.containing(px, py, pz))) continue;
                level.addParticle(AtomicSky.ASH.get(), px, py, pz, wind.x, -0.02 - RAND.nextDouble() * 0.02, wind.z);
            }
        }
        // Heat zone: embers and shimmer rising from the ground.
        if (heat > 0.05) {
            int count = (int) Math.ceil(heat * 16 * density);
            for (int i = 0; i < count; i++) {
                double px = x + (RAND.nextDouble() - 0.5) * 36;
                double pz = z + (RAND.nextDouble() - 0.5) * 36;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) px, (int) pz);
                level.addParticle(AtomicSky.EMBER.get(), px, top + 0.2, pz, (RAND.nextDouble() - 0.5) * 0.02, 0.04 + RAND.nextDouble() * 0.05, (RAND.nextDouble() - 0.5) * 0.02);
                if (RAND.nextInt(3) == 0) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, px, top + 0.3, pz, 0, 0.02 + RAND.nextDouble() * 0.03, 0);
                }
            }
        }
        // Shock front: a ring of dust and debris racing outward along the ground, drawn where it is near the camera.
        for (DetonationSyncPayload.Entry e : ClientState.entries) {
            if (e.shockDone()) continue;
            double radius = ClientState.shockRadiusNow(e);
            if (radius > e.shockRange()) continue;
            double strength = Math.max(0.15, 1 - radius / e.shockRange());
            int spawned = 0, tries = 0;
            int want = (int) Math.ceil(50 * density * strength);
            while (spawned < want && tries++ < want * 4) {
                double ang = RAND.nextDouble() * Math.PI * 2;
                double px = e.center().x + Math.cos(ang) * (radius + (RAND.nextDouble() - 0.5) * 10);
                double pz = e.center().z + Math.sin(ang) * (radius + (RAND.nextDouble() - 0.5) * 10);
                if (Math.hypot(px - x, pz - z) > 140) continue;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(px), (int) Math.floor(pz));
                double vx = Math.cos(ang) * 0.9, vz = Math.sin(ang) * 0.9;
                double py = top + RAND.nextDouble() * (4 + 10 * strength);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, px, py, pz, vx, 0.15 + RAND.nextDouble() * 0.3, vz);
                if (RAND.nextInt(4) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, px, py, pz, vx * 0.5, 0.1, vz * 0.5);
                if (RAND.nextInt(6) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, px, py + 1, pz, 0, 0, 0);
                spawned++;
            }
        }
    }

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (ClientState.flashTicks <= 0 || ClientState.flashPeak <= 0) return;
        float t = 1.0F - (float) ClientState.flashTicks / ClientState.flashDuration;   // 0 at the flash, 1 at the end
        // Instant white, holds briefly, then an eased fade that lingers as a bright afterimage.
        float alpha = t < 0.08F ? 1.0F : (float) Math.pow(1.0F - (t - 0.08F) / 0.92F, 1.8);
        alpha *= ClientState.flashPeak;
        if (alpha <= 0.005F) return;
        GuiGraphics g = event.getGuiGraphics();
        int a = (int) (alpha * 255) & 0xFF;
        int color = (a << 24) | 0xFFF8E8;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), color);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ASClientConfig.DARK_SKY.get()) return;
        Entity cam = event.getCamera().getEntity();
        if (cam == null || ClientState.entries.isEmpty()) return;
        double fallout = ClientState.falloutAt(cam.getX(), cam.getZ());
        if (fallout <= 0.01) return;
        float f = (float) Math.min(0.9, fallout * 0.95);
        event.setRed(Mth.lerp(f, event.getRed(), 0.30F));
        event.setGreen(Mth.lerp(f, event.getGreen(), 0.28F));
        event.setBlue(Mth.lerp(f, event.getBlue(), 0.25F));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!ASClientConfig.DARK_SKY.get()) return;
        Entity cam = event.getCamera().getEntity();
        if (cam == null || ClientState.entries.isEmpty()) return;
        double fallout = ClientState.falloutAt(cam.getX(), cam.getZ());
        if (fallout <= 0.01) return;
        float far = event.getFarPlaneDistance();
        float target = (float) Mth.lerp(fallout, far, Math.min(far, 48.0F));
        if (target < far) {
            event.setFarPlaneDistance(target);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), target * 0.3F));
            event.setCanceled(true);
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.01F || !ASClientConfig.CAMERA_SHAKE.get()) return;
        event.setYaw(event.getYaw() + shakeYaw);
        event.setPitch(event.getPitch() + shakePitch);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ClientState.reset();
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // When the local player enters a level, forget stale state from the last one; the server re-syncs on login.
        if (event.getLevel().isClientSide() && event.getEntity() == Minecraft.getInstance().player) ClientState.reset();
    }

    private ClientEffects() {}
}
