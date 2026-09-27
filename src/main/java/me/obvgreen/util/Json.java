package me.obvgreen.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small JSON helpers on top of Gson. */
public final class Json {
    public static final Gson GSON = new Gson();
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private Json() {}

    public static JsonObject parseObject(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static <T> T fromFile(Path file, Class<T> clazz) throws IOException {
        if (!Files.exists(file)) return null;
        return GSON.fromJson(Files.readString(file), clazz);
    }

    public static void toFile(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, PRETTY.toJson(value));
    }
}
