package me.aleksilassila.litematica.printer.core.runtime;

/** Component whose mutable state is scoped to one runtime epoch. */
public interface RuntimeComponent extends AutoCloseable {
    default void onEpochChanged(RuntimeEvent.EpochChanged event) {
    }

    @Override
    default void close() {
    }
}
