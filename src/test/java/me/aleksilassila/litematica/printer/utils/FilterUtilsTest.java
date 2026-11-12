package me.aleksilassila.litematica.printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterUtilsTest {
    @Test
    void parsedFilterCacheIsBounded() {
        for (int index = 0; index < 1_000; index++) {
            assertFalse(FilterUtils.matchName("test_filter_" + index, new Object()));
        }
        assertTrue(FilterUtils.parsedFilterCacheSize() <= 512);
    }

    @Test
    void stringContainsRuleDoesNotRequireListAllocation() {
        assertTrue(FilterUtils.matchString("minecraft:glass", "glass", new String[]{"c"}));
        assertFalse(FilterUtils.matchString("minecraft:glass", "stone", new String[]{"c"}));
    }
}
