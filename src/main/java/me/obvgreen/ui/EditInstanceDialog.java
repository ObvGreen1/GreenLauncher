package me.obvgreen.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.obvgreen.core.instance.Instance;
import me.obvgreen.core.modrinth.ContentManager;
import me.obvgreen.core.modrinth.ModrinthApi;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/** Edit Instance: console, version/loader management, mods, resource packs, shader packs, worlds. */
public class EditInstanceDialog extends Stage {
    final Instance inst;

    public EditInstanceDialog(Instance inst) {
        this.inst = inst;
        setTitle("Edit Instance - " + inst.name);
        initModality(Modality.APPLICATION_MODAL);
        setWidth(860);
        setHeight(640);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                consoleTab(),
                versionTab(),
                contentTab("Mods", me.obvgreen.core.modrinth.ModrinthApi.Type.MOD),
                contentTab("Resource Packs", me.obvgreen.core.modrinth.ModrinthApi.Type.RESOURCE_PACK),
                contentTab("Shader Packs", me.obvgreen.core.modrinth.ModrinthApi.Type.SHADER),
                worldsTab()
        );

        BorderPane root = new BorderPane(tabs);
        root.setPadding(new Insets(8));
        setScene(new Scene(root));
        Theme.apply(getScene());
    }

    private Tab consoleTab() {
        TextArea area = new TextArea(ConsoleWindow.get().text());
        area.setEditable(false);
        area.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        HBox buttons = new HBox(8,
                btn("Open live console", () -> ConsoleWindow.get().showFor(inst.name)),
                btn("Copy", () -> {
                    var c = new javafx.scene.input.ClipboardContent();
                    c.putString(area.getText());
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
                }),
                btn("Refresh", () -> area.setText(ConsoleWindow.get().text()))
        );
        buttons.setPadding(new Insets(6));
        VBox v = new VBox(buttons, area);
        VBox.setVgrow(area, Priority.ALWAYS);
        return new Tab("Minecraft Log", v);
    }

    Button btn(String label, Runnable r) {
        Button b = new Button(label);
        b.setOnAction(e -> r.run());
        return b;
    }

    String currentLoaderText() {
        return "Version: " + inst.mcVersion + " | Loader: " + inst.loader
                + (inst.loaderVersion.isBlank() ? "" : " " + inst.loaderVersion)
                + " | Launched version id: " + inst.versionId;
    }

    void save() {
        try { InstanceStore_save(); } catch (IOException e) { MainView.alert("Save failed: " + e.getMessage()); }
    }

    private void InstanceStore_save() throws IOException {
        me.obvgreen.core.instance.InstanceStore.save(inst);
    }

    private Tab versionTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        Label info = new Label(currentLoaderText());
        info.setWrapText(true);
        Label status = new Label("");

        java.util.function.BiConsumer<javafx.concurrent.Task<String>, String> run = (t, msg) -> {
            status.setText(msg);
            t.setOnSucceeded(e -> {
                if (t.getValue() != null) { inst.versionId = t.getValue(); inst.loaderVersion = ""; }
                save();
                info.setText(currentLoaderText());
                status.setText("Done." + (t.getValue() != null ? " Now: " + t.getValue() : ""));
            });
            t.setOnFailed(e -> status.setText("Failed: " + t.getException().getMessage()));
            new Thread(t, "loader-task").start();
        };

        TextField fabricVer = new TextField();
        fabricVer.setPromptText("Fabric loader version (empty = latest)");
        Button installFabric = btn("Install/switch Fabric", () ->
                run.accept(new javafx.concurrent.Task<>() {
                    @Override protected String call() throws Exception {
                        inst.loader = Instance.Loader.FABRIC;
                        return me.obvgreen.core.loader.FabricLoader.install(inst, fabricVer.getText().trim(), null);
                    }
                }, "Installing Fabric..."));

        TextField forgeVer = new TextField();
        forgeVer.setPromptText("Forge version (empty = latest)");
        Button installForge = btn("Install/switch Forge", () ->
                run.accept(new javafx.concurrent.Task<>() {
                    @Override protected String call() throws Exception {
                        inst.loader = Instance.Loader.FORGE;
                        var s = me.obvgreen.core.settings.SettingsStore.get();
                        String override = !inst.javaPathOverride.isBlank() ? inst.javaPathOverride : s.defaultJavaPath;
                        String exe = me.obvgreen.core.java.JavaRuntimeManager
                                .resolveJava(me.obvgreen.core.java.JavaRuntimeManager.requiredMajor(null, inst.mcVersion),
                                        override, null).toString();
                        return me.obvgreen.core.loader.ForgeLoader.install(inst, forgeVer.getText().trim(), exe, null);
                    }
                }, "Installing Forge (runs the official installer, takes a while)..."));

        Button updateLoader = btn("Update loader to latest", () ->
                run.accept(new javafx.concurrent.Task<>() {
                    @Override protected String call() throws Exception {
                        if (inst.loader == Instance.Loader.FABRIC)
                            return me.obvgreen.core.loader.FabricLoader.install(inst, "", null);
                        if (inst.loader == Instance.Loader.FORGE) {
                            var s = me.obvgreen.core.settings.SettingsStore.get();
                            String override = !inst.javaPathOverride.isBlank() ? inst.javaPathOverride : s.defaultJavaPath;
                            String exe = me.obvgreen.core.java.JavaRuntimeManager
                                    .resolveJava(me.obvgreen.core.java.JavaRuntimeManager.requiredMajor(null, inst.mcVersion),
                                            override, null).toString();
                            return me.obvgreen.core.loader.ForgeLoader.install(inst, "", exe, null);
                        }
                        return null;
                    }
                }, "Updating loader..."));

        Button removeLoader = btn("Remove loader (revert to vanilla)", () -> {
            inst.loader = Instance.Loader.NONE;
            inst.versionId = inst.mcVersion;
            inst.loaderVersion = "";
            save();
            info.setText(currentLoaderText());
            status.setText("Loader removed.");
        });

        box.getChildren().addAll(info, new Label("Install/switch loader:"), installFabric, fabricVer,
                installForge, forgeVer, updateLoader, removeLoader, status);
        return new Tab("Version", box);
    }

    /** Shared Modrinth tab: installed files list + search/install/update/remove. */
    private Tab contentTab(String title, me.obvgreen.core.modrinth.ModrinthApi.Type type) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        ListView<java.nio.file.Path> installed = new ListView<>();
        installed.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(java.nio.file.Path p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getFileName().toString());
            }
        });
        Runnable refreshInstalled = () -> {
            try {
                installed.getItems().setAll(me.obvgreen.core.modrinth.ContentManager.installed(inst, type));
            } catch (Exception ignored) {}
        };
        refreshInstalled.run();

        ListView<ModrinthApi.SearchResult> results = new ListView<>();
        results.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(ModrinthApi.SearchResult r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null
                        : r.title + " by " + r.author + " (" + String.format("%,d", r.downloads) + " downloads)\n"
                        + r.description);
                setWrapText(true);
                setPrefHeight(48);
            }
        });

        TextField search = new TextField();
        search.setPromptText("Search " + type.projectType + "s on Modrinth...");
        Label status = new Label("");

        Button doSearch = btn("Search", () -> {
            status.setText("Searching...");
            javafx.concurrent.Task<List<ModrinthApi.SearchResult>> t = new javafx.concurrent.Task<>() {
                @Override protected List<ModrinthApi.SearchResult> call() throws Exception {
                    String loader = type == ModrinthApi.Type.MOD && inst.loader == Instance.Loader.FABRIC ? "fabric"
                            : type == ModrinthApi.Type.MOD && inst.loader == Instance.Loader.FORGE ? "forge" : null;
                    return ModrinthApi.search(search.getText(), type, inst.mcVersion, loader);
                }
            };
            t.setOnSucceeded(e -> { results.getItems().setAll(t.getValue()); status.setText("Found " + t.getValue().size() + " results"); });
            t.setOnFailed(e -> status.setText("Search failed: " + t.getException().getMessage()));
            new Thread(t, "modrinth-search").start();
        });
        search.setOnAction(e -> doSearch.fire());

        Button addBtn = btn("Add selected", () -> {
            ModrinthApi.SearchResult r = results.getSelectionModel().getSelectedItem();
            if (r == null) { status.setText("Select a search result first."); return; }
            status.setText("Downloading " + r.title + "...");
            javafx.concurrent.Task<String> t = new javafx.concurrent.Task<>() {
                @Override protected String call() throws Exception {
                    String loader = type == ModrinthApi.Type.MOD ? (inst.loader == Instance.Loader.FABRIC ? "fabric"
                            : inst.loader == Instance.Loader.FORGE ? "forge" : null) : null;
                    ModrinthApi.VersionFile f = ModrinthApi.latestVersion(r.projectId, inst.mcVersion, loader);
                    if (f == null) throw new IllegalStateException("No version compatible with " + inst.mcVersion);
                    ContentManager.install(inst, type, f);
                    return f.fileName;
                }
            };
            t.setOnSucceeded(e -> { status.setText("Installed: " + t.getValue()); refreshInstalled.run(); });
            t.setOnFailed(e -> status.setText("Install failed: " + t.getException().getMessage()));
            new Thread(t, "modrinth-install").start();
        });

        Button removeBtn = btn("Remove selected", () -> {
            java.nio.file.Path p = installed.getSelectionModel().getSelectedItem();
            if (p == null) return;
            try { ContentManager.remove(p); refreshInstalled.run(); status.setText("Removed."); }
            catch (Exception ex) { status.setText("Remove failed: " + ex.getMessage()); }
        });

        Button updateBtn = btn("Update selected", () -> {
            java.nio.file.Path p = installed.getSelectionModel().getSelectedItem();
            if (p == null) return;
            status.setText("Checking for update...");
            javafx.concurrent.Task<String> t = new javafx.concurrent.Task<>() {
                @Override protected String call() throws Exception {
                    return ContentManager.update(inst, type, p)
                            ? "Updated from " + p.getFileName() : "Already latest: " + p.getFileName();
                }
            };
            t.setOnSucceeded(e -> { status.setText(t.getValue()); refreshInstalled.run(); });
            t.setOnFailed(e -> status.setText("Update failed: " + t.getException().getMessage()));
            new Thread(t, "modrinth-update").start();
        });

        root.getChildren().addAll(
                new Label("Installed (" + type.dirName + "):"),
                installed,
                new HBox(8, removeBtn, updateBtn),
                new Separator(),
                new HBox(8, search, doSearch),
                results,
                new HBox(8, addBtn),
                status);
        VBox.setVgrow(results, Priority.ALWAYS);
        VBox.setVgrow(installed, Priority.ALWAYS);
        return new Tab(title, root);
    }

    private Tab worldsTab() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        ListView<String> worlds = new ListView<>();
        try {
            var dir = inst.savesDir();
            if (java.nio.file.Files.isDirectory(dir)) {
                try (Stream<java.nio.file.Path> s = java.nio.file.Files.list(dir)) {
                    worlds.getItems().setAll(s.filter(java.nio.file.Files::isDirectory)
                            .map(p -> p.getFileName().toString()).sorted().collect(java.util.stream.Collectors.toList()));
                }
            }
        } catch (Exception ignored) {}
        HBox buttons = new HBox(8,
                btn("Open folder", () -> {
                    try { java.awt.Desktop.getDesktop().open(inst.savesDir().toFile()); }
                    catch (Exception ex) { MainView.alert("Failed: " + ex.getMessage()); }
                })
        );
        root.getChildren().addAll(new Label("Worlds in this instance:"), worlds, buttons);
        VBox.setVgrow(worlds, Priority.ALWAYS);
        return new Tab("Worlds", root);
    }
}

