package dev.stx.anticheat.bukkit;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Hile cezası gösterisi (ban zaten defterde; gösteri bittiğinde oyuncu atılır):
 *
 * <ol>
 *   <li><b>Yükseliş:</b> oyuncu görünmez bir koltuğa (zırh askısı) oturtulup havaya kaldırılır -> karakter oturma pozunda.
 *       Etrafında lamalar halka olup mevlana gibi döner ve oyuncuya tükürür. Ekranda "PATLADIN BOM" yazısı çıkar.</li>
 *   <li><b>Ban:</b> süre dolunca oyuncu atılır (15 gün, sebep: Hile Kullanımı).</li>
 *   <li><b>Final (sunucudakiler görür):</b> lamalar yavrulaşıp banlandığı yere doğru küçülerek akar, renk değiştirip
 *       yeşile döner; yeşil olunca tam o noktada bir creeper doğar ve döner, sonra saydamlaşıp kaybolur;
 *       yerine TNT sesi gelir. Lamalar son ana kadar tükürür.</li>
 * </ol>
 *
 * Tüm varlıklar kalıcı değildir (setPersistent(false)) ve etiketlidir; hasar vermez. Oyuncunun kalıcı hiçbir özelliği
 * (görünmezlik, ölümsüzlük, yerçekimi...) değiştirilmez.
 */
final class PunishShow {
    static final String TAG = "stx_show";
    private static final String TEAM_PREFIX = "stxsh";
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();
    /** Lama dış hat (glow) renk geçişi: yeşile varınca final. */
    private static final NamedTextColor[] GLOW = {NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN};
    private static final int CONVERGE_TICKS = 40, CREEPER_SPIN_TICKS = 40, FADE_TICKS = 30;

    private enum Phase { RISE, POST, DONE }

    private final STXAntiCheat plugin;
    private final Sanctions owner;
    private final Player p;
    private final PlayerData d;
    private final UUID id;
    private final String reason;
    private final long banUntil;

    private final int llamaCount, spinTicks;
    private final double radius, rise;

    private Phase phase = Phase.RISE;
    private int t;
    private ArmorStand seat;
    private final List<Llama> llamas = new ArrayList<>();
    private Creeper creeper;
    private Location center;
    private double baseY, riseTarget;
    private boolean teleportMode;
    private int glowIdx = -1, spitCursor;
    private BukkitRunnable task;

    PunishShow(STXAntiCheat plugin, Sanctions owner, Player p, PlayerData d, String reason, long banUntil) {
        this.plugin = plugin;
        this.owner = owner;
        this.p = p;
        this.d = d;
        this.id = p.getUniqueId();
        this.reason = reason;
        this.banUntil = banUntil;
        STXConfig c = plugin.cfg();
        this.llamaCount = Math.max(3, Math.min(16, c.i("sanctions.show.llamas", 8)));
        this.radius = Math.max(1.5, Math.min(5.0, c.d("sanctions.show.radius", 2.5)));
        this.rise = Math.max(0.0, Math.min(8.0, c.d("sanctions.show.rise-height", 3.0)));
        this.spinTicks = (int) Math.max(40, Math.min(300, c.d("sanctions.show.spin-seconds", 5.0) * 20));
    }

    // ------------------------------------------------------------------ başlangıç

