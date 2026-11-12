package me.aleksilassila.litematica.printer.integration.bundle;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BundleExtractionEngineTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                //#if MC >= 260300
                //$$ .build(net.minecraft.data.registries.VanillaRegistries.createWorldLookup()).forEach(p -> p.apply());
                //#else
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach(p -> p.apply());
                //#endif
    }

    private static void fillInventory(SimulatedBundleMenuPort port, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            port.set(slot, new ItemStack(Items.COBBLESTONE));
        }
    }

    @Test
    void selectSequenceExtractsTheSelectedEntry() {
        SimulatedBundleMenuPort port = new SimulatedBundleMenuPort(41);
        port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.STONE, 10), new ItemStack(Items.DIRT, 5)));
        BundleExtractionEngine engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.AUTO);
        engine.start(new BundleExtractionEngine.Job(9, new ItemStack(Items.DIRT, 5)));

        assertEquals(BundleExtractionEngine.Outcome.DONE, SimulatedBundleMenuPort.run(engine, 50));

        // The dirt entry sits at index 1: one select packet, then extract + deposit clicks.
        assertTrue(port.selectPackets.contains("9:1"), "select packet must address entry 1: " + port.selectPackets);
        assertTrue(port.clicks.contains("1:9"), "right-click the bundle to extract: " + port.clicks);
        long deposits = port.clicks.stream().filter(c -> c.startsWith("0:") && !c.equals("0:9")).count();
        assertTrue(deposits >= 1, "deposit the cursor into an empty slot: " + port.clicks);
        assertTrue(port.carriedEmpty(), "cursor must be empty after the sequence");
        assertEquals(5, countOf(port, Items.DIRT), "dirt must be loose in the inventory");
        assertEquals(1, countOf(port, Items.BUNDLE), "the bundle itself stays in place");
    }

    @Test
    void rotateSequenceRestoresFrontOrder() {
        SimulatedBundleMenuPort port = new SimulatedBundleMenuPort(41);
        port.set(9, SimulatedBundleMenuPort.bundle(
                new ItemStack(Items.STONE, 1), new ItemStack(Items.DIRT, 1), new ItemStack(Items.IRON_INGOT, 1)));
        BundleExtractionEngine engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.ROTATE);
        engine.start(new BundleExtractionEngine.Job(9, new ItemStack(Items.DIRT, 1)));

        BundleExtractionEngine.Outcome outcome = SimulatedBundleMenuPort.run(engine, 200);
        assertEquals(BundleExtractionEngine.Outcome.DONE, outcome,
                "clicks=" + port.clicks + " selects=" + port.selectPackets);

        assertTrue(port.carriedEmpty());
        assertEquals(1, countOf(port, Items.DIRT), "the target entry must be loose");
        ItemStack bundle = port.inventoryStack(9);
        var contents = BundleExtractionEngine.contents(bundle);
        assertEquals(2, contents.size(), "front stack restored, target removed: " + contents);
        assertEquals(Items.STONE, contents.get(0).getItem(), "last-removed-first restores the front order");
        assertEquals(Items.IRON_INGOT, contents.get(1).getItem());
    }

    @Test
    void noEmptySlotFailsAndNeverLeavesTheCursorOccupied() {
        SimulatedBundleMenuPort port = new SimulatedBundleMenuPort(41);
        fillInventory(port, 0, 41);
        port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.STONE, 1), new ItemStack(Items.DIRT, 1)));
        BundleExtractionEngine engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.SELECT);
        engine.start(new BundleExtractionEngine.Job(9, new ItemStack(Items.DIRT, 1)));

        assertEquals(BundleExtractionEngine.Outcome.FAILED, SimulatedBundleMenuPort.run(engine, 200));
        assertTrue(port.carriedEmpty(), "failure recovery must clear the cursor");
        ItemStack bundle = port.inventoryStack(9);
        var contents = BundleExtractionEngine.contents(bundle);
        assertEquals(2, contents.size(), "the extracted entry goes back into the bundle");
        assertEquals(0, countOf(port, Items.DIRT), "no dirt may end up loose when no slot was free");
    }

    @Test
    void autoEscalatesToRotateWhenSelectionPacketsAreIgnored() {
        SimulatedBundleMenuPort port = new SimulatedBundleMenuPort(41);
        port.ignoreSelectionPackets = true;
        // Two fronts ahead of the target: both select attempts grab a front stack and fail,
        // which is what must trigger the escalation to ROTATE.
        port.set(9, SimulatedBundleMenuPort.bundle(
                new ItemStack(Items.STONE, 1), new ItemStack(Items.COBBLESTONE, 1), new ItemStack(Items.DIRT, 1)));
        BundleExtractionEngine engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.AUTO);
        engine.start(new BundleExtractionEngine.Job(9, new ItemStack(Items.DIRT, 1)));

        assertEquals(BundleExtractionEngine.Outcome.DONE, SimulatedBundleMenuPort.run(engine, 300));
        assertEquals(BundleExtractionEngine.Strategy.ROTATE, engine.strategy(), "repeated select failures must escalate");
        assertEquals(1, countOf(port, Items.DIRT), "rotate extraction still lands the dirt");
        assertTrue(port.carriedEmpty());
    }

    @Test
    void openedScreenPausesTheSequenceWithoutFailingIt() {
        SimulatedBundleMenuPort port = new SimulatedBundleMenuPort(41);
        port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.DIRT, 1)));
        BundleExtractionEngine engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.SELECT);
        engine.start(new BundleExtractionEngine.Job(9, new ItemStack(Items.DIRT, 1)));

        assertEquals(BundleExtractionEngine.Outcome.RUNNING, engine.tick());
        port.idle = false;
        int clicksBeforePause = port.clicks.size();
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(BundleExtractionEngine.Outcome.RUNNING, engine.tick());
        }
        assertEquals(clicksBeforePause, port.clicks.size(), "no clicks while a screen owns the menu");
        port.idle = true;
        assertEquals(BundleExtractionEngine.Outcome.DONE, SimulatedBundleMenuPort.run(engine, 50));
        assertEquals(1, countOf(port, Items.DIRT));
    }

    @Test
    void resetMidSequenceClearsTheCursor() {
        SimulatedBundleMenuPort port = new SimulatedBundleMenuPort(41);
        port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.DIRT, 1)));
        BundleExtractionEngine engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.SELECT);
        engine.start(new BundleExtractionEngine.Job(9, new ItemStack(Items.DIRT, 1)));

        // Advance until the extract click has happened, then reset (e.g. coordinator timeout).
        for (int tick = 0; tick < 50 && !port.clicks.contains("1:9"); tick++) engine.tick();
        assertTrue(port.clicks.contains("1:9"), "sequence must have reached the extract click");
        engine.reset();
        assertTrue(port.carriedEmpty(), "reset must not leave the stack on the cursor");
        assertTrue(countOf(port, Items.DIRT) + countOf(port, Items.BUNDLE) >= 1, "the dirt is somewhere");
    }

    private static int countOf(SimulatedBundleMenuPort port, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < port.inventorySize(); slot++) {
            ItemStack stack = port.inventoryStack(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}