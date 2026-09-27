package me.obvgreen.util;

import java.nio.file.Path;

/** Central filesystem locations. */
public final class Paths {
    public static final Path LAUNCHER_DIR;
    static {
        String appdata = System.getenv("APPDATA");
        Path base = appdata != null ? java.nio.file.Path.of(appdata) : Path.of(System.getProperty("user.home"));
        LAUNCHER_DIR = base.resolve("GreenLauncher");
    }
    public static final Path INSTANCES_DIR = LAUNCHER_DIR.resolve("instances");
    public static final Path RUNTIMES_DIR = LAUNCHER_DIR.resolve("runtimes");

    private Paths() {}
}
