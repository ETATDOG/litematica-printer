package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockScanActivityPolicyTest {
    @Test
    void multipleRetriesKeepLatestExpiry() {
        BedrockScanActivityPolicy policy = new BedrockScanActivityPolicy();

        policy.recordRetry(100L, 12);
        policy.recordRetry(105L, 4);

        assertTrue(policy.hasPendingWork(111L, 0));
        assertFalse(policy.hasPendingWork(112L, 0));

        policy.recordRetry(108L, 12);
        assertTrue(policy.hasPendingWork(119L, 0));
        assertFalse(policy.hasPendingWork(120L, 0));
    }

    @Test
    void activeTargetsAndRetryWindowAreReportedWithoutPositionTracking() {
        BedrockScanActivityPolicy policy = new BedrockScanActivityPolicy();

        assertTrue(policy.hasPendingWork(20L, 3));
        assertEquals(3, policy.getPendingWorkCount(20L, 3));

        policy.recordRetry(20L, 6);
        assertTrue(policy.hasPendingWork(25L, 0));
        assertEquals(1, policy.getPendingWorkCount(25L, 0));
        assertFalse(policy.hasPendingWork(26L, 0));
        assertEquals(0, policy.getPendingWorkCount(26L, 0));
    }

    @Test
    void resetClearsRetryWindowCompletely() {
        BedrockScanActivityPolicy policy = new BedrockScanActivityPolicy();
        policy.recordRetry(40L, 12);

        policy.reset();

        assertFalse(policy.hasPendingWork(40L, 0));
        assertEquals(0, policy.getPendingWorkCount(40L, 0));
    }
}
