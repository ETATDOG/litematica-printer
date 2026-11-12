package me.aleksilassila.litematica.printer.integration.bundle;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.core.action.ResourceLease;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.integration.inventory.InventoryProvider;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialRequest;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialReservation;
import me.aleksilassila.litematica.printer.printer.action.ActionPort;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

/**
 * Material provider that pulls the requested item out of bundles carried in the player
 * inventory. Sits right after the plain-inventory provider: bundle materials are local and
 * cheap, so they win over remote container integrations.
 */
public final class BundleProvider implements InventoryProvider, RuntimeComponent {
    private static final String LEASE_OWNER = "bundle_extract";

    private final ActionPort actionBroker;
    private final BundleMenuPort port;
    private final BundleExtractionEngine engine;
    private boolean resourcesAcquired;

    public BundleProvider(ActionPort actionBroker) {
        this(actionBroker, new MinecraftBundleMenuPort(Minecraft.getInstance()));
    }

    public BundleProvider(ActionPort actionBroker, BundleMenuPort port) {
        this.actionBroker = actionBroker;
        this.port = port;
        this.engine = new BundleExtractionEngine(port, BundleExtractionEngine.Strategy.AUTO);
    }

    @Override
    public String id() {
        return "bundle";
    }

    @Override
    public boolean supports(MaterialRequest request) {
        return Configs.Core.USE_BUNDLE_MATERIALS.getBooleanValue()
                && InventoryProvider.super.supports(request);
    }

    @Override
    public MaterialReservation request(MaterialRequest request) {
        if (!this.acquireResources()) {
            return pending(request);
        }
        return this.advance(request);
    }

    @Override
    public MaterialReservation status(MaterialRequest request) {
        if (!this.resourcesAcquired && !this.acquireResources()) {
            return pending(request);
        }
        return this.advance(request);
    }

    @Override
    public long pendingTimeoutTicks() {
        // One stack is a handful of spaced clicks; several stacks plus server latency must fit.
        return 200L;
    }

    @Override
    public void onEpochChanged(RuntimeEvent.EpochChanged event) {
        this.reset();
    }

    @Override
    public void reset() {
        this.engine.reset();
        this.releaseResources();
    }

    private MaterialReservation advance(MaterialRequest request) {
        Item availableItem = findAvailableItem(request);
        if (availableItem != null) {
            this.releaseResources();
            return MaterialReservation.available(request, availableItem);
        }
        if (this.port.isCreativeLike()) {
            // Creative players never need bundles drained for materials.
            this.releaseResources();
            return unavailable(request);
        }
        if (this.engine.running()) {
            if (this.engine.tick() == BundleExtractionEngine.Outcome.FAILED) {
                this.releaseResources();
                return unavailable(request);
            }
        } else {
            BundleExtractionEngine.Job job = this.planJob(request);
            if (job == null) {
                this.releaseResources();
                return unavailable(request);
            }
            this.engine.start(job);
        }
        return pending(request);
    }

    /** First accepted bundle entry that still sits inside a carried bundle. */
    private BundleExtractionEngine.Job planJob(MaterialRequest request) {
        for (int slot = 0; slot < this.port.inventorySize(); slot++) {
            ItemStack stack = this.port.inventoryStack(slot);
            if (!(stack.getItem() instanceof BundleItem)) {
                continue;
            }
            List<ItemStack> contents = BundleExtractionEngine.contents(stack);
            if (contents == null) {
                continue;
            }
            if (this.port.menuSlotOf(slot) < 0) {
                continue;
            }
            for (ItemStack entry : contents) {
                if (request.acceptsStack(entry)) {
                    return new BundleExtractionEngine.Job(slot, entry.copy());
                }
            }
        }
        return null;
    }

    private static Item findAvailableItem(MaterialRequest request) {
        for (Item item : request.acceptedItems()) {
            if (InventoryUtils.playerHasItemInInventory(Minecraft.getInstance().player, item,
                    request::acceptsStack, request.minimumCount())) {
                return item;
            }
        }
        return null;
    }

    private boolean acquireResources() {
        if (this.resourcesAcquired) {
            return true;
        }
        this.resourcesAcquired = this.actionBroker.tryAcquire(
                LEASE_OWNER,
                EnumSet.of(ResourceLease.MAIN_HAND, ResourceLease.INVENTORY, ResourceLease.CONTAINER),
                0L
        );
        return this.resourcesAcquired;
    }

    private void releaseResources() {
        if (this.resourcesAcquired) {
            this.actionBroker.releaseOwner(LEASE_OWNER);
        }
        this.resourcesAcquired = false;
    }

    private static MaterialReservation pending(MaterialRequest request) {
        return new MaterialReservation(request.token(), MaterialReservation.State.PENDING);
    }

    private static MaterialReservation unavailable(MaterialRequest request) {
        return new MaterialReservation(request.token(), MaterialReservation.State.UNAVAILABLE);
    }
}