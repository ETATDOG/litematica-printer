package me.aleksilassila.litematica.printer.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeEventBusTest {
    @Test
    void epochIsMonotonicAndUnsubscribedListenersStopReceivingEvents() throws Exception {
        RuntimeEventBus bus = new RuntimeEventBus();
        List<RuntimeEvent> received = new ArrayList<>();
        AutoCloseable subscription = bus.subscribe(received::add);

        RuntimeEpoch first = RuntimeEpoch.INITIAL.next();
        bus.publish(new RuntimeEvent.EpochChanged(RuntimeEpoch.INITIAL, first, "join"));
        subscription.close();
        bus.publish(new RuntimeEvent.EpochChanged(first, first.next(), "disconnect"));

        assertEquals(1, received.size());
        RuntimeEvent.EpochChanged event = (RuntimeEvent.EpochChanged) received.get(0);
        assertEquals(0L, event.previous().value());
        assertEquals(1L, event.current().value());
        assertEquals("join", event.reason());
    }

    @Test
    void clearRemovesEveryListener() {
        RuntimeEventBus bus = new RuntimeEventBus();
        List<RuntimeEvent> received = new ArrayList<>();
        bus.subscribe(received::add);
        bus.clear();
        bus.publish(new RuntimeEvent.EpochChanged(
                RuntimeEpoch.INITIAL, RuntimeEpoch.INITIAL.next(), "ignored"));
        assertEquals(List.of(), received);
    }
}
