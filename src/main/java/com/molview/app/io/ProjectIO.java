package com.molview.app.io;

import com.molview.app.model.Arrow;
import com.molview.app.model.ArrowType;
import com.molview.app.model.Atom;
import com.molview.app.model.AtomKind;
import com.molview.app.model.Bond;
import com.molview.app.model.BondType;
import com.molview.app.model.Bracket;
import com.molview.app.model.BracketLabelPosition;
import com.molview.app.model.BracketType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.TextLabel;
import com.molview.app.model.ViewState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native, fully editable MolView Studio project format (JSON, UTF-8). */
public final class ProjectIO {
    public static final String FORMAT = "molview-studio-project";
    public static final int VERSION = 1;

    private ProjectIO() {
    }

    public static void save(ChemDocument document, ViewState view, Path path) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("application", "MolView Studio");
        root.put("title", document.getTitle());
        root.put("modified", Instant.now().toString());

        Map<String, Object> viewMap = new LinkedHashMap<>();
        viewMap.put("zoom", view.zoom());
        viewMap.put("offsetX", view.offsetX());
        viewMap.put("offsetY", view.offsetY());
        viewMap.put("showGrid", view.showGrid());
        viewMap.put("showCarbonLabels", view.showCarbonLabels());
        viewMap.put("snapToGrid", view.snapToGrid());
        root.put("view", viewMap);

        List<Object> atoms = new ArrayList<>();
        List<Object> bonds = new ArrayList<>();
        List<Object> annotations = new ArrayList<>();

