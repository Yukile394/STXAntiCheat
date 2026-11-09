package dev.stx.anticheat.core;

import dev.stx.anticheat.core.Geometry.Box;
import java.util.ArrayList;
import java.util.List;

/**
 * Duvar arkası (görüş hattı) doğrulaması. Bukkit'ten bağımsızdır; blok sorgusu {@link Blocker} ile verilir.
 *
 * <p>Amaç: "hedef gerçekten duvarın arkasında mı?" sorusuna YANLIŞ-POZİTİFSİZ cevap vermek. Hedefe giden
 * <b>en az bir</b> temiz görüş hattı bulunursa vuruş meşrudur. Hat şu kaynaklardan aranır:
 * <ol>
 *   <li>Oyuncunun gerçek bakış ışını (istemci vuruşu tam olarak böyle seçer): hitbox'ın duvarın kenarından
 *       azıcık görünen köşesine nişan almak da meşrudur.</li>
 *   <li>Hitbox üzerinde yoğun örnek noktaları: orta hat + 4 dikey köşe, 4 yükseklikte (ayak, gövde, baş, baş üstü).
 *       Üstü açık duvarlarda yalnızca kafanın/omzun görünmesi de sayılır.</li>
 * </ol>
 * Bunlar birden fazla göz konumundan (şimdiki göz, eğilme/ayakta farkı için yükseltilmiş göz, son birkaç
 * tikteki göz konumları) ve hedefin tüm ping-geçmişi kutuları için denenir.
 */
public final class WallVision {
    /** Göz noktasından hedef noktaya giden doğru bir bloğa çarpıyor mu? (iki uç da dünya koordinatı) */
    public interface Blocker {
        boolean blocked(double ox, double oy, double oz, double tx, double ty, double tz);
    }

    /** Bu mesafeden yakın noktalar her zaman görünür sayılır (duvara yaslanmış oyuncu). */
    private static final double NEAR = 0.8;

    private WallVision() {}

    /**
     * @param eyes      denenecek göz konumları ({x,y,z}); ilki şimdiki göz
     * @param look      birim bakış vektörü
     * @param boxes     hedefin güncel + ping-geçmişi kutuları
     * @param expand    hitbox'a eklenen pay (blok)
     * @param lookRange bakış ışınının azami uzunluğu
     * @param maxRays   bütçe: aşılırsa şüpheden sanık yararlanır (görünür sayılır)
     * @return true: hedefe temiz görüş var (duvar arkası DEĞİL); false: her yol kapalı
     */
    public static boolean clearView(Blocker bl, List<double[]> eyes, double[] look, List<Box> boxes,
                                    double expand, double lookRange, int maxRays) {
        List<Box> uniq = dedupe(boxes, 0.04, 8);
        int[] budget = {maxRays};

        // 1) Gerçek bakış ışını hitbox'ı (genişletilmiş) deliyor ve o noktaya kadar blok yok
        for (double[] e : eyes) {
            for (Box b : uniq) {
                double t = Geometry.rayBox(e[0], e[1], e[2], look[0], look[1], look[2], b.expand(expand), lookRange);
                if (Double.isNaN(t)) continue;
                if (!blocked(bl, e, e[0] + look[0] * t, e[1] + look[1] * t, e[2] + look[2] * t, budget)) return true;
            }
        }
        // 2) Hitbox üzerindeki örnek noktalarından en az birine temiz hat
        for (double[] e : eyes) {
            for (Box b : uniq) {
                for (double[] p : samplePoints(b, expand)) {
                    if (!blocked(bl, e, p[0], p[1], p[2], budget)) return true;
                }
            }
        }
        return false;
    }

    private static boolean blocked(Blocker bl, double[] e, double tx, double ty, double tz, int[] budget) {
        if (budget[0]-- <= 0) return false;                         // bütçe bitti: şüpheden sanık yararlanır
        double dx = tx - e[0], dy = ty - e[1], dz = tz - e[2];
        if (dx * dx + dy * dy + dz * dz < NEAR * NEAR) return false;
        return bl.blocked(e[0], e[1], e[2], tx, ty, tz);
    }

    /** Orta hat + 4 dikey köşe; ayak, gövde, baş, baş üstü yüksekliklerinde. Önce gövde/baş (en olası) denenir. */
    static List<double[]> samplePoints(Box b, double expand) {
        double cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
        double x0 = b.minX() - expand, x1 = b.maxX() + expand, z0 = b.minZ() - expand, z1 = b.maxZ() + expand;
        double h = b.maxY() - b.minY();
        double[] ys = {
                b.minY() + h * 0.5,
                b.maxY() - Math.min(0.05, h * 0.05),
                b.maxY() + Math.min(0.05, expand * 0.5),
                b.minY() + Math.min(0.15, h * 0.1)};
        List<double[]> out = new ArrayList<>(ys.length * 5);
        for (double y : ys) {
            out.add(new double[]{cx, y, cz});
            out.add(new double[]{x0, y, z0});
            out.add(new double[]{x0, y, z1});
            out.add(new double[]{x1, y, z0});
            out.add(new double[]{x1, y, z1});
        }
        return out;
    }

    /** Birbirinin aynısı (hedef duruyorsa geçmiş kutuları aynıdır) kutuları eler; en fazla {@code max} kutu bırakır. */
    static List<Box> dedupe(List<Box> in, double eps, int max) {
        List<Box> out = new ArrayList<>();
        for (Box b : in) {
            boolean dup = false;
            for (Box o : out) {
                if (Math.abs(o.minX() - b.minX()) < eps && Math.abs(o.minY() - b.minY()) < eps
                        && Math.abs(o.minZ() - b.minZ()) < eps) { dup = true; break; }
            }
            if (!dup) out.add(b);
        }
        if (out.size() <= max) return out;
        List<Box> pick = new ArrayList<>(max);
        for (int i = 0; i < max; i++) pick.add(out.get((int) Math.round(i * (out.size() - 1) / (double) (max - 1))));
        return pick;
    }
}
