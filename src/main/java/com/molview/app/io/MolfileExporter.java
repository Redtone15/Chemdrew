package com.molview.app.io;

import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.BondType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** MDL V2000 MOL and multi-record SDF writer. */
public final class MolfileExporter {
    private MolfileExporter() {
    }

    public static void exportMol(ChemDocument document, Path path) throws IOException {
        Files.writeString(path, toMol(document), StandardCharsets.UTF_8);
    }

    public static void exportSdf(ChemDocument document, Path path) throws IOException {
        Files.writeString(path, toSdf(document), StandardCharsets.UTF_8);
    }

    public static String toMol(ChemDocument document) {
        List<Bond> bonds = new ArrayList<>();
        for (Bond bond : document.bonds()) {
            if (bond.getType() != BondType.HYDROGEN) {
                bonds.add(bond);
            }
        }
        MolData data = new MolData(document.getTitle(), document.atoms(), bonds,
                charges(document), radicals(document), genericAliases(document),
                partialCharges(document));
        return writeRecord(data, false);
    }

    public static String toSdf(ChemDocument document) {
        List<List<Atom>> components = components(document);
        if (components.isEmpty()) {
            throw new IllegalStateException("There are no atoms to export.");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < components.size(); i++) {
            List<Atom> atoms = components.get(i);
            Set<Atom> atomSet = new LinkedHashSet<>(atoms);
            List<Bond> bonds = new ArrayList<>();
            for (Bond bond : document.bonds()) {
                if (bond.getType() != BondType.HYDROGEN
                        && atomSet.contains(bond.getFrom()) && atomSet.contains(bond.getTo())) {
                    bonds.add(bond);
                }
            }
            String title = document.getTitle()
                    + (components.size() > 1 ? " - component " + (i + 1) : "");
            MolData data = new MolData(title, atoms, bonds,
                    filterCharges(charges(document), atomSet),
                    filterRadicals(radicals(document), atomSet),
                    filterAliases(genericAliases(document), atomSet),
                    filterCharges(partialCharges(document), atomSet));
            out.append(writeRecord(data, true));
            out.append(">  <MOLVIEW_COMPONENT>\n")
                    .append(i + 1).append(" of ").append(components.size()).append("\n\n");
            out.append(">  <ATOM_COUNT>\n").append(atoms.size()).append("\n\n");
            out.append(">  <BOND_COUNT>\n").append(bonds.size()).append("\n\n");
            String aliases = aliasesText(data.aliases, atoms);
            if (!aliases.isBlank()) {
                out.append(">  <GENERIC_GROUPS>\n").append(aliases).append("\n\n");
            }
            String partial = partialText(data.partialCharges, atoms);
            if (!partial.isBlank()) {
                out.append(">  <PARTIAL_CHARGES>\n").append(partial).append("\n\n");
            }
            out.append("$$$$\n");
        }
        return out.toString();
    }

    private static String writeRecord(MolData data, boolean sdf) {
        if (data.atoms.isEmpty()) {
            throw new IllegalStateException("There are no atoms to export.");
        }
        int n = data.atoms.size();
        Map<Atom, Integer> index = new HashMap<>();
        for (int i = 0; i < data.atoms.size(); i++) {
            index.put(data.atoms.get(i), i + 1);
        }

        double scale = coordinateScale(data);
        double minX = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Atom atom : data.atoms) {
            minX = Math.min(minX, atom.getX());
            maxY = Math.max(maxY, atom.getY());
        }

        StringBuilder out = new StringBuilder(4096);
        out.append(sanitize(data.title)).append('\n');
        out.append("  MolView Studio  2D").append('\n');
        out.append("  Organic structure export").append('\n');
        out.append(String.format(Locale.ROOT, "%3d%3d  0  0  0  0  0  0  0  0999 V2000\n",
                data.atoms.size(), data.bonds.size()));