    void start() {
        if (!p.isOnline()) { owner.showEnded(id); return; }          // ban zaten defterde
        World w = p.getWorld();
        Location base = p.getLocation();
        baseY = base.getY();

        // Başın üstünde tavan varsa yükselme payını kısalt
        double h = rise;
        Location eye = p.getEyeLocation();
        RayTraceResult up = w.rayTraceBlocks(eye, new Vector(0, 1, 0), rise + 0.7, FluidCollisionMode.NEVER, true);
        if (up != null) h = Math.max(0, up.getHitPosition().getY() - eye.getY() - 0.7);
        riseTarget = baseY + h;

        // Gösteri sürerken: saldırı yok, kontroller susar
        long nowWall = System.currentTimeMillis();
        long total = spinTicks * 50L + 10_000L;
        d.mitUntil.merge("ATTACK", nowWall + total, Math::max);
        d.exemptUntilMs = Math.max(d.exemptUntilMs, STXAntiCheat.nowMs() + total);

        p.closeInventory();
        seat = w.spawn(base, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setMarker(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setPersistent(false);
            as.addScoreboardTag(TAG);
        });
        seat.addPassenger(p);                                       // oyuncu OTURMA pozuna geçer

        for (int i = 0; i < llamaCount; i++) {
            final int k = i;
            double a = 2 * Math.PI * i / llamaCount;
            Location at = new Location(w, base.getX() + radius * Math.cos(a), baseY, base.getZ() + radius * Math.sin(a));
            Llama ll = w.spawn(at, Llama.class, l -> {
                l.setAI(false);
                l.setGravity(false);
                l.setInvulnerable(true);
                l.setSilent(true);
                l.setCollidable(false);
                l.setPersistent(false);
                l.setRemoveWhenFarAway(true);
                l.setColor(Llama.Color.values()[k % Llama.Color.values().length]);
                l.addScoreboardTag(TAG);
            });
            llamas.add(ll);
            fx(Particle.POOF, at.clone().add(0, 1, 0), 6, 0.3);
        }
        w.playSound(base, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 1f);
        w.playSound(base, Sound.ENTITY_LLAMA_ANGRY, 1.2f, 1f);

        task = new BukkitRunnable() {
            @Override public void run() {
                try {
                    if (phase == Phase.RISE) riseTick(); else if (phase == Phase.POST) postTick();
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Ceza gösterisi hatası (" + d.name + "): " + ex);
                    finish();
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ 1. aşama: yükseliş + mevlana

    private void riseTick() {
        if (!p.isOnline() || seat == null || !seat.isValid()) { enterPost(false); return; }
        t++;

        // Koltuktan inmeye çalışırsa (shift) geri oturt
        if (!seat.getPassengers().contains(p)) {
            if (p.getWorld().equals(seat.getWorld()) && p.getLocation().distanceSquared(seat.getLocation()) > 4) p.teleport(seat.getLocation());
            seat.addPassenger(p);
        }

        // "Birden" havaya kalk: ~10 tikte yukarı (yavaşlayarak)
        double ease = 1 - Math.pow(1 - Math.min(1.0, t / 10.0), 3);
        lift(baseY + (riseTarget - baseY) * ease);

        Location c = seat.getLocation();
        orbit(c, t * 0.26, radius + 0.25 * Math.sin(t * 0.15));

        if (t == 8) {
            STXConfig cfg = plugin.cfg();
            p.showTitle(Title.title(
                    AMP.deserialize(cfg.s("sanctions.show.title", "&c&lPATLADIN BOM")),
                    AMP.deserialize(cfg.s("sanctions.show.subtitle", "&cHileyle oynarsan patlatırlar.")),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(Math.max(500L, (spinTicks - 8) * 50L)), Duration.ofMillis(300))));
        }
        if (t >= 12 && t % 5 == 0) spitAt(p.getEyeLocation(), 2);

        if (t >= spinTicks) enterPost(true);
    }

    /** Koltuğu hedef yüksekliğe taşır: önce hız; kımıldamıyorsa Paper'ın "yolcuları koru" ışınlaması. */
    private void lift(double targetY) {
        Location sl = seat.getLocation();
        if (!teleportMode) {
            seat.setVelocity(new Vector(0, Math.max(-0.6, Math.min(0.6, (targetY - sl.getY()) * 0.5)), 0));
            if (t == 6 && riseTarget - baseY > 0.8 && sl.getY() - baseY < 0.15) teleportMode = true;
        }
        if (teleportMode) {
            Location n = sl.clone();
            n.setY(targetY);
            teleportKeepRiders(seat, n);
        }
    }

    /** Lamalar merkez etrafında halka: halka döner (mevlana), her lama kendi ekseninde de döner, hafifçe iner-çıkar. */
    private void orbit(Location c, double angle, double r) {
        World w = c.getWorld();
        int n = llamas.size();
        for (int i = 0; i < n; i++) {
            Llama ll = llamas.get(i);
            if (!ll.isValid()) continue;
            double a = angle + 2 * Math.PI * i / n;
            double y = c.getY() + 0.1 + 0.3 * Math.sin(t * 0.2 + i);
            float yaw = (float) ((t * 35.0 + i * 45.0) % 360.0);
            ll.teleport(new Location(w, c.getX() + r * Math.cos(a), y, c.getZ() + r * Math.sin(a), yaw, 0f));
        }
    }

    /** {@code count} lama (sırayla) hedefe tükürür. Tükürük hasar vermez (ShowListener). */
    private void spitAt(Location target, int count) {
        if (llamas.isEmpty()) return;
        for (int k = 0; k < count; k++) {
            Llama from = llamas.get(spitCursor++ % llamas.size());
            if (!from.isValid()) continue;
            Location mouth = from.getLocation().add(0, from.isAdult() ? 1.3 : 0.65, 0);
            Vector v = target.toVector().subtract(mouth.toVector());
            if (v.lengthSquared() < 1e-4) continue;
            v.normalize().multiply(1.1);
            from.getWorld().spawn(mouth, LlamaSpit.class, sp -> {
                sp.setShooter(from);
                sp.setVelocity(v);
                sp.setPersistent(false);
                sp.addScoreboardTag(TAG);
            });
            from.getWorld().playSound(mouth, Sound.ENTITY_LLAMA_SPIT, 0.8f, 1f);
        }
    }

    // ------------------------------------------------------------------ 2. aşama: ban (oyuncu atılır) + final

    /** @param kick oyuncu hâlâ çevrimiçiyse sunucudan at */
    void enterPost(boolean kick) {
        if (phase != Phase.RISE) return;
        center = (seat != null && seat.isValid()) ? seat.getLocation() : (p.isOnline() ? p.getLocation() : null);
        if (center == null) { finish(); return; }
        phase = Phase.POST;
        t = 0;
        if (kick && p.isOnline()) {
            p.kick(SEC.deserialize(Sanctions.banScreen(reason, banUntil, System.currentTimeMillis())));
        }
        if (seat != null) seat.remove();

        // Lamalar küçük (yavru) hâline geçer ve parlamaya başlar
        for (Llama ll : llamas) {
            if (!ll.isValid()) continue;
            ll.setBaby();
            ll.setAgeLock(true);
            ll.setGlowing(true);
            fx(Particle.POOF, ll.getLocation().add(0, 0.5, 0), 4, 0.2);
        }
        setGlow(0);
        center.getWorld().playSound(center, Sound.ENTITY_LLAMA_ANGRY, 1.2f, 1.6f);
    }

    private void postTick() {
        t++;
        World w = center.getWorld();
        if (w == null) { finish(); return; }

        if (t <= CONVERGE_TICKS) {
            // Küçük lamalar banlanan noktaya doğru spiral çizip "içine geçer"
            double prog = (double) t / CONVERGE_TICKS;
            double r = radius * (1 - prog * prog);
            Location c = center.clone();
            orbit(c, t * 0.5, r);
            // Renk değişimi: kırmızı -> altın -> sarı -> YEŞİL (+ lama post rengi titreşimi)
            int gi = Math.min(GLOW.length - 1, (int) (prog * GLOW.length));
            if (t == CONVERGE_TICKS) gi = GLOW.length - 1;
            if (gi != glowIdx) setGlow(gi);
            if (t % 4 == 0) {
                Llama.Color[] cols = Llama.Color.values();
                for (int i = 0; i < llamas.size(); i++) if (llamas.get(i).isValid()) llamas.get(i).setColor(cols[(t / 4 + i) % cols.length]);
            }
            if (t % 4 == 0 && t < CONVERGE_TICKS - 4) spitAt(center.clone().add(0, 0.9, 0), 2);   // "sana tükürsünler"
        }

        if (t == CONVERGE_TICKS) spawnCreeper(w);
        if (t > CONVERGE_TICKS && creeper != null && creeper.isValid()) {
            int s = t - CONVERGE_TICKS;
            Location cl = center.clone();
            cl.setYaw((float) ((s * 40.0) % 360.0));
            creeper.teleport(cl);                                    // olduğu yerde döner
            if (s > CREEPER_SPIN_TICKS) {
                int f = s - CREEPER_SPIN_TICKS;                      // saydamlaşma: gitgide hızlanan titreme
                int half = f < 10 ? 3 : f < 20 ? 2 : 1;
                creeper.setInvisible((f / half) % 2 == 1);
                if (f == 1) w.playSound(center, Sound.ENTITY_TNT_PRIMED, 1.5f, 1f);          // TNT sesi
                if (f >= FADE_TICKS) {
                    creeper.setInvisible(true);
                    creeper.remove();
                    creeper = null;
                    fx(Particle.EXPLOSION, center.clone().add(0, 0.8, 0), 1, 0.0);
                    fx(Particle.CLOUD, center.clone().add(0, 0.8, 0), 25, 0.5);
                    w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1f);             // patladı
                }
            }
        }
        if (t > CONVERGE_TICKS + CREEPER_SPIN_TICKS + FADE_TICKS + 10) finish();
    }

    /** Lamalar yeşile dönünce: lamalar yok olur, tam banlanılan noktada creeper doğar. */
    private void spawnCreeper(World w) {
        for (Llama ll : llamas) if (ll.isValid()) fx(Particle.HAPPY_VILLAGER, ll.getLocation().add(0, 0.5, 0), 8, 0.3);
        removeLlamas();
        creeper = w.spawn(center, Creeper.class, cr -> {
            cr.setAI(false);
            cr.setGravity(false);
            cr.setSilent(true);
            cr.setInvulnerable(true);
            cr.setCollidable(false);
            cr.setPersistent(false);
            cr.setRemoveWhenFarAway(true);
            cr.setExplosionRadius(0);
            cr.addScoreboardTag(TAG);
        });
        fx(Particle.HAPPY_VILLAGER, center.clone().add(0, 1, 0), 20, 0.5);
        w.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.8f);
    }

    // ------------------------------------------------------------------ bitiş / temizlik

    /** Oyuncu gösteri bitmeden çıktı: ban zaten defterde; final yine de oynar (kick yok). */
    void onQuit() { if (phase == Phase.RISE) enterPost(false); }

    void finish() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        if (task != null) task.cancel();
        if (seat != null && seat.isValid()) seat.remove();
        removeLlamas();
        if (creeper != null && creeper.isValid()) creeper.remove();
        creeper = null;
        owner.showEnded(id);
    }

