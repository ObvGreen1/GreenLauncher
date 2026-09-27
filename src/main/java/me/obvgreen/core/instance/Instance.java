package me.obvgreen.core.instance;

import me.obvgreen.util.Json;
import me.obvgreen.util.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Instance {
    public enum Loader { NONE, FABRIC, FORGE }

    public String id = UUID.randomUUID().toString();
    public String name = "New Instance";
    public String group = "";                 // category
    public String mcVersion = "";             // e.g. 1.20.1
    public Loader loader = Loader.NONE;
    public String loaderVersion = "";         // fabric/forge version, empty = latest
    public String versionId = "";             // the version actually launched (may be loader-prefixed)
    public String javaPathOverride = "";
    public long playtimeSeconds = 0;
    public long lastPlayed = 0;

    public Path dir() { return Paths.INSTANCES_DIR.resolve(id); }
    public Path minecraftDir() { return dir().resolve("minecraft"); }
    public Path versionsDir() { return minecraftDir().resolve("versions"); }
    public Path librariesDir() { return minecraftDir().resolve("libraries"); }
    public Path assetsDir() { return minecraftDir().resolve("assets"); }
    public Path modsDir() { return minecraftDir().resolve("mods"); }
    public Path savesDir() { return minecraftDir().resolve("saves"); }
    public Path resourcePacksDir() { return minecraftDir().resolve("resourcepacks"); }
    public Path shaderPacksDir() { return minecraftDir().resolve("shaderpacks"); }
    public Path file() { return dir().resolve("instance.json"); }

    public void save() throws IOException {
        Json.toFile(file(), this);
    }

    public void delete() throws IOException {
        deleteRecursive(dir());
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.list(p)) {
                for (Path child : s.collect(Collectors.toList())) deleteRecursive(child);
            }
        }
        Files.deleteIfExists(p);
    }
}
