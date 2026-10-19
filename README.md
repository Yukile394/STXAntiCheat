# STXAntiCheat

Paper **1.21+** için kanıt tabanlı, düşük yanlış-pozitifli AntiCheat. Bukkit/Paper API kullanır (NMS yok),
bu yüzden 1.21.x sürümleri arasında yeniden derleme gerektirmesi beklenmez.

## Derleme
Yerelde (Java 21 gerekir; Gradle ayrıca kurmanız gerekmez, `gradlew` 8.10'u kendisi indirir):
```
./gradlew build         # build/libs/STXAntiCheat-1.0.0.jar  (+ 80 çekirdek test)
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

## Güvenli kullanım (önerilen sıra)
1. Varsayılan config ile açın: **ceza KAPALI**, yalnızca kayıt + staff uyarısı (`/stx alerts`).
2. `plugins/STXAntiCheat/logs/stx-*.jsonl` dosyalarını birkaç gün inceleyin. Her kayıtta oyuncu, kontrol, ayrıntı,
   ping, TPS, konum ve tüm kontrollerin güncel puan geçmişi vardır. `WOULD_PUNISH_*` kayıtları "ceza açık olsaydı
   verilecekti" demektir; meşru oyuncu görürseniz ilgili kontrolün `weight`/eşiğini düşürün veya kapatın.
3. Yanlış pozitif görmüyorsanız `punishment.enabled: true` **ve** `punishment.i-have-reviewed-the-logs: true` yapın.
   İkisi birden açık olmadan hiçbir kick/ban çalışmaz.

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

## Bilinen sınırlar (dürüst liste)
- **Paket katmanı yok.** Yalnızca Bukkit olayları kullanılır. Sonuçları:
  alt-tik tıklama zamanlaması güvenilmez (bu yüzden tik bazlı + geniş örneklem), `AirStuck`/`Blink`/`TickShift`
  yalnızca Timer üzerinden dolaylı ve zayıf yakalanır, `onGround` bayrağına bağımlı kontroller sunucu çarpışma
  sorgusuyla desteklenir.
- **Kapsam dışı / zayıf:** Jesus (sıvı özel durum sayıldığı için atlanır), FastUse, SpeedMine, NoJumpDelay,
  Invsee/Region exploit'leri (istemci dosyalarıyla çalışır, sunucudan görünmez), EnderChest "sahte kapama".
  `ClickTP` gibi >10 blokluk tek adımlar şüphe değil **tolerans** sayılır (başka plugin'in teleportunu cezalandırmamak için).
- Reach yaklaşık **≥ 3.4–3.5** hilelerini yakalar; 3.1–3.3 gibi küçük uzatmalar bilinçli olarak geçer.
- Rotation (GCD) kontrolü sinematik kamera/özel istemcilerde yanlış pozitif verebilir; bu yüzden düşük ağırlıklıdır.
- Folia desteklenmez.

## Test durumu
- Çekirdek mantık (`core/`): **80 otomatik test geçiyor**; meşru yürüme/sprint/bunny-hop/düşme/knockback/lag
  senaryolarında 0 kanıt, hile senaryolarında tespit.
- Paper adaptörü (`bukkit/`): gerçek Paper API'si ile **derlenmedi ve canlı sunucuda denenmedi**
  (geliştirme ortamında API indirilemedi; yalnızca kendi stub'larımla iç tutarlılık denendi). İlk `./gradlew build`
  derleme hatası verirse çıktıyı paylaşın, düzeltilir.
- Gerçek hile istemcisiyle test yapılmadı; eşikler fizik simülasyonuna dayanır. Canlıda log-only modda doğrulayın.

## Komutlar
`/stx alerts` · `/stx status [oyuncu]` · `/stx exempt <oyuncu> [dk]` · `/stx reset <oyuncu>` · `/stx reload`
İzinler: `stx.admin`, `stx.alerts`, `stx.bypass`.
