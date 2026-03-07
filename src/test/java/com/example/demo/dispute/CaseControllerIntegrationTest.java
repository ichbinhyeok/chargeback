package com.example.demo.dispute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.persistence.DisputeCaseRepository;
import com.example.demo.dispute.service.PublicCaseReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
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
class CaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisputeCaseRepository disputeCaseRepository;

    @Test
    void uploadedStripeEvidenceCanBeValidatedAndMovesToReady() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                simplePdf()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PDF"))
                .andReturn();

        UUID fileId = extractUuid(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "fileId");

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evidenceType").value("ORDER_RECEIPT"));

        mockMvc.perform(patch("/api/cases/{caseId}/files/{fileId}/classification", caseId, fileId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"evidenceType\":\"POLICIES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceType").value("POLICIES"));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.runNo").value(1))
                .andExpect(jsonPath("$.latestValidation.source").value("STORED_FILES"))
                .andExpect(jsonPath("$.files[0].evidenceType").value("POLICIES"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void uploadRejectsCorruptedImagePayload() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile corruptedImage = new MockMultipartFile(
                "file",
                "broken.png",
                "image/png",
                "not-a-real-image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(corruptedImage)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid image file: unreadable image content"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void stripeExternalLinkEvidenceBecomesBlocked() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                pdfWithExternalLink()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "POLICIES"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_LINK_DETECTED"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.BLOCKED, state);
    }

    @Test
    void autoFixRemovesExternalLinksFromPdfAndRevalidatesCase() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                pdfWithExternalLink()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "POLICIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalLinkDetected").value(true));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_LINK_DETECTED"))
                .andExpect(jsonPath("$.issues[0].fixStrategy").value("REMOVE_EXTERNAL_LINKS_PDF"));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalLinkDetected").value(false));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void autoFixDedupesFilesPerTypeAndRevalidatesCase() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

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
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(second)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_MULTI_FILE_PER_TYPE"));

        MvcResult fixResult = mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        UUID jobId = extractUuid(fixResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "jobId");

        mockMvc.perform(get("/api/cases/{caseId}/fix/{jobId}", caseId, jobId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].evidenceType").value("ORDER_RECEIPT"));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void autoFixWithNoSupportedIssueKeepsReadyState() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failCode").value("ERR_FIX_NOTHING_TO_FIX"));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void stripeAutoFixCompressesOversizedPdf() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile oversizedPdf = new MockMultipartFile(
                "file",
                "oversized.pdf",
                "application/pdf",
                oversizedPdfByPadding()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(oversizedPdf)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PDF"));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_STRIPE_TOTAL_SIZE"))
                .andExpect(jsonPath("$.issues[0].fixStrategy").value("COMPRESS_STRIPE_PDF"));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sizeBytes").value(org.hamcrest.Matchers.lessThan(4_718_592)));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));
    }

    @Test
    void shopifyAutoFixConvertsPdfToPdfa() throws Exception {
        CaseRef caseRef = createShopifyPaymentsCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile nonPdfa = new MockMultipartFile(
                "file",
                "non-pdfa.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(nonPdfa)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfACompliant").value(false));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_SHPFY_PDF_NOT_PDFA"))
                .andExpect(jsonPath("$.issues[0].fixStrategy").value("CONVERT_PDF_TO_PDFA"));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pdfACompliant").value(true));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));
    }

    @Test
    void shopifyAutoFixFlattensPortfolioPdf() throws Exception {
        CaseRef caseRef = createShopifyPaymentsCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile portfolioFile = new MockMultipartFile(
                "file",
                "portfolio.pdf",
                "application/pdf",
                portfolioPdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(portfolioFile)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfPortfolio").value(true));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_SHPFY_PDF_PORTFOLIO"))
                .andExpect(jsonPath("$.issues[0].fixStrategy").value("FLATTEN_PDF_PORTFOLIO"));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pdfPortfolio").value(false))
                .andExpect(jsonPath("$[0].pdfACompliant").value(true));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));
    }

    @Test
    void validateEndpointBlocksMastercardWhenPagesExceed19() throws Exception {
        CaseRef caseRef = createStripeMastercardCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

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
                        .header("X-Case-Token", caseToken)
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
        CaseRef caseRef = createShopifyPaymentsCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

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
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.issues[0].code").value("WARN_SHPFY_EARLY_SUBMIT"));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void shopifyCreditValidateEndpointIgnoresPaymentsOnlyRules() throws Exception {
        CaseRef caseRef = createShopifyCreditCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        String body = """
                {
                  "files": [
                    {
                      "evidenceType": "ORDER_RECEIPT",
                      "format": "PDF",
                      "sizeBytes": 2400000,
                      "pageCount": 10,
                      "externalLinkDetected": true,
                      "pdfACompliant": false,
                      "pdfPortfolio": true
                    },
                    {
                      "evidenceType": "ORDER_RECEIPT",
                      "format": "PDF",
                      "sizeBytes": 600000,
                      "pageCount": 5,
                      "externalLinkDetected": false,
                      "pdfACompliant": false,
                      "pdfPortfolio": false
                    }
                  ],
                  "earlySubmit": true
                }
                """;

        mockMvc.perform(post("/api/cases/{caseId}/validate", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.issues.length()").value(0));

        CaseState state = disputeCaseRepository.findById(caseId).orElseThrow().getState();
        org.junit.jupiter.api.Assertions.assertEquals(CaseState.READY, state);
    }

    @Test
    void validateStoredFailsWhenNoUploadedFiles() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("no uploaded files found for case"));
    }

    @Test
    void getFixJobReturnsNotFoundWhenJobDoesNotExist() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();
        UUID unknownJobId = UUID.randomUUID();

        mockMvc.perform(get("/api/cases/{caseId}/fix/{jobId}", caseId, unknownJobId)
                        .header("X-Case-Token", caseToken))
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
    void createCaseTrimsReasonCode() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/cases")
                        .contentType("application/json")
                        .content("""
                                {
                                  "platform": "STRIPE",
                                  "productScope": "STRIPE_DISPUTE",
                                  "reasonCode": "  product_not_received  "
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        UUID caseId = extractUuid(create.getResponse().getContentAsString(StandardCharsets.UTF_8), "caseId");
        String caseToken = extractText(create.getResponse().getContentAsString(StandardCharsets.UTF_8), "caseToken");

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("product_not_received"));
    }

    @Test
    void createCaseRejectsTooLongReasonCode() throws Exception {
        String longReason = "x".repeat(81);
        String body = """
                {
                  "platform": "STRIPE",
                  "productScope": "STRIPE_DISPUTE",
                  "reasonCode": "%s"
                }
                """.formatted(longReason);

        mockMvc.perform(post("/api/cases")
                        .contentType("application/json")
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("reason_code is too long (max 80 chars)"));
    }

    @Test
    void deleteCaseRemovesCaseRecord() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        mockMvc.perform(delete("/api/cases/{caseId}", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void caseApiRequiresCaseTokenHeader() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing X-Case-Token header"));
    }

    @Test
    void shopifyAutoFixCompressesOversizedImage() throws Exception {
        CaseRef caseRef = createShopifyPaymentsCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile oversizedImage = new MockMultipartFile(
                "file",
                "oversized.png",
                "image/png",
                oversizedButCreditSafePng()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(oversizedImage)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PNG"));

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("ERR_SHPFY_FILE_TOO_LARGE"));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileFormat").value("JPEG"))
                .andExpect(jsonPath("$[0].sizeBytes").value(org.hamcrest.Matchers.lessThanOrEqualTo(2_097_152)));

        mockMvc.perform(get("/api/cases/{caseId}/report", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestValidation.source").value("AUTO_FIX"))
                .andExpect(jsonPath("$.latestValidation.passed").value(true));
    }

    @Test
    void shopifyCreditDoesNotAutoCompressOversizedImage() throws Exception {
        CaseRef caseRef = createShopifyCreditCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile oversizedImage = new MockMultipartFile(
                "file",
                "oversized.png",
                "image/png",
                oversizedPng()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(oversizedImage)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PNG"));

        mockMvc.perform(post("/api/cases/{caseId}/fix", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failCode").value("ERR_FIX_NOTHING_TO_FIX"));

        mockMvc.perform(get("/api/cases/{caseId}/files", caseId)
                        .header("X-Case-Token", caseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileFormat").value("PNG"))
                .andExpect(jsonPath("$[0].sizeBytes").value(org.hamcrest.Matchers.greaterThan(2_097_152)));
    }

    @Test
    void robotsTxtIncludesSitemapAndSensitiveDisallowRules() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disallow: /c/")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disallow: /api/")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sitemap: http://localhost:8080/sitemap.xml")));
    }

    @Test
    void homePageHighlightsUploadReadyEvidencePackPositioning() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Turn raw dispute files into an")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("upload-ready evidence pack")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation is free")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("to unlock downloads")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start Free Validation")));
    }

    @Test
    void newCasePageRendersReasonPresetControls() throws Exception {
        mockMvc.perform(get("/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Privacy & Trust")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation is free. Unlock the downloadable evidence pack for")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What happens next")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No files are uploaded on this page.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation runs automatically after upload.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create New Evidence Pack")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reason Code Preset")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Continue to Free Validation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"reasonPreset\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-platform=\"STRIPE\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-platform=\"SHOPIFY\"")));
    }

    @Test
    void dashboardGuidesFirstUploadWhenNoFilesExist() throws Exception {
        CaseRef caseRef = createStripeCase();

        mockMvc.perform(get("/c/{caseToken}", caseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What to click next")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload your first evidence files")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nothing is submitted yet on this dashboard.")));
    }

    @Test
    void uploadPageExplainsMappingModalAndValidationSequence() throws Exception {
        CaseRef caseRef = createStripeProductNotReceivedCase();

        mockMvc.perform(get("/c/{caseToken}/upload", caseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("How upload works")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nothing uploads until you confirm in the mapping modal.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation starts automatically after each confirmed upload.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Re-run Validation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Still needed for this reason")));
    }

    @Test
    void dashboardAndValidatePagesSeparateFormatPassFromExportReadiness() throws Exception {
        CaseRef caseRef = createStripeProductNotReceivedCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(get("/c/{caseToken}", caseToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Format checks passed, but required evidence is still missing.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Customer details")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Ready for export!"))));

        mockMvc.perform(get("/c/{caseToken}/validate", caseToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Format checks passed. Evidence checklist still incomplete.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation only confirms format, size, and rule checks.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload Missing Evidence")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Perfect Format!"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Proceed to Export"))));
    }

    @Test
    void validatePageShowsReviewExportOnlyWhenRequiredEvidenceIsComplete() throws Exception {
        CaseRef caseRef = createStripeProductNotReceivedCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(new MockMultipartFile("file", "receipt.pdf", "application/pdf", simplePdf()))
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(new MockMultipartFile("file", "customer.pdf", "application/pdf", simplePdf()))
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "CUSTOMER_DETAILS"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(new MockMultipartFile("file", "communication.pdf", "application/pdf", simplePdf()))
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "CUSTOMER_COMMUNICATION"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(new MockMultipartFile("file", "delivery.pdf", "application/pdf", simplePdf()))
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "FULFILLMENT_DELIVERY"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cases/{caseId}/validate-stored", caseId)
                        .header("X-Case-Token", caseToken)
                        .contentType("application/json")
                        .content("{\"earlySubmit\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(get("/c/{caseToken}/validate", caseToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Format checks passed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("required evidence checklist is complete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review Export")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Evidence checklist still incomplete"))));
    }

    @Test
    void newCasePagePrefillsFromGuideAttribution() throws Exception {
        mockMvc.perform(get("/new")
                        .param("src", "guide")
                        .param("platform", "stripe")
                        .param("guide", "fraudulent"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"sourcePlatform\" value=\"stripe\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-prefill-code=\"FRAUDULENT\"")));
    }

    @Test
    void createCaseRedirectFromGuideIncludesCreatedFlag() throws Exception {
        mockMvc.perform(post("/new")
                        .param("platform", "STRIPE")
                        .param("productScope", "STRIPE_DISPUTE")
                        .param("reasonCode", "fraudulent")
                        .param("src", "guide")
                        .param("guide", "fraudulent")
                        .param("sourcePlatform", "stripe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("src=guide&platform=stripe&guide=fraudulent&created=1")));
    }

    @Test
    void casePageHasNoIndexMetaAndHeader() throws Exception {
        CaseRef caseRef = createStripeCase();

        mockMvc.perform(get("/c/{caseToken}", caseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("meta name=\"robots\" content=\"noindex,nofollow,noarchive\"")));
    }

    @Test
    void guidesPlatformAndDetailPagesRenderSeoContent() throws Exception {
        mockMvc.perform(get("/guides/stripe"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stripe Error Fixes &amp; Evidence Checklists")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload/Error Fixes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start Free Validation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("See checklist &amp; steps")));

        mockMvc.perform(get("/guides/stripe/fraudulent"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stripe Fraudulent Evidence Checklist for Upload Recovery")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stop Trial-And-Error Uploads")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start Free Validation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Official platform sources")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"@type\": \"FAQPage\"")));

        mockMvc.perform(get("/guides/stripe/evidence-file-size-limit-4-5mb"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stripe \"evidence file size limit 4.5MB\" upload fix")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What you get when you start here")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Search Phrases This Page Solves"))));
    }

    @Test
    void guidesIndexPromotesValidationFlow() throws Exception {
        mockMvc.perform(get("/guides"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Get the manual checklist fast")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Find My Fix Flow")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start Free Validation")));
    }

    @Test
    void guidesRouterRoutesErrorQueriesToNewCaseFlow() throws Exception {
        mockMvc.perform(get("/guides/router")
                        .param("q", "shopify pdf a format required error")
                        .param("platform", "shopify"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/new?src=guide&platform=shopify&guide=pdf-a-format-required-error")));
    }

    @Test
    void guidesRouterNoMatchWithPlatformRedirectsToDirectFixNewCase() throws Exception {
        mockMvc.perform(get("/guides/router")
                        .param("q", "shopify zqxv nonce checksum")
                        .param("platform", "shopify"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/new?src=guide_router_nomatch&guide=router_nomatch&platform=shopify")));
    }

    @Test
    void newCasePageShowsRouterNoMatchBannerAndPreservesQuery() throws Exception {
        mockMvc.perform(get("/new")
                        .param("src", "guide_router_nomatch")
                        .param("platform", "shopify")
                        .param("guide", "router_nomatch")
                        .param("q", "shopify weird embedded object parser failure"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No exact guide match yet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"query\" value=\"shopify weird embedded object parser failure\"")));
    }

    @Test
    void createCaseRedirectFromRouterNoMatchIncludesCreatedFlag() throws Exception {
        mockMvc.perform(post("/new")
                        .param("platform", "SHOPIFY")
                        .param("productScope", "SHOPIFY_PAYMENTS_CHARGEBACK")
                        .param("src", "guide_router_nomatch")
                        .param("guide", "router_nomatch")
                        .param("sourcePlatform", "shopify")
                        .param("query", "shopify weird embedded object parser failure"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("src=guide_router_nomatch")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("guide=router_nomatch")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("platform=shopify")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("created=1")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("q=shopify+weird+embedded+object+parser+failure")));
    }

    @Test
    void guidesRouterRoutesReasonQueriesToGuidePage() throws Exception {
        mockMvc.perform(get("/guides/router")
                        .param("q", "stripe fraudulent evidence checklist")
                        .param("platform", "stripe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/guides/stripe/fraudulent"));
    }

    @Test
    void shopifyPaymentsPagesShowPdfaTrustNoteButStripeDoesNot() throws Exception {
        CaseRef shopifyCaseRef = createShopifyPaymentsCase();
        CaseRef stripeCaseRef = createStripeCase();

        mockMvc.perform(get("/c/{caseToken}/upload", shopifyCaseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shopify Payments PDF/A note")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PDF/A status is checked automatically")));

        mockMvc.perform(get("/c/{caseToken}/validate", shopifyCaseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shopify Payments PDF/A note")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("If Shopify still says \"PDF/A required\"")));

        mockMvc.perform(get("/c/{caseToken}/upload", stripeCaseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Shopify Payments PDF/A note"))));
    }

    @Test
    void sitemapIncludesErrorAndReasonGuideUrls() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/guides/stripe/fraudulent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/guides/stripe/evidence-file-size-limit-4-5mb")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/guides/shopify/pdf-a-format-required-error")));
    }

    @Test
    void openCaseSupportsRawTokenAndFullCaseUrl() throws Exception {
        CaseRef caseRef = createStripeCase();

        mockMvc.perform(post("/open-case")
                        .param("caseTokenOrUrl", caseRef.caseToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/c/" + caseRef.caseToken()));

        mockMvc.perform(post("/open-case")
                        .param("caseTokenOrUrl", "https://example.test/c/" + caseRef.caseToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/c/" + caseRef.caseToken()));
    }

    @Test
    void payEndpointIsPostOnly() throws Exception {
        CaseRef caseRef = createStripeCase();

        mockMvc.perform(get("/c/{caseToken}/pay", caseRef.caseToken()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void summaryDownloadRedirectsWhenCaseIsNotExportReady() throws Exception {
        CaseRef caseRef = createStripeCase();

        mockMvc.perform(get("/c/{caseToken}/download/summary.pdf", caseRef.caseToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/c/" + caseRef.caseToken() + "/export?error=")));
    }

    @Test
    void explanationPageAndDownloadRenderDraftText() throws Exception {
        CaseRef caseRef = createStripeCase();
        UUID caseId = caseRef.caseId();
        String caseToken = caseRef.caseToken();
        String publicCaseRef = PublicCaseReference.from(disputeCaseRepository.findById(caseId).orElseThrow());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delivery.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseId)
                        .file(file)
                        .header("X-Case-Token", caseToken)
                        .param("evidenceType", "FULFILLMENT_DELIVERY"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/c/{caseToken}/explanation", caseToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dispute Explanation Draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Explanation Draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dispute_Explanation_Draft_" + publicCaseRef + "_edited.txt")));

        mockMvc.perform(get("/c/{caseToken}/download/explanation.txt", caseToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(publicCaseRef)))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(caseToken))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Checklist Gaps and Actions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disclaimer: This draft is a submission-writing aid only.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(publicCaseRef)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(caseToken))));
    }

    @Test
    void accessKeyDownloadDisablesCaching() throws Exception {
        CaseRef caseRef = createStripeCase();

        mockMvc.perform(get("/c/{caseToken}/access-key.txt", caseRef.caseToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    private CaseRef createStripeCase() throws Exception {
        return createCase("""
                {
                  "platform": "STRIPE",
                  "productScope": "STRIPE_DISPUTE",
                  "reasonCode": "fraudulent",
                  "cardNetwork": "VISA"
                }
                """);
    }

    private CaseRef createStripeProductNotReceivedCase() throws Exception {
        return createCase("""
                {
                  "platform": "STRIPE",
                  "productScope": "STRIPE_DISPUTE",
                  "reasonCode": "product_not_received",
                  "cardNetwork": "VISA"
                }
                """);
    }

    private CaseRef createStripeMastercardCase() throws Exception {
        return createCase("""
                {
                  "platform": "STRIPE",
                  "productScope": "STRIPE_DISPUTE",
                  "reasonCode": "product_not_received",
                  "cardNetwork": "MASTERCARD"
                }
                """);
    }

    private CaseRef createShopifyPaymentsCase() throws Exception {
        return createCase("""
                {
                  "platform": "SHOPIFY",
                  "productScope": "SHOPIFY_PAYMENTS_CHARGEBACK",
                  "reasonCode": "fraudulent"
                }
                """);
    }

    private CaseRef createShopifyCreditCase() throws Exception {
        return createCase("""
                {
                  "platform": "SHOPIFY",
                  "productScope": "SHOPIFY_CREDIT_DISPUTE",
                  "reasonCode": "fraudulent"
                }
                """);
    }

    private CaseRef createCase(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .contentType("application/json")
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new CaseRef(
                extractUuid(response, "caseId"),
                extractText(response, "caseToken")
        );
    }

    private UUID extractUuid(String content, String fieldName) {
        return UUID.fromString(extractText(content, fieldName));
    }

    private String extractText(String content, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = content.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException(fieldName + " not found in response: " + content);
        }

        int from = start + marker.length();
        int end = content.indexOf('"', from);
        return content.substring(from, end);
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

    private byte[] portfolioPdf() {
        return """
                %PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R /Collection << /Type /Collection >> >>
                endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] >>
                endobj
                trailer << /Root 1 0 R >>
                %%EOF
                """.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] oversizedPdfByPadding() throws Exception {
        byte[] base = simplePdf();
        int target = 5_300_000;
        if (base.length >= target) {
            return base;
        }
        byte[] padded = new byte[target];
        System.arraycopy(base, 0, padded, 0, base.length);
        for (int i = base.length; i < padded.length; i++) {
            padded[i] = '0';
        }
        return padded;
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

    private byte[] oversizedPng() throws Exception {
        BufferedImage image = new BufferedImage(1300, 1300, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42L);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = (random.nextInt(256) << 16) | (random.nextInt(256) << 8) | random.nextInt(256);
                image.setRGB(x, y, rgb);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] data = out.toByteArray();
        if (data.length <= 2_097_152) {
            throw new IllegalStateException("generated image is not oversized enough for test");
        }
        return data;
    }

    private byte[] oversizedButCreditSafePng() throws Exception {
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(84L);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = (random.nextInt(256) << 16) | (random.nextInt(256) << 8) | random.nextInt(256);
                image.setRGB(x, y, rgb);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] data = out.toByteArray();
        if (data.length <= 2_097_152 || data.length > 4_718_592) {
            throw new IllegalStateException("generated credit test image must be >2MB and <=4.5MB");
        }
        return data;
    }

    private record CaseRef(UUID caseId, String caseToken) {
    }
}
