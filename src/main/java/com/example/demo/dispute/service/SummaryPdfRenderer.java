package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.IssueSeverity;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

public final class SummaryPdfRenderer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private SummaryPdfRenderer() {
    }

    public static void write(CaseReportResponse report, OutputStream outputStream, boolean watermark) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            List<String> lines = buildSummaryLines(report);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(14);
                content.newLineAtOffset(48, 740);
                for (String line : lines) {
                    content.showText(sanitize(line));
                    content.newLine();
                }
                content.endText();
            }
            if (watermark) {
                drawWatermark(document, page);
            }

            document.save(outputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to generate summary pdf: " + ex.getMessage(), ex);
        }
    }

    private static List<String> buildSummaryLines(CaseReportResponse report) {
        int blockedCount = 0;
        int fixableCount = 0;
        int warningCount = 0;
        List<ValidationIssueResponse> issues = report.latestValidation() == null
                ? List.of()
                : report.latestValidation().issues();

        for (ValidationIssueResponse issue : issues) {
            if (issue.severity() == IssueSeverity.BLOCKED) {
                blockedCount++;
            } else if (issue.severity() == IssueSeverity.FIXABLE) {
                fixableCount++;
            } else if (issue.severity() == IssueSeverity.WARNING) {
                warningCount++;
            }
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add("Chargeback Submission Guide");
        lines.add("");
        lines.add("Platform: " + report.platform());
        lines.add("Scope: " + report.productScope());
        lines.add("State: " + report.state());
        lines.add("Created at: " + DATE_FORMAT.format(report.createdAt()));
        lines.add("");
        lines.add("Uploaded files:");
        if (report.files().isEmpty()) {
            lines.add("- No files uploaded");
        } else {
            for (EvidenceFileReportResponse file : report.files()) {
                lines.add(String.format(
                        Locale.ROOT,
                        "- %s | %s | %d bytes | pages=%d | externalLink=%s",
                        file.evidenceType(),
                        file.fileFormat(),
                        file.sizeBytes(),
                        file.pageCount(),
                        file.externalLinkDetected()
                ));
            }
        }

        lines.add("");
        lines.add("Validation summary:");
        lines.add(String.format(Locale.ROOT, "- BLOCKED: %d", blockedCount));
        lines.add(String.format(Locale.ROOT, "- FIXABLE: %d", fixableCount));
        lines.add(String.format(Locale.ROOT, "- WARNING: %d", warningCount));

        if (!issues.isEmpty()) {
            lines.add("- Top issues:");
            int limit = Math.min(3, issues.size());
            for (int i = 0; i < limit; i++) {
                ValidationIssueResponse issue = issues.get(i);
                lines.add(String.format(Locale.ROOT, "  %d) %s - %s", i + 1, issue.severity(), issue.message()));
                if (issue.guideSlug() != null && !issue.guideSlug().isBlank()) {
                    lines.add(String.format(
                            Locale.ROOT,
                            "     Fix guide: /guides/%s/%s",
                            report.platform().name().toLowerCase(Locale.ROOT),
                            issue.guideSlug()
                    ));
                }
            }
        }

        lines.add("");
        lines.add("Checklist:");
        lines.add("- Keep one file per evidence type.");
        lines.add("- Remove external links from PDFs.");
        lines.add("- Confirm platform file size/page limits before submission.");
        lines.add("- Use the generated dispute explanation draft and tailor it with transaction-specific facts.");
        lines.add("");
        lines.add("Disclaimer: This tool helps formatting and organization only, and does not guarantee dispute outcomes.");
        return lines;
    }

    private static String sanitize(String line) {
        return line.replace('\t', ' ');
    }

    private static void drawWatermark(PDDocument document, PDPage page) throws IOException {
        try (PDPageContentStream watermarkLayer = new PDPageContentStream(document, page, AppendMode.APPEND, true)) {
            watermarkLayer.beginText();
            watermarkLayer.setNonStrokingColor(0.84f, 0.84f, 0.84f);
            watermarkLayer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 54);
            watermarkLayer.setTextMatrix(Matrix.getRotateInstance(
                    Math.toRadians(35),
                    page.getMediaBox().getWidth() * 0.14f,
                    page.getMediaBox().getHeight() * 0.34f
            ));
            watermarkLayer.showText("FREE VERSION");
            watermarkLayer.endText();

            watermarkLayer.beginText();
            watermarkLayer.setNonStrokingColor(0.88f, 0.88f, 0.88f);
            watermarkLayer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 42);
            watermarkLayer.setTextMatrix(Matrix.getRotateInstance(
                    Math.toRadians(35),
                    page.getMediaBox().getWidth() * 0.22f,
                    page.getMediaBox().getHeight() * 0.27f
            ));
            watermarkLayer.showText("WATERMARKED");
            watermarkLayer.endText();
        }
    }
}