    private void removeLlamas() {
        clearGlow();
        for (Llama ll : llamas) if (ll.isValid()) ll.remove();
        llamas.clear();
    }

    // ------------------------------------------------------------------ yardımcılar

    private static void fx(Particle pt, Location l, int count, double spread) {
        if (l.getWorld() != null) l.getWorld().spawnParticle(pt, l, count, spread, spread, spread, 0.02);
    }

    /** Lamaların dış hat rengini ayarlar (ana skorbord takımları: stxsh0..3). */
    private void setGlow(int idx) {
        glowIdx = idx;
        Scoreboard sb = mainBoard();
        if (sb == null) return;
        clearGlow();
        Team team = sb.getTeam(TEAM_PREFIX + idx);
        if (team == null) {
            team = sb.registerNewTeam(TEAM_PREFIX + idx);
            team.color(GLOW[idx]);
        }
        for (Llama ll : llamas) team.addEntry(ll.getUniqueId().toString());
    }

    private void clearGlow() {
        Scoreboard sb = mainBoard();
        if (sb == null) return;
        for (int i = 0; i < GLOW.length; i++) {
            Team tm = sb.getTeam(TEAM_PREFIX + i);
            if (tm == null) continue;
            for (Llama ll : llamas) tm.removeEntry(ll.getUniqueId().toString());
        }
    }

