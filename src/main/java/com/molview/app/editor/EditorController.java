package com.molview.app.editor;

import com.molview.app.model.Arrow;
import com.molview.app.model.ArrowType;
import com.molview.app.model.Atom;
import com.molview.app.model.AtomKind;
import com.molview.app.model.Bond;
import com.molview.app.model.BondAngleMode;
import com.molview.app.model.BondType;
import com.molview.app.model.Bounds;
import com.molview.app.model.Bracket;
import com.molview.app.model.BracketLabelPosition;
import com.molview.app.model.BracketType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.Geometry;
import com.molview.app.model.HexGrid;
import com.molview.app.model.TextLabel;
import com.molview.app.model.Tool;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central editing state: document, selection, active tools, clipboard and undo.
 * Contains no JavaFX code, which keeps the editing core testable.
 */
public final class EditorController {
    private static final int MAX_UNDO = 120;

    private ChemDocument document;
    private final Deque<ChemDocument> undoStack = new ArrayDeque<>();
    private final Deque<ChemDocument> redoStack = new ArrayDeque<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> dirtyListeners = new CopyOnWriteArrayList<>();

    private Tool tool = Tool.ATOM;
    private String activeElement = "C";
    private AtomKind activeAtomKind = AtomKind.ELEMENT;
    private BondType activeBondType = BondType.SINGLE;
    private String xLabel = "X";
    private String rLabel = "R";
    private boolean showGrid = true;
    private boolean showCarbonLabels = false;
    /** Honeycomb snapping is a core invariant of the sketcher. */
    private boolean snapToGrid = true;
    private BondAngleMode bondAngleMode = BondAngleMode.SNAP_60;
    private int customBondAngle = 45;
    private double chainThresholdUnits = 1.5;
    private boolean showDeltaRings = true;
    private int electronDistance = 30;
    private int deltaLabelDistance = 28;
    private boolean dirty;
    private boolean editInProgress;
    private Path currentFile;
    private ChemDocument clipboard;
    private int pasteGeneration;

    public EditorController() {
        this.document = new ChemDocument();
    }

    public ChemDocument getDocument() {
        return document;
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void addDirtyListener(Runnable listener) {
        dirtyListeners.add(listener);
    }

    public void fireChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private void fireDirtyChanged() {
        for (Runnable listener : dirtyListeners) {
            listener.run();
        }
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool == null ? Tool.SELECT : tool;
        fireChanged();
    }

    public String getActiveElement() {
        return activeElement;
    }

    public AtomKind getActiveAtomKind() {
        return activeAtomKind;
    }

    public void setActiveElement(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        if (symbol.equals("R") || symbol.equals("X")) {
            activeAtomKind = symbol.equals("R") ? AtomKind.R_GROUP : AtomKind.X_GROUP;
        } else {
            activeAtomKind = AtomKind.ELEMENT;
        }
        activeElement = symbol;
        tool = Tool.ATOM;
        fireChanged();
    }

    public void setActiveGeneric(AtomKind kind) {
        activeAtomKind = kind;
        activeElement = kind == AtomKind.R_GROUP ? "R" : "X";
        tool = Tool.ATOM;
        fireChanged();
    }

    public BondType getActiveBondType() {
        return activeBondType;
    }

    public void setActiveBondType(BondType type) {
        activeBondType = type == null ? BondType.SINGLE : type;
        tool = Tool.BOND;
        fireChanged();
    }

    public String getXLabel() {
        return xLabel;
    }

    public void setXLabel(String xLabel) {
        this.xLabel = xLabel == null || xLabel.isBlank() ? "X" : xLabel.strip();
        fireChanged();
    }

    public String getRLabel() {
        return rLabel;
    }

    public void setRLabel(String rLabel) {
        this.rLabel = rLabel == null || rLabel.isBlank() ? "R" : rLabel.strip();
        fireChanged();
    }

    public BondAngleMode getBondAngleMode() {
        return bondAngleMode;
    }

    public void setBondAngleMode(BondAngleMode mode) {
        if (mode == null || mode == bondAngleMode) {
            return;
        }
        bondAngleMode = mode;
        fireChanged();
    }

    public int getCustomBondAngle() {
        return customBondAngle;
    }

    public void setCustomBondAngle(int degrees) {
        int clamped = Math.max(5, Math.min(180, degrees));
        if (clamped == customBondAngle) {
            return;
        }
        customBondAngle = clamped;
        fireChanged();
    }

    public double getChainThresholdUnits() {
        return chainThresholdUnits;
    }

    public void setChainThresholdUnits(double units) {
        double clamped = Math.max(0.5, Math.min(5.0, units));
        if (Math.abs(clamped - chainThresholdUnits) < 1e-9) {
            return;
        }
        chainThresholdUnits = clamped;
        fireChanged();
    }

    public boolean isShowDeltaRings() {
        return showDeltaRings;
    }

    public void setShowDeltaRings(boolean showDeltaRings) {
        if (this.showDeltaRings == showDeltaRings) {
            return;
        }
        this.showDeltaRings = showDeltaRings;
        fireChanged();
    }

    public int getElectronDistance() {
        return electronDistance;
    }

    public void setElectronDistance(int distance) {
        int clamped = Math.max(16, Math.min(80, distance));
        if (clamped == electronDistance) {
            return;
        }
        electronDistance = clamped;
        for (Atom atom : document.atoms()) {
            layoutAttachedElectronMarks(atom);
        }
        fireChanged();
    }

    public int getDeltaLabelDistance() {
        return deltaLabelDistance;
    }

    public void setDeltaLabelDistance(int distance) {
        int clamped = Math.max(16, Math.min(80, distance));
        if (clamped == deltaLabelDistance) {
            return;
        }
        deltaLabelDistance = clamped;
        for (Atom atom : document.atoms()) {
            for (ChargeMark mark : document.chargeMarks()) {
                if (mark.getKind().isPartial()
                        && atom.getId().equals(mark.getAttachedAtomId())) {
                    double[] position = chargeLabelPosition(
                            atom, mark.getKind(), mark);
                    mark.setPosition(position[0], position[1]);
                }
            }
        }
        fireChanged();
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        fireChanged();
    }

    public boolean isShowCarbonLabels() {
        return showCarbonLabels;
    }

    public void setShowCarbonLabels(boolean showCarbonLabels) {
        this.showCarbonLabels = showCarbonLabels;
        fireChanged();
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
        fireChanged();
    }

    /** Snaps a world position to the nearest honeycomb vertex. */
    public HexGrid.Vertex snapToVertex(double x, double y) {
        return HexGrid.canonical(HexGrid.nearestVertex(x, y));
    }

    public Atom atomAtVertex(HexGrid.Vertex vertex) {
        if (vertex == null) {
            return null;
        }
        for (Atom atom : document.atoms()) {
            if (Math.hypot(atom.getX() - vertex.x(), atom.getY() - vertex.y()) < 1.0) {
                return atom;
            }
        }
        return null;
    }

    public Path getCurrentFile() {
        return currentFile;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            fireDirtyChanged();
        }
    }

