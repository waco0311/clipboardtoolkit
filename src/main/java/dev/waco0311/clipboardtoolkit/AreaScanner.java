package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

public final class AreaScanner {
    private AreaScanner() {}

    public static void scan(ServerPlayer player, BlockPos first, BlockPos second) {
        ItemStack clipboard = player.getMainHandItem();
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()
                || player.containerMenu != player.inventoryMenu || !AllBlocks.CLIPBOARD.isIn(clipboard)
                || clipboard.getCount() != 1 || player.getCooldowns().isOnCooldown(clipboard.getItem())) return;
        player.getCooldowns().addCooldown(clipboard.getItem(), 40);
        if (!blank(clipboard)) { message(player, "not_blank"); return; }
        SelectionBox box = SelectionBox.between(first, second);
        if (!box.isValid()) { message(player, "area_limit"); return; }
        var level = player.serverLevel();
        if (player.distanceToSqr(Vec3.atCenterOf(box.min())) > 64 * 64
                || player.distanceToSqr(Vec3.atCenterOf(box.max())) > 64 * 64) {
            message(player, "area_far"); return;
        }
        // Check the complete selection before touching any inventory. Never force-load chunks.
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                message(player, "area_limit"); return;
            }
            if (!level.hasChunkAt(pos)) { message(player, "area_unloaded"); return; }
            if (!level.mayInteract(player, pos)) { message(player, "denied"); return; }
        }
        var placed = new ClipboardLists.Accumulator();
        var stored = new ClipboardLists.Accumulator();
        Set<BlockPos> visited = new HashSet<>();
        Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (BlockPos cursor : BlockPos.betweenClosed(box.min(), box.max())) {
                BlockPos pos = cursor.immutable();
                var state = level.getBlockState(pos);
                var entity = level.getBlockEntity(pos);
                if (!state.isAir() && !state.liquid() && !BlockMaterials.isDuplicateHalf(state, pos, box)) {
                    if (!BlockMaterials.collect(state, entity, placed)) {
                        player.displayClientMessage(Component.translatable("message.clipboardtoolkit.area_material",
                                state.getBlock().getName()), true);
                        return;
                    }
                }
                if (visited.contains(pos) || entity instanceof EnderChestBlockEntity) continue;
                SelectionBox storageBounds = SelectionBox.between(pos, pos);
                if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    storageBounds = SelectionBox.between(pos, pos.relative(ChestBlock.getConnectedDirection(state)));
                } else if (entity instanceof ItemVaultBlockEntity vault) {
                    BlockPos controllerPos = vault.getController();
                    if (controllerPos == null || !level.hasChunkAt(controllerPos)) throw new Failure("area_unloaded");
                    ItemVaultBlockEntity controller = vault.getControllerBE();
                    if (controller == null) throw new Failure("area_unloaded");
                    int width = controller.getWidth();
                    int length = controller.getHeight();
                    Direction.Axis axis = controller.getMainConnectionAxis();
                    if (width <= 0 || width > 3 || length <= 0 || length > 9) throw new Failure("area_boundary");
                    storageBounds = SelectionBox.between(controllerPos, controllerPos.offset(
                            (axis == Direction.Axis.X ? length : width) - 1, width - 1,
                            (axis == Direction.Axis.Z ? length : width) - 1));
                }
                if (!box.contains(storageBounds.min()) || !box.contains(storageBounds.max()))
                    throw new Failure("area_boundary");
                // Validate every member of known combined storage, including locks/protection.
                for (BlockPos member : BlockPos.betweenClosed(storageBounds.min(), storageBounds.max())) {
                    if (!level.hasChunkAt(member)) throw new Failure("area_unloaded");
                    if (!level.mayInteract(player, member)) throw new Failure("denied");
                    var memberEntity = level.getBlockEntity(member);
                    if (memberEntity instanceof RandomizableContainerBlockEntity loot && loot.getLootTable() != null)
                        throw new Failure("area_loot");
                    if (!level.getBlockState(member).isAir()) checkInteraction(player, member);
                    visited.add(member.immutable());
                }
                // A conventional top-side view is used when a storage has no unsided handler.
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                var inventory = StorageScanner.resolveInventory(player, pos, hit);
                if (inventory != null && seenHandlers.add(inventory)) stored.add(inventory);
            }
            if (player.getMainHandItem() != clipboard || !blank(clipboard)) return;
            AreaContents contents = new AreaContents(placed.content(), stored.content());
            ClipboardContent display;
            try {
                display = contents.display(); // Validate all pages before either component is written.
            } catch (ClipboardLists.ScanLimitException limit) {
                message(player, "area_pages");
                return;
            }
            clipboard.set(CreateClipboardToolkit.AREA_CONTENTS, contents);
            clipboard.set(AllDataComponents.CLIPBOARD_CONTENT, display);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            message(player, "area_done");
        } catch (ClipboardLists.ScanLimitException limit) {
            message(player, "too_large");
        } catch (StorageScanner.StorageUnavailable unavailable) {
            message(player, unavailable.reason);
        } catch (Failure failure) {
            message(player, failure.reason);
        }
    }

    private static void checkInteraction(ServerPlayer player, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        if (event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE)
            throw new Failure("denied");
    }

    private static boolean blank(ItemStack clipboard) {
        return !clipboard.has(CreateClipboardToolkit.AREA_CONTENTS)
                && ClipboardLists.isBlank(clipboard.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY));
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.clipboardtoolkit." + key), true);
    }

    private static final class Failure extends RuntimeException {
        final String reason;
        Failure(String reason) { this.reason = reason; }
    }
}

