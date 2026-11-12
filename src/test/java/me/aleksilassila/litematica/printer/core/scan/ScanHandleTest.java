package me.aleksilassila.litematica.printer.core.scan;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeEpoch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanHandleTest {
    @Test
    void acceptsOnlyItsActiveGeneration() {
        ScanGeneration active = new ScanGeneration(new RuntimeEpoch(4L), 2L, 9L, 12L);
        ScanHandle handle = new ScanHandle(active);

        assertTrue(handle.accepts(active));
        assertFalse(handle.accepts(new ScanGeneration(new RuntimeEpoch(5L), 2L, 9L, 12L)));
        assertFalse(handle.accepts(new ScanGeneration(new RuntimeEpoch(4L), 3L, 9L, 12L)));

        handle.close();
        assertFalse(handle.accepts(active));
        assertTrue(handle.isCancelled());
    }
}
