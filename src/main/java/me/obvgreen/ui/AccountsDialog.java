package me.obvgreen.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.obvgreen.core.account.AccountStore;

/** Account manager. Microsoft login is planned for later; offline accounts only for now. */
public class AccountsDialog extends Stage {
    private final ListView<AccountStoreHelper> list = new ListView<>();

    private static final class AccountStoreHelper {
        final me.obvgreen.core.account.Account a;
        AccountStoreHelper(me.obvgreen.core.account.Account a) { this.a = a; }
        @Override public String toString() { return a.username + "  (" + a.type + ")"; }
    }

    public AccountsDialog() {
        setTitle("Accounts");
        initModality(Modality.APPLICATION_MODAL);
        setWidth(420);
        setHeight(360);

        TextField name = new TextField();
        name.setPromptText("Offline username (max 16 chars)");
        Button add = new Button("Add offline account");
        add.setOnAction(e -> {
            try {
                AccountStore.addOffline(name.getText());
                name.clear();
                refresh();
            } catch (Exception ex) {
                MainView.alert(ex.getMessage());
            }
        });
        Button remove = new Button("Remove selected");
        remove.setOnAction(e -> {
            var sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) { AccountStore.remove(sel.a); refresh(); }
        });

        VBox root = new VBox(10,
                new Label("Offline accounts (Microsoft login coming later):"),
                list,
                new HBox(8, name, add),
                new HBox(8, remove),
                new Label("Note: offline mode only works on servers with online-mode=false."))
        ;
        root.setPadding(new Insets(14));
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        refresh();
        setScene(new Scene(root));
        Theme.apply(getScene());
    }

    private void refresh() {
        list.getItems().setAll(AccountStore.all().stream().map(AccountStoreHelper::new)
                .collect(java.util.stream.Collectors.toList()));
    }
}