    private static Scoreboard mainBoard() {
        return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** Eklenti açılırken/kapanırken: kalan gösteri takımlarını sil. */
    static void cleanupTeams() {
        Scoreboard sb = mainBoard();
        if (sb == null) return;
        for (Team tm : new ArrayList<>(sb.getTeams())) {
            if (tm.getName().startsWith(TEAM_PREFIX)) {
                try { tm.unregister(); } catch (IllegalStateException ignored) { }
            }
        }
    }

    /** Paper: Entity#teleport(Location, TeleportFlag...) + EntityState.RETAIN_PASSENGERS (yansıma: sürümden bağımsız derlenir). */
    private static void teleportKeepRiders(Entity e, Location to) {
        try {
            Class<?> state = Class.forName("io.papermc.paper.entity.TeleportFlag$EntityState");
            Class<?> flag = Class.forName("io.papermc.paper.entity.TeleportFlag");
            Object retain = null;
            for (Object o : state.getEnumConstants()) if (o.toString().equals("RETAIN_PASSENGERS")) retain = o;
            if (retain == null) return;
            Object flags = Array.newInstance(flag, 1);
            Array.set(flags, 0, retain);
            Method m = Entity.class.getMethod("teleport", Location.class, flags.getClass());
            m.invoke(e, to, flags);
        } catch (ReflectiveOperationException ignored) {
            // bu Paper sürümünde yok: koltuk hız ile hareket etmeye devam eder
        }
    }
}
