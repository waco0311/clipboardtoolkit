package dev.waco0311.clipboardtoolkit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AreaScanPayload(BlockPos first, BlockPos second, ResourceLocation dimension) implements CustomPacketPayload {
    public static final Type<AreaScanPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateClipboardToolkit.MODID, "scan_area"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AreaScanPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AreaScanPayload::first,
            BlockPos.STREAM_CODEC, AreaScanPayload::second,
            ResourceLocation.STREAM_CODEC, AreaScanPayload::dimension, AreaScanPayload::new);
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

