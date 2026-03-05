package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.SubmissionExportService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
class SubmissionExportServiceIntegrationTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private EvidenceFileService evidenceFileService;

    @Autowired
    private SubmissionExportService submissionExportService;

    @Test
    void submissionZipUsesNormalizedNamesAndLatestFilePerEvidenceType() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "zip_test",
                null,
                null
        ));

        try {
            byte[] oldPdf = simplePdf("old");
            byte[] newPdf = simplePdf("new");
            byte[] policyPng = simplePng(96, 96);

            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt-old.pdf", "application/pdf", oldPdf)
            );
            Thread.sleep(Duration.ofMillis(5));
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt-new.pdf", "application/pdf", newPdf)
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.POLICIES,
                    new MockMultipartFile("file", "policy.png", "image/png", policyPng)
            );

            caseService.transitionState(disputeCase, CaseState.READY);

            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            submissionExportService.writeSubmissionZip(disputeCase.getCaseToken(), zipBytes);

            Map<String, byte[]> entries = unzip(zipBytes.toByteArray());
            assertEquals(List.of("01_ORDER_RECEIPT.pdf", "02_POLICIES.png"), new ArrayList<>(entries.keySet()));
            assertArrayEquals(newPdf, entries.get("01_ORDER_RECEIPT.pdf"));
            assertArrayEquals(policyPng, entries.get("02_POLICIES.png"));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void freeSummaryPdfIncludesGuideTextAndWatermarkMarkers() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "summary_test",
                null,
                null
        ));

        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt.pdf", "application/pdf", simplePdf("summary"))
            );
            caseService.transitionState(disputeCase, CaseState.READY);

            ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
            submissionExportService.writeSummaryPdf(disputeCase.getCaseToken(), pdfBytes, true);

            try (PDDocument document = Loader.loadPDF(pdfBytes.toByteArray())) {
                String text = new PDFTextStripper().getText(document);
                assertTrue(text.contains("Chargeback Submission Guide"));
                assertTrue(text.contains("Disclaimer: This tool helps formatting and organization only"));

                PDPage firstPage = document.getPage(0);
                String contentStream = readContentStream(firstPage);
                assertTrue(contentStream.contains("FREE VERSION"));
                assertTrue(contentStream.contains("WATERMARKED"));
            }
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                result.put(entry.getName(), out.toByteArray());
                zip.closeEntry();
            }
        }
        return result;
    }

    private byte[] simplePdf(String marker) {
        String content = "%PDF-1.4\n"
                + "% marker-" + marker + "\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >>\n"
                + "endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >>\n"
                + "endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] >>\n"
                + "endobj\n"
                + "trailer << /Root 1 0 R >>\n"
                + "%%EOF\n";
        return content.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] simplePng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x * 7) & 0xFF) << 16 | ((y * 5) & 0xFF) << 8 | 0x4A);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String readContentStream(PDPage page) throws Exception {
        try (InputStream in = page.getContents()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
