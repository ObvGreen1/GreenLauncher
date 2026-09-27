package me.obvgreen.core.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Helpers for navigating Mojang version JSON. */
public final class VersionJson {
    private VersionJson() {}

    public static String id(JsonObject v) { return v.get("id").getAsString(); }

    public static boolean appliesToWindows(JsonObject library) {
        if (!library.has("rules")) return true;
        boolean result = false;
        for (var r : library.getAsJsonArray("rules")) {
            JsonObject rule = r.getAsJsonObject();
            boolean applies = true;
            if (rule.has("features")) applies = false; // feature-gated rules only apply when the feature is requested
            else if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) applies = os.get("name").getAsString().equals("windows");
            }
            if (applies) result = rule.get("action").getAsString().equals("allow");
        }
        return result;
    }

    /** Classifier embedded in a maven coordinate ("org.lwjgl:lwjgl:3.3.3:natives-windows" -> "natives-windows"), or null. */
    public static String coordClassifier(String coord) {
        String[] parts = coord.split(":");
        return parts.length > 3 ? parts[3] : null;
    }

    /** maven coordinate like "com.mojang:brigadier:1.0.18" -> relative path. */
    public static String mavenPath(String coord, String classifier, String ext) {
        String[] parts = coord.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        StringBuilder sb = new StringBuilder(group).append('/').append(artifact).append('/').append(version)
                .append('/').append(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty()) sb.append('-').append(classifier);
        return sb.append('.').append(ext).toString();
    }

    /** Resolves the download url for a library artifact, or null if none. */
    public static String libraryUrl(JsonObject lib) {
        JsonObject dl = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
        if (dl != null) {
            JsonObject artifact = dl.has("artifact") ? dl.getAsJsonObject("artifact") : null;
            if (artifact != null && artifact.has("url") && !artifact.get("url").getAsString().isEmpty()) {
                return artifact.get("url").getAsString();
            }
        }
        String name = lib.get("name").getAsString();
        String url = lib.has("url") && !lib.get("url").isJsonNull() ? lib.get("url").getAsString() : null;
        if (url != null) {
            return url.endsWith("/") ? url + mavenPath(name, null, "jar") : url + "/" + mavenPath(name, null, "jar");
        }
        return "https://libraries.minecraft.net/" + mavenPath(name, null, "jar");
    }

    /** Natives classifier for windows, or null. */
    public static String windowsNativesClassifier(JsonObject lib) {
        if (!appliesToWindows(lib)) return null;
        if (!lib.has("natives")) return null;
        JsonObject n = lib.getAsJsonObject("natives");
        if (!n.has("windows")) return null;
        return n.get("windows").getAsString().replace("${arch}", "64");
    }

    public static String nativesUrl(JsonObject lib, String classifier) {
        JsonObject dl = lib.getAsJsonObject("downloads");
        if (dl.has("classifiers")) {
            JsonObject cls = dl.getAsJsonObject("classifiers");
            if (cls.has(classifier)) return cls.getAsJsonObject(classifier).get("url").getAsString();
        }
        return "https://libraries.minecraft.net/" + mavenPath(lib.get("name").getAsString(), classifier, "jar");
    }

    /** Collects the game arguments: supports both old "minecraftArguments" string and new structured "arguments". */
    public static java.util.List<String> gameArgs(JsonObject version) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (version.has("arguments") && version.getAsJsonObject("arguments").has("game")) {
            for (var el : version.getAsJsonObject("arguments").getAsJsonArray("game")) {
                if (el.isJsonPrimitive()) out.add(el.getAsString());
            }
        }
        if (version.has("minecraftArguments") && !version.get("minecraftArguments").getAsString().isBlank()) {
            for (String a : version.get("minecraftArguments").getAsString().split(" ")) out.add(a);
        }
        return out;
    }

    /** Structured (new-style) game args with rules applied; falls back to the plain list. */
    public static java.util.List<String> gameArgsConditional(JsonObject version) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (version.has("arguments") && version.getAsJsonObject("arguments").has("game")) {
            JsonArray arr = version.getAsJsonObject("arguments").getAsJsonArray("game");
            for (var el : arr) {
                if (el.isJsonPrimitive()) { out.add(el.getAsString()); continue; }
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("rules") && !appliesToWindows(obj)) continue;
                if (obj.has("value")) {
                    var val = obj.get("value");
                    if (val.isJsonArray()) for (var s : val.getAsJsonArray()) out.add(s.getAsString());
                    else out.add(val.getAsString());
                }
            }
        }
        if (version.has("minecraftArguments") && !version.get("minecraftArguments").getAsString().isBlank()) {
            for (String a : version.get("minecraftArguments").getAsString().split(" ")) out.add(a);
        }
        return out;
    }
}
