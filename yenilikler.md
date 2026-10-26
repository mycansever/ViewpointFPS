# Yenilikler / Changelog

> Bu dosya **v1.0.0 sonrasındaki** tüm değişiklikleri listeler (ilk sürümün kendi özellikleri hariç).
> This file lists every change **after v1.0.0** (the first release's own features excluded).

---

## v3.1.0 — Sunum FPS'i + Java-tarafı F9 / Presented FPS + Java-side F9

**EN**
- **The overlay now shows the PRESENTED frame rate** — the number external GPU overlays (NVIDIA App etc.) display, not just the game-step rate. Read from Viewpoint's own swap counter (`FrameTimes.count`, reverse-engineered): the headline shows `FPS 287 (presented)` with the game-step rate as `step 160` beside it, so both numbers — and why they differ — are visible at a glance. The F8 panel headline uses the presented rate too.
- **Fixed the F9 overlay toggle not responding when PZ_Optimization is installed**: PZO consumes the F9 key event in Java for its own overlay, so our Lua key listener never saw it (F8 worked because PZO does not eat that key). The overlay key is now **polled directly in Java** (`KeyPoller`, edge-detected `Keyboard.isKeyDown` once per frame with a text-entry guard) — it works no matter who consumes the events. Rebinding through Options > MODS syncs the key to the Java side.
- New config key: `overlayKey` (default 67 / F9).

**TR**
- **Overlay artık SUNULAN kare hızını gösteriyor** — harici GPU overlay'lerinin (NVIDIA App vb.) gösterdiği sayı, sadece game-step hızını değil. Viewpoint'ın kendi swap sayacından okunuyor (`FrameTimes.count`, tersine mühendislik ile bulundu): başlık `FPS 287 (presented)` gösterirken yanında game-step hızı `step 160` olarak duruyor — böylece iki sayı ve neden farklı oldukları tek bakışta görülüyor. F8 panel başlığı da sunum hızını kullanıyor.
- **PZ_Optimization kuruluyken F9 overlay anahtarının tepki vermemesi düzeltildi**: PZO, kendi overlay'i için F9 tuş olayını Java tarafında tüketiyor — bizim Lua dinleyicimiz onu hiç görmüyordu (F8 çalışıyordu çünkü PZO o tuşu yemiyor). Overlay anahtarı artık **doğrudan Java'da yoklanıyor** (`KeyPoller`, frame başına kenar-algılamalı `Keyboard.isKeyDown`, metin girişi korumasıyla) — olayları kim tüketirse tüketsin çalışıyor. Options > MODS üzerinden tuş değiştirme Java tarafına senkronize oluyor.
- Yeni config anahtarı: `overlayKey` (varsayılan 67 / F9).

---

## v3.0.2 — Lua köprüsü düzeltmesi / Lua bridge fix

**EN**
- **Fixed the Lua bridge not loading at all.** Build 42 removed `zombie.LuaManager` (a Build 41 class), so the old reflection-based exposure silently failed — which is why the F8 panel showed `?`/`nan` everywhere while the Java side worked perfectly (knobs bound, governor stepping). The bridge now uses **ZombieBuddy's official `@Exposer.LuaClass` annotation**: ZombieBuddy discovers `Bridge` in the mod's package and registers it with the game's Lua engine as the global `VPFPSBridge` — the same documented pattern used by ZombieBuddy's own example mods. A deduplicating `Exposer.exposeClass()` fallback covers older ZombieBuddy builds.
- Verified the annotation is correctly embedded in the shipped bytecode.

**TR**
- **Lua köprüsünün hiç yüklenmemesi düzeltildi.** Build 42, `zombie.LuaManager` sınıfını (B41 sınıfı) kaldırdı; eski reflection tabanlı expose sessizce başarısız oluyordu — Java tarafı kusursuz çalışırken (knob'lar bağlı, governor çalışıyor) F8 panelinin her şeyde `?`/`nan` göstermesinin sebebi buydu. Köprü artık **ZombieBuddy'nin resmi `@Exposer.LuaClass` annotation'ını** kullanıyor: ZombieBuddy, `Bridge` sınıfını modun paketinde keşfedip oyunun Lua motoruna `VPFPSBridge` globali olarak kaydediyor — ZombieBuddy'nin kendi örnek modlarının kullandığı belgelenmiş pattern. Dedupe eden `Exposer.exposeClass()` fallback'i eski ZombieBuddy sürümlerini kapsıyor.
- Annotation'ın gönderilen bytecode'a doğru gömüldüğü doğrulandı.

---

## v3.0.1 — Kahlua çökmesi + panel değerleri / Kahlua crash + panel values

**EN**
- **Fixed the "Object tried to call nil in pcall" crash** (reported in multiplayer): this Lua VM's `pcall` does **not** catch nil-call errors — they escape as RuntimeException and kill the whole event handler. All Lua code (bootstrap, options, panel) was rewritten with a nil-check-first pattern: no possibly-nil value is ever called, and every callback nil-checks globals before use.
- **Fixed the F8 panel showing `?`/`nan`**: the bridge call wrapper tested Java-exposed methods with `type(fn) ~= "function"`, which is always true for Kahlua Java methods — so every call silently returned nil. Replaced with a plain nil-check.
- Overlay/panel now show `N/A` for unavailable features (NaN and `-1` sentinels) instead of raw values.
- Added the detailed **"knob not found" guide** to the README (retry behaviour, how to verify what bound, how to add renamed settings after a Viewpoint update).

**TR**
- **"Object tried to call nil in pcall" çökmesi düzeltildi** (multiplayer'da raporlandı): bu Lua VM'inin `pcall`'ı nil çağrı hatalarını **yakalamıyor** — RuntimeException olarak kaçıp event handler'ın tamamını öldürüyor. Tüm Lua kodu (bootstrap, options, panel) nil-check-first pattern'iyle yeniden yazıldı: olası-nil hiçbir değer çağrılmıyor, her callback kullanmadan önce global'leri kontrol ediyor.
- **F8 panelinin `?`/`nan` göstermesi düzeltildi**: köprü çağrı sarmalayıcısı Java tarafındaki metodları `type(fn) ~= "function"` ile test ediyordu — Kahlua Java metodları için bu her zaman doğru olduğundan her çağrı sessizce nil dönüyordu. Düz nil-check ile değiştirildi.
- Overlay/panel artık kullanılamayan özellikler için (NaN ve `-1` işaret değerleri) ham değer yerine `N/A` gösteriyor.
- README'ye detaylı **"knob not found" rehberi** eklendi (retry davranışı, neyin bağlandığını doğrulama, Viewpoint güncellemesinden sonra yeniden adlandırılan ayarları ekleme).

---

## v3.0.0 — Gerçek Viewpoint entegrasyonu / Real Viewpoint integration (büyük sürüm / major)

**EN**
- **Reverse-engineered against the real Viewpoint.jar** (REA archive analysis + javap): the mod no longer guesses field names — it drives **Viewpoint's own settings system** (`LiveSettings`), discovered and verified from the shipped jar.
- **CPU / GPU quality ladders now scale Viewpoint's real tuning values** inside their own declared min..max ranges: chunk build budgets (`BUILD_MAX`, `MAX_COOKING`), chunk walk rate (`AT_ONCE`), far world (`BUDGET`, `JOBS`), draw distance (`REACH`), far detail levels (`DETAIL_1/2/4`), floor/shell bake budgets, GPU upload pacing, corpse model limits. Factor 1.0 = stock; a value the user raised in Viewpoint's own settings is never fought.
- **In-game FPS cap control** — Viewpoint's `FrameCaps` exposed in-game: game cap, menu cap and a one-click **UNCAP** button. No more editing files to remove the frame limit.
- **RAM / VRAM control** — Viewpoint's `Pool` memory manager (near meshes, far world, shells, models, corpses, ...) scaled in-game through one budget factor; live VRAM readout from Viewpoint's own `VideoMemory` sampler.
- **F8 settings panel** — a full in-game panel: live stats header (fps, 1% low, GPU busy, VRAM), governor controls, frame cap, memory, overlay options. Built on the most stable UI surface (panel + buttons + steppers) for cross-build reliability.
- **Pass-level GPU attribution** — reads Viewpoint's `Profile` GPU timers: the overlay shows the heaviest render passes by GPU time (e.g. `FAR 3.2ms SHADOW 1.8ms`), and `GpuBusy.percent()` drives the CPU-vs-GPU decision together with our own render-share measurement.
- **Removed the v2 patches that fought Viewpoint's own machinery** — Viewpoint already ships its own chunk budget (`ChunkBuilds.buildBudget`), velocity-aware chunk walking and GPU telemetry; our old mesh-budget gate and walk throttle conflicted with them. Replaced by driving the real settings.
- CSV export now records GPU busy % and both factors; hardware auto-profiling seeds the CPU ladder floor.

**TR**
- **Gerçek Viewpoint.jar'a karşı tersine mühendislik yapıldı** (REA arşiv analizi + javap): mod artık alan adı tahmin etmiyor — gönderilen jar'dan keşfedilip doğrulanan **Viewpoint'ın kendi ayar sistemini** (`LiveSettings`) sürüyor.
- **CPU / GPU kalite merdivenleri artık Viewpoint'ın gerçek ayar değerlerini** kendi beyan ettiği min..max aralıklarında ölçekliyor: chunk build bütçeleri (`BUILD_MAX`, `MAX_COOKING`), chunk walk hızı (`AT_ONCE`), uzak dünya (`BUDGET`, `JOBS`), çizim mesafesi (`REACH`), uzak detay seviyeleri (`DETAIL_1/2/4`), kat/kabuk bake bütçeleri, GPU upload hızlandırması, ceset modeli sınırları. Faktör 1.0 = stok; kullanıcının Viewpoint'ın kendi ayarlarında yükselttiği değer asla ezilmiyor.
- **Oyun içi FPS sınırı kontrolü** — Viewpoint'ın `FrameCaps`'ı oyun içinde: oyun sınırı, menü sınırı ve tek tıkla **UNCAP** butonu. Frame sınırını kaldırmak için dosya düzenlemek yok.
- **RAM / VRAM kontrolü** — Viewpoint'ın `Pool` bellek yöneticisi (yakın mesh'ler, uzak dünya, kabuklar, modeller, cesetler...) tek bütçe faktörüyle oyun içinde ölçekleniyor; canlı VRAM göstergesi Viewpoint'ın kendi `VideoMemory` örnekleyicisinden geliyor.
- **F8 ayar paneli** — tam oyun içi panel: canlı istatistik başlığı (fps, 1% low, GPU doluluğu, VRAM), governor kontrolleri, FPS sınırı, bellek, overlay seçenekleri. Sürümler arası güvenilirlik için en stabil UI yüzeyi üzerine kuruldu (panel + buton + stepper).
- **Pass seviyesinde GPU atıfı** — Viewpoint'ın `Profile` GPU zamanlayıcıları okunuyor: overlay, GPU süresine göre en ağır render pass'lerini gösteriyor (örn. `FAR 3.2ms SHADOW 1.8ms`) ve CPU-vs-GPU kararını `GpuBusy.percent()` + kendi render payı ölçümümüz birlikte sürüyor.
- **Viewpoint'ın kendi mekanizmalarıyla çakışan v2 patch'leri kaldırıldı** — Viewpoint zaten kendi chunk bütçesini (`ChunkBuilds.buildBudget`), hız algılayan chunk walk'unu ve GPU telemetrisini taşıyor; eski mesh-bütçe kapısı ve walk throttle'ı bunlarla çakışıyordu. Yerine gerçek ayarların sürülmesi geldi.
- CSV export artık GPU doluluk %'sini ve her iki faktörü kaydediyor; donanım auto-profile CPU merdiven tabanını belirliyor.

---

## v2.0.0 — Viewpoint dışına çıkış / Beyond Viewpoint's settings

**EN**
- **Chunk-walk idle throttle** — while standing still (looting, fighting, crafting) Viewpoint re-scanned its chunks every frame; this limited the scan to ~4×/s when idle, full rate on movement. *(Superseded in v3.0.0: Viewpoint ships its own velocity-aware walking, so this patch was removed.)*
- **Adaptive draw distance** — a governor ladder scaling Viewpoint's chunk/draw distance. *(Superseded in v3.0.0 by scaling the real `FarWorld.REACH` setting.)*
- **World-capture attribution** — `FP.captureWorld` measured alongside `FP.renderWorld`: each frame split into render (GPU feed) vs world capture (game thread) vs everything else.
- **PZ_Optimization compatibility layer** — runtime detection; the two mods stack cleanly (their class overrides + our runtime hooks). Our overlay steps aside for PZO's own F9 overlay (`deferToPZOpt=auto`). No code is shared — PZO carries no open license; techniques were reimplemented independently.
- **Benchmark CSV export** — `statsExport=true` appends per-window rows to `Zomboid/vpfps-stats.csv`.
- **Hardware auto-profiling** — first run seeds the mesh budget / walk interval from the CPU core count.
- **Overlay v2** — 1% low, draw distance, capture time, PZO status, detail toggle.

**TR**
- **Idle chunk-walk throttle** — olduğun yerde dururken (yağmalama, dövüş, craft) Viewpoint chunk'larını her frame yeniden tarıyordu; bu, durgunken taramayı ~4×/sn'e sınırlıyordu, hareketle tam hız. *(v3.0.0'da değişti: Viewpoint kendi hız algılayan walk'unu taşıdığı için bu patch kaldırıldı.)*
- **Adaptif draw distance** — Viewpoint'ın chunk/draw mesafesini ölçekleyen bir governor merdiveni. *(v3.0.0'da gerçek `FarWorld.REACH` ayarının sürülmesiyle değişti.)*
- **World-capture attribution** — `FP.renderWorld` ile birlikte `FP.captureWorld` da ölçülüyor: her frame; render (GPU) vs world capture (game thread) vs geri kalanına ayrılıyor.
- **PZ_Optimization uyumluluk katmanı** — runtime algılama; iki mod sorunsuz üst üste biniiyor (onların class override'ları + bizim runtime hook'larımız). Bizim overlay, PZO'nun kendi F9 overlay'i için kenara çekiliyor (`deferToPZOpt=auto`). Kod paylaşımı yok — PZO açık lisans taşımıyor; teknikler bağımsız yeniden implemente edildi.
- **Benchmark CSV export** — `statsExport=true` pencere başına satırları `Zomboid/vpfps-stats.csv`'ye yazıyor.
- **Donanım auto-profile** — ilk açılışta mesh bütçesi / walk aralığı CPU çekirdek sayısından tohumlanıyor.
- **Overlay v2** — 1% low, draw distance, capture süresi, PZO durumu, detay toggle'ı.
