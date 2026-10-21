package dev.stx.anticheat.core;

import dev.stx.anticheat.core.Geometry.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Bağımsız test koşucusu (JUnit gerekmez). Çıkış kodu != 0 ise başarısız test var. */
public class CoreTests {
    static int passed, failed;
    static void check(boolean ok, String name) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    // ---------- yardımcılar ----------
    static MoveSample ms(long tick, double dx, double dy, double dz, boolean ground) {
        return new MoveSample(tick, dx, dy, dz, 0, 0, ground, ground, false, false, false, false, false, 1.0, 0, false, 0);
    }
    static MoveSample withUsing(MoveSample s, boolean using) {
        return new MoveSample(s.tick(), s.dx(), s.dy(), s.dz(), s.yaw(), s.pitch(), s.clientGround(), s.standing(), false, false, false, false, false, s.speedMul(), 0, using, 0);
    }

    /** Hareket kontrollerinin hepsini bir örnek listesinden geçirir; üretilen kanıt sayısını döndürür. */
    static int runMovement(List<MoveSample> list, boolean solidBelow) {
        AirCheck air = new AirCheck(); SpeedCheck sp = new SpeedCheck(0.10, 2.2); NoFallCheck nf = new NoFallCheck();
        int n = 0;
        for (MoveSample s : list) {
            if (air.onMove(s) != null) n++;
            if (sp.onMove(s) != null) n++;
            if (nf.onMove(s, () -> solidBelow) != null) n++;
        }
        return n;
    }

    public static void main(String[] args) {
        legitMovement();
        cheatMovement();
        timer();
        velocity();
        combat();
        clicks();
        rotation();
        scaffoldCriticalsWindow();
        policy();
        newChecks();
        accuracy();
        System.out.println("\n== " + passed + " geçti, " + failed + " başarısız ==");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ================= MEŞRU HAREKET =================
    static void legitMovement() {
        System.out.println("[Meşru hareket – kanıt ÜRETMEMELİ]");
        for (double mul : new double[]{1.0, 1.2, 1.4, 1.6})
            for (boolean sprint : new boolean[]{false, true})
                for (boolean bhop : new boolean[]{false, true})
                    check(runMovement(VanillaSim.run(400, sprint, bhop, mul), true) == 0,
                            String.format("yürü/sprint/bhop mul=%.1f sprint=%b bhop=%b", mul, sprint, bhop));

        // yüksekten düşüş + iniş
        VanillaSim f = new VanillaSim(); f.y = 30; f.onGround = false;
        List<MoveSample> fall = new ArrayList<>();
        for (int i = 0; i < 120; i++) fall.add(f.step(false, false, 0, 1.0));
        AirCheck air = new AirCheck(); NoFallCheck nf = new NoFallCheck(); int n = 0;
        for (MoveSample s : fall) {
            if (air.onMove(s) != null) n++;
            if (nf.onMove(s, () -> s.clientGround()) != null) n++; // gerçek zemin: yalnızca iniş tikinde var
        }
        check(n == 0, "30 blok düşüş ve iniş");

        // Zıplama Güçlendirme II
        VanillaSim j = new VanillaSim(); j.jumpBoost = 0.2;
        List<MoveSample> jb = new ArrayList<>();
        for (int i = 0; i < 100; i++) jb.add(j.step(true, true, 1.0, 1.0));
        check(runMovement(jb, true) == 0, "Zıplama Güçlendirme II ile sürekli zıplama");

        // merdiven / slab: zeminden zemine 0.5
        AirCheck a2 = new AirCheck(); int c = 0;
        for (int i = 0; i < 30; i++) { if (a2.onMove(ms(i, 0, 0, 0.2, true)) != null) c++; }
        for (int i = 30; i < 40; i++) { if (a2.onMove(ms(i, 0, 0.5, 0.2, true)) != null) c++; }
        check(c == 0, "merdiven çıkma (dy=0.5)");

        // zıplama sırasında tavana çarpma
        VanillaSim h = new VanillaSim(); List<MoveSample> hit = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            MoveSample s = h.step(false, i == 5, 1.0, 1.0);
            if (i == 8) h.vy = 0; // tavana çarptı
            hit.add(s);
        }
        check(runMovement(hit, true) == 0, "tavana çarpan zıplama");
    }

