package me.obvgreen.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.obvgreen.core.download.GameDownloader;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.instance.InstanceStore;
import me.obvgreen.core.loader.FabricLoader;
import me.obvgreen.core.loader.ForgeLoader;
import me.obvgreen.core.mojang.MojangApi;
import me.obvgreen.core.mojang.VersionEntry;
import me.obvgreen.core.mojang.VersionType;

import java.util.ArrayList;
import java.util.List;

/** Create instance dialog: version filters (multi-select), loader picker, download progress. */
public class CreateInstanceDialog extends Stage {
    private final MainView main;
    private final TextField nameField = new TextField("My Instance");
    private final TextField groupField = new TextField();
    private final TextField searchField = new TextField();
    private final ListView<VersionEntry> versionList = new ListView<>();
    private final ComboBox<String> loaderCombo = new ComboBox<>(javafx.collections.FXCollections
            .observableArrayList("None", "Fabric", "Forge"));
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label("");
    private final List<CheckBox> typeChecks = new ArrayList<>();
    private List<VersionEntry> manifest = new ArrayList<>();

    public CreateInstanceDialog(MainView main) {
        this.main = main;
        setTitle("Download Instance");
        initModality(Modality.APPLICATION_MODAL);
        setWidth(620);
        setHeight(600);

        loaderCombo.getSelectionModel().selectFirst();

        HBox filters = new HBox(10);
        filters.setPadding(new Insets(6, 0, 6, 0));
        for (VersionType t : VersionType.values()) {
            CheckBox cb = new CheckBox(pretty(t));
            cb.setSelected(t == VersionType.RELEASE);
            cb.selectedProperty().addListener((o, ov, nv) -> applyFilter());
            typeChecks.add(cb);
            filters.getChildren().add(cb);
        }

        searchField.textProperty().addListener((o, ov, nv) -> applyFilter());
        searchField.setPromptText("Search versions...");

        versionList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(VersionEntry e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : e.id + "  [" + pretty(mapType(e.type)) + "]");
            }
        });

        Button createBtn = new Button("Download & Create");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> create(createBtn));

        VBox root = new VBox(10,
                row("Name:", nameField),
                row("Group/category:", groupField),
                new Label("Filter versions (multi-select allowed):"),
                filters,
                searchField,
                new Label("Minecraft versions:"),
                versionList,
                row("Mod loader:", loaderCombo),
                progress, status, createBtn);
        root.setPadding(new Insets(14));
        VBox.setVgrow(versionList, Priority.ALWAYS);

        setScene(new Scene(root));
        Theme.apply(getScene());

        progress.setVisible(false);
        status.setText("Loading version manifest...");
        loadManifest();
    }

    private static HBox row(String label, javafx.scene.Node n) {
        return new HBox(10, new Label(label), n);
    }

    private static String pretty(VersionType t) {
        return switch (t) {
            case RELEASE -> "Releases";
            case SNAPSHOT -> "Snapshots";
            case BETA -> "Betas";
            case ALPHA -> "Alphas";
            case EXPERIMENTAL -> "Experimentals";
        };
    }

    private static VersionType mapType(String mojangType) {
        for (VersionType t : VersionType.values()) if (t.mojangId.equals(mojangType)) return t;
        return VersionType.RELEASE;
    }

    private void loadManifest() {
        Task<List<VersionEntry>> task = new Task<>() {
            @Override protected List<VersionEntry> call() throws Exception { return MojangApi.manifest(); }
        };
        task.setOnSucceeded(e -> {
            manifest = task.getValue();
            status.setText("Version manifest loaded (" + manifest.size() + " versions)");
            applyFilter();
        });
        task.setOnFailed(e -> status.setText("Failed to load manifest: " + task.getException().getMessage()));
        new Thread(task, "manifest-load").start();
    }

    private void applyFilter() {
        List<VersionType> selected = new ArrayList<>();
        for (int i = 0; i < typeChecks.size(); i++) if (typeChecks.get(i).isSelected()) selected.add(VersionType.values()[i]);
        List<VersionEntry> filtered = new ArrayList<>();
        try {
            for (VersionEntry e : MojangApi.filtered(selected)) {
                if (searchField.getText() == null || searchField.getText().isBlank()
                        || e.id.toLowerCase().contains(searchField.getText().toLowerCase())) {
                    filtered.add(e);
                }
            }
        } catch (Exception ignored) { }
        versionList.setItems(javafx.collections.FXCollections.observableArrayList(filtered));
    }

    private void create(Button createBtn) {
        VersionEntry v = versionList.getSelectionModel().getSelectedItem();
        if (v == null) { status.setText("Pick a version first."); return; }
        String name = nameField.getText().isBlank() ? v.id : nameField.getText().trim();
        Instance inst = new Instance();
        inst.name = name;
        inst.group = groupField.getText().trim();
        inst.mcVersion = v.id;
        inst.loader = switch (loaderCombo.getValue()) {
            case "Fabric" -> Instance.Loader.FABRIC;
            case "Forge" -> Instance.Loader.FORGE;
            default -> Instance.Loader.NONE;
        };
        inst.versionId = v.id;

        progress.setVisible(true);
        createBtn.setDisable(true);
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                switch (inst.loader) {
                    case FABRIC -> inst.versionId = FabricLoader.install(inst, "", p -> updateProgress(p, 1));
                    case FORGE -> {
                        var s = me.obvgreen.core.settings.SettingsStore.get();
                        String override = !inst.javaPathOverride.isBlank() ? inst.javaPathOverride : s.defaultJavaPath;
                        String exe = me.obvgreen.core.java.JavaRuntimeManager
                                .resolveJava(me.obvgreen.core.java.JavaRuntimeManager.requiredMajor(null, inst.mcVersion),
                                        override, p -> updateProgress(p, 1)).toString();
                        inst.versionId = ForgeLoader.install(inst, "", exe, p -> updateProgress(p, 1));
                    }
                    case NONE -> GameDownloader.ensureVanilla(inst, p -> updateProgress(p, 1));
                }
                InstanceStore.save(inst);
                return "ok";
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            main.refresh();
            close();
        });
        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            createBtn.setDisable(false);
            status.setText("Failed: " + task.getException().getMessage());
            try { InstanceStore.remove(inst); } catch (Exception ignored) {}
        });
        new Thread(task, "instance-download").start();
    }
}
