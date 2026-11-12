package me.aleksilassila.litematica.printer.printer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionScreenSessionsTest {
    @Test
    void signSessionIsBoundToItsBlockAndCanOnlyBeConsumedOnce() {
        InteractionScreenSessions sessions = new InteractionScreenSessions();
        sessions.armPrintSignEdit(12L, 100L);
        sessions.confirmPrintSignEditSent(12L, 110L);

        assertFalse(sessions.consumePrintSignEdit(13L, 120L));
        assertTrue(sessions.consumePrintSignEdit(12L, 120L));
        assertFalse(sessions.consumePrintSignEdit(12L, 120L));
    }

    @Test
    void expiredSignSessionAndResetCannotSuppressManualScreens() {
        InteractionScreenSessions sessions = new InteractionScreenSessions();
        sessions.armPrintSignEdit(4L, 10L);
        assertFalse(sessions.consumePrintSignEdit(4L, 30_000_000_011L));

        sessions.armTaskAnvilScreen(20L);
        sessions.reset();
        assertFalse(sessions.consumeTaskAnvilScreenSuppression(21L));
    }

    @Test
    void manualAnvilSessionWinsOverPrinterSuppression() {
        InteractionScreenSessions sessions = new InteractionScreenSessions();
        sessions.armTaskAnvilScreen(100L);
        sessions.prioritizeManualAnvilScreen(110L);

        assertTrue(sessions.consumeManualAnvilScreenAllowance(120L));
        assertFalse(sessions.consumeTaskAnvilScreenSuppression(120L));
        assertFalse(sessions.consumeManualAnvilScreenAllowance(120L));
    }

    @Test
    void anvilSuppressionIsBoundedAndExpires() {
        InteractionScreenSessions sessions = new InteractionScreenSessions();
        sessions.armTaskAnvilScreen(100L);
        assertTrue(sessions.consumeTaskAnvilScreenSuppression(101L));
        assertFalse(sessions.consumeTaskAnvilScreenSuppression(101L));

        sessions.armTaskAnvilScreen(200L);
        assertFalse(sessions.consumeTaskAnvilScreenSuppression(5_000_000_201L));
    }
}
