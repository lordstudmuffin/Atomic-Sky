package com.atomicsky.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A flake of fallout: slow, wobbly, grey, and it settles rather than vanishes. */
public class AshParticle extends TextureSheetParticle {
    private final double windX, windZ;

    protected AshParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.windX = vx;
        this.windZ = vz;
        this.xd = vx + (random.nextDouble() - 0.5) * 0.01;
        this.yd = vy;
        this.zd = vz + (random.nextDouble() - 0.5) * 0.01;
        this.gravity = 0.0F;
        this.lifetime = 200 + random.nextInt(160);
        this.quadSize = 0.08F + random.nextFloat() * 0.08F;
        float shade = 0.45F + random.nextFloat() * 0.25F;
        setColor(shade, shade * 0.97F, shade * 0.92F);
        this.alpha = 0.9F;
        this.hasPhysics = true;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        double t = age * 0.15 + x * 0.1;
        xd = windX + Math.sin(t) * 0.006;
        zd = windZ + Math.cos(t * 0.8) * 0.006;
        yd = Math.max(yd, -0.045);
        if (onGround) {
            xd = zd = 0;
            if (age < lifetime - 30) age = lifetime - 30;   // fade out quickly once settled
        }
        if (age > lifetime - 30) alpha = Math.max(0, (lifetime - age) / 30.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new AshParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
