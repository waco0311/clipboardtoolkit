package dev.waco0311.clipboardtoolkit.mixin;

import com.simibubi.create.AllBlocks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.simibubi.create.content.equipment.clipboard.ClipboardValueSettingsHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.waco0311.clipboardtoolkit.ClipboardLocks;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClipboardValueSettingsHandler.class, remap = false)
public abstract class ClipboardValueSettingsHandlerMixin {
    @ModifyExpressionValue(method = "interact", at = @At(value = "NEW",
            target = "com/simibubi/create/content/equipment/clipboard/ClipboardEntry"), require = 1, allow = 1)
    private static ClipboardEntry clipboardtoolkit$copyItemDetails(ClipboardEntry created,
            @Local(ordinal = 0) ClipboardEntry source) {
        // Create copies only checked/text here. Keep the item data used by stock orders,
        // with independent copies so editing the destination cannot change the source.
        return new ClipboardEntry(source.checked, source.text.copy())
                .displayItem(source.icon.copy(), source.itemAmount);
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private static void clipboardtoolkit$protectDestination(PlayerInteractEvent event, boolean paste, CallbackInfo ci) {
        var stack = event.getItemStack();
        if (!AllBlocks.CLIPBOARD.isIn(stack) || !ClipboardLocks.isLocked(stack)
                || event.getEntity().isShiftKeyDown() || event.getEntity().isSpectator()) return;
        var target = event.getLevel().getBlockEntity(event.getPos());
        // Create always imports into the held clipboard when clicking another clipboard,
        // even on its nominal "paste" path. Machine pasting only reads the held clipboard.
        if (!(target instanceof SmartBlockEntity) || (paste && !(target instanceof ClipboardBlockEntity))) return;
        if (event instanceof ICancellableEvent cancellable) cancellable.setCanceled(true);
        if (event instanceof PlayerInteractEvent.RightClickBlock right) right.setCancellationResult(InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide()) ClipboardLocks.message(event.getEntity());
        ci.cancel();
    }
}
