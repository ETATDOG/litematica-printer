package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockRunStatsTest {
    @Test
    void successRateCountsOnlyResolvedTargets() {
        BedrockRunStats stats = new BedrockRunStats();
        stats.confirmedSuccesses = 9;
        stats.failedTargets = 1;
        stats.submittedTargets = 20;

        assertEquals(0.9D, stats.successRate());
    }

    @Test
    void tickResetDoesNotEraseSessionTotals() {
        BedrockRunStats stats = new BedrockRunStats();
        stats.acceptedThisTick = 3;
        stats.rejectedThisTick = 2;
        stats.confirmedSuccesses = 5;

        stats.beginTick();

        assertEquals(0, stats.acceptedThisTick);
        assertEquals(0, stats.rejectedThisTick);
        assertEquals(5, stats.confirmedSuccesses);
    }

    @Test
    void epochResetClearsAllCountersAndReason() {
        BedrockRunStats stats = new BedrockRunStats();
        stats.acceptedThisTick = 1;
        stats.failedTargets = 2;
        stats.lastReason = "failed";

        stats.reset();

        assertEquals(0, stats.acceptedThisTick);
        assertEquals(0, stats.failedTargets);
        assertEquals("idle", stats.lastReason);
    }
}
