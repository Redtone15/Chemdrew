package com.molview.app;

import com.molview.app.editor.EditorController;
import com.molview.app.io.ExportFormat;
import com.molview.app.io.ExportOptions;
import com.molview.app.io.ExportService;
import com.molview.app.io.Json;
import com.molview.app.io.LoadedProject;
import com.molview.app.io.MolfileExporter;
import com.molview.app.io.PagePreset;
import com.molview.app.io.ProjectIO;
import com.molview.app.io.SmilesExporter;
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
import com.molview.app.model.Element;
import com.molview.app.model.Geometry;
import com.molview.app.model.HexGrid;
import com.molview.app.model.ImplicitHydrogens;
import com.molview.app.model.PeriodicTable;
import com.molview.app.model.TextLabel;
import com.molview.app.model.Tool;
import com.molview.app.model.ViewState;
import com.molview.app.render.SvgExporter;
import com.molview.app.ui.SampleProjects;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Dependency-free integration tests; run with test.bat. */
public final class SelfTest {
    private static int passed;
    private static int failed;

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("MolView Studio self-test");
        testPeriodicTable();
        testHexGrid();
        testSketcherDefaults();
        testImplicitHydrogens();
        testModelEditing();
        testSmiles();
        testMolfile();
        testProjectRoundtrip();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> fxFailure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                testRasterAndPdfExports();
                testSvgExport();
            } catch (Throwable t) {
                fxFailure.set(t);
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (fxFailure.get() != null) {
            failed++;
            System.out.println("FAIL  JavaFX export tests: " + fxFailure.get());
        }

        System.out.println();
        System.out.println("Passed: " + passed + "  Failed: " + failed);
        Platform.exit();
        Thread.sleep(250);
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void testPeriodicTable() {
        check(PeriodicTable.all().size() == 118, "periodic table has 118 elements");
        Element carbon = PeriodicTable.bySymbol("C");
        check(carbon != null && carbon.atomicNumber() == 6, "carbon lookup");
        check(PeriodicTable.bySymbol("Og") != null, "oganesson lookup");
        check("Carbon".equals(PeriodicTable.nameOf("C")), "element name lookup");
    }

    private static void testHexGrid() {
        HexGrid.Vertex start = HexGrid.nearestVertex(100, 100);
        check(HexGrid.isVertex(start.x(), start.y(), 0.001), "nearest grid vertex is exact");
        check(HexGrid.neighbors(start).size() == 3, "honeycomb vertices have degree three");
        HexGrid.Vertex target = HexGrid.nearestVertex(520, 130);
        List<HexGrid.Vertex> path = HexGrid.path(start, target);
        check(path.size() > 2, "hex path has multiple unit steps");
        check(path.get(0).distanceTo(start) < 1.0
                        && path.get(path.size() - 1).distanceTo(target) < 1.0,
                "hex path reaches its endpoints");
        boolean allUnit = true;
        for (int i = 1; i < path.size(); i++) {
            allUnit &= Math.abs(path.get(i - 1).distanceTo(path.get(i)) - HexGrid.EDGE) < 0.01;
        }
        check(allUnit, "every hex path step equals one hexagon edge");
    }

    private static void testSketcherDefaults() {
        EditorController controller = new EditorController();
        check(controller.getTool() == Tool.ATOM, "editor starts with the carbon/chain tool active");
        check("C".equals(controller.getActiveElement()), "editor starts with carbon selected");
        HexGrid.Vertex start = HexGrid.nearestVertex(200, 300);
        HexGrid.Vertex end = HexGrid.nearestVertex(560, 320);
        List<HexGrid.Vertex> path = HexGrid.path(start, end);
        controller.setActiveElement("N");
        controller.buildAlkaneChain(path);
        check(controller.getDocument().atoms().size() == path.size(),
                "alkane chain creates one atom per grid vertex");
        check(controller.getDocument().bonds().size() == path.size() - 1,
                "alkane chain creates one bond per step");
        check(controller.getDocument().atoms().get(0).getElementSymbol().equals("N"),
                "chain starts with the selected element");
        boolean carbons = controller.getDocument().atoms().stream().skip(1)
                .allMatch(atom -> atom.getElementSymbol().equals("C"));
        check(carbons, "chain body is carbon");
        boolean onGrid = controller.getDocument().atoms().stream()
                .allMatch(atom -> HexGrid.isVertex(atom.getX(), atom.getY(), 0.1));
        check(onGrid, "chain atoms are locked to hex vertices");
        boolean oneEdge = controller.getDocument().bonds().stream()
                .allMatch(bond -> Math.abs(bond.length() - HexGrid.EDGE) < 0.01);
        check(oneEdge, "chain bonds are strictly one hexagon edge");

        ChemDocument example = SampleProjects.sn2Mechanism();
        boolean exampleOnGrid = example.atoms().stream()
                .allMatch(atom -> HexGrid.isVertex(atom.getX(), atom.getY(), 0.1));
        check(exampleOnGrid, "built-in example atoms sit on the hex grid");
        boolean exampleEdges = example.bonds().stream()
                .allMatch(bond -> Math.abs(bond.length() - HexGrid.EDGE) < 0.01);
        check(exampleEdges, "built-in example bonds are one hexagon edge");

        EditorController rotation = new EditorController();
        HexGrid.Vertex rotationStart = HexGrid.nearestVertex(200, 300);
        HexGrid.Vertex rotationEnd = HexGrid.nearestVertex(560, 320);
        rotation.setActiveElement("C");
        rotation.buildAlkaneChain(HexGrid.path(rotationStart, rotationEnd));
        rotation.selectAll();
        rotation.rotateSelection(90);
        Bounds rotatedBounds = null;
        for (Atom atom : rotation.getDocument().atoms()) {
            Bounds point = Bounds.point(atom.getX(), atom.getY());
            rotatedBounds = rotatedBounds == null ? point : rotatedBounds.union(point);
        }
        check(rotatedBounds != null && rotatedBounds.width() > rotatedBounds.height() * 1.2,
                "rotated chain remains horizontal");
        boolean rotatedOnGrid = rotation.getDocument().atoms().stream()
                .allMatch(atom -> HexGrid.isVertex(atom.getX(), atom.getY(), 0.1));
        check(rotatedOnGrid, "rotated chain remains on the hex lattice");
        boolean rotatedEdges = rotation.getDocument().bonds().stream()
                .allMatch(bond -> Math.abs(bond.length() - HexGrid.EDGE) < 0.05);
        check(rotatedEdges, "rotated chain bonds remain one hexagon edge");

        EditorController charged = new EditorController();
        HexGrid.Vertex chargedStart = HexGrid.nearestVertex(100, 500);
        HexGrid.Vertex chargedEnd = HexGrid.nearestVertex(380, 510);
        charged.setActiveElement("C");
        charged.buildAlkaneChain(HexGrid.path(chargedStart, chargedEnd));
        Atom middle = charged.getDocument().atoms().get(1);
        ChargeMark delta = charged.addCharge(middle.getX(), middle.getY(),
                ChargeKind.PARTIAL_POSITIVE, middle);
        double closestBond = Double.MAX_VALUE;
        for (Bond b : charged.getDocument().bonds()) {
            closestBond = Math.min(closestBond, Geometry.distanceToSegment(
                    delta.getX(), delta.getY(),
                    b.getFrom().getX(), b.getFrom().getY(),
                    b.getTo().getX(), b.getTo().getY()));
        }
        check(closestBond > 18, "delta label is displaced clear of bond strokes");

        EditorController lonePairs = new EditorController();
        lonePairs.setActiveElement("C");
        HexGrid.Vertex hostVertex = HexGrid.nearestVertex(360, 540);
        Atom hostAtom = lonePairs.addAtomAt(hostVertex.x(), hostVertex.y());
        HexGrid.Vertex leftNeighbour = null;
        for (HexGrid.Vertex neighbour : HexGrid.neighbors(hostVertex)) {
            if (leftNeighbour == null || neighbour.x() < leftNeighbour.x()) {
                leftNeighbour = neighbour;
            }
        }
        Atom leftAtom = lonePairs.addAtomAt(leftNeighbour.x(), leftNeighbour.y());
        lonePairs.addBond(hostAtom, leftAtom, BondType.SINGLE);
        ElectronMark firstPair = lonePairs.addElectron(hostAtom.getX(), hostAtom.getY(),
                ElectronKind.LONE_PAIR, hostAtom);
        ElectronMark secondPair = lonePairs.addElectron(hostAtom.getX(), hostAtom.getY(),
                ElectronKind.LONE_PAIR, hostAtom);
        ElectronMark thirdPair = lonePairs.addElectron(hostAtom.getX(), hostAtom.getY(),
                ElectronKind.LONE_PAIR, hostAtom);
        ElectronMark fourthPair = lonePairs.addElectron(hostAtom.getX(), hostAtom.getY(),
                ElectronKind.LONE_PAIR, hostAtom);
        check(firstPair.getX() > hostAtom.getX() + 15
                        && Math.abs(firstPair.getY() - hostAtom.getY()) < 2,
                "left-side bond starts lone pairs on the right");
        check(secondPair.getY() < hostAtom.getY() - 15,
                "second lone pair goes above the atom");
        check(thirdPair.getY() > hostAtom.getY() + 15,
                "third lone pair goes below the atom");
        check(fourthPair.getX() < hostAtom.getX() - 15,
                "fourth lone pair goes to the left");

        EditorController mirrored = new EditorController();
        mirrored.setActiveElement("C");
        Atom mirroredHost = mirrored.addAtomAt(hostVertex.x(), hostVertex.y());
        HexGrid.Vertex rightNeighbour = null;
        for (HexGrid.Vertex neighbour : HexGrid.neighbors(hostVertex)) {
            if (rightNeighbour == null || neighbour.x() > rightNeighbour.x()) {
                rightNeighbour = neighbour;
            }
        }
        Atom rightAtom = mirrored.addAtomAt(rightNeighbour.x(), rightNeighbour.y());
        mirrored.addBond(mirroredHost, rightAtom, BondType.SINGLE);
        ElectronMark mirroredPair = mirrored.addElectron(
                mirroredHost.getX(), mirroredHost.getY(), ElectronKind.LONE_PAIR, mirroredHost);
        check(mirroredPair.getX() < mirroredHost.getX() - 15,
                "right-side bond mirrors lone pairs to the left");

        EditorController relayout = new EditorController();
        relayout.setActiveElement("C");
        Atom relayoutHost = relayout.addAtomAt(620, 540);
        ElectronMark relayoutPair = relayout.addElectron(
                relayoutHost.getX(), relayoutHost.getY(),
                ElectronKind.LONE_PAIR, relayoutHost);
        check(relayoutPair.getX() > relayoutHost.getX() + 15,
                "bond-free atom starts its lone pair on the right");
        HexGrid.Vertex rightVertex = null;
        for (HexGrid.Vertex neighbour : HexGrid.neighbors(
                HexGrid.nearestVertex(relayoutHost.getX(), relayoutHost.getY()))) {
            if (rightVertex == null || neighbour.x() > rightVertex.x()) {
                rightVertex = neighbour;
            }
        }
        Atom relayoutRight = relayout.addAtomAt(rightVertex.x(), rightVertex.y());
        relayout.addBond(relayoutHost, relayoutRight, BondType.SINGLE);
        check(relayoutPair.getX() < relayoutHost.getX() - 15,
                "new right-side bond reflows lone pairs to the left");

        EditorController threeBonds = new EditorController();
        threeBonds.setActiveElement("C");
        HexGrid.Vertex hubVertex = HexGrid.nearestVertex(700, 540);
        Atom hub = threeBonds.addAtomAt(hubVertex.x(), hubVertex.y());
        List<Atom> spokes = new ArrayList<>();
        for (HexGrid.Vertex neighbour : HexGrid.neighbors(hubVertex)) {
            spokes.add(threeBonds.addAtomAt(neighbour.x(), neighbour.y()));
        }
        for (Atom spoke : spokes) {
            threeBonds.addBond(hub, spoke, BondType.SINGLE);
        }
        ElectronMark gapPairOne = threeBonds.addElectron(
                hub.getX(), hub.getY(), ElectronKind.LONE_PAIR, hub);
        ElectronMark gapPairTwo = threeBonds.addElectron(
                hub.getX(), hub.getY(), ElectronKind.LONE_PAIR, hub);
        double closestOne = Double.MAX_VALUE;
        double closestTwo = Double.MAX_VALUE;
        for (Bond bond : threeBonds.getDocument().bonds()) {
            closestOne = Math.min(closestOne, Geometry.distanceToSegment(
                    gapPairOne.getX(), gapPairOne.getY(),
                    bond.getFrom().getX(), bond.getFrom().getY(),
                    bond.getTo().getX(), bond.getTo().getY()));
            closestTwo = Math.min(closestTwo, Geometry.distanceToSegment(
                    gapPairTwo.getX(), gapPairTwo.getY(),
                    bond.getFrom().getX(), bond.getFrom().getY(),
                    bond.getTo().getX(), bond.getTo().getY()));
        }
        check(closestOne > 20 && closestTwo > 20,
                "lone pairs sit between the three bonds");
        check(Math.hypot(gapPairOne.getX() - gapPairTwo.getX(),
                        gapPairOne.getY() - gapPairTwo.getY()) > 20,
                "multiple lone pairs occupy different bond gaps");

        Atom freeSource = Atom.element("Br", 0, 0);
        threeBonds.setBondAngleMode(BondAngleMode.SNAP_60);
        double[] snapped60 = threeBonds.freeBondEndpoint(freeSource, 50, 86.6025);
        double angle60 = Math.atan2(snapped60[1], snapped60[0]);
        check(Math.abs(angle60 - Math.PI / 3) < 1e-6,
                "60-degree mode snaps free bonds to 60-degree directions");
        threeBonds.setBondAngleMode(BondAngleMode.FREE);
        double[] free45 = threeBonds.freeBondEndpoint(freeSource, 50, 50);
        double angle45 = Math.atan2(free45[1], free45[0]);
        check(Math.abs(angle45 - Math.PI / 4) < 1e-6,
                "free mode keeps the unconstrained angle");
        threeBonds.setBondAngleMode(BondAngleMode.CUSTOM);
        threeBonds.setCustomBondAngle(45);
        double[] custom = threeBonds.freeBondEndpoint(freeSource, 50, 60);
        double customAngle = Math.toDegrees(Math.atan2(custom[1], custom[0]));
        check(Math.abs(customAngle - 45) < 1e-6,
                "custom mode snaps to the chosen step");

        EditorController annotationSize = new EditorController();
        annotationSize.setActiveElement("O");
        HexGrid.Vertex sizeHostVertex = HexGrid.nearestVertex(780, 540);
        Atom sizeHost = annotationSize.addAtomAt(
                sizeHostVertex.x(), sizeHostVertex.y());
        HexGrid.Vertex sizeLeft = null;
        for (HexGrid.Vertex n : HexGrid.neighbors(sizeHostVertex)) {
            if (sizeLeft == null || n.x() < sizeLeft.x()) {
                sizeLeft = n;
            }
        }
        Atom sizePartner = annotationSize.addAtomAt(sizeLeft.x(), sizeLeft.y());
        annotationSize.addBond(sizeHost, sizePartner, BondType.SINGLE);
        ElectronMark resizedElectron = annotationSize.addElectron(
                sizeHost.getX(), sizeHost.getY(), ElectronKind.LONE_PAIR, sizeHost);
        ChargeMark resizedDelta = annotationSize.addCharge(
                sizeHost.getX(), sizeHost.getY(), ChargeKind.PARTIAL_POSITIVE, sizeHost);
        annotationSize.setElectronDistance(42);
        annotationSize.setDeltaLabelDistance(46);
        double electronRadius = Math.hypot(
                resizedElectron.getX() - sizeHost.getX(),
                resizedElectron.getY() - sizeHost.getY());
        double deltaRadius = Math.hypot(
                resizedDelta.getX() - sizeHost.getX(),
                resizedDelta.getY() - sizeHost.getY());
        check(Math.abs(electronRadius - 42) < 1e-6,
                "electron distance setting reflows lone pairs");
        check(Math.abs(deltaRadius - 46) < 1e-6,
                "delta label distance setting reflows δ labels");
        annotationSize.setShowDeltaRings(false);
        check(!annotationSize.isShowDeltaRings(),
                "delta rings can be disabled");
    }

    private static void testImplicitHydrogens() {
        ChemDocument methane = new ChemDocument("Methane");
        Atom carbon = Atom.element("C", 0, 0);
        methane.add(carbon);
        check(ImplicitHydrogens.count(carbon, methane) == 4,
                "isolated carbon has four implicit hydrogens");
        check(ImplicitHydrogens.label(carbon, methane).equals("CH₄"),
                "isolated carbon is labelled CH₄");
        Atom carbon2 = Atom.element("C", 40, 0);
        methane.add(carbon2);
        methane.add(new Bond(carbon, carbon2, BondType.SINGLE));
        check(ImplicitHydrogens.count(carbon, methane) == 3,
                "carbon loses one hydrogen per single bond");

        ChemDocument water = new ChemDocument("Water");
        Atom oxygen = Atom.element("O", 0, 0);
        water.add(oxygen);
        check(ImplicitHydrogens.label(oxygen, water).equals("H₂O"),
                "isolated oxygen is labelled H₂O");
        Atom oxygenCarbon = Atom.element("C", 40, 0);
        water.add(oxygenCarbon);
        water.add(new Bond(oxygen, oxygenCarbon, BondType.SINGLE));
        check(ImplicitHydrogens.count(oxygen, water) == 1,
                "oxygen loses one hydrogen per single bond");
        check(ImplicitHydrogens.label(oxygen, water).equals("HO"),
                "one-bond oxygen keeps its element on the bond side");

        ChemDocument hydrogen = new ChemDocument("Hydrogen");
        Atom h = Atom.element("H", 0, 0);
        hydrogen.add(h);
        check(ImplicitHydrogens.label(h, hydrogen).equals("H₂"),
                "isolated hydrogen is labelled H₂");
        Atom hCarbon = Atom.element("C", 40, 0);
        hydrogen.add(hCarbon);
        hydrogen.add(new Bond(h, hCarbon, BondType.SINGLE));
        check(ImplicitHydrogens.count(h, hydrogen) == 0,
                "bonded hydrogen has no implicit hydrogen");

        ChemDocument hydrogenHalide = new ChemDocument("Hydrogen bromide");
        Atom bromine = Atom.element("Br", 0, 0);
        hydrogenHalide.add(bromine);
        check(ImplicitHydrogens.label(bromine, hydrogenHalide).equals("BrH"),
                "isolated bromine is labelled BrH");
        Atom brCarbon = Atom.element("C", 40, 0);
        hydrogenHalide.add(brCarbon);
        hydrogenHalide.add(new Bond(bromine, brCarbon, BondType.SINGLE));
        check(ImplicitHydrogens.count(bromine, hydrogenHalide) == 0,
                "bonded bromine has no implicit hydrogen");

        ChemDocument phosphorus = new ChemDocument("Phosphine");
        Atom p = Atom.element("P", 0, 0);
        phosphorus.add(p);
        check(ImplicitHydrogens.label(p, phosphorus).equals("PH₃"),
                "isolated phosphorus is labelled PH₃");
        ChemDocument sulfide = new ChemDocument("Hydrogen sulfide");
        Atom sulfur = Atom.element("S", 0, 0);
        sulfide.add(sulfur);
        check(ImplicitHydrogens.label(sulfur, sulfide).equals("H₂S"),
                "isolated sulfur is labelled H₂S");

        ChemDocument hydroxide = new ChemDocument("Hydroxide");
        Atom chargedO = Atom.element("O", 0, 0);
        Atom explicitH = Atom.element("H", 40, 0);
        hydroxide.add(chargedO);
        hydroxide.add(explicitH);
        hydroxide.add(new Bond(chargedO, explicitH, BondType.SINGLE));
        ChargeMark minus = new ChargeMark(ChargeKind.NEGATIVE, -15, -15);
        minus.setAttachedAtomId(chargedO.getId());
        hydroxide.add(minus);
        check(ImplicitHydrogens.count(chargedO, hydroxide) == 0,
                "charged oxygen with an explicit hydrogen gains no extra hydrogen");

        ChemDocument rightBonded = new ChemDocument("Right");
        Atom rightOxygen = Atom.element("O", 0, 0);
        Atom rightCarbon = Atom.element("C", 40, 0);
        rightBonded.add(rightOxygen);
        rightBonded.add(rightCarbon);
        rightBonded.add(new Bond(rightOxygen, rightCarbon, BondType.SINGLE));
        check(ImplicitHydrogens.label(rightOxygen, rightBonded).equals("HO"),
                "a right-hand bond puts oxygen on the right and H on the left");

        ChemDocument leftBonded = new ChemDocument("Left");
        Atom leftOxygen = Atom.element("O", 0, 0);
        Atom leftCarbon = Atom.element("C", -40, 0);
        leftBonded.add(leftOxygen);
        leftBonded.add(leftCarbon);
        leftBonded.add(new Bond(leftOxygen, leftCarbon, BondType.SINGLE));
        check(ImplicitHydrogens.label(leftOxygen, leftBonded).equals("OH"),
                "a left-hand bond puts oxygen on the left and H on the right");

        ChemDocument carbonRight = new ChemDocument("Methyl");
        Atom methylCarbon = Atom.element("C", 0, 0);
        Atom methylPartner = Atom.element("C", 40, 0);
        carbonRight.add(methylCarbon);
        carbonRight.add(methylPartner);
        carbonRight.add(new Bond(methylCarbon, methylPartner, BondType.SINGLE));
        check(ImplicitHydrogens.label(methylCarbon, carbonRight).equals("H₃C"),
                "a right-hand bond reverses a carbon formula");
    }

    private static void testModelEditing() {
        EditorController controller = new EditorController();
        controller.addAtomAt(100, 100);
        check(controller.getDocument().atoms().size() == 1, "add atom");
        controller.undo();
        check(controller.getDocument().atoms().isEmpty(), "undo atom");
        controller.redo();
        check(controller.getDocument().atoms().size() == 1, "redo atom");

        ChemDocument doc = new ChemDocument("Rotation");
        Atom a = Atom.element("C", 0, 0);
        Atom b = Atom.element("C", 100, 0);
        doc.add(a);
        doc.add(b);
        doc.add(new Bond(a, b, BondType.SINGLE));
        doc.selectAll();
        doc.rotateSelected(Math.PI / 2);
        check(Math.abs(a.getX() - 50) < 0.001 && Math.abs(a.getY() + 50) < 0.001,
                "rotation around selection center (a): " + a.getX() + "," + a.getY());
        check(Math.abs(b.getX() - 50) < 0.001 && Math.abs(b.getY() - 50) < 0.001,
                "rotation around selection center (b): " + b.getX() + "," + b.getY());

        ChemDocument deep = SampleProjects.sn2Mechanism().deepCopy();
        check(deep.objects().size() == SampleProjects.sn2Mechanism().objects().size(),
                "deep copy object count");

        EditorController clipboardController = new EditorController();
        ChemDocument original = new ChemDocument("Clipboard");
        Atom oxygen = Atom.element("O", 50, 50);
        ChargeMark charge = new ChargeMark(ChargeKind.NEGATIVE, 68, 34);
        charge.setAttachedAtomId(oxygen.getId());
        original.add(oxygen);
        original.add(charge);
        original.add(new TextLabel("note", 140, 50));
        original.selectAll();
        clipboardController.replaceDocument(original, null, true);
        clipboardController.copySelection();
        clipboardController.pasteClipboard();
        check(clipboardController.getDocument().chargeMarks().size() == 2,
                "paste duplicates charge annotations");
        check(clipboardController.getDocument().textLabels().size() == 2,
                "paste duplicates text annotations");
        ChargeMark pasted = clipboardController.getDocument().chargeMarks().get(1);
        check(pasted.getAttachedAtomId() != null
                        && clipboardController.getDocument().findAtom(pasted.getAttachedAtomId()) != null,
                "paste remaps attached atom ids");
    }

    private static void testSmiles() {
        ChemDocument ethanol = new ChemDocument("Ethanol");
        Atom c1 = Atom.element("C", 0, 0);
        Atom c2 = Atom.element("C", 60, 0);
        Atom o = Atom.element("O", 120, 0);
        ethanol.add(c1);
        ethanol.add(c2);
        ethanol.add(o);
        ethanol.add(new Bond(c1, c2, BondType.SINGLE));
        ethanol.add(new Bond(c2, o, BondType.SINGLE));
        String ethanolSmiles = SmilesExporter.toSmiles(ethanol);
        check(ethanolSmiles.equals("CCO"), "ethanol SMILES = CCO (got " + ethanolSmiles + ")");

        ChemDocument benzene = new ChemDocument("Benzene");
        List<Atom> ring = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6;
            Atom atom = Atom.element("C", 300 + Math.cos(angle) * 60, 300 + Math.sin(angle) * 60);
            ring.add(atom);
            benzene.add(atom);
        }
        for (int i = 0; i < 6; i++) {
            benzene.add(new Bond(ring.get(i), ring.get((i + 1) % 6), BondType.AROMATIC));
        }
        String benzeneSmiles = SmilesExporter.toSmiles(benzene);
        check(benzeneSmiles.matches("c1ccccc1"), "benzene aromatic SMILES (got " + benzeneSmiles + ")");

        ChemDocument acetate = new ChemDocument("Acetate");
        Atom ac1 = Atom.element("C", 0, 0);
        Atom ac2 = Atom.element("C", 60, 0);
        Atom oxy = Atom.element("O", 120, 0);
        Atom oxy2 = Atom.element("O", 60, -60);
        acetate.addAll(List.of(ac1, ac2, oxy, oxy2));
        acetate.add(new Bond(ac1, ac2, BondType.SINGLE));
        acetate.add(new Bond(ac2, oxy, BondType.SINGLE));
        acetate.add(new Bond(ac2, oxy2, BondType.DOUBLE));
        ChargeMark negative = new ChargeMark(ChargeKind.NEGATIVE, 138, -20);
        negative.setAttachedAtomId(oxy.getId());
        acetate.add(negative);
        String acetateSmiles = SmilesExporter.toSmiles(acetate);
        check(acetateSmiles.contains("[O-]"), "charged oxygen in SMILES (got " + acetateSmiles + ")");
        check(acetateSmiles.contains("="), "double bond in SMILES (got " + acetateSmiles + ")");

        ChemDocument water = new ChemDocument("Water");
        Atom waterO = Atom.element("O", 0, 0);
        Atom waterH1 = Atom.element("H", -50, 0);
        Atom waterH2 = Atom.element("H", 50, 0);
        water.addAll(List.of(waterO, waterH1, waterH2));
        water.add(new Bond(waterH1, waterO, BondType.SINGLE));
        water.add(new Bond(waterO, waterH2, BondType.SINGLE));
        String waterSmiles = SmilesExporter.toSmiles(water);
        check(waterSmiles.contains("[H]") && waterSmiles.contains("O"),
                "explicit hydrogen SMILES is valid (got " + waterSmiles + ")");
    }

    private static void testMolfile() {
        ChemDocument doc = SampleProjects.sn2Mechanism();
        String mol = MolfileExporter.toMol(doc);
        check(mol.contains("V2000"), "MOL V2000 header");
        check(mol.contains("M  CHG"), "MOL formal charges");
        check(mol.contains("M  END"), "MOL end record");
        String sdf = MolfileExporter.toSdf(doc);
        int records = sdf.split("\\$\\$\\$\\$", -1).length - 1;
        check(records >= 2, "SDF has multiple component records (got " + records + ")");
        check(sdf.contains(">  <MOLVIEW_COMPONENT>"), "SDF metadata");
        check(SmilesExporter.toSmiles(doc).contains("Br"), "sample SMILES contains bromine");
    }

    private static void testProjectRoundtrip() throws Exception {
        ChemDocument doc = SampleProjects.sn2Mechanism();
        doc.add(new Bracket(null, BracketType.CURLY, 100, 100, 200, 100, "n",
                BracketLabelPosition.BOTTOM_RIGHT));
        doc.add(new TextLabel("test label", 500, 600));
        doc.add(new Arrow(ArrowType.FISHHOOK, 500, 500, 550, 470));
        ViewState view = new ViewState(1.4, 120, 80, true, true, true);
        Path dir = Path.of("build", "test-output");
        Files.createDirectories(dir);
        Path file = dir.resolve("roundtrip.molview");
        ProjectIO.save(doc, view, file);
        LoadedProject loaded = ProjectIO.load(file);
        check(loaded.document().objects().size() == doc.objects().size(),
                "project roundtrip object count");
        check(loaded.document().atoms().size() == doc.atoms().size(), "project roundtrip atoms");
        check(loaded.document().bonds().size() == doc.bonds().size(), "project roundtrip bonds");
        check(loaded.viewState().showCarbonLabels(), "project roundtrip view state");
        check(Math.abs(loaded.document().atoms().get(0).getX() - doc.atoms().get(0).getX()) < 0.01,
                "project roundtrip coordinates");
        Map<String, Object> json = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        check(ProjectIO.FORMAT.equals(json.get("format")), "project JSON format marker");
        check(json.get("annotations") instanceof List<?>, "project annotations array");
    }

    private static void testSvgExport() throws Exception {
        ChemDocument doc = SampleProjects.sn2Mechanism();
        Bracket curly = new Bracket(null, BracketType.CURLY, 120, 120, 260, 150, "n",
                BracketLabelPosition.BOTTOM_RIGHT);
        doc.add(curly);
        doc.add(new TextLabel("A&B <label> \"quoted\"", 500, 620));
        ExportOptions options = new ExportOptions();
        options.setFormat(ExportFormat.SVG);
        Path path = Path.of("build", "test-output", "structure.svg");
        options.setPath(path);
        options.setWidth(1200);
        options.setHeight(800);
        options.setTransparent(true);
        ExportService.export(doc, options, false);
        String svg = Files.readString(path, StandardCharsets.UTF_8);
        check(svg.contains("<svg"), "SVG root element");
        check(svg.contains("<text"), "SVG text elements");
        check(svg.contains("<path") || svg.contains("<line"), "SVG vector paths");
        check(svg.contains("A&amp;B &lt;label&gt;"), "SVG text escaping");
        check(!svg.contains("base64"), "SVG is not a raster wrapper");
        check(svg.contains(">n</text>"), "SVG bracket label");
    }

    private static void testRasterAndPdfExports() throws Exception {
        ChemDocument doc = SampleProjects.sn2Mechanism();
        Path dir = Path.of("build", "test-output");
        Files.createDirectories(dir);

        ExportOptions png = new ExportOptions();
        png.setFormat(ExportFormat.PNG);
        png.setPath(dir.resolve("structure.png"));
        png.setWidth(1000);
        png.setHeight(700);
        png.setTransparent(true);
        ExportService.export(doc, png, false);
        BufferedImage pngImage = ImageIO.read(png.getPath().toFile());
        check(pngImage != null && pngImage.getWidth() == 1000 && pngImage.getHeight() == 700,
                "PNG dimensions");
        check((pngImage.getRGB(0, 0) >>> 24) == 0, "PNG transparent background");

        ExportOptions jpeg = new ExportOptions();
        jpeg.setFormat(ExportFormat.JPEG);
        jpeg.setPath(dir.resolve("structure.jpg"));
        jpeg.setWidth(800);
        jpeg.setHeight(600);
        jpeg.setBackgroundColor("#f8fafc");
        ExportService.export(doc, jpeg, true);
        BufferedImage jpegImage = ImageIO.read(jpeg.getPath().toFile());
        check(jpegImage != null && jpegImage.getWidth() == 800, "JPEG dimensions");

        ExportOptions pdf = new ExportOptions();
        pdf.setFormat(ExportFormat.PDF);
        pdf.setPath(dir.resolve("structure.pdf"));
        pdf.setPagePreset(PagePreset.A4);
        pdf.setLandscape(true);
        pdf.setDpi(150);
        pdf.setMarginPt(36);
        ExportService.export(doc, pdf, true);
        byte[] pdfBytes = Files.readAllBytes(pdf.getPath());
        check(pdfBytes.length > 2000, "PDF has content");
        check(new String(pdfBytes, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-"),
                "PDF signature");
        check(new String(pdfBytes, StandardCharsets.ISO_8859_1).contains("%%EOF"), "PDF trailer");

        javafx.scene.image.Image preview = ExportService.preview(doc, png, false, 600, 400);
        check(preview.getWidth() > 100, "PNG preview renders");

        // Selected-only export.
        doc.atoms().get(0).setSelected(true);
        png.setSelectionOnly(true);
        png.setPath(dir.resolve("selection.png"));
        png.setWidth(400);
        png.setHeight(300);
        ExportService.export(doc, png, false);
        check(Files.size(png.getPath()) > 100, "selection-only PNG export");
        doc.clearSelection();
    }

    private static void check(boolean condition, String message) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + message);
        } else {
            failed++;
            System.out.println("FAIL  " + message);
        }
    }
}
