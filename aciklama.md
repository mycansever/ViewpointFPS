# Steam Workshop sayfası için açıklama (BBCode)
#
# KULLANIM: Aşağıdaki çizginin ALTINDAKİ her şeyi kopyala ve Steam Workshop'ta
# "Açıklama" kutusuna olduğu gibi yapıştır. Steam BBCode kullanır ([h1], [b], [list]...)
# bu yüzden Markdown değil BBCode yazıldı.
#
# Yüklerken etiket önerisi: Build 42, Graphics / Performance, Client-Only
# Gerekli öğeler (Workshop'ta "Gerekli Öğeler" bölümüne ekle):
#   ZombieBuddy:      https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853
#   Project Viewpoint: https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528
#
# ─────────────────── BURADAN AŞAĞISINI KOPYALA ───────────────────

[h1]🎯 Viewpoint FPS Optimization[/h1]

An adaptive performance layer for [b]Project Viewpoint[/b] — the first/third-person 3D renderer — on Project Zomboid [b]Build 42.21[/b]. It applies the [url=https://github.com/xD3I/PZ_Optimization]PZ_Optimization[/url] philosophy (measure, decide, always reversible) to Viewpoint's own render pipeline. Client-side only.

[hr]

[h1]⚡ What it does[/h1]

[list]
[*][b]Chunk mesh budget[/b] — kills the multi-frame freezes caused by stacked chunk-mesh rebuilds while streaming chunks (driving at 120 km/h, spinning the camera). Sets a per-frame time budget (default 2 ms); leftover chunks simply rebuild next frame.
[*][b]Adaptive quality governor[/b] — every 500 ms it reads the frame-time distribution (p90 / mean) and walks independent quality ladders: render scale, shadow scale, mesh budget. Quality drops instantly; recovery needs 3 stable windows, so it never oscillates.
[*][b]GPU vs CPU attribution[/b] — measures Viewpoint's own render pass. GPU-bound? Resolution and shadows go down first. Game-thread-bound? The mesh budget shrinks first. Lowering the wrong thing gains nothing — this mod doesn't do that.
[*][b]Dynamic resolution[/b] — under GPU pressure, scales Viewpoint's internal render resolution inside your configured window (default 0.50–1.00).
[*][b]Idle chunk-walk throttle[/b] — while you stand still (looting, fighting, crafting) Viewpoint still re-scans its chunks every frame. This limits that scan to a few per second when idle and restores full rate the instant you move — free game-thread time the vanilla settings can never give you.
[*][b]Adaptive draw distance[/b] — a fourth governor ladder that scales Viewpoint's chunk/draw distance (0.70–1.00x of its stock value) under game-thread pressure. Fewer chunks = fewer meshes and draws. The stock baseline is remembered and ratchets up if you raise Viewpoint's own setting, so the governor never fights you.
[*][b]Live stats overlay[/b] — fps, 1% low, frame time, p99, VP render share, capture time, mesh stats and governor state. Toggle with [b]F9[/b] (rebindable). Color-coded by health.
[*][b]Benchmark CSV export[/b] — optional fps/p90/p99/render-share log appended to Zomboid\vpfps-stats.csv every 5 seconds.
[*][b]PZ_Optimization compatible[/b] — detects xD3I's PZ_Optimization at runtime and stacks cleanly with it (their class overrides + our runtime hooks). Our overlay steps aside for theirs when both are installed. No code is shared (PZO carries no open license; techniques only, reimplemented independently).
[*][b]Cannot crash your game[/b] — Viewpoint is alpha and updates often, so this mod [b]never hard-references Viewpoint's classes[/b]. Everything is discovered at runtime via reflection. If a Viewpoint update renames a field, the related optimization reports "unavailable" and everything else keeps working. Every feature has a kill switch.
[/list]

[h1]📋 Requirements[/h1]

[list]
[*]Project Zomboid [b]Build 42.21[/b] (default Steam branch)
[*][url=https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853]ZombieBuddy[/url] (Java mod loader, one-time setup)
[*][url=https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528]Project Viewpoint[/url]
[/list]

[h1]🔧 Installation[/h1]

[list=1]
[*]Subscribe and let Steam download.
[*]In the game's [b]MODS[/b] screen enable, in order: [b]ZombieBuddy → Viewpoint → Viewpoint FPS Optimization[/b], then Accept.
[*]ZombieBuddy will ask to approve this mod's jar on first launch — tick [b]Allow[/b].
[*]Enter a world, press [b]O[/b] for first person, press [b]F9[/b] to see the overlay.
[/list]

[i]Verify via Zomboid\Logs\vpfps.log — you should see "knob 'renderScale' -> ..." lines.[/i]

Multiplayer: client-side only, nothing to install on the server. To let players on a server enable it, add it to the server's mod list alongside the other two: [i]Mods=ZombieBuddy;Viewpoint;ViewpointFPS[/i]

[h1]⚙️ Settings[/h1]

Everything is live-tunable, no restart needed: [b]Options → MODS → Viewpoint FPS Optimization[/b] (tick boxes, sliders, rebindable F9), or edit [i]Zomboid\vpfps.properties[/i] — it hot-reloads within ~5 seconds. Full tuning guide with profiles for weak CPUs / high-refresh / weak GPUs is in the [url=https://github.com/mycansever/ViewpointFPS]GitHub repository[/url] (docs/TUNING.md).

[list]
[*][b]Enable optimizations[/b] — master kill switch (everything passes through, stock behaviour)
[*][b]Adaptive quality governor[/b] — or fixed values
[*][b]Target FPS[/b] — the frame-time budget the governor holds (15–480)
[*][b]Dynamic resolution[/b] — with min/max render-scale window
[*][b]Adaptive draw distance[/b] — with min factor slider (0.5–1.0)
[*][b]Chunk mesh budget[/b] — ms per frame (0.5–8)
[*][b]Chunk-walk idle throttle[/b] — with idle interval slider (100–1000 ms)
[*][b]Performance overlay[/b] — with rebindable toggle key
[*][b]Export stats to CSV[/b] — benchmark log of your session
[/list]

[h1]🛠️ Troubleshooting[/h1]

[list]
[*][b]Nothing changed in FPS[/b] — check the overlay's "VP render %" line; if it's low, the bottleneck is the game thread, lower meshBudgetMs to 1.0 and make sure targetFps is realistic.
[*][b]"knob not found" in the log[/b] — your Viewpoint version renamed a field; the mod keeps working with mesh budget + overlay. Update or edit ViewpointBridge.KNOBS and rebuild.
[*][b]Suspected crash[/b] — set enabled=false in vpfps.properties and check Zomboid\Logs\vpfps.log.
[/list]

[h1]📜 License — MIT[/h1]

This mod is released under the [url=https://opensource.org/licenses/MIT]MIT License[/url]. Copy it, modify it, sell it, bundle it in your own mod, develop and publish your own version — [b]as long as you keep the original copyright notice and license text[/b] (i.e. credit the original project). The author develops this mod however they feel like; you are invited to build on it — just keep the credit.

Project Zomboid © The Indie Stone. Project Viewpoint and ZombieBuddy belong to their respective authors. This is an unrelated third-party work, not affiliated with any of them.

[hr]

[h1]🇹🇷 Türkçe[/h1]

[h1]🎯 Viewpoint FPS Optimization[/h1]

Project Zomboid [b]Build 42.21[/b]'deki [b]Project Viewpoint[/b] (first/third-person 3D renderer) modu için adaptif performans katmanı. [url=https://github.com/xD3I/PZ_Optimization]PZ_Optimization[/url] felsefesini (ölç, karar ver, her zaman geri alınabilir) Viewpoint'ın kendi render pipeline'ına uygular. Sadece client tarafında çalışır.

[h1]⚡ Ne yapar?[/h1]

[list]
[*][b]Chunk mesh bütçesi[/b] — Viewpoint chunk akıtırken (120 km/h'de araba kullanırken, hızlı dönüşlerde) mesh rebuild'lerin üst üste binmesiyle oluşan çok frame'lik donmaları ortadan kaldırır. Frame başına zaman bütçesi (varsayılan 2 ms) koyar; bütçe dolunca kalan chunk'lar bir sonraki frame'e ertelenir.
[*][b]Adaptif kalite governor'ı[/b] — Her 500 ms'de frame-time dağılımını okur (p90/ortalama) ve render scale, gölge kalitesi, mesh bütçesi merdivenlerinde otomatik iner/çıkar. Kalite düşürme anında; geri yükseltme 3 stabil pencere gerektirir (git-gel yok).
[*][b]GPU / CPU darboğaz tespiti[/b] — Viewpoint'ın render pass'inin frame'den aldığı payı ölçer. Darboğaz GPU'daysa önce çözünürlük/gölge düşer, game thread'deyse önce mesh bütçesi. Yanlış şeyi kısmanın faydası yoktur — bu mod bunu yapmaz.
[*][b]Dinamik çözünürlük[/b] — GPU yükü yüksekken Viewpoint'ın dahili render çözünürlüğünü ayarladığın aralıkta (varsayılan 0.50–1.00) ölçekler.
[*][b]Idle chunk-walk throttle[/b] — olduğun yerde dururken (yağmalama, dövüş, craft) Viewpoint chunk'larını her frame yeniden tarar. Bu, durgunken taramayı saniyede birkaç kezle sınırlar; hareket ettiğin an tam hız geri döner — vanilla ayarlarının asla veremeyeceği bedava game-thread zamanı.
[*][b]Adaptif draw distance[/b] — governor'a dördüncü merdiven: game-thread baskısında Viewpoint'ın chunk/draw mesafesini (stok değerinin 0.70–1.00 katı) ölçekler. Daha az chunk = daha az mesh, daha az draw. Stok baseline hatırlanır ve sen Viewpoint'ın kendi ayarını yükseltirsen yukarı çekilir — governor seninle asla kavga etmez.
[*][b]Canlı istatistik overlay'i[/b] — FPS, 1% low, frame time, p99, VP render payı, capture süresi, mesh istatistikleri ve governor durumu. [b]F9[/b] ile açılır/kapanır (tuş değiştirilebilir), duruma göre renklenir.
[*][b]Benchmark CSV export[/b] — opsiyonel: her 5 saniyede fps/p90/p99/render-payı satırları Zomboid\vpfps-stats.csv dosyasına yazılır.
[*][b]PZ_Optimization uyumlu[/b] — xD3I'nin PZ_Optimization modunu runtime'da algılar ve sorunsuz üst üste biner (onların class override'ları + bizim runtime hook'larımız). İkisi de kuruluysa bizim overlay onlarınkinin yerine geçmez (kenara çekilir). Kod paylaşımı yoktur (PZO açık lisans taşımaz; sadece teknikler, bağımsız yeniden implementasyon).
[*][b]Crash edemez[/b] — Viewpoint alpha bir mod ve sık güncelleniyor. Bu mod Viewpoint sınıflarını [b]hiç doğrudan referans etmez[/b]; her şey reflection ile runtime'da keşfedilir. Bir güncelleme alan isimlerini değiştirirse ilgili optimize "unavailable" raporlar ve diğer her şey çalışmaya devam eder. Her özelliğin kill switch'i vardır.
[/list]

[h1]📋 Gereksinimler[/h1]

[list]
[*]Project Zomboid [b]Build 42.21[/b] (Steam varsayılan dalı)
[*][url=https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853]ZombieBuddy[/url] (Java mod loader, tek seferlik kurulum)
[*][url=https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528]Project Viewpoint[/url]
[/list]

[h1]🔧 Kurulum[/h1]

[list=1]
[*]Abone ol, Steam indirmesini bekle.
[*]Oyundaki [b]MODS[/b] ekranında sırayla etkinleştir: [b]ZombieBuddy → Viewpoint → Viewpoint FPS Optimization[/b], sonra Accept.
[*]ZombieBuddy ilk açılışta bu modun jar'ını onaylamanı isteyecek — [b]Allow[/b] işaretle.
[*]Oyuna gir, [b]O[/b] ile first-person'a geç, overlay için [b]F9[/b]'a bas.
[/list]

[i]Doğrulama: Zomboid\Logs\vpfps.log dosyasında "knob 'renderScale' -> ..." satırlarını görmelisin.[/i]

Multiplayer: tamamen client-side, sunucuya bir şey kurulmaz. Sunucudaki oyuncuların etkinleştirebilmesi için sunucu mod listesine ekle: [i]Mods=ZombieBuddy;Viewpoint;ViewpointFPS[/i]

[h1]⚙️ Ayarlar[/h1]

Her şey canlı ayarlanabilir, restart gerekmez: [b]Options → MODS → Viewpoint FPS Optimization[/b] veya [i]Zomboid\vpfps.properties[/i] — ~5 saniyede hot-reload olur. Detaylı tuning rehberi [url=https://github.com/mycansever/ViewpointFPS]GitHub reposunda[/url] (docs/TUNING.md).

[list]
[*][b]Optimizasyonları etkinleştir[/b] — master kill switch (her şey passthrough, stok davranış)
[*][b]Adaptif kalite governor'ı[/b] — ya da sabit değerler
[*][b]Hedef FPS[/b] — governor'ın tutmaya çalıştığı frame süresi bütçesi (15–480)
[*][b]Dinamik çözünürlük[/b] — min/max render-scale aralığıyla
[*][b]Adaptif draw distance[/b] — min faktör slider'ıyla (0.5–1.0)
[*][b]Chunk mesh bütçesi[/b] — frame başına ms (0.5–8)
[*][b]Idle chunk-walk throttle[/b] — idle aralık slider'ıyla (100–1000 ms)
[*][b]Performans overlay'i[/b] — F9 tuşu değiştirilebilir
[*][b]CSV'ye istatistik aktar[/b] — oturumunun benchmark kaydı
[/list]

[h1]🛠️ Sorun giderme[/h1]

[list]
[*][b]FPS değişmedi[/b] — overlay'deki "VP render %" satırına bak; düşükse darboğaz game thread'dir, meshBudgetMs'i 1.0'a düşür ve targetFps'in gerçekçi olduğundan emin ol.
[*][b]Log'da "knob not found"[/b] — Viewpoint sürümün bir alanı yeniden adlandırmış; mod mesh bütçesi + overlay ile çalışmaya devam eder. Güncelle ya da ViewpointBridge.KNOBS listesini düzenleyip yeniden derle.
[*][b]Crash şüphesi[/b] — vpfps.properties içinde enabled=false yap ve Zomboid\Logs\vpfps.log'a bak.
[/list]

[h1]📜 Telif — MIT[/h1]

Bu mod [url=https://opensource.org/licenses/MIT]MIT Lisansı[/url] ile yayınlanır. Kopyala, değiştir, sat, kendi modunda kullan, kendi versiyonunu geliştirip yayınla — tek şartla: [b]orijinal telif notunu ve lisans metnini koru[/b] (yani orijinal projeden bahset/atıf yap). Mod, yazarının keyfine göre geliştirilir; sen de üzerine inşa etmeye davetlisin — sadece krediyi koru.

Project Zomboid © The Indie Stone. Project Viewpoint ve ZombieBuddy kendi yazarlarına aittir. Bu mod onlarla bağlantısız bir üçüncü parti çalışmadır.