    // ================= HİLE HAREKET =================
    static void cheatMovement() {
        System.out.println("[Hile hareketi – tespit EDİLMELİ]");
        // Fly (havada sabit yükseklik)
        List<MoveSample> fly = new ArrayList<>();
        fly.add(ms(1, 0, 0.42, 0.2, false));
        for (int i = 2; i < 40; i++) fly.add(ms(i, 0, 0.0, 0.25, false));
        check(runMovement(fly, true) > 0, "Fly: havada sabit (hover)");

        List<MoveSample> up = new ArrayList<>();
        up.add(ms(0, 0, 0, 0, true));
        for (int i = 1; i < 30; i++) up.add(ms(i, 0, 0.6, 0.25, false));
        check(runMovement(up, true) > 0, "Fly: sürekli yükselme (Vanilla Flight 0.6)");

        List<MoveSample> hj = new ArrayList<>();
        hj.add(ms(0, 0, 0, 0, true));
        hj.add(ms(1, 0, 1.2, 0.1, false));
        for (int i = 2; i < 12; i++) hj.add(ms(i, 0, 0.3, 0.1, false));
        check(runMovement(hj, true) > 0, "HighJump (Wetsuki 2.0 gücü)");

        // Step: zeminden zemine 1.0 tekrar eden
        AirCheck a = new AirCheck(); int c = 0;
        for (int i = 0; i < 20; i++) { if (a.onMove(ms(i, 0, 0, 0.2, true)) != null) c++; }
        for (int i = 20; i < 30; i++) { if (a.onMove(ms(i, 0, 1.0, 0.2, true)) != null) c++; }
        check(c > 0, "Step yüksekliği 1.0");

        // FastFall (Aura pullDown): düşerken beklenenden hızlı
        List<MoveSample> ff = new ArrayList<>();
        ff.add(ms(0, 0, 0, 0, true));
        ff.add(ms(1, 0, 0.42, 0.2, false));
        VanillaSim v = new VanillaSim(); v.y = 50; v.onGround = false;
        double dy = -0.0784;
        for (int i = 2; i < 30; i++) { dy -= 0.35; ff.add(ms(i, 0, dy, 0, false)); }
        check(runMovement(ff, true) > 0, "FastFall");

        // Speed: zeminde sabit 0.6
        List<MoveSample> sp = new ArrayList<>();
        for (int i = 0; i < 60; i++) sp.add(ms(i, 0, 0, 0.6, true));
        check(runMovement(sp, true) > 0, "Speed: yerde 0.6 b/tik");

        // Speed: bhop'un hızlı tavanı (NCP bhop ~0.36 ortalama +): kısmi hile, ortalama 0.5
        List<MoveSample> sp2 = new ArrayList<>();
        for (int i = 0; i < 80; i++) sp2.add(ms(i, 0, i % 2 == 0 ? 0 : 0, 0.5, true));
        check(runMovement(sp2, true) > 0, "Speed: ortalama 0.5");

        // NoFall: düşerken onGround=true gönderme, altında blok yok
        List<MoveSample> nof = new ArrayList<>();
        double d = 0;
        for (int i = 0; i < 60; i++) { d = (d - 0.08) * 0.98; nof.add(ms(i, 0, d, 0, i > 3)); }
        NoFallCheck nfc = new NoFallCheck(); int nc = 0;
        for (MoveSample s : nof) if (nfc.onMove(s, () -> false) != null) nc++;
        check(nc > 0, "NoFall: sahte onGround (blok yok)");

        // NoFall: fallDistance sıfırlanmış
        NoFallCheck nf2 = new NoFallCheck(); int nd = 0; double dd = 0;
        for (int i = 0; i < 40; i++) {
            dd = (dd - 0.08) * 0.98;
            MoveSample s = new MoveSample(i, 0, dd, 0, 0, 0, false, false, false, false, false, false, false, 1, 0, false, 0.0);
            if (nf2.onMove(s, () -> false) != null) nd++;
        }
        check(nd > 0, "NoFall: sunucu fallDistance=0 iken gerçek düşüş >4");

        // NoSlow
        NoSlowCheck ns = new NoSlowCheck(0.12); int nn = 0;
        for (int i = 0; i < 30; i++) if (ns.onMove(withUsing(ms(i, 0, 0, 0.2158, true), true)) != null) nn++;
        check(nn > 0, "NoSlow: yemek yerken normal hız");
        NoSlowCheck ns2 = new NoSlowCheck(0.12); int nl = 0;
        double v0 = 0.2806;
        for (int i = 0; i < 40; i++) { v0 = v0 * 0.546 + 0.098 * 0.2; if (ns2.onMove(withUsing(ms(i, 0, 0, v0, true), true)) != null) nl++; }
        check(nl == 0, "NoSlow: meşru yavaşlama kanıt üretmiyor");

        // Muafiyetler
        AirCheck ex = new AirCheck(); int ec = 0;
        for (int i = 0; i < 40; i++) {
            MoveSample s = new MoveSample(i, 0, 0, 0.25, 0, 0, false, false, true, false, false, false, false, 1, 0, false, 0);
            if (ex.onMove(s) != null) ec++;
        }
        check(ec == 0, "exempt (uçuş/elytra/teleport) -> kontrol yok");
    }

    // ================= TIMER =================
    static void timer() {
        System.out.println("[Timer]");
        Random r = new Random(1);
        TimerCheck t = new TimerCheck(800, 100);
        long now = 0; int n = 0;
        for (int i = 0; i < 2000; i++) { now = i * 50L + (r.nextInt(31) - 15); if (t.onMove(now, false) != null) n++; }
        check(n == 0, "jitter'lı normal 20 paket/sn");

        // paketler tick sınırlarında paketlenmiş (1 tik 2 paket, ertesi tik 0)
        TimerCheck t2 = new TimerCheck(800, 100); n = 0; now = 0;
        for (int i = 0; i < 400; i++) { now += (i % 2 == 0) ? 0 : 100; if (t2.onMove(now, false) != null) n++; }
        check(n == 0, "tik sınırında paketlenmiş (0/100ms)");

        // 600ms lag sonrası patlama (12 paket)
        TimerCheck t3 = new TimerCheck(800, 100); n = 0; now = 0;
        for (int i = 0; i < 100; i++) { now += 50; t3.onMove(now, false); }
        now += 600; if (t3.onMove(now, false) != null) n++;
        for (int i = 0; i < 11; i++) { if (t3.onMove(now, false) != null) n++; }
        check(n == 0, "600ms lag sonrası 12 paketlik patlama");

        // gerçek Timer 2.0x (25ms aralık)
        TimerCheck t4 = new TimerCheck(800, 100); n = 0; now = 0;
        for (int i = 0; i < 400; i++) { now += 25; if (t4.onMove(now, false) != null) n++; }
        check(n > 0, "Timer 2.0x tespit");

        // Matrix Timer 1.088
        TimerCheck t5 = new TimerCheck(800, 100); n = 0; now = 0;
        for (int i = 0; i < 1200; i++) { now += 46; if (t5.onMove(now, false) != null) n++; }
        check(n > 0, "Timer 1.088x (sürekli) tespit");

        // lag muafiyeti
        TimerCheck t6 = new TimerCheck(800, 100); n = 0; now = 0;
        for (int i = 0; i < 400; i++) { now += 25; if (t6.onMove(now, true) != null) n++; }
        check(n == 0, "sunucu lag'liyken Timer kapalı");
    }

