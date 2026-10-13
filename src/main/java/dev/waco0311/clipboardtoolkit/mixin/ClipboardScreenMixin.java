package dev.waco0311.clipboardtoolkit.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import dev.waco0311.clipboardtoolkit.ClipboardLockClient;
import dev.waco0311.clipboardtoolkit.ClipboardLockPayload;
import dev.waco0311.clipboardtoolkit.ClipboardLocks;
import dev.waco0311.clipboardtoolkit.CreateClipboardToolkit;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(value = ClipboardScreen.class, remap = false)
public abstract class ClipboardScreenMixin extends AbstractSimiScreen implements ClipboardLockClient.LockScreen {
    @Shadow @Final private int targetSlot;
    @Shadow public BlockPos targetedBlock;
    @Shadow public ClipboardContent content;
    @Shadow boolean readonly;
    @Shadow int editingIndex;
    @Shadow int currentPage;
    @Shadow List<List<ClipboardEntry>> pages;
    @Shadow IconButton clearBtn;
    @Shadow PageButton forward;
    @Shadow private void send() {}
    @Unique private boolean clipboardtoolkit$locked;
    @Unique private boolean clipboardtoolkit$pending;
    @Unique private IconButton clipboardtoolkit$lockButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void clipboardtoolkit$readInitialLock(int slot, DataComponentMap components, BlockPos pos, CallbackInfo ci) {
        clipboardtoolkit$locked = ClipboardLocks.isLocked(components);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void clipboardtoolkit$addLockButton(CallbackInfo ci) {
        clipboardtoolkit$lockButton = new IconButton(guiLeft + 234, guiTop + 123,
                clipboardtoolkit$locked ? AllIcons.I_CONFIG_LOCKED : AllIcons.I_CONFIG_UNLOCKED)
                .withCallback(() -> {
                    if (clipboardtoolkit$pending || minecraft.player == null || minecraft.level == null) return;
                    // Commit current text/checks BEFORE the server changes the lock.
                    send();
                    clipboardtoolkit$pending = true;
                    clipboardtoolkit$refresh();
                    PacketDistributor.sendToServer(new ClipboardLockPayload(targetSlot,
                            Optional.ofNullable(targetedBlock), minecraft.level.dimension().location(), !clipboardtoolkit$locked));
                });
        addRenderableWidget(clipboardtoolkit$lockButton);
        clipboardtoolkit$refresh();
    }

    @Unique private void clipboardtoolkit$refresh() {
        readonly = clipboardtoolkit$locked || clipboardtoolkit$pending || content.readOnly();
        if (readonly) editingIndex = -1;
        if (clearBtn != null) clearBtn.active = !clipboardtoolkit$locked && !clipboardtoolkit$pending;
        if (forward != null) forward.visible = currentPage < 50 && (!readonly || currentPage + 1 < pages.size());
        if (clipboardtoolkit$lockButton != null) {
            clipboardtoolkit$lockButton.setIcon(clipboardtoolkit$locked ? AllIcons.I_CONFIG_LOCKED : AllIcons.I_CONFIG_UNLOCKED);
            clipboardtoolkit$lockButton.setToolTip(Component.translatable(clipboardtoolkit$locked
                    ? "gui.clipboardtoolkit.unlock" : "gui.clipboardtoolkit.lock"));
            clipboardtoolkit$lockButton.active = !clipboardtoolkit$pending;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void clipboardtoolkit$syncLock(CallbackInfo ci) {
        if (minecraft.player == null || minecraft.level == null || clipboardtoolkit$pending) return;
        boolean locked = clipboardtoolkit$locked;
        if (targetedBlock != null) {
            if (minecraft.level.getBlockEntity(targetedBlock) instanceof ClipboardBlockEntity board)
                locked = ClipboardLocks.isLocked(board.components());
        } else {
            var stack = minecraft.player.getInventory().getItem(targetSlot);
            if (minecraft.player.getInventory().selected != targetSlot || !AllBlocks.CLIPBOARD.isIn(stack)) {
                minecraft.setScreen(null);
                return;
            }
            locked = ClipboardLocks.isLocked(stack);
        }
        clipboardtoolkit$locked = locked;
        clipboardtoolkit$refresh();
    }

    @Override public void clipboardtoolkit$receiveLock(ClipboardLockPayload state) {
        if (minecraft.player == null || minecraft.level == null || state.slot() != targetSlot
                || !state.pos().equals(Optional.ofNullable(targetedBlock))
                || !state.dimension().equals(minecraft.level.dimension().location())) return;
        clipboardtoolkit$locked = state.locked();
        clipboardtoolkit$pending = false;
        // Mirror the acknowledgement until normal inventory/block synchronization arrives.
        if (targetedBlock == null) {
            var stack = minecraft.player.getInventory().getItem(targetSlot);
            if (AllBlocks.CLIPBOARD.isIn(stack)) {
                if (state.locked()) stack.set(CreateClipboardToolkit.LOCKED, true);
                else stack.remove(CreateClipboardToolkit.LOCKED);
            }
        } else if (minecraft.level.getBlockEntity(targetedBlock) instanceof ClipboardBlockEntity board) {
            var map = new PatchedDataComponentMap(board.components());
            if (state.locked()) map.set(CreateClipboardToolkit.LOCKED.get(), true);
            else map.remove(CreateClipboardToolkit.LOCKED.get());
            board.setComponents(map);
        }
        clipboardtoolkit$refresh();
    }
}
