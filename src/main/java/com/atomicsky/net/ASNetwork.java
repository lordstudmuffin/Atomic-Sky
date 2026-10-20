package com.atomicsky.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ASNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(FlashPayload.TYPE, FlashPayload.CODEC, (payload, context) -> com.atomicsky.client.ClientState.onFlash(payload));
        registrar.playToClient(DetonationSyncPayload.TYPE, DetonationSyncPayload.CODEC, (payload, context) -> com.atomicsky.client.ClientState.onSync(payload));
    }

    private ASNetwork() {}
}
