package com.example.demo.dispute.tools;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.ValidationRunReportResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.service.SummaryPdfRenderer;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SummaryPdfFixtureWriter {

    private SummaryPdfFixtureWriter() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        Files.createDirectories(config.pdfPath().getParent());
        try (OutputStream out = Files.newOutputStream(config.pdfPath())) {
            SummaryPdfRenderer.write(buildFixtureReport(), out, true);
        }
        System.out.println("PDF=" + config.pdfPath());
    }

    private static CaseReportResponse buildFixtureReport() {
        Instant createdAt = Instant.parse("2026-03-01T12:00:00Z");
        Instant validatedAt = Instant.parse("2026-03-01T12:35:00Z");

        return new CaseReportResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "case_summary_fixture",
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "PRODUCT_NOT_RECEIVED",
                null,
                CaseState.READY,
                createdAt,
                new ValidationRunReportResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        2,
                        true,
                        ValidationSource.AUTO_FIX,
                        false,
                        validatedAt,
                        List.of()
                ),
                List.of(
                        new EvidenceFileReportResponse(
                                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                                EvidenceType.ORDER_RECEIPT,
                                "order_receipt_autofix.pdf",
                                FileFormat.PDF,
                                184_512L,
                                2,
                                false,
                                true,
                                false,
                                createdAt.plusSeconds(60)
                        ),
                        new EvidenceFileReportResponse(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                EvidenceType.CUSTOMER_DETAILS,
                                "customer_profile_autofix.pdf",
                                FileFormat.PDF,
                                96_128L,
                                1,
                                false,
                                true,
                                false,
                                createdAt.plusSeconds(90)
                        ),
                        new EvidenceFileReportResponse(
                                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                                EvidenceType.FULFILLMENT_DELIVERY,
                                "carrier_tracking_autofix.pdf",
                                FileFormat.PDF,
                                221_376L,
                                3,
                                false,
                                true,
                                false,
                                createdAt.plusSeconds(120)
                        ),
                        new EvidenceFileReportResponse(
                                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                                EvidenceType.CUSTOMER_COMMUNICATION,
                                "chat_followup.jpg",
                                FileFormat.JPEG,
                                88_064L,
                                1,
                                false,
                                true,
                                false,
                                createdAt.plusSeconds(150)
                        )
                )
        );
    }

    private record Config(Path pdfPath) {
        private static Config parse(String[] args) {
            Path pdfPath = Path.of("build", "tmp", "summary-pdf-fixture", "summary-fixture.pdf");
            for (String arg : args) {
                if (arg.startsWith("--pdf=")) {
                    pdfPath = Path.of(arg.substring("--pdf=".length()));
                    continue;
                }
                throw new IllegalArgumentException("unknown argument: " + arg);
            }
            return new Config(pdfPath);
        }
    }
}
