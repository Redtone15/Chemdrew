package com.molview.app.ui;

import com.molview.app.editor.EditorController;
import com.molview.app.io.LoadedProject;
import com.molview.app.io.ProjectIO;
import com.molview.app.model.Atom;
import com.molview.app.model.AtomKind;
import com.molview.app.model.BondType;
import com.molview.app.model.BracketType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.HexGrid;
import com.molview.app.model.Tool;
import com.molview.app.model.ViewState;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Main application window and command wiring. */
public final class MainWindow {
    private final Stage stage;
    private final EditorController controller = new EditorController();
    private final DrawingCanvas canvas;
    private final PropertiesPanel properties;
    private final BorderPane shell = new BorderPane();
    private final Label documentTitle = new Label("Untitled");
    private final Label documentSubtitle = new Label("Unsaved project");
    private final Label dirtyDot = new Label("●");
    private final Label toolLabel = new Label("Select");
    private final Label statusLabel = new Label("Ready");
    private final Label zoomLabel = new Label("100%");
    private final Label zoomPillLabel = new Label("100%");
    private final Label toastLabel = new Label();
    private final StackPane toastLayer = new StackPane(toastLabel);
    private BorderPane root;
    private Button undoButton;
    private Button redoButton;
    private MenuItem undoItem;
    private MenuItem redoItem;
    private CheckMenuItem gridItem;
    private CheckMenuItem carbonItem;
    private CheckMenuItem inspectorItem;
    private boolean updatingViewMenu;
    private boolean inspectorVisible = true;
    private SequentialTransition toastAnimation;

    private final Map<Button, Tool> toolButtons = new LinkedHashMap<>();
    private final Map<Button, String> atomButtons = new LinkedHashMap<>();
    private final Map<Button, BondType> bondButtons = new LinkedHashMap<>();

    public MainWindow(Stage stage) {
        this.stage = stage;
        controller.setShowGrid(com.molview.app.config.AppConfig.getBoolean("canvas.showGrid", true));
        controller.setShowCarbonLabels(com.molview.app.config.AppConfig.getBoolean(
                "canvas.showCarbonLabels", false));
        controller.setSnapToGrid(true);
        controller.setActiveElement("C");
        int width = com.molview.app.config.AppConfig.getInt("window.width", 1440);
        int height = com.molview.app.config.AppConfig.getInt("window.height", 900);
        canvas = new DrawingCanvas(controller);
        properties = new PropertiesPanel(controller, () -> stage,
                this::showToast, this::openPeriodicTableForAtom);
        root = buildRoot();
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(MainWindow.class.getResource("/styles/app.css").toExternalForm());
        installKeyboard(scene);
        stage.setScene(scene);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMinWidth(com.molview.app.config.AppConfig.getInt("window.minWidth", 1080));
        stage.setMinHeight(com.molview.app.config.AppConfig.getInt("window.minHeight", 700));
        stage.setTitle("Untitled");
        stage.setOnCloseRequest(event -> {
            if (!maybeSave()) {
                event.consume();
            }
        });
        loadStageIcon();
        controller.addChangeListener(this::updateChrome);
        controller.addDirtyListener(this::updateTitle);
        canvas.setToastHandler(this::showToast);
        canvas.setStatusHandler(statusLabel::setText);
        canvas.setViewChangedHandler(this::updateZoomLabel);
        canvas.setFocusInspectorHandler(properties::requestFocus);
        statusLabel.setText("Carbon loaded — click to place/change · drag empty: chain · drag atom past threshold: chain.");
        updateTitle();
        updateChrome();
    }

    public void show() {
        stage.show();
    }

    public void loadExampleForDemo() {
        controller.replaceDocument(SampleProjects.sn2Mechanism(), null, true);
        Platform.runLater(canvas::fitToContent);
    }

    /**
     * Demo mode used for documentation screenshots: a clean, all-carbon alkane
     * skeleton drawn by the same hex-grid chain engine as the editor.
     */
    public void loadHexChainDemoForDemo() {
        controller.replaceDocument(new ChemDocument("Hex-Grid Alkane"), null, true);
        controller.setActiveElement("C");
        HexGrid.Vertex start = controller.snapToVertex(300, 430);
        HexGrid.Vertex end = controller.snapToVertex(820, 450);
        controller.buildAlkaneChain(HexGrid.path(start, end));
        controller.setActiveElement("C");
        Platform.runLater(canvas::fitToContent);
    }

    public void openPathForDemo(Path path) throws IOException {
        LoadedProject project = ProjectIO.load(path);
        controller.replaceDocument(project.document(), path, true);
        Platform.runLater(() -> {
            if (project.viewState().offsetX() == 0 && project.viewState().offsetY() == 0) {
                canvas.fitToContent();
            } else {
                canvas.applyViewState(project.viewState());
            }
        });
    }