    // ================= VELOCITY =================
    static void velocity() {
        System.out.println("[Velocity]");
        // meşru: durgun oyuncu knockback alır; üç giriş: yok, ters, ileri
        for (double input : new double[]{0, -1, 1}) {
            VelocityCheck vc = new VelocityCheck(1.2, 3); int n = 0;
            for (int hit = 0; hit < 10; hit++) {
                VanillaSim s = new VanillaSim(); s.vz = 0.40; s.vy = 0.36; s.onGround = false; s.y = 0.01;
                long now = hit * 5000L;
                vc.onVelocity(0, 0.36, 0.40, false, now, 80);
                for (int i = 0; i < 20; i++) {
                    MoveSample m = s.step(false, false, input, 1.0);
                    if (vc.onMove(m, now + 80 + i * 50L) != null) n++;
                }
                if (vc.poll(now + 3000) != null) n++;
            }
            check(n == 0, "meşru knockback, giriş=" + input);
        }
        // hile: knockback iptal, oyuncu yerinde
        VelocityCheck vc = new VelocityCheck(1.2, 3); int n = 0;
        for (int hit = 0; hit < 6; hit++) {
            long now = hit * 5000L;
            vc.onVelocity(0, 0.36, 0.40, false, now, 80);
            for (int i = 0; i < 12; i++) if (vc.onMove(ms(i, 0, 0, 0.0, true), now + 80 + i * 50L) != null) n++;
            if (vc.poll(now + 3000) != null) n++;
        }
        check(n > 0, "AntiKnockback (Wetsuki Velocity Cancel): hareketsiz");
        // hile: %20 kalan
        VelocityCheck v2 = new VelocityCheck(1.2, 3); n = 0;
        for (int hit = 0; hit < 8; hit++) {
            long now = hit * 5000L;
            v2.onVelocity(0, 0.36, 0.40, false, now, 80);
            VanillaSim s = new VanillaSim(); s.vz = 0.08; s.onGround = false; s.y = 0.01;
            for (int i = 0; i < 12; i++) if (v2.onMove(s.step(false, false, 0, 1.0), now + 80 + i * 50L) != null) n++;
            if (v2.poll(now + 3000) != null) n++;
        }
        check(n > 0, "Velocity %20 yatay (Custom 0.2) tespit");
        // engelli knockback ölçülmez
        VelocityCheck v3 = new VelocityCheck(1.2, 3); n = 0;
        for (int hit = 0; hit < 10; hit++) {
            v3.onVelocity(0, 0.36, 0.40, true, hit * 5000L, 80);
            if (v3.poll(hit * 5000L + 3000) != null) n++;
        }
        check(n == 0, "duvara yaslı oyuncu (skip) ölçülmez");
    }

    // ================= SAVAŞ =================
    static void combat() {
        System.out.println("[Reach / Aim / Aura]");
        Box target = Box.ofEntity(0, 0, 3.0, 0.6, 1.8);
        // meşru: göz (0,1.62,0) – hedef 2.4 blok önde
        ReachCheck rc = new ReachCheck(0.25, 6); int n = 0;
        for (int i = 0; i < 100; i++) if (rc.onHit(0, 1.62, 0, List.of(Box.ofEntity(0, 0, 2.8, 0.6, 1.8)), 3.0) != null) n++;
        check(n == 0, "Reach: 2.5 blok normal vuruş");
        // lag: hedef geçmişte yakın, şimdi uzak (ping penceresi)
        rc = new ReachCheck(0.25, 6); n = 0;
        for (int i = 0; i < 100; i++) if (rc.onHit(0, 1.62, 0, List.of(Box.ofEntity(0, 0, 4.4, 0.6, 1.8), Box.ofEntity(0, 0, 3.4, 0.6, 1.8)), 3.0) != null) n++;
        check(n == 0, "Reach: lag geçmişinde ulaşılabilir hedef");
        rc = new ReachCheck(0.25, 6); n = 0;
        for (int i = 0; i < 20; i++) if (rc.onHit(0, 1.62, 0, List.of(Box.ofEntity(0, 0, 4.2, 0.6, 1.8)), 3.0) != null) n++;
        check(n > 0, "Reach 3.9 blok tespit");

        AimCheck ac = new AimCheck(0.15, 7); n = 0;
        for (int i = 0; i < 50; i++) if (ac.onHit(0, 1.62, 0, 0f, 0f, List.of(Box.ofEntity(0, 0, 2.5, 0.6, 1.8)), 3.0) != null) n++;
        check(n == 0, "Aim: hedefe bakarak vuruş");
        ac = new AimCheck(0.15, 7); n = 0;
        for (int i = 0; i < 30; i++) if (ac.onHit(0, 1.62, 0, 90f, 0f, List.of(Box.ofEntity(0, 0, 2.5, 0.6, 1.8)), 3.0) != null) n++;
        check(n > 0, "Aim: 90° yana bakıp vurma (rotasyonsuz Aura)");

        AuraCheck au = new AuraCheck(3, 5, 60); n = 0;
        for (int i = 0; i < 40; i++) if (au.onHit(i * 12L, 1, 10) != null) n++;
        check(n == 0, "Aura: tek hedef normal vuruş");
        au = new AuraCheck(3, 5, 60); n = 0;
        for (int i = 0; i < 12; i++) if (au.onHit(i, i % 4, 5) != null) n++;
        check(n > 0, "Aura: 4 hedef arasında hızlı geçiş");
        au = new AuraCheck(3, 5, 60); n = 0;
        for (int i = 0; i < 30; i++) if (au.onHit(i * 12L, 1, 75) != null) n++;
        check(n > 0, "Aura: her vuruştan önce 75° snap");
    }

