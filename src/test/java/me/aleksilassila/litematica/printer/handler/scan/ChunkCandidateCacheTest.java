package me.aleksilassila.litematica.printer.handler.scan;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkCandidateCacheTest {
    @Test
    void unchangedChunkIsObservedOnceAcrossRepeatedPasses() {
        ChunkCandidateCache cache = new ChunkCandidateCache();
        AtomicInteger reads = new AtomicInteger();

        assertTrue(cache.getOrCompute(10, -4, () -> {
            reads.incrementAndGet();
            return true;
        }));
        assertTrue(cache.getOrCompute(10, -4, () -> {
            reads.incrementAndGet();
            return false;
        }));
        assertTrue(reads.get() == 1);

        cache.invalidate(10, -4);
        assertFalse(cache.getOrCompute(10, -4, () -> {
            reads.incrementAndGet();
            return false;
        }));
        assertTrue(reads.get() == 2);
    }
}
