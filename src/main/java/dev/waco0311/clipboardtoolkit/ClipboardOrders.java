package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;

public final class ClipboardOrders {
    private ClipboardOrders() {}

    public static int requestedAmount(ClipboardEntry entry) {
        return entry.checked ? 0 : entry.itemAmount;
    }
}