    // ================= TIKLAMA =================
    static void clicks() {
        System.out.println("[AutoClicker]");
        Random r = new Random(7);
        ClickCheck cc = new ClickCheck(22, 3, 40, 0.35); int n = 0; long now = 0;
        for (int i = 0; i < 600; i++) { now += 70 + r.nextInt(60); if (cc.onSwing(now) != null) n++; }
        check(n == 0, "insan benzeri ~10 CPS");
        // 20 CPS butterfly patlaması (1 sn) kanıt üretmemeli
        cc = new ClickCheck(22, 3, 40, 0.35); n = 0; now = 0;
        for (int i = 0; i < 24; i++) { now += 42; if (cc.onSwing(now) != null) n++; }
        for (int i = 0; i < 60; i++) { now += 100; if (cc.onSwing(now) != null) n++; }
        check(n == 0, "tek saniyelik 24 CPS patlaması (butterfly)");
        cc = new ClickCheck(22, 3, 40, 0.35); n = 0; now = 0;
        for (int i = 0; i < 200; i++) { now += 25; if (cc.onSwing(now) != null) n++; }
        check(n > 0, "40 CPS sürekli");

        cc = new ClickCheck(22, 3, 40, 0.35); n = 0; long tick = 0;
        for (int i = 0; i < 200; i++) { tick += 2 + r.nextInt(3); if (cc.onAttack(tick) != null) n++; }
        check(n == 0, "insan vuruş aralığı (2-4 tik)");
        cc = new ClickCheck(22, 3, 40, 0.35); n = 0; tick = 0;
        for (int i = 0; i < 200; i++) { tick += 12; if (cc.onAttack(tick) != null) n++; }
        check(n > 0, "makine gibi sabit 12 tik vuruş");
    }

    // ================= ROTASYON (GCD) =================
    static void rotation() {
        System.out.println("[Rotasyon GCD]");
        Random r = new Random(3);
        for (double sens : new double[]{0.0, 0.3, 0.5, 1.0}) {
            double f = sens * 0.6 + 0.2, step = f * f * f * 8 * 0.15;
            RotationCheck rc = new RotationCheck(40, 0.005); int n = 0;
            for (int i = 0; i < 4000; i++) {
                double d = (1 + r.nextInt(30)) * step * (r.nextBoolean() ? 1 : -1);
                d = (float) d;                       // paketteki float hassasiyeti
                if (rc.onRotation(d) != null) n++;
            }
            check(n == 0, String.format("gerçek fare, hassasiyet=%.1f (adım=%.4f)", sens, step));
        }
        RotationCheck rc = new RotationCheck(40, 0.005); int n = 0;
        for (int i = 0; i < 2000; i++) if (rc.onRotation((float) (r.nextDouble() * 6 - 3)) != null) n++;
        check(n > 0, "yazılım üretimi rastgele dönüş (kuantize değil)");
    }

    // ================= SCAFFOLD / CRIT / WINDOW =================
    static void scaffoldCriticalsWindow() {
        System.out.println("[Scaffold / Criticals / Pencere]");
        ScaffoldCheck sc = new ScaffoldCheck(12); int n = 0;
        for (int i = 0; i < 100; i++) if (sc.onPlace(i * 3L, false, false, 0.2) != null) n++;
        check(n == 0, "normal köprü kurma (3 tikte bir)");
        sc = new ScaffoldCheck(12); n = 0;
        for (int i = 0; i < 40; i++) if (sc.onPlace(i, true, false, 0.2) != null) n++;
        check(n > 0, "arka yüz tıklama");
        sc = new ScaffoldCheck(12); n = 0;
        for (int i = 0; i < 40; i++) if (sc.onPlace(i, false, true, 0.2) != null) n++;
        check(n > 0, "bakış ışını uyuşmazlığı");
        sc = new ScaffoldCheck(12); n = 0;
        for (int i = 0; i < 60; i++) if (sc.onPlace(i, false, false, 0.28) != null) n++;
        check(n > 0, "her tik blok koyma (hız)");

        CriticalsCheck cr = new CriticalsCheck(); n = 0;
        for (int i = 0; i < 20; i++) {
            long t = i * 20L;
            cr.onMove(t, ms(t, 0, 0.0625, 0, false), true);       // zeminden 0.0625 yukarı, artık duruyor değil
            cr.onMove(t + 1, ms(t + 1, 0, -0.0625, 0, true), false);
            if (cr.onAttack(t + 1) != null) n++;
        }
        check(n > 0, "Criticals mikro-zıplama paketi (NCP 0.0625)");
        cr = new CriticalsCheck(); n = 0;
        for (int i = 0; i < 20; i++) {
            long t = i * 20L;
            cr.onMove(t, ms(t, 0, 0.42, 0, false), true);
            if (cr.onAttack(t + 1) != null) n++;
        }
        check(n == 0, "gerçek zıplama (dy=0.42) sonrası vuruş (crit) sorunsuz");
        cr = new CriticalsCheck(); n = 0;
        for (int i = 0; i < 20; i++) { long t = i * 20L; cr.onMove(t, ms(t, 0, 0.0625, 0.1, true), true); if (cr.onAttack(t + 1) != null) n++; }
        check(n == 0, "halı/kar katmanına çıkış (0.0625, yeni konumda da duruyor) sorunsuz");

        WindowCheck w = new WindowCheck(3, 12, 0.3); n = 0;
        w.onOpen(100);
        if (w.onContainerClick(101) != null) n++;
        check(n > 0, "ChestStealer: açılıştan 1 tik sonra tıklama");
        w = new WindowCheck(3, 12, 0.3); n = 0; w.onOpen(100);
        Random r = new Random(5); long t = 100;
        for (int i = 0; i < 80; i++) { t += 3 + r.nextInt(6); if (w.onContainerClick(t) != null) n++; }
        check(n == 0, "insan benzeri sandık boşaltma");
        w = new WindowCheck(3, 12, 0.3); n = 0; w.onOpen(100); t = 110;
        for (int i = 0; i < 60; i++) { t += 2; if (w.onContainerClick(t) != null) n++; }
        check(n > 0, "makine ritmi sandık boşaltma (2 tik)");

        WindowCheck g = new WindowCheck(3, 12, 0.3); g.onOpen(0); n = 0;
        for (int i = 20; i < 60; i++) if (g.onMoveWhileOpen(i, 0.22, true, false) != null) n++;
        check(n > 0, "GuiMove: konteyner açıkken yürüme");
        g = new WindowCheck(3, 12, 0.3); g.onOpen(0); n = 0;
        double sp = 0.28; for (int i = 1; i < 60; i++) { sp *= 0.546; if (g.onMoveWhileOpen(i, sp, true, false) != null) n++; }
        check(n == 0, "GuiMove: momentum sönümü meşru");
    }

