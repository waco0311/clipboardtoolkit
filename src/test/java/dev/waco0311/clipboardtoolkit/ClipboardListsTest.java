package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardListsTest {
    @Test
    void readsCapabilityOnlyStorageWithoutInsertingOrExtracting() {
        ItemStack first = new ItemStack(Items.DIAMOND, 128);
        ItemStack second = new ItemStack(Items.DIAMOND, 256);
        IItemHandler handler = new IItemHandler() {
            public int getSlots() { return 2; }
            public ItemStack getStackInSlot(int slot) { return slot == 0 ? first : second; }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                throw new AssertionError("Scanning must never insert items");
            }
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                throw new AssertionError("Scanning must never extract items");
            }
            public int getSlotLimit(int slot) { return 4096; }
            public boolean isItemValid(int slot, ItemStack stack) { return true; }
        };
        var result = ClipboardLists.collect(handler);
        assertEquals(384, result.pages().getFirst().getFirst().itemAmount);
        assertEquals(128, first.getCount());
        assertEquals(256, second.getCount());
    }

    @Test
    void rejectsExcessKindsWithoutTruncation() {
        var handler = new ItemStackHandler(ClipboardLists.MAX_ENTRIES + 1);
        for (int i = 0; i < handler.getSlots(); i++) {
            var item = new ItemStack(Items.STONE);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("Unique " + i));
            handler.setStackInSlot(i, item);
        }
        assertThrows(ClipboardLists.ScanLimitException.class, () -> ClipboardLists.collect(handler));
        assertEquals(1, handler.getStackInSlot(ClipboardLists.MAX_ENTRIES).getCount());
        handler.setStackInSlot(ClipboardLists.MAX_ENTRIES, ItemStack.EMPTY);
        assertEquals(50, ClipboardLists.collect(handler).pages().size());
    }

    @Test
    void rejectsOversizedHandlersBeforeReadingAndCountsBeforeOverflow() {
        var oversized = new ItemStackHandler(0) {
            public int getSlots() { return ClipboardLists.MAX_SLOTS + 1; }
            public ItemStack getStackInSlot(int slot) { throw new AssertionError("Must reject before reading"); }
        };
        assertThrows(ClipboardLists.ScanLimitException.class, () -> ClipboardLists.collect(oversized));
        var counts = new ItemStackHandler(2);
        counts.setStackInSlot(0, new ItemStack(Items.STONE, Integer.MAX_VALUE));
        counts.setStackInSlot(1, new ItemStack(Items.STONE, 1));
        assertThrows(ClipboardLists.ScanLimitException.class, () -> ClipboardLists.collect(counts));
        assertEquals(Integer.MAX_VALUE, counts.getStackInSlot(0).getCount());
    }

    @Test
    void combinesBothHalvesWithoutChangingInventory() {
        ItemStack leftStack = new ItemStack(Items.IRON_INGOT, 64);
        ItemStack rightStack = new ItemStack(Items.IRON_INGOT, 32);
        var left = new SimpleContainer(27);
        var right = new SimpleContainer(27);
        left.setItem(0, leftStack);
        right.setItem(26, rightStack);
        var result = ClipboardLists.collect(new CompoundContainer(left, right));
        assertEquals(1, result.pages().size());
        assertEquals(1, result.pages().getFirst().size());
        var entry = result.pages().getFirst().getFirst();
        assertEquals(96, entry.itemAmount);
        assertEquals(1, entry.icon.getCount());
        assertFalse(entry.checked);
        assertTrue(entry.text.getString().contains("96"));
        assertEquals(64, leftStack.getCount());
        assertEquals(32, rightStack.getCount());
        assertNotSame(leftStack, entry.icon);
    }

    @Test
    void keepsNamesAndDamageSeparate() {
        ItemStack named = new ItemStack(Items.IRON_INGOT, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved"));
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(12);
        var result = ClipboardLists.collect(new SimpleContainer(
                new ItemStack(Items.IRON_INGOT, 5), named, named.copy(),
                new ItemStack(Items.IRON_PICKAXE), damaged));
        var entries = result.pages().getFirst();
        assertEquals(4, entries.size());
        assertEquals(5, entries.get(0).itemAmount);
        assertEquals(6, entries.get(1).itemAmount);
        assertEquals("Reserved", entries.get(1).icon.getHoverName().getString());
        assertEquals(12, entries.get(3).icon.getDamageValue());
    }

    @Test
    void paginatesAll54DistinctStacksAndDoesNotAliasSource() {
        var container = new SimpleContainer(54);
        for (int i = 0; i < 54; i++) {
            var stack = new ItemStack(Items.STONE, i + 1);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Stack " + i));
            container.setItem(i, stack);
        }
        var result = ClipboardLists.collect(container);
        assertEquals(8, result.pages().size());
        assertEquals(5, result.pages().getLast().size());
        assertTrue(result.pages().stream().allMatch(page -> page.size() <= 7));
        assertEquals(1485, result.pages().stream().flatMap(List::stream).mapToInt(e -> e.itemAmount).sum());
        container.getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        assertEquals("Stack 0", result.pages().getFirst().getFirst().icon.getHoverName().getString());
        assertTrue(result.readOnly());
        assertEquals(ClipboardType.WRITTEN, result.type());
    }

    @Test
    void refusesExistingTextIconsChecksAndCopiedSettings() {
        assertTrue(ClipboardLists.isBlank(ClipboardContent.EMPTY));
        assertFalse(ClipboardLists.isBlank(new ClipboardContent(ClipboardType.WRITTEN,
                List.of(List.of(new ClipboardEntry(false, Component.literal("Keep me")))), false)));
        assertFalse(ClipboardLists.isBlank(new ClipboardContent(ClipboardType.WRITTEN,
                List.of(List.of(new ClipboardEntry(false, Component.empty()).displayItem(new ItemStack(Items.STONE), 2))), false)));
        assertFalse(ClipboardLists.isBlank(new ClipboardContent(ClipboardType.WRITTEN,
                List.of(List.of(new ClipboardEntry(true, Component.empty()))), false)));
        assertFalse(ClipboardLists.isBlank(ClipboardContent.EMPTY.setCopiedValues(new CompoundTag())));
        assertFalse(ClipboardLists.isBlank(ClipboardContent.EMPTY.setReadOnly(true)));
    }

    @Test
    void emptyChestProducesNoPages() {
        assertTrue(ClipboardLists.collect(new SimpleContainer(27)).pages().isEmpty());
    }
}
