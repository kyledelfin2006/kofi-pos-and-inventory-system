package com.cc104.pos.app; // Application layer: window, login, navigation. It wires everything together.

import com.cc104.pos.data.CsvPosRepository;    // CSV storage implementation.
import com.cc104.pos.data.CsvStore;            // Low-level CSV file helper.
import com.cc104.pos.service.PosService;       // Business layer.
import com.cc104.pos.ui.view.DashboardView;    // Overview screen.
import com.cc104.pos.ui.view.PointOfSaleView;  // Checkout screen.
import com.cc104.pos.ui.view.ProductView;      // Product maintenance screen.
import com.cc104.pos.ui.view.ReceiptDialog;    // Receipt preview dialog.
import com.cc104.pos.ui.view.ReportsView;      // Reports screen.
import com.cc104.pos.ui.view.SalesView;        // Receipts screen.
import com.cc104.pos.ui.view.SettingsView;     // Settings + reset screen.
import com.cc104.pos.ui.view.StockView;        // Stock screen.
import javafx.application.Application;         // Base class of every JavaFX program.
import javafx.geometry.Insets;                 // Padding.
import javafx.geometry.Pos;                    // Alignment.
import javafx.scene.Node;                      // Base visual type.
import javafx.scene.Parent;                    // Root node type of a scene.
import javafx.scene.Scene;                     // Window content.
import javafx.scene.control.*;                 // Label, Button, TextField, PasswordField, Alert, ScrollPane, Separator ...
import javafx.scene.input.KeyCode;             // Keys.
import javafx.scene.layout.*;                  // VBox, HBox, BorderPane, StackPane, Region, Priority.
import javafx.stage.Stage;                     // The window.

import java.io.IOException; // Storage failure.
import java.util.List;      // Module names.

import static com.cc104.pos.ui.common.UiSupport.field;          // Labelled input helper.
import static com.cc104.pos.ui.common.UiSupport.limitFormatter; // Max-length filter.
import static com.cc104.pos.ui.common.UiSupport.showFieldError; // Error display helper.

/** JavaFX application shell: login, sidebar, top bar; screen behavior lives in the ui.view package. */
public final class PosApp extends Application {
    /** Sidebar entries; the text is also the key used by {@link #showModule(String)}. */
    private static final List<String> MODULES = List.of(
            "Overview", "Point of Sale", "Products", "Stock", "Sales & Receipts", "Reports", "Settings");

    private Stage stage;                 // The single window.
    private PosService service;          // Business layer shared by all screens.
    private String cashier;              // Username of the signed-in user.
    private BorderPane appShell;         // Layout: sidebar left, top bar top, content centre.
    private StackPane contentHost;       // Holds whichever screen is active.
    private Label pageTitle;             // Title in the top bar.
    private Label statusLabel;           // Status message in the top bar.
    private PointOfSaleView activePosView; // Non-null only while the checkout screen is showing.
    private String pendingStockSku;      // SKU to preselect when the Stock screen opens.

