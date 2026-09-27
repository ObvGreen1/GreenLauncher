package me.obvgreen.core.settings;

import me.obvgreen.util.Json;
import me.obvgreen.util.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves settings.json and exposes a global singleton. */
public final class SettingsStore {
    private static Settings instance;
    private static final Path FILE = Paths.LAUNCHER_DIR.resolve("settings.json");

    public static synchronized Settings get() {
        if (instance == null) {
            Settings s = null;
            try {
                if (Files.exists(FILE)) s = Json.fromFile(FILE, Settings.class);
            } catch (IOException ignored) {}
            instance = s != null ? s : new Settings();
            me.obvgreen.util.Http.reconfigure();
        }
        return instance;
    }

    public static synchronized void save() {
        try {
            Json.toFile(FILE, instance);
        } catch (IOException e) {
            System.err.println("Failed saving settings: " + e.getMessage());
        }
    }

    public static synchronized void reload() {
        instance = null;
        get();
    }
}