    // ================= İHLAL DEFTERİ / CEZA BASAMAKLARI / JESUS =================
    static SanctionPolicy.Rule rule(boolean ban) {
        return new SanctionPolicy.Rule(ban, 2, 3, 6, 14, 72L * 3600_000, 60_000, 15, 30);
    }

    static void policy() {
        System.out.println("[İhlal defteri / basamaklar / kalıcılık]");
        var r = rule(true);
        check(SanctionPolicy.level(3, r) == SanctionPolicy.Level.LOW, "düşük puan = LOW (yalnızca kayıt)");
        check(SanctionPolicy.stage(SanctionPolicy.Level.LOW, 5, r, 3, 2) == SanctionPolicy.Stage.LOG, "LOW kanıtta ihlal sayısı kaç olursa olsun LOG");
        check(SanctionPolicy.level(8, r) == SanctionPolicy.Level.MEDIUM && SanctionPolicy.level(20, r) == SanctionPolicy.Level.HIGH, "MEDIUM/HIGH eşikleri");
        check(SanctionPolicy.stage(SanctionPolicy.Level.MEDIUM, 1, r, 1, 2) == SanctionPolicy.Stage.WARN_MITIGATE, "1. ihlal -> geçici müdahale (1/3)");
        check(SanctionPolicy.stage(SanctionPolicy.Level.MEDIUM, 2, r, 1, 2) == SanctionPolicy.Stage.RESTRICT, "2. ihlal -> kısıtlama (2/3)");
        check(SanctionPolicy.stage(SanctionPolicy.Level.MEDIUM, 3, r, 3, 2) == SanctionPolicy.Stage.RESTRICT, "3. ihlal ama kanıt yalnızca MEDIUM -> BAN YOK");
        check(SanctionPolicy.stage(SanctionPolicy.Level.HIGH, 3, r, 1, 2) == SanctionPolicy.Stage.RESTRICT, "3. ihlal, HIGH ama tek hile türü -> BAN YOK");
        check(SanctionPolicy.stage(SanctionPolicy.Level.HIGH, 3, rule(false), 3, 2) == SanctionPolicy.Stage.RESTRICT, "ban-uygun olmayan hile (AutoClicker vb.) asla BAN");
        check(SanctionPolicy.stage(SanctionPolicy.Level.HIGH, 3, r, 2, 2) == SanctionPolicy.Stage.BAN, "3 ihlal + HIGH + 2 bağımsız hile türü -> BAN");

        UUID id = UUID.randomUUID();
        long exp = r.expireMs(), t0 = 1_000_000_000L;
        ViolationLedger l = new ViolationLedger();
        check(l.record(id, "Ali", "Fly", t0, exp) == 1, "ilk ihlal = 1");
        check(l.record(id, "Ali", "Fly", t0 + 120_000, exp) == 2, "ikinci ihlal = 2");
        check(l.record(id, "Ali", "Reach", t0 + 130_000, exp) == 1, "farklı hile türü ayrı sayaç");
        ViolationLedger copy = new ViolationLedger();
        copy.load(l.serialize(t0 + 130_000, exp));
        check(copy.count(id, "Fly", t0 + 130_000 + exp + 1, exp) == 0, "süre dolunca ihlaller sayılmaz (yanlış pozitif birikmesi yok)");

        // çıkış-giriş / sunucu yeniden başlatma: serileştir -> yeni defter -> yükle
        l.setMitigation(id, "ATTACK", t0 + 600_000);
        String saved = l.serialize(t0 + 200_000, exp);
        ViolationLedger l2 = new ViolationLedger();
        check(l2.load(saved) == 0, "kayıt dosyası hatasız yüklendi");
        check(l2.count(id, "Fly", t0 + 200_000, exp) == 2 && l2.count(id, "Reach", t0 + 200_000, exp) == 1,
                "yeniden başlatma sonrası sayaçlar korundu (UUID bazlı)");
        check(l2.mitigations(id, t0 + 200_000).containsKey("ATTACK"), "yeniden girişte aktif kısıtlama korundu (kaçış yok)");
        check(l2.mitigations(id, t0 + 700_000).isEmpty(), "süresi biten kısıtlama kalkar");
        check(l2.distinctChecks(id, t0 + 200_000, k -> exp) == 2, "bağımsız hile türü sayısı");
        ViolationLedger l3 = new ViolationLedger();
        check(l3.load("bozuk satır\nV|not-a-uuid|x|y|1\nV|" + id + "|Ali|Fly|abc\n") == 3, "bozuk kayıtlar atlanır, çökmez");

        Jesus();
        bansAndAim();
    }

    static void bansAndAim() {
        UUID id = UUID.randomUUID(); long now = 5_000_000L, until = now + 7L * 86_400_000L;
        ViolationLedger l = new ViolationLedger();
        check(l.banUntil(id, now) == 0, "ban yokken banUntil=0");
        l.setBan(id, "Ahmet", until, "3/3");
        check(l.banUntil(id, now) == until, "7 günlük ban kaydedildi");
        ViolationLedger l2 = new ViolationLedger(); l2.load(l.serialize(now, 1L << 40));
        check(l2.banUntil(id, now + 1000) == until && l2.banReason(id).equals("3/3"), "ban sunucu yeniden başlatılınca korunur");
        check(l2.banUntil(id, until + 1) == 0, "süre bitince ban kalkar");
        check(l2.uuidByName("ahmet").equals(id), "isimden UUID bulma (unban için)");
        l2.unban(id);
        check(l2.banUntil(id, now) == 0, "unban");

        Random r = new Random(11);
        AimAssistCheck a = new AimAssistCheck(25, 1.2, 0.7); int n = 0;
        for (int i = 0; i < 2500; i++) if (a.onHit(Math.abs(r.nextGaussian()) * 4.0 + 0.5) != null) n++;
        check(n == 0, "AimAssist: insan nişanı (dağınık sapma) kanıt üretmiyor");
        a = new AimAssistCheck(25, 1.2, 0.7); n = 0;
        for (int i = 0; i < 100; i++) if (a.onHit(0.2 + r.nextDouble() * 0.4) != null) n++;
        check(n > 0, "AimAssist: hep merkeze kilitli nişan tespit");

        var box = List.of(Box.ofEntity(0, 0, 3, 0.6, 1.8));
        check(AimCheck.minAngle(0, 1.62, 0, 0f, 0f, box) < 15, "minAngle: hedefe bakarken küçük açı");
        check(AimCheck.minAngle(0, 1.62, 0, 180f, 0f, box) > 150, "minAngle: arkaya bakarken büyük açı");
    }

