package dev.waco0311.clipboardtoolkit.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.recipe.ItemCopyingRecipe;
import dev.waco0311.clipboardtoolkit.ClipboardLocks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemCopyingRecipe.class, remap = false)
public abstract class ClipboardCopyRecipeMixin {
    @Redirect(method = "copyCheck", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/recipe/ItemCopyingRecipe$SupportsItemCopying;canCopyToItem(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean clipboardtoolkit$protectCraftingTarget(ItemCopyingRecipe.SupportsItemCopying item, ItemStack stack) {
        return !(AllBlocks.CLIPBOARD.isIn(stack) && ClipboardLocks.isLocked(stack)) && item.canCopyToItem(stack);
    }
}
