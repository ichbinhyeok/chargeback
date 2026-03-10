package com.example.demo.dispute;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.PaymentStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.PaymentEntity;
import com.example.demo.dispute.persistence.PaymentRepository;
import com.example.demo.dispute.service.CaseService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PrelaunchSandboxSmokeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void stripeSandboxSmokeFlowFromUploadToPaidZipDownload() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "13.1",
                null,
                com.example.demo.dispute.domain.CardNetwork.VISA
        ));
        try {
            upload(
                    disputeCase,
                    EvidenceType.ORDER_RECEIPT,
                    "receipt.pdf",
                    "application/pdf",
                    simplePdf("stripe-receipt")
            );
            upload(
                    disputeCase,
                    EvidenceType.CUSTOMER_DETAILS,
                    "customer.pdf",
                    "application/pdf",
                    simplePdf("stripe-customer")
            );
            upload(
                    disputeCase,
                    EvidenceType.FULFILLMENT_DELIVERY,
                    "delivery.pdf",
                    "application/pdf",
                    simplePdf("stripe-delivery")
            );

            mockMvc.perform(post("/api/cases/{caseId}/validate-stored", disputeCase.getId())
                            .header("X-Case-Token", disputeCase.getCaseToken())
                            .contentType("application/json")
                            .content("{\"earlySubmit\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passed").value(true));

            mockMvc.perform(get("/c/{caseToken}", disputeCase.getCaseToken()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Stripe - Product not received")));

            markPaid(disputeCase, "cs_smoke_stripe_" + UUID.randomUUID());

            MvcResult zipResult = mockMvc.perform(get("/c/{caseToken}/download/submission.zip", disputeCase.getCaseToken()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/zip"))
                    .andReturn();

            Map<String, byte[]> entries = unzip(zipResult.getResponse().getContentAsByteArray());
            assertTrue(entries.containsKey("upload_to_platform/01_ORDER_RECEIPT.pdf"));
            assertTrue(entries.containsKey("upload_to_platform/02_CUSTOMER_DETAILS.pdf"));
            assertTrue(entries.containsKey("upload_to_platform/03_FULFILLMENT_DELIVERY.pdf"));
            assertTrue(entries.containsKey("README_FIRST.txt"));
            assertTrue(entries.containsKey("reference/manifest.json"));

            String manifest = new String(entries.get("reference/manifest.json"), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("\"canonicalReasonKey\" : \"PRODUCT_NOT_RECEIVED\""));
            assertTrue(manifest.contains("\"policyContextKey\" : \"platform=STRIPE|scope=STRIPE_DISPUTE|reason=PRODUCT_NOT_RECEIVED|network=VISA\""));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void shopifySandboxSmokeFlowFromUploadToPaidZipDownload() throws Exception {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "fraudulent",
                null,
                null
        ));
        try {
            upload(
                    disputeCase,
                    EvidenceType.ORDER_RECEIPT,
                    "receipt.jpg",
                    "image/jpeg",
                    simpleJpeg(220, 160, 0x2F6A9A)
            );
            upload(
                    disputeCase,
                    EvidenceType.CUSTOMER_DETAILS,
                    "customer.jpg",
                    "image/jpeg",
                    simpleJpeg(220, 160, 0x4D7B3D)
            );
            upload(
                    disputeCase,
                    EvidenceType.FULFILLMENT_DELIVERY,
                    "delivery.jpg",
                    "image/jpeg",
                    simpleJpeg(220, 160, 0x8A5A2B)
            );

            mockMvc.perform(post("/api/cases/{caseId}/validate-stored", disputeCase.getId())
                            .header("X-Case-Token", disputeCase.getCaseToken())
                            .contentType("application/json")
                            .content("{\"earlySubmit\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passed").value(true));

            mockMvc.perform(get("/c/{caseToken}", disputeCase.getCaseToken()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Shopify - Fraudulent")));

            markPaid(disputeCase, "cs_smoke_shopify_" + UUID.randomUUID());

            MvcResult zipResult = mockMvc.perform(get("/c/{caseToken}/download/submission.zip", disputeCase.getCaseToken()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/zip"))
                    .andReturn();

            Map<String, byte[]> entries = unzip(zipResult.getResponse().getContentAsByteArray());
            assertTrue(entries.containsKey("upload_to_platform/01_ORDER_RECEIPT.jpg"));
            assertTrue(entries.containsKey("upload_to_platform/02_CUSTOMER_DETAILS.jpg"));
            assertTrue(entries.containsKey("upload_to_platform/03_FULFILLMENT_DELIVERY.jpg"));
            assertTrue(entries.containsKey("README_FIRST.txt"));
            assertTrue(entries.containsKey("reference/manifest.json"));

            String manifest = new String(entries.get("reference/manifest.json"), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("\"canonicalReasonKey\" : \"FRAUDULENT\""));
            assertTrue(manifest.contains("\"policyContextKey\" : \"platform=SHOPIFY|scope=SHOPIFY_PAYMENTS_CHARGEBACK|reason=FRAUDULENT|network=-\""));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    private void upload(
            DisputeCase disputeCase,
            EvidenceType evidenceType,
            String fileName,
            String contentType,
            byte[] fileBytes
    ) throws Exception {
        mockMvc.perform(multipart("/api/cases/{caseId}/files", disputeCase.getId())
                        .file(new MockMultipartFile("file", fileName, contentType, fileBytes))
                        .header("X-Case-Token", disputeCase.getCaseToken())
                        .param("evidenceType", evidenceType.name()))
                .andExpect(status().isOk());
    }

    private void markPaid(DisputeCase disputeCase, String checkoutSessionId) {
        PaymentEntity payment = new PaymentEntity();
        payment.setDisputeCase(disputeCase);
        payment.setProvider("stripe");
        payment.setCheckoutSessionId(checkoutSessionId);
        payment.setPaymentIntentId("pi_" + checkoutSessionId);
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmountCents(1900L);
        payment.setCurrency("usd");
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);
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

    private byte[] simpleJpeg(int width, int height, int colorRgb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, colorRgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
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
}