    static void Jesus() {
        JesusCheck j = new JesusCheck(12); int n = 0;
        for (int i = 0; i < 60; i++) if (j.onMove(true, 0.0, 0.2, false) != null) n++;
        check(n > 0, "Jesus: suyun üstünde yürüme tespit");
        j = new JesusCheck(12); n = 0;
        for (int i = 0; i < 200; i++) if (j.onMove(i % 8 != 0, i % 3 == 0 ? 0.0 : 0.3, 0.2, false) != null) n++;
        check(n == 0, "Jesus: zıplayarak/kesintili yüzey teması meşru");
        j = new JesusCheck(12); n = 0;
        for (int i = 0; i < 60; i++) if (j.onMove(true, 0.0, 0.2, true) != null) n++;
        check(n == 0, "Jesus: muaf oyuncu");
    }

    // ================= ELYTRA / AÇIK ALAN AURA / HOT MOD =================
    /** Vanilla elytra simülasyonu; boost=true iken roket (travel öncesi) uygulanır. @return hile türüne göre kanıt sayısı */
    static int elytraRun(int mode, boolean withBoost, long seed) {
        Random r = new Random(seed);
        double vx = 0, vy = 0, vz = 0.6; float yaw = 0, pitch = 10, ty = 0, tp = 10;
        ElytraCheck ec = new ElytraCheck(); int n = 0;
        for (int i = 0; i < 4000; i++) {
            if (i % 97 == 0) ty = r.nextInt(360) - 180;
            if (i % 53 == 0) tp = r.nextInt(100) - 30;
            yaw += (float) Math.max(-14, Math.min(14, Geometry.wrapDegrees(ty - yaw)));
            pitch += Math.max(-6, Math.min(6, tp - pitch));
            double[] look = Geometry.look(yaw, pitch);
            boolean boost = withBoost && (i % 200) < 40;
            if (boost) { vx += look[0] * 0.1 + (look[0] * 1.5 - vx) * 0.5; vy += look[1] * 0.1 + (look[1] * 1.5 - vy) * 0.5; vz += look[2] * 0.1 + (look[2] * 1.5 - vz) * 0.5; }
            double[] p = new double[3];
            ElytraCheck.predict(vx, vy, vz, pitch, look, p);
            double mul = mode == 2 ? 1.35 : 1.0;              // 2: hız çarpanı hilesi
            vx = p[0] * mul; vy = p[1] * mul; vz = p[2] * mul;
            if (mode == 1) {                                  // 1: ElytraPlus sabit hız (XZ 1.55, Y 0.47)
                double yr = Math.toRadians(yaw);
                vx = -Math.sin(yr) * 1.55; vz = Math.cos(yr) * 1.55; vy = 0.47;
            }
            if (ec.onMove(vx, vy, vz, yaw, pitch, boost, false, 50) != null) n++;
        }
        return n;
    }

