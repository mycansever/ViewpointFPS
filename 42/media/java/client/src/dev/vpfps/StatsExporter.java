package dev.vpfps;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight benchmark harness: appends one CSV row per stats window so a
 * session can be analysed afterwards (spreadsheet-friendly, the same spirit
 * as PZ_Optimization's run logs, built independently).
 *
 * Output: <user>\Zomboid\vpfps-stats.csv
 * Enable: statsExport=true (or the ModOptions tick box).
 */
public final class StatsExporter {

    private static final Path FILE =
            Path.of(System.getProperty("user.home", "."), "Zomboid", "vpfps-stats.csv");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Guard guard = new Guard("stats-export", 2);

    private StatsExporter() {}

    public static void writeRow(float fps, float meanMs, float p90Ms, float p99Ms,
                                float vpRenderMs, float captureMs, float renderScale,
                                float drawDistFactor, float shadowScale, float meshBudgetMs) {
        if (!Config.enabled || !Config.statsExport) return;
        guard.run(() -> {
            boolean newFile = !Files.exists(FILE);
            StringBuilder sb = new StringBuilder(160);
            if (newFile) {
                sb.append("timestamp,fps,frame_mean_ms,frame_p90_ms,frame_p99_ms,")
                  .append("vp_render_ms,world_capture_ms,render_scale,draw_dist_factor,")
                  .append("shadow_scale,mesh_budget_ms")
                  .append(System.lineSeparator());
            }
            sb.append(TS.format(LocalDateTime.now())).append(',')
              .append(String.format("%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                      fps, meanMs, p90Ms, p99Ms, vpRenderMs, captureMs,
                      renderScale, drawDistFactor, shadowScale, meshBudgetMs))
              .append(System.lineSeparator());
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        });
    }
}
