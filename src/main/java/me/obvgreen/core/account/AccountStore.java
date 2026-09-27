package me.obvgreen.core.account;

import me.obvgreen.util.Json;
import me.obvgreen.util.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads/saves accounts.json and manages the account list. */
public final class AccountStore {
    private static final Path FILE = Paths.LAUNCHER_DIR.resolve("accounts.json");
    private static List<Account> accounts;

    public static synchronized List<Account> all() {
        if (accounts == null) {
            accounts = new ArrayList<>();
            try {
                if (Files.exists(FILE)) {
                    var arr = com.google.gson.JsonParser.parseString(Files.readString(FILE)).getAsJsonArray();
                    for (var e : arr) accounts.add(Json.GSON.fromJson(e, Account.class));
                }
            } catch (Exception ignored) {}
        }
        return accounts;
    }

    public static synchronized void save() {
        try {
            Json.toFile(FILE, accounts);
        } catch (IOException ignored) {}
    }

    /** Creates an offline account the same way Minecraft does: deterministic UUID from the name. */
    public static synchronized Account addOffline(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Username cannot be empty");
        if (name.length() > 16) throw new IllegalArgumentException("Username must be 16 characters or fewer");
        for (Account a : all()) if (a.username.equalsIgnoreCase(name)) return a;
        Account a = Account.offline(name);
        accounts.add(a);
        save();
        return a;
    }

    public static synchronized void remove(Account a) {
        all().remove(a);
        save();
    }
}

