package com.molview.app.ui;

import com.molview.app.editor.EditorController;
import com.molview.app.model.Arrow;
import com.molview.app.model.ArrowType;
import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.BondAngleMode;
import com.molview.app.model.BondType;
import com.molview.app.model.Bracket;
import com.molview.app.model.BracketType;
import com.molview.app.model.Bounds;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.Geometry;
import com.molview.app.model.HexGrid;
import com.molview.app.model.TextLabel;
import com.molview.app.model.Tool;
import com.molview.app.model.ViewState;
import com.molview.app.render.ChemRenderer;
import com.molview.app.render.RenderSettings;
import com.molview.app.render.SelectionHandles;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interactive drawing surface. Owns the view transform and all direct
 * manipulation gestures, while delegating document mutation to the controller.
 */
public final class DrawingCanvas extends Region {
    public enum ViewChange {
        ZOOM, PAN, LAYOUT
    }

    private final Canvas canvas = new Canvas();
    private final EditorController controller;
    private double zoom = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;

    private Consumer<String> toast = message -> {
    };
    private Consumer<String> status = message -> {
    };
    private Runnable viewChanged = () -> {
    };
    private Runnable focusInspector = () -> {
    };

    private DragMode dragMode = DragMode.NONE;
    private double pressScreenX;
    private double pressScreenY;
    private double pressWorldX;
    private double pressWorldY;
    private double lastWorldX;
    private double lastWorldY;
    private double mouseWorldX;
    private double mouseWorldY;
    private boolean moved;

    private Atom bondDownAtom;
    private HexGrid.Vertex bondDownVertex;
    private Atom pendingBond;
    private HexGrid.Vertex pendingBondVertex;
    private Atom bondTargetAtom;
    private HexGrid.Vertex bondTargetVertex;
    private Atom atomPressTarget;
    private HexGrid.Vertex chainStartVertex;
    private List<HexGrid.Vertex> chainPreview = List.of();
    private boolean freeBondDrag;
    private boolean freeBondTool;
    private boolean pendingFreeBond;
    private boolean chainExtensionActive;
    private double freeBondX = Double.NaN;
    private double freeBondY = Double.NaN;
    private boolean mouseInside;
    private Point2D arrowRawStart;
    private Point2D arrowDraftStart;
    private Point2D arrowDraftEnd;
    private ArrowType arrowDraftType;
    private double bracketStartX;
    private double bracketStartY;
    private double bracketEndX;
    private double bracketEndY;
    private BracketType bracketDraftType;
    private Rectangle2D marquee;
    private boolean spaceDown;
    private boolean selectionMoveStarted;

    private SelectionHandles.Handle activeHandle;
    private boolean handleEditing;
    private double handleBracketX;
    private double handleBracketY;
    private double handleBracketW;
    private double handleBracketH;
    private ChemObject hovered;
    private ChemDocument observedDocument;

    private enum DragMode {
        NONE,
        PAN,
        MOVE_SELECTION,
        MARQUEE,
        CHAIN_DRAW,
        BOND_DRAW,
        ARROW_DRAW,
        BRACKET_DRAW,
        HANDLE
    }

