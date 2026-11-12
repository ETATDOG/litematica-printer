package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockCandidateConflictIndexTest {
    @Test
    void rejectsStructuralAndPowerOverlapWithoutNestedPositionWalk() {
        BedrockCandidateConflictIndex index = new BedrockCandidateConflictIndex();
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos shared = new BlockPos(1, 0, 0);

        assertTrue(index.tryReserve(plan(first, List.of(first, shared), List.of())));
        assertFalse(index.tryReserve(plan(
                new BlockPos(3, 0, 0),
                List.of(new BlockPos(3, 0, 0)),
                List.of(shared)
        )));
    }

    @Test
    void acceptsIndependentFootprints() {
        BedrockCandidateConflictIndex index = new BedrockCandidateConflictIndex();
        assertTrue(index.tryReserve(plan(
                new BlockPos(0, 0, 0),
                List.of(new BlockPos(0, 0, 0)),
                List.of()
        )));
        assertTrue(index.tryReserve(plan(
                new BlockPos(10, 0, 0),
                List.of(new BlockPos(10, 0, 0)),
                List.of()
        )));
    }

    private static BedrockCandidatePlan plan(
            BlockPos pos,
            List<BlockPos> structural,
            List<BlockPos> power
    ) {
        return new BedrockCandidatePlan(
                pos,
                null,
                null,
                null,
                structural,
                power,
                0,
                0,
                new PrinterBox(pos).expand(4),
                0L
        );
    }
}
