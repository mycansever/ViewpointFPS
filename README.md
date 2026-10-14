# Viewpoint FPS Optimization

**Adaptive FPS optimization layer for [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528) on Project Zomboid Build 42.21** — loaded through [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy), client-side only, public domain.

**English** below · [Türkçe sürüm için tıkla ↓](#türkçe)

---

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
| `overlay` | bool | `true` | F9 overlay |
| `debug` | bool | `false` | Verbose logging |

## Architecture (short)

```
ZombieBuddy (java agent) ─ loads dev.vpfps jar
  ├─ Main                 → config, Lua bridge, watcher thread
  ├─ Patch_GameWindow     → frame measurement + budget refill   (zombie.GameWindow.frameStep)
  ├─ Patch_FP             → Viewpoint render time               (viewpoint.FP.renderWorld)
  ├─ Patch_ChunkBuilds    → mesh rebuild budget gate            (viewpoint.world.ChunkBuilds.rebuild)
  ├─ Patch_IngameState    → overlay drawing                     (zombie.gameStates.IngameState.render)
  ├─ FrameGovernor        → adaptive quality decisions (GPU/CPU attribution)
  ├─ ViewpointBridge      → reflection knob discovery (renderScale, shadowScale, ...)
  ├─ MeshBudget           → thread-safe, self-calibrating token bucket
  ├─ Overlay / Bridge     → TextManager overlay / Lua facade (VPFPSBridge)
  └─ Guard / Config / Stats / Log
Lua (client/VPFPS/)  → B42 PZAPI.ModOptions page + F9 keybind + diff-sync
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

**Project Zomboid Build 42.21'deki [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528) (first/third-person 3D renderer) için adaptif FPS optimizasyon katmanı** — [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) üzerinden yüklenir, sadece client tarafı, kamu malı.

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
  ├─ Main                 → config, Lua köprüsü, watcher thread
  ├─ Patch_GameWindow     → frame ölçümü + budget refill   (zombie.GameWindow.frameStep)
  ├─ Patch_FP             → Viewpoint render süresi        (viewpoint.FP.renderWorld)
  ├─ Patch_ChunkBuilds    → mesh rebuild bütçe kapısı      (viewpoint.world.ChunkBuilds.rebuild)
  ├─ Patch_IngameState    → overlay çizimi                 (zombie.gameStates.IngameState.render)
  ├─ FrameGovernor        → adaptif kalite kararları (GPU/CPU attribution)
  ├─ ViewpointBridge      → reflection ile knob discovery (renderScale, shadowScale, ...)
  ├─ MeshBudget           → thread-safe, self-calibrating token bucket
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

**Public domain — [The Unlicense](https://unlicense.org).** Copy it, fork it, modify it, sell it, bundle it, publish your own version — **no permission and no attribution required**. This mod is developed however the author feels like it, and you are invited to do exactly the same: take it and make it yours.

**Kamu malı — [The Unlicense](https://unlicense.org).** Kopyala, fork'la, değiştir, sat, paketle, kendi versiyonunu geliştirip yayınla — **izin ya da atıf gerekmez**. Mod yazarının keyfine göre geliştirilir; sen de aynısını yapmaya davetlisin: al, kendine göre yap.

- Methodology inspiration: [xD3I/PZ_Optimization](https://github.com/xD3I/PZ_Optimization) (chunk bake budget, measurement-driven governor, kill-switch philosophy)
- Loading infrastructure: [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) (zed_0xff)
- This mod never modifies files of Project Zomboid or Project Viewpoint; everything runs at runtime through ZombieBuddy patches.

Project Zomboid © The Indie Stone; Project Viewpoint © its authors. Unaffiliated third-party work.
