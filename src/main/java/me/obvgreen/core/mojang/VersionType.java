package me.obvgreen.core.mojang;

/** Version types that can be filtered on in the download dialog. */
public enum VersionType {
    RELEASE("release"),
    SNAPSHOT("snapshot"),
    BETA("old_beta"),
    ALPHA("old_alpha"),
    EXPERIMENTAL("experiment");

    public final String mojangId;
    VersionType(String id) { this.mojangId = id; }
}