        for (Atom atom : data.atoms) {
            double x = (atom.getX() - minX) * scale + 1.0;
            double y = (maxY - atom.getY()) * scale + 1.0;
            String symbol = atom.exportSymbol();
            out.append(String.format(Locale.ROOT,
                    "%10.4f%10.4f%10.4f %-3s%2d", x, y, 0.0, symbol, 0));
            for (int i = 0; i < 11; i++) {
                out.append(String.format(Locale.ROOT, "%3d", 0));
            }
            out.append('\n');
        }
        for (Bond bond : data.bonds) {
            Integer from = index.get(bond.getFrom());
            Integer to = index.get(bond.getTo());
            if (from == null || to == null) {
                continue;
            }
            out.append(String.format(Locale.ROOT, "%3d%3d%3d%3d%3d%3d%3d\n",
                    from, to, bond.getType().molBondType(), bond.getType().molStereo(),
                    0, 0, 0));
        }

        List<Integer> chargedAtoms = new ArrayList<>();
        for (Atom atom : data.atoms) {
            int charge = data.charges.getOrDefault(atom, 0);
            if (charge != 0) {
                chargedAtoms.add(index.get(atom));
            }
        }
        for (int i = 0; i < chargedAtoms.size(); i += 8) {
            int end = Math.min(chargedAtoms.size(), i + 8);
            out.append(String.format(Locale.ROOT, "M  CHG%3d", end - i));
            for (int j = i; j < end; j++) {
                int atomIndex = chargedAtoms.get(j);
                Atom atom = data.atoms.get(atomIndex - 1);
                out.append(String.format(Locale.ROOT, "%4d%4d", atomIndex,
                        data.charges.getOrDefault(atom, 0)));
            }
            out.append('\n');
        }

