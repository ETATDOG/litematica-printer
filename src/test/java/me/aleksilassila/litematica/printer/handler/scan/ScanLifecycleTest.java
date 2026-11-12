package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.enums.ScanState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanLifecycleTest {
    @Test
    void resetReturnsToFullScanAndClearsIdleState() {
        ScanLifecycle lifecycle = new ScanLifecycle();
        lifecycle.setState(ScanState.PARTIAL);
        lifecycle.idlePolicy().recordLazyProbe(false, false);

        lifecycle.reset();

        assertEquals(ScanState.FULL, lifecycle.state());
    }
}
