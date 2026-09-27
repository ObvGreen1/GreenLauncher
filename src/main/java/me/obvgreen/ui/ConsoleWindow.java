package me.obvgreen.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.obvgreen.util.Http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Singleton console log window. Also used as the instance edit console.
 * Copy / Save / Upload (mclo.gs) / Clear buttons.
 */
public final class ConsoleWindow {
    private static ConsoleWindow instance;
    private final Stage stage = new Stage();
    private final TextArea area = new TextArea();
    private final Label statusLabel = new Label("Idle");

    private ConsoleWindow() {
        stage.initModality(Modality.NONE);
        stage.setTitle("GreenLauncher Console");
        stage.setWidth(760);
        stage.setHeight(520);

        area.setEditable(false);
        area.setWrapText(true);
        area.getStyleClass().add("console-area");

        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var c = new javafx.scene.input.ClipboardContent();
            c.putString(area.getText());
            cb.setContent(c);
            status("Copied to clipboard");
        });
        Button save = new Button("Save...");
        save.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save console log");
            fc.setInitialFileName("greenlauncher.log");
            var chosen = fc.showSaveDialog(stage);
            Path f = chosen == null ? null : chosen.toPath();
            if (f != null) {
                try { Files.writeString(f, area.getText()); status("Saved to " + f); }
                catch (IOException ex) { status("Save failed: " + ex.getMessage()); }
            }
        });
        Button upload = new Button("Upload (mclo.gs)");
        upload.setOnAction(e -> {
            status("Uploading...");
            new Thread(() -> {
                try {
                    String body = "content=" + java.net.URLEncoder.encode(area.getText(), java.nio.charset.StandardCharsets.UTF_8);
                    String resp = Http.postString("https://api.mclo.gs/1/log", body, "application/x-www-form-urlencoded");
                    String url = me.obvgreen.util.Json.parseObject(resp).get("url").getAsString();
                    var cb = new javafx.scene.input.ClipboardContent();
                    cb.putString(url);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cb);
                    javafx.application.Platform.runLater(() -> status("Uploaded: " + url + " (copied)"));
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> status("Upload failed: " + ex.getMessage()));
                }
            }, "mclogs-upload").start();
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> area.clear());

        HBox buttons = new HBox(8, copy, save, upload, clear, statusLabel);
        buttons.getStyleClass().add("console-toolbar");
        statusLabel.getStyleClass().add("status");
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8));

        BorderPane root = new BorderPane(area, buttons, null, null, null);
        Scene scene = new Scene(root);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> stage.hide());
    }

    public static synchronized ConsoleWindow get() {
        if (instance == null) instance = new ConsoleWindow();
        return instance;
    }

    /** Shared mutable buffer so Edit dialogs can show the live log too. */
    private static List<String> sharedBuffer;

    public static void attachBuffer(List<String> buffer) { sharedBuffer = buffer; }

    public String text() {
        if (!sharedBufferIsEmpty()) return String.join("\n", sharedBuffer);
        return area.getText();
    }

    private static boolean sharedBufferIsEmpty() {
        return sharedBuffer == null || sharedBuffer.isEmpty();
    }

    public void showFor(String title) {
        stage.setTitle("GreenLauncher Console - " + title);
        if (stage.isShowing()) stage.toFront();
        else stage.show();
    }

    public void append(String line) {
        area.appendText(line);
    }

    public void status(String s) { statusLabel.setText(s); }

    public void setText(String s) { area.setText(s); }
}
