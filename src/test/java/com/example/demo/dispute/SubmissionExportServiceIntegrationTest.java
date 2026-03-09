package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.PublicCaseReference;
import com.example.demo.dispute.service.SubmissionExportService;
import com.example.demo.dispute.service.ValidationHistoryService;
import com.example.demo.dispute.service.ValidationService;
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

    @Autowired
    private ValidationService validationService;

    @Autowired
    private ValidationHistoryService validationHistoryService;

    @Test
    void submissionZipUsesNormalizedNamesForValidFreshCase() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "zip_test",
                null,
                null
        ));

        try {
            byte[] receiptPdf = simplePdf("receipt");
            byte[] customerPdf = simplePdf("customer");
            byte[] communicationPdf = simplePdf("communication");
            byte[] policyPng = simplePng(96, 96);

            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt.pdf", "application/pdf", receiptPdf)
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_DETAILS,
                    new MockMultipartFile("file", "customer.pdf", "application/pdf", customerPdf)
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_COMMUNICATION,
                    new MockMultipartFile("file", "communication.pdf", "application/pdf", communicationPdf)
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.POLICIES,
                    new MockMultipartFile("file", "policy.png", "image/png", policyPng)
            );

            moveCaseToReady(disputeCase);
            recordValidation(disputeCase);

            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            submissionExportService.writeSubmissionZip(disputeCase.getCaseToken(), zipBytes);

            Map<String, byte[]> entries = unzip(zipBytes.toByteArray());
            assertEquals(
                    List.of(
                            "upload_to_platform/01_ORDER_RECEIPT.pdf",
                            "upload_to_platform/02_CUSTOMER_DETAILS.pdf",
                            "upload_to_platform/03_CUSTOMER_COMMUNICATION.pdf",
                            "upload_to_platform/04_POLICIES.png",
                            "README_FIRST.txt",
                            "reference/dispute_explanation_draft.txt",
                            "reference/manifest.json"
                    ),
                    new ArrayList<>(entries.keySet())
            );
            assertArrayEquals(receiptPdf, entries.get("upload_to_platform/01_ORDER_RECEIPT.pdf"));
            assertArrayEquals(customerPdf, entries.get("upload_to_platform/02_CUSTOMER_DETAILS.pdf"));
            assertArrayEquals(communicationPdf, entries.get("upload_to_platform/03_CUSTOMER_COMMUNICATION.pdf"));
            assertArrayEquals(policyPng, entries.get("upload_to_platform/04_POLICIES.png"));
            String readme = new String(entries.get("README_FIRST.txt"), StandardCharsets.UTF_8);
            assertTrue(readme.contains("Upload only the files inside 'upload_to_platform/'"));
            assertTrue(readme.contains("reference/dispute_explanation_draft.txt"));
            String explanation = new String(entries.get("reference/dispute_explanation_draft.txt"), StandardCharsets.UTF_8);
            String publicCaseRef = PublicCaseReference.from(disputeCase);
            assertTrue(explanation.contains("Dispute Explanation Draft"));
            assertTrue(explanation.contains(publicCaseRef));
            assertTrue(explanation.contains("Disclaimer: This draft is a submission-writing aid only."));
            assertTrue(!explanation.contains(disputeCase.getCaseToken()));
            String manifest = new String(entries.get("reference/manifest.json"), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("\"publicCaseRef\""));
            assertTrue(!manifest.contains("\"caseId\""));
            assertTrue(!manifest.contains(disputeCase.getCaseToken()));
            assertTrue(!manifest.contains("storagePath"));
            assertTrue(manifest.contains("\"policyVersion\""));
            assertTrue(manifest.contains("\"policyContextKey\""));
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
            moveCaseToReady(disputeCase);

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

    @Test
    void submissionZipReadmeAndManifestExposeIssueGuideMetadata() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "zip_issue_guide_test",
                null,
                null
        ));

        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt.pdf", "application/pdf", simplePdf("guide"))
            );
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_DETAILS,
                    new MockMultipartFile("file", "customer.pdf", "application/pdf", simplePdf("guide-customer"))
            );
            moveCaseToReady(disputeCase);

            validationHistoryService.record(
                    disputeCase,
                    new ValidateCaseResponse(
                            true,
                            List.of(new ValidationIssueResponse(
                                    "ERR_STRIPE_TOTAL_SIZE",
                                    "STR_SIZE_001",
                                    IssueSeverity.WARNING,
                                    "Synthetic warning issue to verify guide links in export artifacts.",
                                    IssueTargetScope.GLOBAL,
                                    null,
                                    null,
                                    null,
                                    FixStrategy.REDUCE_TOTAL_SIZE,
                                    "evidence-file-size-limit-4-5mb",
                                    "Stripe evidence size limit"
                            ))
                    ),
                    ValidationSource.STORED_FILES,
                    false,
                    evidenceFileService.listAsValidationInputs(disputeCase.getId())
            );

            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            submissionExportService.writeSubmissionZip(disputeCase.getCaseToken(), zipBytes);

            Map<String, byte[]> entries = unzip(zipBytes.toByteArray());
            String readme = new String(entries.get("README_FIRST.txt"), StandardCharsets.UTF_8);
            String manifest = new String(entries.get("reference/manifest.json"), StandardCharsets.UTF_8);

            assertTrue(readme.contains("Issue-specific fix guides"));
            assertTrue(readme.contains("/guides/stripe/evidence-file-size-limit-4-5mb"));
            assertTrue(manifest.contains("\"guideSlug\" : \"evidence-file-size-limit-4-5mb\""));
            assertTrue(manifest.contains("\"guidePath\" : \"/guides/stripe/evidence-file-size-limit-4-5mb\""));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void shopifyCreditSubmissionZipKeepsAllFilesEvenForSameEvidenceType() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_CREDIT_DISPUTE,
                "shopify_credit_export",
                null,
                null
        ));

        try {
            byte[] firstReceipt = simplePdf("receipt-first");
            byte[] secondReceipt = simplePdf("receipt-second");
            byte[] customerPng = simplePng(80, 80);
            byte[] communicationPdf = simplePdf("shopify-credit-communication");

            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt-first.pdf", "application/pdf", firstReceipt)
            );
            Thread.sleep(Duration.ofMillis(5));
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_DETAILS,
                    new MockMultipartFile("file", "customer.png", "image/png", customerPng)
            );
            Thread.sleep(Duration.ofMillis(5));
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt-second.pdf", "application/pdf", secondReceipt)
            );
            Thread.sleep(Duration.ofMillis(5));
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_COMMUNICATION,
                    new MockMultipartFile("file", "communication.pdf", "application/pdf", communicationPdf)
            );

            moveCaseToReady(disputeCase);
            recordValidation(disputeCase);

            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            submissionExportService.writeSubmissionZip(disputeCase.getCaseToken(), zipBytes);

            Map<String, byte[]> entries = unzip(zipBytes.toByteArray());
            assertEquals(
                    List.of(
                            "upload_to_platform/01_ORDER_RECEIPT_01.pdf",
                            "upload_to_platform/02_ORDER_RECEIPT_02.pdf",
                            "upload_to_platform/03_CUSTOMER_DETAILS.png",
                            "upload_to_platform/04_CUSTOMER_COMMUNICATION.pdf",
                            "README_FIRST.txt",
                            "reference/dispute_explanation_draft.txt",
                            "reference/manifest.json"
                    ),
                    new ArrayList<>(entries.keySet())
            );
            assertArrayEquals(firstReceipt, entries.get("upload_to_platform/01_ORDER_RECEIPT_01.pdf"));
            assertArrayEquals(secondReceipt, entries.get("upload_to_platform/02_ORDER_RECEIPT_02.pdf"));
            assertArrayEquals(customerPng, entries.get("upload_to_platform/03_CUSTOMER_DETAILS.png"));
            assertArrayEquals(communicationPdf, entries.get("upload_to_platform/04_CUSTOMER_COMMUNICATION.pdf"));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void submissionZipRejectsStaleValidationAfterFilesChanged() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "stale_validation_export",
                null,
                null
        ));

        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt.pdf", "application/pdf", simplePdf("stale-base"))
            );
            moveCaseToReady(disputeCase);

            var firstValidation = validationService.validate(
                    disputeCase,
                    evidenceFileService.listAsValidationInputs(disputeCase.getId()),
                    false
            );
            validationHistoryService.record(
                    disputeCase,
                    firstValidation,
                    ValidationSource.STORED_FILES,
                    false,
                    evidenceFileService.listAsValidationInputs(disputeCase.getId())
            );

            Thread.sleep(Duration.ofMillis(5));
            evidenceFileService.upload(
                    disputeCase.getId(),
                    EvidenceType.CUSTOMER_COMMUNICATION,
                    new MockMultipartFile("file", "comm.pdf", "application/pdf", simplePdf("stale-new"))
            );
            // Simulate an incorrect state restoration without a fresh validation run.
            moveCaseToReady(disputeCase);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> submissionExportService.writeSubmissionZip(disputeCase.getCaseToken(), new ByteArrayOutputStream())
            );
            assertTrue(ex.getMessage().contains("stale"));
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

    private void moveCaseToReady(DisputeCase disputeCase) {
        caseService.transitionState(disputeCase, CaseState.UPLOADING);
        caseService.transitionState(disputeCase, CaseState.VALIDATING);
        caseService.transitionState(disputeCase, CaseState.READY);
    }

    private void recordValidation(DisputeCase disputeCase) {
        var inputs = evidenceFileService.listAsValidationInputs(disputeCase.getId());
        var validation = validationService.validate(disputeCase, inputs, false);
        validationHistoryService.record(disputeCase, validation, ValidationSource.STORED_FILES, false, inputs);
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

