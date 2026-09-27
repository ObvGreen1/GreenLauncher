package me.obvgreen.core.mojang;

import me.obvgreen.util.Http;
import me.obvgreen.util.Json;

import java.util.ArrayList;
import java.util.List;

public final class MojangApi {
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static volatile List<VersionEntry> cache;

    /** Fetches (and caches) the full version manifest. */
    public static List<VersionEntry> manifest() throws Exception {
        List<VersionEntry> c = cache;
        if (c != null) return c;
        var root = Json.parseObject(Http.getString(MANIFEST));
        List<VersionEntry> out = new ArrayList<>();
        for (var v : root.getAsJsonArray("versions")) {
            VersionEntry e = new VersionEntry();
            e.id = v.getAsJsonObject().get("id").getAsString();
            e.type = v.getAsJsonObject().get("type").getAsString();
            e.url = v.getAsJsonObject().get("url").getAsString();
            e.releaseTime = v.getAsJsonObject().get("releaseTime").getAsString();
            out.add(e);
        }
        cache = out;
        return out;
    }

    public static VersionEntry find(String id) throws Exception {
        for (VersionEntry e : manifest()) if (e.id.equals(id)) return e;
        throw new IllegalArgumentException("Unknown Minecraft version: " + id);
    }

    /** Filter manifest by allowed types; result is newest-first as Mojang provides it. */
    public static List<VersionEntry> filtered(List<VersionType> types) throws Exception {
        List<VersionEntry> all = manifest();
        List<VersionEntry> out = new ArrayList<>();
        for (VersionEntry e : all) {
            for (VersionType t : types) {
                if (e.type.equals(t.mojangId)) { out.add(e); break; }
            }
        }
        return out;
    }

    public static void clearCache() { cache = null; }
}

