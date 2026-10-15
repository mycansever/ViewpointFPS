# Mimari

ViewpointFPS, ZombieBuddy java agent'ı üzerinden yüklenen, PZ Build 42.21 +
Project Viewpoint alpha-0.1.6 hedefli client-side bir Java modudur. PZ_Optimization'ın
"ölç, karar ver, kill-switch'i her zaman elinde tut" felsefesi izlenir; ancak hedef
oyun değil, başka bir mod (Viewpoint) olduğu için **hiçbir Viewpoint sınıfı
doğrudan referans edilmez** — her şey reflection ve ZombieBuddy patch'leriyle,
fail-soft biçimde çalışır.

## Bileşen haritası

```
                       ZombieBuddy (java agent, ByteBuddy)
                                      │  yükler: media/java/client/build/libs/vpfps.jar
                                      │  keşfeder: javaPkgName=dev.vpfps
                    ┌─────────────────┴──────────────────┐
                    │            dev.vpfps jar           │
                    └─────────────────┬──────────────────┘
        boot (Main.main)              │            runtime (patch'ler)
  ┌─────────────────────────┐         │   ┌────────────────────────────────────┐
  │ Config.load()           │         │   │ Patch_GameWindow  ◄─ GameWindow.frameStep
  │ Config.watchLoop()      │         │   │   └► FrameGovernor.onFrameStart/End
  │ Main.exposeLuaBridge()  │         │   │        └► MeshBudget.beginFrame()
  └─────────────────────────┘         │   │
                                      │   │ Patch_FP          ◄─ viewpoint.FP.renderWorld
   Lua (client/VPFPS/)                │   │   └► FrameGovernor.onVpRenderStart/End
  ┌─────────────────────────┐         │   │
  │ VPFPSBridge (global)    │◄────────┘   │ Patch_ChunkBuilds ◄─ viewpoint.world.
  │  PZAPI.ModOptions page  │  reflection │   └► MeshBudget.tryBegin()/end()
  │  F9 keybind, diff-sync  │  (exposer) │      └► original.call() koşullu
  └─────────────────────────┘         │   │
                                      │   │ Patch_IngameState ◄─ IngameState.render
                                      │   │   └► Overlay.render()
                                      │   └────────────────────────────────────┘
```

## Veri akışı (bir frame'in hikâyesi)

1. `GameWindow.frameStep` girer → **Patch_GameWindow.OnEnter** → `FrameGovernor.onFrameStart()`
   - İlk frame'de: `ViewpointBridge.init()` (knob discovery) + `applyAll()`
   - `MeshBudget.beginFrame()` → token bucket'a `budgetMs` refill (cap: 3× refill)
2. Viewpoint, `Hooks.skipIsoCellRender` üzerinden `viewpoint.FP.renderWorld`'ü çağırır
   → **Patch_FP** ölçer → `vpRenderMs` istatistiğine yazılır.
3. Chunk streaming sırasında `viewpoint.world.ChunkBuilds.rebuild` çağrılır
   → **Patch_ChunkBuilds.intercept** (MethodDelegation):
   - `MeshBudget.tryBegin()` false → rebuild atlanır (chunk sonra yeniden denenir)
   - true → `original.call()` + süresi ölçülüp bucket'tan düşülür
