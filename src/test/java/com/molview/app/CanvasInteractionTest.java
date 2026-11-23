package com.molview.app;

import com.molview.app.editor.EditorController;
import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.HexGrid;
import com.molview.app.model.Tool;
import com.molview.app.ui.DrawingCanvas;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises the main canvas gestures with synthesized mouse events. */
public final class CanvasInteractionTest extends Application {
    private int checks;
    private int failures;
    private EditorController controller;
    private DrawingCanvas canvas;
    private final Path output = Path.of("build", "test-output");

    @Override
    public void start(Stage stage) {
        controller = new EditorController();
        canvas = new DrawingCanvas(controller);
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, 900, 650);
        stage.setScene(scene);
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(event -> {
            try {
                runGestures();
            } catch (Throwable t) {
                t.printStackTrace();
                failures++;
            }
            System.out.println("Canvas interaction checks: " + checks
                    + " passed, " + failures + " failed");
            stage.close();
            Platform.exit();
            if (failures > 0) {
                System.exit(1);
            }
        });
        pause.play();
    }

    private void runGestures() {
        check(controller.getTool() == Tool.ATOM, "startup tool is the atom/chain tool");
        check("C".equals(controller.getActiveElement()), "startup element is carbon");
        check(controller.getBondAngleMode()
                        == com.molview.app.model.BondAngleMode.SNAP_60,
                "startup new-bond angle mode is 60 degrees");
        controller.setActiveElement("C");
        click(200, 200);
        check(controller.getDocument().atoms().size() == 1, "atom tool click creates an atom");

        controller.setTool(Tool.BOND);
        controller.setActiveBondType(com.molview.app.model.BondType.SINGLE);
        click(200, 200);
        click(320, 200);
        check(controller.getDocument().atoms().size() == 2, "bond click-chain creates second atom");
        check(controller.getDocument().bonds().size() == 1, "bond click-chain creates a bond");
        check(controller.getDocument().selectedObjects().isEmpty(),
                "a newly placed bond is unselected");

        Bond bond = controller.getDocument().bonds().get(0);
        double midX = (bond.getFrom().getX() + bond.getTo().getX()) / 2;
        double midY = (bond.getFrom().getY() + bond.getTo().getY()) / 2;
        check(Math.abs(bond.length() - com.molview.app.model.HexGrid.EDGE) < 0.05,
                "drawn bond is exactly one hex edge");

        // A selected single tool cycles an existing single bond single → triple → single.
        controller.setActiveBondType(com.molview.app.model.BondType.SINGLE);
        click(midX, midY);
        check(bond.getType() == com.molview.app.model.BondType.TRIPLE,
                "single-tool click cycles single to triple");
        click(midX, midY);
        check(bond.getType() == com.molview.app.model.BondType.SINGLE,
                "second click cycles triple back to single");
        controller.setActiveBondType(com.molview.app.model.BondType.DOUBLE);
        click(midX, midY);
        check(bond.getType() == com.molview.app.model.BondType.DOUBLE,
                "a different active bond type applies directly");

        controller.setTool(Tool.SELECT);
        click(midX, midY);
        check(controller.getDocument().selectedObjects().size() == 1
                        && controller.getDocument().selectedObjects().get(0) instanceof Bond,
                "select tool picks a bond");
        Atom first = bond.getFrom();
        double beforeX = first.getX();
        drag(midX, midY, midX + 20, midY + 20);
        check(Math.abs(first.getX() - (beforeX + 20)) < 0.1, "dragging a bond moves its atoms");
        controller.undo();
        check(Math.abs(controller.getDocument().atoms().get(0).getX() - beforeX) < 0.1,
                "undo restores moved atom");

        controller.setTool(Tool.CHARGE_NEG);
        click(202, 202);
        check(controller.getDocument().chargeMarks().size() == 1, "charge tool adds a charge");
        controller.setTool(Tool.LONE_PAIR);
        click(202, 202);
        check(controller.getDocument().electronMarks().size() == 1, "electron tool adds a lone pair");

        controller.setTool(Tool.ARROW_CURVED);
        click(450, 260);
        check(controller.getDocument().arrows().size() == 1, "arrow tool adds a curved arrow");

        controller.setTool(Tool.BRACKET_SQUARE);
        click(500, 400);
        check(controller.getDocument().brackets().size() == 1, "bracket tool creates a bracket");

        check(controller.canUndo(), "gestures produce undo history");

        // A fresh document: startup element C is active and a drag expands to an
        // alkane skeleton whose heteroatom sits at the beginning.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("O");
        canvas.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 200, 320, true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 330, 325, true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 440, 330, true));
        writeCanvas(output.resolve("chain-preview.png"));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 440, 330, false));
        check(controller.getDocument().atoms().size() >= 3,
                "dragging an element grows an alkane skeleton");
        check(controller.getDocument().bonds().size()
                        == controller.getDocument().atoms().size() - 1,
                "alkane skeleton is a single connected chain");
        boolean allEdges = controller.getDocument().bonds().stream()
                .allMatch(b -> Math.abs(b.length() - com.molview.app.model.HexGrid.EDGE) < 0.05);
        check(allEdges, "every skeleton bond is exactly one hexagon edge");
        boolean onGrid = controller.getDocument().atoms().stream()
                .allMatch(a -> com.molview.app.model.HexGrid.isVertex(a.getX(), a.getY(), 0.1));
        check(onGrid, "all skeleton atoms sit on hex-grid vertices");
        check(controller.getDocument().atoms().get(0).getElementSymbol().equals("O"),
                "the selected element starts the chain");
        boolean carbonsFollow = controller.getDocument().atoms().stream().skip(1)
                .allMatch(a -> a.getElementSymbol().equals("C"));
        check(carbonsFollow, "the rest of the skeleton is carbon");

        writeCanvas(output.resolve("hex-alkane-chain.png"));

        // Clicking an existing atom replaces its element. Dragging from it forms
        // one grid-snapped single bond with the selected element at the far end.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(250, 420);
        Atom source = controller.getDocument().atoms().get(0);
        controller.setActiveElement("N");
        click(source.getX(), source.getY());
        check(source.getElementSymbol().equals("N"),
                "clicking an existing atom applies the newly selected element");
        controller.setActiveElement("O");
        canvas.fireEvent(mouse(MouseEvent.MOUSE_PRESSED,
                source.getX(), source.getY(), true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED,
                source.getX() + 44, source.getY() + 20, true));
        writeCanvas(output.resolve("atom-bond-preview.png"));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_RELEASED,
                source.getX() + 44, source.getY() + 20, false));
        check(controller.getDocument().atoms().size() == 2,
                "dragging from an atom adds one endpoint");
        check(controller.getDocument().bonds().size() == 1,
                "dragging from an atom forms exactly one single bond");
        Bond newBond = controller.getDocument().bonds().get(0);
        check(newBond.getType() == com.molview.app.model.BondType.SINGLE,
                "atom-drag bond is a single bond");
        check(Math.abs(newBond.length() - com.molview.app.model.HexGrid.EDGE) < 0.05,
                "atom-drag bond has one hex-edge length");
        Atom endpoint = newBond.getFrom() == source ? newBond.getTo() : newBond.getFrom();
        check(endpoint.getElementSymbol().equals("O"),
                "the newly selected element is placed at the bond endpoint");

        // A short drag from carbon stays a single grid bond; a long drag becomes
        // an alkane chain.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(300, 500);
        Atom shortSource = controller.getDocument().atoms().get(0);
        drag(shortSource.getX(), shortSource.getY(),
                shortSource.getX() + 25, shortSource.getY());
        check(controller.getDocument().atoms().size() == 2
                        && controller.getDocument().bonds().size() == 1,
                "a short carbon drag places one single bond");
        check(controller.getDocument().selectedObjects().isEmpty(),
                "the short atom-drag bond is unselected");

        // A long drag from an existing carbon grows a chain to the selected
        // element at the far end, even outside strict hex mode.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(300, 500);
        Atom growSource = controller.getDocument().atoms().get(0);
        controller.setActiveElement("O");
        drag(growSource.getX(), growSource.getY(),
                growSource.getX() + 105, growSource.getY());
        check(controller.getDocument().atoms().size() > 2
                        && controller.getDocument().bonds().size() > 1,
                "a long carbon drag from an atom grows an alkane chain");
        Atom farElement = controller.getDocument().atoms().stream()
                .filter(a -> a.getElementSymbol().equals("O"))
                .findFirst().orElse(null);
        check(farElement != null && farElement != growSource,
                "selected element is placed at the far end of the chain");

        controller.setChainThresholdUnits(3.0);
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(300, 500);
        Atom thresholdSource = controller.getDocument().atoms().get(0);
        controller.setActiveElement("O");
        drag(thresholdSource.getX(), thresholdSource.getY(),
                thresholdSource.getX() + 100, thresholdSource.getY());
        check(controller.getDocument().atoms().size() == 2,
                "raising the chain threshold keeps a shorter carbon drag as one bond");
        controller.setChainThresholdUnits(1.5);

        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        controller.setBondAngleMode(com.molview.app.model.BondAngleMode.HEX);
        click(300, 500);
        Atom chainSource = controller.getDocument().atoms().get(0);
        drag(chainSource.getX(), chainSource.getY(),
                chainSource.getX() + 105, chainSource.getY());
        check(controller.getDocument().atoms().size() > 2
                        && controller.getDocument().bonds().size() > 1,
                "a carbon drag beyond 1.5 units expands into an alkane chain");
        check(controller.getDocument().atoms().stream()
                        .allMatch(a -> com.molview.app.model.HexGrid.isVertex(a.getX(), a.getY(), 0.1)),
                "expanded alkane chain stays on the hex grid");
        check(controller.getDocument().selectedObjects().isEmpty(),
                "the expanded alkane chain is unselected");

        // Terminal bromine rotates a new bond freely to an off-grid angle.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("Br");
        click(300, 560);
        Atom bromine = controller.getDocument().atoms().get(0);
        controller.setActiveElement("C");
        drag(bromine.getX(), bromine.getY(), bromine.getX() + 30, bromine.getY() + 30);
        check(controller.getDocument().atoms().size() == 2
                        && controller.getDocument().bonds().size() == 1,
                "a terminal halogen accepts one free-angle bond");
        Bond freeBond = controller.getDocument().bonds().get(0);
        Atom freeEnd = freeBond.getFrom() == bromine ? freeBond.getTo() : freeBond.getFrom();
        check(Math.abs(freeBond.length() - com.molview.app.model.HexGrid.EDGE) < 0.05,
                "the free-angle bond keeps one grid-unit length");
        check(!com.molview.app.model.HexGrid.isVertex(freeEnd.getX(), freeEnd.getY(), 0.05),
                "the terminal halogen bond is not snapped to a grid vertex");

        // The Bond tool must use the same free-angle rotation from a terminal
        // bromine (1-bromoethane), rather than snapping to its three neighbours.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(300, 520);
        Atom chainC = controller.getDocument().atoms().get(0);
        HexGrid.Vertex chainCVertex = HexGrid.nearestVertex(chainC.getX(), chainC.getY());
        HexGrid.Vertex brVertex = HexGrid.neighbors(chainCVertex).get(0);
        controller.setActiveElement("Br");
        click(brVertex.x(), brVertex.y());
        Atom bondedBr = controller.getDocument().atoms().get(1);
        controller.addBond(chainC, bondedBr, com.molview.app.model.BondType.SINGLE);
        controller.setTool(Tool.BOND);
        controller.setActiveBondType(com.molview.app.model.BondType.SINGLE);
        drag(bondedBr.getX(), bondedBr.getY(),
                bondedBr.getX() + 48, bondedBr.getY() + 48);
        check(controller.getDocument().atoms().size() == 3,
                "bond tool adds one free endpoint from bromine");
        Bond toolFreeBond = controller.getDocument().bonds().get(1);
        Atom toolFreeEnd = toolFreeBond.getFrom() == bondedBr
                ? toolFreeBond.getTo() : toolFreeBond.getFrom();
        check(!com.molview.app.model.HexGrid.isVertex(
                        toolFreeEnd.getX(), toolFreeEnd.getY(), 0.05),
                "bond tool rotates freely from a terminal bromine");
        check(controller.getDocument().selectedObjects().isEmpty(),
                "the free bond from the bond tool is unselected");

        // Outside Select mode, right-click deletes the picked atom immediately.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(300, 500);
        Atom rightClickAtom = controller.getDocument().atoms().get(0);
        controller.setTool(Tool.ATOM);
        rightClick(rightClickAtom.getX(), rightClickAtom.getY());
        check(controller.getDocument().atoms().isEmpty(),
                "right-click deletes the designated atom outside selection mode");

        // Click-click must be free-angle too, not only press-drag-release.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        click(300, 520);
        Atom clickC = controller.getDocument().atoms().get(0);
        HexGrid.Vertex clickCVertex = HexGrid.nearestVertex(clickC.getX(), clickC.getY());
        HexGrid.Vertex clickBrVertex = HexGrid.neighbors(clickCVertex).get(0);
        controller.setActiveElement("Br");
        click(clickBrVertex.x(), clickBrVertex.y());
        Atom clickBr = controller.getDocument().atoms().get(1);
        controller.addBond(clickC, clickBr, com.molview.app.model.BondType.SINGLE);
        controller.setTool(Tool.BOND);
        controller.setActiveBondType(com.molview.app.model.BondType.SINGLE);
        click(clickBr.getX(), clickBr.getY());
        click(clickBr.getX() + 45, clickBr.getY() + 50);
        check(controller.getDocument().atoms().size() == 3,
                "bond-tool click-click adds one free endpoint");
        Bond clickFreeBond = controller.getDocument().bonds().get(1);
        Atom clickFreeEnd = clickFreeBond.getFrom() == clickBr
                ? clickFreeBond.getTo() : clickFreeBond.getFrom();
        check(!com.molview.app.model.HexGrid.isVertex(
                        clickFreeEnd.getX(), clickFreeEnd.getY(), 0.05),
                "bond-tool click-click rotates freely from a terminal bromine");

        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        controller.buildAlkaneChain(com.molview.app.model.HexGrid.path(
                com.molview.app.model.HexGrid.nearestVertex(220, 300),
                com.molview.app.model.HexGrid.nearestVertex(460, 320)));
        Atom chargedAtom = controller.getDocument().atoms().get(2);
        controller.addCharge(chargedAtom.getX(), chargedAtom.getY(),
                com.molview.app.model.ChargeKind.PARTIAL_POSITIVE, chargedAtom);
        writeCanvas(output.resolve("delta-label.png"));

        // Double-bond junction, horizontal-dash hydrogen bond and lone-pair
        // ordering reference image.
        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        HexGrid.Vertex aVertex = HexGrid.nearestVertex(300, 300);
        Atom a = controller.addAtomAt(aVertex.x(), aVertex.y());
        HexGrid.Vertex bVertex = null;
        HexGrid.Vertex cVertex = null;
        HexGrid.Vertex dVertex = null;
        for (HexGrid.Vertex n : HexGrid.neighbors(aVertex)) {
            if (bVertex == null || n.x() > bVertex.x()) {
                bVertex = n;
            }
            if (cVertex == null || n.x() < cVertex.x()) {
                cVertex = n;
            }
        }
        for (HexGrid.Vertex n : HexGrid.neighbors(aVertex)) {
            if (!n.equals(bVertex) && !n.equals(cVertex)) {
                dVertex = n;
            }
        }
        controller.setActiveElement("O");
        Atom b = controller.addAtomAt(bVertex.x(), bVertex.y());
        controller.setActiveElement("C");
        Atom c = controller.addAtomAt(cVertex.x(), cVertex.y());
        Atom d = controller.addAtomAt(dVertex.x(), dVertex.y());
        controller.addBond(a, b, com.molview.app.model.BondType.DOUBLE);
        controller.addBond(a, c, com.molview.app.model.BondType.SINGLE);
        controller.addBond(a, d, com.molview.app.model.BondType.SINGLE);

        HexGrid.Vertex h1Vertex = HexGrid.nearestVertex(620, 470);
        Atom h1 = controller.addAtomAt(h1Vertex.x(), h1Vertex.y());
        HexGrid.Vertex h2Vertex = HexGrid.neighbors(h1Vertex).get(0);
        Atom h2 = controller.addAtomAt(h2Vertex.x(), h2Vertex.y());
        controller.addBond(h1, h2, com.molview.app.model.BondType.HYDROGEN);

        controller.setActiveElement("O");
        HexGrid.Vertex oVertex = HexGrid.nearestVertex(440, 540);
        Atom o = controller.addAtomAt(oVertex.x(), oVertex.y());
        controller.setActiveElement("C");
        HexGrid.Vertex leftVertex = HexGrid.neighbors(oVertex).get(0);
        for (HexGrid.Vertex n : HexGrid.neighbors(oVertex)) {
            if (n.x() < leftVertex.x()) {
                leftVertex = n;
            }
        }
        Atom oLeft = controller.addAtomAt(leftVertex.x(), leftVertex.y());
        controller.addBond(o, oLeft, com.molview.app.model.BondType.SINGLE);
        for (int i = 0; i < 3; i++) {
            controller.addElectron(o.getX(), o.getY(),
                    com.molview.app.model.ElectronKind.LONE_PAIR, o);
        }
        controller.clearSelection();
        writeCanvas(output.resolve("bond-styles.png"));

        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        String[] isolated = {"C", "O", "P", "S", "H", "Br"};
        double labelX = 170;
        for (String symbol : isolated) {
            controller.setActiveElement(symbol);
            controller.addAtomAt(labelX, 600);
            labelX += 105;
        }
        controller.clearSelection();
        writeCanvas(output.resolve("hydrogen-labels.png"));

        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("C");
        HexGrid.Vertex pairHubVertex = HexGrid.nearestVertex(700, 260);
        Atom pairHub = controller.addAtomAt(pairHubVertex.x(), pairHubVertex.y());
        for (HexGrid.Vertex neighbour : HexGrid.neighbors(pairHubVertex)) {
            Atom spoke = controller.addAtomAt(neighbour.x(), neighbour.y());
            controller.addBond(pairHub, spoke, com.molview.app.model.BondType.SINGLE);
        }
        controller.addElectron(pairHub.getX(), pairHub.getY(),
                com.molview.app.model.ElectronKind.LONE_PAIR, pairHub);
        controller.addElectron(pairHub.getX(), pairHub.getY(),
                com.molview.app.model.ElectronKind.LONE_PAIR, pairHub);
        controller.clearSelection();
        writeCanvas(output.resolve("three-bond-pairs.png"));

        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setActiveElement("O");
        HexGrid.Vertex rightOHub = HexGrid.nearestVertex(300, 300);
        Atom rightO = controller.addAtomAt(rightOHub.x(), rightOHub.y());
        HexGrid.Vertex rightCVertex = null;
        for (HexGrid.Vertex n : HexGrid.neighbors(rightOHub)) {
            if (rightCVertex == null || n.x() > rightCVertex.x()) {
                rightCVertex = n;
            }
        }
        controller.setActiveElement("C");
        Atom rightC = controller.addAtomAt(rightCVertex.x(), rightCVertex.y());
        controller.addBond(rightO, rightC, com.molview.app.model.BondType.SINGLE);

        controller.setActiveElement("O");
        HexGrid.Vertex leftOHub = HexGrid.nearestVertex(650, 300);
        Atom leftO = controller.addAtomAt(leftOHub.x(), leftOHub.y());
        HexGrid.Vertex leftCVertex = null;
        for (HexGrid.Vertex n : HexGrid.neighbors(leftOHub)) {
            if (leftCVertex == null || n.x() < leftCVertex.x()) {
                leftCVertex = n;
            }
        }
        controller.setActiveElement("C");
        Atom leftC = controller.addAtomAt(leftCVertex.x(), leftCVertex.y());
        controller.addBond(leftO, leftC, com.molview.app.model.BondType.SINGLE);
        controller.clearSelection();
        writeCanvas(output.resolve("bond-element-labels.png"));

        // Electron-transfer arrows snap to the bond middle and stop outside atoms.
        com.molview.app.model.ChemDocument arrowDoc =
                new com.molview.app.model.ChemDocument("Arrow snap");
        Atom arrowO = Atom.element("O", 300, 300);
        Atom arrowC = Atom.element("C", 380, 300);
        arrowDoc.add(arrowO);
        arrowDoc.add(arrowC);
        arrowDoc.add(new Bond(arrowO, arrowC,
                com.molview.app.model.BondType.SINGLE));
        controller.replaceDocument(arrowDoc, null, true);
        controller.setTool(Tool.ARROW_CURVED);
        double arrowMidX = (arrowO.getX() + arrowC.getX()) / 2;
        double arrowMidY = (arrowO.getY() + arrowC.getY()) / 2;
        drag(arrowMidX, arrowMidY, arrowO.getX(), arrowO.getY());
        check(controller.getDocument().arrows().size() == 1,
                "electron-transfer arrow is created");
        com.molview.app.model.Arrow snapArrow =
                controller.getDocument().arrows().get(0);
        check(Math.hypot(snapArrow.getX1() - arrowMidX,
                        snapArrow.getY1() - arrowMidY) < 0.1,
                "arrow start snaps to the middle of the bond");
        check(Math.hypot(snapArrow.getX2() - arrowO.getX(),
                        snapArrow.getY2() - arrowO.getY()) > 12,
                "arrow end stops outside the element");
        check(snapArrow.getControlY() < 300
                        && Math.abs(snapArrow.getControlX() - arrowO.getX()) < 0.1
                        && Math.abs(snapArrow.getX2() - arrowO.getX()) < 0.1
                        && snapArrow.getY2() < arrowO.getY(),
                "arrow rises over the atom-side bond end and falls onto the element");
        controller.clearSelection();
        writeCanvas(output.resolve("arrow-snap.png"));

        controller.replaceDocument(new com.molview.app.model.ChemDocument(), null, true);
        controller.setTool(Tool.ARROW_STRAIGHT);
        dragShift(500, 450, 650, 520);
        check(controller.getDocument().arrows().size() == 1,
                "reaction arrow is created with Shift held");
        com.molview.app.model.Arrow reactionArrow =
                controller.getDocument().arrows().get(0);
        check(Math.abs(reactionArrow.getY1() - reactionArrow.getY2()) < 0.01,
                "Shift snaps the reaction arrow horizontally");
    }

    private void writeCanvas(Path output) {
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
            WritableImage image = canvas.snapshot(params, null);
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height,
                    PixelFormat.getIntArgbInstance(), pixels, 0, width);
            BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            buffered.setRGB(0, 0, width, height, pixels, 0, width);
            Files.createDirectories(output.getParent());
            ImageIO.write(buffered, "png", output.toFile());
            System.out.println("Wrote " + output.toAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void click(double x, double y) {
        canvas.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, x, y, true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, x, y, false));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, x, y, false));
    }

    private void drag(double x1, double y1, double x2, double y2) {
        canvas.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, x1, y1, true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, (x1 + x2) / 2, (y1 + y2) / 2, true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, x2, y2, true));
        canvas.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, x2, y2, false));
    }

    private MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y,
                             boolean primaryDown) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, primaryDown, false, false,
                true, false, true, null);
    }

    private void dragShift(double x1, double y1, double x2, double y2) {
        MouseEvent pressed = new MouseEvent(MouseEvent.MOUSE_PRESSED,
                x1, y1, x1, y1, MouseButton.PRIMARY, 1,
                true, false, false, false, true, false, false,
                true, false, true, null);
        MouseEvent dragged = new MouseEvent(MouseEvent.MOUSE_DRAGGED,
                x2, y2, x2, y2, MouseButton.PRIMARY, 1,
                true, false, false, false, true, false, false,
                true, false, true, null);
        MouseEvent released = new MouseEvent(MouseEvent.MOUSE_RELEASED,
                x2, y2, x2, y2, MouseButton.PRIMARY, 1,
                true, false, false, false, false, false, false,
                true, false, true, null);
        canvas.fireEvent(pressed);
        canvas.fireEvent(dragged);
        canvas.fireEvent(released);
    }

    private void rightClick(double x, double y) {
        MouseEvent pressed = new MouseEvent(MouseEvent.MOUSE_PRESSED, x, y, x, y,
                MouseButton.SECONDARY, 1, false, false, false, false,
                false, false, true, true, true, true, null);
        canvas.fireEvent(pressed);
        canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, x, y, x, y,
                MouseButton.SECONDARY, 1, false, false, false, false,
                false, false, false, true, true, true, null));
    }

    private void check(boolean condition, String message) {
        checks++;
        if (condition) {
            System.out.println("PASS  " + message);
        } else {
            failures++;
            System.out.println("FAIL  " + message);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
