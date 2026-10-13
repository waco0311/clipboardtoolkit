package dev.waco0311.clipboardtoolkit.mixin;

import com.simibubi.create.content.equipment.clipboard.ClipboardEditPacket;
import dev.waco0311.clipboardtoolkit.ClipboardLocks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClipboardEditPacket.class, remap = false)
public abstract class ClipboardEditPacketMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void clipboardtoolkit$protectContent(ServerPlayer player, CallbackInfo ci) {
        if (ClipboardLocks.handleEdit(player, (ClipboardEditPacket) (Object) this)) ci.cancel();
    }
}
