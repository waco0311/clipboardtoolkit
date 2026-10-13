package dev.waco0311.clipboardtoolkit;

import net.minecraft.client.Minecraft;

public final class ClipboardLockClient {
    private ClipboardLockClient() {}

    public interface LockScreen {
        void clipboardtoolkit$receiveLock(ClipboardLockPayload state);
    }

    public static void receive(ClipboardLockPayload state) {
        if (Minecraft.getInstance().screen instanceof LockScreen screen) screen.clipboardtoolkit$receiveLock(state);
    }
}
