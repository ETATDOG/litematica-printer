package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtyRegionTrackerTest {
    private final DirtyRegionTracker tracker = new DirtyRegionTracker();

    @AfterEach
    void resetTracker() {
        this.tracker.clear();
    }

    @Test
    void retainsAtMostConfiguredRegionLimit() {
        this.tracker.clear();
        for (int index = 0; index < 10_000; index++) {
            this.tracker.markDirty(new BlockPos(index * 16 + 1, 64, 1));
        }
        assertEquals(8_192, this.tracker.retainedRegionCount());
    }

    @Test
    void historyGapRequestsBoundedFullRescan() {
        this.tracker.clear();
        long staleVersion = this.tracker.currentVersion();
        for (int index = 0; index < 10_000; index++) {
            this.tracker.markDirty(new BlockPos(index * 16 + 1, 64, 1));
        }
        PrinterBox bounds = new PrinterBox(0, 0, 0, 15, 15, 15);
        DirtyRegionTracker.DirtySnapshot snapshot = this.tracker.snapshotAfter(staleVersion, bounds);
        assertEquals(1, snapshot.boxes().size());
        assertSame(bounds, snapshot.boxes().get(0));
        assertTrue(snapshot.version() > staleVersion);
    }
}
