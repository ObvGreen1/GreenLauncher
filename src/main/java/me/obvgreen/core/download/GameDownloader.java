package me.obvgreen.core.download;

import com.google.gson.JsonObject;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.mojang.MojangApi;
import me.obvgreen.util.Http;
import me.obvgreen.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads everything a vanilla (or loader-inheriting-vanilla) version needs:
 * version jsons, client jar, libraries, natives, assets.
 */
public final class GameDownloader {

    /** Loads the version json for versionId inside the instance, resolving inheritsFrom from Mojang if needed. */
    public static JsonObject loadVersionJson(Instance inst, String versionId) throws Exception {
        Path f = inst.versionsDir().resolve(versionId).resolve(versionId + ".json");
        if (Files.exists(f)) return Json.parseObject(Files.readString(f));
        return Json.parseObject(Http.getString(MojangApi.find(versionId).url));
    }

    /** Ensures the vanilla base of the instance's mcVersion exists (json + client jar). */
    public static void ensureVanilla(Instance inst, DoubleConsumer progress) throws Exception {
        Path dir = inst.versionsDir().resolve(inst.mcVersion);
        Path jsonFile = dir.resolve(inst.mcVersion + ".json");
        Path jarFile = dir.resolve(inst.mcVersion + ".jar");
        JsonObject json;
        if (Files.exists(jsonFile)) {
            json = Json.parseObject(Files.readString(jsonFile));
        } else {
            String body = Http.getString(MojangApi.find(inst.mcVersion).url);
            Files.createDirectories(dir);
            Files.writeString(jsonFile, body);
            json = Json.parseObject(body);
        }
        JsonObject downloads = json.has("downloads") ? json.getAsJsonObject("downloads") : null;
        JsonObject client = downloads != null && downloads.has("client") && downloads.get("client").isJsonObject()
                ? downloads.getAsJsonObject("client") : null;
        if (client != null && client.has("url") && !Files.exists(jarFile)) {
            Http.download(client.get("url").getAsString(), jarFile, progress);
        }
    }

    /** Walks the inheritsFrom chain root-first. */
    public static List<JsonObject> chain(Instance inst, JsonObject version) throws Exception {
        List<JsonObject> out = new ArrayList<>();
        JsonObject cur = version;
        while (true) {
            out.add(0, cur);
            if (cur.has("inheritsFrom")) cur = loadVersionJson(inst, cur.get("inheritsFrom").getAsString());
            else break;
        }
        return out;
    }

    /** Downloads all libraries (+ windows natives extraction) required by the given version chain. */
    public static void ensureLibraries(Instance inst, JsonObject version, DoubleConsumer progress) throws Exception {
        List<Path> nativeJars = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonObject v : chain(inst, version)) {
            for (var libEl : v.getAsJsonArray("libraries")) {
                JsonObject lib = libEl.getAsJsonObject();
                if (!VersionJson.appliesToWindows(lib)) continue;
                String url = VersionJson.libraryUrl(lib);
                String name = lib.get("name").getAsString();
                String cls = VersionJson.coordClassifier(name); // new-style: classifier lives in the coordinate
                Path target = inst.librariesDir().resolve(VersionJson.mavenPath(name, cls, "jar"));
                if (!seen.add(target.toString())) continue; // duplicate entry, already handled
                if (url != null && !Files.exists(target)) Http.download(url, target, null);
                String natCls = VersionJson.windowsNativesClassifier(lib);
                if (natCls != null) {
                    // old-style: separate "natives" map on the library
                    Path natTarget = inst.librariesDir().resolve(VersionJson.mavenPath(name, natCls, "jar"));
                    if (!Files.exists(natTarget)) Http.download(VersionJson.nativesUrl(lib, natCls), natTarget, null);
                    nativeJars.add(natTarget);
                } else if (cls != null && cls.startsWith("natives-windows")) {
                    // new-style (1.19+): the natives jar IS this library
                    nativeJars.add(target);
                }
            }
        }
        Path nativesDir = inst.minecraftDir().resolve("natives");
        Files.createDirectories(nativesDir);
        for (Path nj : nativeJars) {
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(nj))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String fn = e.getName().replace("\\", "/");
                    if (!(fn.endsWith(".dll") || fn.endsWith(".so") || fn.endsWith(".jnilib"))) continue;
                    fn = fn.substring(fn.lastIndexOf('/') + 1);
                    try (var os = Files.newOutputStream(nativesDir.resolve(fn))) { zin.transferTo(os); }
                }
            }
        }
    }

    /** Ensures the asset index + objects exist for the version chain. */
    public static void ensureAssets(Instance inst, JsonObject version, DoubleConsumer progress) throws Exception {
        JsonObject assetIndex = null;
        for (JsonObject v : chain(inst, version)) {
            if (v.has("assetIndex")) { assetIndex = v.getAsJsonObject("assetIndex"); break; }
        }
        if (assetIndex == null) throw new IOException("No assetIndex found");
        String indexId = assetIndex.get("id").getAsString();
        Path indexFile = inst.assetsDir().resolve("indexes").resolve(indexId + ".json");
        JsonObject index;
        if (Files.exists(indexFile)) {
            index = Json.parseObject(Files.readString(indexFile));
        } else {
            String body = Http.getString(assetIndex.get("url").getAsString());
            Files.createDirectories(indexFile.getParent());
            Files.writeString(indexFile, body);
            index = Json.parseObject(body);
        }
        Path objectsDir = inst.assetsDir().resolve("objects");
        var objects = index.getAsJsonObject("objects");
        int i = 0, total = objects.size();
        for (var key : objects.keySet()) {
            JsonObject obj = objects.getAsJsonObject(key);
            String hash = obj.get("hash").getAsString();
            Path target = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
            if (!Files.exists(target)) {
                Http.download("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, target, null);
            }
            i++;
            if (progress != null && i % 50 == 0) progress.accept((double) i / total);
        }
    }

    /** Assembles the full classpath string (libraries + client jars) for the version chain. */
    public static String classpath(Instance inst, JsonObject version) throws Exception {
        List<String> cp = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (JsonObject v : chain(inst, version)) {
            for (var libEl : v.getAsJsonArray("libraries")) {
                JsonObject lib = libEl.getAsJsonObject();
                if (!VersionJson.appliesToWindows(lib)) continue;
                String name = lib.get("name").getAsString();
                String cls = VersionJson.coordClassifier(name);
                Path target = inst.librariesDir().resolve(VersionJson.mavenPath(name, cls, "jar"));
                if (seen.add(target.toString())) cp.add(target.toString());
            }
            String vid = v.get("id").getAsString();
            Path jar = inst.versionsDir().resolve(vid).resolve(vid + ".jar");
            if (Files.exists(jar)) cp.add(jar.toString());
        }
        return String.join(";", cp);
    }

    /** Resolves the root (vanilla) json of a chain. */
    public static JsonObject rootJson(Instance inst, JsonObject version) throws Exception {
        return chain(inst, version).get(0);
    }
}
