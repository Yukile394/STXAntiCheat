package dev.stx.anticheat.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Ceza gösterisi güvenliği: lamalar/tükürükler/creeper kimseye zarar vermez, gösterideki oyuncu zarar görmez. */
final class ShowListener implements Listener {
    private final STXAntiCheat plugin;

    ShowListener(STXAntiCheat plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        if (en.getScoreboardTags().contains(PunishShow.TAG)) { e.setCancelled(true); return; }
        if (en instanceof Player p && plugin.sanctions().showing(p.getUniqueId())) e.setCancelled(true);
    }

    /** Gösteri tükürüğü (veya gösteri varlığı) kime çarparsa çarpsın hasar yok. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        if (e.getDamager().getScoreboardTags().contains(PunishShow.TAG)) e.setCancelled(true);
    }
}
