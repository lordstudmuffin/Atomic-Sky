package com.atomicsky.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A glowing ember rising off baked ground in the heat zone. */
public class EmberParticle extends TextureSheetParticle {
    protected EmberParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = -0.01F;
        this.lifetime = 30 + random.nextInt(40);
        this.quadSize = 0.04F + random.nextFloat() * 0.05F;
        setColor(1.0F, 0.55F + random.nextFloat() * 0.3F, 0.15F);
        this.hasPhysics = false;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float f = 1.0F - (float) age / lifetime;
        alpha = f;
        quadSize *= 0.985F;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
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
            return new EmberParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
