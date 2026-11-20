package com.molview.app.ui;

import com.molview.app.model.Arrow;
import com.molview.app.model.ArrowType;
import com.molview.app.model.Atom;
import com.molview.app.model.AtomKind;
import com.molview.app.model.Bond;
import com.molview.app.model.BondType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.HexGrid;
import com.molview.app.model.TextLabel;

/**
 * Built-in example drawings. The S_N2 example is laid out on the same
 * pointy-top honeycomb lattice used by the sketcher, so every molecular bond is
 * exactly one hexagon edge and every atom sits on a vertex.
 */
public final class SampleProjects {
    private SampleProjects() {
    }

    private static HexGrid.Vertex v(double x, double y) {
        return HexGrid.canonical(HexGrid.nearestVertex(x, y));
    }

    private static HexGrid.Vertex neighbor(HexGrid.Vertex start, double x, double y) {
        return HexGrid.canonical(HexGrid.neighborToward(start, x, y));
    }

    private static Atom atom(ChemDocument doc, String symbol, HexGrid.Vertex vertex) {
        Atom atom = Atom.element(symbol, vertex.x(), vertex.y());
        doc.add(atom);
        return atom;
    }

    private static void bond(ChemDocument doc, Atom a, Atom b, BondType type) {
        doc.add(new Bond(a, b, type));
    }

    private static void addLonePair(ChemDocument doc, Atom atom, double x, double y) {
        ElectronMark mark = new ElectronMark(ElectronKind.LONE_PAIR, x, y);
        mark.setAttachedAtomId(atom.getId());
        doc.add(mark);
    }

    private static ChargeMark charge(ChemDocument doc, Atom atom, ChargeKind kind,
                                     double x, double y) {
        ChargeMark mark = new ChargeMark(kind, x, y);
        mark.setAttachedAtomId(atom.getId());
        doc.add(mark);
        return mark;
    }

    /** A compact, fully editable, grid-aligned S_N2 mechanism exercise. */
    public static ChemDocument sn2Mechanism() {
        ChemDocument doc = new ChemDocument("Sₙ2 Substitution Mechanism");

        doc.add(new TextLabel("Bimolecular nucleophilic substitution  ·  Sₙ2", 600, 150));
        doc.add(new TextLabel("reactants", 290, 215));
        doc.add(new TextLabel("products", 925, 215));

        // Hydroxide nucleophile.
        HexGrid.Vertex oVertex = v(170, 340);
        HexGrid.Vertex hVertex = neighbor(oVertex, 100, 320);
        Atom oxygen = atom(doc, "O", oVertex);
        Atom hydroxylH = atom(doc, "H", hVertex);
        bond(doc, oxygen, hydroxylH, BondType.SINGLE);
        charge(doc, oxygen, ChargeKind.NEGATIVE, oVertex.x() + 19, oVertex.y() - 20);
        addLonePair(doc, oxygen, oVertex.x() + 4, oVertex.y() - 30);
        ElectronMark attackPair = new ElectronMark(ElectronKind.LONE_PAIR,
                oVertex.x() - 18, oVertex.y() + 14);
        attackPair.setAttachedAtomId(oxygen.getId());
        doc.add(attackPair);
        doc.add(new TextLabel("+", 245, 330));

        // Ethyl bromide: a two-carbon zig-zag terminating in bromine.
        HexGrid.Vertex c1Vertex = v(300, 340);
        HexGrid.Vertex c2Vertex = neighbor(c1Vertex, 380, 320);
        HexGrid.Vertex brVertex = neighbor(c2Vertex, 470, 340);
        Atom carbon1 = atom(doc, "C", c1Vertex);
        Atom carbon2 = atom(doc, "C", c2Vertex);
        Atom bromine = atom(doc, "Br", brVertex);
        bond(doc, carbon1, carbon2, BondType.SINGLE);
        bond(doc, carbon2, bromine, BondType.SINGLE);
        charge(doc, carbon2, ChargeKind.PARTIAL_POSITIVE,
                c2Vertex.x() + 4, c2Vertex.y() - 25);
        charge(doc, bromine, ChargeKind.PARTIAL_NEGATIVE,
                brVertex.x() + 18, brVertex.y() - 18);
        addLonePair(doc, bromine, brVertex.x() + 20, brVertex.y() + 16);
        doc.add(new TextLabel("ethyl bromide", 345, 410));

        // Curved electron-pair arrows.
        doc.add(new Arrow(null, ArrowType.CURVED,
                attackPair.getX(), attackPair.getY(),
                280, 430,
                c2Vertex.x(), c2Vertex.y() + 21));
        doc.add(new Arrow(null, ArrowType.CURVED,
                (c2Vertex.x() + brVertex.x()) / 2, (c2Vertex.y() + brVertex.y()) / 2 - 4,
                c2Vertex.x() + 42, c2Vertex.y() - 45,
                brVertex.x() - 2, brVertex.y() + 11));

        // Reaction arrow and label.
        doc.add(new Arrow(ArrowType.STRAIGHT, 485, 335, 760, 335));
        doc.add(new TextLabel("Sₙ2", 620, 302));

        // Ethanol product: C-C-O-H laid out on the same lattice.
        HexGrid.Vertex p1Vertex = v(820, 340);
        HexGrid.Vertex p2Vertex = neighbor(p1Vertex, 900, 320);
        HexGrid.Vertex poVertex = neighbor(p2Vertex, 980, 340);
        HexGrid.Vertex phVertex = neighbor(poVertex, 1040, 330);
        Atom productC1 = atom(doc, "C", p1Vertex);
        Atom productC2 = atom(doc, "C", p2Vertex);
        Atom productO = atom(doc, "O", poVertex);
        Atom productH = atom(doc, "H", phVertex);
        bond(doc, productC1, productC2, BondType.SINGLE);
        bond(doc, productC2, productO, BondType.SINGLE);
        bond(doc, productO, productH, BondType.SINGLE);
        addLonePair(doc, productO, poVertex.x() - 20, poVertex.y() - 14);
        addLonePair(doc, productO, poVertex.x() + 19, poVertex.y() + 13);
        doc.add(new TextLabel("ethanol", 880, 410));

        // Bromide leaving group.
        doc.add(new TextLabel("+", 1004, 330));
        HexGrid.Vertex pBrVertex = v(1080, 340);
        Atom productBromine = atom(doc, "Br", pBrVertex);
        charge(doc, productBromine, ChargeKind.NEGATIVE,
                pBrVertex.x() + 22, pBrVertex.y() - 20);
        addLonePair(doc, productBromine, pBrVertex.x() - 18, pBrVertex.y() + 13);
        addLonePair(doc, productBromine, pBrVertex.x() + 19, pBrVertex.y() - 12);
        addLonePair(doc, productBromine, pBrVertex.x() + 17, pBrVertex.y() + 18);

        return doc;
    }
}
