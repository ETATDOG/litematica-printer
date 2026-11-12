package me.aleksilassila.litematica.printer.handler;

import me.aleksilassila.litematica.printer.handler.handlers.PrintHandler;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;

/**
 * Compatibility ABI for TakeItOut versions that still read the printer target
 * through the pre-runtime class name.  The class deliberately contains no
 * tick or lifecycle logic; PrinterRuntime remains the only tick entry point.
 */
@Deprecated(forRemoval = false)
public final class ClientPlayerTickManager {
    public static final PrintHandler PRINT = RuntimeAccess.get().modules().print();

    private ClientPlayerTickManager() {
    }
}
