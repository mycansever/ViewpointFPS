# Tuning rehberi

> **v2.0:** yeni anahtarlar — `drawDistanceAdaptive` / `drawDistanceMin` (governor 0.70–1.00×
> aralığında chunk mesafesini ölçekler), `chunkWalkThrottleEnabled` / `chunkWalkThrottleMs`
> (dururken chunk taraması ~4×/sn'e iner), `statsExport` (CSV benchmark), `deferToPZOpt`
> (PZ_Optimization algılandığında overlay'miz kenara çekilir), `autoProfile` (ilk açılışta
> CPU çekirdek sayısına göre varsayılanlar).

Tüm ayarlar canlıdır: Options > MODS > **Viewpoint FPS Optimization** (oyun içinde)
veya `%USERPROFILE%\Zomboid\vpfps.properties` (≈5 sn'de hot-reload). Şablon:
`tools/vpfps.properties.example`.

## Hızlı profiller

| Senaryo | governor | targetFps | meshBudgetMs | renderScaleMin |
|---|---|---|---|---|
| Genel (önerilen) | adaptive | 60 | 2.0 | 0.50 |
| Zayıf CPU / laptop | adaptive | 45–60 | 1.0 | 0.50 |
| Yüksek refresh (120/144 Hz) | adaptive | 120/144 | 2.0 | 0.60 |
| Zayıf GPU (eski ekran kartı) | adaptive | 60 | 2.0 | **0.40** |
| "Sadece mesh budget, görüntüye dokunma" | off | — | 1.0–2.0 | — |
| Sabit düşük çözünürlük | fixed | — | 2.0 | — (`renderScaleFixed=0.75`) |

## Nasıl okunur (F9 overlay)

```
VPFPS 1.0.0 | ADAPTIVE | target 60 fps
FPS 47.3   frame 21.1 ms   p99 34.8 ms          ← durum (yeşil/sarı/kırmızı)
VP render 14.2 ms (67%)   mesh 3.1 ms avg   12 skipped
scale 0.70   shadow 0.75   mesh budget 1.0 ms    ← aktif kademe değerleri
render scale 0.70 (p90 26.1ms > 21.9ms, GPU-bound)   ← son governor eylemi
knobs: renderScale=FP.renderScale, shadowScale=FP.shadowScale
```

- **VP render (%)**: yüksekse (%45+) darboğaz GPU → render scale/shadow düşürülüyor
  demektir. Düşükse darboğaz game thread → mesh budget devreye girer.
- **mesh X ms avg, N skipped**: skipped sayısı sürekli artıyorsa ve görsel olarak
  geç gelen chunk'lar rahatsız ediyorsa `meshBudgetMs`'i artır (2.0 → 4.0).
  Donma hissi devam ediyorsa azalt (2.0 → 1.0) — erteleme, donmadan iyidir.
- **p99**: ortalama değil kuyruk. 60 fps hedefinde p99 < ~25 ms iyi bir hedeftir.

## Governor'ı anlamak

- **İniş** anında yapılır: `p90 > (1000/targetFps) × 1.2`.
- **Çıkış** temkinlidir: `mean < (1000/targetFps) × 0.8` AND 3 karar penceresi
  (toplam ~1.5 sn) boyunca stabil. Bu, kalitenin git-gel yapmasını önler.
- Kademe sırası (iniş): GPU-bound ise `renderScale → shadow`; CPU-bound ise
  `meshBudget → renderScale`. Çıkışta ters: `meshBudget → shadow → renderScale`.
- Kademe adımları: scale `0.50 0.60 0.70 0.80 0.90 1.00`, mesh `0.5 1 2 4 8 ms`,
  shadow `0.50 0.75 1.00`.

## Sık yapılan hatalar

- **`targetFps`'i oyunun kendi FPS limiter'ından yüksek yapmak** boşuna kalite
  kaybettirir: governor asla tatmin olamaz ve sürekli iner. Oyun limiter'ın 60 ise
  `targetFps=60` bırak.
- **`renderScaleMin=0.25`** gibi aşırı değerler bulanık bir görüntü verir; 0.50
  çoğu GPU'da fark edilmeden %50'ye varan GPU kazancı sağlar.
- **Fixed modda `renderScaleFixed` ile oynayıp adaptive'i beklemek**: fixed modda
  governor asla gözlem yapmaz; `adaptive=true` iken `renderScaleFixed` yok sayılır.

## Viewpoint güncellemesi sonrası

1. Oyunu aç, `Zomboid\Logs\vpfps.log`'a bak.
2. `knob 'renderScale' -> ...` satırı varsa her şey yolunda.
3. `knob 'renderScale' not found` ise: yeni field ismini `ViewpointBridge.KNOBS`
   dizisine ekle (aday isimler zaten birden fazladır), `build.ps1` ile yeniden derle.
4. Mesh budget ve overlay Viewpoint'ten bağımsızdır; knob kaybı bunları etkilemez.
