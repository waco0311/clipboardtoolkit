package dev.waco0311.clipboardtoolkit;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client requests a state; server replies with the state it actually accepted. */
public record ClipboardLockPayload(int slot, Optional<BlockPos> pos, ResourceLocation dimension, boolean locked)
        implements CustomPacketPayload {
    public static final Type<ClipboardLockPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateClipboardToolkit.MODID, "clipboard_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardLockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClipboardLockPayload::slot,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ClipboardLockPayload::pos,
            ResourceLocation.STREAM_CODEC, ClipboardLockPayload::dimension,
            ByteBufCodecs.BOOL, ClipboardLockPayload::locked, ClipboardLockPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
