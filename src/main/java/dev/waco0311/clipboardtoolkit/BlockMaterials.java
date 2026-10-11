package dev.waco0311.clipboardtoolkit;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class BlockMaterials {
    private BlockMaterials() {}

    public static boolean isDuplicateHalf(BlockState state, BlockPos pos, SelectionBox selection) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return selection.contains(pos.below());
        }
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return selection.contains(pos.relative(state.getValue(BedBlock.FACING).getOpposite()));
        }
        return false;
    }

    // False means the material conversion is unsupported; report that instead of silently omitting it.
    public static boolean collect(BlockState state, BlockEntity entity, ClipboardLists.Accumulator result) {
        if (state.isAir()) return true;
        ItemRequirement requirement = ItemRequirement.of(state, entity);
        if (requirement.isInvalid()) return false;
        if (requirement.isEmpty()) return true;
        boolean complete = true;
        for (var item : requirement.getRequiredItems()) {
            if (item.usage == ItemRequirement.ItemUseType.CONSUME) result.add(item.stack);
            else complete = false;
        }
        return complete;
    }
}