        for (ChemObject object : document.objects()) {
            if (object instanceof Atom atom) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", atom.getId());
                map.put("symbol", atom.getElementSymbol());
                map.put("kind", atom.getKind().name());
                map.put("label", atom.getGenericLabel());
                map.put("x", clean(atom.getX()));
                map.put("y", clean(atom.getY()));
                atoms.add(map);
            } else if (object instanceof Bond bond) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", bond.getId());
                map.put("from", bond.getFrom().getId());
                map.put("to", bond.getTo().getId());
                map.put("type", bond.getType().name());
                bonds.add(map);
            } else if (object instanceof ChargeMark charge) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", "charge");
                map.put("id", charge.getId());
                map.put("kind", charge.getKind().name());
                map.put("atomId", charge.getAttachedAtomId());
                map.put("x", clean(charge.getX()));
                map.put("y", clean(charge.getY()));
                annotations.add(map);
            } else if (object instanceof ElectronMark electron) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", "electron");
                map.put("id", electron.getId());
                map.put("kind", electron.getKind().name());
                map.put("atomId", electron.getAttachedAtomId());
                map.put("x", clean(electron.getX()));
                map.put("y", clean(electron.getY()));
                annotations.add(map);
            } else if (object instanceof TextLabel text) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", "text");
                map.put("id", text.getId());
                map.put("text", text.getText());
                map.put("atomId", text.getAttachedAtomId());
                map.put("x", clean(text.getX()));
                map.put("y", clean(text.getY()));
                map.put("fontSize", clean(text.getFontSize()));
                map.put("bold", text.isBold());
                map.put("color", text.getColor());
                annotations.add(map);
            } else if (object instanceof Arrow arrow) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", "arrow");
                map.put("id", arrow.getId());
                map.put("kind", arrow.getType().name());
                map.put("x1", clean(arrow.getX1()));
                map.put("y1", clean(arrow.getY1()));
                map.put("cx", clean(arrow.getControlX()));
                map.put("cy", clean(arrow.getControlY()));
                map.put("x2", clean(arrow.getX2()));
                map.put("y2", clean(arrow.getY2()));
                annotations.add(map);
            } else if (object instanceof Bracket bracket) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", "bracket");
                map.put("id", bracket.getId());
                map.put("kind", bracket.getType().name());
                map.put("x", clean(bracket.getX()));
                map.put("y", clean(bracket.getY()));
                map.put("w", clean(bracket.getWidth()));
                map.put("h", clean(bracket.getHeight()));
                map.put("label", bracket.getLabel());
                map.put("labelPosition", bracket.getLabelPosition().name());
                annotations.add(map);
            }
        }

        root.put("atoms", atoms);
        root.put("bonds", bonds);
        root.put("annotations", annotations);
        root.put("counts", Map.of("atoms", atoms.size(), "bonds", bonds.size(),
                "annotations", annotations.size()));

        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, Json.stringify(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static LoadedProject load(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, Object> root;
        try {
            root = Json.parseObject(text);
        } catch (RuntimeException ex) {
            throw new IOException("This file is not valid JSON: " + ex.getMessage(), ex);
        }
        String format = string(root.get("format"), "");
        if (!FORMAT.equals(format)) {
            throw new IOException("Unsupported project format: " + (format.isBlank() ? "unknown" : format));
        }
        ChemDocument document = new ChemDocument(string(root.get("title"),
                stripExtension(path.getFileName() == null ? "Untitled" : path.getFileName().toString())));

        Map<String, Atom> atomsById = new LinkedHashMap<>();
        Object atomsValue = root.get("atoms");
        if (atomsValue instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> map = cast(raw);
                String id = string(map.get("id"), null);
                String symbol = string(map.get("symbol"), "C");
                AtomKind kind = enumValue(AtomKind.class, string(map.get("kind"), "ELEMENT"),
                        AtomKind.ELEMENT);
                String label = string(map.get("label"), null);
                double x = number(map.get("x"), 0);
                double y = number(map.get("y"), 0);
                if (id == null) {
                    id = com.molview.app.model.Ids.next("a");
                }
                if (atomsById.containsKey(id)) {
                    throw new IOException("Duplicate atom id in project: " + id);
                }
                Atom atom = new Atom(id, symbol, kind, label, x, y);
                atomsById.put(id, atom);
                document.add(atom);
            }
        }

        Object bondsValue = root.get("bonds");
        if (bondsValue instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> map = cast(raw);
                Atom from = atomsById.get(string(map.get("from"), ""));
                Atom to = atomsById.get(string(map.get("to"), ""));
                if (from == null || to == null || from == to) {
                    continue;
                }
                String id = string(map.get("id"), null);
                BondType type = enumValue(BondType.class, string(map.get("type"), "SINGLE"),
                        BondType.SINGLE);
                if (id == null) {
                    id = com.molview.app.model.Ids.next("b");
                }
                document.add(new Bond(id, from, to, type));
            }
        }

        Object annotationsValue = root.get("annotations");
        if (annotationsValue instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> map = cast(raw);
                String id = string(map.get("id"), null);
                String type = string(map.get("type"), "");
                String atomId = string(map.get("atomId"), null);
                if (atomId != null && !atomsById.containsKey(atomId)) {
                    atomId = null;
                }
                try {
                    ChemObject object = switch (type) {
                        case "charge" -> new ChargeMark(id,
                                enumValue(ChargeKind.class, string(map.get("kind"), "POSITIVE"),
                                        ChargeKind.POSITIVE),
                                number(map.get("x"), 0), number(map.get("y"), 0));
                        case "electron" -> new ElectronMark(id,
                                enumValue(ElectronKind.class, string(map.get("kind"), "LONE_PAIR"),
                                        ElectronKind.LONE_PAIR),
                                number(map.get("x"), 0), number(map.get("y"), 0));
                        case "text" -> new TextLabel(id, string(map.get("text"), ""),
                                number(map.get("x"), 0), number(map.get("y"), 0),
                                number(map.get("fontSize"), 17),
                                bool(map.get("bold"), false),
                                string(map.get("color"), "#253047"));
                        case "arrow" -> new Arrow(id,
                                enumValue(ArrowType.class, string(map.get("kind"), "STRAIGHT"),
                                        ArrowType.STRAIGHT),
                                number(map.get("x1"), 0), number(map.get("y1"), 0),
                                number(map.get("cx"), 100), number(map.get("cy"), 100),
                                number(map.get("x2"), 200), number(map.get("y2"), 100));
                        case "bracket" -> new Bracket(id,
                                enumValue(BracketType.class, string(map.get("kind"), "SQUARE"),
                                        BracketType.SQUARE),
                                number(map.get("x"), 0), number(map.get("y"), 0),
                                number(map.get("w"), 160), number(map.get("h"), 120),
                                string(map.get("label"), ""),
                                enumValue(BracketLabelPosition.class,
                                        string(map.get("labelPosition"), "BOTTOM_RIGHT"),
                                        BracketLabelPosition.BOTTOM_RIGHT));
                        default -> null;
                    };
                    if (object != null) {
                        if (object instanceof com.molview.app.model.Annotation annotation) {
                            annotation.setAttachedAtomId(atomId);
                        }
                        if (id != null && object.getId().startsWith("o") && !id.equals(object.getId())) {
                            // Keep stable ids where the constructor accepted them.
                            object.setId(id);
                        }
                        document.add(object);
                    }
                } catch (RuntimeException ex) {
                    throw new IOException("Invalid " + type + " annotation: " + ex.getMessage(), ex);
                }
            }
        }

        ViewState viewState = ViewState.defaults();
        if (root.get("view") instanceof Map<?, ?> viewRaw) {
            Map<String, Object> view = cast(viewRaw);
            viewState = new ViewState(
                    number(view.get("zoom"), 1),
                    number(view.get("offsetX"), 0),
                    number(view.get("offsetY"), 0),
                    bool(view.get("showGrid"), true),
                    bool(view.get("showCarbonLabels"), false),
                    true);
        }
        return new LoadedProject(document, viewState);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static double clean(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String string(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