    public void writeScreenshot(Path path) throws IOException {
        canvas.hidePointerPreview();
        statusLabel.setText(controller.getDocument().isEmpty()
                ? "Carbon loaded — click to place/change · drag empty: chain · drag atom past threshold: chain."
                : "Ready  ·  hexagonal grid  ·  one-edge bonds");
        root.applyCss();
        root.layout();
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        WritableImage image = root.snapshot(params, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(path.toAbsolutePath().getParent());
        ImageIO.write(buffered, "png", path.toFile());
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setTop(buildTop());
        root.setLeft(buildToolRail());
        root.setCenter(buildCanvasArea());
        root.setRight(properties);
        root.setBottom(buildStatusBar());
        return root;
    }

    private VBox buildTop() {
        VBox top = new VBox();
        top.getStyleClass().add("top-shell");
        top.getChildren().addAll(buildMenuBar(), buildHeader(), buildToolbars());
        return top;
    }

    private MenuBar buildMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("app-menu");

        Menu file = new Menu("File");
        MenuItem newItem = item("New Project", "Shortcut+N", this::newProject);
        MenuItem open = item("Open Project…", "Shortcut+O", this::openProject);
        MenuItem example = item("Open Example", null, this::openExample);
        MenuItem save = item("Save", "Shortcut+S", this::saveProject);
        MenuItem saveAs = item("Save As…", "Shortcut+Shift+S", this::saveAsProject);
        MenuItem export = item("Export…", "Shortcut+E", this::openExportDialog);
        MenuItem exit = item("Exit", null, () -> stage.close());
        file.getItems().addAll(newItem, open, example, new SeparatorMenuItem(),
                save, saveAs, new SeparatorMenuItem(), export, new SeparatorMenuItem(), exit);

        Menu edit = new Menu("Edit");
        undoItem = item("Undo", "Shortcut+Z", controller::undo);
        redoItem = item("Redo", "Shortcut+Y", controller::redo);
        MenuItem cut = item("Cut", "Shortcut+X", controller::cutSelection);
        MenuItem copy = item("Copy", "Shortcut+C", controller::copySelection);
        MenuItem paste = item("Paste", "Shortcut+V", controller::pasteClipboard);
        MenuItem duplicate = item("Duplicate", "Shortcut+D", controller::duplicateSelection);
        MenuItem delete = item("Delete Selection", null, controller::deleteSelection);
        MenuItem selectAll = item("Select All", "Shortcut+A", controller::selectAll);
        MenuItem selectConnected = item("Select Connected Structure",
                "Shortcut+Shift+A", this::selectConnected);
        Menu rotate = new Menu("Rotate Selection");
        rotate.getItems().addAll(
                item("Counter-clockwise 15°", "Shortcut+[", () -> controller.rotateSelection(-15)),
                item("Clockwise 15°", "Shortcut+]", () -> controller.rotateSelection(15)),
                new SeparatorMenuItem(),
                item("Counter-clockwise 90°", null, () -> controller.rotateSelection(-90)),
                item("Clockwise 90°", null, () -> controller.rotateSelection(90)));
        Menu bracket = new Menu("Enclose in Brackets");
        for (BracketType type : BracketType.values()) {
            bracket.getItems().add(item(type.toString(), null,
                    () -> controller.addBracket(type, "n", 400, 300)));
        }
        MenuItem front = item("Bring to Front", null, this::bringSelectionToFront);
        edit.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(),
                cut, copy, paste, duplicate, delete, new SeparatorMenuItem(),
                selectAll, selectConnected, new SeparatorMenuItem(), rotate, bracket, front);

        Menu view = new Menu("View");
        gridItem = new CheckMenuItem("Show Hexagonal Grid");
        gridItem.setSelected(controller.isShowGrid());
        gridItem.setOnAction(e -> {
            if (!updatingViewMenu) {
                controller.setShowGrid(gridItem.isSelected());
            }
        });
        carbonItem = new CheckMenuItem("Show Carbon Labels");
        carbonItem.setSelected(controller.isShowCarbonLabels());
        carbonItem.setOnAction(e -> {
            if (!updatingViewMenu) {
                controller.setShowCarbonLabels(carbonItem.isSelected());
            }
        });
        inspectorItem = new CheckMenuItem("Show Inspector");
        inspectorItem.setSelected(true);
        inspectorItem.setOnAction(e -> toggleInspector(inspectorItem.isSelected()));
        view.getItems().addAll(gridItem, carbonItem, new SeparatorMenuItem(),
                item("Zoom In", "Shortcut+Plus", () -> canvas.zoomBy(1.2)),
                item("Zoom Out", "Shortcut+Minus", () -> canvas.zoomBy(1 / 1.2)),
                item("Actual Size", "Shortcut+0", canvas::resetView),
                item("Fit Drawing to Window", "Shortcut+Shift+F", canvas::fitToContent),
                new SeparatorMenuItem(), inspectorItem);

