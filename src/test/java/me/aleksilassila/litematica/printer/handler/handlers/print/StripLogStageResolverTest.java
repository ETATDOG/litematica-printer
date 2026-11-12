package me.aleksilassila.litematica.printer.handler.handlers.print;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StripLogStageResolverTest {
    @Test
    void placedSourceLogAdvancesToStripWithoutASelectionRescan() {
        assertEquals(StripLogTaskStage.STRIP_LOG, StripLogStageResolver.resolve(
                StripLogTaskStage.WAIT_LOG_CONFIRM,
                StripLogStageResolver.Observation.SOURCE_LOG,
                false,
                true
        ));
    }

    @Test
    void missedConfirmationRetriesOnlyTheCurrentStage() {
        assertEquals(StripLogTaskStage.PLACE_LOG, StripLogStageResolver.resolve(
                StripLogTaskStage.WAIT_LOG_CONFIRM,
                StripLogStageResolver.Observation.REPLACEABLE,
                true,
                true
        ));
        assertEquals(StripLogTaskStage.STRIP_LOG, StripLogStageResolver.resolve(
                StripLogTaskStage.WAIT_STRIP_CONFIRM,
                StripLogStageResolver.Observation.SOURCE_LOG,
                true,
                true
        ));
    }

    @Test
    void finalStateCompletesFromEveryWaitingStage() {
        for (StripLogTaskStage stage : StripLogTaskStage.values()) {
            assertEquals(StripLogTaskStage.COMPLETE, StripLogStageResolver.resolve(
                    stage,
                    StripLogStageResolver.Observation.COMPLETE,
                    false,
                    false
            ));
        }
    }
}
