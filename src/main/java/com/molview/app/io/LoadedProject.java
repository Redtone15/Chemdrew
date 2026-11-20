package com.molview.app.io;

import com.molview.app.model.ChemDocument;
import com.molview.app.model.ViewState;

public record LoadedProject(ChemDocument document, ViewState viewState) {
}