    static void newChecks() {
        System.out.println("[Elytra / ElytraTarget]");
        int legit = 0; for (long sd = 1; sd <= 5; sd++) legit += elytraRun(0, false, sd);
        check(legit == 0, "vanilla elytra uçuşu (dalış/tırmanış/keskin dönüş) 20000 tik: 0 kanıt");
        legit = 0; for (long sd = 1; sd <= 5; sd++) legit += elytraRun(0, true, sd);
        check(legit == 0, "vanilla elytra + havai fişek boost'ları: 0 kanıt");
        check(elytraRun(1, false, 9) > 0, "ElytraPlus sabit hız (XZ 1.55, Y 0.47) tespit");
        check(elytraRun(2, false, 9) > 0, "elytra hız çarpanı 1.35x tespit");
        ElytraCheck lag = new ElytraCheck(); int ln = 0;
        for (int i = 0; i < 5; i++) lag.onMove(0, 0, 0.5, 0, 10, false, false, 50);
        if (lag.onMove(0, 0, 9.0, 0, 10, false, false, 900) != null) ln++;      // lag boşluğundan sonra büyük sıçrama
        check(ln == 0, "büyük lag boşluğundan sonraki ilk paket değerlendirilmez");

        ElytraHitCheck eh = new ElytraHitCheck(1.8, 6, 4); int en = 0; Random r = new Random(4);
        for (int i = 0; i < 600; i++) if (eh.onHit(true, false, 3 + r.nextDouble() * 8) != null) en++;
        check(en == 0, "elytra ile insan nişanı (3–11° sapma) kanıt üretmiyor");
        eh = new ElytraHitCheck(1.8, 6, 4); en = 0;
        for (int i = 0; i < 30; i++) if (eh.onHit(true, false, 0.3 + r.nextDouble() * 0.8) != null) en++;
        check(en > 0, "elytra ile hedef gözüne milimetrik kilit tespit");
        eh = new ElytraHitCheck(1.8, 6, 4); en = 0;
        for (int i = 0; i < 30; i++) if (eh.onHit(true, true, 20) != null) en++;
        check(en > 0, "elytra: dön–vur–geri dön (alternate look) tespit");
        eh = new ElytraHitCheck(1.8, 6, 4); en = 0;
        for (int i = 0; i < 30; i++) if (eh.onHit(false, true, 0.1) != null) en++;
        check(en == 0, "yerdeki oyuncu için ElytraTarget kontrolü çalışmaz");

        RocketCadenceCheck rc = new RocketCadenceCheck(6, 2.5); int rn = 0; long t = 0;
        for (int i = 0; i < 40; i++) { t += 31 + (i % 2); if (rc.onUse(t) != null) rn++; }
        check(rn > 0, "otomatik roket (~1.5 sn ritmik) tespit");
        rc = new RocketCadenceCheck(6, 2.5); rn = 0; t = 0;
        for (int i = 0; i < 300; i++) { t += 25 + r.nextInt(70); if (rc.onUse(t) != null) rn++; }
        check(rn == 0, "insan roket kullanımı (düzensiz) kanıt üretmiyor");

        System.out.println("[Açık alan Aura]");
        TrackingCheck tc = new TrackingCheck(60, 0.985, 2.0); int tn = 0;
        for (int i = 0; i < 6000; i++) if (tc.onTick(true, r.nextDouble() < 0.78, 3 + r.nextDouble() * 4) != null) tn++;
        check(tn == 0, "insan takibi (strafe eden hedefte %78 kros-hair) kanıt üretmiyor");
        tc = new TrackingCheck(60, 0.985, 2.0); tn = 0;
        for (int i = 0; i < 600; i++) if (tc.onTick(true, true, 4.0) != null) tn++;
        check(tn > 0, "strafe eden hedefte %100 kilit (Aura Track) tespit");
        tc = new TrackingCheck(60, 0.985, 2.0); tn = 0;
        for (int i = 0; i < 600; i++) if (tc.onTick(true, true, 0.2) != null) tn++;
        check(tn == 0, "düz kovalanan hedefte %100 kros-hair meşru (açısal hız ~0)");

        SnapShapeCheck ss = new SnapShapeCheck(35, 4, 100); int sn = 0; long tk = 0;
        for (int i = 0; i < 12; i++) {
            if (ss.onRotation(tk += 2, 0.3, 0.1) != null) sn++;
            if (ss.onRotation(tk += 1, 62, 5) != null) sn++;                 // izole snap
            if (ss.onRotation(tk += 1, 0.4, 0.2) != null) sn++;
            if (ss.onRotation(tk += 8, 0.3, 0.0) != null) sn++;
        }
        check(sn > 0, "izole rotasyon snap'leri (Aura Track/vuruş snap) tespit");
        ss = new SnapShapeCheck(35, 4, 100); sn = 0; tk = 0;
        double[][] flick = {{3, 1}, {14, 3}, {46, 6}, {31, 4}, {9, 2}, {2, 0.5}};   // insan flick'i: rampalı
        for (int i = 0; i < 60; i++) for (double[] f : flick) if (ss.onRotation(tk += 1, f[0], f[1]) != null) sn++;
        check(sn == 0, "insan flick'i (rampalı dönüş) kanıt üretmiyor");

        System.out.println("[Hot mod / NoSlow]");
        SpeedCheck n1 = new SpeedCheck(0.10, 2.2), n2 = new SpeedCheck(0.10, 2.2); n2.setHot(true);
        int first1 = -1, first2 = -1;
        for (int i = 1; i <= 8; i++) {
            if (first1 < 0 && n1.onMove(ms(i, 0, 0, 0.45, true)) != null) first1 = i;
            if (first2 < 0 && n2.onMove(ms(i, 0, 0, 0.45, true)) != null) first2 = i;
        }
        check(first2 > 0 && first2 < first1, "hot modda Speed daha erken yakalanır (normal=" + first1 + ". tik, hot=" + first2 + ". tik)");
        SpeedCheck n3 = new SpeedCheck(0.10, 2.2); n3.setHot(true); int hn = 0;
        for (VanillaSim sim = new VanillaSim(); sim.tick < 600; ) if (n3.onMove(sim.step(true, true, 1.0, 1.0)) != null) hn++;
        check(hn == 0, "hot modda bile meşru sprint+bhop kanıt üretmiyor");
        AirCheck a1 = new AirCheck(), a2 = new AirCheck(); a2.setHot(true); int h1 = -1, h2 = -1;
        a1.onMove(ms(0, 0, 0, 0.2, true)); a2.onMove(ms(0, 0, 0, 0.2, true));
        a1.onMove(ms(1, 0, 0.42, 0.2, false)); a2.onMove(ms(1, 0, 0.42, 0.2, false));
        for (int i = 2; i < 40; i++) {
            if (h1 < 0 && a1.onMove(ms(i, 0, 0, 0.2, false)) != null) h1 = i;
            if (h2 < 0 && a2.onMove(ms(i, 0, 0, 0.2, false)) != null) h2 = i;
        }
        check(h2 > 0 && h2 < h1, "hot modda Fly/Hover daha erken yakalanır");
        NoSlowCheck ns = new NoSlowCheck(0.12); int nsn = 0; double sp = 0.6;
        for (int i = 0; i < 40; i++) { sp *= 0.91; if (ns.onMove(withUsing(ms(i, 0, 0.0, sp, false), true)) != null) nsn++; }
        check(nsn == 0, "NoSlow: havada (bunny-hop momentumu) yemek yemek meşru");
    }

    // ================= SPEED / HIGHJUMP / NOSLOW / SCAFFOLD DOĞRULUĞU =================
    static int motionRun(long seed, int speedLevel, double mul, double add, int ticks) {
        Sim2D sim = new Sim2D(seed, speedLevel);
        MotionCheck mc = new MotionCheck(1.12, 0.02);
        int n = 0;
        for (int i = 0; i < ticks; i++) {
            if (i == 200) { sim.cheatMul = mul; sim.cheatAdd = add; }       // hile 200. tikten sonra açılır
            if (mc.onMove(sim.step(), 50, () -> false) != null) n++;
        }
        return n;
    }

