package me.obvgreen.core.java;

import com.google.gson.JsonObject;
import me.obvgreen.util.Http;
import me.obvgreen.util.Json;
import me.obvgreen.util.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves the Java runtime a Minecraft version needs. Uses the override path when set,
 * otherwise auto-downloads a JRE from Adoptium.
 */
public final class JavaRuntimeManager {
    private JavaRuntimeManager() {}

    /** The Java major version needed for the given root version json (falls back on mc version heuristics). */
    public static int requiredMajor(JsonObject rootVanillaJson, String mcVersion) {
        if (rootVanillaJson != null && rootVanillaJson.has("javaVersion")) {
            return rootVanillaJson.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        }
        String[] parts = mcVersion.split("\\.");
        try {
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2].split("-")[0]) : 0;
            if (minor > 20 || (minor == 20 && patch >= 5)) return 21;
            if (minor >= 18) return 17;
            if (minor == 17) return 16;
            return 8;
        } catch (Exception e) {
            return 21;
        }
    }

    /** Returns a path to java.exe for the required major version. */
    public static Path resolveJava(int major, String overridePath, DoubleConsumer progress) throws Exception {
        if (overridePath != null && !overridePath.isBlank()) {
            Path p = Path.of(overridePath, "bin", "java.exe");
            if (Files.exists(p)) return p;
            p = Path.of(overridePath);
            if (Files.exists(p)) return p;
            throw new IOException("Java override path invalid: " + overridePath);
        }
        return autoDownload(major, progress);
    }

    public static synchronized Path autoDownload(int major, DoubleConsumer progress) throws Exception {
        Path dir = Paths.RUNTIMES_DIR.resolve("java-" + major);
        Path exe = findJavaExe(dir);
        if (exe != null) return exe;
        String meta = Http.getString("https://api.adoptium.net/v3/assets/latest/" + major
                + "/hotspot?os=windows&architecture=x64&image_type=jre&release_type=ga");
        var arr = com.google.gson.JsonParser.parseString(meta).getAsJsonArray();
        if (arr.isEmpty()) throw new IOException("No Adoptium JRE found for Java " + major);
        JsonObject entry = arr.get(0).getAsJsonObject();
        if (!entry.has("binary") || !entry.getAsJsonObject("binary").has("package")
                || !entry.getAsJsonObject("binary").getAsJsonObject("package").has("link")) {
            throw new IOException("Unexpected Adoptium response for Java " + major);
        }
        String link = entry.getAsJsonObject("binary").getAsJsonObject("package").get("link").getAsString();
        Path zip = dir.resolve("jre.zip");
        Files.createDirectories(dir);
        Http.download(link, zip, progress);
        extractZip(zip, dir);
        Files.deleteIfExists(zip);
        exe = findJavaExe(dir);
        if (exe == null) throw new IOException("JRE extraction failed - no java.exe found");
        return exe;
    }

    private static Path findJavaExe(Path dir) throws IOException {
        if (!Files.exists(dir)) return null;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.getFileName() != null && p.getFileName().toString().equals("java.exe"))
                    .filter(p -> p.getParent() != null && p.getParent().getFileName().toString().equals("bin"))
                    .findFirst().orElse(null);
        }
    }

    private static void extractZip(Path zip, Path targetDir) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                Path out = targetDir.resolve(e.getName().replace("\\", "/")).normalize();
                if (!out.startsWith(targetDir)) continue; // zip-slip guard
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (var os = Files.newOutputStream(out)) {
                        int n;
                        while ((n = zin.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
            }
        }
    }
}
