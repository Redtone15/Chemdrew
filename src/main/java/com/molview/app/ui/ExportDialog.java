package com.molview.app.ui;

import com.molview.app.editor.EditorController;
import com.molview.app.io.ExportFormat;
import com.molview.app.io.ExportOptions;
import com.molview.app.io.ExportService;
import com.molview.app.io.PagePreset;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Export workspace with live preview, format settings and page controls. */
public final class ExportDialog extends Dialog<Path> {
    private final EditorController controller;
    private final Window owner;
    private final ExportOptions options = new ExportOptions();
    private final ComboBox<ExportFormat> formatCombo = new ComboBox<>();
    private final TextField pathField = new TextField();
    private final CheckBox selectionOnly = new CheckBox("Selection only");
    private final Spinner<Integer> widthSpinner = new Spinner<>();
    private final Spinner<Integer> heightSpinner = new Spinner<>();
    private final CheckBox transparent = new CheckBox("Transparent background");
    private final ColorPicker backgroundPicker = new ColorPicker(Color.WHITE);
    private final ComboBox<PagePreset> pagePreset = new ComboBox<>();
    private final ComboBox<String> orientation = new ComboBox<>();
    private final TextField customWidth = new TextField();
    private final TextField customHeight = new TextField();
    private final TextField marginField = new TextField();
    private final ComboBox<Integer> dpiCombo = new ComboBox<>();
    private final ImageView previewImage = new ImageView();
    private final TextArea textPreview = new TextArea();
    private final Label previewMessage = new Label();
    private final VBox imageSettings = new VBox(10);
    private final VBox pdfSettings = new VBox(10);
    private final VBox chemistrySettings = new VBox(10);
    private final Label formatNote = new Label();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(280));
    private final ButtonType exportType = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    private Path exportedPath;
    private boolean initialising = true;

    public ExportDialog(Window owner, EditorController controller) {
        this.owner = owner;
        this.controller = controller;
        initOwner(owner);
        setTitle("Export — MolView Studio");
        setHeaderText(null);
        setResizable(true);
        UiUtil.styleDialog(this);
        getDialogPane().getButtonTypes().addAll(exportType, cancelType);
        getDialogPane().setPrefSize(1180, 740);
        getDialogPane().setMinSize(1040, 640);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("export-root");

        VBox left = new VBox(14);
        left.getStyleClass().add("export-settings");
        left.setPadding(new Insets(20, 20, 20, 20));
        left.setPrefWidth(440);
        left.setMaxWidth(460);

        Label heading = new Label("Export drawing");
        heading.getStyleClass().add("dialog-title");
        Label sub = new Label("Choose a format, configure the output and preview the result before saving.");
        sub.getStyleClass().add("dialog-subtitle");
        sub.setWrapText(true);

        formatCombo.getItems().setAll(ExportFormat.values());
        formatCombo.setValue(ExportFormat.PNG);
        formatCombo.getStyleClass().add("inspector-combo");
        formatCombo.setMaxWidth(Double.MAX_VALUE);
        formatCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ExportFormat format) {
                return format == null ? "" : format.displayName() + "   " + format.extension();
            }

            @Override
            public ExportFormat fromString(String string) {
                return formatCombo.getValue();
            }
        });

        pathField.getStyleClass().add("inspector-field");
        pathField.setPrefWidth(280);
        Button browse = new Button("Browse…");
        browse.getStyleClass().add("small-button");
        browse.setOnAction(e -> browse());
        HBox pathRow = new HBox(8, pathField, browse);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        selectionOnly.setSelected(false);
        selectionOnly.setDisable(!controller.hasSelection());
        selectionOnly.setOnAction(e -> schedulePreview());
        selectionOnly.getStyleClass().add("inspector-check");
        selectionOnly.setTooltip(new javafx.scene.control.Tooltip(controller.hasSelection()
                ? "Export only the selected objects"
                : "Select objects on the canvas to enable this option"));

        imageSettings.getStyleClass().add("export-group");
        Label imageTitle = new Label("Image settings");
        imageTitle.getStyleClass().add("section-heading");
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                64, 12000, com.molview.app.config.AppConfig.getInt("export.image.width", 1600), 100));
        heightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                64, 12000, com.molview.app.config.AppConfig.getInt("export.image.height", 1200), 100));
        widthSpinner.setEditable(true);
        heightSpinner.setEditable(true);
        widthSpinner.getStyleClass().add("inspector-spinner");
        heightSpinner.getStyleClass().add("inspector-spinner");
        HBox sizeRow = new HBox(10, labeled("Width (px)", widthSpinner),
                labeled("Height (px)", heightSpinner));
        HBox.setHgrow(sizeRow.getChildren().get(0), Priority.ALWAYS);
        transparent.getStyleClass().add("inspector-check");
        backgroundPicker.getStyleClass().add("inspector-color");
        transparent.setOnAction(e -> {
            if (!initialising) {
                backgroundPicker.setDisable(transparent.isSelected());
                schedulePreview();
            }
        });
        backgroundPicker.setOnAction(e -> schedulePreview());
        HBox backgroundRow = new HBox(12, transparent, backgroundPicker);
        backgroundRow.setAlignment(Pos.CENTER_LEFT);
        imageSettings.getChildren().addAll(imageTitle, sizeRow, backgroundRow);

        pdfSettings.getStyleClass().add("export-group");
        Label pdfTitle = new Label("Print page");
        pdfTitle.getStyleClass().add("section-heading");
        pagePreset.getItems().setAll(PagePreset.values());
        pagePreset.setValue(PagePreset.A4);
        pagePreset.getStyleClass().add("inspector-combo");
        pagePreset.setMaxWidth(Double.MAX_VALUE);
        orientation.getItems().setAll("Portrait", "Landscape");
        orientation.setValue("Portrait");
        orientation.getStyleClass().add("inspector-combo");
        orientation.setMaxWidth(Double.MAX_VALUE);
        HBox pageRow = new HBox(10, labeled("Page size", pagePreset),
                labeled("Orientation", orientation));
        HBox.setHgrow(pageRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(pageRow.getChildren().get(1), Priority.ALWAYS);
        customWidth.getStyleClass().add("inspector-field");
        customHeight.getStyleClass().add("inspector-field");
        customWidth.setPromptText("8.27");
        customHeight.setPromptText("11.69");
        customWidth.setText("8.27");
        customHeight.setText("11.69");
        HBox customRow = new HBox(10, labeled("Width (in)", customWidth),
                labeled("Height (in)", customHeight));
        HBox.setHgrow(customRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(customRow.getChildren().get(1), Priority.ALWAYS);
        marginField.getStyleClass().add("inspector-field");
        marginField.setText("0.5");
        marginField.setPromptText("0.5");
        dpiCombo.getItems().setAll(150, 300, 600);
        dpiCombo.setValue(com.molview.app.config.AppConfig.getInt("export.pdf.resolution", 300));
        dpiCombo.getStyleClass().add("inspector-combo");
        dpiCombo.setMaxWidth(Double.MAX_VALUE);
        HBox printRow = new HBox(10, labeled("Margin (in)", marginField),
                labeled("Resolution", dpiCombo));
        HBox.setHgrow(printRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(printRow.getChildren().get(1), Priority.ALWAYS);
        pagePreset.setOnAction(e -> {
            boolean custom = pagePreset.getValue() == PagePreset.CUSTOM;
            customWidth.setDisable(!custom);
            customHeight.setDisable(!custom);
            schedulePreview();
        });
        customWidth.textProperty().addListener((obs, old, value) -> schedulePreview());
        customHeight.textProperty().addListener((obs, old, value) -> schedulePreview());
        marginField.textProperty().addListener((obs, old, value) -> schedulePreview());
        orientation.setOnAction(e -> schedulePreview());
        dpiCombo.setOnAction(e -> schedulePreview());
        pdfSettings.getChildren().addAll(pdfTitle, pageRow, customRow, printRow,
                note("PDF pages are rendered at the chosen resolution, preserving the layout, symbols and annotations for printing."));
        customWidth.setDisable(true);
        customHeight.setDisable(true);

        chemistrySettings.getStyleClass().add("export-group");
        Label chemistryTitle = new Label("Chemical structure data");
        chemistryTitle.getStyleClass().add("section-heading");
        Label chemistryNote = note("Atoms, bonds and formal charges are written in standard formats. "
                + "MOL/SDF also preserve wedge/hash stereochemistry and radicals. "
                + "SMILES uses the wildcard * for generic R/X groups.");
        chemistryNote.setWrapText(true);
        chemistrySettings.getChildren().addAll(chemistryTitle, chemistryNote);

        formatNote.getStyleClass().add("section-note");
        formatNote.setWrapText(true);

        VBox pathGroup = new VBox(6);
        Label fileLabel = new Label("File");
        fileLabel.getStyleClass().add("property-label");
        pathGroup.getChildren().addAll(fileLabel, pathRow, selectionOnly);

        left.getChildren().addAll(heading, sub, labeled("Format", formatCombo), pathGroup,
                imageSettings, pdfSettings, chemistrySettings, formatNote);
        ScrollPane leftScroll = new ScrollPane(left);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.getStyleClass().add("export-scroll");
        leftScroll.setPrefWidth(458);
        root.setLeft(leftScroll);

        StackPane previewSurface = new StackPane();
        previewSurface.getStyleClass().add("preview-frame");
        previewSurface.setPadding(new Insets(16));
        previewImage.setPreserveRatio(true);
        previewImage.setSmooth(true);
        previewImage.setFitWidth(560);
        previewImage.setFitHeight(430);
        textPreview.setEditable(false);
        textPreview.setWrapText(false);
        textPreview.getStyleClass().add("export-text-preview");
        textPreview.setMaxSize(590, 460);
        previewSurface.getChildren().addAll(previewImage, textPreview);
        StackPane.setAlignment(previewImage, Pos.CENTER);
        StackPane.setAlignment(textPreview, Pos.CENTER);
        VBox previewPane = new VBox(10);
        previewPane.getStyleClass().add("preview-pane");
        previewPane.setPadding(new Insets(20));
        Label previewTitle = new Label("Preview");
        previewTitle.getStyleClass().add("dialog-title");
        Label previewSub = new Label("The preview reflects the current output settings.");
        previewSub.getStyleClass().add("dialog-subtitle");
        previewMessage.getStyleClass().add("section-note");
        previewMessage.setWrapText(true);
        Button refresh = new Button("Refresh preview");
        refresh.getStyleClass().add("small-button");
        refresh.setOnAction(e -> refreshPreview());
        previewPane.getChildren().addAll(previewTitle, previewSub, previewSurface, previewMessage, refresh);
        VBox.setVgrow(previewSurface, Priority.ALWAYS);
        root.setCenter(previewPane);
        getDialogPane().setContent(root);

        transparent.setDisable(true);
        updateFormatVisibility();
        updateDefaultPath(ExportFormat.PNG);
        initialising = false;

        formatCombo.setOnAction(e -> {
            updateFormatVisibility();
            updateDefaultPath(formatCombo.getValue());
            schedulePreview();
        });
        widthSpinner.valueProperty().addListener((obs, old, value) -> schedulePreview());
        heightSpinner.valueProperty().addListener((obs, old, value) -> schedulePreview());
        debounce.setOnFinished(e -> refreshPreview());

        Button exportButton = (Button) getDialogPane().lookupButton(exportType);
        exportButton.getStyleClass().add("primary-button");
        exportButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!performExport()) {
                event.consume();
            }
        });
        setResultConverter(button -> button == exportType ? exportedPath : null);
        setOnShown(e -> Platform.runLater(this::refreshPreview));
    }

    private void updateFormatVisibility() {
        ExportFormat format = formatCombo.getValue();
        if (format == null) {
            return;
        }
        boolean image = format == ExportFormat.PNG || format == ExportFormat.JPEG
                || format == ExportFormat.SVG;
        boolean pdf = format == ExportFormat.PDF;
        boolean chemistry = format.category() == ExportFormat.Category.CHEMISTRY;
        imageSettings.setVisible(image);
        imageSettings.setManaged(image);
        pdfSettings.setVisible(pdf);
        pdfSettings.setManaged(pdf);
        chemistrySettings.setVisible(chemistry);
        chemistrySettings.setManaged(chemistry);
        transparent.setDisable(!(format == ExportFormat.PNG || format == ExportFormat.SVG));
        if (format == ExportFormat.JPEG || format == ExportFormat.PDF) {
            transparent.setSelected(false);
        }
        backgroundPicker.setDisable(transparent.isSelected() || pdf);
        formatNote.setText(switch (format) {
            case PNG -> "Lossless raster image with optional transparency — ideal for web and documents.";
            case JPEG -> "Compressed raster image with a solid background — ideal for quick sharing and slides.";
            case SVG -> "Scalable vector graphics with sharp lines, text and chemical symbols at any resolution.";
            case PDF -> "A print-ready page with custom size, orientation, margins and resolution.";
            case MOL -> "MDL V2000 molfile containing atoms, bonds, charges, wedge/hash stereochemistry and radical marks.";
            case SDF -> "Multi-record SDF; each connected structure is written as its own record with metadata.";
            case SMILES -> "Simplified molecular line notation; ring closures, branches, charges and aromatic rings are included.";
        });
        textPreview.setVisible(chemistry);
        textPreview.setManaged(chemistry);
        previewImage.setVisible(!chemistry);
        previewImage.setManaged(!chemistry);
    }

    private void updateDefaultPath(ExportFormat format) {
        if (format == null) {
            return;
        }
        Path current = pathField.getText().isBlank() ? null : Path.of(pathField.getText());
        if (current == null) {
            current = Path.of("exports", safeName(controller.getDocument().getTitle()) + format.extension());
        } else {
            current = withExtension(current, format);
        }
        pathField.setText(current.toString());
    }

    private void schedulePreview() {
        if (!initialising) {
            debounce.playFromStart();
        }
    }

    private void refreshPreview() {
        ExportFormat format = formatCombo.getValue();
        if (format == null) {
            return;
        }
        try {
            applyControlsToOptions();
            if (format.category() == ExportFormat.Category.CHEMISTRY) {
                String text = ExportService.textPreview(controller.getDocument(), options);
                textPreview.setText(text);
                previewMessage.setText("Structure data preview");
                previewMessage.getStyleClass().removeAll("error-text");
                return;
            }
            WritableImage image = ExportService.preview(controller.getDocument(), options,
                    controller.isShowCarbonLabels(), 580, 480);
            previewImage.setImage(image);
            previewMessage.setText(options.isSelectionOnly()
                    ? "Previewing selection only" : "Previewing entire drawing");
            previewMessage.getStyleClass().removeAll("error-text");
            debounce.stop();
        } catch (Exception ex) {
            ex.printStackTrace();
            previewImage.setImage(null);
            textPreview.setText("");
            previewMessage.setText(ex.getMessage() == null ? "Preview unavailable" : ex.getMessage());
            if (!previewMessage.getStyleClass().contains("error-text")) {
                previewMessage.getStyleClass().add("error-text");
            }
        }
    }

    private void applyControlsToOptions() {
        ExportFormat format = formatCombo.getValue();
        options.setFormat(format);
        String rawPath = pathField.getText().strip();
        if (rawPath.isBlank()) {
            throw new IllegalArgumentException("Choose an output file name.");
        }
        Path path = Path.of(rawPath);
        options.setPath(withExtension(path, format));
        options.setSelectionOnly(selectionOnly.isSelected());
        options.setWidth(widthSpinner.getValue());
        options.setHeight(heightSpinner.getValue());
        options.setTransparent(transparent.isSelected());
        options.setBackgroundColor(toHex(backgroundPicker.getValue()));
        options.setPagePreset(pagePreset.getValue());
        options.setLandscape("Landscape".equals(orientation.getValue()));
        try {
            double cw = Double.parseDouble(customWidth.getText().strip());
            double ch = Double.parseDouble(customHeight.getText().strip());
            double margin = Double.parseDouble(marginField.getText().strip());
            options.setCustomWidthPt(cw * 72);
            options.setCustomHeightPt(ch * 72);
            options.setMarginPt(margin * 72);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Page dimensions and margin must be numbers (inches).");
        }
        Integer dpi = dpiCombo.getValue();
        options.setDpi(dpi == null ? 300 : dpi);
    }

    private boolean performExport() {
        try {
            applyControlsToOptions();
            Path path = options.getPath();
            if (Files.exists(path)) {
                if (!UiUtil.confirm(owner, "Replace file?",
                        path.getFileName() + " already exists",
                        "Do you want to replace the existing file?", "Replace", "Cancel")) {
                    return false;
                }
            }
            ExportService.export(controller.getDocument(), options, controller.isShowCarbonLabels());
            exportedPath = path;
            return true;
        } catch (Exception ex) {
            UiUtil.showError(owner, "Export failed",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return false;
        }
    }

    private void browse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export drawing");
        ExportFormat format = formatCombo.getValue();
        Path currentPath = pathField.getText().isBlank() ? Path.of("exports") : Path.of(pathField.getText());
        Path parent = currentPath.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                if (Files.isDirectory(parent)) {
                    chooser.setInitialDirectory(parent.toFile());
                }
            }
        } catch (IOException ignored) {
        }
        Path filename = currentPath.getFileName();
        if (filename != null) {
            chooser.setInitialFileName(filename.toString());
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.displayName(), "*" + format.extension()));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showSaveDialog(owner);
        if (file != null) {
            pathField.setText(withExtension(file.toPath(), format).toString());
            schedulePreview();
        }
    }

    private static Path withExtension(Path path, ExportFormat format) {
        if (format == null) {
            return path;
        }
        String name = path.getFileName() == null ? "structure" : path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = format.extension();
        if (lower.endsWith(ext)) {
            return path;
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0 && lower.substring(dot).matches("\\.(png|jpe?g|svg|pdf|mol|sdf|smi)")) {
            name = name.substring(0, dot) + ext;
        } else {
            name = name + ext;
        }
        return path.resolveSibling(name);
    }

    private static String safeName(String title) {
        String safe = title == null ? "structure" : title.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return safe.isBlank() ? "structure" : safe;
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private static VBox labeled(String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("property-label");
        VBox box = new VBox(4, label, control);
        box.setFillWidth(true);
        HBox.setHgrow(box, Priority.ALWAYS);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-note");
        label.setWrapText(true);
        return label;
    }

}
