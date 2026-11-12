package me.aleksilassila.litematica.printer.core.scan;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeEpoch;

/**
 * Immutable identity attached to one scan traversal.
 *
 * <p>It deliberately contains no Minecraft objects so it is safe to carry through the scan
 * worker. A result is usable only while all four revisions still identify the active request.</p>
 */
public record ScanGeneration(
        RuntimeEpoch epoch,
        long selectionRevision,
        long snapshotRevision,
        long sequence
) {
    public ScanGeneration {
        if (epoch == null) throw new IllegalArgumentException("epoch must not be null");
        if (selectionRevision < 0L) throw new IllegalArgumentException("selectionRevision must not be negative");
        if (snapshotRevision < 0L) throw new IllegalArgumentException("snapshotRevision must not be negative");
        if (sequence < 0L) throw new IllegalArgumentException("sequence must not be negative");
    }
}
