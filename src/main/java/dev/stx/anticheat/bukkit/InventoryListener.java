package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.Result;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Yalnızca GERÇEK blok konteynerleri (sandık, varil, shulker...). Plugin GUI menüleri kapsam dışıdır. */
final class InventoryListener implements Listener {
    private final STXAntiCheat plugin;

    InventoryListener(STXAntiCheat plugin) { this.plugin = plugin; }

    private static boolean realContainer(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder h = inv.getHolder();
        return h instanceof Container || h instanceof DoubleChest;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        PlayerData d = plugin.data(p);
        if (realContainer(e.getInventory())) d.window.onOpen(plugin.tick());
        else d.window.onClose();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        plugin.data(p).window.onClose();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getClick().isShiftClick()) return;
        if (!realContainer(e.getView().getTopInventory())) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        PlayerData d = plugin.data(p);
        if (d.bypass || plugin.exemptReason(p, d) != null || plugin.lagging(p, d)) return;
        Result r = d.window.onContainerClick(plugin.tick());
        if (r != null) plugin.flagger().flag(p, d, "ChestStealer", r);
    }
}
