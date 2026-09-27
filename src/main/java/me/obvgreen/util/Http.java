package me.obvgreen.util;

import me.obvgreen.core.settings.Settings;
import me.obvgreen.core.settings.SettingsStore;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/** Central HTTP layer. Rebuilds the HttpClient whenever proxy settings change. */
public final class Http {
    private static volatile HttpClient client;
    private static final String UA = "GreenLauncher/1.0 (me.obvgreen)";

    private Http() {}

    public static synchronized HttpClient client() {
        if (client == null) {
            client = build();
        }
        return client;
    }

    /** Called on startup and whenever proxy settings change. */
    public static synchronized void reconfigure() {
        client = build();
        var s = SettingsStore.get().proxy;
        // Also propagate to anything that uses the default ProxySelector.
        System.setProperty("java.net.useSystemProxies", s.type == Settings.ProxyType.SYSTEM ? "true" : "false");
    }

    private static HttpClient build() {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL);
        var s = SettingsStore.get().proxy;
        switch (s.type) {
            case NONE -> b.proxy(HttpClient.Builder.NO_PROXY);
            case SYSTEM -> b.proxy(ProxySelector.getDefault());
            case HTTP, SOCKS5 -> {
                Proxy proxy = new Proxy(s.type == Settings.ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
                        new InetSocketAddress(s.address, s.port));
                b.proxy(new FixedProxy(proxy));
                if (!s.username.isEmpty()) {
                    final String user = s.username, pass = s.password;
                    b.authenticator(new java.net.Authenticator() {
                        @Override protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(user, pass.toCharArray());
                        }
                    });
                }
            }
        }
        return b.build();
    }

    private static final class FixedProxy extends ProxySelector {
        private final Proxy proxy;
        FixedProxy(Proxy p) { this.proxy = p; }
        @Override public List<Proxy> select(URI uri) {
            List<Proxy> l = new ArrayList<>();
            l.add(proxy);
            return l;
        }
        @Override public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
    }

    public static String getString(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }

    public static String postString(String url, String body, String contentType) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new IOException("HTTP " + resp.statusCode() + " for " + url + ": " + resp.body());
        return resp.body();
    }

    /** Downloads url to target, reporting progress 0..1. Serialized per-target so concurrent downloads don't clobber each other. */
    public static void download(String url, Path target, DoubleConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Object lock = LOCKS.computeIfAbsent(target.toString(), k -> new Object());
        synchronized (lock) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).build();
                HttpResponse<InputStream> resp = client().send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() >= 400) throw new IOException("HTTP " + resp.statusCode() + " downloading " + url);
                long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
                try (InputStream in = resp.body()) {
                    Path tmp = target.resolveSibling(target.getFileName() + ".part");
                    try (var out = Files.newOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        long done = 0;
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                            done += n;
                            if (progress != null && total > 0) progress.accept((double) done / total);
                        }
                    }
                    try {
                        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.NoSuchFileException e) {
                        // another thread already completed this exact download
                        if (!Files.exists(target)) throw e;
                    }
                }
            } finally {
                LOCKS.remove(target.toString(), lock);
            }
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();
}
