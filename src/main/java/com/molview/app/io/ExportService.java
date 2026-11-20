package com.molview.app.io;

import com.molview.app.model.Bounds;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;
import com.molview.app.render.ChemRenderer;
import com.molview.app.render.RenderSettings;
import com.molview.app.render.SvgExporter;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Coordinates all export formats. Raster/PDF output is rendered off-screen with
 * the same drawing engine as the editor, then encoded without external
 * libraries.
 */
public final class ExportService {
    private static final int MAX_RASTER_PIXELS = 64_000_000;

    private ExportService() {
    }

    public static void export(ChemDocument document, ExportOptions options,
                              boolean showCarbonLabels) throws IOException {
        ChemDocument content = contentFor(document, options);
        Files.createDirectories(options.getPath().toAbsolutePath().getParent());
        switch (options.getFormat()) {
            case PNG -> writeRaster(content, options, showCarbonLabels, false);
            case JPEG -> writeRaster(content, options, showCarbonLabels, true);
            case SVG -> writeSvg(content, options, showCarbonLabels);
            case PDF -> writePdf(content, options, showCarbonLabels);
            case MOL -> {
                requireAtoms(content);
                MolfileExporter.exportMol(content, options.getPath());
            }
            case SDF -> {
                requireAtoms(content);
                MolfileExporter.exportSdf(content, options.getPath());
            }
            case SMILES -> {
                requireAtoms(content);
                String smiles = SmilesExporter.toSmiles(content);
                Files.writeString(options.getPath(), smiles + System.lineSeparator(),
                        StandardCharsets.UTF_8);
            }
        }
    }

    /** A low-resolution preview with exactly the same composition as the export. */
    public static WritableImage preview(ChemDocument document, ExportOptions options,
                                        boolean showCarbonLabels, double maxWidth, double maxHeight)
            throws IOException {
        ChemDocument content = contentFor(document, options);
        double aspect;
        if (options.getFormat() == ExportFormat.PDF) {
            aspect = options.pageWidthPt() / options.pageHeightPt();
        } else {
            aspect = options.getWidth() / (double) options.getHeight();
        }
        double width = maxWidth;
        double height = width / aspect;
        if (height > maxHeight) {
            height = maxHeight;
            width = height * aspect;
        }
        int previewWidth = Math.max(64, (int) Math.round(width));
        int previewHeight = Math.max(64, (int) Math.round(height));
        boolean transparent = (options.getFormat() == ExportFormat.PNG
                || options.getFormat() == ExportFormat.SVG) && options.isTransparent();
        Color background = Color.web(options.getBackgroundColor());

        if (options.getFormat() == ExportFormat.PDF) {
            return renderPage(content, options, showCarbonLabels,
                    previewWidth, previewHeight, true);
        }
        return renderRaster(content, previewWidth, previewHeight, background,
                transparent, showCarbonLabels);
    }

    public static String textPreview(ChemDocument document, ExportOptions options)
            throws IOException {
        ChemDocument content = contentFor(document, options);
        requireAtoms(content);
        return switch (options.getFormat()) {
            case MOL -> previewLines(MolfileExporter.toMol(content), 26);
            case SDF -> previewLines(MolfileExporter.toSdf(content), 26);
            case SMILES -> SmilesExporter.toSmiles(content);
            default -> "";
        };
    }

