package me.aleksilassila.litematica.printer.handler.scan;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanBudgetTest {
    @Test
    void oneOwnerCannotConsumeAnotherOwnersTimeSlice() {
        AtomicLong now = new AtomicLong();
        long configuredNanos = 2_000_000L;
        ScanBudget budget = new ScanBudget(now::get, () -> configuredNanos);
        ScanMetricsAccumulator metrics = new ScanMetricsAccumulator();

        budget.beginTick(1L);
        now.set(configuredNanos);
        assertTrue(budget.isExceeded("mine", 0L));

        now.set(0L);
        budget.record("mine", metrics, 0L);
        assertFalse(budget.isExceeded("fluid", 0L));
    }

    @Test
    void budgetResetsAtTheStartOfTheNextTick() {
        AtomicLong now = new AtomicLong();
        long configuredNanos = 2_000_000L;
        ScanBudget budget = new ScanBudget(now::get, () -> configuredNanos);

        budget.beginTick(1L);
        now.set(configuredNanos);
        assertTrue(budget.isExceeded("print", 0L));

        budget.beginTick(2L);
        now.set(0L);
        assertFalse(budget.isExceeded("print", 0L));
    }

    @Test
    void repeatedCandidateLookupsShareOneOwnerBudget() {
        AtomicLong now = new AtomicLong();
        long configuredNanos = 2_000_000L;
        ScanBudget budget = new ScanBudget(now::get, () -> configuredNanos);
        ScanMetricsAccumulator metrics = new ScanMetricsAccumulator();

        budget.beginTick(1L);
        now.set(800_000L);
        budget.record("print", metrics, 0L);

        long secondLookupStart = now.get();
        now.set(1_600_000L);
        budget.record("print", metrics, secondLookupStart);

        long thirdLookupStart = now.get();
        now.set(2_050_000L);
        assertTrue(budget.isExceeded("print", thirdLookupStart));
        assertFalse(budget.isExceeded("mine", thirdLookupStart));
    }
}
