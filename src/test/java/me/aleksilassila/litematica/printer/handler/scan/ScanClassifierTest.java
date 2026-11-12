package me.aleksilassila.litematica.printer.handler.scan;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanClassifierTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void printSkipsMatchingStateButKeepsNonAirSchematicTarget() {
        assertEquals(0, ScanClassifier.flags(
                ScanIntent.PRINT,
                Blocks.STONE.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                false,
                false
        ));
        assertEquals(ScanFlags.SCHEMATIC_SAMPLED | ScanFlags.SCHEMATIC_NON_AIR,
                ScanClassifier.flags(
                        ScanIntent.PRINT,
                        Blocks.AIR.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(),
                        false,
                        false
                ));
    }

    @Test
    void fillRequiresSupportAndMineAndFluidOnlyUseTheirWorldState() {
        assertEquals(0, ScanClassifier.flags(
                ScanIntent.FILL,
                Blocks.AIR.defaultBlockState(),
                null,
                false,
                false
        ));
        assertEquals(ScanFlags.BASE_FILL_TARGET, ScanClassifier.flags(
                ScanIntent.FILL,
                Blocks.AIR.defaultBlockState(),
                null,
                true,
                false
        ));
        assertEquals(ScanFlags.WORLD_NON_AIR,
                ScanClassifier.flags(ScanIntent.MINE, Blocks.STONE.defaultBlockState(), null, false, false));
        assertEquals(ScanFlags.WORLD_NON_AIR | ScanFlags.WORLD_FLUID,
                ScanClassifier.flags(ScanIntent.FLUID, Blocks.WATER.defaultBlockState(), null, false, false));
    }
}
