package me.obvgreen.core.launch;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.instance.InstanceStore;
import me.obvgreen.core.settings.SettingsStore;
import me.obvgreen.ui.ConsoleWindow;
import me.obvgreen.util.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the game process in the background: streams output to the console window,
 * tracks playtime, and applies the show-console / close-on-quit / crash-dialog settings.
 */
public final class ProcessManager {
    private static final Map<String, Process> RUNNING = new HashMap<>();
    /** Instances currently being prepared/launched (prevents double-launch races). */
    private static final java.util.Set<String> STARTING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private ProcessManager() {}

    /** Optional UI hook: fired (on the FX thread) whenever an instance changes state. */
    public static volatile Runnable onStateChange;

    private static void fireStateChange() {
        Runnable r = onStateChange;
        if (r != null) Platform.runLater(r);
    }

    public static boolean isRunning(Instance inst) {
        if (STARTING.contains(inst.id)) return true;
        Process p = RUNNING.get(inst.id);
        return p != null && p.isAlive();
    }

    /** True while the instance is being prepared (downloads etc.), before the process exists. */
    public static boolean isStarting(Instance inst) {
        return STARTING.contains(inst.id);
    }

    public static void kill(Instance inst) {
        Process p = RUNNING.get(inst.id);
        if (p != null && p.isAlive()) p.destroyForcibly();
    }

    /** Async: prepares downloads on a worker thread, then starts monitoring. */
    public static void launchAsync(Instance inst, me.obvgreen.core.account.Account account) {
        if (!STARTING.add(inst.id)) return; // already launching/running
        fireStateChange();
        new Thread(() -> {
            List<String> log = new ArrayList<>();
            ConsoleWindow console = ConsoleWindow.get();
            ConsoleWindow.attachBuffer(log);
            try {
                Platform.runLater(() -> console.append("[GreenLauncher] Preparing instance: " + inst.name + "\n"));
                if (SettingsStore.get().showConsoleOnLaunch) Platform.runLater(() -> console.showFor(inst.name));
                java.util.function.Consumer<String> sink = line -> {
                    log.add(line);
                    Platform.runLater(() -> console.append(line + "\n"));
                };
                java.util.function.DoubleConsumer progress = d ->
                        Platform.runLater(() -> console.status(String.format("Downloading... %.0f%%", d * 100)));
                Process p = Launcher.launch(inst, account, sink, progress);
                RUNNING.put(inst.id, p);
                Platform.runLater(() -> console.status("Game running"));
                fireStateChange();
                inst.lastPlayed = System.currentTimeMillis();
                InstanceStore.save(inst);

                long startedAt = System.currentTimeMillis();
                Thread watcher = new Thread(() -> {
                    try (var in = p.getInputStream()) {
                        byte[] buf = new byte[8192];
                        StringBuilder pending = new StringBuilder();
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            pending.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                            int idx;
                            while ((idx = pending.indexOf("\n")) != -1) {
                                String line = pending.substring(0, idx);
                                pending.delete(0, idx + 1);
                                sink.accept(line);
                            }
                        }
                    } catch (IOException ignored) {}
                }, "mc-output");
                watcher.setDaemon(true);
                watcher.start();

                Thread ticker = new Thread(() -> {
                    while (p.isAlive()) {
                        try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                        inst.playtimeSeconds += 5;
                    }
                });
                ticker.setDaemon(true);
                ticker.start();

                int code = p.waitFor();
                inst.playtimeSeconds += (System.currentTimeMillis() - startedAt) / 1000 % 5;
                RUNNING.remove(inst.id);
                STARTING.remove(inst.id);
                fireStateChange();
                InstanceStore.save(inst);
                Platform.runLater(() -> console.append("[GreenLauncher] Instance '" + inst.name
                        + "' exited with code " + code + "\n"));
                writeLog(inst, log);
                boolean crashed = code != 0;
                if (crashed && SettingsStore.get().showConsoleOnCrash) {
                    Platform.runLater(() -> {
                        console.showFor(inst.name + " (crashed, exit code " + code + ")");
                        console.status("Game crashed");
                    });
                }
                if (SettingsStore.get().closeOnQuit && !anyRunning()) {
                    Platform.runLater(() -> {
                        console.append("[GreenLauncher] Closing launcher (setting enabled)\n");
                        Platform.exit();
                    });
                } else if (crashed) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.ERROR,
                                "Minecraft crashed (exit code " + code + "). Check the console window for details.");
                        a.setHeaderText("Game crashed");
                        a.show();
                    });
                }
            } catch (Exception e) {
                STARTING.remove(inst.id);
                fireStateChange();
                Platform.runLater(() -> {
                    console.append("[GreenLauncher] Launch failed: " + e + "\n");
                    console.showFor(inst.name + " (launch failed)");
                    console.status("Launch failed");
                });
                writeLog(inst, log);
            }
        }, "launcher-" + inst.id).start();
    }

    private static boolean anyRunning() {
        return RUNNING.values().stream().anyMatch(Process::isAlive);
    }

    private static void writeLog(Instance inst, List<String> log) {
        try {
            Path dir = Paths.LAUNCHER_DIR.resolve("logs");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Files.writeString(dir.resolve(inst.name.replaceAll("[^\\w.-]+", "_") + "-" + stamp + ".log"),
                    String.join("\n", log));
        } catch (IOException ignored) {}
    }
}
