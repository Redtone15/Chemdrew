package com.molview.app.ui;

import com.molview.app.editor.EditorController;
import com.molview.app.model.Arrow;
import com.molview.app.model.ArrowType;
import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.BondAngleMode;
import com.molview.app.model.BondType;
import com.molview.app.model.Bracket;
import com.molview.app.model.BracketLabelPosition;
import com.molview.app.model.BracketType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemObject;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.ImplicitHydrogens;
import com.molview.app.model.PeriodicTable;
import com.molview.app.model.TextLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Context-sensitive inspector for the current selection and drawing defaults. */
public final class PropertiesPanel extends ScrollPane {
    private final EditorController controller;
    private final Supplier<Window> owner;
    private final Consumer<String> toast;
    private final Consumer<Atom> changeElementHandler;
    private final VBox root = new VBox(0);
    private final VBox selectionBox = new VBox(10);
    private final Label selectionTitle = new Label("Nothing selected");
    private final Label selectionSubtitle = new Label("Select an atom, bond or annotation to edit it.");
    private final Label counts = new Label();
    private final TextField rLabelField = new TextField();
    private final TextField xLabelField = new TextField();
    private boolean refreshing;

    public PropertiesPanel(EditorController controller, Supplier<Window> owner,
                           Consumer<String> toast, Consumer<Atom> changeElementHandler) {
        this.controller = controller;
        this.owner = owner;
        this.toast = toast;
        this.changeElementHandler = changeElementHandler;
        getStyleClass().add("inspector");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        root.getStyleClass().add("inspector-root");
        setContent(root);

        VBox heading = new VBox(2);
        heading.setPadding(new Insets(18, 18, 12, 18));
        Label title = new Label("Inspector");
        title.getStyleClass().add("inspector-title");
        Label subtitle = new Label("Selection and drawing properties");
        subtitle.getStyleClass().add("inspector-subtitle");
        heading.getChildren().addAll(title, subtitle);
        root.getChildren().add(heading);

        VBox selectionCard = new VBox(7);
        selectionCard.getStyleClass().add("inspector-section");
        selectionCard.setPadding(new Insets(0, 18, 16, 18));
        selectionTitle.getStyleClass().add("section-heading");
        selectionSubtitle.getStyleClass().add("section-note");
        selectionSubtitle.setWrapText(true);
        selectionBox.getStyleClass().add("selection-card");
        selectionBox.setPadding(new Insets(13));
        selectionCard.getChildren().addAll(selectionTitle, selectionSubtitle, selectionBox);
        root.getChildren().add(selectionCard);

        root.getChildren().add(sectionSeparator());

        VBox generic = new VBox(9);
        generic.getStyleClass().add("inspector-section");
        generic.setPadding(new Insets(0, 18, 16, 18));
        Label genericTitle = new Label("Generic groups");
        genericTitle.getStyleClass().add("section-heading");
        Label genericNote = new Label("Reusable labels for unspecified fragments or substituents.");
        genericNote.getStyleClass().add("section-note");
        genericNote.setWrapText(true);

        rLabelField.getStyleClass().add("inspector-field");
        xLabelField.getStyleClass().add("inspector-field");
        rLabelField.setPromptText("R label");
        xLabelField.setPromptText("X label");
        Button addR = smallButton("Add R", "Place an R group");
        Button addX = smallButton("Add X", "Place an X group");
        addR.setOnAction(e -> {
            controller.setRLabel(rLabelField.getText());
            controller.setActiveGeneric(com.molview.app.model.AtomKind.R_GROUP);
            toast.accept("R group ready — click the canvas to place it.");
        });
        addX.setOnAction(e -> {
            controller.setXLabel(xLabelField.getText());
            controller.setActiveGeneric(com.molview.app.model.AtomKind.X_GROUP);
            toast.accept("X group ready — click the canvas to place it.");
        });
        HBox rRow = new HBox(7, labeled("R label", rLabelField), addR);
        HBox xRow = new HBox(7, labeled("X label", xLabelField), addX);
        HBox.setHgrow(rLabelField, Priority.ALWAYS);
        HBox.setHgrow(xLabelField, Priority.ALWAYS);
        rRow.setAlignment(Pos.BOTTOM_LEFT);
        xRow.setAlignment(Pos.BOTTOM_LEFT);
        generic.getChildren().addAll(genericTitle, genericNote, rRow, xRow);
        root.getChildren().add(generic);

        root.getChildren().add(sectionSeparator());

        VBox canvasSection = new VBox(9);
        canvasSection.getStyleClass().add("inspector-section");
        canvasSection.setPadding(new Insets(0, 18, 18, 18));
        Label canvasTitle = new Label("Canvas");
        canvasTitle.getStyleClass().add("section-heading");
        CheckBox grid = new CheckBox("Show hexagonal grid");
        CheckBox carbon = new CheckBox("Show carbon labels");
        grid.getStyleClass().add("inspector-check");
        carbon.getStyleClass().add("inspector-check");
        grid.setOnAction(e -> {
            if (!refreshing) {
                controller.setShowGrid(grid.isSelected());
            }
        });
        carbon.setOnAction(e -> {
            if (!refreshing) {
                controller.setShowCarbonLabels(carbon.isSelected());
            }
        });
        Label angleHeading = new Label("New bond angle");
        angleHeading.getStyleClass().add("property-value");
        ComboBox<BondAngleMode> angleMode = new ComboBox<>();
        angleMode.getItems().setAll(BondAngleMode.values());
        angleMode.setValue(controller.getBondAngleMode());
        angleMode.getStyleClass().add("inspector-combo");
        angleMode.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> customAngle = new Spinner<>(5, 180,
                controller.getCustomBondAngle());
        customAngle.setEditable(true);
        customAngle.setPrefWidth(88);
        HBox customRow = new HBox(7, new Label("Custom step °"), customAngle);
        customRow.setAlignment(Pos.CENTER_LEFT);
        customRow.setVisible(controller.getBondAngleMode() == BondAngleMode.CUSTOM);
        customRow.setManaged(customRow.isVisible());
        angleMode.setOnAction(e -> {
            if (!refreshing && angleMode.getValue() != null) {
                controller.setBondAngleMode(angleMode.getValue());
                customRow.setVisible(angleMode.getValue() == BondAngleMode.CUSTOM);
                customRow.setManaged(customRow.isVisible());
            }
        });
        customAngle.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                controller.setCustomBondAngle(newValue);
            }
        });
        Label angleNote = new Label(
                "Free: no snap · 60°: six directions · Custom: any step · Hex: 120° lattice");
        angleNote.getStyleClass().add("section-note");
        angleNote.setWrapText(true);
        Spinner<Double> chainThreshold = new Spinner<>(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(
                        0.5, 5.0, controller.getChainThresholdUnits(), 0.25));
        chainThreshold.setEditable(true);
        chainThreshold.setPrefWidth(82);
        chainThreshold.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                controller.setChainThresholdUnits(newValue);
            }
        });
        HBox chainRow = new HBox(7, new Label("Chain threshold"), chainThreshold,
                new Label("units"));
        chainRow.setAlignment(Pos.CENTER_LEFT);
        Label chainNote = new Label(
                "Drag an atom farther than this to grow a chain with the selected element at its end.");
        chainNote.getStyleClass().add("section-note");
        chainNote.setWrapText(true);
        Label annotationsHeading = new Label("Annotations");
        annotationsHeading.getStyleClass().add("property-value");
        CheckBox deltaRings = new CheckBox("Show δ+ / δ− rings");
        deltaRings.getStyleClass().add("inspector-check");
        deltaRings.setSelected(controller.isShowDeltaRings());
        deltaRings.setOnAction(e -> {
            if (!refreshing) {
                controller.setShowDeltaRings(deltaRings.isSelected());
            }
        });
        Spinner<Integer> electronDistance = new Spinner<>(16, 80,
                controller.getElectronDistance());
        electronDistance.setEditable(true);
        electronDistance.setPrefWidth(82);
        electronDistance.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                controller.setElectronDistance(newValue);
            }
        });
        Spinner<Integer> deltaDistance = new Spinner<>(16, 80,
                controller.getDeltaLabelDistance());
        deltaDistance.setEditable(true);
        deltaDistance.setPrefWidth(82);
        deltaDistance.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                controller.setDeltaLabelDistance(newValue);
            }
        });
        HBox electronRow = new HBox(7, new Label("Electron distance"), electronDistance);
        electronRow.setAlignment(Pos.CENTER_LEFT);
        HBox deltaRow = new HBox(7, new Label("δ label distance"), deltaDistance);
        deltaRow.setAlignment(Pos.CENTER_LEFT);
        Label annotationNote = new Label(
                "Canvas units from the atom to lone-pair dots or δ text.");
        annotationNote.getStyleClass().add("section-note");
        annotationNote.setWrapText(true);
        Label lattice = new Label("Hex mode keeps one-edge lattice bonds.");
        lattice.getStyleClass().add("section-note");
        lattice.setWrapText(true);
        counts.getStyleClass().add("section-note");
        counts.setWrapText(true);
        Button selectAll = smallButton("Select all", "Select every object");
        selectAll.setOnAction(e -> controller.selectAll());
        HBox quick = new HBox(7, selectAll);
        canvasSection.getChildren().addAll(canvasTitle, grid, carbon,
                angleHeading, angleMode, customRow, angleNote,
                chainRow, chainNote,
                annotationsHeading, deltaRings, electronRow, deltaRow,
                annotationNote, lattice, counts, quick);
        root.getChildren().add(canvasSection);

        rLabelField.setOnAction(e -> controller.setRLabel(rLabelField.getText()));
        xLabelField.setOnAction(e -> controller.setXLabel(xLabelField.getText()));
        rLabelField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                controller.setRLabel(rLabelField.getText());
            }
        });
        xLabelField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                controller.setXLabel(xLabelField.getText());
            }
        });

        this.gridBox = grid;
        this.carbonBox = carbon;
        controller.addChangeListener(this::refresh);
        refresh();
    }

    private CheckBox gridBox;
    private CheckBox carbonBox;

    public void refresh() {
        refreshing = true;
        try {
            rLabelField.setText(controller.getRLabel());
            xLabelField.setText(controller.getXLabel());
            if (gridBox != null) {
                gridBox.setSelected(controller.isShowGrid());
                carbonBox.setSelected(controller.isShowCarbonLabels());
            }
            List<ChemObject> selected = controller.selectedObjects();
            selectionTitle.setText(controller.selectionSummary());
            selectionSubtitle.setText(selected.isEmpty()
                    ? "Select an atom, bond or annotation to edit it."
                    : "Edits apply to the current selection.");
            rebuildSelection(selected);
            int atomCount = controller.getDocument().atoms().size();
            int bondCount = controller.getDocument().bonds().size();
            int annotationCount = controller.getDocument().annotations().size()
                    + controller.getDocument().arrows().size()
                    + controller.getDocument().brackets().size();
            counts.setText(atomCount + " atoms  •  " + bondCount + " bonds  •  "
                    + annotationCount + " annotations");
        } finally {
            refreshing = false;
        }
    }

    private void rebuildSelection(List<ChemObject> selected) {
        selectionBox.getChildren().clear();
        if (selected.isEmpty()) {
            Label hint = new Label("Tip: double-click an atom for properties, or drag a marquee to select a group.");
            hint.getStyleClass().add("section-note");
            hint.setWrapText(true);
            selectionBox.getChildren().add(hint);
            return;
        }
        if (selected.size() > 1) {
            rebuildMultiple(selected);
            return;
        }
        ChemObject object = selected.get(0);
        if (object instanceof Atom atom) {
            rebuildAtom(atom);
        } else if (object instanceof Bond bond) {
            rebuildBond(bond);
        } else if (object instanceof ChargeMark charge) {
            rebuildCharge(charge);
        } else if (object instanceof ElectronMark electron) {
            rebuildElectron(electron);
        } else if (object instanceof TextLabel label) {
            rebuildText(label);
        } else if (object instanceof Arrow arrow) {
            rebuildArrow(arrow);
        } else if (object instanceof Bracket bracket) {
            rebuildBracket(bracket);
        }
    }

    private void rebuildAtom(Atom atom) {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label chip = new Label(ImplicitHydrogens.label(atom, controller.getDocument()));
        chip.getStyleClass().add("atom-chip");
        chip.setStyle("-fx-text-fill: " + (atom.isGeneric()
                ? (atom.getElementSymbol().equals("R") ? "#7c3aed" : "#0e7490")
                : PeriodicTable.atomColor(atom.getElementSymbol())) + ";");
        VBox details = new VBox(1);
        Label name = new Label(atom.isGeneric()
                ? (atom.getKind() == com.molview.app.model.AtomKind.R_GROUP ? "R group" : "X group")
                : "Atom " + atom.getElementSymbol() + " — " + PeriodicTable.nameOf(atom.getElementSymbol()));
        name.getStyleClass().add("property-value");
        int charge = controller.getDocument().formalCharge(atom);
        Label chargeNote = new Label(charge == 0 ? "Neutral" : "Formal charge " + (charge > 0 ? "+" : "") + charge);
        chargeNote.getStyleClass().add("section-note");
        details.getChildren().addAll(name, chargeNote);
        header.getChildren().addAll(chip, details);
        selectionBox.getChildren().add(header);

        Button elementButton = smallButton("Change element…", "Open the periodic table");
        elementButton.setOnAction(e -> changeElementHandler.accept(atom));
        selectionBox.getChildren().add(elementButton);

        if (atom.isGeneric()) {
            TextField label = new TextField(atom.getGenericLabel());
            label.getStyleClass().add("inspector-field");
            label.setOnAction(e -> controller.setAtomGenericLabel(atom, label.getText()));
            label.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused) {
                    controller.setAtomGenericLabel(atom, label.getText());
                }
            });
            selectionBox.getChildren().add(labeled("Group label", label));
        }

        Label chargeLabel = new Label("Charges");
        chargeLabel.getStyleClass().add("property-label");
        FlowPane charges = new FlowPane(6, 6);
        for (ChargeKind kind : ChargeKind.values()) {
            Button button = smallButton(kind.label(), "Add " + kind);
            button.setOnAction(e -> controller.addCharge(atom.getX() + 18, atom.getY() - 15, kind, atom));
            charges.getChildren().add(button);
        }
        selectionBox.getChildren().addAll(chargeLabel, charges);

        Label electronLabel = new Label("Electrons");
        electronLabel.getStyleClass().add("property-label");
        FlowPane electrons = new FlowPane(6, 6);
        Button lone = smallButton("Lone pair", "Add a lone pair");
        lone.setOnAction(e -> controller.addElectron(atom.getX() - 20, atom.getY() + 12,
                ElectronKind.LONE_PAIR, atom));
        Button radical = smallButton("Radical •", "Add one unpaired electron");
        radical.setOnAction(e -> controller.addElectron(atom.getX() - 20, atom.getY() + 12,
                ElectronKind.RADICAL, atom));
        electrons.getChildren().addAll(lone, radical);
        selectionBox.getChildren().addAll(electronLabel, electrons);

        Label position = new Label(String.format(Locale.ROOT, "x %.1f   y %.1f", atom.getX(), atom.getY()));
        position.getStyleClass().add("section-note");
        selectionBox.getChildren().add(position);

        if (!controller.getDocument().annotationsOf(atom).isEmpty()) {
            Label attached = new Label(controller.getDocument().annotationsOf(atom).size()
                    + " attached annotation(s)");
            attached.getStyleClass().add("section-note");
            selectionBox.getChildren().add(attached);
        }
        addDangerActions();
    }

    private void rebuildBond(Bond bond) {
        Label type = new Label(bond.getType().displayName() + " bond");
        type.getStyleClass().add("property-value");
        Label endpoints = new Label(
                ImplicitHydrogens.label(bond.getFrom(), controller.getDocument()) + "  →  "
                + ImplicitHydrogens.label(bond.getTo(), controller.getDocument())
                + "\n" + String.format(Locale.ROOT, "Length %.1f px", bond.length()));
        endpoints.getStyleClass().add("section-note");
        ComboBox<BondType> combo = new ComboBox<>();
        combo.getItems().setAll(BondType.values());
        combo.setValue(bond.getType());
        combo.getStyleClass().add("inspector-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setOnAction(e -> {
            if (!refreshing && combo.getValue() != null) {
                controller.setSelectedBondsType(combo.getValue());
            }
        });
        selectionBox.getChildren().addAll(type, endpoints, labeled("Bond type", combo));
        addDangerActions();
    }

    private void rebuildCharge(ChargeMark charge) {
        Label title = new Label("Charge annotation");
        title.getStyleClass().add("property-value");
        ComboBox<ChargeKind> combo = new ComboBox<>();
        combo.getItems().setAll(ChargeKind.values());
        combo.setValue(charge.getKind());
        combo.getStyleClass().add("inspector-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setOnAction(e -> {
            if (!refreshing && combo.getValue() != null) {
                controller.perform("Change charge", () -> charge.setKind(combo.getValue()));
            }
        });
        Label attached = new Label(charge.getAttachedAtomId() == null
                ? "Not attached to an atom" : "Attached to "
                + attachedLabel(charge.getAttachedAtomId()));
        attached.getStyleClass().add("section-note");
        selectionBox.getChildren().addAll(title, attached, labeled("Charge type", combo));
        addDangerActions();
    }

    private void rebuildElectron(ElectronMark electron) {
        Label title = new Label("Electron annotation");
        title.getStyleClass().add("property-value");
        ComboBox<ElectronKind> combo = new ComboBox<>();
        combo.getItems().setAll(ElectronKind.values());
        combo.setValue(electron.getKind());
        combo.getStyleClass().add("inspector-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setOnAction(e -> {
            if (!refreshing && combo.getValue() != null) {
                controller.perform("Change electron mark", () -> electron.setKind(combo.getValue()));
            }
        });
        Label attached = new Label(electron.getAttachedAtomId() == null
                ? "Independent annotation" : "Attached to "
                + attachedLabel(electron.getAttachedAtomId()));
        attached.getStyleClass().add("section-note");
        selectionBox.getChildren().addAll(title, attached, labeled("Electrons", combo));
        addDangerActions();
    }

    private void rebuildText(TextLabel label) {
        Label title = new Label("Text annotation");
        title.getStyleClass().add("property-value");
        TextField text = new TextField(label.getText());
        text.getStyleClass().add("inspector-field");
        text.setOnAction(e -> commitText(label, text.getText()));
        text.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                commitText(label, text.getText());
            }
        });
        Spinner<Integer> size = new Spinner<>();
        size.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 72,
                (int) Math.round(label.getFontSize()), 1));
        size.getStyleClass().add("inspector-spinner");
        size.valueProperty().addListener((obs, old, value) -> {
            if (!refreshing && value != null) {
                controller.perform("Resize text", () -> label.setFontSize(value));
            }
        });
        CheckBox bold = new CheckBox("Bold");
        bold.setSelected(label.isBold());
        bold.getStyleClass().add("inspector-check");
        bold.setOnAction(e -> {
            if (!refreshing) {
                controller.perform("Bold text", () -> label.setBold(bold.isSelected()));
            }
        });
        ColorPicker color = new ColorPicker(Color.web(label.getColor()));
        color.getStyleClass().add("inspector-color");
        color.setOnAction(e -> {
            if (!refreshing) {
                controller.perform("Change text color", () -> label.setColor(toHex(color.getValue())));
            }
        });
        HBox formatting = new HBox(8, size, bold);
        formatting.setAlignment(Pos.CENTER_LEFT);
        selectionBox.getChildren().addAll(title, labeled("Text", text), labeled("Size", formatting),
                labeled("Color", color));
        addDangerActions();
    }

    private void rebuildArrow(Arrow arrow) {
        Label title = new Label(arrow.getType().displayName());
        title.getStyleClass().add("property-value");
        ComboBox<ArrowType> combo = new ComboBox<>();
        combo.getItems().setAll(ArrowType.values());
        combo.setValue(arrow.getType());
        combo.getStyleClass().add("inspector-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setOnAction(e -> {
            if (!refreshing && combo.getValue() != null) {
                controller.setArrowType(arrow, combo.getValue());
            }
        });
        Button reverse = smallButton("Reverse direction", "Swap the arrow endpoints");
        reverse.setOnAction(e -> controller.reverseArrow(arrow));
        Label hint = new Label(arrow.isCurved()
                ? "Drag the round endpoints to resize; drag the square handle to bend the curve."
                : "Drag either endpoint to reposition or resize the arrow.");
        hint.getStyleClass().add("section-note");
        hint.setWrapText(true);
        selectionBox.getChildren().addAll(title, labeled("Arrow type", combo), reverse, hint);
        addDangerActions();
    }

    private void rebuildBracket(Bracket bracket) {
        Label title = new Label(bracket.getType().open() + " … " + bracket.getType().close());
        title.getStyleClass().add("property-value");
        ComboBox<BracketType> type = new ComboBox<>();
        type.getItems().setAll(BracketType.values());
        type.setValue(bracket.getType());
        type.getStyleClass().add("inspector-combo");
        type.setMaxWidth(Double.MAX_VALUE);
        type.setOnAction(e -> {
            if (!refreshing && type.getValue() != null) {
                controller.perform("Change bracket", () -> bracket.setType(type.getValue()));
            }
        });
        TextField label = new TextField(bracket.getLabel());
        label.getStyleClass().add("inspector-field");
        label.setPromptText("e.g. n, 2−, +");
        label.setOnAction(e -> {
            if (!bracket.getLabel().equals(label.getText())) {
                controller.perform("Set bracket label", () -> bracket.setLabel(label.getText()));
            }
        });
        label.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && !bracket.getLabel().equals(label.getText())) {
                controller.perform("Set bracket label", () -> bracket.setLabel(label.getText()));
            }
        });
        ComboBox<BracketLabelPosition> position = new ComboBox<>();
        position.getItems().setAll(BracketLabelPosition.values());
        position.setValue(bracket.getLabelPosition());
        position.getStyleClass().add("inspector-combo");
        position.setMaxWidth(Double.MAX_VALUE);
        position.setOnAction(e -> {
            if (!refreshing && position.getValue() != null) {
                controller.perform("Position bracket label",
                        () -> bracket.setLabelPosition(position.getValue()));
            }
        });
        Label hint = new Label("Drag a corner or edge handle to resize the bracket.");
        hint.getStyleClass().add("section-note");
        hint.setWrapText(true);
        selectionBox.getChildren().addAll(title, labeled("Bracket type", type),
                labeled("Label", label), labeled("Label position", position), hint);
        addDangerActions();
    }

    private void rebuildMultiple(List<ChemObject> selected) {
        boolean allBonds = selected.stream().allMatch(o -> o instanceof Bond);
        Label summary = new Label(controller.selectionSummary());
        summary.getStyleClass().add("property-value");
        selectionBox.getChildren().add(summary);
        if (allBonds) {
            ComboBox<BondType> combo = new ComboBox<>();
            combo.getItems().setAll(BondType.values());
            combo.setValue(((Bond) selected.get(0)).getType());
            combo.getStyleClass().add("inspector-combo");
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setOnAction(e -> {
                if (!refreshing && combo.getValue() != null) {
                    controller.setSelectedBondsType(combo.getValue());
                }
            });
            selectionBox.getChildren().add(labeled("Set all bond types", combo));
        }
        Label groupHint = new Label("Group operations");
        groupHint.getStyleClass().add("property-label");
        FlowPane operations = new FlowPane(7, 7);
        Button rotateLeft = smallButton("⟲ 15°", "Rotate selection counter-clockwise");
        rotateLeft.setOnAction(e -> controller.rotateSelection(-15));
        Button rotateRight = smallButton("⟳ 15°", "Rotate selection clockwise");
        rotateRight.setOnAction(e -> controller.rotateSelection(15));
        Button bracket = smallButton("[ Enclose ]", "Enclose selection in brackets");
        bracket.setOnAction(e -> controller.addBracket(BracketType.SQUARE, "n", 400, 300));
        Button duplicate = smallButton("Duplicate", "Duplicate the selection");
        duplicate.setOnAction(e -> controller.duplicateSelection());
        operations.getChildren().addAll(rotateLeft, rotateRight, bracket, duplicate);
        selectionBox.getChildren().addAll(groupHint, operations);
        addDangerActions();
    }

    private void addDangerActions() {
        HBox actions = new HBox(7);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button delete = new Button("Delete selection");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> controller.deleteSelection());
        actions.getChildren().add(delete);
        selectionBox.getChildren().add(actions);
    }

    private String attachedLabel(String atomId) {
        Atom atom = controller.getDocument().findAtom(atomId);
        return atom == null ? "missing atom"
                : ImplicitHydrogens.label(atom, controller.getDocument());
    }

    private void commitText(TextLabel label, String value) {
        if (!label.getText().equals(value)) {
            controller.perform("Edit text", () -> label.setText(value));
        }
    }

    private VBox labeled(String text, Node control) {
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

    private Button smallButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("small-button");
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private Separator sectionSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("inspector-separator");
        return separator;
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
}
