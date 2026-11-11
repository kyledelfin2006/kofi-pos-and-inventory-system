package com.cc104.pos;

import com.cc104.pos.model.Sale;
import com.cc104.pos.data.CsvPosRepository;
import com.cc104.pos.data.CsvStore;
import com.cc104.pos.service.PosService;
import com.cc104.pos.ui.DashboardView;
import com.cc104.pos.ui.PointOfSaleView;
import com.cc104.pos.ui.ProductView;
import com.cc104.pos.ui.ReceiptDialog;
import com.cc104.pos.ui.ReportsView;
import com.cc104.pos.ui.SalesView;
import com.cc104.pos.ui.SettingsView;
import com.cc104.pos.ui.StockView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

import static com.cc104.pos.ui.UiSupport.field;
import static com.cc104.pos.ui.UiSupport.limitFormatter;
import static com.cc104.pos.ui.UiSupport.showFieldError;

/** JavaFX entry point and application shell; screen behavior lives in module views. */
public final class PosApp extends Application {
    private static final List<String> MODULES = List.of(
            "Overview", "Point of Sale", "Products", "Stock", "Sales & Receipts", "Reports", "Settings");

    private Stage stage;
    private PosService service;
    private String cashier;
    private BorderPane appShell;
    private StackPane contentHost;
    private Label pageTitle;
    private Label statusLabel;
    private PointOfSaleView activePosView;
    private String pendingStockSku;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        try {
            service = new PosService(new CsvPosRepository(new CsvStore()));
            showLogin();
        } catch (IOException e) {
            showFatal("Local CSV data could not be opened", e.getMessage());
        }
    }

    private Scene scene(Parent root) {
        Scene scene = new Scene(root, 1360, 860);
        var stylesheet = getClass().getResource("/style.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && activePosView != null) {
                Node focus = scene.getFocusOwner();
                activePosView.handleEscape(focus);
            }
        });
        return scene;
    }

    private void showLogin() {
        activePosView = null;
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
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setTextFormatter(limitFormatter(128));
        Label error = new Label();
        error.getStyleClass().add("field-error");
        Button login = new Button("Sign in");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        login.disableProperty().bind(username.textProperty().isEmpty().or(password.textProperty().isEmpty()));

        Runnable submit = () -> {
            String cleanUsername = username.getText().trim();
            username.setText(cleanUsername);
            if (cleanUsername.isEmpty() || cleanUsername.length() > 30) {
                showFieldError(username, error, "Enter a username up to 30 characters.");
                return;
            }
            if (!service.authenticate(cleanUsername, password.getText())) {
                password.clear();
                showFieldError(username, error, "Username or password is incorrect.");
                showFieldError(password, error, "Username or password is incorrect.");
                password.requestFocus();
                return;
            }
            cashier = cleanUsername;
            showShell("Overview");
        };
        login.setOnAction(event -> submit.run());
        username.setOnAction(event -> { submit.run(); event.consume(); });
        password.setOnAction(event -> { submit.run(); event.consume(); });
        username.textProperty().addListener((obs, old, value) -> {
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

        StackPane root = new StackPane(form);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(32));
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
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
        username.requestFocus();
    }

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

    private VBox buildNavigation() {
        Label brand = new Label(service.settings().storeName());
        brand.getStyleClass().add("nav-brand");
        Label kind = new Label("LOCAL REGISTER");
        kind.getStyleClass().add("nav-caption");
        VBox brandBox = new VBox(4, brand, kind);
        brandBox.setPadding(new Insets(24, 18, 24, 20));

        VBox links = new VBox(6);
        links.setPadding(new Insets(8, 10, 10, 10));
        for (String module : MODULES) {
            Button link = new Button(module);
            link.getStyleClass().add("nav-button");
            link.setMaxWidth(Double.MAX_VALUE);
            link.setAlignment(Pos.CENTER_LEFT);
            link.setOnAction(event -> showModule(module));
            links.getChildren().add(link);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label local = new Label("Works without internet\nRecords saved to local CSV");
        local.getStyleClass().add("nav-footnote");
        local.setPadding(new Insets(14, 18, 20, 18));
        Button quit = new Button("Exit application");
        quit.getStyleClass().add("nav-button");
        quit.setMaxWidth(Double.MAX_VALUE);
        quit.setAlignment(Pos.CENTER_LEFT);
        quit.setOnAction(event -> {
            if (!service.cart().isEmpty()) {
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
            service.clearCart();
            pendingStockSku = null;
            showLogin();
        });
        HBox bar = new HBox(18, pageTitle, statusLabel, spacer, user, exit);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 28));
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private void showModule(String module) {
        activePosView = null;
        pageTitle.setText(module);
        Node view = switch (module) {
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
            case "Settings" -> new SettingsView(service, this::showNotice, this::showModule, this::settingsSaved).build();
            default -> new DashboardView(service, this::openStock).build();
        };
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        contentHost.getChildren().setAll(scroll);
        for (Node node : ((VBox) appShell.getLeft()).getChildren()) {
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

    private void openStock(String sku) {
        pendingStockSku = sku;
        showModule("Stock");
    }

    private void settingsSaved() {
        stage.setTitle(service.settings().storeName() + " · POS");
        appShell.setLeft(buildNavigation());
        showModule("Settings");
    }

    private void showNotice(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.getStyleClass().remove("status-error");
            statusLabel.getStyleClass().add("status-success");
        }
    }

    private void showFatal(String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR, detail, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
        stage.close();
    }

    @Override
    public void stop() {
        if (service != null) service.clearCart();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
