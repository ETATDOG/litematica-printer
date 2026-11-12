package me.aleksilassila.litematica.printer.handler.handlers.print;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterStageResolverTest {
    @Test
    void waterloggedTargetNeverFallsThroughToDryPlacement() {
        assertEquals(WaterTaskStage.PLACE_FINAL_BLOCK, resolve(
                WaterTaskStage.WAIT_WATER_CONFIRM,
                WaterStageResolver.Observation.WATER_READY,
                true,
                false,
                true
        ));
    }

    @Test
    void missingWaterUpdateRetriesIceWithoutPlayerMovement() {
        assertEquals(WaterTaskStage.WAIT_WATER_CONFIRM, resolve(
                WaterTaskStage.WAIT_WATER_CONFIRM,
                WaterStageResolver.Observation.REPLACEABLE_WITH_SUPPORT,
                true,
                false,
                true
        ));
        assertEquals(WaterTaskStage.PLACE_ICE, resolve(
                WaterTaskStage.WAIT_WATER_CONFIRM,
                WaterStageResolver.Observation.REPLACEABLE_WITH_SUPPORT,
                true,
                true,
                true
        ));
    }

    @Test
    void confirmedIceImmediatelyAdvancesToBreakStage() {
        assertEquals(WaterTaskStage.BREAK_ICE, resolve(
                WaterTaskStage.WAIT_ICE_CONFIRM,
                WaterStageResolver.Observation.ICE,
                true,
                false,
                true
        ));
        assertEquals(WaterTaskStage.WAIT_ICE_CONFIRM, resolve(
                WaterTaskStage.WAIT_ICE_CONFIRM,
                WaterStageResolver.Observation.REPLACEABLE_WITH_SUPPORT,
                true,
                false,
                true
        ));
    }

    @Test
    void removeConfirmationKeepsTargetReservedUntilTimeout() {
        assertEquals(WaterTaskStage.WAIT_REMOVE_CONFIRM, resolve(
                WaterTaskStage.WAIT_REMOVE_CONFIRM,
                WaterStageResolver.Observation.BLOCKED,
                true,
                false,
                true
        ));
        assertEquals(WaterTaskStage.REMOVE_EXISTING, resolve(
                WaterTaskStage.WAIT_REMOVE_CONFIRM,
                WaterStageResolver.Observation.BLOCKED,
                true,
                true,
                true
        ));
    }

    @Test
    void retryWaitHasExplicitDeadline() {
        assertEquals(WaterTaskStage.RETRY_WAIT, resolve(
                WaterTaskStage.RETRY_WAIT,
                WaterStageResolver.Observation.REPLACEABLE_WITH_SUPPORT,
                true,
                false,
                false
        ));
        assertEquals(WaterTaskStage.PLACE_ICE, resolve(
                WaterTaskStage.RETRY_WAIT,
                WaterStageResolver.Observation.REPLACEABLE_WITH_SUPPORT,
                true,
                false,
                true
        ));
    }

    private static WaterTaskStage resolve(
            WaterTaskStage stage,
            WaterStageResolver.Observation observation,
            boolean finalBlockRequired,
            boolean stalled,
            boolean retryReady
    ) {
        return WaterStageResolver.resolve(
                stage,
                observation,
                finalBlockRequired,
                stalled,
                retryReady
        );
    }
}
