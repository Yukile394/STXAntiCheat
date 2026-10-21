package dev.stx.anticheat.core;

/** Scaffold doğrulaması için saf geometri. */
public final class ScaffoldMath {
    private ScaffoldMath() {}

    /**
     * Bakış ışınının, tıklanan blok yüzünün (1x1 kare) düzlemini kestiği noktanın kareye uzaklığı (blok).
     * Kare üzerinde = 0. Işın yüzden uzağa bakıyorsa veya 7 bloktan uzaksa büyük bir değer (99) döner.
     * Göz yüksekliği/eğilme geçişi gibi küçük sapmalar küçük hata üretir; bakmadan yerleştirme büyük hata üretir.
     */
    public static double rayFaceError(double ex, double ey, double ez, float yaw, float pitch,
                                      double fx, double fy, double fz, int nx, int ny, int nz) {
        double[] look = Geometry.look(yaw, pitch);
        double denom = look[0] * nx + look[1] * ny + look[2] * nz;
        if (denom >= -1e-6) return 99;                                   // yüzden uzağa bakıyor
        double t = ((fx - ex) * nx + (fy - ey) * ny + (fz - ez) * nz) / denom;
        if (t < 0 || t > 7.0) return 99;
        double[] hit = {ex + t * look[0], ey + t * look[1], ez + t * look[2]};
        double[] f = {fx, fy, fz};
        int[] n = {nx, ny, nz};
        double sq = 0;
        for (int a = 0; a < 3; a++) {
            if (n[a] != 0) continue;
            double over = Math.max(0, Math.abs(hit[a] - f[a]) - 0.5);
            sq += over * over;
        }
        return Math.sqrt(sq);
    }
}
