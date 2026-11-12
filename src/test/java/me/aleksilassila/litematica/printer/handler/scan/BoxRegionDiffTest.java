package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxRegionDiffTest {
    @Test
    void movingBoxProducesOnlyNewCoordinatesWithoutDuplicates() {
        PrinterBox previous = new PrinterBox(0, 0, 0, 4, 4, 4);
        PrinterBox current = new PrinterBox(1, 1, -1, 5, 5, 3);

        BoxRegionDiff.Result result = BoxRegionDiff.newlyExposed(previous, current);
        Set<BlockPos> actual = new HashSet<>();
        result.boxes().forEach(box -> box.forEach(pos -> assertTrue(actual.add(pos))));

        Set<BlockPos> expected = new HashSet<>();
        current.forEach(pos -> {
            if (!previous.contains(pos)) {
                expected.add(pos);
            }
        });
        assertFalse(result.requiresFullScan());
        assertEquals(expected, actual);
    }

    @Test
    void identicalBoxHasNoNewRegion() {
        PrinterBox box = new PrinterBox(-2, 3, 4, 6, 7, 8);
        BoxRegionDiff.Result result = BoxRegionDiff.newlyExposed(box, box);

        assertFalse(result.requiresFullScan());
        assertTrue(result.boxes().isEmpty());
    }

    @Test
    void disjointBoxesRequestFullScan() {
        BoxRegionDiff.Result result = BoxRegionDiff.newlyExposed(
                new PrinterBox(0, 0, 0, 2, 2, 2),
                new PrinterBox(8, 0, 0, 10, 2, 2)
        );

        assertTrue(result.requiresFullScan());
        assertTrue(result.boxes().isEmpty());
    }
}
