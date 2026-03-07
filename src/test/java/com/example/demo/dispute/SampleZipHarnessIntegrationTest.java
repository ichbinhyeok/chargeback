package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.api.FixJobResponse;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixJobStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.AutoFixService;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.SubmissionExportService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SAMPLE_ZIP_HARNESS", matches = "true")
class SampleZipHarnessIntegrationTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private EvidenceFileService evidenceFileService;

    @Autowired
    private AutoFixService autoFixService;

    @Autowired
    private SubmissionExportService submissionExportService;

    @Test
    void generatesRepresentativeSubmissionZips() throws Exception {
        Path outputDir = Path.of(".tmp", "sample-zips");
        Files.createDirectories(outputDir);

        Path stripeZip = generateStripeSampleZip(outputDir);
        Path shopifyZip = generateShopifySampleZip(outputDir);

        assertTrue(Files.exists(stripeZip));
        assertTrue(Files.exists(shopifyZip));
        assertTrue(Files.size(stripeZip) > 0);
        assertTrue(Files.size(shopifyZip) > 0);
        assertTrue(hasEntry(stripeZip, "manifest.json"));
        assertTrue(hasEntry(stripeZip, "dispute_explanation_draft.txt"));
        assertTrue(hasEntry(shopifyZip, "manifest.json"));
        assertTrue(hasEntry(shopifyZip, "dispute_explanation_draft.txt"));
    }

    private Path generateStripeSampleZip(Path outputDir) throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "fraudulent",
                null,
                CardNetwork.VISA
        ));

        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "invoice-main.pdf", "application/pdf", basicPdf("Invoice #2026-031", false))
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "invoice-extra.pdf", "application/pdf", basicPdf("Invoice line-items", false))
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_COMMUNICATION,
                    new MockMultipartFile("file", "support-thread.pdf", "application/pdf", basicPdf("Support thread", true))
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_DETAILS,
                    new MockMultipartFile("file", "customer-profile.png", "image/png", smallPng(240, 180))
            );

            FixJobResponse fix = autoFixService.requestAutoFix(disputeCase.getId());
            assertEquals(FixJobStatus.SUCCEEDED, fix.status(), fix.summary());

            Path zipPath = outputDir.resolve("stripe_submission_sample.zip");
            writeSubmissionZip(disputeCase.getCaseToken(), zipPath);
            return zipPath;
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    private Path generateShopifySampleZip(Path outputDir) throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "fraudulent",
                null,
                null
        ));

        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt-non-pdfa.pdf", "application/pdf", basicPdf("Receipt screenshot export", false))
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_COMMUNICATION,
                    new MockMultipartFile("file", "portfolio.pdf", "application/pdf", portfolioPdf())
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.POLICIES,
                    new MockMultipartFile("file", "policy-oversized.png", "image/png", oversizedPng())
            );

            FixJobResponse fix = autoFixService.requestAutoFix(disputeCase.getId());
            assertEquals(FixJobStatus.SUCCEEDED, fix.status(), fix.summary());

            Path zipPath = outputDir.resolve("shopify_submission_sample.zip");
            writeSubmissionZip(disputeCase.getCaseToken(), zipPath);
            return zipPath;
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    private void writeSubmissionZip(String caseToken, Path zipPath) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipPath)) {
            submissionExportService.writeSubmissionZip(caseToken, out);
        }
    }

    private boolean hasEntry(Path zipPath, String expectedEntry) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (expectedEntry.equals(entry.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private byte[] basicPdf(String title, boolean withExternalLink) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 710);
                content.showText(title);
                content.newLineAtOffset(0, -18);
                content.showText("Order: #A-2042");
                content.newLineAtOffset(0, -18);
                content.showText("Amount: USD 49.00");
                content.endText();
            }

            if (withExternalLink) {
                PDAnnotationLink link = new PDAnnotationLink();
                link.setRectangle(new PDRectangle(72, 620, 280, 24));
                PDActionURI action = new PDActionURI();
                action.setURI("https://tracking.example.com/order/A-2042");
                link.setAction(action);

                var annotations = new ArrayList<>(page.getAnnotations());
                annotations.add(link);
                page.setAnnotations(annotations);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] smallPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = ((x * 5) & 0xFF) << 16 | ((y * 7) & 0xFF) << 8 | 0x2E;
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] oversizedPng() throws Exception {
        BufferedImage image = new BufferedImage(1300, 1300, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(77L);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = (random.nextInt(256) << 16) | (random.nextInt(256) << 8) | random.nextInt(256);
                image.setRGB(x, y, rgb);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] bytes = out.toByteArray();
        if (bytes.length <= 2_097_152L) {
            throw new IllegalStateException("generated image must be larger than 2MB");
        }
        return bytes;
    }

    private byte[] portfolioPdf() {
        String content = """
                %PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R /Collection << /Type /Collection >> >>
                endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] >>
                endobj
                trailer << /Root 1 0 R >>
                %%EOF
                """;
        return content.getBytes(StandardCharsets.ISO_8859_1);
    }
}
