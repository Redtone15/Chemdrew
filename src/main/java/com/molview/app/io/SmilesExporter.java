package com.molview.app.io;

import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.BondType;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph-based SMILES writer. Handles organic-subset atoms, branches, ring
 * closures, aromatic rings, formal charges and disconnected fragments.
 *
 * <p>SMILES cannot preserve every 2D drawing-only annotation, so aromatic bond
 * marks and formal charges are carried over while wedge/hash direction is left
 * to the MOL export.</p>
 */
public final class SmilesExporter {
    private static final Set<String> ORGANIC = Set.of(
            "B", "C", "N", "O", "P", "S", "F", "Cl", "Br", "I");
    private static final Set<String> AROMATIC = Set.of(
            "B", "C", "N", "O", "P", "S", "Se", "As");

    private final ChemDocument document;
    private final List<Bond> covalentBonds = new ArrayList<>();
    private final Map<Atom, List<Bond>> adjacency = new HashMap<>();
    private final Map<Bond, Integer> ringNumbers = new HashMap<>();
    private final Map<Bond, Boolean> ringSymbolEmitted = new HashMap<>();
    private final Map<Atom, List<Bond>> ringsAtAtom = new HashMap<>();
    private final Set<Bond> treeBonds = new HashSet<>();
    private final Map<Atom, Atom> parent = new HashMap<>();
    private final Set<Atom> aromaticAtoms = new HashSet<>();

    private SmilesExporter(ChemDocument document) {
        this.document = document;
    }

    public static String toSmiles(ChemDocument document) {
        return new SmilesExporter(document).export();
    }

    private String export() {
        for (Bond bond : document.bonds()) {
            if (bond.getType() == BondType.HYDROGEN) {
                continue;
            }
            covalentBonds.add(bond);
            adjacency.computeIfAbsent(bond.getFrom(), k -> new ArrayList<>()).add(bond);
            adjacency.computeIfAbsent(bond.getTo(), k -> new ArrayList<>()).add(bond);
            if (bond.getType().isAromaticOrDelocalised()) {
                aromaticAtoms.add(bond.getFrom());
                aromaticAtoms.add(bond.getTo());
            }
        }
        for (Atom atom : document.atoms()) {
            adjacency.putIfAbsent(atom, new ArrayList<>());
        }
        for (Map.Entry<Atom, List<Bond>> entry : adjacency.entrySet()) {
            entry.getValue().sort(Comparator
                    .comparing((Bond b) -> b.other(entry.getKey()).getElementSymbol())
                    .thenComparing(Bond::getId));
        }

        Set<Atom> visited = new HashSet<>();
        List<String> components = new ArrayList<>();
        for (Atom atom : document.atoms()) {
            if (!visited.contains(atom)) {
                List<Atom> component = new ArrayList<>();
                Deque<Atom> queue = new ArrayDeque<>();
                queue.add(atom);
                while (!queue.isEmpty()) {
                    Atom current = queue.removeFirst();
                    if (!visited.add(current)) {
                        continue;
                    }
                    component.add(current);
                    for (Bond bond : adjacency.getOrDefault(current, List.of())) {
                        Atom other = bond.other(current);
                        if (!visited.contains(other)) {
                            queue.addLast(other);
                        }
                    }
                }
                components.add(componentSmiles(component));
            }
        }
        return String.join(".", components);
    }

    private String componentSmiles(List<Atom> component) {
        // Build a deterministic spanning tree.
        treeBonds.clear();
        parent.clear();
        ringNumbers.clear();
        ringSymbolEmitted.clear();
        ringsAtAtom.clear();
        Set<Atom> visited = new HashSet<>();
        Atom root = component.get(0);
        buildTree(root, null, visited);

        List<Bond> ringBonds = new ArrayList<>();
        for (Bond bond : covalentBonds) {
            if (component.contains(bond.getFrom()) && component.contains(bond.getTo())
                    && !treeBonds.contains(bond)) {
                ringBonds.add(bond);
            }
        }
        ringBonds.sort(Comparator.comparing(Bond::getId));
        int ringNumber = 1;
        for (Bond bond : ringBonds) {
            ringNumbers.put(bond, ringNumber++);
            ringsAtAtom.computeIfAbsent(bond.getFrom(), k -> new ArrayList<>()).add(bond);
            ringsAtAtom.computeIfAbsent(bond.getTo(), k -> new ArrayList<>()).add(bond);
            ringSymbolEmitted.put(bond, false);
        }
        for (List<Bond> list : ringsAtAtom.values()) {
            list.sort(Comparator.comparingInt(b -> ringNumbers.getOrDefault(b, 999)));
        }
        StringBuilder out = new StringBuilder();
        emit(root, null, out);
        return out.toString();
    }

    private void buildTree(Atom atom, Atom parentAtom, Set<Atom> visited) {
        visited.add(atom);
        parent.put(atom, parentAtom);
        for (Bond bond : adjacency.getOrDefault(atom, List.of())) {
            Atom other = bond.other(atom);
            if (other == parentAtom) {
                continue;
            }
            if (!visited.contains(other)) {
                treeBonds.add(bond);
                buildTree(other, atom, visited);
            }
        }
    }

