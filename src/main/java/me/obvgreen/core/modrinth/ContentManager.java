package me.obvgreen.core.modrinth;

import me.obvgreen.core.instance.Instance;
import me.obvgreen.util.Http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Installs/updates/removes Modrinth content inside an instance. */
public final class ContentManager {
    private ContentManager() {}

    public static Path dirFor(Instance inst, ModrinthApi.Type type) {
        return inst.minecraftDir().resolve(type.dirName);
    }

    public static List<Path> installed(Instance inst, ModrinthApi.Type type) throws IOException {
        Path dir = dirFor(inst, type);
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".jar") || n.endsWith(".zip");
            }).forEach(out::add);
        }
        out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return out;
    }

    public static void install(Instance inst, ModrinthApi.Type type, ModrinthApi.VersionFile file) throws Exception {
        Path dir = dirFor(inst, type);
        Files.createDirectories(dir);
        Http.download(file.url, dir.resolve(file.fileName), null);
    }

    public static void remove(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    public static String sha1(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Returns true if the file was updated. */
    public static boolean update(Instance inst, ModrinthApi.Type type, Path file) throws Exception {
        String projectId = ModrinthApi.projectIdFromSha1(sha1(file));
        ModrinthApi.VersionFile latest = ModrinthApi.latestVersion(projectId, inst.mcVersion,
                inst.loader == Instance.Loader.FABRIC ? "fabric" : inst.loader == Instance.Loader.FORGE ? "forge" : null);
        if (latest == null) return false;
        if (latest.fileName.equals(file.getFileName().toString())) return false;
        String loaderDir = latest.url;
        Files.deleteIfExists(file);
        Http.download(loaderDir, dirFor(inst, type).resolve(latest.fileName), null);
        return true;
    }
}
