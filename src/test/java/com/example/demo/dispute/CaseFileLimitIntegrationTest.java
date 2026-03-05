package com.example.demo.dispute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.case.max-files=2")
@AutoConfigureMockMvc
class CaseFileLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadFailsWhenCaseFileLimitIsReached() throws Exception {
        CaseRef caseRef = createStripeCase();

        MockMultipartFile first = new MockMultipartFile(
                "file",
                "receipt-1.pdf",
                "application/pdf",
                simplePdf()
        );
        MockMultipartFile second = new MockMultipartFile(
                "file",
                "receipt-2.pdf",
                "application/pdf",
                simplePdf()
        );
        MockMultipartFile third = new MockMultipartFile(
                "file",
                "receipt-3.pdf",
                "application/pdf",
                simplePdf()
        );

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseRef.caseId())
                        .file(first)
                        .header("X-Case-Token", caseRef.caseToken())
                        .param("evidenceType", "ORDER_RECEIPT"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseRef.caseId())
                        .file(second)
                        .header("X-Case-Token", caseRef.caseToken())
                        .param("evidenceType", "CUSTOMER_DETAILS"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/cases/{caseId}/files", caseRef.caseId())
                        .file(third)
                        .header("X-Case-Token", caseRef.caseToken())
                        .param("evidenceType", "POLICIES"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("file limit reached for this case (2). Delete unused files before uploading more."));
    }

    private CaseRef createStripeCase() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .contentType("application/json")
                        .content("""
                                {
                                  "platform": "STRIPE",
                                  "productScope": "STRIPE_DISPUTE",
                                  "reasonCode": "fraudulent",
                                  "cardNetwork": "VISA"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new CaseRef(
                UUID.fromString(extractText(response, "caseId")),
                extractText(response, "caseToken")
        );
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

    private byte[] simplePdf() {
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

    private record CaseRef(UUID caseId, String caseToken) {
    }
}

