package me.aleksilassila.litematica.printer.integration.quickshulker;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShulkerSelectionPolicyTest {
    @Test
    void everyDecisionCombinationHasStableUniquePriority() {
        Set<Integer> scores = new HashSet<>();
        for (boolean recorded : booleans()) {
            for (boolean sameType : booleans()) {
                for (boolean snapshot : booleans()) {
                    for (boolean originalFits : booleans()) {
                        for (boolean capacity : booleans()) {
                            int score = ShulkerSelectionPolicy.score(
                                    recorded, sameType, snapshot, originalFits, capacity
                            );
                            assertTrue(score >= 0 && score <= 17);
                            scores.add(score);
                        }
                    }
                }
            }
        }
        assertEquals(18, scores.size());
    }

    @Test
    void exactOriginalBoxWinsOverFallbackCapacity() {
        int exact = ShulkerSelectionPolicy.score(true, true, true, true, true);
        int matchingSnapshot = ShulkerSelectionPolicy.score(false, true, true, true, true);
        int genericCapacity = ShulkerSelectionPolicy.score(false, false, false, false, true);

        assertTrue(exact < matchingSnapshot);
        assertTrue(matchingSnapshot < genericCapacity);
    }

    private static boolean[] booleans() {
        return new boolean[]{false, true};
    }
}
