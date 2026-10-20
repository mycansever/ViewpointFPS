# Viewpoint FPS Optimization

**Adaptive FPS optimization layer for [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528) on Project Zomboid Build 42.21** — loaded through [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy), client-side only, MIT licensed.

**English** below · [Türkçe sürüm için tıkla ↓](#türkçe)

---

## What's new in v3.0 — reverse-engineered against the real Viewpoint.jar

v3.0 was rebuilt from a full static analysis of the shipped Viewpoint.jar (REA + javap: class inventory, `LiveSettings` registry, `Pool` memory manager, `FrameCaps`, `Profile` GPU timers). The mod no longer guesses — it drives **Viewpoint's own settings**:

- **Drives Viewpoint's real settings** — two quality ladders (CPU / GPU) scale Viewpoint's own `LiveSettings` numbers inside their declared min..max ranges: chunk build budgets (`BUILD_MAX`, `MAX_COOKING`), chunk walk (`AT_ONCE`), far world (`BUDGET`, `JOBS`, `REACH`, `DETAIL_1/2/4`), bake budgets, GPU upload pacing, corpse models. Factor 1.0 = stock, and a stock value the user raised in Viewpoint's own UI is never fought.
- **In-game FPS cap control** — Viewpoint's `FrameCaps` exposed in-game: game cap, menu cap and a full UNCAP button. No more editing files to remove the limit.
- **RAM / VRAM control** — Viewpoint's `Pool` memory manager (NEAR_MESHES, FAR, SHELL, MODEL_MESHES, LIGHT_GRIDS, CORPSES, CPU, ...) scaled in-game through one pool-budget factor; live VRAM readout from Viewpoint's own `VideoMemory` sampler.
- **F8 settings panel** — a full in-game panel: live stats header (fps, 1% low, GPU busy, VRAM), governor controls, frame cap, memory, overlay options. Everything adjustable without touching a file.
- **Pass-level GPU attribution** — reads Viewpoint's `Profile` GPU timers: the overlay shows the top render passes by GPU time (e.g. `FAR 3.2ms SHADOW 1.8ms`), and `GpuBusy.percent()` drives the CPU-vs-GPU decision together with our own render-pass share.
- **Removed the conflicting v2 patches** — Viewpoint already ships its own chunk budget (`ChunkBuilds.buildBudget`), velocity-aware chunk walking and GPU telemetry. Our v2 mesh-budget gate and walk throttle fought those; both are gone, replaced by driving the real settings.

## What's new in v2.0

- **Chunk-walk idle throttle** — while you stand still (looting, fighting in place, crafting) Viewpoint still re-scans its chunk cache every frame. v2.0 rate-limits that scan to ~4×/s when idle; full rate returns the instant you move. Free game-thread time the vanilla settings can never give you.
- **Adaptive draw distance** — a fourth governor ladder that scales Viewpoint's chunk/draw distance (factor 0.70–1.00 of its stock value) under game-thread pressure. Fewer chunks = fewer meshes, fewer draws. The stock baseline is remembered and ratchets up if you raise Viewpoint's own setting, so the governor never fights you.
- **World-capture attribution** — `FP.captureWorld` is now measured alongside `FP.renderWorld`, splitting each frame into render (GPU feed) vs world capture (game thread) vs everything else.
- **PZ_Optimization compatibility layer** — detects [xD3I/PZ_Optimization](https://github.com/xD3I/PZ_Optimization) at runtime; the two stack cleanly (their class overrides + our ZombieBuddy hooks). Our overlay steps aside when PZO's own F9 overlay is present (`deferToPZOpt=auto`). Note: PZO carries **no open license**, so this project contains zero of its code — techniques only, reimplemented independently.
- **Benchmark CSV export** — `statsExport=true` appends fps/p90/p99/render-share rows to `Zomboid/vpfps-stats.csv` every 5 s.
- **Hardware auto-profiling** — first run seeds the mesh budget and walk interval from your CPU core count (≤4 cores → 1.0 ms / 400 ms, ≥12 → 3.0 ms).
- **Overlay v2** — 1% low, draw distance, capture time, PZO status, detail toggle.

## What it does

| Optimization | What it actually does | How |
|---|---|---|
| **Chunk mesh budget** | When Viewpoint streams many chunks at once (driving at 120 km/h, fast camera turns) the mesh rebuilds stack into multi-frame freezes. This sets a per-frame time budget (default 2 ms); leftover chunks rebuild next frame. | `viewpoint.world.ChunkBuilds.rebuild` delegation patch + self-calibrating token bucket |
| **Adaptive quality governor** | Every 500 ms it reads the frame-time distribution (p90/mean) and walks independent quality ladders (render scale, shadow scale, mesh budget). Drops are instant, recovery needs 3 stable windows — no oscillation. | `zombie.GameWindow.frameStep` measurement + FrameGovernor |
| **GPU / CPU attribution** | If Viewpoint's render pass eats ≥45% of the frame the bottleneck is the GPU → render scale drops first; otherwise it's the game thread → mesh budget drops first. Lowering the wrong thing gains nothing. | `viewpoint.FP.renderWorld` measurement |
| **Dynamic resolution** | Under GPU pressure, scales Viewpoint's internal render resolution between your configured min (default 0.50) and 1.00. | ViewpointBridge (reflection, runtime knob discovery) |
| **Shadow scaling** | Scales shadow quality 1.00 → 0.50. | ViewpointBridge |
| **Live stats overlay** | fps, frame time, p99, VP render share, mesh stats, governor state. Toggle with F9 (rebindable). | `zombie.gameStates.IngameState.render` + TextManager |
| **Fail-soft by design** | Viewpoint is alpha and updates often, so this mod **never hard-references its classes**. Everything runs through reflection + runtime knob discovery. If an update renames a field, the related optimization reports "unavailable" and the rest keeps working. Circuit breakers disable a feature after repeated failures. | Guard class |

## Requirements

- Project Zomboid **Build 42.21** (Steam, default branch)
- **ZombieBuddy** 1.5.0+ — [installation guide](https://github.com/zed-0xff/ZombieBuddy)
- **Project Viewpoint** (Workshop `3809306528`), approved in ZombieBuddy

## Installation

1. Copy the `ViewpointFPS` folder into `%USERPROFILE%\Zomboid\mods\`.
2. Run `build.ps1` if `42\media\java\client\build\libs\vpfps.jar` is missing (JDK 17+; the compiled jar ships with the repo). Gradle alternative: `cd 42/media/java/client && gradle build`.
3. Launch the game, enable in **MODS**: `ZombieBuddy` → `Viewpoint` → `Viewpoint FPS Optimization`, then **Allow** the jar when ZombieBuddy asks.
4. Enter a world, press `O` for first person, open the overlay with **F9**.

Verify in `Zomboid\Logs\vpfps.log`:
```
[VPFPS] Viewpoint FPS Optimization v1.0.0 booting
[VPFPS] viewpoint class found: viewpoint.FP
[VPFPS] knob 'renderScale' -> viewpoint.FP.renderScale (governor-adjustable)
```

## Settings

Two equivalent paths: **Options → MODS → Viewpoint FPS Optimization** (applies live) or `%USERPROFILE%\Zomboid\vpfps.properties` (hot-reloads in ~5 s; template in `tools/vpfps.properties.example`).

| Key | Range | Default | Description |
|---|---|---|---|
| `enabled` | bool | `true` | Master kill switch |
| `governor` | adaptive/fixed/off | `adaptive` | Governor mode |
| `targetFps` | 15–480 | `60` | Target frame-time budget |
| `dynamicResolution` | bool | `true` | Governor controls render scale |
| `renderScaleMin` / `Max` | 0.25–1.0 | `0.50` / `1.00` | Adaptive render-scale window |
| `renderScaleFixed` / `shadowScaleFixed` | 0.25–1.0 | `1.00` | Fixed-mode values |
| `meshBudgetEnabled` | bool | `true` | Mesh budget kill switch |
| `meshBudgetMs` | 0.1–16 | `2.0` | Per-frame mesh time (ms) |
| `drawDistanceAdaptive` | bool | `true` | Governor scales draw distance |
| `drawDistanceMin` | 0.40–1.0 | `0.70` | Draw-distance floor (factor of stock) |
| `chunkWalkThrottleEnabled` | bool | `true` | Idle chunk-walk throttle kill switch |
| `chunkWalkThrottleMs` | 50–2000 | `250` | Idle walk interval (ms) |
| `statsExport` | bool | `false` | CSV benchmark log (vpfps-stats.csv) |
| `deferToPZOpt` | auto/on/off | `auto` | Step aside for PZ_Optimization's overlay |
| `autoProfile` | bool | `true` | First-run hardware heuristic |
| `overlayDetail` | bool | `true` | Extra overlay lines |
| `overlay` | bool | `true` | F9 overlay |
| `debug` | bool | `false` | Verbose logging |

## Architecture (short)

```
ZombieBuddy (java agent) ─ loads dev.vpfps jar
  ├─ Main                 → config, Lua bridge, watcher thread, autoProfile
  ├─ Patch_GameWindow     → frame measurement                (zombie.GameWindow.frameStep)
  ├─ Patch_FP             → Viewpoint render time            (viewpoint.FP.renderWorld)
  ├─ Patch_FPCapture      → world-capture time               (viewpoint.FP.captureWorld)
  ├─ Patch_IngameState    → overlay drawing                  (zombie.gameStates.IngameState.render)
  ├─ FrameGovernor        → CPU/GPU factor ladders, attribution (GpuBusy + render share)
  ├─ ViewpointKnobs       → drives Viewpoint's own LiveSettings (BUILD_MAX, AT_ONCE, REACH...)
  ├─ ViewpointMemory      → RAM/VRAM pool scaling (Pool) + VideoMemory telemetry
  ├─ FrameCapControl      → in-game FPS cap (FrameCaps.setGameCap / uncap)
  ├─ GpuStats             → GpuBusy% + Profile top render passes
  ├─ PZOCompat            → PZ_Optimization detection + defer policy
  ├─ StatsExporter        → CSV benchmark log
  ├─ Overlay / Bridge     → TextManager overlay / Lua facade (VPFPSBridge)
  └─ Guard / Config / Stats / Log
Lua (client/VPFPS/)  → F8 settings panel + F9 overlay + PZAPI.ModOptions page + diff-sync
```

Details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · Tuning guide: [`docs/TUNING.md`](docs/TUNING.md) · Steam description: [`aciklama.md`](aciklama.md)

## Known limitations

- **Viewpoint is alpha and changes often.** Field/knob names were derived from alpha-0.1.6 public stack traces. If a Viewpoint update renames the render-scale field, the governor reports that knob "unavailable" and mesh budget + overlay keep working. Nothing crashes (Guard mechanism). New field names can be added to `ViewpointBridge.KNOBS` and rebuilt.
- `Patch_ChunkBuilds` is a **delegation** patch (it can skip the method entirely). `meshBudgetEnabled=false` restores byte-for-byte stock behaviour.
- The overlay draws in-world only, not on the main menu.
- macOS is unsupported (Viewpoint itself does not support it).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Not in the Mods list | Does `42\media\java\client\build\libs\vpfps.jar` exist? If not, run `build.ps1`. |
| "knob 'renderScale' not found" | Different Viewpoint version; the mod still provides mesh budget + overlay. Add the new field name to `ViewpointBridge.KNOBS` and rebuild. |
| No FPS gain | Check the overlay's "VP render %" line; if low, the bottleneck is the game thread — set `meshBudgetMs=1.0` and a realistic `targetFps`. |
| Suspected crash | Check `Zomboid\Logs\vpfps.log`; test with `enabled=false`. |

---

## Türkçe

**Project Zomboid Build 42.21'deki [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528) (first/third-person 3D renderer) için adaptif FPS optimizasyon katmanı** — [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) üzerinden yüklenir, sadece client tarafı, MIT lisanslı.

### v3.0'daki yenilikler — gerçek Viewpoint.jar'a karşı tersine mühendislik yapıldı

v3.0, gönderilen Viewpoint.jar'ın tam statik analizinden yeniden inşa edildi (REA + javap: sınıf envanteri, `LiveSettings` kayıt defteri, `Pool` bellek yöneticisi, `FrameCaps`, `Profile` GPU zamanlayıcıları). Artık tahmin yok — mod **Viewpoint'ın kendi ayarlarını** sürüyor:

- **Viewpoint'ın gerçek ayarlarını sürer** — iki kalite merdiveni (CPU / GPU), Viewpoint'ın kendi `LiveSettings` sayılarını beyan edilen min..max aralıklarında ölçekler: chunk build bütçeleri (`BUILD_MAX`, `MAX_COOKING`), chunk walk (`AT_ONCE`), uzak dünya (`BUDGET`, `JOBS`, `REACH`, `DETAIL_1/2/4`), bake bütçeleri, GPU upload hızlandırması, ceset modelleri. Faktör 1.0 = stok; kullanıcı Viewpoint'ın kendi arayüzünde yükselttiği stok değeri asla ezilmez.
- **Oyun içi FPS sınırı kontrolü** — Viewpoint'ın `FrameCaps`'ı oyun içinde: oyun sınırı, menü sınırı ve tam UNCAP butonu. Sınırı kaldırmak için artık dosya düzenlemek yok.
- **RAM / VRAM kontrolü** — Viewpoint'ın `Pool` bellek yöneticisi (NEAR_MESHES, FAR, SHELL, MODEL_MESHES, LIGHT_GRIDS, CORPSES, CPU, ...) tek bir havuz-bütçe faktörüyle oyun içinde ölçeklenir; canlı VRAM durumu Viewpoint'ın kendi `VideoMemory` örnekleyicisinden gelir.
- **F8 ayar paneli** — tam oyun içi panel: canlı istatistik başlığı (fps, 1% low, GPU doluluğu, VRAM), governor kontrolleri, FPS sınırı, bellek, overlay seçenekleri. Bir dosyaya dokunmadan her şey ayarlanabilir.
- **Pass seviyesinde GPU atıfı** — Viewpoint'ın `Profile` GPU zamanlayıcıları okunur: overlay, GPU süresine göre en çok zaman harcayan render pass'lerini gösterir (örn. `FAR 3.2ms SHADOW 1.8ms`) ve CPU-vs-GPU kararını `GpuBusy.percent()` + kendi render payı ölçümümüz birlikte sürer.
- **Çakışan v2 patch'leri kaldırıldı** — Viewpoint zaten kendi chunk bütçesini (`ChunkBuilds.buildBudget`), hız algılayan chunk walk'unu ve GPU telemetrisini taşıyor. v2'nin mesh-bütçe kapısı ve walk throttle'ı bunlarla çakışıyordu; ikisi de kaldırıldı, yerine gerçek ayarların sürülmesi geldi.

### v2.0'daki yenilikler

- **Chunk-walk idle throttle** — olduğun yerde dururken (yağmalama, yerinde dövüş, craft) Viewpoint chunk cache'ini her frame yeniden tarar. v2.0 bunu durgunken saniyede ~4 kezle sınırlar; hareket ettiğin an tam hız geri döner. Vanilla ayarlarının asla veremeyeceği bedava game-thread zamanı.
- **Adaptif draw distance** — governor'a dördüncü merdiven: game-thread baskısında Viewpoint'ın chunk/draw mesafesini (stok değerinin 0.70–1.00 katı) ölçekler. Daha az chunk = daha az mesh, daha az draw. Stok baseline hatırlanır ve sen Viewpoint'ın kendi ayarını yükseltirsen yukarı çekilir — governor seninle asla kavga etmez.
- **World-capture attribution** — artık `FP.captureWorld` da ölçülüyor (`FP.renderWorld` ile birlikte): frame; render (GPU) vs world capture (game thread) vs geri kalanına ayrılıyor.
- **PZ_Optimization uyumluluk katmanı** — [xD3I/PZ_Optimization](https://github.com/xD3I/PZ_Optimization) runtime'da algılanır; ikisi sorunsuz üst üste biner (onların class override'ları + bizim ZombieBuddy hook'larımız). PZO'nun kendi F9 overlay'i varken bizimki kenara çekilir (`deferToPZOpt=auto`). Not: PZO **açık lisans taşımıyor**, bu yüzden bu projede ondan sıfır kod var — sadece teknikler, bağımsız yeniden implementasyon.
- **Benchmark CSV export** — `statsExport=true` her 5 saniyede fps/p90/p99/render-payı satırlarını `Zomboid/vpfps-stats.csv`'ye yazar.
- **Donanım auto-profile** — ilk açılışta mesh bütçesi ve walk aralığı CPU çekirdek sayısına göre atanır (≤4 çekirdek → 1.0 ms / 400 ms, ≥12 → 3.0 ms).
- **Overlay v2** — 1% low, draw distance, capture süresi, PZO durumu, detay toggle'ı.

### Ne yapar?

| Optimizasyon | Ne yapıyor | Nasıl |
|---|---|---|
| **Chunk mesh budget** | Viewpoint bir sürü chunk akıtırken (120 km/h'de araba, hızlı dönüş) mesh rebuild'leri üst üste binip çok frame'lik donmalara yol açar. Frame başına zaman bütçesi (varsayılan 2 ms) koyar; bütçe dolunca chunk bir sonraki frame'e ertelenir. | `viewpoint.world.ChunkBuilds.rebuild` delegation patch + self-calibrating token bucket |
| **Adaptive quality governor** | Her 500 ms'de frame-time dağılımına bakar (p90/mean) ve kalite merdivenlerinde (render scale, gölge, mesh budget) iner/çıkar. İniş anında, çıkış 3 stabil pencere ister (osilasyon yok). | `zombie.GameWindow.frameStep` ölçümü + FrameGovernor |
| **GPU / CPU attribution** | Viewpoint'ın render pass'i frame'in %45+'ını yiyorsa darboğaz GPU → önce render scale düşer; değilse game thread → önce mesh budget düşer. Yanlış şeyi kısmanın faydası yok. | `viewpoint.FP.renderWorld` ölçümü |
| **Dynamic resolution** | GPU-bound anlarında dahili render çözünürlüğünü ayarladığın aralıkta (varsayılan 0.50–1.00) ölçekler. | ViewpointBridge (reflection, knob discovery) |
| **Shadow scaling** | Gölge kalitesini 1.00 → 0.50 aralığında ölçekler. | ViewpointBridge |
| **Live stats overlay** | FPS, frame time, p99, VP render payı, mesh istatistikleri, governor durumu. F9 ile aç/kapa. | `zombie.gameStates.IngameState.render` + TextManager |
| **Fail-soft tasarım** | Viewpoint alpha bir mod ve sık güncelleniyor. Bu mod Viewpoint sınıflarını **hiç doğrudan referans etmez**; her şey reflection + runtime knob discovery ile çalışır. Alan adı değişirse ilgili optimize "unavailable" raporlar ve mod çalışmaya devam eder. | Guard sınıfı |

### Gereksinimler

- Project Zomboid **Build 42.21** (Steam, varsayılan dal)
- **ZombieBuddy** 1.5.0+ — [kurulum rehberi](https://github.com/zed-0xff/ZombieBuddy)
- **Project Viewpoint** (Workshop `3809306528`), ZombieBuddy'den onaylanmış

### Kurulum

1. `ViewpointFPS` klasörünü `%USERPROFILE%\Zomboid\mods\` içine kopyala.
2. `42\media\java\client\build\libs\vpfps.jar` yoksa `build.ps1` çalıştır (JDK 17+; derlenmiş jar repoda gelir). Gradle alternatifi: `cd 42/media/java/client && gradle build`.
3. Oyunu başlat, **Mods** ekranında sırayla etkinleştir: `ZombieBuddy` → `Viewpoint` → `Viewpoint FPS Optimization`; ZombieBuddy sorarsa **Allow** de.
4. Oyuna gir, `O` ile first-person'a geç, **F9** ile overlay'i aç.

Doğrulama: `Zomboid\Logs\vpfps.log` içinde `knob 'renderScale' -> ...` satırlarını görmelisin.

### Ayarlar

İki eşdeğer yol: **Options → MODS → Viewpoint FPS Optimization** (canlı uygular) veya `%USERPROFILE%\Zomboid\vpfps.properties` (≈5 sn'de hot-reload; şablon: `tools/vpfps.properties.example`). Tuş aralıkları yukarıdaki İngilizce tabloda.

### Mimari (kısaca)

```
ZombieBuddy (java agent) ─ dev.vpfps jar'ı yükler
  ├─ Main                 → config, Lua köprüsü, watcher thread, autoProfile
  ├─ Patch_GameWindow     → frame ölçümü + budget refill   (zombie.GameWindow.frameStep)
  ├─ Patch_FP             → Viewpoint render süresi        (viewpoint.FP.renderWorld)
  ├─ Patch_FPCapture      → world-capture süresi           (viewpoint.FP.captureWorld)
  ├─ Patch_ChunkBuilds    → mesh rebuild bütçe kapısı      (viewpoint.world.ChunkBuilds.rebuild)
  ├─ Patch_ChunkWalk      → idle chunk-walk throttle       (viewpoint.world.ChunkWalk.walk)
  ├─ Patch_IngameState    → overlay çizimi                 (zombie.gameStates.IngameState.render)
  ├─ FrameGovernor        → adaptif kalite kararları (GPU/CPU attribution, 4 merdiven)
  ├─ ViewpointBridge      → reflection ile knob discovery (renderScale, shadowScale, drawDistance)
  ├─ ChunkWalkThrottle    → hareket algılayan idle throttle + emniyet supabı
  ├─ MeshBudget           → thread-safe, self-calibrating token bucket
  ├─ PZOCompat            → PZ_Optimization algılama + defer politikası
  ├─ StatsExporter        → CSV benchmark log
  ├─ Overlay / Bridge     → TextManager overlay / Lua facade (VPFPSBridge)
  └─ Guard / Config / Stats / Log
Lua (client/VPFPS/)  → B42 PZAPI.ModOptions sayfası + F9 keybind + diff-sync
```

Detaylar: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · Tuning: [`docs/TUNING.md`](docs/TUNING.md) · Steam açıklaması: [`aciklama.md`](aciklama.md)

### Bilinen sınırlar

- **Viewpoint alpha'dır ve sık değişir.** Field/knob isimleri alpha-0.1.6 public stack trace'lerinden türetildi. Bir güncelleme render-scale alanını yeniden adlandırırsa governor o knob için "unavailable" der; mesh budget + overlay çalışmaya devam eder. Hiçbir şey crash etmez. Yeni field isimleri `ViewpointBridge.KNOBS` listesine eklenip yeniden derlenebilir.
- `Patch_ChunkBuilds` bir **delegation** patch'idir (metodu tamamen atlayabilir). `meshBudgetEnabled=false` bayt-bayt stok davranışa döner.
- Overlay yalnızca dünya içinde çizilir, ana menüde değil.
- macOS desteklenmez (Viewpoint'in kendisi de desteklemiyor).

### Sorun giderme

| Belirti | Çözüm |
|---|---|
| Mods listesinde yok | `42\media\java\client\build\libs\vpfps.jar` var mı? Yoksa `build.ps1` çalıştır. |
| "knob 'renderScale' not found" | Viewpoint sürümün farklı; mod yine de mesh budget + overlay sağlar. Yeni field adını `ViewpointBridge.KNOBS`'a ekleyip derle. |
| FPS artışı yok | Overlay'deki "VP render %" satırına bak; düşükse darboğaz game thread → `meshBudgetMs=1.0` ve gerçekçi `targetFps`. |
| Crash şüphesi | `Zomboid\Logs\vpfps.log`'a bak; `enabled=false` ile test et. |

---

## License / Lisans

**[MIT License](LICENSE).** Copy it, fork it, modify it, sell it, bundle it, publish your own version — **as long as the original copyright notice and this license text are kept** in the copy (credit to the original project). This mod is developed however the author feels like it, and you are welcome to take it and build on it — just keep the credit line.

**[MIT Lisansı](LICENSE).** Kopyala, fork'la, değiştir, sat, paketle, kendi versiyonunu geliştirip yayınla — tek şartla: kopyada **orijinal telif notu ve bu lisans metni kalsın** (yani orijinal projeden bahset/atıf yap). Mod yazarının keyfine göre geliştirilir; alıp üzerine inşa etmeye davetlisin — sadece kredi satırını koru.

- Methodology inspiration: [xD3I/PZ_Optimization](https://github.com/xD3I/PZ_Optimization) (chunk bake budget, measurement-driven governor, kill-switch philosophy)
- Loading infrastructure: [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) (zed_0xff)
- This mod never modifies files of Project Zomboid or Project Viewpoint; everything runs at runtime through ZombieBuddy patches.

Project Zomboid © The Indie Stone; Project Viewpoint © its authors. Unaffiliated third-party work.
