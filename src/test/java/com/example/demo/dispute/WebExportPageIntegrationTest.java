package com.example.demo.dispute;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.PaymentStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.DisputeCaseRepository;
import com.example.demo.dispute.persistence.PaymentEntity;
import com.example.demo.dispute.persistence.PaymentRepository;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.ValidationHistoryService;
import com.example.demo.dispute.service.ValidationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebExportPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DisputeCaseRepository disputeCaseRepository;

    @Autowired
    private EvidenceFileService evidenceFileService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private ValidationHistoryService validationHistoryService;

    @Test
    void exportPageDoesNotClaimUnlockedFromPaidQueryWhenPaymentIsNotConfirmed() throws Exception {
        DisputeCase disputeCase = createReadyCase("web_export_unpaid");
        try {
            mockMvc.perform(get("/c/{caseToken}/export", disputeCase.getCaseToken())
                            .param("paid", "1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Waiting for webhook confirmation before unlock.")))
                    .andExpect(content().string(not(containsString("Payment confirmed. Downloads unlocked."))));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void exportPageShowsUnlockedOnlyWhenPaymentIsActuallyConfirmed() throws Exception {
        DisputeCase disputeCase = createReadyCase("web_export_paid");
        try {
            PaymentEntity payment = new PaymentEntity();
            payment.setDisputeCase(disputeCase);
            payment.setProvider("stripe");
            payment.setCheckoutSessionId("cs_test_confirmed");
            payment.setPaymentIntentId("pi_test_confirmed");
            payment.setStatus(PaymentStatus.PAID);
            payment.setAmountCents(1900L);
            payment.setCurrency("usd");
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);

            mockMvc.perform(get("/c/{caseToken}/export", disputeCase.getCaseToken())
                            .param("payment", "success"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Payment confirmed. Downloads unlocked.")))
                    .andExpect(content().string(not(containsString("Waiting for webhook confirmation before unlock."))));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void payEndpointRejectsStaleValidation() throws Exception {
        DisputeCase disputeCase = createReadyCase("stale_pay_case");
        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    com.example.demo.dispute.domain.EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt.pdf", "application/pdf", simplePdf("base"))
            );
            moveCaseToReady(disputeCase);

            var response = validationService.validate(
                    disputeCase,
                    evidenceFileService.listAsValidationInputs(disputeCase.getId()),
                    false
            );
            validationHistoryService.record(
                    disputeCase,
                    response,
                    ValidationSource.STORED_FILES,
                    false,
                    evidenceFileService.listAsValidationInputs(disputeCase.getId())
            );

            Thread.sleep(5L);
            evidenceFileService.upload(
                    disputeCase.getId(),
                    com.example.demo.dispute.domain.EvidenceType.CUSTOMER_DETAILS,
                    new MockMultipartFile("file", "customer.pdf", "application/pdf", simplePdf("new"))
            );
            moveCaseToReady(disputeCase);

            mockMvc.perform(post("/c/{caseToken}/pay", disputeCase.getCaseToken()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("/c/" + disputeCase.getCaseToken() + "/export?error=*stale*"));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void payEndpointRejectsStaleValidationWhenCaseContextChanges() throws Exception {
        DisputeCase disputeCase = createReadyCase("stale_context_pay_case");
        try {
            evidenceFileService.upload(
                    disputeCase.getId(),
                    com.example.demo.dispute.domain.EvidenceType.ORDER_RECEIPT,
                    new MockMultipartFile("file", "receipt.pdf", "application/pdf", simplePdf("context-base"))
            );
            moveCaseToReady(disputeCase);

            var inputs = evidenceFileService.listAsValidationInputs(disputeCase.getId());
            var response = validationService.validate(disputeCase, inputs, false);
            validationHistoryService.record(disputeCase, response, ValidationSource.STORED_FILES, false, inputs);

            disputeCase.setReasonCode("changed_reason_after_validation");
            disputeCaseRepository.save(disputeCase);

            mockMvc.perform(post("/c/{caseToken}/pay", disputeCase.getCaseToken()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("/c/" + disputeCase.getCaseToken() + "/export?error=*stale*"));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    private DisputeCase createReadyCase(String reasonCode) {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                reasonCode,
                null,
                null
        ));
        return moveCaseToReady(disputeCase);
    }

    private DisputeCase moveCaseToReady(DisputeCase disputeCase) {
        caseService.transitionState(disputeCase, CaseState.UPLOADING);
        caseService.transitionState(disputeCase, CaseState.VALIDATING);
        return caseService.transitionState(disputeCase, CaseState.READY);
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
        return content.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}


