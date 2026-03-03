package com.example.demo.dispute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.persistence.DisputeCaseRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisputeCaseRepository disputeCaseRepository;

    @Test
    void uploadedStripeEvidenceCanBeValidatedAndMovesToReady() throws Exception {
        UUID caseId = createStripeCase();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                simplePdf()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PDF"))
                .andReturn();

        UUID fileId = extractUuid(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "fileId");

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evidenceType").value("ORDER_RECEIPT"));

        mockMvc.perform(patch("/api/cases/{caseId}/files/{fileId}/classification", caseId, fileId)
                        .contentType("application/json")
                        .content("{\"evidenceType\":\"POLICIES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceType").value("POLICIES"));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.runNo").value(1))
                .andExpect(jsonPath("$.latestValidation.source").value("STORED_FILES"))
                .andExpect(jsonPath("$.files[0].evidenceType").value("POLICIES"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void stripeExternalLinkEvidenceBecomesBlocked() throws Exception {
        UUID caseId = createStripeCase();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                pdfWithExternalLink()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .param("evidenceType", "POLICIES"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_LINK_DETECTED"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.BLOCKED, state);
    }

    @Test
    void autoFixDedupesFilesPerTypeAndRevalidatesCase() throws Exception {
        UUID caseId = createStripeCase();

        MockMultipartFile first = new MockMultipartFile(
                "file",
                "receipt-old.pdf",
                "application/pdf",
                simplePdf()
        );
        MockMultipartFile second = new MockMultipartFile(
                "file",
                "receipt-new.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(first)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(second)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_MULTI_FILE_PER_TYPE"));

        MvcResult fixResult = mockMvc.perform(post("/api/cases/{caseId}/fix", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        UUID jobId = extractUuid(fixResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "jobId");

        mockMvc.perform(get("/api/cases/{caseId}/fix/{jobId}", caseId, jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].evidenceType").value("ORDER_RECEIPT"));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void autoFixWithNoSupportedIssueKeepsReadyState() throws Exception {
        UUID caseId = createStripeCase();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failCode").value("ERR_FIX_NOTHING_TO_FIX"));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void validateEndpointBlocksMastercardWhenPagesExceed19() throws Exception {
        UUID caseId = createStripeMastercardCase();

        String body = """
                {
                  "files": [
                    {
                      "evidenceType": "ORDER_RECEIPT",
                      "format": "PDF",
                      "sizeBytes": 1000,
                      "pageCount": 20,
                      "externalLinkDetected": false,
                      "pdfACompliant": true,
                      "pdfPortfolio": false
                    }
                  ],
                  "earlySubmit": false
                }
                """;

        mockMvc.perform(post("/api/cases/{caseId}/validate", caseId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_MC_19P"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.BLOCKED, state);
    }

    @Test
    void shopifyValidateEndpointCanPassWithEarlySubmitWarning() throws Exception {
        UUID caseId = createShopifyPaymentsCase();

        String body = """
                {
                  "files": [
                    {
                      "evidenceType": "ORDER_RECEIPT",
                      "format": "PDF",
                      "sizeBytes": 120000,
                      "pageCount": 1,
                      "externalLinkDetected": false,
                      "pdfACompliant": true,
                      "pdfPortfolio": false
                    }
                  ],
                  "earlySubmit": true
                }
                """;

        mockMvc.perform(post("/api/cases/{caseId}/validate", caseId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.issues[0].code").value("WARN_SHPFY_EARLY_SUBMIT"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void validateStoredFailsWhenNoUploadedFiles() throws Exception {
        UUID caseId = createStripeCase();

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("no uploaded files found for case"));
    }

    @Test
    void getFixJobReturnsNotFoundWhenJobDoesNotExist() throws Exception {
        UUID caseId = createStripeCase();
        UUID unknownJobId = UUID.randomUUID();

        mockMvc.perform(get("/api/cases/{caseId}/fix/{jobId}", caseId, unknownJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("fix job not found: " + unknownJobId));
    }

    @Test
    void createCaseRejectsMismatchedPlatformAndScope() throws Exception {
        String body = """
                {
                  "platform": "STRIPE",
                  "productScope": "SHOPIFY_PAYMENTS_CHARGEBACK",
                  "reasonCode": "fraudulent"
                }
                """;

        mockMvc.perform(post("/api/cases")
                        .contentType("application/json")
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("product_scope does not match platform. platform=STRIPE, product_scope=SHOPIFY_PAYMENTS_CHARGEBACK"));
    }

    @Test
    void deleteCaseRemovesCaseRecord() throws Exception {
        UUID caseId = createStripeCase();

        mockMvc.perform(delete("/api/cases/{caseId}", caseId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId))
                .andExpect(status().isNotFound());
    }

    private UUID createStripeCase() throws Exception {
        return createCase("""
                {
                  "platform": "STRIPE",
                  "productScope": "STRIPE_DISPUTE",
                  "reasonCode": "fraudulent",
                  "cardNetwork": "VISA"
                }
                """);
    }

    private UUID createStripeMastercardCase() throws Exception {
        return createCase("""
                {
                  "platform": "STRIPE",
                  "productScope": "STRIPE_DISPUTE",
                  "reasonCode": "product_not_received",
                  "cardNetwork": "MASTERCARD"
                }
                """);
    }

    private UUID createShopifyPaymentsCase() throws Exception {
        return createCase("""
                {
                  "platform": "SHOPIFY",
                  "productScope": "SHOPIFY_PAYMENTS_CHARGEBACK",
                  "reasonCode": "fraudulent"
                }
                """);
    }

    private UUID createCase(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .contentType("application/json")
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        return extractUuid(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "caseId");
    }

    private UUID extractUuid(String content, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = content.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException(fieldName + " not found in response: " + content);
        }

        int from = start + marker.length();
        int end = content.indexOf('"', from);
        return UUID.fromString(content.substring(from, end));
    }

    private byte[] simplePdf() throws Exception {
        return """
                %PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] >>
                endobj
                trailer << /Root 1 0 R >>
                %%EOF
                """.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] pdfWithExternalLink() throws Exception {
        return """
                %PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Annots [4 0 R] >>
                endobj
                4 0 obj << /Type /Annot /Subtype /Link /A << /S /URI /URI (https://example.com) >> >>
                endobj
                trailer << /Root 1 0 R >>
                %%EOF
                """.getBytes(StandardCharsets.ISO_8859_1);
    }
}