    private static String previewLines(String text, int count) {
        String[] lines = text.split("\\R", -1);
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(count, lines.length); i++) {
            if (i > 0) {
                preview.append('\n');
            }
            preview.append(lines[i]);
        }
        if (lines.length > count) {
            preview.append("\n…");
        }
        return preview.toString();
    }

    private static ChemDocument contentFor(ChemDocument document, ExportOptions options) {
        if (!options.isSelectionOnly()) {
            return document;
        }
        List<ChemObject> selected = document.selectedObjects();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Nothing is selected. Select objects first or export the entire drawing.");
        }
        ChemDocument fragment = document.extractFragment(selected, false);
        if (fragment.isEmpty()) {
            throw new IllegalArgumentException("The selection contains nothing that can be exported.");
        }
        fragment.setTitle(document.getTitle() + " selection");
        return fragment;
    }

    private static void requireAtoms(ChemDocument content) {
        if (content.atoms().isEmpty()) {
            throw new IllegalArgumentException("The drawing contains no atoms to export as a chemical structure.");
        }
    }

    private static void writeRaster(ChemDocument content, ExportOptions options,
                                    boolean showCarbonLabels, boolean jpeg) throws IOException {
        int width = options.getWidth();
        int height = options.getHeight();
        if ((long) width * height > MAX_RASTER_PIXELS) {
            throw new IllegalArgumentException("The requested image is too large. Use dimensions below 64 megapixels.");
        }
        Color background = Color.web(options.getBackgroundColor());
        boolean transparent = !jpeg && options.isTransparent();
        WritableImage image = renderRaster(content, width, height, background, transparent, showCarbonLabels);
        BufferedImage buffered = toBufferedImage(image, transparent);
        if (jpeg || !transparent) {
            BufferedImage flattened = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = flattened.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(buffered, 0, 0, null);
            g.dispose();
            buffered = flattened;
        }
        if (jpeg) {
            writeJpeg(buffered, options.getPath());
        } else {
            ImageIO.write(buffered, "png", options.getPath().toFile());
        }
    }

    private static void writeSvg(ChemDocument content, ExportOptions options,
                                 boolean showCarbonLabels) throws IOException {
        String background = options.isTransparent() ? null : options.getBackgroundColor();
        String svg = SvgExporter.render(content, showCarbonLabels,
                options.getWidth(), options.getHeight(), background);
        Files.writeString(options.getPath(), svg, StandardCharsets.UTF_8);
    }

    private static void writePdf(ChemDocument content, ExportOptions options,
                                 boolean showCarbonLabels) throws IOException {
        double pageW = options.pageWidthPt();
        double pageH = options.pageHeightPt();
        int pixelWidth = (int) Math.round(pageW / 72.0 * options.getDpi());
        int pixelHeight = (int) Math.round(pageH / 72.0 * options.getDpi());
        if ((long) pixelWidth * pixelHeight > MAX_RASTER_PIXELS) {
            throw new IllegalArgumentException(
                    "The PDF page at " + options.getDpi() + " DPI is too large. Reduce the DPI.");
        }
        WritableImage image = renderPage(content, options, showCarbonLabels,
                pixelWidth, pixelHeight, true);
        BufferedImage buffered = toBufferedImage(image, false);
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        writeJpeg(buffered, jpegBytes);
        PdfExportWriter.write(options.getPath(), jpegBytes.toByteArray(),
                pixelWidth, pixelHeight, pageW, pageH, content.getTitle());
    }

    private static WritableImage renderRaster(ChemDocument content, int width, int height,
                                              Color background, boolean transparent,
                                              boolean showCarbonLabels) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        if (!transparent) {
            gc.setFill(background);
            gc.fillRect(0, 0, width, height);
        }
        drawFitted(gc, content, 0, 0, width, height,
                Math.min(width, height) * 0.055, showCarbonLabels);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private static WritableImage renderPage(ChemDocument content, ExportOptions options,
                                            boolean showCarbonLabels, int width, int height,
                                            boolean whiteBackground) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        if (whiteBackground) {
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, width, height);
        }
        double marginX = options.getMarginPt() / options.pageWidthPt() * width;
        double marginY = options.getMarginPt() / options.pageHeightPt() * height;
        drawFitted(gc, content, marginX, marginY,
                Math.max(10, width - marginX * 2), Math.max(10, height - marginY * 2),
                Math.min(width, height) * 0.02, showCarbonLabels);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        return canvas.snapshot(params, null);
    }

    private static void drawFitted(GraphicsContext gc, ChemDocument content,
                                   double targetX, double targetY, double targetWidth, double targetHeight,
                                   double padding, boolean showCarbonLabels) {
        Bounds bounds = content.bounds();
        if (bounds == null) {
            bounds = new Bounds(0, 0, 100, 100);
        }
        double contentWidth = Math.max(60, bounds.width());
        double contentHeight = Math.max(60, bounds.height());
        double availableWidth = Math.max(10, targetWidth - padding * 2);
        double availableHeight = Math.max(10, targetHeight - padding * 2);
        double scale = Math.min(availableWidth / contentWidth, availableHeight / contentHeight);
        double offsetX = targetX + (targetWidth - contentWidth * scale) / 2 - bounds.minX * scale;
        double offsetY = targetY + (targetHeight - contentHeight * scale) / 2 - bounds.minY * scale;
        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(scale, scale);
        ChemRenderer.drawScene(gc, content, RenderSettings.export(showCarbonLabels));
        gc.restore();
    }

    private static BufferedImage toBufferedImage(WritableImage image, boolean alpha) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        return buffered;
    }

    private static void writeJpeg(BufferedImage image, Path path) throws IOException {
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            writeJpeg(image, output);
        }
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream bytes) throws IOException {
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writeJpeg(image, output);
        }
    }

    private static void writeJpeg(BufferedImage image, ImageOutputStream output) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.94f);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
            output.flush();
        }
    }
}
