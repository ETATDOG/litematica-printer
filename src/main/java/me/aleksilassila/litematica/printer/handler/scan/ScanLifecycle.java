package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.enums.ScanState;

/**
 * Owns the lifecycle state shared by a module's scan pass.
 *
 * <p>Candidate production stays in {@link ScanEngine}; this class only
 * describes whether a module is doing a full, partial, or lazy pass and keeps
 * the idle policy beside that state.</p>
 */
public final class ScanLifecycle {
    private final ScanIdlePolicy idlePolicy = new ScanIdlePolicy();
    private ScanState state = ScanState.FULL;

    public ScanState state() {
        return this.state;
    }

    public void setState(ScanState state) {
        this.state = state;
    }

    public ScanIdlePolicy idlePolicy() {
        return this.idlePolicy;
    }

    public void reset() {
        this.state = ScanState.FULL;
        this.idlePolicy.reset();
    }
}
