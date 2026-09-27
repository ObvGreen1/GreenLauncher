package me.obvgreen.core.settings;

public class Settings {
    public enum ProxyType { SYSTEM, NONE, SOCKS5, HTTP }
    public static class Proxy {
        public ProxyType type = ProxyType.SYSTEM;
        public String address = "";
        public int port = 1080;
        public String username = "";
        public String password = "";
    }

    public String theme = "dark";
    public boolean startMinimized = false;
    public boolean closeOnQuit = false;
    public boolean showConsoleOnLaunch = false;
    public boolean showConsoleOnCrash = true;
    public Proxy proxy = new Proxy();
    public int memoryMB = 4096;
    public String customJavaArgs = "";
    public String defaultJavaPath = ""; // override for instance Java; empty = auto-download
}
