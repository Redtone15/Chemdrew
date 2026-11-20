package com.molview.app.io;

public enum ExportFormat {
    PNG("PNG image", ".png", Category.IMAGE),
    JPEG("JPEG image", ".jpg", Category.IMAGE),
    SVG("SVG vector", ".svg", Category.IMAGE),
    PDF("PDF document", ".pdf", Category.DOCUMENT),
    MOL("MOL structure", ".mol", Category.CHEMISTRY),
    SDF("SDF structures", ".sdf", Category.CHEMISTRY),
    SMILES("SMILES string", ".smi", Category.CHEMISTRY);

    public enum Category {
        IMAGE, DOCUMENT, CHEMISTRY
    }

    private final String displayName;
    private final String extension;
    private final Category category;

    ExportFormat(String displayName, String extension, Category category) {
        this.displayName = displayName;
        this.extension = extension;
        this.category = category;
    }

    public String displayName() {
        return displayName;
    }

    public String extension() {
        return extension;
    }

    public Category category() {
        return category;
    }

    public boolean isRasterImage() {
        return this == PNG || this == JPEG || this == PDF;
    }

    @Override
    public String toString() {
        return displayName + "  (" + extension + ")";
    }
}
