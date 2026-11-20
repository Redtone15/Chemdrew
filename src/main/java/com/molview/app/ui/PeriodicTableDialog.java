package com.molview.app.ui;

import com.molview.app.model.Element;
import com.molview.app.model.PeriodicTable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Accessible, searchable full 118-element periodic table chooser. */
public final class PeriodicTableDialog extends Dialog<String> {
    private final List<ElementButton> elementButtons = new ArrayList<>();
    private final Label detailSymbol = new Label();
    private final Label detailName = new Label();
    private final Label detailMeta = new Label();
    private final Button useButton;
    private Element selected;

    public PeriodicTableDialog(Window owner, String currentSymbol) {
        initOwner(owner);
        setTitle("Periodic Table — MolView Studio");
        setHeaderText(null);
        setResizable(true);
        UiUtil.styleDialog(this);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("periodic-root");
        root.setPadding(new Insets(18));

        VBox header = new VBox(4);
        Label title = new Label("Periodic table");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("Choose any element for the next atom you draw, or apply it to the selected atom.");
        subtitle.getStyleClass().add("dialog-subtitle");
        header.getChildren().addAll(title, subtitle);

        TextField search = new TextField();
        search.setPromptText("Search by name, symbol or atomic number…");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(360);
        HBox topRow = new HBox(12, header, spacer(), search);
        topRow.setAlignment(Pos.CENTER_LEFT);
        root.setTop(topRow);
        BorderPane.setMargin(topRow, new Insets(0, 0, 14, 0));

        GridPane table = new GridPane();
        table.getStyleClass().add("periodic-grid");
        table.setHgap(3);
        table.setVgap(3);
        for (int col = 1; col <= 18; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPrefWidth(42);
            cc.setMinWidth(36);
            table.getColumnConstraints().add(cc);
            Label group = new Label(Integer.toString(col));
            group.getStyleClass().add("periodic-group-label");
            table.add(group, col, 0);
        }
        for (Element element : PeriodicTable.all()) {
            ElementButton button = new ElementButton(element);
            button.setOnAction(event -> select(element));
            button.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    select(element);
                    setResult(element.symbol());
                    close();
                }
            });
            int row = PeriodicTable.gridRow(element);
            if ("lanthanide".equals(element.category())) {
                row = 9;
            } else if ("actinide".equals(element.category())) {
                row = 10;
            }
            table.add(button, PeriodicTable.gridColumn(element), row);
            elementButtons.add(button);
        }
        Label lanthanide = new Label("Lanthanides");
        lanthanide.getStyleClass().add("periodic-series-label");
        table.add(lanthanide, 1, 9, 2, 1);
        Label actinide = new Label("Actinides");
        actinide.getStyleClass().add("periodic-series-label");
        table.add(actinide, 1, 10, 2, 1);

        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("periodic-scroll");
        root.setCenter(scroll);

        VBox details = new VBox(7);
        details.getStyleClass().add("element-details");
        details.setPadding(new Insets(18));
        details.setPrefWidth(250);
        detailSymbol.getStyleClass().add("element-detail-symbol");
        detailName.getStyleClass().add("element-detail-name");
        detailMeta.getStyleClass().add("element-detail-meta");
        Label hint = new Label("Tip: double-click an element to choose it immediately.");
        hint.getStyleClass().add("dialog-hint");
        hint.setWrapText(true);
        details.getChildren().addAll(detailSymbol, detailName, detailMeta, new Region(),
                hint);
        VBox.setVgrow(details.getChildren().get(3), Priority.ALWAYS);
        root.setRight(details);
        BorderPane.setMargin(details, new Insets(0, 0, 0, 16));

        ButtonType useType = new ButtonType("Use Element", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(useType, cancelType);
        getDialogPane().setContent(root);
        getDialogPane().setPrefSize(1180, 720);
        useButton = (Button) getDialogPane().lookupButton(useType);
        useButton.getStyleClass().add("primary-button");
        useButton.setDisable(true);
        setResultConverter(button -> button == useType && selected != null ? selected.symbol() : null);

        search.textProperty().addListener((obs, old, value) -> filter(value));
        search.setOnAction(event -> {
            Element match = firstMatch(search.getText());
            if (match != null) {
                select(match);
                setResult(match.symbol());
                close();
            }
        });

        Element initial = PeriodicTable.bySymbol(currentSymbol);
        if (initial == null) {
            initial = PeriodicTable.bySymbol("C");
        }
        select(initial);
    }

    private void select(Element element) {
        selected = element;
        detailSymbol.setText(element.symbol());
        detailName.setText(element.name());
        detailMeta.setText("Atomic number " + element.atomicNumber()
                + "\n" + PeriodicTable.categoryName(element.category())
                + "\nPeriod " + element.period() + "   •   Group " + element.group());
        for (ElementButton button : elementButtons) {
            button.pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("selected"),
                    button.element.equals(element));
        }
        useButton.setDisable(false);
    }

    private void filter(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Element first = null;
        for (ElementButton button : elementButtons) {
            Element e = button.element;
            boolean match = q.isBlank()
                    || e.symbol().toLowerCase(Locale.ROOT).contains(q)
                    || e.name().toLowerCase(Locale.ROOT).contains(q)
                    || Integer.toString(e.atomicNumber()).equals(q);
            button.setOpacity(match ? 1.0 : 0.18);
            if (match && first == null) {
                first = e;
            }
        }
        if (!q.isBlank() && first != null) {
            select(first);
        }
    }

    private Element firstMatch(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return selected;
        }
        for (Element e : PeriodicTable.all()) {
            if (e.symbol().toLowerCase(Locale.ROOT).contains(q)
                    || e.name().toLowerCase(Locale.ROOT).contains(q)
                    || Integer.toString(e.atomicNumber()).equals(q)) {
                return e;
            }
        }
        return null;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static final class ElementButton extends Button {
        private final Element element;

        private ElementButton(Element element) {
            this.element = element;
            getStyleClass().add("element-cell");
            setStyle("-fx-background-color: " + PeriodicTable.categoryColor(element.category()) + ";");
            VBox content = new VBox(0);
            content.setAlignment(Pos.CENTER);
            Label number = new Label(Integer.toString(element.atomicNumber()));
            number.getStyleClass().add("element-number");
            Label symbol = new Label(element.symbol());
            symbol.getStyleClass().add("element-symbol");
            content.getChildren().addAll(number, symbol);
            setGraphic(content);
            setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            setGraphicTextGap(0);
            setPadding(Insets.EMPTY);
            content.setMinWidth(Region.USE_PREF_SIZE);
            number.setMinWidth(Region.USE_PREF_SIZE);
            symbol.setMinWidth(Region.USE_PREF_SIZE);
            setPrefSize(40, 42);
            setMinSize(36, 38);
            setMaxSize(44, 44);
            setFocusTraversable(false);
            Tooltip tooltip = new Tooltip(element.name() + " (" + element.symbol() + ")"
                    + "\n" + PeriodicTable.categoryName(element.category())
                    + " • atomic number " + element.atomicNumber());
            tooltip.setShowDelay(javafx.util.Duration.millis(280));
            setTooltip(tooltip);
        }
    }
}
