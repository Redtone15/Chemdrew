package com.molview.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Editable chemical drawing document. A single, insertion-ordered object list
 * keeps z-order stable while typed accessors expose atoms, bonds and annotations.
 */
public final class ChemDocument {
    private final List<ChemObject> objects = new ArrayList<>();
    private String title = "Untitled";

    public ChemDocument() {
    }

    public ChemDocument(String title) {
        this.title = title == null || title.isBlank() ? "Untitled" : title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null || title.isBlank() ? "Untitled" : title;
    }

    public List<ChemObject> objects() {
        return Collections.unmodifiableList(objects);
    }

    public List<Atom> atoms() {
        List<Atom> atoms = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof Atom atom) {
                atoms.add(atom);
            }
        }
        return atoms;
    }

    public List<Bond> bonds() {
        List<Bond> bonds = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof Bond bond) {
                bonds.add(bond);
            }
        }
        return bonds;
    }

    public List<Annotation> annotations() {
        List<Annotation> annotations = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof Annotation annotation) {
                annotations.add(annotation);
            }
        }
        return annotations;
    }

    public List<ChargeMark> chargeMarks() {
        List<ChargeMark> charges = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof ChargeMark charge) {
                charges.add(charge);
            }
        }
        return charges;
    }

    public List<ElectronMark> electronMarks() {
        List<ElectronMark> marks = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof ElectronMark mark) {
                marks.add(mark);
            }
        }
        return marks;
    }

    public List<Arrow> arrows() {
        List<Arrow> arrows = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof Arrow arrow) {
                arrows.add(arrow);
            }
        }
        return arrows;
    }

    public List<Bracket> brackets() {
        List<Bracket> brackets = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof Bracket bracket) {
                brackets.add(bracket);
            }
        }
        return brackets;
    }

    public List<TextLabel> textLabels() {
        List<TextLabel> labels = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object instanceof TextLabel label) {
                labels.add(label);
            }
        }
        return labels;
    }

    public void add(ChemObject object) {
        Objects.requireNonNull(object);
        if (findById(object.getId()) != null) {
            throw new IllegalArgumentException("Duplicate object id: " + object.getId());
        }
        objects.add(object);
    }

    public void addAll(Collection<? extends ChemObject> toAdd) {
        for (ChemObject object : toAdd) {
            add(object);
        }
    }

    public boolean remove(ChemObject object) {
        return objects.remove(object);
    }

    public void removeAll(Collection<? extends ChemObject> toRemove) {
        if (toRemove.isEmpty()) {
            return;
        }
        Set<ChemObject> expanded = new HashSet<>(toRemove);
        Set<Atom> removedAtoms = new HashSet<>();
        for (ChemObject object : toRemove) {
            if (object instanceof Atom atom) {
                removedAtoms.add(atom);
            }
        }
        if (!removedAtoms.isEmpty()) {
            for (Bond bond : bonds()) {
                if (removedAtoms.contains(bond.getFrom()) || removedAtoms.contains(bond.getTo())) {
                    expanded.add(bond);
                }
            }
            Set<String> removedIds = new HashSet<>();
            for (Atom atom : removedAtoms) {
                removedIds.add(atom.getId());
            }
            for (Annotation annotation : annotations()) {
                if (annotation.getAttachedAtomId() != null
                        && removedIds.contains(annotation.getAttachedAtomId())) {
                    expanded.add(annotation);
                }
            }
        }
        objects.removeIf(expanded::contains);
    }

    public void clear() {
        objects.clear();
    }

    public boolean isEmpty() {
        return objects.isEmpty();
    }

    public ChemObject findById(String id) {
        if (id == null) {
            return null;
        }
        for (ChemObject object : objects) {
            if (id.equals(object.getId())) {
                return object;
            }
        }
        return null;
    }

    public Atom findAtom(String id) {
        ChemObject object = findById(id);
        return object instanceof Atom atom ? atom : null;
    }

    public List<Bond> bondsOf(Atom atom) {
        List<Bond> result = new ArrayList<>();
        for (Bond bond : bonds()) {
            if (bond.connects(atom)) {
                result.add(bond);
            }
        }
        return result;
    }

    public int bondCount(Atom atom) {
        int count = 0;
        for (Bond bond : bonds()) {
            if (bond.connects(atom)) {
                count++;
            }
        }
        return count;
    }

    public List<Annotation> annotationsOf(Atom atom) {
        List<Annotation> result = new ArrayList<>();
        if (atom == null) {
            return result;
        }
        for (Annotation annotation : annotations()) {
            if (atom.getId().equals(annotation.getAttachedAtomId())) {
                result.add(annotation);
            }
        }
        return result;
    }

    public int formalCharge(Atom atom) {
        int charge = 0;
        for (ChargeMark mark : chargeMarks()) {
            if (atom.getId().equals(mark.getAttachedAtomId()) && !mark.getKind().isPartial()) {
                charge += mark.getKind().formalCharge();
            }
        }
        return charge;
    }

    public Bounds bounds() {
        Bounds result = null;
        for (ChemObject object : objects) {
            Bounds b = object.bounds();
            result = result == null ? b : result.union(b);
        }
        return result;
    }

    public Bounds selectionBounds() {
        Bounds result = null;
        for (ChemObject object : objects) {
            if (object.isSelected()) {
                Bounds b = object.bounds();
                result = result == null ? b : result.union(b);
            }
        }
        return result;
    }

    public List<ChemObject> selectedObjects() {
        List<ChemObject> result = new ArrayList<>();
        for (ChemObject object : objects) {
            if (object.isSelected()) {
                result.add(object);
            }
        }
        return result;
    }

    public void clearSelection() {
        for (ChemObject object : objects) {
            object.setSelected(false);
        }
    }

    public void selectAll() {
        for (ChemObject object : objects) {
            object.setSelected(true);
        }
    }

    public void selectOnly(ChemObject object) {
        clearSelection();
        if (object != null) {
            object.setSelected(true);
        }
    }

    public void toggleSelection(ChemObject object) {
        if (object != null) {
            object.setSelected(!object.isSelected());
        }
    }

    /** Selects a whole connected molecular structure, including attached marks. */
    public Set<ChemObject> structureFor(ChemObject seed) {
        Set<ChemObject> result = new LinkedHashSet<>();
        if (seed == null) {
            return result;
        }
        Deque<Atom> queue = new ArrayDeque<>();
        Set<Atom> seen = new HashSet<>();
        if (seed instanceof Atom atom) {
            queue.add(atom);
        } else if (seed instanceof Bond bond) {
            queue.add(bond.getFrom());
            queue.add(bond.getTo());
        }
        while (!queue.isEmpty()) {
            Atom atom = queue.removeFirst();
            if (!seen.add(atom)) {
                continue;
            }
            result.add(atom);
            for (Bond bond : bondsOf(atom)) {
                result.add(bond);
                Atom other = bond.other(atom);
                if (!seen.contains(other)) {
                    queue.addLast(other);
                }
            }
        }
        Set<String> atomIds = new HashSet<>();
        for (Atom atom : seen) {
            atomIds.add(atom.getId());
        }
        for (Annotation annotation : annotations()) {
            if (annotation.getAttachedAtomId() != null
                    && atomIds.contains(annotation.getAttachedAtomId())) {
                result.add(annotation);
            }
        }
        if (seed instanceof Annotation annotation) {
            result.add(annotation);
        }
        return result;
    }

    public void selectStructure(ChemObject seed) {
        Set<ChemObject> structure = structureFor(seed);
        clearSelection();
        for (ChemObject object : structure) {
            object.setSelected(true);
        }
    }

    /**
     * Builds a detached document from selected objects. Bonds between selected
     * atoms are included automatically; selected bond endpoints are included so
     * copy/paste never creates dangling bonds.
     */
    public ChemDocument extractFragment(Collection<? extends ChemObject> selected, boolean preserveIds) {
        ChemDocument fragment = new ChemDocument("Fragment");
        Set<Atom> sourceAtoms = new LinkedHashSet<>();
        for (ChemObject object : selected) {
            if (object instanceof Atom atom) {
                sourceAtoms.add(atom);
            } else if (object instanceof Bond bond) {
                sourceAtoms.add(bond.getFrom());
                sourceAtoms.add(bond.getTo());
            }
        }
        Map<Atom, Atom> atomMap = new HashMap<>();
        Map<String, String> idMap = new HashMap<>();
        for (Atom atom : sourceAtoms) {
            Atom copy = atom.copy(preserveIds);
            atomMap.put(atom, copy);
            idMap.put(atom.getId(), copy.getId());
            copy.setSelected(true);
            fragment.add(copy);
        }
        for (Bond bond : bonds()) {
            boolean endpointsCopied = atomMap.containsKey(bond.getFrom()) && atomMap.containsKey(bond.getTo());
            if (endpointsCopied && (selected.contains(bond) || sourceAtoms.contains(bond.getFrom())
                    || sourceAtoms.contains(bond.getTo()))) {
                Bond copy = bond.copy(atomMap, preserveIds);
                copy.setSelected(true);
                fragment.add(copy);
            }
        }
        Set<String> copiedAtomIds = new HashSet<>();
        for (Atom atom : sourceAtoms) {
            copiedAtomIds.add(atom.getId());
        }
        for (Annotation annotation : annotations()) {
            boolean attachedToCopied = annotation.getAttachedAtomId() != null
                    && copiedAtomIds.contains(annotation.getAttachedAtomId());
            if (selected.contains(annotation) || attachedToCopied) {
                if (annotation.getAttachedAtomId() != null
                        && !copiedAtomIds.contains(annotation.getAttachedAtomId())) {
                    continue;
                }
                Annotation copy = (Annotation) annotation.copy(preserveIds);
                if (copy.getAttachedAtomId() != null) {
                    copy.setAttachedAtomId(idMap.get(copy.getAttachedAtomId()));
                }
                copy.setSelected(true);
                fragment.add(copy);
            }
        }
        for (ChemObject object : objects) {
            if ((object instanceof Arrow || object instanceof Bracket)
                    && selected.contains(object)) {
                ChemObject copy = object.copy(preserveIds);
                copy.setSelected(true);
                fragment.add(copy);
            }
        }
        return fragment;
    }

    public ChemDocument deepCopy() {
        ChemDocument copy = new ChemDocument(title);
        Map<Atom, Atom> atomMap = new HashMap<>();
        for (Atom atom : atoms()) {
            Atom atomCopy = atom.copy(true);
            atomMap.put(atom, atomCopy);
            copy.add(atomCopy);
        }
        for (Bond bond : bonds()) {
            copy.add(bond.copy(atomMap, true));
        }
        for (Annotation annotation : annotations()) {
            copy.add((Annotation) annotation.copy(true));
        }
        for (ChemObject object : objects) {
            if (object instanceof Arrow || object instanceof Bracket) {
                copy.add(object.copy(true));
            }
        }
        return copy;
    }

    /** Returns connected components as atom lists, in document order. */
    public List<List<Atom>> components() {
        List<List<Atom>> components = new ArrayList<>();
        Set<Atom> visited = new HashSet<>();
        for (Atom atom : atoms()) {
            if (visited.contains(atom)) {
                continue;
            }
            List<Atom> component = new ArrayList<>();
            Deque<Atom> queue = new ArrayDeque<>();
            queue.add(atom);
            while (!queue.isEmpty()) {
                Atom current = queue.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                component.add(current);
                for (Bond bond : bondsOf(current)) {
                    Atom other = bond.other(current);
                    if (!visited.contains(other)) {
                        queue.addLast(other);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    public void translateSelected(double dx, double dy) {
        Set<Atom> atomsToMove = new LinkedHashSet<>();
        Set<ChemObject> direct = new LinkedHashSet<>();
        for (ChemObject object : selectedObjects()) {
            if (object instanceof Atom atom) {
                atomsToMove.add(atom);
                direct.add(atom);
            } else if (object instanceof Bond bond) {
                atomsToMove.add(bond.getFrom());
                atomsToMove.add(bond.getTo());
            } else {
                direct.add(object);
            }
        }
        Set<String> movedAtomIds = new HashSet<>();
        for (Atom atom : atomsToMove) {
            movedAtomIds.add(atom.getId());
        }
        for (ChemObject object : objects) {
            if (object instanceof Annotation annotation
                    && annotation.getAttachedAtomId() != null
                    && movedAtomIds.contains(annotation.getAttachedAtomId())
                    && !direct.contains(annotation)) {
                direct.add(annotation);
            }
        }
        for (ChemObject object : direct) {
            if (object instanceof Bond) {
                continue;
            }
            object.translate(dx, dy);
        }
        for (Atom atom : atomsToMove) {
            atom.translate(dx, dy);
        }
    }

    public void rotateSelected(double angleRadians) {
        Bounds bounds = selectionBounds();
        if (bounds == null) {
            return;
        }
        rotateSelectedAround(angleRadians, bounds.centerX(), bounds.centerY());
    }

    /** Rotates the selection around an explicit pivot. */
    public void rotateSelectedAround(double angleRadians, double cx, double cy) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        Set<ChemObject> direct = new LinkedHashSet<>();
        Set<Atom> atomsToRotate = new LinkedHashSet<>();
        for (ChemObject object : selectedObjects()) {
            if (object instanceof Atom atom) {
                atomsToRotate.add(atom);
            } else if (object instanceof Bond bond) {
                atomsToRotate.add(bond.getFrom());
                atomsToRotate.add(bond.getTo());
            } else {
                direct.add(object);
            }
        }
        Set<String> movedAtomIds = new HashSet<>();
        for (Atom atom : atomsToRotate) {
            movedAtomIds.add(atom.getId());
        }
        for (Annotation annotation : annotations()) {
            if (annotation.getAttachedAtomId() != null
                    && movedAtomIds.contains(annotation.getAttachedAtomId())) {
                direct.add(annotation);
            }
        }
        for (Atom atom : atomsToRotate) {
            double dx = atom.getX() - cx;
            double dy = atom.getY() - cy;
            atom.setPosition(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
        }
        for (ChemObject object : direct) {
            if (object instanceof ChargeMark charge) {
                double dx = charge.getX() - cx;
                double dy = charge.getY() - cy;
                charge.setPosition(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
            } else if (object instanceof ElectronMark electron) {
                double dx = electron.getX() - cx;
                double dy = electron.getY() - cy;
                electron.setPosition(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
            } else if (object instanceof TextLabel label) {
                double dx = label.getX() - cx;
                double dy = label.getY() - cy;
                label.setPosition(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
            } else if (object instanceof Arrow arrow) {
                double x1 = arrow.getX1() - cx;
                double y1 = arrow.getY1() - cy;
                double xc = arrow.getControlX() - cx;
                double yc = arrow.getControlY() - cy;
                double x2 = arrow.getX2() - cx;
                double y2 = arrow.getY2() - cy;
                arrow.setPoints(
                        cx + x1 * cos - y1 * sin, cy + x1 * sin + y1 * cos,
                        cx + xc * cos - yc * sin, cy + xc * sin + yc * cos,
                        cx + x2 * cos - y2 * sin, cy + x2 * sin + y2 * cos);
            } else if (object instanceof Bracket bracket) {
                double dx = bracket.getX() + bracket.getWidth() / 2 - cx;
                double dy = bracket.getY() + bracket.getHeight() / 2 - cy;
                double nx = cx + dx * cos - dy * sin;
                double ny = cy + dx * sin + dy * cos;
                bracket.setFrame(nx - bracket.getWidth() / 2, ny - bracket.getHeight() / 2,
                        bracket.getWidth(), bracket.getHeight());
            }
        }
    }

    public void moveToFront(ChemObject object) {
        if (objects.remove(object)) {
            objects.add(object);
        }
    }

    public Map<String, Atom> atomByIdMap() {
        Map<String, Atom> map = new HashMap<>();
        for (Atom atom : atoms()) {
            map.put(atom.getId(), atom);
        }
        return map;
    }
}
