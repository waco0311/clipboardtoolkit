package dev.waco0311.clipboardtoolkit.mixin;

import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import dev.waco0311.clipboardtoolkit.ClipboardOrders;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StockKeeperRequestScreen.class, remap = false)
public abstract class StockKeeperRequestScreenMixin {
    // Create skips zero amounts; only change the order import, never the saved entry.
    @Redirect(method = "requestSchematicList", at = @At(value = "FIELD",
        target = "Lcom/simibubi/create/content/equipment/clipboard/ClipboardEntry;itemAmount:I",
        opcode = Opcodes.GETFIELD), require = 1, allow = 1)
    private int clipboardtoolkit$uncheckedAmount(ClipboardEntry entry) {
        return ClipboardOrders.requestedAmount(entry);
    }
}
