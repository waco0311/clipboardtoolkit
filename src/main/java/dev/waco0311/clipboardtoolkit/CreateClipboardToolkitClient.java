package dev.waco0311.clipboardtoolkit;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = CreateClipboardToolkit.MODID, dist = Dist.CLIENT)
public class CreateClipboardToolkitClient {
    private final AreaSelectionClient areaSelection = new AreaSelectionClient();
    private static final KeyMapping SCAN = new KeyMapping("key.clipboardtoolkit.scan",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.clipboardtoolkit");

    public CreateClipboardToolkitClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onInteraction);
        NeoForge.EVENT_BUS.addListener(areaSelection::tick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SCAN);
        event.register(AreaSelectionClient.SELECT);
    }

    private void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (areaSelection.interact(event)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || !SCAN.isDown() || mc.player == null || mc.level == null
                || mc.screen != null || !AllBlocks.CLIPBOARD.isIn(mc.player.getMainHandItem())
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        // Capabilities may only exist on the server. Let it decide whether this
        // target exposes inventory instead of rejecting modded storage on the client.
        // NeoForge fires this for both hands even after cancellation. Consume both
        // so an offhand item cannot open the chest or place a block during a scan.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() == InteractionHand.MAIN_HAND
                && !mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem().getItem())) {
            PacketDistributor.sendToServer(new ScanStoragePayload(hit.getBlockPos()));
        }
    }
}
