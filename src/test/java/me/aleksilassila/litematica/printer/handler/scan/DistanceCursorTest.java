package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceCursorTest {
    @Test
    void boxCursorVisitsEveryBlockOnceInRadialOrder() {
        BoxDistanceCursor cursor = new BoxDistanceCursor(new PrinterBox(-2, -1, -2, 2, 1, 2), 0, 0, 0);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        Set<BlockPos> visited = new HashSet<>();
        int previousBand = -1;

        while (cursor.next(mutable)) {
            BlockPos pos = mutable.immutable();
            int band = radialBand(pos, 0, 0, 0);
            assertTrue(band >= previousBand);
            assertTrue(visited.add(pos));
            previousBand = band;
        }

        assertEquals(75, visited.size());
        assertFalse(cursor.next(mutable));
    }

    @Test
    void mergedCursorDeduplicatesOverlappingSelectionBoxes() {
        PrinterBox first = new PrinterBox(0, 0, 0, 2, 0, 0);
        PrinterBox second = new PrinterBox(1, 0, 0, 3, 0, 0);
        PlayerDistanceCursor cursor = new PlayerDistanceCursor(List.of(first, second), 2, 0, 0);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        Set<BlockPos> visited = new HashSet<>();
        int previousBand = -1;

        while (cursor.next(mutable)) {
            BlockPos pos = mutable.immutable();
            int band = radialBand(pos, 2, 0, 0);
            assertTrue(band >= previousBand);
            assertTrue(visited.add(pos));
            previousBand = band;
        }

        assertEquals(Set.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0)
        ), visited);
        assertTrue(cursor.isComplete());
    }

    @Test
    void recenteringPreservesCheckedPositionsAndPrioritizesTheNewCenter() {
        PrinterBox box = new PrinterBox(-8, 0, -8, 8, 0, 8);
        PlayerDistanceCursor original = new PlayerDistanceCursor(List.of(box), 0, 0, 0);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        Set<BlockPos> checked = new HashSet<>();
        for (int index = 0; index < 25; index++) {
            assertTrue(original.next(mutable));
            assertTrue(checked.add(mutable.immutable()));
        }

        PlayerDistanceCursor recentered = new PlayerDistanceCursor(List.of(box), 8, 0, 8);
        BlockPos firstUnchecked = null;
        while (recentered.next(mutable)) {
            if (!checked.contains(mutable)) {
                firstUnchecked = mutable.immutable();
                break;
            }
        }

        assertEquals(new BlockPos(8, 0, 8), firstUnchecked);
        assertFalse(checked.contains(firstUnchecked));
    }

    @Test
    void largeThinSelectionKeepsLinearProbeCost() {
        PrinterBox plane = new PrinterBox(-64, 0, -64, 64, 0, 64);
        BoxDistanceCursor cursor = new BoxDistanceCursor(plane, 0, 0, 0);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int count = 0;

        while (cursor.next(mutable)) {
            count++;
        }

        assertEquals(129 * 129, count);
        assertTrue(cursor.probeCount() <= count * 3L,
                "thin selections must not probe a surrounding 3D volume");
    }

    @Test
    void distanceBandCapSkipsUnreachableAabbCorners() {
        BoxDistanceCursor cursor = new BoxDistanceCursor(
                new PrinterBox(-64, -64, -64, 64, 64, 64),
                0,
                0,
                0,
                64
        );
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int count = 0;

        while (cursor.next(mutable)) {
            assertTrue(squaredDistance(mutable, 0, 0, 0) <= 64L * 64L);
            count++;
        }

        assertTrue(count > 1_000_000);
        assertTrue(count < 1_200_000);

        BoxDistanceCursor stabilityCursor = new BoxDistanceCursor(
                new PrinterBox(-64, -64, -64, 64, 64, 64),
                0,
                0,
                0,
                64
        );
        int half = count / 2;
        int emitted = 0;
        while (emitted < half && stabilityCursor.next(mutable)) {
            emitted++;
        }
        long firstHalfProbes = stabilityCursor.probeCount();
        while (stabilityCursor.next(mutable)) {
            emitted++;
        }
        long secondHalfProbes = stabilityCursor.probeCount() - firstHalfProbes;
        assertEquals(count, emitted);
        assertTrue(secondHalfProbes <= firstHalfProbes * 2L,
                "outer scan work must stay proportional instead of degrading with radius");
    }

    @Test
    void randomizedBoxesMatchTheExpectedCappedVolume() {
        Random random = new Random(0x48414E41L);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int sample = 0; sample < 100; sample++) {
            int minX = random.nextInt(11) - 5;
            int minY = random.nextInt(11) - 5;
            int minZ = random.nextInt(11) - 5;
            int maxX = minX + random.nextInt(6);
            int maxY = minY + random.nextInt(6);
            int maxZ = minZ + random.nextInt(6);
            int centerX = random.nextInt(11) - 5;
            int centerY = random.nextInt(11) - 5;
            int centerZ = random.nextInt(11) - 5;
            int cap = random.nextInt(10);
            PrinterBox box = new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ);
            BoxDistanceCursor cursor = new BoxDistanceCursor(box, centerX, centerY, centerZ, cap);
            Set<BlockPos> actual = new HashSet<>();
            int previousBand = -1;

            while (cursor.next(mutable)) {
                BlockPos pos = mutable.immutable();
                int band = radialBand(pos, centerX, centerY, centerZ);
                assertTrue(band >= previousBand);
                assertTrue(actual.add(pos));
                previousBand = band;
            }

            Set<BlockPos> expected = new HashSet<>();
            for (BlockPos pos : box) {
                if (radialBand(pos, centerX, centerY, centerZ) <= cap) {
                    expected.add(pos);
                }
            }
            assertEquals(expected, actual);
        }
    }

    private static long squaredDistance(BlockPos pos, int x, int y, int z) {
        long dx = pos.getX() - (long) x;
        long dy = pos.getY() - (long) y;
        long dz = pos.getZ() - (long) z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int radialBand(BlockPos pos, int x, int y, int z) {
        return (int) Math.ceil(Math.sqrt(squaredDistance(pos, x, y, z)));
    }
}
