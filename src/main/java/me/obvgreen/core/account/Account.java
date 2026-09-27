package me.obvgreen.core.account;

import java.util.UUID;

/** An account. Microsoft login is deferred; only offline accounts for now. */
public class Account {
    public String type = "offline"; // offline | microsoft
    public String username = "";
    public String uuid = "";

    public Account() {}

    /** Creates an offline account with the deterministic Minecraft offline UUID. */
    public static Account offline(String username) {
        Account a = new Account();
        a.type = "offline";
        a.username = username;
        a.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
        return a;
    }
}