    public void replaceDocument(ChemDocument newDocument, Path file, boolean clean) {
        document = newDocument == null ? new ChemDocument() : newDocument;
        undoStack.clear();
        redoStack.clear();
        clipboard = null;
        currentFile = file;
        dirty = !clean;
        editInProgress = false;
        if (file != null) {
            String name = file.getFileName() == null ? "Untitled" : file.getFileName().toString();
            document.setTitle(stripExtension(name));
        }
        fireChanged();
        fireDirtyChanged();
    }

    public void newDocument() {
        replaceDocument(new ChemDocument(), null, true);
    }

    public void markSaved(Path file) {
        if (file != null) {
            currentFile = file;
            String name = file.getFileName() == null ? "Untitled" : file.getFileName().toString();
            document.setTitle(stripExtension(name));
        }
        dirty = false;
        fireDirtyChanged();
        fireChanged();
    }

    public static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    public void checkpoint(String label) {
        if (editInProgress) {
            return;
        }
        undoStack.push(document.deepCopy());
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        editInProgress = true;
        dirty = true;
        fireDirtyChanged();
    }

    public void beginEdit(String label) {
        checkpoint(label);
    }

    public void endEdit() {
        if (editInProgress) {
            editInProgress = false;
            fireChanged();
        }
    }

    public void perform(String label, Runnable action) {
        checkpoint(label);
        action.run();
        endEdit();
    }

