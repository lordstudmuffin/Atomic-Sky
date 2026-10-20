package com.atomicsky.client;

import com.atomicsky.AtomicSky;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = AtomicSky.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ClientEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Pre.class, ClientEffects::onRenderGui);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ViewportEvent.ComputeFogColor.class, ClientEffects::onFogColor);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ViewportEvent.RenderFog.class, ClientEffects::onRenderFog);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, ClientEffects::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, ClientEffects::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, ClientEffects::onEntityJoin);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AtomicSky.ASH.get(), AshParticle.Provider::new);
        event.registerSpriteSet(AtomicSky.EMBER.get(), EmberParticle.Provider::new);
    }

    private ClientSetup() {}
}
