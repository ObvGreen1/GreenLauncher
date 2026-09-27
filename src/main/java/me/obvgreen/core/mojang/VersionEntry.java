package me.obvgreen.core.mojang;

/** One entry from the Mojang version manifest. */
public class VersionEntry {
    public String id;          // e.g. "1.20.1"
    public String type;        // release | snapshot | old_beta | old_alpha | experiment
    public String url;         // version json url
    public String releaseTime;
}
