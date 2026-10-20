package com.atomicsky.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Radiation sickness: slow damage (never below half a heart from the effect alone at level I) and hunger drain. */
public class RadiationEffect extends MobEffect {
    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, 0x7FB33A);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            player.causeFoodExhaustion(0.08F * (amplifier + 1));
        }
        float floor = amplifier >= 1 ? 0.0F : 1.0F;
        if (entity.getHealth() > floor) {
            entity.hurt(entity.damageSources().source(com.atomicsky.AtomicSky.RADIATION_DAMAGE), 1.0F);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = amplifier >= 1 ? 30 : 60;
        return duration % interval == 0;
    }
}
