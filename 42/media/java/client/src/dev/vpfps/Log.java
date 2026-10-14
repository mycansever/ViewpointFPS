package dev.vpfps;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Thin logger: console + Zomboid/Logs/vpfps.log. Never throws; if the file
 * cannot be written it silently degrades to console-only.
 */
public final class Log {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Path DIR = Path.of(System.getProperty("user.home", "."), "Zomboid", "Logs");
    private static final Path FILE = DIR.resolve("vpfps.log");
    private static boolean fileOk = true;

    private Log() {}

    public static void info(String msg)  { write("INFO ", msg); }
    public static void warn(String msg)  { write("WARN ", msg); }
    public static void error(String msg) { write("ERROR", msg); }
    public static void debug(String msg) { if (Config.debug) write("DEBUG", msg); }

    private static void write(String lvl, String msg) {
        String line = TS.format(LocalTime.now()) + " " + lvl + " " + msg;
        System.out.println("[VPFPS] " + line);
        if (!fileOk) return;
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable t) {
            fileOk = false;
        }
    }
}
