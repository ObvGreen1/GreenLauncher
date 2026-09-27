package me.obvgreen.core.loader;

import com.google.gson.JsonObject;
import me.obvgreen.core.download.GameDownloader;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.util.Http;
import me.obvgreen.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;

/** Installs Fabric via the official meta API. */
public final class FabricLoader {
    private FabricLoader() {}

    /**
     * Installs (or replaces) Fabric for the instance.
     * @param loaderVersion empty means latest
     * @return the version id installed (fabric-loader-x.y.z-mcver)
     */
    public static String install(Instance inst, String loaderVersion, DoubleConsumer progress) throws Exception {
        GameDownloader.ensureVanilla(inst, progress);
        String lv = loaderVersion == null || loaderVersion.isBlank()
                ? latestLoader() : loaderVersion;
        String url = "https://meta.fabricmc.net/v2/versions/loader/" + inst.mcVersion + "/" + lv + "/profile/json";
        JsonObject profile = Json.parseObject(Http.getString(url));
        String versionId = profile.get("id").getAsString();
        Path dir = inst.versionsDir().resolve(versionId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(versionId + ".json"), profile.toString());
        // Fabric profile libraries carry their own "url" (maven.fabricmc.net); GameDownloader handles that.
        GameDownloader.ensureLibraries(inst, profile, null);
        return versionId;
    }

    public static String latestLoader() throws Exception {
        var list = com.google.gson.JsonParser.parseString(Http.getString("https://meta.fabricmc.net/v2/versions/loader")).getAsJsonArray();
        if (list.isEmpty()) throw new java.io.IOException("Fabric meta returned no loader versions");
        // New format: flat entries {version, stable, maven}; old format: nested {loader:{version}}.
        for (var el : list) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("loader")) return o.getAsJsonObject("loader").get("version").getAsString();
            if (o.has("stable") && o.get("stable").isJsonPrimitive() && o.get("stable").getAsBoolean()
                    && o.has("version")) {
                return o.get("version").getAsString();
            }
        }
        // no stable entry -> fall back to first entry with a version
        for (var el : list) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("loader")) return o.getAsJsonObject("loader").get("version").getAsString();
            if (o.has("version")) return o.get("version").getAsString();
        }
        throw new java.io.IOException("Fabric meta returned no usable loader version");
    }
}