    static void accuracy() {
        System.out.println("[Speed modeli – meşru çeşitlilik / hile]");
        int total = 0;
        for (int lvl = 0; lvl <= 2; lvl++) for (long sd = 1; sd <= 6; sd++) total += motionRun(sd * 31 + lvl, lvl, 1.0, 0.0, 20000);
        check(total == 0, "rastgele strafe/çapraz/zıplama/sprint/buz/hız efekti, 360 000 tik: 0 kanıt (" + total + ")");
        int c1 = 0, c2 = 0, c3 = 0;
        for (long sd = 1; sd <= 4; sd++) { c1 += motionRun(sd, 0, 1.25, 0.0, 600); c2 += motionRun(sd, 0, 1.0, 0.12, 600); c3 += motionRun(sd, 1, 1.12, 0.0, 600); }
        check(c1 > 0, "Speed 1.25x çarpan hilesi tespit");
        check(c2 > 0, "Speed +0.12 ivme (bhop/strafe hilesi) tespit");
        check(c3 > 0, "Speed %12 sinsi hız artışı (hız efektiyle) tespit");
        // durup kalkma / boşluktan sonra: ilk paket değerlendirilmez
        MotionCheck mg = new MotionCheck(1.12, 0.02); int gn = 0;
        for (int i = 0; i < 10; i++) mg.onMove(ms(i, -0.28, 0, 0, true), 50, () -> false);
        if (mg.onMove(ms(11, 0.13, 0, 0, true), 400, () -> false) != null) gn++;   // 400ms boşluk sonrası ters yönlü kalkış
        check(gn == 0, "uzun boşluktan sonra ters yönlü kalkış değerlendirilmez");

        System.out.println("[HighJump]");
        AirCheck ac = new AirCheck(); int hn = 0;
        for (int i = 0; i < 20; i++) ac.onMove(ms(i, 0, 0, 0.2, true));
        if (ac.onMove(ms(20, 0, 0.6, 0.2, false)) != null) hn++;               // basamak/slab çıkışı kalkış tiki (probe şaşırırsa)
        for (int i = 21; i < 60; i++) ac.onMove(ms(i, 0, 0, 0.0, true));
        check(hn == 0, "kalkış tikinde 0.6 basamak çıkışı HighJump sayılmaz");
        ac = new AirCheck(); hn = 0;
        for (int i = 0; i < 20; i++) ac.onMove(ms(i, 0, 0, 0.2, true));
        for (int i = 20; i < 22; i++) if (ac.onMove(ms(i, 0, i == 20 ? 0.42 : 0.33, 0.2, false)) != null) hn++;
        check(hn == 0, "normal zıplama HighJump sayılmaz");
        ac = new AirCheck(); hn = 0;
        for (int i = 0; i < 20; i++) ac.onMove(ms(i, 0, 0, 0.2, true));
        double dy = 0.9; if (ac.onMove(ms(20, 0, dy, 0.2, false)) != null) hn++;
        for (int i = 21; i < 40; i++) { dy = (dy - 0.08) * 0.98; if (ac.onMove(ms(i, 0, dy, 0.2, false)) != null) hn++; }
        check(hn > 0, "0.9 kalkış hızı (HighJump) tespit");
        ac = new AirCheck(); hn = 0;
        for (int i = 0; i < 20; i++) ac.onMove(ms(i, 0, 0, 0.2, true));
        dy = 0.42; ac.onMove(ms(20, 0, dy, 0.2, false));
        for (int i = 21; i < 120; i++) {                       // yükselip alçalan zıp-zıp: net yükseklik 1.32'yi aşmamalı
            dy = (dy - 0.08) * 0.98; if (ac.onMove(ms(i, 0, dy, 0.2, i > 30 && dy < -0.5)) != null) hn++;
        }
        check(hn == 0, "net yükseklik: meşru zıplama + düşüş yanlış yükselme sayılmaz");

        System.out.println("[NoSlow]");
        NoSlowCheck ns = new NoSlowCheck(0.12); int nn = 0;
        for (int i = 0; i < 40; i++) {
            MoveSample m = new MoveSample(i, 0, 0, 0.30, 0, 0, true, true, false, false, false, false, false, 1.0, 0, true, 0, 0.6, true, 0.13);
            if (ns.onMove(m) != null) nn++;
        }
        check(nn > 0, "eşya kullanırken sprint + hız (NoSlow) tespit");
        ns = new NoSlowCheck(0.12); nn = 0;
        for (int i = 0; i < 80; i++) {
            MoveSample m = new MoveSample(i, 0, 0, 0.045, 0, 0, true, true, false, false, false, false, false, 1.0, 0, true, 0, 0.6, false, 0.1);
            if (ns.onMove(m) != null) nn++;
        }
        check(nn == 0, "meşru yavaş yürüyerek yemek yemek kanıt üretmiyor");

        System.out.println("[Scaffold geometri]");
        // A bloğu [0,1]^3. Oyuncu A'nın üstünde, +z kenarında, A'nın +z yan yüzüne tıklıyor (köprü kurma).
        double fx = 0.5, fy = 0.5, fz = 1.0;
        double ex = 0.5, ey = 2.62, ez = 1.15;
        double[] dir = {fx - ex, fy - ey, fz - ez}; double L = Math.sqrt(dir[0]*dir[0] + dir[1]*dir[1] + dir[2]*dir[2]);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir[0], dir[2])), pitch = (float) -Math.toDegrees(Math.asin(dir[1] / L));
        check(ScaffoldMath.rayFaceError(ex, ey, ez, yaw, pitch, fx, fy, fz, 0, 0, 1) < 0.05, "tam nişan: hata ~0");
        check(ScaffoldMath.rayFaceError(ex, ey - 0.35, ez, yaw, pitch, fx, fy, fz, 0, 0, 1) < 0.6,
                "eğilme geçişinde göz 0.35 oynasa da köprü kurma meşru (hata < 0.6)");
        check(ScaffoldMath.rayFaceError(ex, ey, ez, 0f, 0f, fx, fy, fz, 0, 0, 1) > 5, "bakmadan yerleştirme (yatay bakış) tespit");
        check(ScaffoldMath.rayFaceError(ex, ey, ez, 140f, 70f, fx, fy, fz, 0, 0, 1) > 0.9, "tamamen başka yöne bakarak yerleştirme tespit");
    }
}
