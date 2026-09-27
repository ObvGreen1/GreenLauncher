package me.obvgreen.core.loader;

import me.obvgreen.core.instance.Instance;
import me.obvgreen.util.Http;
import me.obvgreen.util.Paths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;

/**
 * Installs Forge by running the official installer headlessly with --installClient
 * pointed at the instance's minecraft dir. Requires the vanilla base to be present.
 */
public final class ForgeLoader {
    private ForgeLoader() {}

    public static String latestLoader(String mcVersion) throws Exception {
        String xml = Http.getString("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
        var m = java.util.regex.Pattern.compile("<version>" + java.util.regex.Pattern.quote(mcVersion) + "-([^<]+)</version>").matcher(xml);
        String last = null;
        while (m.find()) last = m.group(1); // last match = newest (maven metadata ordering)
        if (last == null) throw new IllegalArgumentException("No Forge version found for " + mcVersion);
        return last;
    }

    /**
     * @param loaderVersion empty means latest
     * @return the installed version id, e.g. 1.20.1-forge-47.2.0
     */
    public static String install(Instance inst, String loaderVersion, String javaExe, DoubleConsumer progress) throws Exception {
        // vanilla base first (installer needs it)
        me.obvgreen.core.download.GameDownloader.ensureVanilla(inst, progress);
        String lv = loaderVersion == null || loaderVersion.isBlank() ? latestLoader(inst.mcVersion) : loaderVersion;
        String coord = inst.mcVersion + "-" + lv;
        String url = "https://maven.minecraftforge.net/net/minecraftforge/forge/forge-" + coord + "-installer.jar";
        Path installer = Paths.LAUNCHER_DIR.resolve("cache").resolve("forge-" + coord + "-installer.jar");
        if (!Files.exists(installer)) Http.download(url, installer, progress);

        String mcDir = inst.minecraftDir().toString();
        Process p = new ProcessBuilder(javaExe, "-jar", installer.toString(), "--installClient", mcDir)
                .redirectErrorStream(true)
                .start();
        StringBuilder log = new StringBuilder();
        try (var in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) log.append(new String(buf, 0, n));
        }
        int code = p.waitFor();
        if (code != 0) throw new IllegalStateException("Forge installer failed (exit " + code + "):\n" + log);
        String versionId = inst.mcVersion + "-forge-" + lv;
        if (!Files.exists(inst.versionsDir().resolve(versionId).resolve(versionId + ".json"))) {
            // some forge versions use "-forge" naming differences; locate any forge json
            try (var s = Files.list(inst.versionsDir())) {
                versionId = s.filter(Files::isDirectory)
                        .map(d -> d.getFileName().toString())
                        .filter(n -> n.contains("forge"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Forge install produced no version json:\n" + log));
            }
        }
        // forge installers run processors that may still need libraries; pre-resolve the chain
        var json = me.obvgreen.util.Json.parseObject(
                Files.readString(inst.versionsDir().resolve(versionId).resolve(versionId + ".json")));
        me.obvgreen.core.download.GameDownloader.ensureLibraries(inst, json, null);
        return versionId;
    }
}