    public void notifyChanged() {
        fireChanged();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(document.deepCopy());
        document = undoStack.pop();
        dirty = true;
        editInProgress = false;
        fireDirtyChanged();
        fireChanged();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(document.deepCopy());
        document = redoStack.pop();
        dirty = true;
        editInProgress = false;
        fireDirtyChanged();
        fireChanged();
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    public List<ChemObject> selectedObjects() {
        return document.selectedObjects();
    }

    public int selectionCount() {
        return document.selectedObjects().size();
    }

    public boolean hasSelection() {
        return !document.selectedObjects().isEmpty();
    }

    public void clearSelection() {
        document.clearSelection();
        fireChanged();
    }

    public void selectOnly(ChemObject object) {
        document.selectOnly(object);
        fireChanged();
    }

    public void toggleSelection(ChemObject object) {
        document.toggleSelection(object);
        fireChanged();
    }

    public void selectAll() {
        document.selectAll();
        fireChanged();
    }

    public void selectStructure(ChemObject seed) {
        document.selectStructure(seed);
        fireChanged();
    }

    public Atom createAtomForActive(double x, double y) {
        HexGrid.Vertex vertex = snapToVertex(x, y);
        return switch (activeAtomKind) {
            case R_GROUP -> Atom.rGroup(rLabel, vertex.x(), vertex.y());
            case X_GROUP -> Atom.xGroup(xLabel, vertex.x(), vertex.y());
            default -> Atom.element(activeElement, vertex.x(), vertex.y());
        };
    }

    private Atom createAtomForActiveExact(double x, double y) {
        return switch (activeAtomKind) {
            case R_GROUP -> Atom.rGroup(rLabel, x, y);
            case X_GROUP -> Atom.xGroup(xLabel, x, y);
            default -> Atom.element(activeElement, x, y);
        };
    }

    public Atom addAtomAt(double x, double y) {
        HexGrid.Vertex vertex = snapToVertex(x, y);
        Atom existing = atomAtVertex(vertex);
        if (existing != null) {
            document.clearSelection();
            existing.setSelected(true);
            fireChanged();
            return existing;
        }
        Atom atom = createAtomForActive(vertex.x(), vertex.y());
        perform("Add atom", () -> {
            document.clearSelection();
            atom.setSelected(true);
            document.add(atom);
        });
        return atom;
    }

    public Atom addAtom(String symbol, double x, double y) {
        AtomKind kind = symbol.equals("R") ? AtomKind.R_GROUP
                : symbol.equals("X") ? AtomKind.X_GROUP : AtomKind.ELEMENT;
        String label = kind == AtomKind.R_GROUP ? rLabel : kind == AtomKind.X_GROUP ? xLabel : null;
        HexGrid.Vertex vertex = snapToVertex(x, y);
        Atom existing = atomAtVertex(vertex);
        if (existing != null) {
            return existing;
        }
        Atom atom = new Atom(symbol, kind, label, vertex.x(), vertex.y());
        perform("Add atom", () -> {
            document.clearSelection();
            atom.setSelected(true);
            document.add(atom);
        });
        return atom;
    }

    /**
     * Expands a drag across the honeycomb into an alkane skeleton. The first
     * atom uses the currently selected element while every following vertex is
     * a carbon. All steps are exactly one hexagon edge.
     */
    public int buildAlkaneChain(List<HexGrid.Vertex> path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        List<HexGrid.Vertex> vertices = canonicalPath(path);
        if (vertices.isEmpty()) {
            return 0;
        }
        List<ChemObject> created = new ArrayList<>();
        perform("Draw alkane chain", () -> {
            document.clearSelection();
            Atom previous = null;
            for (int i = 0; i < vertices.size(); i++) {
                HexGrid.Vertex vertex = vertices.get(i);
                Atom atom = atomAtVertex(vertex);
                if (atom == null) {
                    if (i == 0) {
                        atom = createAtomForActive(vertex.x(), vertex.y());
                    } else {
                        atom = Atom.element("C", vertex.x(), vertex.y());
                    }
                    document.add(atom);
                    created.add(atom);
                }
                if (previous != null && findBond(previous, atom) == null && HexGrid.isEdge(
                        new HexGrid.Vertex(previous.getX(), previous.getY()),
                        new HexGrid.Vertex(atom.getX(), atom.getY()))) {
                    Bond bond = new Bond(previous, atom, BondType.SINGLE);
                    document.add(bond);
                    layoutAttachedCharges(previous);
                    layoutAttachedCharges(atom);
                    layoutAttachedElectronMarks(previous);
                    layoutAttachedElectronMarks(atom);
                    created.add(bond);
                }
                previous = atom;
            }
            document.clearSelection();
        });
        return vertices.size();
    }

    /**
     * Grows an alkane chain from an existing atom. Intermediate vertices are
     * carbon; the selected element is placed at the far end.
     */
    public int buildChainFromExisting(Atom source, List<HexGrid.Vertex> path) {
        if (source == null || path == null || path.size() < 2) {
            return 0;
        }
        if (!HexGrid.isVertex(source.getX(), source.getY(), 0.25)) {
            return 0;
        }
        List<HexGrid.Vertex> vertices = canonicalPath(path);
        HexGrid.Vertex sourceVertex = snapToVertex(source.getX(), source.getY());
        if (vertices.isEmpty()
                || vertices.get(0).distanceTo(sourceVertex) > 0.25) {
            if (vertices.isEmpty()
                    || !HexGrid.isEdge(sourceVertex, vertices.get(0))) {
                return 0;
            }
            vertices.add(0, sourceVertex);
        }
        if (vertices.size() < 2) {
            return 0;
        }
        perform("Draw alkane chain", () -> {
            document.clearSelection();
            Atom previous = source;
            for (int i = 1; i < vertices.size(); i++) {
                HexGrid.Vertex vertex = vertices.get(i);
                Atom atom = atomAtVertex(vertex);
                if (atom == null) {
                    if (i == vertices.size() - 1) {
                        atom = createAtomForActive(vertex.x(), vertex.y());
                    } else {
                        atom = Atom.element("C", vertex.x(), vertex.y());
                    }
                    document.add(atom);
                } else if (i == vertices.size() - 1) {
                    applyActiveAtomTo(atom);
                }
                if (previous != atom && findBond(previous, atom) == null
                        && HexGrid.isEdge(
                        new HexGrid.Vertex(previous.getX(), previous.getY()),
                        new HexGrid.Vertex(atom.getX(), atom.getY()))) {
                    document.add(new Bond(previous, atom, BondType.SINGLE));
                    layoutAttachedCharges(previous);
                    layoutAttachedCharges(atom);
                    layoutAttachedElectronMarks(previous);
                    layoutAttachedElectronMarks(atom);
                }
                previous = atom;
            }
            document.clearSelection();
        });
        return vertices.size();
    }

    private void applyActiveAtomTo(Atom atom) {
        switch (activeAtomKind) {
            case R_GROUP -> {
                atom.setKind(AtomKind.R_GROUP);
                atom.setGenericLabel(rLabel);
            }
            case X_GROUP -> {
                atom.setKind(AtomKind.X_GROUP);
                atom.setGenericLabel(xLabel);
            }
            default -> {
                atom.setKind(AtomKind.ELEMENT);
                atom.setGenericLabel(null);
                atom.setElementSymbol(activeElement);
            }
        }
    }

    private List<HexGrid.Vertex> canonicalPath(List<HexGrid.Vertex> path) {
        List<HexGrid.Vertex> result = new ArrayList<>();
        for (HexGrid.Vertex vertex : path) {
            HexGrid.Vertex canonical = HexGrid.canonical(vertex);
            if (result.isEmpty() || result.get(result.size() - 1).distanceTo(canonical) > 1e-6) {
                result.add(canonical);
            }
        }
        return result;
    }

    /**
     * Creates a bond that is constrained to one honeycomb edge. Missing endpoints
     * become carbon atoms; an existing atom can be used as either endpoint.
     */
    public Bond createGridBond(Atom start, HexGrid.Vertex startVertex,
                               Atom end, HexGrid.Vertex endVertex, BondType type) {
        if (startVertex == null || endVertex == null) {
            return null;
        }
        HexGrid.Vertex fromVertex = HexGrid.canonical(startVertex);
        HexGrid.Vertex toVertex = HexGrid.canonical(endVertex);
        if (!HexGrid.isEdge(fromVertex, toVertex)) {
            return null;
        }
        Atom from = start != null ? start : atomAtVertex(fromVertex);
        Atom to = end != null ? end : atomAtVertex(toVertex);
        if (from != null && Math.hypot(from.getX() - fromVertex.x(),
                from.getY() - fromVertex.y()) > 0.1) {
            return null;
        }
        if (to != null && Math.hypot(to.getX() - toVertex.x(),
                to.getY() - toVertex.y()) > 0.1) {
            return null;
        }
        if (!HexGrid.isEdge(fromVertex, toVertex)) {
            return null;
        }
        if (from == null) {
            from = new Atom("C", AtomKind.ELEMENT, null, fromVertex.x(), fromVertex.y());
        }
        if (to == null) {
            to = new Atom("C", AtomKind.ELEMENT, null, toVertex.x(), toVertex.y());
        }
        if (from == to) {
            return null;
        }
        Bond existing = findBond(from, to);
        if (existing != null) {
            if (existing.getType() != type) {
                perform("Change bond", () -> {
                    existing.setType(type);
                    document.selectOnly(existing);
                });
            }
            return existing;
        }
        Atom actualFrom = from;
        Atom actualTo = to;
        boolean addFrom = !document.objects().contains(actualFrom);
        boolean addTo = !document.objects().contains(actualTo);
        Bond bond = new Bond(actualFrom, actualTo, type == null ? BondType.SINGLE : type);
        perform("Draw bond", () -> {
            document.clearSelection();
            if (addFrom) {
                document.add(actualFrom);
            }
            if (addTo) {
                document.add(actualTo);
            }
            document.add(bond);
            layoutAttachedCharges(actualFrom);
            layoutAttachedCharges(actualTo);
            layoutAttachedElectronMarks(actualFrom);
            layoutAttachedElectronMarks(actualTo);
        });
        return bond;
    }

    /**
     * Draws exactly one single bond outward from an existing atom, placing the
     * selected element at the new endpoint (or reusing an atom already there).
     * Used by the atom tool's click-versus-drag gesture.
     */
    public Bond drawSingleBondFromAtom(Atom source, HexGrid.Vertex targetVertex,
                                       BondType type) {
        if (source == null || targetVertex == null) {
            return null;
        }
        HexGrid.Vertex sourceVertex = snapToVertex(source.getX(), source.getY());
        if (!HexGrid.isVertex(source.getX(), source.getY(), 0.1)
                || !HexGrid.isEdge(sourceVertex, targetVertex)) {
            return null;
        }
        Atom existingTarget = atomAtVertex(targetVertex);
        if (existingTarget != null) {
            return createGridBond(source, sourceVertex, existingTarget, targetVertex,
                    type == null ? BondType.SINGLE : type);
        }
        Atom createdTarget = createAtomForActive(targetVertex.x(), targetVertex.y());
        Bond bond = new Bond(source, createdTarget, type == null ? BondType.SINGLE : type);
        perform("Draw bond", () -> {
            document.clearSelection();
            document.add(createdTarget);
            document.add(bond);
            layoutAttachedCharges(source);
            layoutAttachedCharges(createdTarget);
            layoutAttachedElectronMarks(source);
            layoutAttachedElectronMarks(createdTarget);
        });
        return bond;
    }

    /**
     * One free-angle bond of standard grid length from a terminal atom such as
     * bromine. The endpoint does not snap to a lattice vertex, so the bond can
     * rotate fully around a singly occurring atom.
     */
    /**
     * Endpoint for a free-angle terminal bond. The angle is free unless it comes
     * within 7.5° of a 60° direction, where it softly snaps to that angle.
     */
    public double[] freeBondEndpoint(Atom source, double towardX, double towardY) {
        if (source == null) {
            return null;
        }
        double dx = towardX - source.getX();
        double dy = towardY - source.getY();
        double distance = Math.hypot(dx, dy);
        if (distance < 1) {
            return null;
        }
        double angle = Math.atan2(dy, dx);
        angle = switch (bondAngleMode) {
            case SNAP_60 -> Geometry.snapAngle(angle, Math.PI / 3);
            case CUSTOM -> Geometry.snapAngle(angle, Math.toRadians(customBondAngle));
            case FREE, HEX -> angle;
        };
        return new double[]{
                source.getX() + Math.cos(angle) * HexGrid.EDGE,
                source.getY() + Math.sin(angle) * HexGrid.EDGE
        };
    }

    public Bond drawFreeBondFromAtom(Atom source, double towardX, double towardY,
                                    BondType type) {
        if (source == null) {
            return null;
        }
        double[] endpoint = freeBondEndpoint(source, towardX, towardY);
        if (endpoint == null) {
            return null;
        }
        double ex = endpoint[0];
        double ey = endpoint[1];
        Atom target = null;
        double nearest = 9;
        for (Atom atom : document.atoms()) {
            if (atom == source) {
                continue;
            }
            double d = Math.hypot(atom.getX() - ex, atom.getY() - ey);
            if (d < nearest) {
                nearest = d;
                target = atom;
            }
        }
        if (target != null) {
            Bond existing = findBond(source, target);
            if (existing != null) {
                return existing;
            }
        }
        Atom actualTarget = target != null ? target : createAtomForActiveExact(ex, ey);
        Bond bond = new Bond(source, actualTarget, type == null ? BondType.SINGLE : type);
        boolean addTarget = target == null;
        perform("Draw bond", () -> {
            document.clearSelection();
            if (addTarget) {
                document.add(actualTarget);
            }
            document.add(bond);
            layoutAttachedCharges(source);
            layoutAttachedCharges(actualTarget);
            layoutAttachedElectronMarks(source);
            layoutAttachedElectronMarks(actualTarget);
        });
        return bond;
    }

    public Bond addBond(Atom from, Atom to, BondType type) {
        if (from == null || to == null || from == to) {
            return null;
        }
        HexGrid.Vertex fromVertex = HexGrid.nearestVertex(from.getX(), from.getY());
        HexGrid.Vertex toVertex = HexGrid.nearestVertex(to.getX(), to.getY());
        if (Math.hypot(from.getX() - fromVertex.x(), from.getY() - fromVertex.y()) > 0.1
                || Math.hypot(to.getX() - toVertex.x(), to.getY() - toVertex.y()) > 0.1
                || !HexGrid.isEdge(fromVertex, toVertex)) {
            return null;
        }
        Bond existing = findBond(from, to);
        if (existing != null) {
            if (existing.getType() != type) {
                perform("Change bond", () -> {
                    existing.setType(type);
                    document.selectOnly(existing);
                });
            }
            return existing;
        }
        Bond bond = new Bond(from, to, type);
        perform("Add bond", () -> {
            document.clearSelection();
            document.add(bond);
            layoutAttachedCharges(from);
            layoutAttachedCharges(to);
            layoutAttachedElectronMarks(from);
            layoutAttachedElectronMarks(to);
        });
        return bond;
    }

    /**
     * Backwards-compatible bond helper constrained to a single honeycomb edge.
     * Both coordinates snap to vertices before the bond is accepted.
     */
    public Bond createBondBetween(Atom start, double startX, double startY,
                                  Atom end, double endX, double endY, BondType type) {
        HexGrid.Vertex startVertex = snapToVertex(
                start != null ? start.getX() : startX,
                start != null ? start.getY() : startY);
        HexGrid.Vertex endVertex = snapToVertex(
                end != null ? end.getX() : endX,
                end != null ? end.getY() : endY);
        return createGridBond(start, startVertex, end, endVertex, type);
    }

    public Bracket addBracketFrame(BracketType type, double x, double y, double width, double height,
                                   String label) {
        Bracket bracket = new Bracket(type, x, y, width, height);
        bracket.setLabel(label == null ? "" : label);
        bracket.setLabelPosition(BracketLabelPosition.BOTTOM_RIGHT);
        perform("Add bracket", () -> {
            document.clearSelection();
            bracket.setSelected(true);
            document.add(bracket);
        });
        return bracket;
    }

    public void setArrowType(Arrow arrow, ArrowType type) {
        if (arrow == null || arrow.getType() == type) {
            return;
        }
        perform("Change arrow type", () -> arrow.setType(type));
    }

    public void reverseArrow(Arrow arrow) {
        if (arrow == null) {
            return;
        }
        perform("Reverse arrow", () -> {
            double x1 = arrow.getX1();
            double y1 = arrow.getY1();
            double x2 = arrow.getX2();
            double y2 = arrow.getY2();
            double cx = arrow.getControlX();
            double cy = arrow.getControlY();
            arrow.setPoints(x2, y2, cx, cy, x1, y1);
        });
    }

    public Bond findBond(Atom a, Atom b) {
        for (Bond bond : document.bonds()) {
            if ((bond.getFrom() == a && bond.getTo() == b)
                    || (bond.getFrom() == b && bond.getTo() == a)) {
                return bond;
            }
        }
        return null;
    }

    public ChargeMark addCharge(double x, double y, ChargeKind kind, Atom host) {
        ChargeMark mark;
        if (host != null) {
            double[] position = chargeLabelPosition(host, kind, null);
            mark = new ChargeMark(kind, position[0], position[1]);
            mark.setAttachedAtomId(host.getId());
        } else {
            mark = new ChargeMark(kind, x, y);
        }
        ChargeMark created = mark;
        perform("Add charge", () -> {
            document.clearSelection();
            created.setSelected(true);
            document.add(created);
        });
        return created;
    }

    /**
     * Finds a clear spot for a charge label around its atom. Candidate positions
     * are scored against every bond line, other charge/electron marks and atom
     * labels so the bond stroke does not cut through the charge text.
     */
    private double[] chargeLabelPosition(Atom host, ChargeKind kind, ChargeMark ignore) {
        double halfWidth = kind.isPartial() ? 13 : 8;
        double extent = Math.hypot(halfWidth, 11);
        double[] radii = kind.isPartial()
                ? new double[]{deltaLabelDistance}
                : new double[]{20, 25, 30};
        double bestX = host.getX();
        double bestY = host.getY() - (kind.isPartial() ? 28 : 21);
        double bestScore = -Double.MAX_VALUE;
        for (double radius : radii) {
            for (int step = 0; step < 24; step++) {
                double angle = -Math.PI / 2 + step * Math.PI * 2 / 24;
                double x = host.getX() + Math.cos(angle) * radius;
                double y = host.getY() + Math.sin(angle) * radius;
                double score = chargePositionScore(host, kind, x, y, ignore, extent);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new double[]{bestX, bestY};
    }

    private double chargePositionScore(Atom host, ChargeKind kind,
                                       double x, double y, ChargeMark ignore,
                                       double extent) {
        double score = 0;
        for (Bond bond : document.bonds()) {
            double distance = Geometry.distanceToSegment(x, y,
                    bond.getFrom().getX(), bond.getFrom().getY(),
                    bond.getTo().getX(), bond.getTo().getY());
            double gap = distance - extent - 3;
            score += gap < 0 ? gap * 35 : Math.min(gap, 16) * 0.5;
        }
        for (ChargeMark mark : document.chargeMarks()) {
            if (mark == ignore) {
                continue;
            }
            double separation = kind.isPartial() ? 29 : 22;
            double distance = Math.hypot(mark.getX() - x, mark.getY() - y);
            if (distance < separation) {
                score -= (separation - distance) * 7;
            }
        }
        for (ElectronMark electron : document.electronMarks()) {
            double distance = Math.hypot(electron.getX() - x, electron.getY() - y);
            if (distance < 19) {
                score -= (19 - distance) * 3;
            }
        }
        for (Atom atom : document.atoms()) {
            if (atom == host) {
                continue;
            }
            double distance = Math.hypot(atom.getX() - x, atom.getY() - y);
            if (distance < 22) {
                score -= (22 - distance) * 1.5;
            }
        }
        score += (host.getY() - y) * 0.25;
        score += (x - host.getX()) * 0.04;
        return score;
    }

    private boolean chargePositionIsClear(ChargeMark mark, Atom host) {
        double halfWidth = mark.getKind().isPartial() ? 13 : 8;
        double extent = Math.hypot(halfWidth, 11);
        for (Bond bond : document.bonds()) {
            double distance = Geometry.distanceToSegment(mark.getX(), mark.getY(),
                    bond.getFrom().getX(), bond.getFrom().getY(),
                    bond.getTo().getX(), bond.getTo().getY());
            if (distance < extent + 3) {
                return false;
            }
        }
        for (ChargeMark other : document.chargeMarks()) {
            if (other == mark) {
                continue;
            }
            double separation = mark.getKind().isPartial() ? 29 : 22;
            if (Math.hypot(other.getX() - mark.getX(), other.getY() - mark.getY()) < separation) {
                return false;
            }
        }
        for (ElectronMark electron : document.electronMarks()) {
            if (Math.hypot(electron.getX() - mark.getX(),
                    electron.getY() - mark.getY()) < 19) {
                return false;
            }
        }
        return true;
    }

    private void layoutAttachedCharges(Atom atom) {
        if (atom == null) {
            return;
        }
        for (ChargeMark mark : document.chargeMarks()) {
            if (!atom.getId().equals(mark.getAttachedAtomId())) {
                continue;
            }
            if (!chargePositionIsClear(mark, atom)) {
                double[] position = chargeLabelPosition(atom, mark.getKind(), mark);
                mark.setPosition(position[0], position[1]);
            }
        }
    }

    private static final int LP_RIGHT = 0;
    private static final int LP_TOP = 1;
    private static final int LP_BOTTOM = 2;
    private static final int LP_LEFT = 3;

    /**
     * Lone-pair placement order around an atom. With a bond on the left the
     * sequence starts to the right (right, top, bottom, left); with a bond on
     * the right it is mirrored (left, top, bottom, right). Two-sided or vertical
     * bonds fall back to the free faces.
     */
    private int[] lonePairOrder(Atom host) {
        boolean left = false;
        boolean right = false;
        boolean above = false;
        boolean below = false;
        for (Bond bond : document.bondsOf(host)) {
            Atom other = bond.other(host);
            double dx = other.getX() - host.getX();
            double dy = other.getY() - host.getY();
            if (dx < -2) {
                left = true;
            } else if (dx > 2) {
                right = true;
            }
            if (dy < -2) {
                above = true;
            } else if (dy > 2) {
                below = true;
            }
        }
        if (left && !right) {
            return new int[]{LP_RIGHT, LP_TOP, LP_BOTTOM, LP_LEFT};
        }
        if (right && !left) {
            return new int[]{LP_LEFT, LP_TOP, LP_BOTTOM, LP_RIGHT};
        }
        if (left && right) {
            if (above && !below) {
                return new int[]{LP_BOTTOM, LP_RIGHT, LP_LEFT, LP_TOP};
            }
            if (below && !above) {
                return new int[]{LP_TOP, LP_RIGHT, LP_LEFT, LP_BOTTOM};
            }
            return new int[]{LP_TOP, LP_BOTTOM, LP_RIGHT, LP_LEFT};
        }
        if (above && !below) {
            return new int[]{LP_BOTTOM, LP_RIGHT, LP_LEFT, LP_TOP};
        }
        if (below && !above) {
            return new int[]{LP_TOP, LP_RIGHT, LP_LEFT, LP_BOTTOM};
        }
        return new int[]{LP_RIGHT, LP_TOP, LP_BOTTOM, LP_LEFT};
    }

    private double[] lonePairSlot(Atom host, int slot) {
        return switch (slot) {
            case LP_RIGHT -> new double[]{host.getX() + electronDistance, host.getY()};
            case LP_TOP -> new double[]{host.getX(), host.getY() - electronDistance};
            case LP_BOTTOM -> new double[]{host.getX(), host.getY() + electronDistance};
            default -> new double[]{host.getX() - electronDistance, host.getY()};
        };
    }

    /**
     * Positions between the bond directions, used once an atom has three or more
     * bonds. Pairs are placed at the bisector of each angular gap, larger gaps
     * first and with the upper gap preferred for a single pair.
     */
    private List<double[]> lonePairGapPositions(Atom host) {
        List<Bond> bonds = document.bondsOf(host);
        if (bonds.size() < 3) {
            return List.of();
        }
        List<Double> angles = new ArrayList<>();
        for (Bond bond : bonds) {
            Atom other = bond.other(host);
            angles.add(Math.atan2(other.getY() - host.getY(),
                    other.getX() - host.getX()));
        }
        angles.sort(Double::compare);
        List<double[]> gaps = new ArrayList<>();
        for (int i = 0; i < angles.size(); i++) {
            double start = angles.get(i);
            double end = i + 1 < angles.size()
                    ? angles.get(i + 1) : angles.get(0) + Math.PI * 2;
            double size = end - start;
            double bisector = normalizeAngle(start + size / 2);
            gaps.add(new double[]{bisector, size});
        }
        gaps.sort((a, b) -> {
            int bySize = Double.compare(b[1], a[1]);
            if (bySize != 0) {
                return bySize;
            }
            int byUp = Double.compare(
                    Math.abs(normalizeAngle(a[0] + Math.PI / 2)),
                    Math.abs(normalizeAngle(b[0] + Math.PI / 2)));
            if (byUp != 0) {
                return byUp;
            }
            return Double.compare(normalizeAngle(a[0]), normalizeAngle(b[0]));
        });
        List<double[]> positions = new ArrayList<>();
        for (double[] gap : gaps) {
            positions.add(new double[]{
                    host.getX() + Math.cos(gap[0]) * electronDistance,
                    host.getY() + Math.sin(gap[0]) * electronDistance
            });
        }
        return positions;
    }

    private double normalizeAngle(double angle) {
        while (angle <= -Math.PI) {
            angle += Math.PI * 2;
        }
        while (angle > Math.PI) {
            angle -= Math.PI * 2;
        }
        return angle;
    }

    private boolean electronSlotOccupied(Atom host, double x, double y,
                                         Set<ElectronMark> ignored) {
        for (ElectronMark mark : document.electronMarks()) {
            if (ignored.contains(mark) || !host.getId().equals(mark.getAttachedAtomId())) {
                continue;
            }
            if (Math.hypot(mark.getX() - x, mark.getY() - y) < 11) {
                return true;
            }
        }
        return false;
    }

    private double[] nextElectronPosition(Atom host) {
        if (document.bondsOf(host).size() >= 3) {
            for (double[] position : lonePairGapPositions(host)) {
                if (!electronSlotOccupied(host, position[0], position[1], Set.of())) {
                    return position;
                }
            }
        }
        int[] order = lonePairOrder(host);
        for (int slot : order) {
            double[] position = lonePairSlot(host, slot);
            if (!electronSlotOccupied(host, position[0], position[1], Set.of())) {
                return position;
            }
        }
        int attached = 0;
        for (ElectronMark mark : document.electronMarks()) {
            if (host.getId().equals(mark.getAttachedAtomId())) {
                attached++;
            }
        }
        return new double[]{host.getX() + electronDistance + 12 * (attached / 4),
                host.getY() + electronDistance + 12 * (attached % 4)};
    }

    private void layoutAttachedElectronMarks(Atom atom) {
        if (atom == null) {
            return;
        }
        List<ElectronMark> pairs = new ArrayList<>();
        for (ElectronMark mark : document.electronMarks()) {
            if (atom.getId().equals(mark.getAttachedAtomId())) {
                pairs.add(mark);
            }
        }
        if (pairs.isEmpty()) {
            return;
        }
        Set<ElectronMark> ignored = new LinkedHashSet<>(pairs);
        List<double[]> candidates = new ArrayList<>();
        if (document.bondsOf(atom).size() >= 3) {
            candidates.addAll(lonePairGapPositions(atom));
        }
        if (candidates.isEmpty()) {
            int[] order = lonePairOrder(atom);
            for (int slot : order) {
                candidates.add(lonePairSlot(atom, slot));
            }
        }
        List<double[]> used = new ArrayList<>();
        int index = 0;
        for (ElectronMark pair : pairs) {
            double[] chosen = null;
            for (double[] position : candidates) {
                boolean alreadyUsed = false;
                for (double[] previous : used) {
                    if (Math.hypot(previous[0] - position[0],
                            previous[1] - position[1]) < 11) {
                        alreadyUsed = true;
                        break;
                    }
                }
                if (!alreadyUsed && !electronSlotOccupied(
                        atom, position[0], position[1], ignored)) {
                    chosen = position;
                    break;
                }
            }
            if (chosen == null) {
                chosen = new double[]{atom.getX() + electronDistance + 12 * (index / 4),
                        atom.getY() + electronDistance + 12 * (index % 4)};
            }
            pair.setPosition(chosen[0], chosen[1]);
            used.add(chosen);
            index++;
        }
    }

    public ElectronMark addElectron(double x, double y, ElectronKind kind, Atom host) {
        ElectronMark mark;
        if (host != null) {
            double[] position = nextElectronPosition(host);
            mark = new ElectronMark(kind, position[0], position[1]);
            mark.setAttachedAtomId(host.getId());
        } else {
            mark = new ElectronMark(kind, x, y);
        }
        ElectronMark created = mark;
        perform("Add electron mark", () -> {
            document.clearSelection();
            created.setSelected(true);
            document.add(created);
        });
        return created;
    }

    public TextLabel addText(String text, double x, double y) {
        TextLabel label = new TextLabel(text, x, y);
        perform("Add text", () -> {
            document.clearSelection();
            label.setSelected(true);
            document.add(label);
        });
        return label;
    }

    public Arrow addArrow(ArrowType type, double x1, double y1, double x2, double y2) {
        if (Math.hypot(x2 - x1, y2 - y1) < 8) {
            return null;
        }
        return addArrow(type, x1, y1,
                (x1 + x2) / 2 + (y2 - y1) * 0.18,
                (y1 + y2) / 2 - (x2 - x1) * 0.18,
                x2, y2);
    }

    public Arrow addArrow(ArrowType type, double x1, double y1,
                          double cx, double cy, double x2, double y2) {
        if (Math.hypot(x2 - x1, y2 - y1) < 8) {
            return null;
        }
        Arrow arrow = new Arrow(null, type, x1, y1, cx, cy, x2, y2);
        perform("Add arrow", () -> {
            document.clearSelection();
            arrow.setSelected(true);
            document.add(arrow);
        });
        return arrow;
    }

    public Bracket addBracket(BracketType type, String label, double defaultCenterX, double defaultCenterY) {
        Bounds bounds = document.selectionBounds();
        if (bounds == null) {
            bounds = document.bounds();
        }
        double x;
        double y;
        double w;
        double h;
        if (bounds == null) {
            w = 170;
            h = 130;
            x = defaultCenterX - w / 2;
            y = defaultCenterY - h / 2;
        } else {
            double margin = 28;
            x = bounds.minX - margin;
            y = bounds.minY - margin;
            w = Math.max(80, bounds.width() + margin * 2);
            h = Math.max(80, bounds.height() + margin * 2);
        }
        Bracket bracket = new Bracket(type, x, y, w, h);
        bracket.setLabel(label == null ? "" : label);
        bracket.setLabelPosition(BracketLabelPosition.BOTTOM_RIGHT);
        perform("Add bracket", () -> {
            document.clearSelection();
            bracket.setSelected(true);
            document.add(bracket);
        });
        return bracket;
    }

    public boolean deleteSelection() {
        List<ChemObject> selected = document.selectedObjects();
        if (selected.isEmpty()) {
            return false;
        }
        perform("Delete", () -> document.removeAll(selected));
        return true;
    }

    public boolean deleteObject(ChemObject object) {
        if (object == null) {
            return false;
        }
        perform("Delete", () -> document.removeAll(List.of(object)));
        return true;
    }

    public boolean copySelection() {
        List<ChemObject> selected = document.selectedObjects();
        if (selected.isEmpty()) {
            return false;
        }
        clipboard = document.extractFragment(selected, true);
        pasteGeneration = 0;
        return true;
    }

    public boolean cutSelection() {
        if (!copySelection()) {
            return false;
        }
        List<ChemObject> selected = document.selectedObjects();
        perform("Cut", () -> document.removeAll(selected));
        return true;
    }

    public boolean pasteClipboard() {
        if (clipboard == null || clipboard.isEmpty()) {
            return false;
        }
        ChemDocument fresh = clipboard.extractFragment(clipboard.objects(), false);
        if (fresh.isEmpty()) {
            return false;
        }
        double offset = 24 + pasteGeneration * 14;
        pasteGeneration++;
        List<ChemObject> toAdd = new ArrayList<>(fresh.objects());
        perform("Paste", () -> {
            document.clearSelection();
            for (ChemObject object : toAdd) {
                object.translate(offset, offset);
                if (object instanceof Bond bond) {
                    // Endpoints have already moved; bond geometry follows them.
                }
                object.setSelected(true);
                document.add(object);
            }
        });
        return true;
    }

    public boolean duplicateSelection() {
        List<ChemObject> selected = document.selectedObjects();
        if (selected.isEmpty()) {
            return false;
        }
        ChemDocument fragment = document.extractFragment(selected, false);
        if (fragment.isEmpty()) {
            return false;
        }
        List<ChemObject> toAdd = new ArrayList<>(fragment.objects());
        perform("Duplicate", () -> {
            document.clearSelection();
            for (ChemObject object : toAdd) {
                object.translate(24, 24);
                object.setSelected(true);
                document.add(object);
            }
        });
        return true;
    }

    public void nudgeSelection(double dx, double dy) {
        if (!hasSelection()) {
            return;
        }
        perform("Move", () -> document.translateSelected(dx, dy));
    }

    public void rotateSelection(double degrees) {
        if (!hasSelection()) {
            return;
        }
        Set<Atom> chainAtoms = selectedTransformAtoms();
        if (isHorizontalAlkaneChainSelection(chainAtoms)) {
            Bounds atomBounds = boundsOf(chainAtoms);
            if (atomBounds != null) {
                HexGrid.Center pivot = HexGrid.nearestCenter(
                        atomBounds.centerX(), atomBounds.centerY());
                // A half-turn around a lattice cell centre preserves the
                // horizontal chain orientation and maps every vertex to a vertex.
                perform("Rotate chain horizontally", () -> {
                    document.rotateSelectedAround(Math.PI, pivot.x(), pivot.y());
                    for (Atom atom : chainAtoms) {
                        HexGrid.Vertex vertex = snapToVertex(atom.getX(), atom.getY());
                        atom.setPosition(vertex.x(), vertex.y());
                    }
                });
                return;
            }
        }
        perform("Rotate", () -> document.rotateSelected(Math.toRadians(degrees)));
    }

    /**
     * True when the selection is one complete, acyclic, horizontal chain whose
     * atoms are all on the honeycomb. Those chains rotate end-to-end in place of
     * tilting off the horizontal axis.
     */
    private boolean isHorizontalAlkaneChainSelection(Set<Atom> selectedAtoms) {
        if (selectedAtoms.size() < 2) {
            return false;
        }
        for (Atom atom : selectedAtoms) {
            for (Bond bond : document.bondsOf(atom)) {
                if (!selectedAtoms.contains(bond.other(atom))) {
                    return false;
                }
            }
        }
        int internalBonds = 0;
        Set<Bond> counted = new HashSet<>();
        for (Atom atom : selectedAtoms) {
            int degree = 0;
            for (Bond bond : document.bondsOf(atom)) {
                if (selectedAtoms.contains(bond.other(atom))) {
                    degree++;
                    if (counted.add(bond)) {
                        internalBonds++;
                    }
                }
            }
            if (degree > 2) {
                return false;
            }
        }
        if (internalBonds != selectedAtoms.size() - 1) {
            return false;
        }
        Bounds atomBounds = boundsOf(selectedAtoms);
        if (atomBounds == null || atomBounds.width() <= atomBounds.height() * 1.2) {
            return false;
        }
        for (Atom atom : selectedAtoms) {
            if (!HexGrid.isVertex(atom.getX(), atom.getY(), 0.1)) {
                return false;
            }
        }
        return true;
    }

    private Set<Atom> selectedTransformAtoms() {
        Set<Atom> atoms = new LinkedHashSet<>();
        for (ChemObject object : document.selectedObjects()) {
            if (object instanceof Atom atom) {
                atoms.add(atom);
            } else if (object instanceof Bond bond) {
                atoms.add(bond.getFrom());
                atoms.add(bond.getTo());
            }
        }
        return atoms;
    }

    private Bounds boundsOf(Set<Atom> atoms) {
        Bounds bounds = null;
        for (Atom atom : atoms) {
            Bounds b = Bounds.point(atom.getX(), atom.getY());
            bounds = bounds == null ? b : bounds.union(b);
        }
        return bounds;
    }

    public void setSelectedBondsType(BondType type) {
        List<Bond> bonds = new ArrayList<>();
        for (ChemObject object : document.selectedObjects()) {
            if (object instanceof Bond bond) {
                bonds.add(bond);
            }
        }
        if (bonds.isEmpty()) {
            return;
        }
        perform("Change bond type", () -> {
            for (Bond bond : bonds) {
                bond.setType(type);
            }
        });
    }

    public void setAtomElement(Atom atom, String symbol) {
        if (atom == null || symbol == null || symbol.isBlank()) {
            return;
        }
        perform("Change element", () -> {
            if (symbol.equals("R") || symbol.equals("X")) {
                atom.setKind(symbol.equals("R") ? AtomKind.R_GROUP : AtomKind.X_GROUP);
                atom.setGenericLabel(symbol.equals("R") ? rLabel : xLabel);
            } else {
                atom.setKind(AtomKind.ELEMENT);
                atom.setElementSymbol(symbol);
            }
        });
    }

    public void setAtomGenericLabel(Atom atom, String label) {
        if (atom == null || !atom.isGeneric()) {
            return;
        }
        String clean = label == null || label.isBlank() ? atom.getElementSymbol() : label.strip();
        if (clean.equals(atom.getGenericLabel())) {
            return;
        }
        perform("Rename generic group", () -> atom.setGenericLabel(clean));
        if (atom.getKind() == AtomKind.R_GROUP) {
            rLabel = clean;
        } else {
            xLabel = clean;
        }
        fireChanged();
    }

    public void setObjectsAttachedAtom(Collection<? extends ChemObject> objects, Atom atom) {
        if (objects.isEmpty()) {
            return;
        }
        perform("Attach annotation", () -> {
            for (ChemObject object : objects) {
                if (object instanceof com.molview.app.model.Annotation annotation) {
                    annotation.setAttachedAtomId(atom == null ? null : atom.getId());
                }
            }
        });
    }

    public void moveToFront(ChemObject object) {
        if (object == null) {
            return;
        }
        perform("Bring to front", () -> document.moveToFront(object));
    }

    public Set<ChemObject> hitCandidates(double x, double y, double tolerance) {
        Set<ChemObject> hits = new HashSet<>();
        for (ChemObject object : document.objects()) {
            if (object.hitTest(x, y, tolerance)) {
                hits.add(object);
            }
        }
        return hits;
    }

    public String selectionSummary() {
        List<ChemObject> selected = document.selectedObjects();
        if (selected.isEmpty()) {
            return "Nothing selected";
        }
        if (selected.size() == 1) {
            return selected.get(0).typeName();
        }
        int atoms = 0;
        int bonds = 0;
        int others = 0;
        for (ChemObject object : selected) {
            if (object instanceof Atom) {
                atoms++;
            } else if (object instanceof Bond) {
                bonds++;
            } else {
                others++;
            }
        }
        StringBuilder text = new StringBuilder();
        if (atoms > 0) {
            text.append(atoms).append(atoms == 1 ? " atom" : " atoms");
        }
        if (bonds > 0) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(bonds).append(bonds == 1 ? " bond" : " bonds");
        }
        if (others > 0) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(others).append(others == 1 ? " annotation" : " annotations");
        }
        return text.toString();
    }
}
