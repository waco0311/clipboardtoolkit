package dev.waco0311.clipboardtoolkit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record SelectionBox(BlockPos min, BlockPos max) {
    public static final int MAX_VOLUME = 4096;
    public static final int MAX_SIDE = 64;

    public static SelectionBox between(BlockPos a, BlockPos b) {
        return new SelectionBox(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    public boolean isValid() {
        long x = (long) max.getX() - min.getX() + 1;
        long y = (long) max.getY() - min.getY() + 1;
        long z = (long) max.getZ() - min.getZ() + 1;
        return x > 0 && y > 0 && z > 0 && x <= MAX_SIDE && y <= MAX_SIDE && z <= MAX_SIDE
                && x * y * z <= MAX_VOLUME;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(),
                (double) max.getX() + 1, (double) max.getY() + 1, (double) max.getZ() + 1);
    }
}

