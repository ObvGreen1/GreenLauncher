package me.obvgreen.core.launch;

import com.google.gson.JsonObject;
import me.obvgreen.core.account.Account;
import me.obvgreen.core.download.GameDownloader;
import me.obvgreen.core.download.VersionJson;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.java.JavaRuntimeManager;
import me.obvgreen.core.settings.SettingsStore;
import me.obvgreen.util.Paths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Builds and runs the Minecraft process for an instance. */
public final class Launcher {
    private Launcher() {}

    /** Returns the java command line for the instance (without running it). */
    public static List<String> buildCommand(Instance inst, Account account, JsonObject versionJson) throws Exception {
        var settings = SettingsStore.get();
        JsonObject root = GameDownloader.rootJson(inst, versionJson);
        int major = JavaRuntimeManager.requiredMajor(root, inst.mcVersion);
        String javaOverride = !inst.javaPathOverride.isBlank() ? inst.javaPathOverride : settings.defaultJavaPath;
        Path javaExe = JavaRuntimeManager.resolveJava(major, javaOverride, null);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe.toString());
        cmd.add("-Xmx" + settings.memoryMB + "M");
        cmd.add("-Xms" + Math.min(512, settings.memoryMB) + "M");
        if (!settings.customJavaArgs.isBlank()) {
            for (String a : settings.customJavaArgs.split("\\s+")) if (!a.isBlank()) cmd.add(a);
        }
        cmd.add("-Djava.library.path=" + inst.minecraftDir().resolve("natives"));
        cmd.add("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"); // safety on odd GPUs
        String cp = GameDownloader.classpath(inst, versionJson);
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(versionJson.get("mainClass").getAsString());

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", account.username);
        vars.put("auth_uuid", account.uuid);
        vars.put("auth_access_token", "0");
        vars.put("auth_xuid", "0");
        vars.put("clientid", "greenlauncher");
        vars.put("user_type", "offline");
        vars.put("user_properties", "{}");
        vars.put("version_name", versionJson.get("id").getAsString());
        vars.put("version_type", root.has("type") ? root.get("type").getAsString() : "release");
        vars.put("game_directory", inst.minecraftDir().toString());
        vars.put("assets_root", inst.assetsDir().toString());
        vars.put("assets_index_name", root.getAsJsonObject("assetIndex").get("id").getAsString());
        vars.put("launcher_name", "GreenLauncher");
        vars.put("launcher_version", "1.0.0");
        vars.put("classpath", cp);
        vars.put("classpath_separator", ";");
        vars.put("library_directory", inst.librariesDir().toString());
        vars.put("natives_directory", inst.minecraftDir().resolve("natives").toString());
        vars.put("resolution_width", "854");
        vars.put("resolution_height", "480");
        vars.put("quickPlayPath", "");
        vars.put("quickPlaySingleplayer", "");
        vars.put("quickPlayMultiplayer", "");
        vars.put("quickPlayRealms", "");

        List<String> gameArgs = VersionJson.gameArgsConditional(versionJson);
        for (int i = 0; i < gameArgs.size(); i++) {
            String a = gameArgs.get(i);
            if (a.startsWith("${") && a.endsWith("}")) {
                String key = a.substring(2, a.length() - 1);
                if (vars.containsKey(key)) { cmd.add(vars.get(key)); continue; }
                // unknown placeholder: skip it and its value if next token is a known flag pair
                if (i + 1 < gameArgs.size() && !gameArgs.get(i + 1).startsWith("${") && a.contains("quickPlay")) {
                    i++;
                }
                continue;
            }
            cmd.add(a);
        }
        return cmd;
    }

    /** Ensures all downloads are done, then starts the process. Returns the live process. */
    public static Process launch(Instance inst, Account account, Consumer<String> logger,
                                 java.util.function.DoubleConsumer progress) throws Exception {
        GameDownloader.ensureVanilla(inst, progress);
        JsonObject versionJson = GameDownloader.loadVersionJson(inst, inst.versionId);
        GameDownloader.ensureLibraries(inst, versionJson, progress);
        GameDownloader.ensureAssets(inst, versionJson, progress);

        List<String> cmd = buildCommand(inst, account, versionJson);
        logger.accept(String.join(" ", cmd));
        Files.createDirectories(inst.minecraftDir());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(inst.minecraftDir().toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        return p;
    }

    /** Writes a .lnk on the desktop that launches this instance via the launcher binary. */
    public static void createShortcut(Instance inst) throws Exception {
        String desktop = System.getenv("USERPROFILE") + "\\Desktop";
        Path target = Path.of(desktop, inst.name + ".lnk");
        String self = ProcessHandle.current().info().command().orElse("javaw");
        String selfDir = Paths.LAUNCHER_DIR.toString();
        String args = "--launch \"" + inst.id + "\"";
        String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + target + "');"
                + "$s.TargetPath='" + self + "';$s.Arguments='" + args + "';"
                + "$s.WorkingDirectory='" + selfDir + "';$s.IconLocation='" + self + ",0';$s.Save()";
        new ProcessBuilder("powershell", "-NoProfile", "-Command", ps).start().waitFor();
        if (!Files.exists(target)) throw new IllegalStateException("Shortcut could not be created");
    }
}
