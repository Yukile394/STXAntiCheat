package dev.stx.anticheat.core;

/**
 * Bir hareket paketinin Bukkit'ten bağımsız özeti. Çevresel durumlar (sıvı, sarmaşık, buz vb.)
 * adapter katmanında hesaplanıp bayrak olarak gelir; kontroller yalnızca matematikle ilgilenir.
 *
 * @param clientGround istemcinin bildirdiği onGround (yalnızca NoFall karşılaştırması için; güvenilmez)
 * @param standing     SUNUCUNUN çarpışma sorgusuna göre oyuncu bir bloğun üstünde duruyor mu (güvenilir)
 * @param exempt       sert muafiyet (creative, uçuş, elytra, araç, teleport/giriş toleransı, lag...)
 * @param velocity     sunucu az önce hız verdi (knockback, patlama, rüzgar topu...)
 * @param special      sıvı, tırmanılabilir blok, ağ, bal/slime, piston vb. özel zemin/ortam
 * @param gravityOff   levitation / yavaş düşme gibi yerçekimini değiştiren etki
 * @param ice          buz benzeri kaygan zemin (yakın geçmiş dahil)
 * @param speedMul     hız çarpanı (efekt + yürüme hızı)
 * @param jumpLevel    Zıplama Güçlendirme seviyesi (0 = yok)
 * @param usingItem    yemek/yay/kalkan kullanıyor
 * @param serverFall   sunucunun tuttuğu fallDistance
 * @param slip         ayak altı bloğun sürtünmesi (normal 0.6, buz 0.98, mavi buz 0.989)
 * @param sprinting    sunucu oyuncuyu koşuyor sayıyor
 * @param attr         hareket hızı özniteliği değeri (yürüme 0.1, sprint 0.13, efektler dahil)
 */
public record MoveSample(long tick, double dx, double dy, double dz, float yaw, float pitch,
                         boolean clientGround, boolean standing, boolean exempt, boolean velocity, boolean special,
                         boolean gravityOff, boolean ice, double speedMul, int jumpLevel,
                         boolean usingItem, double serverFall,
                         double slip, boolean sprinting, double attr) {
    /** Eski kurucu: varsayılanlar (normal zemin, sprint yok, hız özniteliği 0.1). */
    public MoveSample(long tick, double dx, double dy, double dz, float yaw, float pitch, boolean clientGround, boolean standing,
                      boolean exempt, boolean velocity, boolean special, boolean gravityOff, boolean ice, double speedMul,
                      int jumpLevel, boolean usingItem, double serverFall) {
        this(tick, dx, dy, dz, yaw, pitch, clientGround, standing, exempt, velocity, special, gravityOff, ice, speedMul,
                jumpLevel, usingItem, serverFall, 0.6, false, 0.1);
    }

    public double hSpeed() { return Math.hypot(dx, dz); }
}
