package com.molview.app.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Minimal PDF 1.4 writer used for print-ready export. The rendered page is
 * embedded as a high-resolution JPEG at the selected DPI, preserving the exact
 * on-screen layout, fonts and chemical notation.
 */
public final class PdfExportWriter {
    private PdfExportWriter() {
    }

    public static void write(Path path, byte[] jpeg, int pixelWidth, int pixelHeight,
                             double pageWidthPt, double pageHeightPt, String title) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + 8192);
        out.write("%PDF-1.4\n%".getBytes(StandardCharsets.ISO_8859_1));
        out.write(new byte[]{(byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3});
        out.write('\n');
        long[] offsets = new long[7];

        offsets[1] = out.size();
        ascii(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = out.size();
        ascii(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets[3] = out.size();
        ascii(out, String.format(Locale.ROOT,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.3f %.3f] " +
                        "/Resources << /XObject << /Im0 4 0 R >> /ProcSet [/PDF /ImageC] >> " +
                        "/Contents 5 0 R >>\nendobj\n",
                pageWidthPt, pageHeightPt));

        offsets[4] = out.size();
        ascii(out, String.format(Locale.ROOT,
                "4 0 obj\n<< /Type /XObject /Subtype /Image /Width %d /Height %d " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                        "/Interpolate true /Length %d >>\nstream\n",
                pixelWidth, pixelHeight, jpeg.length));
        out.write(jpeg);
        ascii(out, "\nendstream\nendobj\n");

        byte[] content = String.format(Locale.ROOT,
                "q\n%.3f 0 0 %.3f 0 0 cm\n/Im0 Do\nQ\n",
                pageWidthPt, pageHeightPt).getBytes(StandardCharsets.ISO_8859_1);
        offsets[5] = out.size();
        ascii(out, "5 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        out.write(content);
        ascii(out, "endstream\nendobj\n");

        offsets[6] = out.size();
        String safeTitle = title == null ? "Chemical drawing" : title
                .replace("\\", "").replace("(", "").replace(")", "");
        ascii(out, "6 0 obj\n<< /Title (" + safeTitle + ") /Producer (MolView Studio) >>\nendobj\n");

        long xref = out.size();
        ascii(out, "xref\n0 7\n");
        ascii(out, "0000000000 65535 f \n");
        for (int i = 1; i <= 6; i++) {
            ascii(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }
        ascii(out, String.format(Locale.ROOT,
                "trailer\n<< /Size 7 /Root 1 0 R /Info 6 0 R >>\nstartxref\n%d\n%%%%EOF\n", xref));

        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.write(path, out.toByteArray());
    }

    private static void ascii(ByteArrayOutputStream out, String text) {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
    }
}
