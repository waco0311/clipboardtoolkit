package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.Arrays;

/** Opt-in isolated client launch: validate real client transformations, then exit. Never shipped. */
@EventBusSubscriber(modid = CreateClipboardToolkit.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClipboardLockClientSmokeTest {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("clipboardtoolkit.clientSmokeTest")) return;
        event.enqueueWork(() -> {
            try {
                var components = DataComponentMap.builder().set(CreateClipboardToolkit.LOCKED.get(), true).build();
                var screen = new ClipboardScreen(0, components, null);
                if (!(screen instanceof ClipboardLockClient.LockScreen)) throw new AssertionError("Screen mixin missing");
                screen.init(Minecraft.getInstance(), 960, 540);
                var readonly = ClipboardScreen.class.getDeclaredField("readonly");
                readonly.setAccessible(true);
                if (!readonly.getBoolean(screen)) throw new AssertionError("Locked editor is editable");
                var clear = ClipboardScreen.class.getDeclaredField("clearBtn");
                clear.setAccessible(true);
                if (((net.minecraft.client.gui.components.AbstractWidget) clear.get(screen)).active)
                    throw new AssertionError("Locked erase button is active");
                long buttons = screen.children().stream()
                        .filter(child -> child instanceof com.simibubi.create.foundation.gui.widget.IconButton).count();
                if (buttons != 3) throw new AssertionError("Expected lock, erase and close buttons, got " + buttons);
                if (Arrays.stream(StockKeeperRequestScreen.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().contains("clipboardtoolkit$uncheckedAmount")))
                    throw new AssertionError("Order filter mixin missing");
                System.out.println("CLIPBOARD_LOCK_CLIENT_SMOKE_TEST_PASSED");
                System.exit(0);
            } catch (Throwable error) {
                error.printStackTrace();
                System.exit(1);
            }
        });
    }
}
