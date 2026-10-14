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
[*][b]Live stats overlay[/b] — fps, frame time, p99, VP render share, mesh stats and governor state. Toggle with [b]F9[/b] (rebindable). Color-coded by health.
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

Everything is live-tunable, no restart needed: [b]Options → MODS → Viewpoint FPS Optimization[/b] (tick boxes, sliders, rebindable F9), or edit [i]Zomboid\vpfps.properties[/i] — it hot-reloads within ~5 seconds. Full tuning guide with profiles for weak CPUs / high-refresh / weak GPUs is in the [url=https://github.com/]GitHub repository[/url] (docs/TUNING.md).

[list]
[*][b]Enable optimizations[/b] — master kill switch (everything passes through, stock behaviour)
[*][b]Adaptive quality governor[/b] — or fixed values
[*][b]Target FPS[/b] — the frame-time budget the governor holds (15–480)
[*][b]Dynamic resolution[/b] — with min/max render-scale window
[*][b]Mesh budget[/b] — ms per frame (0.5–8)
[*][b]Performance overlay[/b] — with rebindable toggle key
[/list]

[h1]🛠️ Troubleshooting[/h1]

[list]
[*][b]Nothing changed in FPS[/b] — check the overlay's "VP render %" line; if it's low, the bottleneck is the game thread, lower meshBudgetMs to 1.0 and make sure targetFps is realistic.
[*][b]"knob not found" in the log[/b] — your Viewpoint version renamed a field; the mod keeps working with mesh budget + overlay. Update or edit ViewpointBridge.KNOBS and rebuild.
[*][b]Suspected crash[/b] — set enabled=false in vpfps.properties and check Zomboid\Logs\vpfps.log.
[/list]

[h1]📜 License — public domain[/h1]

This mod is released under [url=https://unlicense.org]The Unlicense[/url] (public domain). Copy it, modify it, sell it, bundle it in your own mod, develop and publish your own version — [b]no permission and no attribution required[/b]. The author develops this mod however they feel like; you are invited to do exactly the same.

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
[*][b]Canlı istatistik overlay'i[/b] — FPS, frame time, p99, VP render payı, mesh istatistikleri ve governor durumu. [b]F9[/b] ile açılır/kapanır (tuş değiştirilebilir), duruma göre renklenir.
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

Her şey canlı ayarlanabilir, restart gerekmez: [b]Options → MODS → Viewpoint FPS Optimization[/b] (tick box'lar, slider'lar, F9 tuşu değiştirilebilir) veya [i]Zomboid\vpfps.properties[/i] dosyasını düzenle — ~5 saniyede hot-reload olur. Zayıf CPU / yüksek refresh / zayıf GPU profilleriyle birlikte detaylı tuning rehberi GitHub reposunda (docs/TUNING.md).

[h1]🛠️ Sorun giderme[/h1]

[list]
[*][b]FPS değişmedi[/b] — overlay'deki "VP render %" satırına bak; düşükse darboğaz game thread'dir, meshBudgetMs'i 1.0'a düşür ve targetFps'in gerçekçi olduğundan emin ol.
[*][b]Log'da "knob not found"[/b] — Viewpoint sürümün bir alanı yeniden adlandırmış; mod mesh bütçesi + overlay ile çalışmaya devam eder. Güncelle ya da ViewpointBridge.KNOBS listesini düzenleyip yeniden derle.
[*][b]Crash şüphesi[/b] — vpfps.properties içinde enabled=false yap ve Zomboid\Logs\vpfps.log'a bak.
[/list]

[h1]📜 Telif — kamu malı[/h1]

Bu mod [url=https://unlicense.org]The Unlicense[/url] (kamu malı / public domain) ile yayınlanır. Kopyala, değiştir, sat, kendi modunda kullan, kendi versiyonunu geliştirip yayınla — [b]izin almaya ve atıf yapmaya gerek yok[/b]. Mod, yazarının keyfine göre geliştirilir; sen de aynısını yapmaya davetlisin.

Project Zomboid © The Indie Stone. Project Viewpoint ve ZombieBuddy kendi yazarlarına aittir. Bu mod onlarla bağlantısız bir üçüncü parti çalışmadır.
