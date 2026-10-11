package dev.waco0311.clipboardtoolkit;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaScanTest {
    @Test
    void normalizesInclusiveBoundsAndRejectsOverflowAndOversizedRequests() {
        var box = SelectionBox.between(new BlockPos(15, 15, 15), BlockPos.ZERO);
        assertTrue(box.isValid());
        assertEquals(BlockPos.ZERO, box.min());
        assertTrue(box.contains(new BlockPos(15, 15, 15)));
        assertFalse(box.contains(new BlockPos(16, 0, 0)));
        assertEquals(16, box.bounds().getXsize());
        assertFalse(SelectionBox.between(BlockPos.ZERO, new BlockPos(16, 15, 15)).isValid());
        assertFalse(SelectionBox.between(BlockPos.ZERO, new BlockPos(64, 0, 0)).isValid());
        assertFalse(SelectionBox.between(new BlockPos(Integer.MIN_VALUE, 0, 0),
                new BlockPos(Integer.MAX_VALUE, 0, 0)).isValid());
    }

    @Test
    void countsDoorAndBedOnceAndKeepsBoundaryHalf() {
        var selection = SelectionBox.between(BlockPos.ZERO, new BlockPos(2, 2, 2));
        var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        var upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        assertFalse(BlockMaterials.isDuplicateHalf(lower, BlockPos.ZERO, selection));
        assertTrue(BlockMaterials.isDuplicateHalf(upper, BlockPos.ZERO.above(), selection));
        assertFalse(BlockMaterials.isDuplicateHalf(upper, BlockPos.ZERO,
                SelectionBox.between(BlockPos.ZERO, BlockPos.ZERO)));
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        assertFalse(BlockMaterials.isDuplicateHalf(bed.setValue(BedBlock.PART, BedPart.FOOT), BlockPos.ZERO, selection));
        assertTrue(BlockMaterials.isDuplicateHalf(bed.setValue(BedBlock.PART, BedPart.HEAD),
                BlockPos.ZERO.south(), selection));
        var accumulator = new ClipboardLists.Accumulator();
        assertTrue(BlockMaterials.collect(lower, null, accumulator));
        assertEquals(1, accumulator.content().pages().getFirst().getFirst().itemAmount);
    }

    @Test
    void createRequirementsCountDoubleSlabsAsTwoItems() {
        var accumulator = new ClipboardLists.Accumulator();
        assertTrue(BlockMaterials.collect(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE), null, accumulator));
        assertEquals(2, accumulator.content().pages().getFirst().getFirst().itemAmount);
        assertTrue(accumulator.content().pages().getFirst().getFirst().icon.is(Items.OAK_SLAB));
    }

    @Test
    void separatesPlacedAndStoredAndDoesNotAliasDisplay() {
        AreaContents contents = sample();
        var display = contents.display();
        assertEquals(2, display.pages().size());
        assertEquals(10, contents.placed().pages().getFirst().getFirst().itemAmount);
        assertEquals(20, contents.stored().pages().getFirst().getFirst().itemAmount);
        assertEquals(10, display.pages().get(0).get(1).itemAmount);
        assertEquals(20, display.pages().get(1).get(1).itemAmount);
        display.pages().get(0).get(1).checked = true;
        display.pages().get(0).get(1).icon.setCount(7);
        assertFalse(contents.placed().pages().getFirst().getFirst().checked);
        assertEquals(1, contents.placed().pages().getFirst().getFirst().icon.getCount());
        assertEquals(2, new AreaContents(ClipboardContent.EMPTY, ClipboardContent.EMPTY).display().pages().size());
    }

    @Test
    void preservesSeparateListsThroughDiskAndNetworkCodecs() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var encoded = AreaContents.CODEC.encodeStart(ops, sample()).getOrThrow();
        var restored = AreaContents.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(10, restored.placed().pages().getFirst().getFirst().itemAmount);
        assertEquals(20, restored.stored().pages().getFirst().getFirst().itemAmount);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            AreaContents.STREAM_CODEC.encode(buffer, restored);
            var decoded = AreaContents.STREAM_CODEC.decode(buffer);
            assertEquals(10, decoded.placed().pages().getFirst().getFirst().itemAmount);
            assertEquals(20, decoded.stored().pages().getFirst().getFirst().itemAmount);
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsCombinedDisplayAboveCreatePageLimit() {
        var one = sample().placed().pages().getFirst().getFirst();
        var entries = java.util.Collections.nCopies(151, one);
        var section = new ClipboardContent(sample().placed().type(), List.of(entries), true);
        assertThrows(ClipboardLists.ScanLimitException.class, () -> new AreaContents(section, section).display());
    }

    private AreaContents sample() {
        var placed = new ClipboardLists.Accumulator();
        placed.add(new ItemStack(Items.IRON_BLOCK, 10));
        var stored = new ClipboardLists.Accumulator();
        stored.add(new ItemStack(Items.IRON_BLOCK, 20));
        return new AreaContents(placed.content(), stored.content());
    }
}

