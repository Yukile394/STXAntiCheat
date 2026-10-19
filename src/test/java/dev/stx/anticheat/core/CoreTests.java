package dev.stx.anticheat.core;

import dev.stx.anticheat.core.Geometry.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    // ================= POLİTİKA =================
    static void policy() {
        System.out.println("[Şüphe ve ceza politikası]");
        PunishPolicy p = new PunishPolicy(new PunishPolicy.Thresholds(10, 25, 50, 2, 8, 60, 90));
        Suspicion s = new Suspicion(0.2);
        for (int i = 0; i < 12; i++) s.add("Speed", 1, 0);
        check(p.decide(s, 0) == PunishPolicy.Action.ALERT, "tek kontrol 12 puan -> yalnızca ALERT");
        for (int i = 0; i < 40; i++) s.add("Speed", 1, 0);
        check(p.decide(s, 0) == PunishPolicy.Action.ALERT, "tek kontrol 52 puan -> hâlâ KICK/BAN yok (bağımsız kanıt yok)");
        for (int i = 0; i < 10; i++) s.add("Fly", 1, 0);
        check(p.decide(s, 0) == PunishPolicy.Action.BAN, "iki bağımsız kontrol, yüksek toplam -> BAN eşiği");
        Suspicion s2 = new Suspicion(0.2);
        for (int i = 0; i < 28; i++) s2.add("Reach", 1, 0);
        for (int i = 0; i < 2; i++) s2.add("Aim", 1, 0);
        check(p.decide(s2, 0) == PunishPolicy.Action.ALERT, "30 puan ama yalnızca 1 kontrol >=8: KICK yok");
        for (int i = 0; i < 6; i++) s2.add("Aim", 1, 0);
        check(p.decide(s2, 0) == PunishPolicy.Action.KICK, "iki bağımsız kontrol + 25+ puan -> KICK");
        check(p.decide(s2, 600_000) == PunishPolicy.Action.NONE, "10 dk sonra puanlar sönmüş");
    }
}