    private void emit(Atom atom, Atom parentAtom, StringBuilder out) {
        out.append(atomToken(atom));
        for (Bond closure : ringsAtAtom.getOrDefault(atom, List.of())) {
            boolean first = !ringSymbolEmitted.getOrDefault(closure, false);
            if (first) {
                out.append(bondSymbol(closure, atom));
                ringSymbolEmitted.put(closure, true);
            }
            out.append(ringLabel(ringNumbers.get(closure)));
        }

        List<Bond> children = new ArrayList<>();
        for (Bond bond : adjacency.getOrDefault(atom, List.of())) {
            Atom other = bond.other(atom);
            if (other == parentAtom || ringsAtAtom.getOrDefault(atom, List.of()).contains(bond)) {
                continue;
            }
            if (treeBonds.contains(bond) && parent.get(other) == atom) {
                children.add(bond);
            }
        }
        children.sort(Comparator.comparing(b -> b.other(atom).getId()));
        for (int i = 0; i < children.size(); i++) {
            Bond bond = children.get(i);
            Atom child = bond.other(atom);
            boolean last = i == children.size() - 1;
            String symbol = bondSymbol(bond, atom);
            if (!last) {
                out.append('(').append(symbol);
                emit(child, atom, out);
                out.append(')');
            } else {
                out.append(symbol);
                emit(child, atom, out);
            }
        }
    }

    private String bondSymbol(Bond bond, Atom from) {
        if (bond.getType().isAromaticOrDelocalised()
                || (aromaticAtoms.contains(bond.getFrom()) && aromaticAtoms.contains(bond.getTo()))) {
            return "";
        }
        return switch (bond.getType()) {
            case DOUBLE -> "=";
            case TRIPLE -> "#";
            default -> "";
        };
    }

    private String ringLabel(int number) {
        return number <= 9 ? Integer.toString(number) : "%" + number;
    }

    private String atomToken(Atom atom) {
        if (atom.isGeneric()) {
            return "*";
        }
        String symbol = atom.getElementSymbol();
        boolean aromatic = aromaticAtoms.contains(atom) && AROMATIC.contains(symbol);
        int charge = formalCharge(atom);
        int hydrogens = implicitHydrogens(atom, charge);
        boolean bracket = charge != 0 || !ORGANIC.contains(symbol);
        if (aromatic && !bracket) {
            return symbol.toLowerCase();
        }
        if (!bracket && !aromatic && isBracketRequiredByValence(atom, charge, hydrogens)) {
            bracket = true;
        }
        if (!bracket) {
            return symbol;
        }
        StringBuilder token = new StringBuilder("[");
        token.append(aromatic ? symbol.toLowerCase() : symbol);
        if (hydrogens > 0) {
            token.append('H');
            if (hydrogens > 1) {
                token.append(hydrogens);
            }
        }
        if (charge > 0) {
            token.append('+');
            if (charge > 1) {
                token.append(charge);
            }
        } else if (charge < 0) {
            token.append('-');
            if (charge < -1) {
                token.append(-charge);
            }
        }
        token.append(']');
        return token.toString();
    }

    private boolean isBracketRequiredByValence(Atom atom, int charge, int hydrogens) {
        double order = bondOrderSum(atom);
        int valence = expectedValence(atom, charge);
        // A neutral organic atom with zero or more hydrogens is unambiguous in
        // the organic subset. Unusual over-valent atoms are safer in brackets.
        return order > valence + 0.01 || hydrogens < 0;
    }

    private int formalCharge(Atom atom) {
        return document.formalCharge(atom);
    }

    private double bondOrderSum(Atom atom) {
        double sum = 0;
        for (Bond bond : adjacency.getOrDefault(atom, List.of())) {
            sum += bond.getType().order();
        }
        return sum;
    }

    private int implicitHydrogens(Atom atom, int charge) {
        String symbol = atom.getElementSymbol();
        if (!isValenceAtom(symbol) || "H".equals(symbol)) {
            // Hydrogen cannot carry an H-count in a bracket atom; it is either
            // an explicit [H] or part of a heavier atom's implicit count.
            return 0;
        }
        int valence = expectedValence(atom, charge);
        double used = bondOrderSum(atom);
        return Math.max(0, (int) Math.round(valence - used));
    }

    private boolean isValenceAtom(String symbol) {
        return switch (symbol) {
            case "H", "B", "C", "N", "O", "F", "Si", "P", "S", "Cl",
                    "Se", "Br", "I", "As", "Te" -> true;
            default -> false;
        };
    }

    private int expectedValence(Atom atom, int charge) {
        String symbol = atom.getElementSymbol();
        int base = switch (symbol) {
            case "H", "F", "Cl", "Br", "I" -> 1;
            case "B" -> 3;
            case "C", "Si" -> 4;
            case "N", "P", "As" -> 3;
            case "O", "S", "Se", "Te" -> 2;
            default -> 0;
        };
        if (symbol.equals("C") || symbol.equals("Si")) {
            return charge == 0 ? 4 : 3;
        }
        return Math.max(0, base + charge);
    }
}
