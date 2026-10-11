package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardOrdersTest {
    @Test
    void checkedEntryIsExcludedAndUncheckingRestoresItsSavedAmount() {
        var icon = new ItemStack(Items.IRON_INGOT);
        var entry = new ClipboardEntry(false, Component.literal("Iron")).displayItem(icon, 128);
        assertEquals(128, ClipboardOrders.requestedAmount(entry));
        entry.checked = true;
        assertEquals(0, ClipboardOrders.requestedAmount(entry));
        assertTrue(entry.checked);
        assertEquals(128, entry.itemAmount);
        assertSame(icon, entry.icon);
        assertEquals("Iron", entry.text.getString());
        entry.checked = false;
        assertEquals(128, ClipboardOrders.requestedAmount(entry));
    }

    @Test
    void createOrderImportHasExactlyOneMatchingRedirectTarget() throws Exception {
        // Check the dependency bytecode: JUnit does not launch the client mixin environment.
        var screen = Class.forName("com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen");
        try (var stream = screen.getResourceAsStream("StockKeeperRequestScreen.class")) {
            assertNotNull(stream);
            var type = new ClassNode();
            new ClassReader(stream).accept(type, 0);
            var method = type.methods.stream()
                .filter(candidate -> candidate.name.equals("requestSchematicList") && candidate.desc.equals("()V"))
                .findFirst().orElseThrow();
            int matches = 0;
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                        && field.owner.equals("com/simibubi/create/content/equipment/clipboard/ClipboardEntry")
                        && field.name.equals("itemAmount") && field.desc.equals("I")) {
                    matches++;
                }
            }
            assertEquals(1, matches, "Create must still read the amount at our redirect target");
        }
    }
}
