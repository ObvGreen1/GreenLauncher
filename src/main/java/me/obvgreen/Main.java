package me.obvgreen;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.instance.InstanceStore;
import me.obvgreen.core.launch.Launcher;
import me.obvgreen.ui.ConsoleWindow;
import me.obvgreen.ui.MainView;
import me.obvgreen.ui.Theme;
import me.obvgreen.util.Paths;

import java.util.Arrays;

public class Main extends Application {
    private static String[] args;

    public static void main(String[] args) {
        Main.args = args;
        Application.launch(Main.class, args);
    }

    @Override
    public void start(Stage stage) {
        FilesExist();
        MainView root = new MainView();
        Scene scene = new Scene(root, 1000, 640);
        Theme.apply(scene);
        stage.setTitle("GreenLauncher");
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(520);
        stage.show();

        // --launch <id> argument: auto-launch that instance at startup (used by desktop shortcuts)
        var list = Arrays.asList(args);
        if (list.contains("--launch")) {
            String id = list.get(list.indexOf("--launch") + 1);
            Instance inst = InstanceStore.all().stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
            var acc = me.obvgreen.core.account.AccountStore.all().stream().findFirst().orElse(null);
            if (inst != null && acc != null) {
                me.obvgreen.core.launch.ProcessManager.launchAsync(inst, acc);
            } else {
                MainView.showAlert("Shortcut target instance or account not found.");
            }
        }
        if (me.obvgreen.core.settings.SettingsStore.get().startMinimized) {
            stage.setIconified(true);
        }
    }

    /** Ensures the launcher directories exist on first run. */
    private static void FilesExist() {
        try {
            java.nio.file.Files.createDirectories(Paths.LAUNCHER_DIR);
            java.nio.file.Files.createDirectories(Paths.INSTANCES_DIR);
            java.nio.file.Files.createDirectories(Paths.RUNTIMES_DIR);
        } catch (Exception ignored) {}
    }
}
