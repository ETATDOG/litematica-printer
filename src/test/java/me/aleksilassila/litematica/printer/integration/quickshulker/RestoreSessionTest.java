package me.aleksilassila.litematica.printer.integration.quickshulker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreSessionTest {
    @Test
    void onePendingRestoreOwnsTheContainerWait() {
        RestoreSession<String> session = new RestoreSession<>();
        assertTrue(session.schedule("glass"));
        assertFalse(session.schedule("stone"));

        session.beginContainerWait(3);
        assertTrue(session.isWaitingForContainer());
        assertFalse(session.tickContainerTimeout());
        assertFalse(session.tickContainerTimeout());
        assertTrue(session.tickContainerTimeout());

        session.stopContainerWait();
        assertFalse(session.isWaitingForContainer());
        assertEquals("glass", session.pending());
    }

    @Test
    void clearingPendingReturnsTheOwnedTransaction() {
        RestoreSession<Object> session = new RestoreSession<>();
        Object transaction = new Object();
        session.schedule(transaction);
        session.beginContainerWait(40);

        assertSame(transaction, session.clearPending());
        assertNull(session.pending());
        assertFalse(session.isWaitingForContainer());
    }

    @Test
    void pressureRecoveryUsesHysteresis() {
        RestoreSession<String> session = new RestoreSession<>();
        session.updatePressureRecovery(4, 4, 8);
        assertTrue(session.isPressureRecoveryActive());
        session.updatePressureRecovery(6, 4, 8);
        assertTrue(session.isPressureRecoveryActive());
        session.updatePressureRecovery(8, 4, 8);
        assertFalse(session.isPressureRecoveryActive());
    }

    @Test
    void worldTimeRollbackNormalizesActivityClock() {
        RestoreSession<String> session = new RestoreSession<>();
        session.markActivity(500);
        session.normalizeActivity(20);
        assertEquals(20, session.lastActivityTick());

        session.reset();
        assertEquals(Long.MIN_VALUE, session.lastActivityTick());
        assertFalse(session.hasPending());
    }
}
