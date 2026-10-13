package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardLocksTest {
    private ClipboardContent content(boolean checked, String text, int count) {
        return new ClipboardContent(ClipboardType.WRITTEN, List.of(List.of(
                new ClipboardEntry(checked, Component.literal(text)).displayItem(new ItemStack(Items.IRON_INGOT), count))), true);
    }

    @Test void allowsChecksWithoutChangingMetadataOrMutatingSource() {
        var tag = new CompoundTag();
        tag.putString("Settings", "preserved");
        var before = content(false, "Iron", 64).setCopiedValues(tag);
        var after = ClipboardLocks.mergeChecks(before, content(true, "Iron", 64).setReadOnly(false));
        assertTrue(after.pages().getFirst().getFirst().checked);
        assertFalse(before.pages().getFirst().getFirst().checked);
        assertTrue(after.readOnly());
        assertEquals(before.copiedValues(), after.copiedValues());
        assertEquals(64, after.pages().getFirst().getFirst().itemAmount);
        assertEquals(0, ClipboardOrders.requestedAmount(after.pages().getFirst().getFirst()));
    }

    @Test void refusesOverwriteEraseAndChangedAmounts() {
        var before = content(false, "Iron", 64);
        assertSame(before, ClipboardLocks.mergeChecks(before, null));
        assertSame(before, ClipboardLocks.mergeChecks(before, ClipboardContent.EMPTY));
        assertSame(before, ClipboardLocks.mergeChecks(before, content(true, "Replaced", 64)));
        assertSame(before, ClipboardLocks.mergeChecks(before, content(true, "Iron", 1)));
        var changedIcon = content(true, "Iron", 64);
        changedIcon.pages().getFirst().getFirst().icon = new ItemStack(Items.DIAMOND);
        assertSame(before, ClipboardLocks.mergeChecks(before, changedIcon));
    }

    @Test void lockSurvivesItemSaveAndLoad() {
        var stack = AllBlocks.CLIPBOARD.asStack();
        assertFalse(ClipboardLocks.isLocked(stack));
        stack.set(CreateClipboardToolkit.LOCKED, true);
        var ops = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(NbtOps.INSTANCE);
        var saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        var loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        assertTrue(ClipboardLocks.isLocked(loaded));
        loaded.remove(CreateClipboardToolkit.LOCKED);
        assertFalse(ClipboardLocks.isLocked(loaded));
    }

    @Test void closingScreenCanTrimBlankEditorRowsWithoutLosingChecks() {
        var before = content(false, "Iron", 64);
        before = before.setPages(List.of(List.of(before.pages().getFirst().getFirst(),
                new ClipboardEntry(false, Component.empty())), List.of()));
        var after = ClipboardLocks.mergeChecks(before, content(true, "Iron", 64));
        assertTrue(after.pages().getFirst().getFirst().checked);
        assertEquals(64, after.pages().getFirst().getFirst().itemAmount);
        var empty = ClipboardLocks.mergeChecks(ClipboardContent.EMPTY.setType(ClipboardType.EDITING), null);
        assertEquals(ClipboardType.WRITTEN, empty.type());
    }
}
