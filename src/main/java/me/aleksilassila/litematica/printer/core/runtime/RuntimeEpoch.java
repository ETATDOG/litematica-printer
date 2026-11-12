package me.aleksilassila.litematica.printer.core.runtime;

/**
 * Monotonic identity for one connected client runtime.
 *
 * <p>Work created for an older epoch is stale by definition and must never reach the live
 * runtime.</p>
 */
public record RuntimeEpoch(long value) {
    public static final RuntimeEpoch INITIAL = new RuntimeEpoch(0L);

    public RuntimeEpoch next() {
        return new RuntimeEpoch(this.value + 1L);
    }
}
