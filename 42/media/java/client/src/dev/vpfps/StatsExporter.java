package dev.vpfps;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight benchmark harness: appends one CSV row per stats window.
 * Output: <user>\Zomboid\vpfps-stats.csv
 * Columns: timestamp, fps, mean/p90/p99 ms, gpuBusy%, vpRenderMs, captureMs,
 *          cpuFactor, gpuFactor.
 */
public final class StatsExporter {

    private static final Path FILE =
            Path.of(System.getProperty("user.home", "."), "Zomboid", "vpfps-stats.csv");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Guard guard = new Guard("stats-export", 2);

    private StatsExporter() {}

    public static void writeRow(float fps, float meanMs, float p90Ms, float p99Ms,
                                float gpuBusyPct, float vpRenderMs, float captureMs,
                                float cpuFactor, float gpuFactor) {
        if (!Config.enabled || !Config.statsExport) return;
        guard.run(() -> {
            boolean newFile = !Files.exists(FILE);
            StringBuilder sb = new StringBuilder(160);
            if (newFile) {
                sb.append("timestamp,fps,frame_mean_ms,frame_p90_ms,frame_p99_ms,")
                  .append("gpu_busy_pct,vp_render_ms,world_capture_ms,cpu_factor,gpu_factor")
                  .append(System.lineSeparator());
            }
            sb.append(TS.format(LocalDateTime.now())).append(',')
              .append(String.format("%.1f,%.2f,%.2f,%.2f,%.0f,%.2f,%.2f,%.2f,%.2f",
                      fps, meanMs, p90Ms, p99Ms, gpuBusyPct, vpRenderMs, captureMs,
                      cpuFactor, gpuFactor))
              .append(System.lineSeparator());
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        });
    }
}
