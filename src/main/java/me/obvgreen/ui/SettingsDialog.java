package me.obvgreen.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.obvgreen.core.settings.SettingsStore;
import me.obvgreen.util.Http;

/** Settings: theme, launch behavior, console behavior, proxy, memory, java args. */
public class SettingsDialog extends Stage {
    private final me.obvgreen.core.settings.Settings s = SettingsStore.get();

    private final ComboBox<String> theme = new ComboBox<>(javafx.collections.FXCollections.observableArrayList("dark", "light"));
    private final CheckBox startMinimized = new CheckBox("Start Minecraft minimized");
    private final CheckBox closeOnQuit = new CheckBox("When the game quits, close the launcher");
    private final CheckBox showOnLaunch = new CheckBox("Show console window when the game is launched");
    private final CheckBox showOnCrash = new CheckBox("Show console window when the game crashes");
    private final ComboBox<String> proxyType = new ComboBox<>(javafx.collections.FXCollections
            .observableArrayList("System default", "None", "SOCKS5", "HTTP"));
    private final TextField proxyAddr = new TextField(s.proxy.address);
    private final TextField proxyPort = new TextField(s.proxy.port > 0 ? String.valueOf(s.proxy.port) : "");
    private final TextField proxyUser = new TextField(s.proxy.username);
    private final PasswordField proxyPass = new PasswordField();
    private final Spinner<Integer> memory = new Spinner<>(512, 65536, s.memoryMB, 256);
    private final TextField javaArgs = new TextField(s.customJavaArgs);
    private final TextField defaultJava = new TextField(s.defaultJavaPath);

    public SettingsDialog() {
        setTitle("Settings");
        initModality(Modality.APPLICATION_MODAL);

        memory.setPrefWidth(140);
        memory.setEditable(true);
        theme.getSelectionModel().select(s.theme);
        theme.setOnAction(e -> { s.theme = theme.getValue(); save(); });
        startMinimized.setSelected(s.startMinimized);
        closeOnQuit.setSelected(s.closeOnQuit);
        showOnLaunch.setSelected(s.showConsoleOnLaunch);
        showOnCrash.setSelected(s.showConsoleOnCrash);
        proxyType.getSelectionModel().select(switch (s.proxy.type) {
            case SYSTEM -> 0; case NONE -> 1; case SOCKS5 -> 2; case HTTP -> 3;
        });

        GridPane proxyGrid = new GridPane();
        proxyGrid.setHgap(8);
        proxyGrid.setVgap(6);
        proxyGrid.addRow(0, new Label("Type:"), proxyType);
        proxyGrid.addRow(1, new Label("Address:"), proxyAddr, new Label("Port:"), proxyPort);
        proxyGrid.addRow(2, new Label("Username:"), proxyUser, new Label("Password:"), proxyPass);
        proxyPass.setText(s.proxy.password);

        GridPane general = new GridPane();
        general.setHgap(8);
        general.setVgap(8);
        general.addRow(0, new Label("Appearance theme:"), theme);
        VBox behavior = new VBox(6, startMinimized, closeOnQuit, showOnLaunch, showOnCrash);
        GridPane mem = new GridPane();
        mem.setHgap(8);
        mem.addRow(0, new Label("Memory (MB):"), memory, new Label("Custom Java arguments:"), javaArgs);
        mem.addRow(1, new Label("Default Java path override:"), defaultJava);

        Button saveBtn = new Button("Save");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> { apply(); close(); });
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> close());

        VBox root = new VBox(12,
                new Label("General"), general,
                new Label("Game launch behavior"), behavior,
                new Label("Proxy"), proxyGrid,
                new Label("Memory & Java"), mem,
                new HBox(10, saveBtn, cancel));
        root.setPadding(new Insets(16));
        defaultJava.setPrefColumnCount(30);
        javaArgs.setPrefColumnCount(30);

        setScene(new Scene(root));
        Theme.apply(getScene());
    }

    private void apply() {
        s.theme = theme.getValue();
        s.startMinimized = startMinimized.isSelected();
        s.closeOnQuit = closeOnQuit.isSelected();
        s.showConsoleOnLaunch = showOnLaunch.isSelected();
        s.showConsoleOnCrash = showOnCrash.isSelected();
        s.proxy.type = switch (proxyType.getSelectionModel().getSelectedIndex()) {
            case 0 -> me.obvgreen.core.settings.Settings.ProxyType.SYSTEM;
            case 1 -> me.obvgreen.core.settings.Settings.ProxyType.NONE;
            case 2 -> me.obvgreen.core.settings.Settings.ProxyType.SOCKS5;
            default -> me.obvgreen.core.settings.Settings.ProxyType.HTTP;
        };
        s.proxy.address = proxyAddr.getText().trim();
        try { s.proxy.port = Integer.parseInt(proxyPort.getText().trim()); } catch (Exception e) { s.proxy.port = 1080; }
        s.proxy.username = proxyUser.getText();
        s.proxy.password = proxyPass.getText();
        s.memoryMB = memory.getValue();
        s.customJavaArgs = javaArgs.getText().trim();
        s.defaultJavaPath = defaultJava.getText().trim();
        save();
    }

    private void save() {
        SettingsStore.save();
        Http.reconfigure();
    }
}
