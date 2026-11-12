package me.aleksilassila.litematica.printer.interaction;

import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** Minecraft/Fabric boundary for the vanilla axe strippable registry. */
public interface StrippableBlockPort {
    @Nullable Block sourceFor(Block strippedBlock);
}
