package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockExposureGateTest {
    @Test
    void defersOnceThenAllowsCurrentAndOneRetry() {
        BedrockExposureGate<String> gate = new BedrockExposureGate<>(1);

        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.ALLOW, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.ALLOW, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, true));
    }

    @Test
    void readOnlyProbeDoesNotAdvanceDeferralOrConsumeBypass() {
        BedrockExposureGate<String> gate = new BedrockExposureGate<>(1);

        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, false));
        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, false));
        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.ALLOW, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.ALLOW, gate.evaluate("target", true, false));
        assertEquals(BedrockExposureGate.Decision.ALLOW, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, true));
    }

    @Test
    void exposedTargetClearsPreviousState() {
        BedrockExposureGate<String> gate = new BedrockExposureGate<>(1);

        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, true));
        assertEquals(BedrockExposureGate.Decision.ALLOW, gate.evaluate("target", false, true));
        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("target", true, true));
    }

    @Test
    void clearResetsAllTargets() {
        BedrockExposureGate<String> gate = new BedrockExposureGate<>(1);
        gate.evaluate("a", true, true);
        gate.evaluate("b", true, true);
        gate.evaluate("b", true, true);

        gate.clear();

        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("a", true, true));
        assertEquals(BedrockExposureGate.Decision.DEFER, gate.evaluate("b", true, true));
    }
}
