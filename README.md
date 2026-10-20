# STXAntiCheat

Paper **1.21+** için kanıt tabanlı, düşük yanlış-pozitifli AntiCheat. Bukkit/Paper API kullanır (NMS yok),
bu yüzden 1.21.x sürümleri arasında yeniden derleme gerektirmesi beklenmez.

## Derleme
Yerelde (Java 21 gerekir; Gradle ayrıca kurmanız gerekmez, `gradlew` 8.10'u kendisi indirir):
```
./gradlew build         # build/libs/STXAntiCheat-1.0.0.jar  (+ 126 çekirdek test)
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

## Müdahale sistemi
**1) Anında engel (log düştüğü an):** bir kontrol kanıt kaydedince hileye özel kısa engel devreye girer (ihlal sayacından bağımsız):
- Saldırı hileleri (Aura/Reach/Aim): vuruşlar 3 sn iptal. **Duvar arkası vuruş ve bakmadan (≥70° sapma) vuruş her seferinde anında iptal edilir.**
- Hareket hileleri (Fly/Speed/Step/HighJump/Jesus/Spider/Phase/NoFall/NoSlow): son temiz konuma geri alma + 2 sn **yerinde dondurma**
  (yatay hareket ve yukarı çıkış engellenir; düşüş ve bakış serbest; sıvı/lav/araç/elytra içindekiler tuzağa düşmesin diye serbest).
- Scaffold: blok koyma, ChestStealer: sandık işlemleri iptal.

**2) Kalıcı ihlal (UUID bazlı, `data/violations.dat`):** puan `confirm-points`'i aşarsa ihlal sayılır (aynı hile için en fazla 30 sn'de bir):

| İhlal | Müdahale | Mesaj |
|---|---|---|
| 1 | 15 sn hileye özel engel | `§e§lAntiCheat §8» §eŞüpheli hareketlerin algılandı! 15 saniye boyunca saldırıların engellendi. §7(1/3)` |
| 2 | 30 sn kısıtlama | `§e§lAntiCheat §8» §6Hile şüphesi devam ediyor! Hareketlerin kısıtlandı. §7(2/3)` |
| 3 | **7 gün BAN** (ban-uygun hile + güçlü kanıt) | `§c§lAntiCheat §8» §cBirden fazla hile ihlali tespit edildi. Sunucudan yasaklandın. §7(3/3)` |

Ban eklentinin kendi UUID tabanlı listesindedir (sunucu ban listesine bağımlı değil, sürümden bağımsız): oyuncu giriş yapamaz,
kalan süre gösterilir, sunucu yeniden başlasa da sürer. Kaldırmak için `/stx unban <oyuncu>`. Süre ve açma/kapama: `sanctions.ban-*`.
AutoClicker, Criticals, ChestStealer, GuiMove, Velocity, Rotation, AimAssist **ban-uygun değildir** (en fazla geçici kısıt).

Çıkıp girme: girişte kontroller sıfırdan kurulur, ihlaller ve bitmemiş kısıtlar defterden geri yüklenir. Dünya değişimi,
respawn, teleport ve kendi geri-ışınlamamız için ayrı kısa tolerans vardır. Defter periyodik (asenkron, atomik) ve kapanışta kaydedilir.

## Hot mod (hile açıkken sürekli engel)
Bir hareket hilesi (Fly/Speed/Step/Jesus/NoSlow/...) yakalanınca oyuncu **30 sn "hot" moda** girer: tüm hareket kontrollerinin
tamponu ~%66 küçülür, Speed toleransı daralır; hot iken her yeni kanıt anında son temiz konuma geri alma + 3 sn dondurma getirir.
Böylece hile açık kaldıkça sürekli geri alınır, "engellendikten sonra 3-4 sn kullanım" kalmaz. Meşru oyuncu hot modda da
yanlış pozitif vermez (testle doğrulandı). NoSlow yalnızca yerde değerlendirilir (havadaki bunny-hop momentumu meşru).

## Elytra / ElytraTarget
- **ElytraSpeed:** vanilla elytra formülü (önceki hız + bakış → sonraki hız) ile her tik tahmin; fazlalık tek yönlü ve birikimli sayılır
  (ElytraPlus sabit hız/boost/çarpan). Roket (havai fişek) pencereleri ayrı: hız tavanı + hız yönü bakışa yakınsamalı. Lag boşluğu,
  knockback ve sıvıda değerlendirme yapılmaz. Vanilla simülasyonunda 20 000+ tikte 0 yanlış pozitif.
- **ElytraTarget:** elytra ile vuruşlarda hedef gözüne/merkezine milimetrik kilit, "ileri bak–vuruş tikinde snap–geri dön" ve savaşta
  makine ritminde otomatik roket tespiti.
- **Ceza: 1/2 → elytra kapatılır (uçuş bitirilir, 60 sn tekrar başlatılamaz) + saldırı engeli; 2/2 → doğrudan 7 gün ban.**
  (`checks.ElytraSpeed/ElytraTarget` altında ayarlanır.)

## Aura / saldırı tespiti
Her vuruşta: (1) hedefin ping geçmişindeki **tüm** konumlarına 5 noktalı duvar ışını, (2) bakış–hedef açısı, (3) Reach, (4) bakış ışını–hitbox,
(5) AimAssist (nişanın hedef merkezine fazla kilitli/tutarlı olması), (6) çoklu hedef, keskin dönüş ve **sessiz rotasyon** (dön–vur–geri dön).
**Açık alan:** (7) strafe eden oyuncu hedefte kros-hair'in hitbox üzerinde kalma oranı (Aura Track ~%100, insan çok daha düşük;
düz kovalamada sayılmaz), (8) **rotasyon şekli**: tek tikte büyük izole snap (insan dönüşü rampalıdır). Bu ikisi Aura'nın GCD
düzeltmesinden (gcdFix) etkilenmez. Elytra'dayken savaş kontrolleri muaf tutulmaz.
Duvar ve açı kontrolleri TPS düşüşünde kapanmaz (yalnızca muafiyet ve ping > 700 ms'de atlanır).

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
| Aura (Track/rotasyonsuz/sessiz), TriggerBot, AimBot | Aim, Aura, AimAssist, Rotation, AutoClicker | bakış ışını–hitbox, çoklu hedef, snap, duvar, fare GCD, vuruş ritmi |
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

## Bataklık/çamur yanlış pozitifi (düzeltildi)
Zemin sorgusu bloğu "tam küp" sanıyordu; çamur (0.875), ruh kumu, tarla ve patika tam küp değildir, bu yüzden oyuncu
bunların üstündeyken "havada" (Fly/Hover/NoFall) veya blok "içinde" (Phase) görünüyordu. Artık her zaman gerçek çarpışma şekli kullanılır.
Bu düzeltme koddan türetildi; gerçek sunucuda bataklıkta doğrulayın.

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
- Çekirdek mantık (`core/`): **126 otomatik test geçiyor** (ihlal defteri kalıcılığı/relog, ceza basamakları ve ban kilitleri, Jesus dahil); meşru yürüme/sprint/bunny-hop/düşme/knockback/lag
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