4. `IngameState.render` biter → **Patch_IngameState.OnExit** → `Overlay.render()`
   (TextManager, reflection ile bound, Guard'lı)
5. `GameWindow.frameStep` biter → **Patch_GameWindow.OnExit** → `FrameGovernor.onFrameEnd()`
   - frame süresi `frameMs`'e yazılır
   - her 500 ms'de bir `tick()`: p90 > bütçe×1.2 → stepDown; mean < bütçe×0.8 ×3 pencere → stepUp

## Karar mantığı (FrameGovernor)

```
budget = 1000 / targetFps                        # hedef frame süresi
share  = vpRenderMs.mean / frameMs.mean          # Viewpoint'ın frame'den aldığı pay

p90 > budget*1.2  (yavaş):
    share >= 0.45 (GPU-bound)  → renderScale ↓, sonra shadow ↓
    share <  0.45 (CPU-bound)  → meshBudget ↓, sonra renderScale ↓
mean < budget*0.8, 3 pencere üst üste (headroom):
    meshBudget ↑ → shadow ↑ → renderScale ↑      # ucuz olandan başla
```

- İniş anında (rahatsızlık anında hissedilir), çıkış 3 stabil pencere ister (osilasyon
  kötü bir governor'dan iyidir).
- `renderScale` kullanıcı penceresi `[renderScaleMin, renderScaleMax]` ile sınırlıdır.
- Her kademe değişikliği `vpfps.log`'a yazılır ve overlay'de görünür.

## MeshBudget: neden token bucket?

Viewpoint'ın chunk walk'ı worker thread'te koşabiliyor; refill (frame loop) ile
harcama (mesh thread) yarışabilir. Klasik "bu frame'de N tane" sayacı race'li olur.
Token bucket:

- her frame `budgetMs` kadar token ekler (birikme cap'i 3× — borç birikmez),
- her rebuild **ölçülen gerçek maliyetini** düşer (self-calibrating; chunk
  karmaşıklığından bağımsız),
- `AtomicLong` üzerinde koşar → thread-safe, kilit yok,
- token ≤ 0 → rebuild atlanır → Viewpoint o chunk'ı bir sonraki fırsatta yeniden dener.

## ViewpointBridge: knob discovery

Viewpoint alpha bir mod; internal field isimleri güncellemelerle değişebilir.
Bridge, boot'ta (ilk frame'de, oyun tamamen ayakta iken) şunu yapar:

1. Aday sınıfları `Class.forName(name, initialize=false, ...)` ile yoklar
   (`viewpoint.FP`, `viewpoint.Settings`, ... — statik init tetiklenmez).
2. Her knob (`renderScale`, `shadowScale`, `drawDistance`) için aday field
   isimlerini dener (`renderScale`, `resolutionScale`, ...).
3. Instance field ise aynı tipteki static alanlardan singleton'u bulur.
4. **Scale-like guard**: yalnızca float/double VE mevcut değeri (0,1] aralığında
   olan knob'lar governor tarafından yazılabilir. İnt türündeki veya aralık dışı
   değerli alanlar yalnızca raporlanır — yanlış semantikte bir alana sayı yazmak
   görsel bozulmaya yol açabilir; bu yüzden yazılmaz.
5. Bulunan her şey `capabilities()` olarak loglanır ve overlay'de görünür.

Sonuç: Viewpoint güncellendiğinde worst-case "render-scale knob unavailable"
mesajıdır; crash yok, yan etki yok.

## Fail-soft zinciri

```
Guard (circuit breaker, 3 hata → özellik kapanır)
  ├─ Overlay    : TextManagerDrawString bulunamazsa overlay sessizce kapanır
  ├─ her Knob   : field yazılamıyorsa knob devre dışı kalır
  ├─ Lua expose : LuaManager hazır değilse Java tarafı bağımsız çalışır
Config (clamp)  : properties dosyasındaki saçma değerler güvenli aralığa çekilir
meshBudgetEnabled=false → Patch_ChunkBuilds bayt-bayt stok davranış (pass-through)
enabled=false           → tüm governor/passthrough kapanır, ölçüm devam eder
```

## Lua ↔ Java köprüsü

- `Main.exposeLuaBridge()` PZ'nin kendi `LuaManager.exposer.ExposeToLua`'sını
  reflection ile çağırır → `dev.vpfps.Bridge` sınıfı Lua'da global `VPFPSBridge`
  olur. Java metotları yalnızca `double/boolean/String` alır/verir (Kahlua
  sayıları `Double` olarak taşır — `float`/`int` parametreler çağrıyı kırardı).
- Lua tarafı (B42 native `PZAPI.ModOptions`) ayar sayfasını yönetir; değişiklikleri
  ~3 Hz'deki diff-sync tick'i ile Java'ya iter (callback API'sine bağımlılık yok).
- Köprü hiç kurulamazsa: ayarlar `vpfps.properties` üzerinden (5 sn hot-reload)
  yaşar. İki yüz de her zaman çalışır.

## Derleme

- `build.ps1` / `build.sh`: `javac --release 17` → `jar` (Gradal'sız, JDK 17+ yeter).
- `tools/stubs/`: compile-only stub'lar (ZombieBuddy `@Patch`, ByteBuddy
  `@RuntimeType`/`@SuperCall`). Jar'a **girmezler** (yalnız `dev/vpfps` paketlenir);
  runtime'da gerçek annotation'lar ZombieBuddy.jar'dan çözülür.
- Gerçek ZombieBuddy.jar'ın varsa stub yerine ona derleyebilirsin.

## v2.0 eklemeleri

- **Patch_ChunkWalk** (`viewpoint.world.ChunkWalk.walk`, delegation): dururken
  frame başına tekrarlanan chunk yürüyüşünü `ChunkWalkThrottle` ile sınırlar —
  oyuncu 0.25 kareden fazla hareket ettiğinde tam hız, durgunken
  `chunkWalkThrottleMs` aralığı, 800 ms'lik emniyet supabı.
- **Patch_FPCapture** (`viewpoint.FP.captureWorld`, advice): world-capture
  süresini ölçer; overlay ve governor attribution'ı artık render / capture /
  kalan olarak üç parçalı.
- **Dördüncü kalite merdiveni — draw distance**: `ViewpointBridge` drawDistance
  knob'unu int alanlarında da keşfeder, stok baseline'ı saklar ve governor
  `baseline × faktör` yazar (0.70–1.00). Kullanıcı Viewpoint'ın kendi ayarını
  yükseltirse baseline yukarı çeneklenir (asla kavga edilmez).
- **PZOCompat**: PZ_Optimization'i `Zomboid/pzopt` dizini + işaret sınıf
  yoklamalarıyla algılar. PZO'nun class override'ları ve bizim ZombieBuddy
  hook'larımız üst üste biner (ByteBuddy yüklenen sınıfa — yani PZO
  sürümüne — uygulanır). Tek çakışma F9 overlay'i: `deferToPZOpt=auto/on`
  iken bizim overlay kapanır. PZO açık lisans taşımadığı için **sıfır kod
  alınmıştır**; teknikler bağımsız yeniden implemente edilmiştir.
- **StatsExporter**: `statsExport=true` iken her 5 sn'de
  `Zomboid/vpfps-stats.csv`'ye bir satır — oturum sonrası analiz için.
- **autoProfile**: config dosyası yoksa ilk açılışta `availableProcessors()`
  ile mesh bütçesi / walk aralığı tohumlanır (≤4 çekirdek → 1.0 ms / 400 ms,
  ≥12 → 3.0 ms).
