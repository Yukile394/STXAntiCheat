package dev.stx.anticheat.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.Vector;

/** Meşru durum değişimleri (teleport, mod değişimi, uçuş, elytra, araç, hız) için tolerans pencereleri. */
final class StateListener implements Listener {
    private final STXAntiCheat plugin;

    StateListener(STXAntiCheat plugin) { this.plugin = plugin; }

    private void grace(Player p, String key, int def) {
        PlayerData d = plugin.data(p);
        d.grace(plugin.tick() + plugin.cfg().i(key, def));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        plugin.data(e.getPlayer());
        grace(e.getPlayer(), "safety.join-grace-ticks", 100);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { plugin.remove(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        d.grace(plugin.tick() + plugin.cfg().i("safety.teleport-grace-ticks", 30));
        d.resetMotionState();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        d.grace(plugin.tick() + plugin.cfg().i("safety.respawn-grace-ticks", 60));
        d.resetMotionState();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        d.grace(plugin.tick() + plugin.cfg().i("safety.teleport-grace-ticks", 30));
        d.resetMotionState();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent e) { grace(e.getPlayer(), "safety.gamemode-grace-ticks", 40); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlight(PlayerToggleFlightEvent e) { grace(e.getPlayer(), "safety.flight-toggle-grace-ticks", 80); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) grace(p, "safety.glide-end-grace-ticks", 40);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent e) { grace(e.getPlayer(), "safety.riptide-grace-ticks", 60); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent e) { grace(e.getPlayer(), "safety.teleport-grace-ticks", 30); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) grace(p, "safety.vehicle-exit-grace-ticks", 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p) plugin.data(p).resetMotionState();
    }

    /** Sunucu bir oyuncuya hız verdi (knockback, patlama, rüzgar topu, plugin). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        Vector v = e.getVelocity();
        long tick = plugin.tick();
        // Hız verildikten sonra hareket modeli sapacağı için süre tanı (büyük hızda daha uzun).
        d.velocityUntilTick = Math.max(d.velocityUntilTick, tick + 12 + Math.min(80, (int) (25 * v.length())));

        boolean skip = plugin.exemptReason(p, d) != null || plugin.lagging(p, d)
                || p.isInWater() || p.isInLava() || tick < d.liquidUntilTick || tick < d.climbUntilTick
                || tick < d.bounceUntilTick || tick < d.pistonUntilTick
                || Probe.knockbackBlocked(p, v);
        d.velocity.onVelocity(v.getX(), v.getY(), v.getZ(), skip, STXAntiCheat.nowMs(), p.getPing());
    }
}
