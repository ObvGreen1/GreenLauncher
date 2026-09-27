package me.obvgreen.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import me.obvgreen.core.account.AccountStore;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.instance.InstanceStore;
import me.obvgreen.core.launch.Launcher;
import me.obvgreen.core.launch.ProcessManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/** Main launcher window: search bar, grouped instance list with rich cells, sidebar, playtime bar. */
public class MainView extends BorderPane {
    final ListView<Object> list = new ListView<>(); // Instance or String (group header)
    final VBox sidebar = new VBox(6);
    final Label totalPlaytime = new Label();
    final Label selectedPlaytime = new Label();
    final Label accountLabel = new Label();
    final Label statusLabel = new Label("");
    final TextField searchField = new TextField();
    Instance selected;
    private final List<Object> allItems = new ArrayList<>();
    private Button launchBtn, killBtn;

    public MainView() {
        setTop(buildTopBar());
        setCenter(list);
        setRight(sidebar);
        setBottom(buildBottomBar());

        list.getStyleClass().add("instance-list");
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("group-header");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof String g) {
                    setText(null);
                    setGraphic(null);
                    setText("▼  " + g.toUpperCase());
                    getStyleClass().add("group-header");
                } else {
                    setText(null);
                    setGraphic(buildCell((Instance) item));
                }
            }
        });

        list.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv instanceof Instance i) { selected = i; }
            updateSidebar();
            updatePlaytimeBar();
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY
                    && list.getSelectionModel().getSelectedItem() instanceof Instance i) {
                launch(i);
            }
        });
        list.setContextMenu(buildContextMenu());
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && selected != null) launch(selected);
            else if (e.getCode() == KeyCode.DELETE && selected != null) deleteInstance();
        });
        searchField.textProperty().addListener((o, ov, nv) -> applyFilter());

        refresh();
        updateSidebar();

        ProcessManager.onStateChange = () -> { updateSidebar(); list.refresh(); };

        Timer t = new Timer("playtime-refresh", true);
        t.schedule(new TimerTask() {
            @Override public void run() { Platform.runLater(() -> { updatePlaytimeBar(); list.refresh(); }); }
        }, 5000, 5000);
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("top-bar");
        Label title = new Label("GreenLauncher");
        title.getStyleClass().add("app-title");
        searchField.setPromptText("Search instances...");
        HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(title, searchField);
        return bar;
    }

    void updatePlaytimeBar() {
        long total = InstanceStore.all().stream().mapToLong(i -> i.playtimeSeconds).sum();
        totalPlaytime.setText("Total playtime: " + formatTime(total));
        if (selected != null) selectedPlaytime.setText(selected.name + ": " + formatTime(selected.playtimeSeconds));
        else selectedPlaytime.setText("No instance selected");
    }

    /** Builds the two-line rich cell content for an instance. */
    private VBox buildCell(Instance i) {
        Label title = new Label(i.name);
        title.getStyleClass().add("cell-title");
        Label sub = new Label(i.mcVersion + "  •  " + prettyLoader(i.loader) + "  •  " + formatTime(i.playtimeSeconds));
        sub.getStyleClass().add("cell-subtitle");
        HBox line2 = new HBox(8, sub);
        line2.setAlignment(Pos.CENTER_LEFT);
        if (ProcessManager.isRunning(i)) {
            Label badge = new Label(ProcessManager.isStarting(i) ? "STARTING" : "RUNNING");
            badge.getStyleClass().add("running-badge");
            line2.getChildren().add(badge);
        }
        VBox box = new VBox(1, title, line2);
        box.getStyleClass().add("cell-box");
        return box;
    }

    private static String prettyLoader(Object loader) {
        String s = String.valueOf(loader);
        if (s == null || s.isBlank() || s.equalsIgnoreCase("vanilla")) return "Vanilla";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem launch = new MenuItem("Launch");
        launch.setOnAction(e -> { if (selected != null) launch(selected); });
        MenuItem kill = new MenuItem("Kill");
        kill.setOnAction(e -> { if (selected != null) ProcessManager.kill(selected); });
        MenuItem edit = new MenuItem("Edit...");
        edit.setOnAction(e -> { if (selected != null) new EditInstanceDialog(selected).show(); });
        MenuItem folder = new MenuItem("Open folder");
        folder.setOnAction(e -> openFolder());
        MenuItem group = new MenuItem("Change group...");
        group.setOnAction(e -> changeGroup());
        MenuItem shortcut = new MenuItem("Create desktop shortcut");
        shortcut.setOnAction(e -> createShortcut());
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> deleteInstance());
        cm.getItems().addAll(launch, new SeparatorMenuItem(), kill, edit, folder, group, shortcut,
                new SeparatorMenuItem(), delete);
        cm.setOnShowing(e -> {
            boolean has = selected != null;
            boolean running = has && ProcessManager.isRunning(selected);
            launch.setVisible(has && !running);
            kill.setVisible(running);
            edit.setVisible(has);
            folder.setVisible(has);
            group.setVisible(has);
            shortcut.setVisible(has);
            delete.setVisible(has);
        });
        return cm;
    }

    private HBox buildBottomBar() {
        HBox bar = new HBox(24, totalPlaytime, selectedPlaytime, statusLabel);
        bar.getStyleClass().add("bottom-bar");
        totalPlaytime.getStyleClass().add("title");
        statusLabel.getStyleClass().add("status");
        updatePlaytimeBar();
        return bar;
    }

    public static String formatTime(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return String.format("%dh %02dm %02ds", h, m, s);
    }

    /** Rebuilds the list: groups in sorted order, ungrouped last, honoring the search filter. */
    public void refresh() {
        allItems.clear();
        for (String g : InstanceStore.groups()) {
            allItems.add(g);
            InstanceStore.all().stream().filter(i -> g.equals(i.group)).forEach(allItems::add);
        }
        InstanceStore.all().stream()
                .filter(i -> i.group == null || i.group.isBlank()).forEach(allItems::add);
        applyFilter();
        updatePlaytimeBar();
    }

    /** Applies the search box filter to the visible list. */
    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        List<Object> visible = new ArrayList<>();
        String currentGroup = null;
        boolean groupHasMatch = false;
        for (Object o : allItems) {
            if (o instanceof String g) {
                if (groupHasMatch) visible.add(currentGroup);
                currentGroup = g;
                groupHasMatch = false;
            } else {
                Instance i = (Instance) o;
                if (q.isEmpty() || i.name.toLowerCase().contains(q)
                        || i.mcVersion.toLowerCase().contains(q)
                        || String.valueOf(i.loader).toLowerCase().contains(q)) {
                    groupHasMatch = true;
                    visible.add(i);
                }
            }
        }
        if (groupHasMatch) visible.add(currentGroup);
        list.setItems(FXCollections.observableArrayList(visible));
    }

    static void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.show();
    }

    public static void showAlert(String msg) { alert(msg); }

    static FileChooser fileChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        return fc;
    }

    private Button sbBtn(String label, String styleClass, Runnable action) {
        Button b = new Button(label);
        b.getStyleClass().add("button");
        if (styleClass != null) b.getStyleClass().add(styleClass);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return b;
    }

    void updateSidebar() {
        sidebar.getChildren().clear();
        if (!sidebar.getStyleClass().contains("sidebar")) sidebar.getStyleClass().add("sidebar");

        boolean has = selected != null;
        boolean running = has && ProcessManager.isRunning(selected);
        boolean starting = has && ProcessManager.isStarting(selected);

        // Account box
        VBox accountBox = new VBox(2);
        accountBox.getStyleClass().add("account-box");
        accountLabel.setText("Account");
        if (!accountLabel.getStyleClass().contains("title")) accountLabel.getStyleClass().add("title");
        var accounts = AccountStore.all();
        Label accName = new Label(accounts.isEmpty() ? "No account — click to add"
                : accounts.get(0).username);
        accName.setWrapText(true);
        if (!accounts.isEmpty()) accountBox.getChildren().addAll(accountLabel, accName);
        else accountBox.getChildren().add(accName);
        accName.setOnMouseClicked(e -> openAccounts());
        accName.setCursor(javafx.scene.Cursor.HAND);
        sidebar.getChildren().add(accountBox);

        Label instHeader = new Label("INSTANCE");
        instHeader.getStyleClass().add("section-header");
        sidebar.getChildren().add(instHeader);

        launchBtn = sbBtn(running ? (starting ? "Starting..." : "Running") : "▶  Launch", "launch",
                () -> { if (selected != null) launch(selected); });
        killBtn = sbBtn("■  Kill", "kill", () -> { if (selected != null) ProcessManager.kill(selected); });
        launchBtn.setDisable(!has || running);
        killBtn.setDisable(!running);
        Button editBtn = sbBtn("Edit", null, () -> { if (selected != null) new EditInstanceDialog(selected).show(); });
        Button groupBtn = sbBtn("Change group", null, this::changeGroup);
        Button folderBtn = sbBtn("Open folder", null, this::openFolder);
        Button deleteBtn = sbBtn("Delete", null, this::deleteInstance);
        Button shortcutBtn = sbBtn("Create shortcut", null, this::createShortcut);
        editBtn.setDisable(!has);
        groupBtn.setDisable(!has);
        folderBtn.setDisable(!has);
        deleteBtn.setDisable(!has);
        shortcutBtn.setDisable(!has);
        sidebar.getChildren().addAll(launchBtn, killBtn, editBtn, groupBtn, folderBtn, deleteBtn, shortcutBtn);

        Label genHeader = new Label("GENERAL");
        genHeader.getStyleClass().add("section-header");
        sidebar.getChildren().addAll(genHeader,
                sbBtn("New instance", null, () -> new CreateInstanceDialog(this).show()),
                sbBtn("Accounts", null, this::openAccounts),
                sbBtn("Settings", null, () -> new SettingsDialog().showAndWait()));

        // status label
        if (starting) statusLabel.setText(selected.name + ": starting…");
        else if (running) statusLabel.setText(selected.name + ": running");
        else if (has) statusLabel.setText("");
        else statusLabel.setText("");
    }

    private void openFolder() {
        if (selected == null) return;
        try {
            java.awt.Desktop.getDesktop().open(selected.minecraftDir().toFile());
        } catch (Exception ex) {
            alert("Could not open folder: " + ex.getMessage());
        }
    }

    private void createShortcut() {
        if (selected == null) return;
        try {
            Launcher.createShortcut(selected);
            alert("Shortcut created on the desktop.");
        } catch (Exception ex) { alert("Shortcut failed: " + ex.getMessage()); }
    }

    private void openAccounts() {
        new AccountsDialog().showAndWait();
        updateSidebar();
    }

    private void changeGroup() {
        TextInputDialog d = new TextInputDialog(selected.group);
        d.setTitle("Change group");
        d.setHeaderText("Set the category for '" + selected.name + "'");
        d.setContentText("Group (empty = none):");
        d.showAndWait().ifPresent(g -> {
            selected.group = g.trim();
            try { InstanceStore.save(selected); } catch (IOException ex) { alert("Save failed: " + ex.getMessage()); }
            refresh();
        });
    }

    private void deleteInstance() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + selected.name + "' and all its files? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("Delete instance");
        if (a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                if (ProcessManager.isRunning(selected)) ProcessManager.kill(selected);
                InstanceStore.remove(selected);
                selected = null;
                updateSidebar();
                refresh();
            } catch (IOException ex) {
                alert("Delete failed: " + ex.getMessage());
            }
        }
    }

    private void launch(Instance i) {
        if (ProcessManager.isRunning(i)) { alert("Instance is already running."); return; }
        var account = AccountStore.all().isEmpty() ? null : AccountStore.all().get(0);
        if (account == null) {
            alert("No account configured. Add an account first.");
            new AccountsDialog().showAndWait();
            updateSidebar();
            return;
        }
        ProcessManager.launchAsync(i, account);
        list.refresh();
    }
}
