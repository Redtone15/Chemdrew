package com.molview.app.render;

import com.molview.app.model.Arrow;
import com.molview.app.model.Bracket;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;

import java.util.ArrayList;
import java.util.List;

/** Screen-independent manipulation handles for arrows and brackets. */
public final class SelectionHandles {
    public enum Kind {
        ARROW_START,
        ARROW_END,
        ARROW_CONTROL,
        BRACKET_NW,
        BRACKET_N,
        BRACKET_NE,
        BRACKET_E,
        BRACKET_SE,
        BRACKET_S,
        BRACKET_SW,
        BRACKET_W
    }

    public record Handle(Kind kind, double x, double y, ChemObject target) {
    }

    private SelectionHandles() {
    }

    public static List<Handle> forDocument(ChemDocument document) {
        List<Handle> handles = new ArrayList<>();
        for (ChemObject object : document.selectedObjects()) {
            if (object instanceof Arrow arrow) {
                handles.add(new Handle(Kind.ARROW_START, arrow.getX1(), arrow.getY1(), arrow));
                handles.add(new Handle(Kind.ARROW_END, arrow.getX2(), arrow.getY2(), arrow));
                if (arrow.isCurved()) {
                    handles.add(new Handle(Kind.ARROW_CONTROL,
                            arrow.getControlX(), arrow.getControlY(), arrow));
                }
            } else if (object instanceof Bracket bracket) {
                double x = bracket.getX();
                double y = bracket.getY();
                double w = bracket.getWidth();
                double h = bracket.getHeight();
                handles.add(new Handle(Kind.BRACKET_NW, x, y, bracket));
                handles.add(new Handle(Kind.BRACKET_N, x + w / 2, y, bracket));
                handles.add(new Handle(Kind.BRACKET_NE, x + w, y, bracket));
                handles.add(new Handle(Kind.BRACKET_E, x + w, y + h / 2, bracket));
                handles.add(new Handle(Kind.BRACKET_SE, x + w, y + h, bracket));
                handles.add(new Handle(Kind.BRACKET_S, x + w / 2, y + h, bracket));
                handles.add(new Handle(Kind.BRACKET_SW, x, y + h, bracket));
                handles.add(new Handle(Kind.BRACKET_W, x, y + h / 2, bracket));
            }
        }
        return handles;
    }
}
