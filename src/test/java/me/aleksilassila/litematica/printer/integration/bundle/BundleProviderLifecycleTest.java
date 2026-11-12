package me.aleksilassila.litematica.printer.integration.bundle;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEpoch;
import me.aleksilassila.litematica.printer.integration.inventory.InventoryProvider;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialRequest;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialRequestCoordinator;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialReservation;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.action.ActionBroker;
import me.aleksilassila.litematica.printer.printer.action.ActionPort;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.InventoryMenu;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the real provider and coordinator against a simulated bundle menu. */
class BundleProviderLifecycleTest {
    private static Boolean oldBundleConfig;

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                //#if MC >= 260300
                //$$ .build(net.minecraft.data.registries.VanillaRegistries.createWorldLookup()).forEach(p -> p.apply());
                //#else
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach(p -> p.apply());
                //#endif
        oldBundleConfig = Configs.Core.USE_BUNDLE_MATERIALS.getBooleanValue();
    }

    @AfterAll
    static void restoreConfig() {
        if (oldBundleConfig != null) {
            Configs.Core.USE_BUNDLE_MATERIALS.setBooleanValue(oldBundleConfig);
        }
    }

    static <T> T allocate(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    static void set(Object instance, Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    static class Player extends LocalPlayer {
        Inventory inventory;
        Player() { super(null, null, null, null, null, null, false, null, null); }
        @Override public Inventory getInventory() { return this.inventory; }
    }

    static class RecordingProvider implements InventoryProvider {
        int calls;
        @Override public String id() { return "next"; }
        @Override public MaterialReservation request(MaterialRequest request) {
            this.calls++;
            return new MaterialReservation(request.token(), MaterialReservation.State.UNAVAILABLE);
        }
    }

    static class Fixture implements AutoCloseable {
        Object oldClient;
        Object oldRuntime;
        Minecraft client;
        Player player;
        AtomicBoolean leased = new AtomicBoolean();
        SimulatedBundleMenuPort port;
        ActionBroker broker;
        BundleProvider provider;
        RecordingProvider next = new RecordingProvider();
        MaterialRequestCoordinator coordinator;

        Fixture() throws Exception {
            this.oldClient = get(null, Minecraft.class, "instance");
            this.oldRuntime = get(null, RuntimeAccess.class, "current");
            this.client = allocate(Minecraft.class);
            this.player = allocate(Player.class);
            this.player.inventory = new Inventory(this.player, new net.minecraft.world.entity.EntityEquipment());
            set(this.player, net.minecraft.world.entity.player.Player.class, "inventoryMenu",
                    new InventoryMenu(this.player.inventory, true, this.player));
            this.player.containerMenu = this.player.inventoryMenu;
            set(this.player, net.minecraft.world.entity.player.Player.class, "abilities", new Abilities());
            this.client.player = this.player;
            set(null, Minecraft.class, "instance", this.client);
            // Inventory-backed: deposits must land in the real player inventory that the
            // provider's availability check reads.
            this.port = new SimulatedBundleMenuPort(this.player.inventory);

            ActionPort port = (ActionPort) java.lang.reflect.Proxy.newProxyInstance(
                    ActionPort.class.getClassLoader(),
                    new Class<?>[]{ActionPort.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("tryAcquire")) { this.leased.set(true); return true; }
                        if (method.getName().equals("releaseOwner")) { this.leased.set(false); return null; }
                        return method.getReturnType() == boolean.class ? false : null;
                    });
            PrinterRuntime runtime = allocate(PrinterRuntime.class);
            set(runtime, PrinterRuntime.class, "epoch", RuntimeEpoch.INITIAL);
            RuntimeAccess.install(runtime);
            this.broker = new ActionBroker(runtime, new ActionManager());
            this.provider = new BundleProvider(this.broker, this.port);
            this.coordinator = new MaterialRequestCoordinator(List.of(this.provider, this.next));
        }

        static Object get(Object instance, Class<?> type, String name) throws Exception {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        }

        MaterialReservation request(Item item, int minimumCount) {
            MaterialReservation result = this.coordinator.request(item, minimumCount, MaterialRequest.Source.PRINT);
            for (int tick = 0; tick < 300 && result.state() == MaterialReservation.State.PENDING; tick++) {
                this.coordinator.tick();
                result = this.coordinator.request(item, minimumCount, MaterialRequest.Source.PRINT);
            }
            return result;
        }

        @Override
        public void close() throws Exception {
            set(null, Minecraft.class, "instance", this.oldClient);
            set(null, RuntimeAccess.class, "current", this.oldRuntime);
        }
    }

    @Test
    void extractionMakesTheRequestedMaterialAvailable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.STONE, 16)));

            MaterialReservation result = f.request(Items.STONE, 1);

            assertEquals(MaterialReservation.State.AVAILABLE, result.state(), "bundle supply must satisfy the request");
            assertTrue(f.leased.get() == false, "lease must be released once available");
            assertEquals(16, looseCount(f, Items.STONE), "the extracted stack must sit loose in the inventory");
            var contents = BundleExtractionEngine.contents(f.player.getInventory().getItem(9));
            assertTrue(contents == null || contents.isEmpty(), "the bundle must be drained");
            assertEquals(0, f.next.calls, "the next provider must not run");
        }
    }

    @Test
    void disabledConfigFallsThroughToTheNextProvider() throws Exception {
        try (Fixture f = new Fixture()) {
            f.port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.STONE, 16)));
            Configs.Core.USE_BUNDLE_MATERIALS.setBooleanValue(false);

            MaterialReservation result = f.request(Items.STONE, 1);

            assertEquals(MaterialReservation.State.UNAVAILABLE, result.state());
            assertEquals(0, f.port.clicks.size(), "a disabled provider must not click");
            assertEquals(1, f.next.calls, "the coordinator must walk to the next provider");
        }
    }

    @Test
    void noBundleMeansUnavailableWithoutClicks() throws Exception {
        try (Fixture f = new Fixture()) {
            MaterialReservation result = f.request(Items.STONE, 1);

            assertEquals(MaterialReservation.State.UNAVAILABLE, result.state());
            assertEquals(0, f.port.clicks.size());
            assertTrue(f.leased.get() == false, "lease must be released");
        }
    }

    @Test
    void bundleSupplyShortOfMinimumCountDrainsAndReportsUnavailable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.port.set(9, SimulatedBundleMenuPort.bundle(new ItemStack(Items.STONE, 16)));

            MaterialReservation result = f.request(Items.STONE, 32);

            assertEquals(MaterialReservation.State.UNAVAILABLE, result.state());
            assertEquals(16, looseCount(f, Items.STONE), "extracted progress stays loose");
            assertEquals(1, f.next.calls, "the coordinator continues past the drained bundle");
        }
    }

    private static int looseCount(Fixture f, Item item) {
        int count = 0;
        for (int slot = 0; slot < f.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = f.player.getInventory().getItem(slot);
            if (stack.is(item) && stack.getItem() != Items.BUNDLE) count += stack.getCount();
        }
        return count;
    }
}