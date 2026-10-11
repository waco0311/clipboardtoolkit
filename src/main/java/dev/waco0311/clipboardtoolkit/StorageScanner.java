package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public final class StorageScanner {
    private StorageScanner() {}

    public static void scan(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        ItemStack clipboard = player.getMainHandItem();
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()
                || player.containerMenu != player.inventoryMenu
                || !AllBlocks.CLIPBOARD.isIn(clipboard) || clipboard.getCount() != 1
                || player.getCooldowns().isOnCooldown(clipboard.getItem())) return;
        player.getCooldowns().addCooldown(clipboard.getItem(), 10);

        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !player.canInteractWithBlock(pos, 0) || !level.mayInteract(player, pos)) return;
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye,
                eye.add(player.getViewVector(1).scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return;

        var interaction = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        if (interaction.isCanceled() || interaction.getUseBlock() == TriState.FALSE
                || interaction.getUseItem() == TriState.FALSE) {
            message(player, "denied");
            return;
        }
        ClipboardContent old = clipboard.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        if (clipboard.has(CreateClipboardToolkit.AREA_CONTENTS) || !ClipboardLists.isBlank(old)) {
            message(player, "not_blank");
            return;
        }
        IItemHandler inventory;
        try {
            inventory = resolveInventory(player, pos, hit);
        } catch (StorageUnavailable unavailable) {
            message(player, unavailable.reason);
            return;
        }
        if (inventory == null) {
            message(player, "unsupported");
            return;
        }
        // Interaction hooks may replace a held item. Never write through a stale reference.
        if (player.getMainHandItem() != clipboard || !ClipboardLists.isBlank(
                clipboard.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY))) return;
        ClipboardContent content;
        try {
            content = ClipboardLists.collect(inventory);
        } catch (ClipboardLists.ScanLimitException limit) {
            message(player, "too_large");
            return;
        }
        if (content.pages().isEmpty()) {
            message(player, "empty");
            return;
        }
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT, content);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        int kinds = content.pages().stream().mapToInt(java.util.List::size).sum();
        player.displayClientMessage(Component.translatable("message.clipboardtoolkit.scanned", kinds), true);
    }

    static IItemHandler resolveInventory(ServerPlayer player, BlockPos pos, BlockHitResult hit) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BaseContainerBlockEntity lockable && !lockable.canOpen(player)) {
            throw new StorageUnavailable("denied");
        }
        // Preserve vanilla double-chest, lock and lid semantics instead of reading one half.
        if (state.getBlock() instanceof ChestBlock chestBlock && blockEntity instanceof ChestBlockEntity chest) {
            ChestBlockEntity other = null;
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
                if (!level.hasChunkAt(otherPos) || !level.mayInteract(player, otherPos)
                        || !(level.getBlockEntity(otherPos) instanceof ChestBlockEntity partner)
                        || !partner.canOpen(player)) throw new StorageUnavailable("denied");
                var otherHit = new BlockHitResult(Vec3.atCenterOf(otherPos), hit.getDirection(), otherPos, false);
                var access = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, otherPos, otherHit);
                if (access.isCanceled() || access.getUseBlock() == TriState.FALSE
                        || access.getUseItem() == TriState.FALSE) throw new StorageUnavailable("denied");
                other = partner;
            }
            Container container = ChestBlock.getContainer(chestBlock, state, level, pos, false);
            if (container == null) throw new StorageUnavailable("blocked");
            chest.unpackLootTable(player);
            if (other != null) other.unpackLootTable(player);
            return new InvWrapper(container);
        }
        if (blockEntity instanceof EnderChestBlockEntity) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).isRedstoneConductor(level, above)) throw new StorageUnavailable("blocked");
            // Ender inventories belong to the requesting player, never to the block.
            return new InvWrapper(player.getEnderChestInventory());
        }
        if (blockEntity instanceof ShulkerBoxBlockEntity shulker
                && state.getBlock() instanceof ShulkerBoxBlock
                && shulker.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.CLOSED
                && !level.noCollision(Shulker.getProgressDeltaAabb(1, state.getValue(ShulkerBoxBlock.FACING), 0, 0.5F)
                        .move(pos).deflate(1.0E-6))) throw new StorageUnavailable("blocked");
        if (blockEntity instanceof RandomizableContainerBlockEntity loot) loot.unpackLootTable(player);

        // Prefer an unsided view (e.g. the whole Create vault). If unavailable, use
        // the clicked face only. Combining all six faces can count the same slot twice.
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, hit.getDirection());
        if (handler != null) return handler;
        if (blockEntity instanceof Container container) return new InvWrapper(container);
        return null;
    }

    static final class StorageUnavailable extends RuntimeException {
        final String reason;

        private StorageUnavailable(String reason) {
            this.reason = reason;
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.clipboardtoolkit." + key), true);
    }
}
