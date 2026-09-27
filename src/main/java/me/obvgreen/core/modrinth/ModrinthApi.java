package me.obvgreen.core.modrinth;

import me.obvgreen.util.Http;
import me.obvgreen.util.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Minimal Modrinth v2 API client. */
public final class ModrinthApi {
    private static final String API = "https://api.modrinth.com/v2";

    public enum Type { MOD("mod", "mods"), RESOURCE_PACK("resourcepack", "resourcepacks"), SHADER("shader", "shaderpacks");
        public final String projectType, dirName;
        Type(String t, String d) { projectType = t; dirName = d; }
    }

    public static class SearchResult {
        public String projectId, slug, title, description, iconUrl, author;
        public long downloads;
    }

    private ModrinthApi() {}

    public static List<SearchResult> search(String query, Type type, String mcVersion, String loader) throws Exception {
        String facets = "[[\"project_type:" + type.projectType + "\"]";
        if (mcVersion != null && !mcVersion.isBlank()) facets += ",[\"versions:" + mcVersion + "\"]";
        if (loader != null && !loader.isBlank() && type == Type.MOD) facets += ",[\"categories:" + loader + "\"]";
        facets += "]";
        String url = API + "/search?limit=30&index=relevance&query=" + java.net.URLEncoder.encode(query == null ? "" : query,
                java.nio.charset.StandardCharsets.UTF_8) + "&facets=" + java.net.URLEncoder.encode(facets,
                java.nio.charset.StandardCharsets.UTF_8);
        JsonObject root = Json.parseObject(Http.getString(url));
        List<SearchResult> out = new ArrayList<>();
        for (var el : root.getAsJsonArray("hits")) {
            JsonObject h = el.getAsJsonObject();
            SearchResult r = new SearchResult();
            r.projectId = h.get("project_id").getAsString();
            r.slug = h.has("slug") ? h.get("slug").getAsString() : r.projectId;
            r.title = h.has("title") ? h.get("title").getAsString() : r.slug;
            r.description = h.has("description") ? h.get("description").getAsString() : "";
            r.iconUrl = h.has("icon_url") && !h.get("icon_url").isJsonNull() ? h.get("icon_url").getAsString() : "";
            r.author = h.has("author") ? h.get("author").getAsString() : "";
            r.downloads = h.has("downloads") ? h.get("downloads").getAsLong() : 0;
            out.add(r);
        }
        return out;
    }

    public static class VersionFile {
        public String versionId, versionNumber, fileName, url, changelog;
    }

    /** Latest version compatible with mcVersion (+loader for mods). */
    public static VersionFile latestVersion(String projectId, String mcVersion, String loader) throws Exception {
        String qs = "";
        if (mcVersion != null && !mcVersion.isBlank()) {
            qs += "?game_versions=%5B%22" + mcVersion + "%22%5D";
            if (loader != null && !loader.isBlank()) qs += "&loaders=%5B%22" + loader + "%22%5D";
        }
        JsonArray arr = com.google.gson.JsonParser.parseString(Http.getString(API + "/project/" + projectId + "/version" + qs)).getAsJsonArray();
        if (arr.isEmpty()) return null;
        JsonObject v = arr.get(0).getAsJsonObject();
        VersionFile f = new VersionFile();
        f.versionId = v.get("id").getAsString();
        f.versionNumber = v.get("version_number").getAsString();
        if (v.has("changelog") && !v.get("changelog").isJsonNull()) f.changelog = v.get("changelog").getAsString();
        for (var fe : v.getAsJsonArray("files")) {
            JsonObject fo = fe.getAsJsonObject();
            boolean primary = fo.has("primary") && fo.get("primary").getAsBoolean();
            if (primary || f.fileName == null) {
                f.fileName = fo.get("filename").getAsString();
                f.url = fo.get("url").getAsString();
                if (primary) break;
            }
        }
        return f;
    }

    /** Resolves a jar by its sha1 back to its project + latest file (for the Update button). */
    public static String projectIdFromSha1(String sha1) throws Exception {
        JsonObject v = Json.parseObject(Http.getString(API + "/version_file/" + sha1 + "?algorithm=sha1"));
        return v.get("project_id").getAsString();
    }
}