    /** JavaFX calls this once after startup; it is the "main" of a JavaFX app. */
    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage; // Remember the window.
        try {
            // Composition root: choose CSV storage, hand it to the service.
            service = new PosService(new CsvPosRepository(new CsvStore()));
            showLogin(); // Open the sign-in screen.
        } catch (IOException e) {
            showFatal("Local CSV data could not be opened", e.getMessage());
        }
    }

    /** Creates a scene with the shared stylesheet and the Escape-key hook for the checkout screen. */
    private Scene scene(Parent root) {
        Scene scene = new Scene(root, 1360, 860);                // Initial window size.
        var stylesheet = getClass().getResource("/style.css");   // Look for the CSS in resources.
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && activePosView != null) {
                Node focus = scene.getFocusOwner();              // Which control has the cursor.
                activePosView.handleEscape(focus);
            }
        });
        return scene;
    }

    /** Builds and shows the sign-in screen. */
    private void showLogin() {
        activePosView = null; // No checkout screen while logged out.
        Label brand = new Label("Tindahan POS");
        brand.getStyleClass().add("brand-title");
        Label subBrand = new Label("OFFLINE SALES & STOCK");
        subBrand.getStyleClass().add("brand-subtitle");

        Label title = new Label("Welcome back");
        title.getStyleClass().add("page-heading");
        Label description = new Label("Sign in to open the register and manage store records.");
        description.getStyleClass().add("muted-text");

        TextField username = new TextField();
        username.setPromptText("Username");
        username.setTextFormatter(limitFormatter(30));
        PasswordField password = new PasswordField();   // Hides the typed characters.
        password.setPromptText("Password");
        password.setTextFormatter(limitFormatter(128));
        Label error = new Label();
        error.getStyleClass().add("field-error");
        Button login = new Button("Sign in");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        login.disableProperty().bind(username.textProperty().isEmpty().or(password.textProperty().isEmpty())); // Disabled until both filled.

        Runnable submit = () -> { // Runs when the user signs in.
            String cleanUsername = username.getText().trim();
            username.setText(cleanUsername);
            if (cleanUsername.isEmpty() || cleanUsername.length() > 30) {
                showFieldError(username, error, "Enter a username up to 30 characters.");
                return;
            }
            if (!service.authenticate(cleanUsername, password.getText())) { // Wrong credentials.
                password.clear();
                showFieldError(username, error, "Username or password is incorrect.");
                showFieldError(password, error, "Username or password is incorrect.");
                password.requestFocus();
                return;
            }
            cashier = cleanUsername; // Remember who signed in.
            showShell("Overview");   // Enter the main application.
        };
        login.setOnAction(event -> submit.run());
        username.setOnAction(event -> { submit.run(); event.consume(); });
        password.setOnAction(event -> { submit.run(); event.consume(); });
        username.textProperty().addListener((obs, old, value) -> { // Typing clears the error state.
            showFieldError(username, error, "");
            showFieldError(password, error, "");
        });
        password.textProperty().addListener((obs, old, value) -> {
            showFieldError(username, error, "");
            showFieldError(password, error, "");
        });
        username.focusedProperty().addListener((obs, old, focused) -> { if (!focused) username.setText(username.getText().trim()); });

        Label demo = new Label("Demo access  ·  admin / admin123");
        demo.getStyleClass().add("muted-small");
        VBox form = new VBox(14, brand, subBrand, new Separator(), title, description,
                field("Username *", username), field("Password *", password), error, login, demo);
        form.getStyleClass().add("login-panel");
        form.setMaxWidth(430);
        form.setPadding(new Insets(34));

        StackPane root = new StackPane(form); // Centres the form in the window.
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(32));
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) { // Esc clears the form.
                username.clear();
                password.clear();
                error.setText("");
            }
        });
        stage.setTitle("Tindahan POS · Sign in");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setScene(scene(root));
        stage.show();
        username.requestFocus(); // Cursor starts in the username box.
    }

    /** Builds the main layout (sidebar + top bar + content) and opens the given module. */
    private void showShell(String module) {
        appShell = new BorderPane();
        appShell.getStyleClass().add("app-root");
        contentHost = new StackPane();
        contentHost.getStyleClass().add("content-host");
        appShell.setLeft(buildNavigation());
        appShell.setTop(buildTopBar());
        appShell.setCenter(contentHost);
        stage.setTitle(service.settings().storeName() + " · POS");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene(appShell));
        stage.setMaximized(true);
        showModule(module);
    }

    /** Builds the left sidebar with module buttons and the Exit button. */
    private VBox buildNavigation() {
        Label brand = new Label(service.settings().storeName()); // Store name from settings.
        brand.getStyleClass().add("nav-brand");
        Label kind = new Label("LOCAL REGISTER");
        kind.getStyleClass().add("nav-caption");
        VBox brandBox = new VBox(4, brand, kind);
        brandBox.setPadding(new Insets(24, 18, 24, 20));

        VBox links = new VBox(6);
        links.setPadding(new Insets(8, 10, 10, 10));
        for (String module : MODULES) { // One button per module.
            Button link = new Button(module);
            link.getStyleClass().add("nav-button");
            link.setMaxWidth(Double.MAX_VALUE);
            link.setAlignment(Pos.CENTER_LEFT);
            link.setOnAction(event -> showModule(module));
            links.getChildren().add(link);
        }
        Region spacer = new Region(); // Pushes the exit button to the bottom.
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label local = new Label("Works without internet\nRecords saved to local CSV");
        local.getStyleClass().add("nav-footnote");
        local.setPadding(new Insets(14, 18, 20, 18));
        Button quit = new Button("Exit application");
        quit.getStyleClass().add("nav-button");
        quit.setMaxWidth(Double.MAX_VALUE);
        quit.setAlignment(Pos.CENTER_LEFT);
        quit.setOnAction(event -> {
            if (!service.cart().isEmpty()) { // Warn before throwing away an unfinished sale.
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "The current cart has not been checked out. Exit and discard it?", ButtonType.CANCEL, ButtonType.OK);
                confirm.initOwner(stage);
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                service.clearCart();
            }
            stage.close();
        });
        VBox nav = new VBox(brandBox, new Separator(), links, spacer, quit, local);
        nav.getStyleClass().add("sidebar");
        nav.setPrefWidth(224);
        return nav;
    }

    /** Builds the top bar: page title, status text, signed-in chip, sign-out button. */
    private HBox buildTopBar() {
        pageTitle = new Label();
        pageTitle.getStyleClass().add("topbar-title");
        statusLabel = new Label("Local data ready");
        statusLabel.getStyleClass().add("status-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label user = new Label("Signed in as  " + cashier);
        user.getStyleClass().add("user-chip");
        Button exit = new Button("Sign out");
        exit.getStyleClass().add("quiet-button");
        exit.setOnAction(event -> {
            service.clearCart();    // Discard any unfinished sale.
            pendingStockSku = null;
            showLogin();
        });
        HBox bar = new HBox(18, pageTitle, statusLabel, spacer, user, exit);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 28));
        bar.getStyleClass().add("topbar");
        return bar;
    }

    /** Swaps the centre of the window to the requested screen and highlights its sidebar button. */
    private void showModule(String module) {
        activePosView = null;          // Only set again when the POS screen is chosen.
        pageTitle.setText(module);
        Node view = switch (module) {  // Pick the screen class for this module name.
            case "Overview" -> new DashboardView(service, this::openStock).build();
            case "Point of Sale" -> {
                activePosView = new PointOfSaleView(service, cashier, this::showNotice,
                        sale -> new ReceiptDialog(service, stage).show(sale));
                yield activePosView.build();
            }
            case "Products" -> new ProductView(service, stage, this::showNotice).build();
            case "Stock" -> new StockView(service, pendingStockSku, () -> pendingStockSku = null,
                    this::showNotice, this::showModule).build();
            case "Sales & Receipts" -> new SalesView(service, stage, this::showNotice).build();
            case "Reports" -> new ReportsView(service).build();
            case "Settings" -> new SettingsView(service, stage, this::showNotice, this::showModule, this::settingsSaved).build();
            default -> new DashboardView(service, this::openStock).build();
        };
        ScrollPane scroll = new ScrollPane(view); // Allow scrolling on small windows.
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        contentHost.getChildren().setAll(scroll);
        for (Node node : ((VBox) appShell.getLeft()).getChildren()) { // Update the active-button highlight.
            if (node instanceof VBox links) {
                for (Node link : links.getChildren()) {
                    if (link instanceof Button button) {
                        button.getStyleClass().remove("nav-button-active");
                        if (button.getText().equals(module)) button.getStyleClass().add("nav-button-active");
                    }
                }
            }
        }
    }

    /** Opens the Stock screen with a product preselected (used by the restock queue). */
    private void openStock(String sku) {
        pendingStockSku = sku;
        showModule("Stock");
    }

    /** Called after settings change or data is reset: refresh title, sidebar and the Settings screen. */
    private void settingsSaved() {
        stage.setTitle(service.settings().storeName() + " · POS");
        appShell.setLeft(buildNavigation()); // Rebuild the sidebar so the new store name shows.
        showModule("Settings");
    }

    /** Shows a green message in the top bar. */
    private void showNotice(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.getStyleClass().remove("status-error");
            statusLabel.getStyleClass().add("status-success");
        }
    }

    /** Shows an unrecoverable startup error and closes the app. */
    private void showFatal(String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR, detail, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
        stage.close();
    }

    /** JavaFX calls this when the app closes. */
    @Override
    public void stop() {
        if (service != null) service.clearCart(); // Nothing else to clean up: data is saved after every change.
    }

    /** Starts JavaFX; {@link com.cc104.pos.Launcher} calls this. */
    public static void main(String[] args) {
        launch(args);
    }
}