        List<Integer> radicalAtoms = new ArrayList<>();
        for (Atom atom : data.atoms) {
            int count = data.radicals.getOrDefault(atom, 0);
            if (count > 0) {
                radicalAtoms.add(index.get(atom));
            }
        }
        for (int i = 0; i < radicalAtoms.size(); i += 8) {
            int end = Math.min(radicalAtoms.size(), i + 8);
            out.append(String.format(Locale.ROOT, "M  RAD%3d", end - i));
            for (int j = i; j < end; j++) {
                int atomIndex = radicalAtoms.get(j);
                Atom atom = data.atoms.get(atomIndex - 1);
                int count = Math.min(2, Math.max(1, data.radicals.getOrDefault(atom, 1)));
                out.append(String.format(Locale.ROOT, "%4d%4d", atomIndex, count));
            }
            out.append('\n');
        }
        out.append("M  END\n");
        return out.toString();
    }

    private static double coordinateScale(MolData data) {
        double sum = 0;
        int count = 0;
        for (Bond bond : data.bonds) {
            double length = bond.length();
            if (length > 1) {
                sum += length;
                count++;
            }
        }
        double average = count == 0 ? 60 : sum / count;
        return Math.max(0.006, Math.min(0.12, 1.5 / average));
    }

    private static Map<Atom, Integer> charges(ChemDocument document) {
        Map<Atom, Integer> result = new HashMap<>();
        for (Atom atom : document.atoms()) {
            int charge = document.formalCharge(atom);
            if (charge != 0) {
                result.put(atom, charge);
            }
        }
        return result;
    }

    private static Map<Atom, Integer> partialCharges(ChemDocument document) {
        Map<Atom, Integer> result = new HashMap<>();
        for (ChargeMark mark : document.chargeMarks()) {
            if (!mark.getKind().isPartial() || mark.getAttachedAtomId() == null) {
                continue;
            }
            Atom atom = document.findAtom(mark.getAttachedAtomId());
            if (atom != null) {
                result.merge(atom, mark.getKind() == ChargeKind.PARTIAL_POSITIVE ? 1 : -1, Integer::sum);
            }
        }
        return result;
    }

    private static Map<Atom, Integer> radicals(ChemDocument document) {
        Map<Atom, Integer> result = new HashMap<>();
        for (ElectronMark mark : document.electronMarks()) {
            if (mark.getKind() == ElectronKind.RADICAL && mark.getAttachedAtomId() != null) {
                Atom atom = document.findAtom(mark.getAttachedAtomId());
                if (atom != null) {
                    result.merge(atom, 1, Integer::sum);
                }
            }
        }
        return result;
    }

    private static Map<Atom, String> genericAliases(ChemDocument document) {
        Map<Atom, String> result = new HashMap<>();
        for (Atom atom : document.atoms()) {
            if (atom.isGeneric()) {
                result.put(atom, atom.displayLabel());
            }
        }
        return result;
    }

    private static Map<Atom, Integer> filterCharges(Map<Atom, Integer> source, Set<Atom> atoms) {
        Map<Atom, Integer> result = new HashMap<>();
        for (Map.Entry<Atom, Integer> entry : source.entrySet()) {
            if (atoms.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Map<Atom, Integer> filterRadicals(Map<Atom, Integer> source, Set<Atom> atoms) {
        return filterCharges(source, atoms);
    }

    private static Map<Atom, String> filterAliases(Map<Atom, String> source, Set<Atom> atoms) {
        Map<Atom, String> result = new HashMap<>();
        for (Map.Entry<Atom, String> entry : source.entrySet()) {
            if (atoms.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static String aliasesText(Map<Atom, String> aliases, List<Atom> atoms) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < atoms.size(); i++) {
            String alias = aliases.get(atoms.get(i));
            if (alias != null) {
                if (text.length() > 0) {
                    text.append("; ");
                }
                text.append(i + 1).append('=').append(alias);
            }
        }
        return text.toString();
    }

    private static String partialText(Map<Atom, Integer> partials, List<Atom> atoms) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < atoms.size(); i++) {
            Integer partial = partials.get(atoms.get(i));
            if (partial != null) {
                if (text.length() > 0) {
                    text.append("; ");
                }
                text.append(i + 1).append('=').append(partial > 0 ? "delta+" : "delta-");
            }
        }
        return text.toString();
    }

    private static List<List<Atom>> components(ChemDocument document) {
        Map<Atom, List<Bond>> adjacency = new HashMap<>();
        for (Atom atom : document.atoms()) {
            adjacency.put(atom, new ArrayList<>());
        }
        for (Bond bond : document.bonds()) {
            if (bond.getType() == BondType.HYDROGEN) {
                continue;
            }
            adjacency.computeIfAbsent(bond.getFrom(), k -> new ArrayList<>()).add(bond);
            adjacency.computeIfAbsent(bond.getTo(), k -> new ArrayList<>()).add(bond);
        }
        Set<Atom> visited = new HashSet<>();
        List<List<Atom>> result = new ArrayList<>();
        for (Atom atom : document.atoms()) {
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
                for (Bond bond : adjacency.getOrDefault(current, Collections.emptyList())) {
                    Atom other = bond.other(current);
                    if (!visited.contains(other)) {
                        queue.addLast(other);
                    }
                }
            }
            result.add(component);
        }
        return result;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "Untitled";
        }
        return value.replace('\n', ' ').replace('\r', ' ').strip();
    }

    private static final class MolData {
        private final String title;
        private final List<Atom> atoms;
        private final List<Bond> bonds;
        private final Map<Atom, Integer> charges;
        private final Map<Atom, Integer> radicals;
        private final Map<Atom, String> aliases;
        private final Map<Atom, Integer> partialCharges;

        private MolData(String title, List<Atom> atoms, List<Bond> bonds,
                        Map<Atom, Integer> charges, Map<Atom, Integer> radicals,
                        Map<Atom, String> aliases, Map<Atom, Integer> partialCharges) {
            this.title = title;
            this.atoms = atoms;
            this.bonds = bonds;
            this.charges = charges;
            this.radicals = radicals;
            this.aliases = aliases;
            this.partialCharges = partialCharges;
        }
    }
}
