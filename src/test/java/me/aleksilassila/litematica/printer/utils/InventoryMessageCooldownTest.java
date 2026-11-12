package me.aleksilassila.litematica.printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryMessageCooldownTest {
    @Test
    void throttlesEachMessageKeyIndependently() {
        InventoryMessageCooldown cooldown = new InventoryMessageCooldown();

        assertTrue(cooldown.shouldSend("no_slots", 1_000L));
        assertFalse(cooldown.shouldSend("no_slots", 1_001L));
        assertTrue(cooldown.shouldSend("no_item", 1_001L));
        assertTrue(cooldown.shouldSend("no_slots", 6_001L));
    }

    @Test
    void resetStartsANewRuntimeEpoch() {
        InventoryMessageCooldown cooldown = new InventoryMessageCooldown();

        assertTrue(cooldown.shouldSend("no_slots", 1_000L));
        cooldown.reset();

        assertTrue(cooldown.shouldSend("no_slots", 1_001L));
    }
}
