package me.obvgreen.ui;

import javafx.scene.Scene;

/** Light/dark appearance themes applied to every scene. */
public final class Theme {
    private Theme() {}

    public static void apply(Scene scene) {
        String theme = me.obvgreen.core.settings.SettingsStore.get().theme;
        String css = "/theme-" + ("light".equals(theme) ? "light" : "dark") + ".css";
        var url = Theme.class.getResource(css);
        scene.getStylesheets().clear();
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }
}