        Menu insert = new Menu("Insert");
        insert.getItems().addAll(
                item("Periodic Table…", null, this::openPeriodicTableForActive),
                item("R Group", null, () -> activateGeneric(AtomKind.R_GROUP)),
                item("X Group", null, () -> activateGeneric(AtomKind.X_GROUP)),
                new SeparatorMenuItem(),
                item("Positive Charge", null, () -> setTool(Tool.CHARGE_POS)),
                item("Negative Charge", null, () -> setTool(Tool.CHARGE_NEG)),
                item("Lone Pair", null, () -> setTool(Tool.LONE_PAIR)),
                item("Single Electron", null, () -> setTool(Tool.RADICAL)),
                new SeparatorMenuItem(),
                item("Reaction Arrow", null, () -> setTool(Tool.ARROW_STRAIGHT)),
                item("Curved Arrow", null, () -> setTool(Tool.ARROW_CURVED)),
                item("Fishhook Arrow", null, () -> setTool(Tool.ARROW_FISHHOOK)),
                item("Text…", null, () -> setTool(Tool.TEXT)));

        Menu help = new Menu("Help");
        help.getItems().addAll(
                item("Keyboard Shortcuts", "F1", this::showShortcuts),
                item("About MolView Studio", null, this::showAbout));

        menuBar.getMenus().addAll(file, edit, view, insert, help);
        return menuBar;
    }

    private HBox buildHeader() {
        HBox header = new HBox(10);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(9, 18, 9, 16));

        Button newButton = headerCommand("New", "Create a new drawing (Ctrl+N)", this::newProject);
        Button openButton = headerCommand("Open", "Open a .molview project (Ctrl+O)", this::openProject);
        Button saveButton = headerCommand("Save", "Save the project (Ctrl+S)", this::saveProject);
        undoButton = headerIcon("↶", "Undo (Ctrl+Z)", controller::undo);
        redoButton = headerIcon("↷", "Redo (Ctrl+Y)", controller::redo);
        HBox files = new HBox(5, newButton, openButton, saveButton);
        files.setAlignment(Pos.CENTER_LEFT);
        HBox history = new HBox(4, undoButton, redoButton);
        history.setAlignment(Pos.CENTER_LEFT);

        StackPane brandMark = new StackPane();
        brandMark.getStyleClass().add("brand-mark");
        Label brandGlyph = new Label("M");
        brandGlyph.getStyleClass().add("brand-mark-text");
        brandMark.getChildren().add(brandGlyph);
        Label brandTitle = new Label("MolView Studio");
        brandTitle.getStyleClass().add("brand-title");
        Label brandSubtitle = new Label("Organic structure editor");
        brandSubtitle.getStyleClass().add("brand-subtitle");
        VBox brandText = new VBox(0, brandTitle, brandSubtitle);
        brandText.setAlignment(Pos.CENTER_LEFT);
        HBox brand = new HBox(9, brandMark, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(1);
        documentTitle.getStyleClass().add("document-title");
        documentSubtitle.getStyleClass().add("document-subtitle");
        dirtyDot.getStyleClass().add("dirty-dot");
        HBox titleRow = new HBox(7, documentTitle, dirtyDot);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleBox.getChildren().addAll(titleRow, documentSubtitle);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.setPadding(new Insets(0, 0, 0, 14));

        Button export = new Button("Export");
        export.getStyleClass().add("export-button");
        export.setTooltip(new Tooltip("Export image, PDF or chemical structure (Ctrl+E)"));
        export.setOnAction(e -> openExportDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(brand, headerDivider(), files, history,
                spacer, titleBox, headerDivider(), export);
        return header;
    }

    private VBox buildToolbars() {
        VBox toolbars = new VBox();
        toolbars.getStyleClass().add("toolbar-shell");

        HBox row1 = new HBox(5);
        row1.getStyleClass().add("chemistry-toolbar");
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6, 16, 5, 16));
        row1.getChildren().add(toolbarGroup("ELEMENTS",
                atomButton("C"), atomButton("N"), atomButton("O"), atomButton("H"),
                atomButton("X"), atomButton("R"),
                periodicButton()));
        row1.getChildren().add(toolSeparator());
        row1.getChildren().add(toolbarGroup("BONDS",
                bondButton(BondType.SINGLE), bondButton(BondType.DOUBLE), bondButton(BondType.TRIPLE),
                bondButton(BondType.WEDGE), bondButton(BondType.DASHED), bondButton(BondType.WAVY),
                bondButton(BondType.HYDROGEN), bondButton(BondType.AROMATIC),
                bondButton(BondType.RESONANCE)));
        Region row1Spacer = new Region();
        HBox.setHgrow(row1Spacer, Priority.ALWAYS);
        Label hint = new Label("Draw · select · annotate");
        hint.getStyleClass().add("toolbar-hint");
        row1.getChildren().addAll(row1Spacer, hint);

        HBox row2 = new HBox(5);
        row2.getStyleClass().addAll("chemistry-toolbar", "secondary-toolbar");
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(4, 16, 7, 16));
        row2.getChildren().addAll(
                toolbarGroup("CHARGES",
                        chargeButton(ChargeKind.POSITIVE, Tool.CHARGE_POS),
                        chargeButton(ChargeKind.NEGATIVE, Tool.CHARGE_NEG),
                        chargeButton(ChargeKind.PARTIAL_POSITIVE, Tool.PARTIAL_POS),
                        chargeButton(ChargeKind.PARTIAL_NEGATIVE, Tool.PARTIAL_NEG)),
                toolSeparator(),
                toolbarGroup("ELECTRONS",
                        electronButton(com.molview.app.model.ElectronKind.LONE_PAIR, Tool.LONE_PAIR),
                        electronButton(com.molview.app.model.ElectronKind.RADICAL, Tool.RADICAL)),
                toolSeparator(),
                toolbarGroup("MECHANISM",
                        arrowButton(com.molview.app.model.ArrowType.STRAIGHT, Tool.ARROW_STRAIGHT),
                        arrowButton(com.molview.app.model.ArrowType.CURVED, Tool.ARROW_CURVED),
                        arrowButton(com.molview.app.model.ArrowType.FISHHOOK, Tool.ARROW_FISHHOOK)),
                toolSeparator(),
                toolbarGroup("GROUPING",
                        bracketButton(BracketType.SQUARE, Tool.BRACKET_SQUARE),
                        bracketButton(BracketType.ROUND, Tool.BRACKET_ROUND),
                        bracketButton(BracketType.CURLY, Tool.BRACKET_CURLY),
                        textButton()));
        Region row2Spacer = new Region();
        HBox.setHgrow(row2Spacer, Priority.ALWAYS);
        Label shortcutHint = new Label("Esc: select   ·   Del: erase   ·   [  ]: rotate   ·   Space: pan");
        shortcutHint.getStyleClass().add("toolbar-hint");
        row2.getChildren().addAll(row2Spacer, shortcutHint);

        toolbars.getChildren().addAll(row1, row2);
        return toolbars;
    }

    private Node buildToolRail() {
        VBox rail = new VBox(5);
        rail.getStyleClass().add("tool-rail");
        rail.setPadding(new Insets(10, 8, 10, 8));
        rail.setPrefWidth(60);
        rail.setMinWidth(60);
        rail.setMaxWidth(60);
        rail.setAlignment(Pos.TOP_CENTER);

        Button select = railButton("↖", "Select / move (Esc)", Tool.SELECT);
        Button pan = railButton("✋", "Pan canvas (Space or middle mouse)", Tool.PAN);
        Button erase = railButton(Icons.eraserIcon(), "Erase objects (E)", Tool.ERASE);
        rail.getChildren().addAll(select, pan, erase);
        rail.getChildren().add(new Separator());
        Button zoomIn = railButton("＋", "Zoom in", null);
        zoomIn.setOnAction(e -> canvas.zoomBy(1.2));
        Button zoomOut = railButton("−", "Zoom out", null);
        zoomOut.setOnAction(e -> canvas.zoomBy(1 / 1.2));
        Button fit = railButton("⤢", "Fit drawing to window", null);
        fit.setOnAction(e -> canvas.fitToContent());
        rail.getChildren().addAll(zoomIn, zoomOut, fit);
        return rail;
    }

    private StackPane buildCanvasArea() {
        StackPane stack = new StackPane();
        stack.getStyleClass().add("canvas-shell");
        stack.getChildren().add(canvas);

        HBox zoomPill = new HBox(4);
        zoomPill.getStyleClass().add("zoom-pill");
        Button minus = zoomPillButton("−", "Zoom out");
        minus.setOnAction(e -> canvas.zoomBy(1 / 1.2));
        zoomPillLabel.getStyleClass().add("zoom-value");
        zoomPillLabel.setMinWidth(48);
        zoomPillLabel.setAlignment(Pos.CENTER);
        Button plus = zoomPillButton("＋", "Zoom in");
        plus.setOnAction(e -> canvas.zoomBy(1.2));
        Button fit = zoomPillButton("Fit", "Fit drawing to window");
        fit.setOnAction(e -> canvas.fitToContent());
        zoomPill.getChildren().addAll(minus, zoomPillLabel, plus, fit);
        zoomPill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(zoomPill, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(zoomPill, new Insets(0, 18, 16, 0));
        stack.getChildren().add(zoomPill);

        toastLabel.getStyleClass().add("toast");
        toastLayer.getStyleClass().add("toast-layer");
        toastLayer.setMouseTransparent(true);
        toastLayer.setVisible(false);
        toastLayer.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(toastLayer, Pos.TOP_CENTER);
        StackPane.setMargin(toastLayer, new Insets(16, 16, 0, 16));
        stack.getChildren().add(toastLayer);
        return stack;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(4, 14, 4, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        Label toolCaption = new Label("TOOL");
        toolCaption.getStyleClass().add("status-caption");
        toolLabel.getStyleClass().add("status-tool");
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        statusLabel.getStyleClass().add("status-message");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        Label scaleCaption = new Label("ZOOM");
        scaleCaption.getStyleClass().add("status-caption");
        zoomLabel.getStyleClass().add("status-zoom");
        bar.getChildren().addAll(toolCaption, toolLabel, separator, statusLabel,
                scaleCaption, zoomLabel);
        return bar;
    }

    private HBox toolbarGroup(String label, Node... nodes) {
        HBox group = new HBox(4);
        group.setAlignment(Pos.CENTER_LEFT);
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("toolbar-group-label");
        group.getChildren().add(labelNode);
        group.getChildren().addAll(nodes);
        return group;
    }

    private Button atomButton(String symbol) {
        Button button = new Button(symbol);
        button.getStyleClass().add("element-quick");
        button.setAccessibleText("Place " + symbol
                + "; drag empty space for a chain, or drag an atom to set a bond angle");
        button.setTooltip(new Tooltip("Place " + symbol
                + "; drag empty space for a chain, or drag an atom to set a bond angle"));
        button.setOnAction(e -> {
            controller.setActiveElement(symbol);
            canvas.requestFocus();
        });
        atomButtons.put(button, symbol);
        return button;
    }

    private Button periodicButton() {
        Button button = new Button();
        button.setGraphic(Icons.periodicTableIcon());
        button.getStyleClass().add("tool-button");
        button.setAccessibleText("Periodic table");
        button.setTooltip(new Tooltip("Periodic table — choose any element"));
        button.setOnAction(e -> openPeriodicTableForActive());
        button.setFocusTraversable(false);
        return button;
    }

    private Button bondButton(BondType type) {
        Button button = new Button();
        button.setGraphic(Icons.bondIcon(type));
        button.getStyleClass().add("tool-button");
        button.setAccessibleText(type.displayName() + " bond");
        button.setTooltip(new Tooltip(type.displayName() + " bond"));
        button.setOnAction(e -> {
            controller.setActiveBondType(type);
            boolean selectedBond = controller.getDocument().selectedObjects().stream()
                    .anyMatch(object -> object instanceof com.molview.app.model.Bond);
            if (selectedBond) {
                controller.setSelectedBondsType(type);
            }
            canvas.requestFocus();
        });
        bondButtons.put(button, type);
        return button;
    }

    private Button chargeButton(ChargeKind kind, Tool tool) {
        Button button = new Button();
        button.setGraphic(Icons.chargeIcon(kind));
        button.getStyleClass().add("tool-button");
        button.setAccessibleText(tool.displayName());
        button.setTooltip(new Tooltip(tool.displayName() + "  (" + kind.label() + ")"));
        button.setOnAction(e -> {
            controller.setTool(tool);
            canvas.requestFocus();
        });
        toolButtons.put(button, tool);
        return button;
    }

    private Button electronButton(com.molview.app.model.ElectronKind kind, Tool tool) {
        Button button = new Button();
        button.setGraphic(Icons.electronIcon(kind));
        button.getStyleClass().add("tool-button");
        button.setAccessibleText(tool.displayName());
        button.setTooltip(new Tooltip(tool.displayName()));
        button.setOnAction(e -> {
            controller.setTool(tool);
            canvas.requestFocus();
        });
        toolButtons.put(button, tool);
        return button;
    }

    private Button arrowButton(com.molview.app.model.ArrowType type, Tool tool) {
        Button button = new Button();
        button.setGraphic(Icons.arrowIcon(type));
        button.getStyleClass().add("tool-button");
        button.setAccessibleText(type.displayName());
        button.setTooltip(new Tooltip(type.displayName()));
        button.setOnAction(e -> {
            controller.setTool(tool);
            canvas.requestFocus();
        });
        toolButtons.put(button, tool);
        return button;
    }

    private Button bracketButton(BracketType type, Tool tool) {
        Button button = new Button();
        button.setGraphic(Icons.bracketIcon(type));
        button.getStyleClass().add("tool-button");
        button.setAccessibleText(tool.displayName());
        button.setTooltip(new Tooltip(tool.displayName() + " — click to enclose the selection, or drag a frame"));
        button.setOnAction(e -> {
            controller.setTool(tool);
            canvas.requestFocus();
        });
        toolButtons.put(button, tool);
        return button;
    }

    private Button textButton() {
        Button button = new Button("T");
        button.getStyleClass().add("tool-button");
        button.setStyle("-fx-font-weight: 700; -fx-font-size: 15px;");
        button.setTooltip(new Tooltip("Text annotation"));
        button.setOnAction(e -> {
            controller.setTool(Tool.TEXT);
            canvas.requestFocus();
        });
        toolButtons.put(button, Tool.TEXT);
        return button;
    }

    private Button railButton(String glyph, String tooltip, Tool tool) {
        return railButton(Icons.toolbarGlyph(glyph), tooltip, tool);
    }

    private Button railButton(Node graphic, String tooltip, Tool tool) {
        Button button = new Button();
        button.setGraphic(graphic);
        button.getStyleClass().add("rail-button");
        button.setAccessibleText(tooltip);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        if (tool != null) {
            button.setOnAction(e -> {
                controller.setTool(tool);
                canvas.requestFocus();
            });
            toolButtons.put(button, tool);
        }
        return button;
    }

    private Button headerCommand(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("header-command");
        button.setAccessibleText(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button headerIcon(String glyph, String tooltip, Runnable action) {
        Button button = new Button(glyph);
        button.getStyleClass().add("header-icon");
        button.setAccessibleText(tooltip);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button zoomPillButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("zoom-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private Node headerDivider() {
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        separator.getStyleClass().add("header-divider");
        return separator;
    }

    private Separator toolSeparator() {
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        separator.getStyleClass().add("tool-separator");
        return separator;
    }

    private MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(e -> action.run());
        return item;
    }

    private void installKeyboard(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof TextInputControl
                    || event.getTarget() instanceof javafx.scene.control.ComboBoxBase<?>
                    || event.getTarget() instanceof javafx.scene.control.Spinner<?>) {
                return;
            }
            if (event.isControlDown() || event.isMetaDown() || event.isAltDown()) {
                return;
            }
            boolean canvasKeys = canvas.isFocused();
            switch (event.getCode()) {
                case DELETE, BACK_SPACE -> {
                    controller.deleteSelection();
                    event.consume();
                }
                case ESCAPE -> {
                    canvas.cancelInteraction();
                    controller.setTool(Tool.SELECT);
                    event.consume();
                }
                case LEFT -> {
                    if (canvasKeys) {
                        nudge(event, -1, 0);
                    }
                }
                case RIGHT -> {
                    if (canvasKeys) {
                        nudge(event, 1, 0);
                    }
                }
                case UP -> {
                    if (canvasKeys) {
                        nudge(event, 0, -1);
                    }
                }
                case DOWN -> {
                    if (canvasKeys) {
                        nudge(event, 0, 1);
                    }
                }
                case OPEN_BRACKET -> {
                    if (canvasKeys) {
                        controller.rotateSelection(-15);
                        event.consume();
                    }
                }
                case CLOSE_BRACKET -> {
                    if (canvasKeys) {
                        controller.rotateSelection(15);
                        event.consume();
                    }
                }
                case PLUS, EQUALS -> {
                    if (canvasKeys) {
                        canvas.zoomBy(1.2);
                        event.consume();
                    }
                }
                case MINUS -> {
                    if (canvasKeys) {
                        canvas.zoomBy(1 / 1.2);
                        event.consume();
                    }
                }
                case DIGIT0 -> {
                    if (canvasKeys) {
                        canvas.resetView();
                        event.consume();
                    }
                }
                case SPACE -> {
                    if (canvasKeys || canvas.isHover()) {
                        canvas.setSpaceDown(true);
                        event.consume();
                    }
                }
                default -> {
                }
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                canvas.setSpaceDown(false);
            }
        });
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Shortcut+Shift+Z"), controller::redo);
    }

    private void nudge(KeyEvent event, double dx, double dy) {
        if (!controller.hasSelection()) {
            return;
        }
        double amount = event.isShiftDown() ? 10 : 1;
        controller.nudgeSelection(dx * amount, dy * amount);
        event.consume();
    }

    private void setTool(Tool tool) {
        controller.setTool(tool);
        canvas.requestFocus();
    }

    private void activateGeneric(AtomKind kind) {
        controller.setActiveGeneric(kind);
        canvas.requestFocus();
    }

    private void selectConnected() {
        if (controller.selectedObjects().isEmpty()) {
            showToast("Select an atom or bond first.");
            return;
        }
        controller.selectStructure(controller.selectedObjects().get(0));
    }

    private void bringSelectionToFront() {
        List<com.molview.app.model.ChemObject> selected = controller.selectedObjects();
        for (com.molview.app.model.ChemObject object : new ArrayList<>(selected)) {
            controller.moveToFront(object);
        }
    }

    private void updateChrome() {
        updateTitle();
        undoButton.setDisable(!controller.canUndo());
        redoButton.setDisable(!controller.canRedo());
        if (undoItem != null) {
            undoItem.setDisable(!controller.canUndo());
            redoItem.setDisable(!controller.canRedo());
        }
        for (Map.Entry<Button, Tool> entry : toolButtons.entrySet()) {
            setActive(entry.getKey(), controller.getTool() == entry.getValue());
        }
        for (Map.Entry<Button, String> entry : atomButtons.entrySet()) {
            setActive(entry.getKey(), controller.getTool() == Tool.ATOM
                    && entry.getValue().equals(controller.getActiveElement()));
        }
        for (Map.Entry<Button, BondType> entry : bondButtons.entrySet()) {
            setActive(entry.getKey(), controller.getTool() == Tool.BOND
                    && entry.getValue() == controller.getActiveBondType());
        }
        if (controller.getTool() == Tool.ATOM) {
            toolLabel.setText("Atom · " + activeElementLabel());
        } else {
            toolLabel.setText(controller.getTool().displayName());
        }
        updatingViewMenu = true;
        if (gridItem != null) {
            gridItem.setSelected(controller.isShowGrid());
            carbonItem.setSelected(controller.isShowCarbonLabels());
        }
        updatingViewMenu = false;
        updateZoomLabel();
    }

    private void setActive(Button button, boolean active) {
        if (active && !button.getStyleClass().contains("active")) {
            button.getStyleClass().add("active");
        } else if (!active) {
            button.getStyleClass().remove("active");
        }
    }

    private void updateTitle() {
        String title = controller.getDocument().getTitle();
        documentTitle.setText(title);
        Path file = controller.getCurrentFile();
        documentSubtitle.setText(file == null ? "Unsaved project" : file.getFileName().toString());
        documentSubtitle.setTooltip(new Tooltip(file == null ? "Not saved yet" : file.toAbsolutePath().toString()));
        dirtyDot.setVisible(controller.isDirty());
        stage.setTitle(title + (controller.isDirty() ? " •" : ""));
    }

    private void updateZoomLabel() {
        String text = Math.round(canvas.getZoom() * 100) + "%";
        zoomLabel.setText(text);
        zoomPillLabel.setText(text);
    }

    private String activeElementLabel() {
        return switch (controller.getActiveAtomKind()) {
            case R_GROUP -> controller.getRLabel();
            case X_GROUP -> controller.getXLabel();
            default -> controller.getActiveElement();
        };
    }

    private void toggleInspector(boolean show) {
        inspectorVisible = show;
        root.setRight(show ? properties : null);
        if (inspectorItem != null) {
            inspectorItem.setSelected(show);
        }
    }

    private void openPeriodicTableForActive() {
        showPeriodic(controller.getActiveElement(), symbol -> {
            controller.setActiveElement(symbol);
            showToast(symbol + " selected — click the canvas to place it.");
        });
    }

    private void openPeriodicTableForAtom(Atom atom) {
        showPeriodic(atom.getElementSymbol(), symbol -> controller.setAtomElement(atom, symbol));
    }

    private void showPeriodic(String current, java.util.function.Consumer<String> consumer) {
        PeriodicTableDialog dialog = new PeriodicTableDialog(stage, current);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(consumer);
        canvas.requestFocus();
    }

    private void openExportDialog() {
        if (controller.getDocument().isEmpty()) {
            showToast("Draw or open a structure before exporting.");
            return;
        }
        ExportDialog dialog = new ExportDialog(stage, controller);
        Optional<Path> result = dialog.showAndWait();
        result.ifPresent(path -> showToast("Exported " + path.getFileName() + " to " + path.toAbsolutePath().getParent()));
        canvas.requestFocus();
    }

    private boolean maybeSave() {
        if (!controller.isDirty()) {
            return true;
        }
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Save the changes to “" + controller.getDocument().getTitle() + "”?",
                save, discard, cancel);
        alert.initOwner(stage);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("This drawing has unsaved changes.");
        UiUtil.styleDialog(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            return false;
        }
        if (result.get() == save) {
            return saveProject();
        }
        return true;
    }

    private void newProject() {
        if (!maybeSave()) {
            return;
        }
        controller.newDocument();
        controller.setActiveElement("C");
        canvas.resetView();
        showToast("New project ready with carbon loaded.");
        canvas.requestFocus();
    }

    private void openExample() {
        if (!maybeSave()) {
            return;
        }
        controller.replaceDocument(SampleProjects.sn2Mechanism(), null, true);
        Platform.runLater(canvas::fitToContent);
        showToast("Opened example: Sₙ2 substitution mechanism.");
        canvas.requestFocus();
    }

    private void openProject() {
        if (!maybeSave()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open MolView Studio Project");
        prepareDirectory(Path.of("projects"));
        chooser.setInitialDirectory(Path.of("projects").toAbsolutePath().toFile());
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MolView Studio project", "*.molview"),
                new FileChooser.ExtensionFilter("JSON project", "*.json"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            LoadedProject project = ProjectIO.load(file.toPath());
            controller.replaceDocument(project.document(), file.toPath(), true);
            ViewState view = project.viewState();
            if (view.offsetX() == 0 && view.offsetY() == 0 && !project.document().isEmpty()) {
                Platform.runLater(canvas::fitToContent);
            } else {
                canvas.applyViewState(view);
            }
            showToast("Opened " + file.getName());
        } catch (Exception ex) {
            UiUtil.showError(stage, "Could not open project",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
        canvas.requestFocus();
    }

    private boolean saveProject() {
        if (controller.getCurrentFile() == null) {
            return saveAsProject();
        }
        return writeProject(controller.getCurrentFile());
    }

    private boolean saveAsProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save MolView Studio Project");
        prepareDirectory(Path.of("projects"));
        chooser.setInitialDirectory(Path.of("projects").toAbsolutePath().toFile());
        String safe = controller.getDocument().getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
        chooser.setInitialFileName((safe.isBlank() ? "Untitled" : safe) + ".molview");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MolView Studio project", "*.molview"));
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return false;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".molview")) {
            path = path.resolveSibling(path.getFileName() + ".molview");
        }
        return writeProject(path);
    }

    private boolean writeProject(Path path) {
        try {
            ProjectIO.save(controller.getDocument(), canvas.getViewState(), path);
            controller.markSaved(path);
            showToast("Saved " + path.getFileName());
            return true;
        } catch (Exception ex) {
            UiUtil.showError(stage, "Could not save project",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return false;
        }
    }

    private void prepareDirectory(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath());
        } catch (IOException ignored) {
        }
    }

    private void showToast(String message) {
        toastLabel.setText(message);
        toastLayer.setVisible(true);
        toastLayer.setOpacity(1);
        toastLayer.setTranslateY(0);
        if (toastAnimation != null) {
            toastAnimation.stop();
        }
        PauseTransition pause = new PauseTransition(Duration.millis(2600));
        FadeTransition fade = new FadeTransition(Duration.millis(380), toastLayer);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> toastLayer.setVisible(false));
        toastAnimation = new SequentialTransition(pause, fade);
        toastAnimation.play();
    }

    private void showShortcuts() {
        String text = """
                Editing
                  Ctrl+Z / Ctrl+Y        Undo / redo
                  Ctrl+C / Ctrl+X / Ctrl+V  Copy / cut / paste
                  Ctrl+D                 Duplicate selection
                  Delete                 Delete selection
                  Ctrl+A                 Select all
                  Ctrl+Shift+A           Select connected structure
                  [  /  ]                Rotate selection 15°
                  Arrow keys             Nudge selection (Shift = 10 px)

                Drawing
                  Click with an element to place an atom
                  Drag with a bond tool, or click two atoms to connect them
                  Click a bond with a bond tool to change its type
                  Esc                    Return to Select tool / cancel

                View
                  Mouse wheel            Zoom around pointer
                  Space + drag           Pan
                  Middle mouse drag      Pan
                  Ctrl+0                 Actual size
                  Ctrl+Shift+F           Fit drawing to window

                Files
                  Ctrl+N / Ctrl+O / Ctrl+S   New / open / save
                  Ctrl+E                 Export
                """;
        UiUtil.showInfo(stage, "Keyboard Shortcuts", text);
    }

    private void showAbout() {
        UiUtil.showInfo(stage, "About MolView Studio",
                "MolView Studio 1.0\n"
                        + "A JavaFX organic chemistry structure editor.\n\n"
                        + "• Element palette and full periodic table\n"
                        + "• Organic bonds, stereochemistry and resonance\n"
                        + "• Charges, lone pairs and radical electrons\n"
                        + "• Reaction and electron-pushing arrows\n"
                        + "• Grouping brackets with labels\n"
                        + "• PNG, JPEG, SVG, PDF, MOL, SDF and SMILES export\n"
                        + "• Native fully editable .molview projects\n\n"
                        + "Built with Java 21 and JavaFX 21.");
    }

    private void loadStageIcon() {
        try {
            var stream = MainWindow.class.getResourceAsStream("/assets/app-icon.png");
            if (stream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
        }
    }
}
