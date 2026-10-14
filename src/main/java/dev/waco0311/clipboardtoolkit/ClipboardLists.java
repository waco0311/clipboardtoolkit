package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;

public final class ClipboardLists {
    private static final int ENTRIES_PER_PAGE = 7;
    public static final int MAX_ENTRIES = 350;
    public static final int MAX_SLOTS = 16384;

    private ClipboardLists() {}

    private static MutableComponent entryText(ClipboardEntry entry) {
        var text = entry.text.copy().append(Component.literal("\n x" + entry.itemAmount)
                .withStyle(ChatFormatting.BLACK));
        // Match Create's schematic checklist: its stack symbol always denotes 64 items.
        text.append(Component.literal(" | " + entry.itemAmount / 64 + "\u25A4 +" + entry.itemAmount % 64)
                .withStyle(ChatFormatting.GRAY));
        return text;
    }

    public static boolean isBlank(ClipboardContent content) {
        return !content.readOnly() && content.copiedValues().isEmpty()
                && content.pages().stream().flatMap(List::stream)
                .allMatch(entry -> entry.text.getString().isBlank() && entry.icon.isEmpty()
                        && entry.itemAmount == 0 && !entry.checked);
    }

    public static ClipboardContent collect(Container container) {
        return collect(new InvWrapper(container));
    }

    public static ClipboardContent collect(IItemHandler inventory) {
        Accumulator accumulator = new Accumulator();
        accumulator.add(inventory);
        return accumulator.content();
    }

    public static final class Accumulator {
        private final List<ClipboardEntry> entries = new ArrayList<>();
        private int slotsRead;

        public void add(IItemHandler inventory) {
            int slots = inventory.getSlots();
            if (slots < 0 || slots > MAX_SLOTS - slotsRead) throw new ScanLimitException();
            slotsRead += slots;
            for (int slot = 0; slot < slots; slot++) add(inventory.getStackInSlot(slot));
        }

        public void add(ItemStack stack) {
            if (stack.isEmpty()) return;
            ClipboardEntry match = null;
            for (ClipboardEntry entry : entries) {
                if (ItemStack.isSameItemSameComponents(entry.icon, stack)) {
                    match = entry;
                    break;
                }
            }
            if (match == null) {
                if (entries.size() == MAX_ENTRIES) throw new ScanLimitException();
                entries.add(new ClipboardEntry(false, stack.getHoverName().copy())
                        .displayItem(stack.copyWithCount(1), stack.getCount()));
            } else {
                long amount = (long) match.itemAmount + stack.getCount();
                if (amount > Integer.MAX_VALUE) throw new ScanLimitException();
                match.itemAmount = (int) amount;
            }
        }

        public ClipboardContent content() {
            List<ClipboardEntry> snapshot = entries.stream().map(entry ->
                    new ClipboardEntry(false, entryText(entry))
                            .displayItem(entry.icon.copy(), entry.itemAmount)).toList();
            List<List<ClipboardEntry>> pages = new ArrayList<>();
            for (int offset = 0; offset < snapshot.size(); offset += ENTRIES_PER_PAGE) {
                pages.add(List.copyOf(snapshot.subList(offset, Math.min(offset + ENTRIES_PER_PAGE, snapshot.size()))));
            }
            return new ClipboardContent(ClipboardType.WRITTEN, List.copyOf(pages), true);
        }
    }

    public static final class ScanLimitException extends RuntimeException {}
}