    public DrawingCanvas(EditorController controller) {
        this.controller = controller;
        getStyleClass().add("drawing-canvas");
        setFocusTraversable(true);
        setMinSize(120, 120);
        canvas.setMouseTransparent(true);
        getChildren().add(canvas);
        observedDocument = controller.getDocument();
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        setOnMouseMoved(this::handleMouseMoved);
        setOnMouseEntered(event -> {
            mouseInside = true;
            redraw();
        });
        setOnMouseExited(event -> {
            hovered = null;
            mouseInside = false;
            redraw();
        });
        setOnScroll(this::handleScroll);
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY
                    && controller.getTool() == Tool.SELECT) {
                handleDoubleClick(event);
            }
        });
        controller.addChangeListener(() -> {
            if (observedDocument != controller.getDocument()) {
                observedDocument = controller.getDocument();
                pendingBond = null;
                pendingBondVertex = null;
                bondDownAtom = null;
                bondDownVertex = null;
                bondTargetAtom = null;
                bondTargetVertex = null;
                atomPressTarget = null;
                chainPreview = List.of();
                activeHandle = null;
            }
            if (controller.getTool() != Tool.BOND) {
                pendingBond = null;
                pendingBondVertex = null;
            }
            if (pendingBond != null
                    && !controller.getDocument().objects().contains(pendingBond)) {
                pendingBond = null;
                pendingBondVertex = null;
            }
            if (controller.getTool() != Tool.ATOM) {
                atomPressTarget = null;
                chainPreview = List.of();
            }
            updateCursor();
            redraw();
        });
        updateCursor();
    }

    public void setToastHandler(Consumer<String> toast) {
        this.toast = toast;
    }

    public void setStatusHandler(Consumer<String> status) {
        this.status = status;
    }

    public void setViewChangedHandler(Runnable viewChanged) {
        this.viewChanged = viewChanged;
    }

    public void setFocusInspectorHandler(Runnable focusInspector) {
        this.focusInspector = focusInspector;
    }

    public double getZoom() {
        return zoom;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas.setWidth(w);
            canvas.setHeight(h);
        }
        canvas.relocate(0, 0);
        redraw();
    }

    public void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = Math.max(1, canvas.getWidth());
        double h = Math.max(1, canvas.getHeight());
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);

        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(zoom, zoom);
        if (controller.isShowGrid() && zoom > 0.3) {
            double minX = -offsetX / zoom;
            double minY = -offsetY / zoom;
            double maxX = (w - offsetX) / zoom;
            double maxY = (h - offsetY) / zoom;
            ChemRenderer.drawHexGrid(gc, minX, minY, maxX, maxY, zoom);
        }
        ChemDocument document = controller.getDocument();
        drawDeltaRings(gc, document);
        ChemRenderer.drawScene(gc, document,
                RenderSettings.screen(controller.isShowCarbonLabels(), zoom));
        drawHover(gc);
        drawChainPreview(gc);
        drawPendingBond(gc);
        drawArrowDraft(gc);
        drawBracketDraft(gc);
        drawMarquee(gc);
        gc.restore();
    }

    private void drawHover(GraphicsContext gc) {
        if (!mouseInside || dragMode != DragMode.NONE) {
            return;
        }
        Tool tool = controller.getTool();
        if (tool == Tool.PARTIAL_POS || tool == Tool.PARTIAL_NEG) {
            if (!controller.isShowDeltaRings()) {
                return;
            }
            Atom target = nearestAtom(mouseWorldX, mouseWorldY, 26);
            if (target != null) {
                ChargeKind kind = tool.chargeKind();
                if (kind != null) {
                    drawChargeRing(gc, target, chargeColor(kind), true);
                }
            }
        }
    }

    /**
     * A partial-charge (δ+/δ−) assignment gets a ring around its host atom.
     * This is deliberately separate from the selection outline and is not used
     * for the chain-drag preview.
     */
    private void drawDeltaRings(GraphicsContext gc, ChemDocument document) {
        if (!controller.isShowDeltaRings()) {
            return;
        }
        // Build the host-to-kind lookup once. The previous atom-first lookup
        // rescanned (and reallocated) all charge marks for every atom on every
        // canvas redraw.
        Map<String, ChargeKind> partialKinds = new HashMap<>();
        for (ChargeMark mark : document.chargeMarks()) {
            ChargeKind kind = mark.getKind();
            if (!kind.isPartial() || mark.getAttachedAtomId() == null) {
                continue;
            }
            if (kind == ChargeKind.PARTIAL_POSITIVE) {
                partialKinds.put(mark.getAttachedAtomId(), kind);
            } else {
                partialKinds.putIfAbsent(mark.getAttachedAtomId(), kind);
            }
        }
        if (partialKinds.isEmpty()) {
            return;
        }
        for (Atom atom : document.atoms()) {
            ChargeKind kind = partialKinds.get(atom.getId());
            if (kind != null) {
                drawChargeRing(gc, atom, chargeColor(kind), false);
            }
        }
    }

    private String chargeColor(ChargeKind kind) {
        return switch (kind) {
            case POSITIVE -> "#1d4ed8";
            case NEGATIVE -> "#b91c1c";
            case PARTIAL_POSITIVE -> "#b45309";
            case PARTIAL_NEGATIVE -> "#0f766e";
        };
    }

    private void drawChargeRing(GraphicsContext gc, Atom atom, String color, boolean hover) {
        double radius = Math.max(16,
                ChemRenderer.atomLabelRadius(atom, controller.getDocument(),
                        controller.isShowCarbonLabels()) + 7);
        gc.save();
        gc.setStroke(Color.web(color, hover ? 0.95 : 0.72));
        gc.setLineWidth((hover ? 1.9 : 1.4) / zoom);
        gc.setLineDashes(4 / zoom, 3 / zoom);
        gc.strokeOval(atom.getX() - radius, atom.getY() - radius, radius * 2, radius * 2);
        gc.setLineDashes();
        gc.restore();
    }

    private void drawChainPreview(GraphicsContext gc) {
        if (dragMode != DragMode.CHAIN_DRAW) {
            return;
        }
        if (freeBondDrag && !chainExtensionActive) {
            if (atomPressTarget != null && !Double.isNaN(freeBondX)
                    && !Double.isNaN(freeBondY)) {
                gc.save();
                gc.setStroke(Color.web("#2563eb", 0.8));
                gc.setLineWidth(2.2 / zoom);
                gc.strokeLine(atomPressTarget.getX(), atomPressTarget.getY(),
                        freeBondX, freeBondY);
                gc.restore();
            }
            return;
        }
        if (chainPreview.size() < 2) {
            return;
        }
        gc.save();
        gc.setStroke(Color.web("#2563eb", 0.8));
        gc.setFill(Color.web("#1d4ed8", 0.9));
        gc.setLineWidth(2.2 / zoom);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);

        // Stretch preview is a solid line. No element indicator and no circular
        // atom halos are drawn.
        for (int i = 1; i < chainPreview.size(); i++) {
            HexGrid.Vertex a = chainPreview.get(i - 1);
            HexGrid.Vertex b = chainPreview.get(i);
            gc.strokeLine(a.x(), a.y(), b.x(), b.y());
        }

        if (atomPressTarget == null || chainPreview.size() > 2) {
            // Number every carbon after the source; the selected heteroatom at
            // the far end is not counted as a carbon.
            boolean endHetero = atomPressTarget != null
                    && !(controller.getActiveAtomKind()
                    == com.molview.app.model.AtomKind.ELEMENT
                    && "C".equals(controller.getActiveElement()));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            for (int i = 1; i < chainPreview.size(); i++) {
                if (endHetero && i == chainPreview.size() - 1) {
                    continue;
                }
                HexGrid.Vertex vertex = chainPreview.get(i);
                String number = Integer.toString(i + 1);
                double x = vertex.x() + 8;
                double y = vertex.y() - 10;
                gc.setLineWidth(2.4 / zoom);
                gc.setStroke(Color.web("#ffffff", 0.9));
                gc.strokeText(number, x, y);
                gc.setFill(Color.web("#1d4ed8", 0.95));
                gc.fillText(number, x, y);
            }
        }
        gc.restore();
    }

    private void drawPendingBond(GraphicsContext gc) {
        if (controller.getTool() != Tool.BOND) {
            return;
        }
        if (freeBondTool && bondDownAtom != null
                && !Double.isNaN(freeBondX) && !Double.isNaN(freeBondY)) {
            gc.save();
            gc.setStroke(Color.web("#2563eb", 0.8));
            gc.setLineWidth(2.0 / zoom);
            gc.strokeLine(bondDownAtom.getX(), bondDownAtom.getY(), freeBondX, freeBondY);
            gc.restore();
            return;
        }
        if (pendingFreeBond && pendingBond != null) {
            double[] endpoint = controller.freeBondEndpoint(
                    pendingBond, mouseWorldX, mouseWorldY);
            if (endpoint != null) {
                gc.save();
                gc.setStroke(Color.web("#2563eb", 0.8));
                gc.setLineWidth(2.0 / zoom);
                gc.strokeLine(pendingBond.getX(), pendingBond.getY(),
                        endpoint[0], endpoint[1]);
                gc.restore();
            }
            return;
        }
        HexGrid.Vertex startVertex = pendingBondVertex != null ? pendingBondVertex
                : (dragMode == DragMode.BOND_DRAW ? bondDownVertex : null);
        if (startVertex == null) {
            return;
        }
        HexGrid.Vertex target = bondTargetVertex;
        if (target == null) {
            target = HexGrid.neighborToward(startVertex, mouseWorldX, mouseWorldY);
        }
        if (target == null) {
            return;
        }
        gc.save();
        gc.setStroke(Color.web("#2563eb", 0.8));
        gc.setLineWidth(2.0 / zoom);
        gc.strokeLine(startVertex.x(), startVertex.y(), target.x(), target.y());
        gc.restore();
    }

    private void drawArrowDraft(GraphicsContext gc) {
        if (dragMode != DragMode.ARROW_DRAW || arrowDraftStart == null || arrowDraftEnd == null) {
            return;
        }
        gc.save();
        gc.setStroke(Color.web("#2563eb", 0.8));
        gc.setFill(Color.web("#2563eb", 0.8));
        gc.setLineWidth(2.0 / zoom);
        gc.setLineCap(StrokeLineCap.ROUND);
        if (arrowDraftType == ArrowType.STRAIGHT) {
            gc.strokeLine(arrowDraftStart.getX(), arrowDraftStart.getY(),
                    arrowDraftEnd.getX(), arrowDraftEnd.getY());
            drawPreviewHead(gc, arrowDraftStart, arrowDraftEnd, arrowDraftType.hasFullHead());
        } else {
            double cx = (arrowDraftStart.getX() + arrowDraftEnd.getX()) / 2
                    + (arrowDraftEnd.getY() - arrowDraftStart.getY()) * 0.18;
            double cy = (arrowDraftStart.getY() + arrowDraftEnd.getY()) / 2
                    - (arrowDraftEnd.getX() - arrowDraftStart.getX()) * 0.18;
            gc.beginPath();
            gc.moveTo(arrowDraftStart.getX(), arrowDraftStart.getY());
            gc.quadraticCurveTo(cx, cy, arrowDraftEnd.getX(), arrowDraftEnd.getY());
            gc.stroke();
            drawPreviewHead(gc, new Point2D(cx, cy), arrowDraftEnd, arrowDraftType.hasFullHead());
        }
        gc.restore();
    }

    private void drawPreviewHead(GraphicsContext gc, Point2D previous, Point2D tip, boolean full) {
        double angle = Math.atan2(tip.getY() - previous.getY(), tip.getX() - previous.getX());
        if (full) {
            double backX = tip.getX() - Math.cos(angle) * 12 / zoom;
            double backY = tip.getY() - Math.sin(angle) * 12 / zoom;
            double px = -Math.sin(angle) * 5 / zoom;
            double py = Math.cos(angle) * 5 / zoom;
            gc.fillPolygon(new double[]{tip.getX(), backX + px, backX - px},
                    new double[]{tip.getY(), backY + py, backY - py}, 3);
        } else {
            double back = angle + Math.PI - 0.6;
            gc.strokeLine(tip.getX(), tip.getY(),
                    tip.getX() + Math.cos(back) * 12 / zoom,
                    tip.getY() + Math.sin(back) * 12 / zoom);
        }
    }

    private void drawBracketDraft(GraphicsContext gc) {
        if (dragMode != DragMode.BRACKET_DRAW) {
            return;
        }
        double x = Math.min(bracketStartX, bracketEndX);
        double y = Math.min(bracketStartY, bracketEndY);
        double w = Math.abs(bracketEndX - bracketStartX);
        double h = Math.abs(bracketEndY - bracketStartY);
        gc.save();
        gc.setStroke(Color.web("#2563eb", 0.75));
        gc.setFill(Color.web("#2563eb", 0.05));
        gc.setLineWidth(1.5 / zoom);
        gc.setLineDashes(7 / zoom, 5 / zoom);
        gc.fillRect(x, y, w, h);
        gc.strokeRect(x, y, w, h);
        gc.restore();
    }

    private void drawMarquee(GraphicsContext gc) {
        if (marquee == null) {
            return;
        }
        gc.save();
        gc.setFill(Color.web("#2563eb", 0.08));
        gc.setStroke(Color.web("#2563eb", 0.8));
        gc.setLineWidth(1.2 / zoom);
        gc.fillRect(marquee.getMinX(), marquee.getMinY(), marquee.getWidth(), marquee.getHeight());
        gc.strokeRect(marquee.getMinX(), marquee.getMinY(), marquee.getWidth(), marquee.getHeight());
        gc.restore();
    }

    private void handleMousePressed(MouseEvent event) {
        requestFocus();
        pressScreenX = event.getX();
        pressScreenY = event.getY();
        pressWorldX = toWorldX(event.getX());
        pressWorldY = toWorldY(event.getY());
        lastWorldX = pressWorldX;
        lastWorldY = pressWorldY;
        mouseWorldX = pressWorldX;
        mouseWorldY = pressWorldY;
        mouseInside = true;
        moved = false;
        selectionMoveStarted = false;
        handleEditing = false;
        activeHandle = null;
        atomPressTarget = null;
        chainStartVertex = null;
        chainPreview = List.of();
        bondTargetVertex = null;
        bondTargetAtom = null;

        if (event.getButton() == MouseButton.SECONDARY) {
            showContextMenu(event);
            return;
        }
        if (event.getButton() == MouseButton.MIDDLE
                || controller.getTool() == Tool.PAN || spaceDown) {
            beginPan(event);
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        Tool tool = controller.getTool();
        switch (tool) {
            case SELECT -> handleSelectPress(event);
            case ATOM -> handleAtomPress(event);
            case BOND -> handleBondPress(event);
            case CHARGE_POS, CHARGE_NEG, PARTIAL_POS, PARTIAL_NEG -> handleChargePress();
            case LONE_PAIR, RADICAL -> handleElectronPress();
            case ARROW_STRAIGHT, ARROW_CURVED, ARROW_FISHHOOK -> handleArrowPress();
            case BRACKET_SQUARE, BRACKET_ROUND, BRACKET_CURLY -> handleBracketPress();
            case TEXT -> handleTextPress();
            case ERASE -> handleErase();
            case PAN -> beginPan(event);
        }
    }

    private void beginPan(MouseEvent event) {
        dragMode = DragMode.PAN;
        setCursor(Cursor.CLOSED_HAND);
    }

    private void handleSelectPress(MouseEvent event) {
        ChemObject hitHandleObject = null;
        SelectionHandles.Handle hit = hitHandle(pressWorldX, pressWorldY);
        if (hit != null) {
            activeHandle = hit;
            hitHandleObject = hit.target();
            if (hit.target() instanceof Bracket bracket) {
                handleBracketX = bracket.getX();
                handleBracketY = bracket.getY();
                handleBracketW = bracket.getWidth();
                handleBracketH = bracket.getHeight();
            }
            dragMode = DragMode.HANDLE;
            return;
        }

        ChemObject pick = pick(pressWorldX, pressWorldY);
        if (pick != null) {
            if (event.isShiftDown()) {
                controller.toggleSelection(pick);
            } else if (!pick.isSelected()) {
                controller.selectOnly(pick);
            }
            dragMode = DragMode.MOVE_SELECTION;
            return;
        }

        if (!event.isShiftDown()) {
            controller.clearSelection();
        }
        dragMode = DragMode.MARQUEE;
        marquee = new Rectangle2D(pressWorldX, pressWorldY, 0, 0);
    }

    private void handleAtomPress(MouseEvent event) {
        atomPressTarget = nearestAtom(pressWorldX, pressWorldY, 22);
        freeBondDrag = false;
        freeBondX = Double.NaN;
        freeBondY = Double.NaN;
        chainExtensionActive = false;
        if (atomPressTarget != null) {
            freeBondDrag = isFreeBondSource(atomPressTarget)
                    || controller.getBondAngleMode() != BondAngleMode.HEX;
            if (!HexGrid.isVertex(atomPressTarget.getX(), atomPressTarget.getY(), 1.0)
                    && !freeBondDrag) {
                chainStartVertex = null;
                chainPreview = List.of();
                dragMode = DragMode.CHAIN_DRAW;
                return;
            }
            chainStartVertex = controller.snapToVertex(
                    atomPressTarget.getX(), atomPressTarget.getY());
        } else {
            chainStartVertex = controller.snapToVertex(pressWorldX, pressWorldY);
        }
        chainPreview = List.of(chainStartVertex);
        dragMode = DragMode.CHAIN_DRAW;
        redraw();
    }

    /**
     * Terminal hetero atoms (for example the bromine of 1-bromoethane) are free
     * to rotate a new bond to any angle. Carbon remains on the alkane-chain path.
     */
    private boolean isFreeBondSource(Atom atom) {
        return atom != null && !atom.isCarbon()
                && controller.getDocument().bondCount(atom) <= 1;
    }

    private void handleBondPress(MouseEvent event) {
        freeBondTool = false;
        freeBondX = Double.NaN;
        freeBondY = Double.NaN;
        Atom atomHit = nearestAtom(pressWorldX, pressWorldY, 20);
        Bond bondHit = nearestBond(pressWorldX, pressWorldY, 10);
        double atomDistance = atomHit == null ? Double.MAX_VALUE
                : Math.hypot(atomHit.getX() - pressWorldX, atomHit.getY() - pressWorldY);
        boolean clickedBondLine = bondHit != null && atomDistance > 14;
        if (pendingBond == null && clickedBondLine) {
            BondType next = clickedBondType(bondHit.getType());
            if (next == bondHit.getType()) {
                controller.selectOnly(bondHit);
            } else {
                controller.perform("Change bond", () -> {
                    bondHit.setType(next);
                    controller.getDocument().selectOnly(bondHit);
                });
            }
            pendingBond = null;
            pendingBondVertex = null;
            dragMode = DragMode.NONE;
            return;
        }
        if (pendingBond == null && atomHit != null
                && (controller.getBondAngleMode() != BondAngleMode.HEX
                || isFreeBondSource(atomHit))) {
            freeBondTool = true;
            bondDownAtom = atomHit;
            bondDownVertex = controller.snapToVertex(atomHit.getX(), atomHit.getY());
            bondTargetAtom = null;
            bondTargetVertex = null;
            dragMode = DragMode.BOND_DRAW;
            redraw();
            return;
        }
        bondDownAtom = atomHit;
        if (bondDownAtom != null
                && !HexGrid.isVertex(bondDownAtom.getX(), bondDownAtom.getY(), 1.0)) {
            toast.accept("Bonds must start and end on hexagonal-grid vertices.");
            bondDownAtom = null;
            bondDownVertex = null;
            dragMode = DragMode.NONE;
            return;
        }
        bondDownVertex = bondDownAtom != null
                ? controller.snapToVertex(bondDownAtom.getX(), bondDownAtom.getY())
                : controller.snapToVertex(pressWorldX, pressWorldY);
        bondTargetAtom = null;
        bondTargetVertex = null;
        dragMode = DragMode.BOND_DRAW;
        redraw();
    }

    private void handleChargePress() {
        Tool tool = controller.getTool();
        ChargeKind kind = tool.chargeKind();
        if (kind == null) {
            return;
        }
        Atom host = nearestAtom(pressWorldX, pressWorldY, 26);
        controller.addCharge(pressWorldX, pressWorldY, kind, host);
    }

    private void handleElectronPress() {
        Tool tool = controller.getTool();
        ElectronKind kind = tool.electronKind();
        if (kind == null) {
            return;
        }
        Atom host = nearestAtom(pressWorldX, pressWorldY, 26);
        controller.addElectron(pressWorldX, pressWorldY, kind, host);
    }

    private void handleArrowPress() {
        ArrowType type = switch (controller.getTool()) {
            case ARROW_STRAIGHT -> ArrowType.STRAIGHT;
            case ARROW_CURVED -> ArrowType.CURVED;
            case ARROW_FISHHOOK -> ArrowType.FISHHOOK;
            default -> ArrowType.STRAIGHT;
        };
        arrowDraftType = type;
        arrowRawStart = new Point2D(pressWorldX, pressWorldY);
        arrowDraftStart = snapArrowPoint(arrowRawStart, null, true);
        arrowDraftEnd = arrowDraftStart;
        dragMode = DragMode.ARROW_DRAW;
    }

    private void handleBracketPress() {
        BracketType type = controller.getTool().bracketType();
        if (type == null) {
            return;
        }
        bracketDraftType = type;
        bracketStartX = pressWorldX;
        bracketStartY = pressWorldY;
        bracketEndX = pressWorldX;
        bracketEndY = pressWorldY;
        dragMode = DragMode.BRACKET_DRAW;
    }

    private void handleTextPress() {
        TextInputDialog dialog = new TextInputDialog("n");
        dialog.setTitle("Text annotation");
        dialog.setHeaderText("Add text to the drawing");
        dialog.setContentText("Text:");
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(text -> {
            if (!text.isBlank()) {
                controller.addText(text, pressWorldX, pressWorldY);
            }
        });
    }

    private void handleErase() {
        ChemObject pick = pick(pressWorldX, pressWorldY);
        if (pick != null) {
            controller.deleteObject(pick);
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        double worldX = toWorldX(event.getX());
        double worldY = toWorldY(event.getY());
        mouseWorldX = worldX;
        mouseWorldY = worldY;
        if (Math.hypot(event.getX() - pressScreenX, event.getY() - pressScreenY) > 3) {
            moved = true;
        }
        switch (dragMode) {
            case PAN -> {
                offsetX += event.getX() - pressScreenX;
                offsetY += event.getY() - pressScreenY;
                pressScreenX = event.getX();
                pressScreenY = event.getY();
                redraw();
                viewChanged.run();
            }
            case MOVE_SELECTION -> {
                if (!moved) {
                    return;
                }
                if (!selectionMoveStarted) {
                    controller.beginEdit("Move selection");
                    selectionMoveStarted = true;
                }
                double dx = worldX - lastWorldX;
                double dy = worldY - lastWorldY;
                controller.getDocument().translateSelected(dx, dy);
                redraw();
            }
            case MARQUEE -> {
                marquee = normalizedRectangle(pressWorldX, pressWorldY, worldX, worldY);
                redraw();
            }
            case CHAIN_DRAW -> {
                if (!moved || chainStartVertex == null) {
                    return;
                }
                if (atomPressTarget != null) {
                    double displacement = Math.hypot(
                            worldX - atomPressTarget.getX(),
                            worldY - atomPressTarget.getY());
                    if (HexGrid.isVertex(atomPressTarget.getX(),
                            atomPressTarget.getY(), 0.25)
                            && displacement > HexGrid.EDGE
                            * controller.getChainThresholdUnits()) {
                        HexGrid.Vertex target = controller.snapToVertex(worldX, worldY);
                        chainPreview = HexGrid.path(chainStartVertex, target);
                        if (chainPreview.isEmpty()) {
                            chainPreview = List.of(chainStartVertex);
                        }
                        if (chainPreview.size() > 2) {
                            chainExtensionActive = true;
                            redraw();
                            return;
                        }
                    }
                    chainExtensionActive = false;
                    if (freeBondDrag) {
                        double[] endpoint = controller.freeBondEndpoint(
                                atomPressTarget, worldX, worldY);
                        if (endpoint != null) {
                            freeBondX = endpoint[0];
                            freeBondY = endpoint[1];
                        }
                        chainPreview = List.of(chainStartVertex);
                        redraw();
                        return;
                    }
                    HexGrid.Vertex neighbor = HexGrid.neighborToward(
                            chainStartVertex, worldX, worldY);
                    HexGrid.Vertex target = neighbor == null
                            ? null : HexGrid.canonical(neighbor);
                    chainPreview = target == null
                            ? List.of(chainStartVertex)
                            : List.of(chainStartVertex, target);
                } else {
                    HexGrid.Vertex target = controller.snapToVertex(worldX, worldY);
                    chainPreview = HexGrid.path(chainStartVertex, target);
                    if (chainPreview.isEmpty()) {
                        chainPreview = List.of(chainStartVertex);
                    }
                }
                redraw();
            }
            case BOND_DRAW -> {
                if (freeBondTool && bondDownAtom != null) {
                    double[] endpoint = controller.freeBondEndpoint(
                            bondDownAtom, worldX, worldY);
                    if (endpoint != null) {
                        freeBondX = endpoint[0];
                        freeBondY = endpoint[1];
                    }
                    redraw();
                    return;
                }
                HexGrid.Vertex start = pendingBondVertex != null
                        ? pendingBondVertex : bondDownVertex;
                if (start != null) {
                    bondTargetVertex = chooseBondTarget(start, worldX, worldY);
                    bondTargetAtom = nearestAtom(worldX, worldY, 20);
                }
                redraw();
            }
            case ARROW_DRAW -> {
                if (arrowRawStart != null) {
                    arrowDraftEnd = snapArrowPoint(
                            new Point2D(worldX, worldY), arrowDraftStart, false);
                    arrowDraftStart = snapArrowPoint(arrowRawStart, arrowDraftEnd, true);
                    arrowDraftEnd = snapArrowPoint(
                            new Point2D(worldX, worldY), arrowDraftStart, false);
                    if (arrowDraftType == ArrowType.STRAIGHT && event.isShiftDown()) {
                        arrowDraftEnd = new Point2D(
                                arrowDraftEnd.getX(), arrowDraftStart.getY());
                    }
                }
                redraw();
            }
            case BRACKET_DRAW -> {
                bracketEndX = worldX;
                bracketEndY = worldY;
                redraw();
            }
            case HANDLE -> {
                if (!moved) {
                    return;
                }
                if (!handleEditing) {
                    controller.beginEdit("Adjust annotation");
                    handleEditing = true;
                }
                dragHandle(worldX, worldY, event.isShiftDown());
                redraw();
            }
            case NONE -> {
            }
        }
        lastWorldX = worldX;
        lastWorldY = worldY;
        updateStatus(worldX, worldY);
    }

    private void handleMouseReleased(MouseEvent event) {
        double worldX = toWorldX(event.getX());
        double worldY = toWorldY(event.getY());
        switch (dragMode) {
            case PAN -> setCursor(controller.getTool() == Tool.PAN ? Cursor.OPEN_HAND : Cursor.DEFAULT);
            case MOVE_SELECTION -> {
                if (selectionMoveStarted) {
                    controller.endEdit();
                }
            }
            case MARQUEE -> {
                if (moved && marquee != null) {
                    applyMarquee(event.isShiftDown());
                }
                marquee = null;
                redraw();
            }
            case CHAIN_DRAW -> finishChain();
            case BOND_DRAW -> finishBond(worldX, worldY, moved);
            case ARROW_DRAW -> finishArrow(worldX, worldY, event.isShiftDown());
            case BRACKET_DRAW -> finishBracket(worldX, worldY);
            case HANDLE -> {
                if (handleEditing) {
                    controller.endEdit();
                }
                activeHandle = null;
            }
            case NONE -> {
            }
        }
        dragMode = DragMode.NONE;
        selectionMoveStarted = false;
        moved = false;
        atomPressTarget = null;
        chainStartVertex = null;
        chainPreview = List.of();
        freeBondDrag = false;
        freeBondTool = false;
        chainExtensionActive = false;
        freeBondX = Double.NaN;
        freeBondY = Double.NaN;
        bondDownAtom = null;
        bondDownVertex = null;
        bondTargetVertex = null;
        bondTargetAtom = null;
        if (controller.getTool() == Tool.PAN) {
            setCursor(Cursor.OPEN_HAND);
        } else {
            updateCursor();
        }
        redraw();
    }

    private void handleMouseMoved(MouseEvent event) {
        mouseWorldX = toWorldX(event.getX());
        mouseWorldY = toWorldY(event.getY());
        hovered = pick(mouseWorldX, mouseWorldY);
        updateStatus(mouseWorldX, mouseWorldY);
        if (pendingBond != null || dragMode == DragMode.BOND_DRAW
                || controller.getTool() == Tool.ATOM) {
            redraw();
        }
    }

    private void updateStatus(double worldX, double worldY) {
        String description;
        if (hovered != null) {
            description = hovered.typeName();
        } else {
            description = controller.getTool().hint();
        }
        status.accept(String.format("%s   •   x: %.0f  y: %.0f", description, worldX, worldY));
    }

    private void handleScroll(ScrollEvent event) {
        double factor = Math.pow(1.0018, event.getDeltaY());
        zoomAt(event.getX(), event.getY(), zoom * factor);
        event.consume();
    }

    public void zoomAt(double screenX, double screenY, double targetZoom) {
        double newZoom = Geometry.clamp(targetZoom, 0.2, 6.0);
        if (Math.abs(newZoom - zoom) < 1e-6) {
            return;
        }
        double wx = (screenX - offsetX) / zoom;
        double wy = (screenY - offsetY) / zoom;
        zoom = newZoom;
        offsetX = screenX - wx * zoom;
        offsetY = screenY - wy * zoom;
        redraw();
        viewChanged.run();
    }

    public void zoomBy(double factor) {
        zoomAt(getWidth() / 2, getHeight() / 2, zoom * factor);
    }

    public void setZoomOne() {
        zoomAt(getWidth() / 2, getHeight() / 2, 1.0);
    }

    public void fitToContent() {
        Bounds bounds = controller.getDocument().bounds();
        if (bounds == null) {
            zoom = 1;
            offsetX = 0;
            offsetY = 0;
        } else {
            double margin = 90;
            double bw = Math.max(80, bounds.width());
            double bh = Math.max(80, bounds.height());
            double z = Math.min((getWidth() - margin * 2) / bw, (getHeight() - margin * 2) / bh);
            zoom = Geometry.clamp(z, 0.2, 3.5);
            offsetX = getWidth() / 2 - bounds.centerX() * zoom;
            offsetY = getHeight() / 2 - bounds.centerY() * zoom;
        }
        redraw();
        viewChanged.run();
    }

    public void resetView() {
        zoom = 1;
        offsetX = 0;
        offsetY = 0;
        Bounds bounds = controller.getDocument().bounds();
        if (bounds != null) {
            offsetX = getWidth() / 2 - bounds.centerX();
            offsetY = getHeight() / 2 - bounds.centerY();
        }
        redraw();
        viewChanged.run();
    }

    public ViewState getViewState() {
        return new ViewState(zoom, offsetX, offsetY, controller.isShowGrid(),
                controller.isShowCarbonLabels(), controller.isSnapToGrid());
    }

    public void applyViewState(ViewState state) {
        zoom = Geometry.clamp(state.zoom(), 0.2, 6);
        offsetX = state.offsetX();
        offsetY = state.offsetY();
        redraw();
        viewChanged.run();
    }

    public void setSpaceDown(boolean spaceDown) {
        this.spaceDown = spaceDown;
        if (dragMode == DragMode.NONE) {
            setCursor(spaceDown ? Cursor.OPEN_HAND : cursorForTool());
        }
    }

    /** Clears pointer-only affordances before taking a documentation screenshot. */
    public void hidePointerPreview() {
        mouseInside = false;
        redraw();
    }

    public void cancelInteraction() {
        pendingBond = null;
        pendingBondVertex = null;
        bondDownAtom = null;
        bondDownVertex = null;
        bondTargetAtom = null;
        bondTargetVertex = null;
        atomPressTarget = null;
        chainStartVertex = null;
        chainPreview = List.of();
        dragMode = DragMode.NONE;
        marquee = null;
        selectionMoveStarted = false;
        handleEditing = false;
        controller.endEdit();
        redraw();
        updateCursor();
    }

    /**
     * Clicking with the atom tool places one atom. Dragging follows the
     * honeycomb and expands the gesture into an all-single-bond alkane chain,
     * with the currently selected element at the start.
     */
    private void finishChain() {
        boolean dragged = moved;
        if (atomPressTarget != null) {
            if (dragged && chainExtensionActive && chainPreview.size() > 2) {
                controller.buildChainFromExisting(atomPressTarget, chainPreview);
                return;
            }
            if (freeBondDrag) {
                if (dragged && !Double.isNaN(freeBondX) && !Double.isNaN(freeBondY)) {
                    Bond bond = controller.drawFreeBondFromAtom(atomPressTarget,
                            freeBondX, freeBondY, BondType.SINGLE);
                    if (bond == null) {
                        toast.accept("Drag farther to rotate a new bond around this atom.");
                    }
                } else {
                    controller.setAtomElement(atomPressTarget, controller.getActiveElement());
                    controller.selectOnly(atomPressTarget);
                }
                return;
            }
            if (chainStartVertex == null) {
                if (!dragged) {
                    controller.setAtomElement(atomPressTarget, controller.getActiveElement());
                    controller.selectOnly(atomPressTarget);
                } else {
                    toast.accept("Place atoms on hexagonal-grid vertices before drawing bonds.");
                }
                return;
            }
            if (dragged && chainPreview.size() == 2) {
                Bond bond = controller.drawSingleBondFromAtom(
                        atomPressTarget, chainPreview.get(1), BondType.SINGLE);
                if (bond == null) {
                    toast.accept("A bond must connect to an adjacent hex-grid vertex.");
                }
            } else {
                controller.setAtomElement(atomPressTarget, controller.getActiveElement());
                controller.selectOnly(atomPressTarget);
            }
            return;
        }
        if (chainStartVertex == null) {
            return;
        }
        if (dragged && chainPreview.size() > 1) {
            controller.buildAlkaneChain(chainPreview);
        } else {
            controller.addAtomAt(chainStartVertex.x(), chainStartVertex.y());
        }
    }

    /**
     * Bond gestures are locked to the lattice. Click-click and drag both produce
     * exactly one edge; growing a longer chain is the atom tool's job.
     */
    private void finishBond(double worldX, double worldY, boolean dragged) {
        if (pendingBond != null && pendingFreeBond) {
            Bond free = controller.drawFreeBondFromAtom(pendingBond,
                    worldX, worldY, controller.getActiveBondType());
            pendingBond = null;
            pendingBondVertex = null;
            pendingFreeBond = false;
            if (free == null) {
                toast.accept("Click farther from the atom to set the bond angle.");
            }
            return;
        }
        if (freeBondTool) {
            if (dragged && bondDownAtom != null
                    && !Double.isNaN(freeBondX) && !Double.isNaN(freeBondY)) {
                Bond free = controller.drawFreeBondFromAtom(bondDownAtom,
                        freeBondX, freeBondY, controller.getActiveBondType());
                if (free == null) {
                    toast.accept("Drag farther to rotate a new bond around this atom.");
                }
                return;
            }
            freeBondTool = false;
        }
        HexGrid.Vertex startVertex = pendingBondVertex != null ? pendingBondVertex : bondDownVertex;
        Atom startAtom = pendingBond != null ? pendingBond : bondDownAtom;
        if (startVertex == null) {
            return;
        }
        if (dragged && startAtom == null
                && controller.getBondAngleMode() != BondAngleMode.HEX) {
            Atom created = controller.addAtom("C", startVertex.x(), startVertex.y());
            if (created != null) {
                controller.drawFreeBondFromAtom(created, worldX, worldY,
                        controller.getActiveBondType());
            }
            pendingBond = null;
            pendingBondVertex = null;
            pendingFreeBond = false;
            return;
        }

        if (!dragged && pendingBond == null) {
            if (startAtom == null) {
                startAtom = controller.addAtom("C", startVertex.x(), startVertex.y());
            } else {
                controller.selectOnly(startAtom);
            }
            if (startAtom == null) {
                return;
            }
            pendingBond = startAtom;
            pendingBondVertex = startVertex;
            pendingFreeBond = controller.getBondAngleMode() != BondAngleMode.HEX
                    || isFreeBondSource(startAtom);
            toast.accept(pendingFreeBond
                    ? "Free bond start placed. Click anywhere to set its angle."
                    : "Bond start placed. Click an adjacent hex vertex, or drag to the next vertex.");
            return;
        }

        Atom releaseAtom = nearestAtom(worldX, worldY, 22);
        if (releaseAtom != null && releaseAtom == startAtom) {
            pendingBond = null;
            pendingBondVertex = null;
            toast.accept("Bond cancelled.");
            return;
        }

        HexGrid.Vertex targetVertex;
        Atom targetAtom = null;
        if (releaseAtom != null) {
            if (!HexGrid.isVertex(releaseAtom.getX(), releaseAtom.getY(), 1.0)) {
                toast.accept("That atom is not on the hexagonal grid.");
                pendingBond = null;
                pendingBondVertex = null;
                return;
            }
            HexGrid.Vertex snapped = controller.snapToVertex(
                    releaseAtom.getX(), releaseAtom.getY());
            if (!HexGrid.isEdge(startVertex, snapped)) {
                toast.accept("Bonds must connect adjacent hex vertices (one edge apart).");
                pendingBond = null;
                pendingBondVertex = null;
                return;
            }
            targetVertex = snapped;
            targetAtom = releaseAtom;
        } else {
            targetVertex = chooseBondTarget(startVertex, worldX, worldY);
        }
        if (targetVertex == null || targetVertex.distanceTo(startVertex) < 1) {
            pendingBond = null;
            pendingBondVertex = null;
            return;
        }
        Bond bond = controller.createGridBond(startAtom, startVertex, targetAtom, targetVertex,
                controller.getActiveBondType());
        if (bond == null) {
            toast.accept("A bond must connect two adjacent hexagonal-grid vertices.");
        }
        pendingBond = null;
        pendingBondVertex = null;
    }

    /**
     * Single-tool click behaviour: an existing single bond cycles
     * single → triple → single, while choosing any other bond representation
     * applies that representation directly. With a non-single tool active, the
     * clicked bond simply becomes that type.
     */
    private BondType clickedBondType(BondType current) {
        BondType active = controller.getActiveBondType();
        if (active != BondType.SINGLE) {
            return active;
        }
        if (current == BondType.SINGLE) {
            return BondType.TRIPLE;
        }
        if (current == BondType.TRIPLE) {
            return BondType.SINGLE;
        }
        return BondType.SINGLE;
    }

    private HexGrid.Vertex chooseBondTarget(HexGrid.Vertex start, double worldX, double worldY) {
        Atom near = nearestAtom(worldX, worldY, 22);
        if (near != null && HexGrid.isVertex(near.getX(), near.getY(), 1.0)) {
            HexGrid.Vertex vertex = controller.snapToVertex(near.getX(), near.getY());
            if (HexGrid.isEdge(start, vertex)) {
                return vertex;
            }
        }
        return HexGrid.neighborToward(start, worldX, worldY);
    }

    private void finishArrow(double worldX, double worldY, boolean shiftDown) {
        if (arrowDraftStart == null) {
            return;
        }
        Point2D rawStart = arrowRawStart != null ? arrowRawStart : arrowDraftStart;
        Point2D rawEnd = new Point2D(worldX, worldY);
        Point2D end = snapArrowPoint(rawEnd, arrowDraftStart, false);
        Point2D start = snapArrowPoint(rawStart, end, true);
        end = snapArrowPoint(rawEnd, start, false);
        if (arrowDraftType == ArrowType.STRAIGHT && shiftDown) {
            end = new Point2D(end.getX(), start.getY());
        }
        if (start.distance(end) < 4) {
            double dx = rawEnd.getX() - start.getX();
            double dy = rawEnd.getY() - start.getY();
            double length = Math.hypot(dx, dy);
            if (length < 0.1) {
                dx = 1;
                dy = 0;
                length = 1;
            }
            end = start.add(dx / length * 60, dy / length * 60);
            if (arrowDraftType == ArrowType.STRAIGHT && shiftDown) {
                end = new Point2D(end.getX(), start.getY());
            }
        }
        Arrow arrow;
        Bond startBond = bondAtMidpoint(start, 1.0);
        Atom endAtom = nearestAtom(rawEnd.getX(), rawEnd.getY(), 20);
        if (arrowDraftType != ArrowType.STRAIGHT
                && startBond != null && endAtom != null) {
            Point2D near = nearestBondEnd(startBond, endAtom.getX(), endAtom.getY());
            double topBoundary = ChemRenderer.labelBoundaryDistance(
                    endAtom, controller.getDocument(),
                    controller.isShowCarbonLabels(), -Math.PI / 2);
            Point2D hookEnd = new Point2D(endAtom.getX(),
                    endAtom.getY() - Math.max(10, topBoundary + 4));
            double height = Math.max(42,
                    Math.min(95, start.distance(hookEnd) * 0.9));
            // Rise above the bond end nearest the atom, then drop vertically
            // onto the top of the element label.
            double cx = near.getX();
            double cy = near.getY() - height;
            arrow = controller.addArrow(arrowDraftType,
                    start.getX(), start.getY(), cx, cy,
                    hookEnd.getX(), hookEnd.getY());
        } else {
            arrow = controller.addArrow(arrowDraftType,
                    start.getX(), start.getY(), end.getX(), end.getY());
        }
        if (arrow != null) {
            controller.selectOnly(arrow);
        }
        arrowRawStart = null;
        arrowDraftStart = null;
        arrowDraftEnd = null;
    }

    private Bond bondAtMidpoint(Point2D point, double tolerance) {
        for (Bond bond : controller.getDocument().bonds()) {
            double midX = (bond.getFrom().getX() + bond.getTo().getX()) / 2;
            double midY = (bond.getFrom().getY() + bond.getTo().getY()) / 2;
            if (Math.hypot(point.getX() - midX, point.getY() - midY) <= tolerance) {
                return bond;
            }
        }
        return null;
    }

    private Point2D nearestBondEnd(Bond bond, double x, double y) {
        Point2D start = new Point2D(bond.getFrom().getX(), bond.getFrom().getY());
        Point2D end = new Point2D(bond.getTo().getX(), bond.getTo().getY());
        return start.distance(x, y) <= end.distance(x, y) ? start : end;
    }

    private void finishBracket(double worldX, double worldY) {
        if (dragMode != DragMode.BRACKET_DRAW || bracketDraftType == null) {
            return;
        }
        double w = Math.abs(worldX - bracketStartX);
        double h = Math.abs(worldY - bracketStartY);
        if (w > 18 && h > 18) {
            controller.addBracketFrame(bracketDraftType,
                    Math.min(bracketStartX, worldX), Math.min(bracketStartY, worldY),
                    w, h, "n");
        } else {
            controller.addBracket(bracketDraftType, "n", pressWorldX, pressWorldY);
        }
        bracketDraftType = null;
    }

    private void dragHandle(double worldX, double worldY, boolean shiftDown) {
        if (activeHandle == null) {
            return;
        }
        if (activeHandle.target() instanceof Arrow arrow) {
            switch (activeHandle.kind()) {
                case ARROW_START -> {
                    Point2D snapped = snapArrowPoint(new Point2D(worldX, worldY),
                            new Point2D(arrow.getX2(), arrow.getY2()), true);
                    if (arrow.getType() == ArrowType.STRAIGHT && shiftDown) {
                        snapped = new Point2D(snapped.getX(), arrow.getY2());
                    }
                    arrow.setStart(snapped.getX(), snapped.getY());
                }
                case ARROW_END -> {
                    Point2D snapped = snapArrowPoint(new Point2D(worldX, worldY),
                            new Point2D(arrow.getX1(), arrow.getY1()), false);
                    if (arrow.getType() == ArrowType.STRAIGHT && shiftDown) {
                        snapped = new Point2D(snapped.getX(), arrow.getY1());
                    }
                    arrow.setEnd(snapped.getX(), snapped.getY());
                }
                case ARROW_CONTROL -> arrow.setControl(worldX, worldY);
                default -> {
                }
            }
        } else if (activeHandle.target() instanceof Bracket bracket) {
            double left = handleBracketX;
            double top = handleBracketY;
            double right = handleBracketX + handleBracketW;
            double bottom = handleBracketY + handleBracketH;
            switch (activeHandle.kind()) {
                case BRACKET_NW -> {
                    left = worldX;
                    top = worldY;
                }
                case BRACKET_N -> top = worldY;
                case BRACKET_NE -> {
                    right = worldX;
                    top = worldY;
                }
                case BRACKET_E -> right = worldX;
                case BRACKET_SE -> {
                    right = worldX;
                    bottom = worldY;
                }
                case BRACKET_S -> bottom = worldY;
                case BRACKET_SW -> {
                    left = worldX;
                    bottom = worldY;
                }
                case BRACKET_W -> left = worldX;
                default -> {
                }
            }
            double x = Math.min(left, right);
            double y = Math.min(top, bottom);
            bracket.setFrame(x, y, Math.max(24, Math.abs(right - left)),
                    Math.max(24, Math.abs(bottom - top)));
        }
    }

    private void applyMarquee(boolean shift) {
        Set<ChemObject> hits = new LinkedHashSet<>();
        for (ChemObject object : controller.getDocument().objects()) {
            if (marquee.intersects(object.bounds().minX, object.bounds().minY,
                    Math.max(1, object.bounds().width()), Math.max(1, object.bounds().height()))) {
                hits.add(object);
            }
        }
        if (!shift) {
            controller.getDocument().clearSelection();
        }
        for (ChemObject object : hits) {
            object.setSelected(true);
        }
        controller.fireChanged();
    }

    private void handleDoubleClick(MouseEvent event) {
        double x = toWorldX(event.getX());
        double y = toWorldY(event.getY());
        ChemObject pick = pick(x, y);
        if (pick instanceof TextLabel label) {
            editTextLabel(label);
        } else if (pick instanceof Atom) {
            focusInspector.run();
        }
    }

    private void editTextLabel(TextLabel label) {
        TextInputDialog dialog = new TextInputDialog(label.getText());
        dialog.setTitle("Edit text");
        dialog.setHeaderText("Edit text annotation");
        dialog.setContentText("Text:");
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(text -> controller.perform("Edit text",
                () -> label.setText(text)));
    }

    private SelectionHandles.Handle hitHandle(double x, double y) {
        double tolerance = 9 / zoom;
        SelectionHandles.Handle best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SelectionHandles.Handle handle : SelectionHandles.forDocument(controller.getDocument())) {
            double distance = Math.hypot(handle.x() - x, handle.y() - y);
            if (distance <= tolerance && distance < bestDistance) {
                best = handle;
                bestDistance = distance;
            }
        }
        return best;
    }

    private ChemObject pick(double x, double y) {
        double tolerance = 2.5 / zoom;
        List<ChemObject> objects = controller.getDocument().objects();
        for (int i = objects.size() - 1; i >= 0; i--) {
            ChemObject object = objects.get(i);
            if ((object instanceof ChargeMark || object instanceof ElectronMark
                    || object instanceof TextLabel || object instanceof Arrow
                    || object instanceof Bracket) && object.hitTest(x, y, tolerance)) {
                return object;
            }
        }
        for (int i = objects.size() - 1; i >= 0; i--) {
            ChemObject object = objects.get(i);
            if (object instanceof Atom atom && atom.hitTest(x, y, tolerance)) {
                return atom;
            }
        }
        for (int i = objects.size() - 1; i >= 0; i--) {
            ChemObject object = objects.get(i);
            if (object instanceof Bond bond && bond.hitTest(x, y, tolerance)) {
                return bond;
            }
        }
        return null;
    }

    private Atom nearestAtom(double x, double y, double maxDistance) {
        Atom best = null;
        double bestDistance = maxDistance;
        for (Atom atom : controller.getDocument().atoms()) {
            double distance = Math.hypot(atom.getX() - x, atom.getY() - y);
            if (distance < bestDistance) {
                best = atom;
                bestDistance = distance;
            }
        }
        return best;
    }

    private ElectronMark nearestElectron(double x, double y, double maxDistance) {
        ElectronMark best = null;
        double bestDistance = maxDistance;
        for (ElectronMark mark : controller.getDocument().electronMarks()) {
            double distance = Math.hypot(mark.getX() - x, mark.getY() - y);
            if (distance < bestDistance) {
                best = mark;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Bond nearestBond(double x, double y, double maxDistance) {
        Bond best = null;
        double bestDistance = maxDistance;
        for (Bond bond : controller.getDocument().bonds()) {
            double distance = Geometry.distanceToSegment(x, y,
                    bond.getFrom().getX(), bond.getFrom().getY(),
                    bond.getTo().getX(), bond.getTo().getY());
            if (distance < bestDistance) {
                best = bond;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Arrow endpoints snap to lone pairs, to the middle of bonds, or to a point
     * just outside an atom's element label. Element snapping always stops clear
     * of the label so the arrowhead never touches the atom glyph.
     */
    private Point2D snapArrowPoint(Point2D point, Point2D otherEnd, boolean start) {
        ElectronMark electron = nearestElectron(point.getX(), point.getY(), 18);
        if (electron != null) {
            return new Point2D(electron.getX(), electron.getY());
        }
        Bond midpointBond = nearestBond(point.getX(), point.getY(), 10);
        if (midpointBond != null) {
            Point2D midpoint = new Point2D(
                    (midpointBond.getFrom().getX() + midpointBond.getTo().getX()) / 2,
                    (midpointBond.getFrom().getY() + midpointBond.getTo().getY()) / 2);
            if (point.distance(midpoint) <= 10) {
                return midpoint;
            }
        }
        Atom atom = nearestAtom(point.getX(), point.getY(), 20);
        if (atom != null) {
            if (otherEnd != null) {
                double dx = otherEnd.getX() - atom.getX();
                double dy = otherEnd.getY() - atom.getY();
                double distance = Math.hypot(dx, dy);
                double angle = Math.atan2(dy, dx);
                double boundary = ChemRenderer.labelBoundaryDistance(
                        atom, controller.getDocument(),
                        controller.isShowCarbonLabels(), angle);
                double offset = Math.max(10, boundary + 4);
                if (distance > offset + 1) {
                    return new Point2D(atom.getX() + dx / distance * offset,
                            atom.getY() + dy / distance * offset);
                }
            }
            return new Point2D(atom.getX(), atom.getY());
        }
        Bond bond = nearestBond(point.getX(), point.getY(), 10);
        if (bond != null) {
            return new Point2D(
                    (bond.getFrom().getX() + bond.getTo().getX()) / 2,
                    (bond.getFrom().getY() + bond.getTo().getY()) / 2);
        }
        return point;
    }

    private Point2D closestPointOnSegment(double px, double py,
                                          double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return new Point2D(x1, y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return new Point2D(x1 + t * dx, y1 + t * dy);
    }

    private void showContextMenu(MouseEvent event) {
        if (dragMode != DragMode.NONE) {
            return;
        }
        ChemObject pick = pick(toWorldX(event.getX()), toWorldY(event.getY()));
        if (controller.getTool() != Tool.SELECT) {
            // Outside selection mode, right-click is the quick destructive action.
            pendingBond = null;
            pendingBondVertex = null;
            pendingFreeBond = false;
            if (pick != null) {
                String name = pick.typeName();
                controller.deleteObject(pick);
                toast.accept("Deleted " + name + ".");
            }
            return;
        }
        if (pick != null && !pick.isSelected()) {
            controller.selectOnly(pick);
        }
        ContextMenu menu = new ContextMenu();

        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(e -> {
            if (controller.copySelection()) {
                toast.accept("Copied selection.");
            }
        });
        MenuItem cut = new MenuItem("Cut");
        cut.setOnAction(e -> {
            if (controller.cutSelection()) {
                toast.accept("Cut selection.");
            }
        });
        MenuItem paste = new MenuItem("Paste");
        paste.setOnAction(e -> controller.pasteClipboard());
        MenuItem duplicate = new MenuItem("Duplicate");
        duplicate.setOnAction(e -> controller.duplicateSelection());
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> controller.deleteSelection());
        menu.getItems().addAll(cut, copy, paste, duplicate, delete);

        if (pick != null) {
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem structure = new MenuItem("Select Connected Structure");
            structure.setOnAction(e -> controller.selectStructure(pick));
            menu.getItems().add(structure);

            MenuItem edit;
            if (pick instanceof TextLabel label) {
                edit = new MenuItem("Edit Text…");
                edit.setOnAction(e -> editTextLabel(label));
            } else {
                edit = new MenuItem("Show Properties");
                edit.setOnAction(e -> focusInspector.run());
            }
            menu.getItems().add(edit);

            Menu rotate = new Menu("Rotate");
            MenuItem left = new MenuItem("15° Counter-clockwise");
            left.setOnAction(e -> controller.rotateSelection(-15));
            MenuItem right = new MenuItem("15° Clockwise");
            right.setOnAction(e -> controller.rotateSelection(15));
            rotate.getItems().addAll(left, right);
            menu.getItems().add(rotate);

            Menu bracket = new Menu("Enclose in Brackets");
            for (BracketType type : BracketType.values()) {
                MenuItem item = new MenuItem(type.toString());
                item.setOnAction(e -> controller.addBracket(type, "n", 400, 300));
                bracket.getItems().add(item);
            }
            menu.getItems().add(bracket);

            MenuItem front = new MenuItem("Bring to Front");
            front.setOnAction(e -> controller.moveToFront(pick));
            menu.getItems().add(front);
        } else {
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem selectAll = new MenuItem("Select All");
            selectAll.setOnAction(e -> controller.selectAll());
            menu.getItems().add(selectAll);
            MenuItem fit = new MenuItem("Fit Drawing to Window");
            fit.setOnAction(e -> fitToContent());
            menu.getItems().add(fit);
        }
        menu.show(this, event.getScreenX(), event.getScreenY());
    }

    private void updateCursor() {
        if (spaceDown) {
            setCursor(Cursor.OPEN_HAND);
            return;
        }
        setCursor(cursorForTool());
    }

    private Cursor cursorForTool() {
        return switch (controller.getTool()) {
            case SELECT -> Cursor.DEFAULT;
            case PAN -> Cursor.OPEN_HAND;
            case ERASE -> Cursor.HAND;
            case TEXT, ARROW_STRAIGHT, ARROW_CURVED, ARROW_FISHHOOK -> Cursor.CROSSHAIR;
            default -> Cursor.CROSSHAIR;
        };
    }

    private double toWorldX(double screenX) {
        return (screenX - offsetX) / zoom;
    }

    private double toWorldY(double screenY) {
        return (screenY - offsetY) / zoom;
    }

    private static Rectangle2D normalizedRectangle(double x1, double y1, double x2, double y2) {
        return new Rectangle2D(Math.min(x1, x2), Math.min(y1, y2),
                Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
}
