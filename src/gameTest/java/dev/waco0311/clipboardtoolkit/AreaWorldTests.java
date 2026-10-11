package dev.waco0311.clipboardtoolkit;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(CreateClipboardToolkit.MODID)
@PrefixGameTestTemplate(false)
public class AreaWorldTests {
    @GameTest(template = "empty")
    public static void recordsWholeVaultOnceAndRefusesPartialVault(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = a.east();
        var state = AllBlocks.ITEM_VAULT.getDefaultState().setValue(
                com.simibubi.create.content.logistics.vault.ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
        helper.setBlock(a, state);
        helper.setBlock(b, state);
        helper.runAfterDelay(10, () -> {
            var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(a), null);
            helper.assertTrue(handler != null, "Vault inventory missing");
            helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(), "Insertion failed");
            helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 32), false).isEmpty(), "Insertion failed");
            var player = player(helper);
            AreaScanner.scan(player, helper.absolutePos(a), helper.absolutePos(b));
            var contents = player.getMainHandItem().get(CreateClipboardToolkit.AREA_CONTENTS);
            helper.assertTrue(contents != null, "Vault area was not saved");
            int amount = contents.stored().pages().stream().flatMap(List::stream).mapToInt(e -> e.itemAmount).sum();
            helper.assertTrue(amount == 96, "Vault was counted more than once");
            var partial = player(helper);
            AreaScanner.scan(partial, helper.absolutePos(b), helper.absolutePos(b));
            helper.assertTrue(!partial.getMainHandItem().has(CreateClipboardToolkit.AREA_CONTENTS), "Partial vault was saved");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void excludesPersonalEnderInventory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.ENDER_CHEST);
        var player = player(helper);
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));
        AreaScanner.scan(player, helper.absolutePos(pos), helper.absolutePos(pos));
        var contents = player.getMainHandItem().get(CreateClipboardToolkit.AREA_CONTENTS);
        helper.assertTrue(contents != null, "Ender chest block was not recorded");
        helper.assertTrue(contents.stored().pages().isEmpty(), "Personal inventory leaked into area result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void respectsLockedStorage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        barrel.setItem(0, new ItemStack(Items.DIAMOND, 12));
        var data = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
        data.putString("Lock", "Secret key");
        barrel.loadWithComponents(data, helper.getLevel().registryAccess());
        var player = player(helper);
        AreaScanner.scan(player, helper.absolutePos(pos), helper.absolutePos(pos));
        helper.assertTrue(!player.getMainHandItem().has(CreateClipboardToolkit.AREA_CONTENTS), "Locked storage was scanned");
        helper.assertTrue(!player.getMainHandItem().has(AllDataComponents.CLIPBOARD_CONTENT), "Partial data was written");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recordsPlacedAndBarrelSeparately(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(2, 1, 1);
        helper.setBlock(a, Blocks.IRON_BLOCK);
        helper.setBlock(b, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(b));
        barrel.setItem(0, new ItemStack(Items.IRON_BLOCK, 20));
        var player = player(helper);
        AreaScanner.scan(player, helper.absolutePos(a), helper.absolutePos(b));
        var area = player.getMainHandItem().get(CreateClipboardToolkit.AREA_CONTENTS);
        helper.assertTrue(area != null, "Range snapshot was not saved");
        int placed = area.placed().pages().stream().flatMap(List::stream)
                .filter(e -> e.icon.is(Items.IRON_BLOCK)).mapToInt(e -> e.itemAmount).sum();
        int stored = area.stored().pages().stream().flatMap(List::stream)
                .filter(e -> e.icon.is(Items.IRON_BLOCK)).mapToInt(e -> e.itemAmount).sum();
        helper.assertTrue(placed == 1 && stored == 20, "Placed and stored amounts were mixed");
        helper.assertTrue(barrel.getItem(0).getCount() == 20, "Inventory changed during scanning");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void countsDoubleChestOnlyOnce(GameTestHelper helper) {
        BlockPos left = new BlockPos(1, 1, 1);
        BlockPos right = left.east();
        doubleChest(helper, left, right);
        var player = player(helper);
        AreaScanner.scan(player, helper.absolutePos(left), helper.absolutePos(right));
        var area = player.getMainHandItem().get(CreateClipboardToolkit.AREA_CONTENTS);
        helper.assertTrue(area != null, "Combined chest snapshot missing");
        int amount = area.stored().pages().stream().flatMap(List::stream).mapToInt(e -> e.itemAmount).sum();
        helper.assertTrue(amount == 96, "Double chest counted twice or only one half");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refusesPartialDoubleChestWithoutWriting(GameTestHelper helper) {
        BlockPos left = new BlockPos(1, 1, 1);
        doubleChest(helper, left, left.east());
        var player = player(helper);
        AreaScanner.scan(player, helper.absolutePos(left), helper.absolutePos(left));
        helper.assertTrue(!player.getMainHandItem().has(CreateClipboardToolkit.AREA_CONTENTS), "Partial area was saved");
        helper.assertTrue(!player.getMainHandItem().has(AllDataComponents.CLIPBOARD_CONTENT), "Visible clipboard changed");
        helper.succeed();
    }

    private static void doubleChest(GameTestHelper helper, BlockPos left, BlockPos right) {
        var state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        helper.setBlock(left, state.setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(right, state.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(left))).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(right))).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AreaTester"));
        player.setGameMode(GameType.SURVIVAL);
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.CLIPBOARD.asStack());
        return player;
    }
}

