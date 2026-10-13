package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardEditPacket;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ClipboardLocks {
    private ClipboardLocks() {}

    public static boolean isLocked(DataComponentHolder holder) {
        return holder.getOrDefault(CreateClipboardToolkit.LOCKED, false);
    }

    public static boolean isLocked(net.minecraft.core.component.DataComponentMap components) {
        return components.getOrDefault(CreateClipboardToolkit.LOCKED.get(), false);
    }

    public static void message(net.minecraft.world.entity.player.Player player) {
        player.displayClientMessage(Component.translatable("message.clipboardtoolkit.locked"), true);
    }

    private static boolean canEdit(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()) return false;
        if (pos == null) return player.containerMenu == player.inventoryMenu;
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !player.canInteractWithBlock(pos, 0) || !level.mayInteract(player, pos)) return false;
        return true;
    }

    public static boolean setLocked(ServerPlayer player, ClipboardLockPayload request) {
        if (!player.level().dimension().location().equals(request.dimension())) return false;
        BlockPos pos = request.pos().orElse(null);
        if (pos != null) {
            if (!player.level().hasChunkAt(pos)
                    || !(player.level().getBlockEntity(pos) instanceof ClipboardBlockEntity board)) return false;
            if (canEdit(player, pos)) {
                var map = new PatchedDataComponentMap(board.components());
                if (request.locked()) map.set(CreateClipboardToolkit.LOCKED.get(), true);
                else map.remove(CreateClipboardToolkit.LOCKED.get());
                board.setComponents(map);
                board.onEditedBy(player);
            }
            return isLocked(board.components());
        }
        if (request.slot() < 0 || request.slot() > 8) return false;
        ItemStack stack = player.getInventory().getItem(request.slot());
        if (!AllBlocks.CLIPBOARD.isIn(stack)) return false;
        if (canEdit(player, null) && request.slot() == player.getInventory().selected && stack.getCount() == 1) {
            if (request.locked()) stack.set(CreateClipboardToolkit.LOCKED, true);
            else stack.remove(CreateClipboardToolkit.LOCKED);
            player.inventoryMenu.broadcastChanges();
        }
        return isLocked(stack);
    }

    /** Preserve all stored fields, accepting only checks on an otherwise identical list. */
    public static ClipboardContent mergeChecks(ClipboardContent stored, ClipboardContent incoming) {
        // Create strips empty editor rows/pages when closing the screen. They are not an overwrite.
        var storedPages = nonEmptyPages(stored);
        var incomingPages = incoming == null ? List.<List<ClipboardEntry>>of() : nonEmptyPages(incoming);
        if (storedPages.size() != incomingPages.size()) return stored;
        List<List<ClipboardEntry>> result = new ArrayList<>();
        for (int p = 0; p < storedPages.size(); p++) {
            var before = storedPages.get(p);
            var after = incomingPages.get(p);
            if (before.size() != after.size()) return stored;
            List<ClipboardEntry> page = new ArrayList<>();
            for (int e = 0; e < before.size(); e++) {
                var a = before.get(e);
                var b = after.get(e);
                if (!a.text.equals(b.text) || a.itemAmount != b.itemAmount
                        || !ItemStack.matches(a.icon, b.icon)) return stored;
                page.add(new ClipboardEntry(b.checked, a.text.copy()).displayItem(a.icon.copy(), a.itemAmount));
            }
            result.add(page);
        }
        return stored.setPages(result).setType(ClipboardType.WRITTEN)
                .setPreviouslyOpenedPage(Math.clamp(incoming == null ? 0 : incoming.previouslyOpenedPage(), 0, Math.max(0, result.size() - 1)));
    }

    private static List<List<ClipboardEntry>> nonEmptyPages(ClipboardContent content) {
        return content.pages().stream().map(page -> page.stream()
                        .filter(entry -> !entry.text.getString().isBlank()).toList())
                .filter(page -> !page.isEmpty()).toList();
    }

    /** true means the locked target was handled and Create must not overwrite it. */
    public static boolean handleEdit(ServerPlayer player, ClipboardEditPacket packet) {
        BlockPos pos = packet.targetedBlock();
        if (pos != null) {
            if (!player.level().hasChunkAt(pos)) return true;
            if (!(player.level().getBlockEntity(pos) instanceof ClipboardBlockEntity board)) return false;
            if (!isLocked(board.components())) return false;
            if (canEdit(player, pos)) {
                var original = board.components().getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
                var map = new PatchedDataComponentMap(board.components());
                map.set(AllDataComponents.CLIPBOARD_CONTENT, mergeChecks(original, packet.clipboardContent()).setType(ClipboardType.WRITTEN));
                board.setComponents(map);
                board.onEditedBy(player);
            }
            return true;
        }
        if (packet.hotbarSlot() < 0 || packet.hotbarSlot() > 8) return true;
        var stack = player.getInventory().getItem(packet.hotbarSlot());
        if (!AllBlocks.CLIPBOARD.isIn(stack) || !isLocked(stack)) return false;
        if (canEdit(player, null) && player.getInventory().selected == packet.hotbarSlot()) {
            var original = stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
            stack.set(AllDataComponents.CLIPBOARD_CONTENT, mergeChecks(original, packet.clipboardContent()).setType(ClipboardType.WRITTEN));
        }
        return true;
    }
}
