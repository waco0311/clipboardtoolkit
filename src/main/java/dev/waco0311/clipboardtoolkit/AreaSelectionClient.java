package dev.waco0311.clipboardtoolkit;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

final class AreaSelectionClient {
    static final KeyMapping SELECT = new KeyMapping("key.clipboardtoolkit.area",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.clipboardtoolkit");
    private static final Object OUTLINE = new Object();
    private boolean active;
    private boolean useHeld;
    private BlockPos first;
    private BlockPos second;
    private ResourceKey<Level> dimension;
    private int slot;

    void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            while (SELECT.consumeClick()) {}
            return;
        }
        if (active && (mc.level.dimension() != dimension || mc.player.getInventory().selected != slot
                || !AllBlocks.CLIPBOARD.isIn(mc.player.getMainHandItem())
                || ClipboardLocks.isLocked(mc.player.getMainHandItem()))) reset();
        if (!mc.options.keyUse.isDown()) useHeld = false;
        while (SELECT.consumeClick()) {
            if (mc.screen != null || !AllBlocks.CLIPBOARD.isIn(mc.player.getMainHandItem())) continue;
            if (ClipboardLocks.isLocked(mc.player.getMainHandItem())) { ClipboardLocks.message(mc.player); continue; }
            if (active && mc.player.isShiftKeyDown()) {
                reset();
                message("area_cancel");
            } else if (!active) {
                active = true;
                dimension = mc.level.dimension();
                slot = mc.player.getInventory().selected;
                message("area_start");
            } else if (first != null && second != null) {
                var box = SelectionBox.between(first, second);
                if (!box.isValid()) {
                    message("area_limit");
                } else {
                    PacketDistributor.sendToServer(new AreaScanPayload(first, second, dimension.location()));
                    reset();
                }
            } else {
                message("area_pick");
            }
        }
        if (!active || mc.screen != null) return;
        BlockPos end = second;
        if (end == null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            end = hit.getBlockPos();
        if (end == null) end = first;
        if (end != null) {
            var box = SelectionBox.between(first == null ? end : first, end);
            Outliner.getInstance().showAABB(OUTLINE, box.bounds())
                    .colored(box.isValid() ? 0x55ddbb : 0xff5555).lineWidth(1 / 32f);
        }
    }

    boolean interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active || !event.isUseItem()) return false;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() != InteractionHand.MAIN_HAND || useHeld) return true;
        useHeld = true;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return true;
        if (first == null) {
            first = hit.getBlockPos().immutable();
            message("area_first");
        } else {
            second = hit.getBlockPos().immutable();
            message("area_ready");
        }
        return true;
    }

    private void message(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(
                Component.translatable("message.clipboardtoolkit." + key, SELECT.getTranslatedKeyMessage(), SELECT.getTranslatedKeyMessage()), true);
    }

    private void reset() {
        active = false;
        first = null;
        second = null;
        dimension = null;
        useHeld = false;
        Outliner.getInstance().remove(OUTLINE);
    }
}

