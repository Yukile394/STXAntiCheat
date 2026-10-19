# STXAntiCheat

Paper **1.21+** için kanıt tabanlı, düşük yanlış-pozitifli AntiCheat. Bukkit/Paper API kullanır (NMS yok),
bu yüzden 1.21.x sürümleri arasında yeniden derleme gerektirmesi beklenmez.

## Derleme
Yerelde (Java 21 gerekir; Gradle ayrıca kurmanız gerekmez, `gradlew` 8.10'u kendisi indirir):
```
./gradlew build         # build/libs/STXAntiCheat-1.0.0.jar  (+ 96 çekirdek test)
./gradlew coreTests     # yalnızca tespit mantığı testleri
```
Windows'ta `gradlew.bat build`. JAR'ı sunucunun `plugins/` klasörüne atın.

### GitHub Actions
`.github/workflows/build.yml` her push/PR'da: wrapper doğrulama → JDK 21 → `./gradlew build` (testler dahil) → jar'ı
**Actions > Artifacts > STXAntiCheat** olarak yükler. `v1.0.0` gibi bir etiket push'larsanız jar otomatik **GitHub Release** olur:
```
git tag v1.0.0 && git push origin v1.0.0
```
Windows'tan yüklerseniz `gradlew` çalıştırma biti kaybolabilir; workflow `chmod +x gradlew` ile bunu zaten düzeltir
(yerelde için: `git update-index --chmod=+x gradlew`).

## Kademeli müdahale sistemi
Her kontrol **kendi puanını** tutar. Puan `confirm-points` altındaysa (**LOW**) yalnızca kayıt tutulur. Üstüne çıkarsa
bir **ihlal** sayılır (aynı hile için en fazla dakikada bir; UUID bazlı, `plugins/STXAntiCheat/data/violations.dat` içinde
kalıcı, 72 saat sonra düşer):

| İhlal | Müdahale | Mesaj |
|---|---|---|
| 1 | geçici (varsayılan 15 sn) hileye özel engel | `§e§lAntiCheat §8» §eŞüpheli hareketlerin algılandı! 15 saniye boyunca saldırıların engellendi. §7(1/3)` |
| 2 | daha uzun (30 sn) kısıtlama | `§e§lAntiCheat §8» §6Hile şüphesi devam ediyor! Hareketlerin kısıtlandı. §7(2/3)` |
| 3 | **BAN** – yalnızca ban-uygun hile + HIGH kanıt + ≥2 bağımsız hile türü + kilit açıkken | `§c§lAntiCheat §8» §cBirden fazla hile ihlali tespit edildi. Sunucudan yasaklandın. §7(3/3)` |

Hileye özel müdahale (`checks.<Ad>.mitigation`): **ATTACK** (vuruşlar iptal) · **MOVE** (son temiz konuma geri alma +
kısıt süresince doğrulanmış her yeni kanıtta tekrar geri alma) · **PLACE** (blok koyma iptal) · **CONTAINER** (sandık
işlemleri iptal) · **NONE** (yalnızca kayıt). Reach'te ayrıca ulaşılamayan vuruş ihlal sayısından bağımsız anında iptal edilir.
Her hile için ayrı `confirm-points`, `strong-points`, `violations-to-ban`, süreler ve `ban-eligible` ayarlanabilir.
AutoClicker, Criticals, ChestStealer, GuiMove, Velocity, Rotation **ban-uygun değildir** (en fazla geçici kısıt).

Çıkıp girme: girişte tüm kontroller sıfırdan kurulur (`reinit`), ihlaller UUID'den, bitmemiş kısıtlamalar defterden
geri yüklenir (relog ile kaçış yok). Dünya değişimi, respawn, teleport ve kendi geri-ışınlamamız için ayrı kısa tolerans vardır.
Defter periyodik (asenkron, atomik yazım), çıkışta ve kapanışta kaydedilir.

## Güvenli kullanım (önerilen sıra)
1. Varsayılan: geçici müdahaleler **açık**, kalıcı ban **kilitli** (`sanctions.ban-enabled: false`). 3/3'e ulaşan oyuncu
   atılmaz/banlanmaz; `WOULD_BAN` olarak loglanır ve OP'lere bildirilir.
2. `logs/stx-*.jsonl` içindeki `VIOLATION_*` ve `WOULD_BAN` kayıtlarını inceleyin. Hatalı kısıtlanan oyuncu için `/stx reset <oyuncu>`.
3. Yanlış pozitif görmüyorsanız `sanctions.ban-enabled: true` **ve** `sanctions.i-have-reviewed-the-logs: true` yapın.
   İkisi birden açık olmadan hiçbir ban çalışmaz. Geçici müdahaleyi kapatmak için `sanctions.mitigation-enabled: false`.

## OP bildirimleri
Oyuncu **atılmadığında** (ceza kapalıyken) tespitler OP'lere görünür (`alerts:` bölümü):
- OP olan oyuncular `stx.alerts` izni olmasa da her kanıtı sohbette görür (`ops-see-all-evidence`).
- Ceza eşiği aşılıp oyuncu atılmadıysa OP'lere "eşik aşıldı ama ceza KAPALI, atılmadı" mesajı gider.
- Kişisel kapatma: `/stx alerts`.

## Yanlış pozitif koruması
- **Lag**: TPS < 18, ping > 400 ms veya ani ping sıçramasında kanıt toplanmaz.
- **Muafiyetler**: creative/spectator, uçuş izni, elytra, araç, riptide, teleport/giriş/respawn/dünya/mod değişimi
  sonrası tolerans, sunucu hız verdikten (knockback, patlama, rüzgar topu) sonra tolerans.
- **Ortam**: sıvı, tırmanılabilir blok, ağ, bal/slime/yatak, piston, buz → ilgili kontroller atlanır.
- **Tampon**: tek bir şüpheli olay kanıt üretmez; her kontrol kendi `FlagBuffer`'ında birikir.
- **Çoklu bağımsız kanıt**: ceza için toplam puan **ve** en az 2 farklı kontrolde yeterli puan gerekir.
- **Eşikler ölçüldü**: Speed/Fly limitleri, bunny-hop dahil vanilla fizik simülasyonundan türetildi.
- Bedrock (Geyser) oyuncularında rotasyon/aim/clicker kontrolleri kapalı (`bedrock-prefix`).
- Plugin GUI menüleri ChestStealer kapsamı dışındadır (yalnızca gerçek blok konteynerleri).

## Hile → kontrol eşlemesi (ThunderHack + Wetsuki analizi)
| İstemci modülü | Kontrol | Yöntem |
|---|---|---|
| Aura (Track/rotasyonsuz), TriggerBot, AimBot | Aim, Aura, Rotation, AutoClicker | bakış ışını–hitbox, çoklu hedef, snap, duvar, fare GCD, vuruş ritmi |
| Reach, HitBox | Reach, Aim | göz–kutu mesafesi (ping penceresindeki hedef geçmişiyle), genişletilmiş kutu ıskalama |
| Criticals | Criticals | vuruş öncesi zeminden mikro-zıplama (0.0625 vb.) |
| Velocity / AntiKnockback (Wetsuki "Cancel") | Velocity | verilen hıza karşı gerçek yer değiştirme oranı |
| Flight, HighJump, Step, Spider(dolaylı), Strafe-Elytra yok | Fly, HighJump, Step | yerçekimi modeli, havada sabitlik, yükselme sınırı |
| Speed, LongJump (dolaylı) | Speed | anlık + 20 tik ortalama limit |
| NoFall (Rubberband/sahte onGround) | NoFall | istemci onGround iddiası ↔ sunucu çarpışması, fallDistance sapması |
| Timer, TickShift, Blink (kısmi) | Timer | paket zamanlaması kredi modeli |
| NoSlow | NoSlow | eşya kullanırken hız |
| Scaffold, Tower | Scaffold | imkansız yüz, bakış–blok ışını, yerleştirme hızı |
| ChestStealer, AutoTotem (kısmi), GuiMove/InvMove | ChestStealer, GuiMove | açılış sonrası ilk tık, ritim, konteyner açıkken yürüme |
| Phase | Phase | önceki konum serbest, yeni konum katı blok içinde |
| Jesus | Jesus | sıvı yüzeyinde (içinde değil) düz yatay ilerleme |
| Spider | Spider | yukarı yönlü yerçekimi sapması + duvar yanında |

## Bilinen sınırlar (dürüst liste)
- **Paket katmanı yok.** Yalnızca Bukkit olayları kullanılır. Sonuçları:
  alt-tik tıklama zamanlaması güvenilmez (bu yüzden tik bazlı + geniş örneklem), `AirStuck`/`Blink`/`TickShift`
  yalnızca Timer üzerinden dolaylı ve zayıf yakalanır, `onGround` bayrağına bağımlı kontroller sunucu çarpışma
  sorgusuyla desteklenir.
- **Kapsam dışı / zayıf:** FastUse, SpeedMine, NoJumpDelay,
  Invsee/Region exploit'leri (istemci dosyalarıyla çalışır, sunucudan görünmez), EnderChest "sahte kapama".
  `ClickTP` gibi >10 blokluk tek adımlar şüphe değil **tolerans** sayılır (başka plugin'in teleportunu cezalandırmamak için).
- Reach yaklaşık **≥ 3.4–3.5** hilelerini yakalar; 3.1–3.3 gibi küçük uzatmalar bilinçli olarak geçer.
- Rotation (GCD) kontrolü sinematik kamera/özel istemcilerde yanlış pozitif verebilir; bu yüzden düşük ağırlıklıdır.
- Folia desteklenmez.

## Test durumu
- Çekirdek mantık (`core/`): **96 otomatik test geçiyor** (ihlal defteri kalıcılığı/relog, ceza basamakları ve ban kilitleri, Jesus dahil); meşru yürüme/sprint/bunny-hop/düşme/knockback/lag
  senaryolarında 0 kanıt, hile senaryolarında tespit.
- Paper adaptörü (`bukkit/`): gerçek Paper API'si ile **derlenmedi ve canlı sunucuda denenmedi**
  (geliştirme ortamında API indirilemedi; yalnızca kendi stub'larımla iç tutarlılık denendi). İlk `./gradlew build`
  derleme hatası verirse çıktıyı paylaşın, düzeltilir.
- **Test EDİLMEYENLER:** geri-ışınlama, vuruş/blok/konteyner iptali, relog'da kısıt geri yükleme ve oyuncu mesajları
  Paper adaptöründe yer aldığı için yalnızca derleme düzeyinde (stub) kontrol edildi; canlıda doğrulanmalıdır.
- Gerçek hile istemcisiyle test yapılmadı; eşikler fizik simülasyonuna dayanır. Canlıda log-only modda doğrulayın.

## Komutlar
`/stx alerts` · `/stx status [oyuncu]` · `/stx exempt <oyuncu> [dk]` · `/stx reset <oyuncu>` · `/stx reload`
İzinler: `stx.admin`, `stx.alerts`, `stx.bypass`.
