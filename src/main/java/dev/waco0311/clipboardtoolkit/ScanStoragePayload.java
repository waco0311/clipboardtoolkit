package dev.waco0311.clipboardtoolkit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// The client supplies only a target. Inventory and counts are server-owned.
public record ScanStoragePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ScanStoragePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateClipboardToolkit.MODID, "scan_storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanStoragePayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ScanStoragePayload::pos, ScanStoragePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
