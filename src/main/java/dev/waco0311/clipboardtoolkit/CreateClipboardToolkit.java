package dev.waco0311.clipboardtoolkit;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

@Mod(CreateClipboardToolkit.MODID)
public class CreateClipboardToolkit {
    public static final String MODID = "clipboardtoolkit";
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AreaContents>> AREA_CONTENTS =
            COMPONENTS.registerComponentType("area_contents", builder -> builder
                    .persistent(AreaContents.CODEC).networkSynchronized(AreaContents.STREAM_CODEC));

    public CreateClipboardToolkit(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToServer(ScanStoragePayload.TYPE, ScanStoragePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StorageScanner.scan(player, payload.pos());
                    }
                }));
        registrar.playToServer(AreaScanPayload.TYPE, AreaScanPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.level().dimension().location().equals(payload.dimension())) {
                        AreaScanner.scan(player, payload.first(), payload.second());
                    }
                }));
    }
}
