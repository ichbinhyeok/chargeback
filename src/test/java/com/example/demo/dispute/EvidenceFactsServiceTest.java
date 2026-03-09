package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.service.EvidenceAliasCatalogService;
import com.example.demo.dispute.service.EvidenceFactsService;
import com.example.demo.dispute.service.EvidenceTextExtractionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceFactsServiceTest {

    private final EvidenceAliasCatalogService aliasCatalogService = new EvidenceAliasCatalogService();

    @Test
    void analyzeUsesImageOcrTextAsAnchorSignalForCoherence() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID imageFileId = UUID.randomUUID();
        UUID pdfFileId = UUID.randomUUID();

        Path tempImage = Files.createTempFile("evidence-facts-", ".png");
        try {
            EvidenceFileEntity storedImage = new EvidenceFileEntity();
            storedImage.setId(imageFileId);
            storedImage.setEvidenceType(EvidenceType.ORDER_RECEIPT);
            storedImage.setOriginalName("document_ocr.png");
            storedImage.setStoragePath(tempImage.toString());
            storedImage.setFileFormat(FileFormat.PNG);

            EvidenceFileRepository repository = mock(EvidenceFileRepository.class);
            when(repository.findByDisputeCaseId(caseId)).thenReturn(List.of(storedImage));

            EvidenceTextExtractionService extractionService = new EvidenceTextExtractionService() {
                @Override
                public String extractImageOcrText(Path path, int maxChars) {
                    return "order receipt payment total 84.20 order number nw-9001";
                }
            };

            EvidenceFactsService service = new EvidenceFactsService(repository, extractionService, aliasCatalogService);
            EvidenceFactsService.CaseEvidenceFacts facts = service.analyze(
                    caseId,
                    List.of(
                            reportFile(imageFileId, EvidenceType.ORDER_RECEIPT, "document_ocr.png", FileFormat.PNG),
                            reportFile(pdfFileId, EvidenceType.CUSTOMER_COMMUNICATION, "support_order_NW-9001.pdf", FileFormat.PDF)
                    )
            );

            assertTrue(facts.fileFacts().stream()
                    .filter(file -> file.fileId().equals(imageFileId))
                    .anyMatch(file -> file.orderRefs().contains("NW-9001")));
            assertTrue(facts.sharedAnchors().stream().anyMatch(anchor -> "NW-9001".equals(anchor.value())));
            assertTrue(facts.coherenceHighlights().stream().anyMatch(line -> line.contains("NW-9001")));
            assertTrue(facts.coherenceScore() >= 60);
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    @Test
    void analyzeNormalizesOrderReferencesAcrossSpacesHyphensAndCompactForms() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID imageFileId = UUID.randomUUID();
        UUID communicationFileId = UUID.randomUUID();
        UUID detailFileId = UUID.randomUUID();

        Path tempImage = Files.createTempFile("evidence-facts-normalize-", ".png");
        try {
            EvidenceFileEntity storedImage = new EvidenceFileEntity();
            storedImage.setId(imageFileId);
            storedImage.setEvidenceType(EvidenceType.ORDER_RECEIPT);
            storedImage.setOriginalName("receipt_capture.png");
            storedImage.setStoragePath(tempImage.toString());
            storedImage.setFileFormat(FileFormat.PNG);

            EvidenceFileRepository repository = mock(EvidenceFileRepository.class);
            when(repository.findByDisputeCaseId(caseId)).thenReturn(List.of(storedImage));

            EvidenceTextExtractionService extractionService = new EvidenceTextExtractionService() {
                @Override
                public String extractImageOcrText(Path path, int maxChars) {
                    return "order no. nw 9001 payment total 84.20";
                }
            };

            EvidenceFactsService service = new EvidenceFactsService(repository, extractionService, aliasCatalogService);
            EvidenceFactsService.CaseEvidenceFacts facts = service.analyze(
                    caseId,
                    List.of(
                            reportFile(imageFileId, EvidenceType.ORDER_RECEIPT, "receipt_capture.png", FileFormat.PNG),
                            reportFile(communicationFileId, EvidenceType.CUSTOMER_COMMUNICATION, "merchant_order_NW-9001.pdf", FileFormat.PDF),
                            reportFile(detailFileId, EvidenceType.CUSTOMER_DETAILS, "ord_NW9001.pdf", FileFormat.PDF)
                    )
            );

            assertTrue(facts.fileFacts().stream()
                    .filter(file -> file.fileId().equals(imageFileId))
                    .anyMatch(file -> file.orderRefs().contains("NW-9001")));
            assertTrue(facts.sharedAnchors().stream().anyMatch(anchor -> "NW-9001".equals(anchor.value())));
            assertTrue(facts.sharedAnchors().stream().anyMatch(anchor -> anchor.evidenceTypes().size() >= 3));
            assertTrue(facts.coherenceHighlights().stream().anyMatch(line -> line.contains("NW-9001")));
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    @Test
    void analyzeNormalizesCarrierSpecificTrackingAliases() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID imageFileId = UUID.randomUUID();
        UUID pdfFileId = UUID.randomUUID();

        Path tempImage = Files.createTempFile("evidence-facts-carrier-", ".png");
        try {
            EvidenceFileEntity storedImage = new EvidenceFileEntity();
            storedImage.setId(imageFileId);
            storedImage.setEvidenceType(EvidenceType.FULFILLMENT_DELIVERY);
            storedImage.setOriginalName("delivery_capture.png");
            storedImage.setStoragePath(tempImage.toString());
            storedImage.setFileFormat(FileFormat.PNG);

            EvidenceFileRepository repository = mock(EvidenceFileRepository.class);
            when(repository.findByDisputeCaseId(caseId)).thenReturn(List.of(storedImage));

            EvidenceTextExtractionService extractionService = new EvidenceTextExtractionService() {
                @Override
                public String extractImageOcrText(Path path, int maxChars) {
                    return "tracking number 9400 1111 2222 3333 4444 55 delivered";
                }
            };

            EvidenceFactsService service = new EvidenceFactsService(repository, extractionService, aliasCatalogService);
            EvidenceFactsService.CaseEvidenceFacts facts = service.analyze(
                    caseId,
                    List.of(
                            reportFile(imageFileId, EvidenceType.FULFILLMENT_DELIVERY, "delivery_capture.png", FileFormat.PNG),
                            reportFile(pdfFileId, EvidenceType.CUSTOMER_COMMUNICATION, "tracking_9400111122223333444455.pdf", FileFormat.PDF)
                    )
            );

            assertTrue(facts.fileFacts().stream()
                    .filter(file -> file.fileId().equals(imageFileId))
                    .anyMatch(file -> file.trackingRefs().contains("USPS:9400111122223333444455")));
            assertTrue(facts.sharedAnchors().stream().anyMatch(anchor -> "USPS:9400111122223333444455".equals(anchor.value())));
            assertTrue(facts.coherenceHighlights().stream().anyMatch(line -> line.contains("USPS 9400111122223333444455")));
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    @Test
    void analyzeNormalizesMerchantLocalOrderAliases() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID imageFileId = UUID.randomUUID();
        UUID pdfFileId = UUID.randomUUID();

        Path tempImage = Files.createTempFile("evidence-facts-booking-", ".png");
        try {
            EvidenceFileEntity storedImage = new EvidenceFileEntity();
            storedImage.setId(imageFileId);
            storedImage.setEvidenceType(EvidenceType.ORDER_RECEIPT);
            storedImage.setOriginalName("booking_capture.png");
            storedImage.setStoragePath(tempImage.toString());
            storedImage.setFileFormat(FileFormat.PNG);

            EvidenceFileRepository repository = mock(EvidenceFileRepository.class);
            when(repository.findByDisputeCaseId(caseId)).thenReturn(List.of(storedImage));

            EvidenceTextExtractionService extractionService = new EvidenceTextExtractionService() {
                @Override
                public String extractImageOcrText(Path path, int maxChars) {
                    return "merchant reference bk 2042 booking confirmed";
                }
            };

            EvidenceFactsService service = new EvidenceFactsService(repository, extractionService, aliasCatalogService);
            EvidenceFactsService.CaseEvidenceFacts facts = service.analyze(
                    caseId,
                    List.of(
                            reportFile(imageFileId, EvidenceType.ORDER_RECEIPT, "booking_capture.png", FileFormat.PNG),
                            reportFile(pdfFileId, EvidenceType.CUSTOMER_COMMUNICATION, "reservation_ref_BK-2042.pdf", FileFormat.PDF)
                    )
            );

            assertTrue(facts.fileFacts().stream()
                    .filter(file -> file.fileId().equals(imageFileId))
                    .anyMatch(file -> file.orderRefs().contains("BK-2042")));
            assertTrue(facts.sharedAnchors().stream().anyMatch(anchor -> "BK-2042".equals(anchor.value())));
            assertTrue(facts.coherenceHighlights().stream().anyMatch(line -> line.contains("BK-2042")));
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    @Test
    void analyzeNormalizesFedexTrackingWhenCarrierHintExists() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID imageFileId = UUID.randomUUID();
        UUID pdfFileId = UUID.randomUUID();

        Path tempImage = Files.createTempFile("evidence-facts-fedex-", ".png");
        try {
            EvidenceFileEntity storedImage = new EvidenceFileEntity();
            storedImage.setId(imageFileId);
            storedImage.setEvidenceType(EvidenceType.FULFILLMENT_DELIVERY);
            storedImage.setOriginalName("fedex_capture.png");
            storedImage.setStoragePath(tempImage.toString());
            storedImage.setFileFormat(FileFormat.PNG);

            EvidenceFileRepository repository = mock(EvidenceFileRepository.class);
            when(repository.findByDisputeCaseId(caseId)).thenReturn(List.of(storedImage));

            EvidenceTextExtractionService extractionService = new EvidenceTextExtractionService() {
                @Override
                public String extractImageOcrText(Path path, int maxChars) {
                    return "fedex tracking number 1234 5678 9012 delivered";
                }
            };

            EvidenceFactsService service = new EvidenceFactsService(repository, extractionService, aliasCatalogService);
            EvidenceFactsService.CaseEvidenceFacts facts = service.analyze(
                    caseId,
                    List.of(
                            reportFile(imageFileId, EvidenceType.FULFILLMENT_DELIVERY, "fedex_capture.png", FileFormat.PNG),
                            reportFile(pdfFileId, EvidenceType.CUSTOMER_COMMUNICATION, "fedex_tracking_123456789012.pdf", FileFormat.PDF)
                    )
            );

            assertTrue(facts.fileFacts().stream()
                    .filter(file -> file.fileId().equals(imageFileId))
                    .anyMatch(file -> file.trackingRefs().contains("FEDEX:123456789012")));
            assertTrue(facts.sharedAnchors().stream().anyMatch(anchor -> "FEDEX:123456789012".equals(anchor.value())));
            assertTrue(facts.coherenceHighlights().stream().anyMatch(line -> line.contains("FEDEX 123456789012")));
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    private EvidenceFileReportResponse reportFile(
            UUID fileId,
            EvidenceType evidenceType,
            String originalName,
            FileFormat format
    ) {
        return new EvidenceFileReportResponse(
                fileId,
                evidenceType,
                originalName,
                format,
                1200,
                1,
                false,
                true,
                false,
                Instant.now()
        );
    }
}
