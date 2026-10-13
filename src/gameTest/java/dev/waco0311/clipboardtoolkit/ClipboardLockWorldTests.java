package dev.waco0311.clipboardtoolkit;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(CreateClipboardToolkit.MODID)
@PrefixGameTestTemplate(false)
public class ClipboardLockWorldTests {
    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "LockTester"));
        player.setGameMode(GameType.SURVIVAL);
        var pos = helper.absolutePos(new BlockPos(1, 2, 2));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.CLIPBOARD.asStack());
        return player;
    }

    private static ClipboardContent content(boolean checked, String text) {
        return new ClipboardContent(ClipboardOverrides.ClipboardType.WRITTEN,
                List.of(List.of(new ClipboardEntry(checked, Component.literal(text)))), false);
    }

    @GameTest(template = "empty")
    public static void locksHeldBoardAndProtectsRealEditPacket(GameTestHelper helper) {
        var player = player(helper);
        var stack = player.getMainHandItem();
        stack.set(AllDataComponents.CLIPBOARD_CONTENT, content(false, "Keep"));
        var request = new ClipboardLockPayload(0, Optional.empty(), player.level().dimension().location(), true);
        helper.assertTrue(ClipboardLocks.setLocked(player, request), "Lock not accepted");
        new ClipboardEditPacket(0, content(false, "Overwrite"), null).handle(player);
        helper.assertTrue(stack.get(AllDataComponents.CLIPBOARD_CONTENT).pages().getFirst().getFirst().text.getString().equals("Keep"), "Real packet overwrote locked board");
        new ClipboardEditPacket(0, content(true, "Keep"), null).handle(player);
        helper.assertTrue(stack.get(AllDataComponents.CLIPBOARD_CONTENT).pages().getFirst().getFirst().checked, "Checks blocked");
        new ClipboardEditPacket(0, null, null).handle(player);
        helper.assertTrue(stack.has(AllDataComponents.CLIPBOARD_CONTENT), "Erased locked content");
        ClipboardLocks.setLocked(player, new ClipboardLockPayload(0, Optional.empty(), request.dimension(), false));
        new ClipboardEditPacket(0, content(false, "Unlocked"), null).handle(player);
        helper.assertTrue(stack.get(AllDataComponents.CLIPBOARD_CONTENT).pages().getFirst().getFirst().text.getString().equals("Unlocked"), "Unlocked edit blocked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedBlankBoardRejectsBothScanners(GameTestHelper helper) {
        var player = player(helper);
        var stack = player.getMainHandItem();
        stack.set(CreateClipboardToolkit.LOCKED, true);
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        barrel.setItem(0, new ItemStack(Items.DIAMOND, 3));
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(pos)));
        StorageScanner.scan(player, helper.absolutePos(pos));
        AreaScanner.scan(player, helper.absolutePos(pos), helper.absolutePos(pos));
        helper.assertTrue(!stack.has(AllDataComponents.CLIPBOARD_CONTENT) && !stack.has(CreateClipboardToolkit.AREA_CONTENTS), "Locked board scanned");
        stack.remove(CreateClipboardToolkit.LOCKED);
        AreaScanner.scan(player, helper.absolutePos(pos), helper.absolutePos(pos));
        helper.assertTrue(stack.has(CreateClipboardToolkit.AREA_CONTENTS), "Unlocked scan failed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedLockAllowsCopyOutButBlocksCopyIntoHeldLock(GameTestHelper helper) {
        var rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel.below(), Blocks.STONE);
        helper.setBlock(rel, AllBlocks.CLIPBOARD.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        var pos = helper.absolutePos(rel);
        var board = (ClipboardBlockEntity) helper.getLevel().getBlockEntity(pos);
        var map = new PatchedDataComponentMap(board.components());
        map.set(AllDataComponents.CLIPBOARD_CONTENT, content(false, "Source"));
        board.setComponents(map);
        var player = player(helper);
        helper.assertTrue(ClipboardLocks.setLocked(player, new ClipboardLockPayload(0, Optional.of(pos), player.level().dimension().location(), true)), "Placed lock failed");
        new ClipboardEditPacket(0, content(false, "Overwrite"), pos).handle(player);
        helper.assertTrue(board.components().get(AllDataComponents.CLIPBOARD_CONTENT).pages().getFirst().getFirst().text.getString().equals("Source"), "Placed content overwritten");
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ClipboardValueSettingsHandler.rightClickToCopy(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
        helper.assertTrue(player.getMainHandItem().get(AllDataComponents.CLIPBOARD_CONTENT).pages().getFirst().getFirst().text.getString().equals("Source"), "Could not copy FROM locked source");
        player.getMainHandItem().set(CreateClipboardToolkit.LOCKED, true);
        player.getMainHandItem().set(AllDataComponents.CLIPBOARD_CONTENT, content(false, "Held"));
        ClipboardValueSettingsHandler.rightClickToCopy(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
        helper.assertTrue(player.getMainHandItem().get(AllDataComponents.CLIPBOARD_CONTENT).pages().getFirst().size() == 1, "Copied INTO locked destination");
        var drop = AllBlocks.CLIPBOARD.get().getCloneItemStack(helper.getLevel(), pos, helper.getLevel().getBlockState(pos));
        helper.assertTrue(ClipboardLocks.isLocked(drop), "Pickup lost lock component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsInvalidLockRequests(GameTestHelper helper) {
        var player = player(helper);
        var dimension = player.level().dimension().location();
        ClipboardLocks.setLocked(player, new ClipboardLockPayload(9, Optional.empty(), dimension, true));
        ClipboardLocks.setLocked(player, new ClipboardLockPayload(0, Optional.empty(), net.minecraft.resources.ResourceLocation.parse("minecraft:the_nether"), true));
        player.setGameMode(GameType.SPECTATOR);
        ClipboardLocks.setLocked(player, new ClipboardLockPayload(0, Optional.empty(), dimension, true));
        helper.assertTrue(!ClipboardLocks.isLocked(player.getMainHandItem()), "Invalid request changed lock");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingCopyProtectsTargetAndAllowsLockedSource(GameTestHelper helper) {
        var source = AllBlocks.CLIPBOARD.asStack();
        source.set(AllDataComponents.CLIPBOARD_CONTENT, content(false, "Source"));
        source.set(CreateClipboardToolkit.LOCKED, true);
        var target = AllBlocks.CLIPBOARD.asStack();
        var recipe = new com.simibubi.create.foundation.recipe.ItemCopyingRecipe(
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, List.of(source, target, ItemStack.EMPTY, ItemStack.EMPTY));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Locked source cannot be copied");
        target.set(CreateClipboardToolkit.LOCKED, true);
        helper.assertTrue(!recipe.matches(input, helper.getLevel()), "Locked target accepted for crafting copy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boardCopyPreservesOrderItemsCountsAndChecks(GameTestHelper helper) {
        var rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel.below(), Blocks.STONE);
        helper.setBlock(rel, AllBlocks.CLIPBOARD.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        var pos = helper.absolutePos(rel);
        var board = (ClipboardBlockEntity) helper.getLevel().getBlockEntity(pos);
        var iron = new ItemStack(Items.IRON_INGOT);
        iron.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Special iron"));
        var sourceEntry = new ClipboardEntry(false, Component.literal("Iron x128")).displayItem(iron, 128);
        var checked = new ClipboardEntry(true, Component.literal("Diamond x12")).displayItem(new ItemStack(Items.DIAMOND), 12);
        var source = new ClipboardContent(ClipboardOverrides.ClipboardType.WRITTEN,
                List.of(List.of(sourceEntry), List.of(checked, new ClipboardEntry(false, Component.literal("Note")))) , true);
        var map = new PatchedDataComponentMap(board.components());
        map.set(AllDataComponents.CLIPBOARD_CONTENT, source);
        map.set(CreateClipboardToolkit.LOCKED.get(), true);
        board.setComponents(map);
        var player = player(helper);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ClipboardValueSettingsHandler.rightClickToCopy(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
        var entries = ClipboardEntry.readAll(player.getMainHandItem()).stream().flatMap(List::stream).toList();
        helper.assertTrue(entries.size() == 3, "Multi-page copy lost entries");
        var copied = entries.get(0);
        helper.assertTrue(ItemStack.matches(copied.icon, iron), "Icon components lost");
        helper.assertTrue(copied.itemAmount == 128 && ClipboardOrders.requestedAmount(copied) == 128, "Copied list cannot request original quantity");
        helper.assertTrue(entries.get(1).icon.is(Items.DIAMOND) && entries.get(1).itemAmount == 12
                && entries.get(1).checked && ClipboardOrders.requestedAmount(entries.get(1)) == 0, "Checked entry was not preserved/excluded");
        helper.assertTrue(entries.get(2).icon.isEmpty() && entries.get(2).text.getString().equals("Note"), "Plain text copy changed");
        ClipboardValueSettingsHandler.rightClickToCopy(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
        helper.assertTrue(ClipboardEntry.readAll(player.getMainHandItem()).stream().mapToInt(List::size).sum() == 3, "Repeated copy duplicated entries");
        // The actual component must survive a save/load, not only the screen's temporary list.
        var saved = player.getMainHandItem().save(helper.getLevel().registryAccess());
        var loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) saved);
        var loadedEntry = ClipboardEntry.readAll(loaded).getFirst().getFirst();
        helper.assertTrue(ItemStack.matches(loadedEntry.icon, iron) && loadedEntry.itemAmount == 128, "Saved copy lost ordering data");
        copied.icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Edited copy"));
        copied.text.append(" edited");
        helper.assertTrue(sourceEntry.icon.getHoverName().getString().equals("Special iron")
                && sourceEntry.text.getString().equals("Iron x128"), "Copy aliases source data");
        helper.succeed();
    }
}
