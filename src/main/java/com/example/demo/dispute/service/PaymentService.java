package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.PaymentStatus;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.PaymentEntity;
import com.example.demo.dispute.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@Transactional
public class PaymentService {

    private static final String STRIPE_PROVIDER = "stripe";

    private final CaseService caseService;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final StripeWebhookVerifier stripeWebhookVerifier;
    private final ValidationFreshnessService validationFreshnessService;
    private final CaseReportService caseReportService;
    private final ReadinessService readinessService;
    private final PolicyCatalogService policyCatalogService;
    private final ObjectMapper objectMapper;
    private final RestClient stripeRestClient;

    private final String stripeSecretKey;
    private final String stripeWebhookSecret;
    private final long amountCents;
    private final String currency;
    private final String successUrlTemplate;
    private final String cancelUrlTemplate;

    public PaymentService(
            CaseService caseService,
            PaymentRepository paymentRepository,
            AuditLogService auditLogService,
            StripeWebhookVerifier stripeWebhookVerifier,
            ValidationFreshnessService validationFreshnessService,
            CaseReportService caseReportService,
            ReadinessService readinessService,
            PolicyCatalogService policyCatalogService,
            @Value("${app.billing.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.billing.stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${app.billing.amount-cents:1900}") long amountCents,
            @Value("${app.billing.currency:usd}") String currency,
            @Value("${app.billing.success-url-template:http://localhost:8080/c/{caseToken}/export?payment=success}") String successUrlTemplate,
            @Value("${app.billing.cancel-url-template:http://localhost:8080/c/{caseToken}/export?payment=cancelled}") String cancelUrlTemplate
    ) {
        this.caseService = caseService;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
        this.stripeWebhookVerifier = stripeWebhookVerifier;
        this.validationFreshnessService = validationFreshnessService;
        this.caseReportService = caseReportService;
        this.readinessService = readinessService;
        this.policyCatalogService = policyCatalogService;
        this.objectMapper = new ObjectMapper();
        this.stripeSecretKey = stripeSecretKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.amountCents = amountCents;
        this.currency = currency;
        this.successUrlTemplate = successUrlTemplate;
        this.cancelUrlTemplate = cancelUrlTemplate;
        this.stripeRestClient = RestClient.builder().baseUrl("https://api.stripe.com").build();
    }

    public CheckoutStartResult startCheckout(String caseToken) {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        if (!supportsCheckout(disputeCase.getState())) {
            throw new IllegalArgumentException("case must be export-ready (READY/PAID/DOWNLOADED) before payment");
        }

        if (isPaid(disputeCase.getId())) {
            return new CheckoutStartResult(null, true);
        }
        if (!validationFreshnessService.hasFreshPassedValidation(disputeCase.getId())) {
            throw new IllegalArgumentException("Validation is stale or failed. Run validation again before payment.");
        }
        var missingRequiredEvidenceTypes = readinessService
                .summarize(caseReportService.getReport(disputeCase.getId()))
                .missingRequiredEvidenceTypes();
        if (!missingRequiredEvidenceTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required evidence before payment: " + String.join(", ", missingRequiredEvidenceTypes)
            );
        }
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork()
        );
        String requiredEvidenceSnapshot = serializeRequiredEvidence(policy.requiredEvidenceTypes());

        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("stripe secret key is not configured");
        }

        StripeCheckoutSession session = createStripeCheckoutSession(disputeCase);
        Optional<PaymentEntity> existing = paymentRepository.findByCheckoutSessionId(session.id());
        if (existing.isPresent()) {
            PaymentEntity reused = existing.get();
            if (!reused.getDisputeCase().getId().equals(disputeCase.getId())) {
                throw new IllegalStateException("checkout session is linked to a different case");
            }
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "CHECKOUT_SESSION_REUSED",
                    "provider=stripe,sessionId=" + session.id()
            );
            backfillSnapshotIfMissing(reused, policy.policyVersion(), requiredEvidenceSnapshot);
            return new CheckoutStartResult(session.url(), false);
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setDisputeCase(disputeCase);
        payment.setProvider(STRIPE_PROVIDER);
        payment.setCheckoutSessionId(session.id());
        payment.setPaymentIntentId(null);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setAmountCents(amountCents);
        payment.setCurrency(currency);
        payment.setCustomerEmail(null);
        payment.setPolicyVersion(policy.policyVersion());
        payment.setRequiredEvidenceSnapshot(requiredEvidenceSnapshot);
        paymentRepository.save(payment);

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "CHECKOUT_SESSION_CREATED",
                "provider=stripe,sessionId=" + session.id()
        );

        return new CheckoutStartResult(session.url(), false);
    }

    @Transactional(readOnly = true)
    public boolean isPaid(String caseToken) {
        return isPaid(caseService.getCaseByToken(caseToken).getId());
    }

    @Transactional(readOnly = true)
    public boolean isCheckoutConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    @Transactional(readOnly = true)
    public boolean isPaid(UUID caseId) {
        return paymentRepository.existsByDisputeCaseIdAndStatus(caseId, PaymentStatus.PAID);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentEntity> latestPayment(UUID caseId) {
        return paymentRepository.findFirstByDisputeCaseIdOrderByCreatedAtDesc(caseId);
    }

    @Transactional(readOnly = true)
    public List<String> missingRequiredEvidenceForPaidExport(UUID caseId) {
        Optional<PaymentEntity> paidPayment = paymentRepository
                .findFirstByDisputeCaseIdAndStatusOrderByCreatedAtDesc(caseId, PaymentStatus.PAID);
        if (paidPayment.isEmpty()) {
            return List.of();
        }

        List<String> requiredSnapshot = deserializeRequiredEvidence(paidPayment.get().getRequiredEvidenceSnapshot());
        if (requiredSnapshot.isEmpty()) {
            // Legacy paid records created before snapshot support: do not retroactively lock exports.
            return List.of();
        }

        EnumSet<EvidenceType> presentTypes = EnumSet.noneOf(EvidenceType.class);
        caseReportService.getReport(caseId).files().forEach(file -> presentTypes.add(file.evidenceType()));

        return requiredSnapshot.stream()
                .filter(name -> !isPresent(name, presentTypes))
                .toList();
    }

    public void processStripeWebhook(String payload, String signatureHeader) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new IllegalStateException("stripe webhook secret is not configured");
        }
        if (!stripeWebhookVerifier.isValid(payload, signatureHeader, stripeWebhookSecret)) {
            throw new IllegalArgumentException("invalid stripe signature");
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid webhook payload");
        }

        String eventType = event.path("type").asText("");
        if (!"checkout.session.completed".equals(eventType)) {
            return;
        }

        JsonNode sessionNode = event.path("data").path("object");
        String sessionId = sessionNode.path("id").asText(null);
        String paymentIntentId = sessionNode.path("payment_intent").asText(null);
        String email = sessionNode.path("customer_details").path("email").asText(null);

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        paymentRepository.findByCheckoutSessionId(sessionId).ifPresent(payment -> markPaid(payment, paymentIntentId, email));
    }

    private void markPaid(PaymentEntity payment, String paymentIntentId, String email) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentIntentId(paymentIntentId);
        payment.setCustomerEmail(email);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        DisputeCase disputeCase = payment.getDisputeCase();
        if (disputeCase.getState() != CaseState.PAID && disputeCase.getState() != CaseState.DOWNLOADED) {
            caseService.transitionState(disputeCase, CaseState.PAID);
        }

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "PAYMENT_COMPLETED",
                "provider=stripe,sessionId=" + payment.getCheckoutSessionId()
        );
    }

    private StripeCheckoutSession createStripeCheckoutSession(DisputeCase disputeCase) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", interpolateCaseToken(successUrlTemplate, disputeCase.getCaseToken()));
        form.add("cancel_url", interpolateCaseToken(cancelUrlTemplate, disputeCase.getCaseToken()));
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amountCents));
        form.add("line_items[0][price_data][product_data][name]", "Chargeback Submission Pack");
        form.add("metadata[case_id]", disputeCase.getId().toString());

        Map<String, Object> response;
        try {
            response = stripeRestClient.post()
                    .uri("/v1/checkout/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + stripeSecretKey)
                    .header("Idempotency-Key", "checkout-case-" + disputeCase.getId())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create stripe checkout session: " + ex.getMessage(), ex);
        }

        if (response == null) {
            throw new IllegalStateException("stripe returned empty response");
        }

        String sessionId = valueAsString(response.get("id"));
        String url = valueAsString(response.get("url"));
        if (sessionId == null || url == null) {
            throw new IllegalStateException("stripe response missing id/url");
        }

        return new StripeCheckoutSession(sessionId, url);
    }

    private String interpolateCaseToken(String template, String caseToken) {
        return template.replace("{caseToken}", caseToken);
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

    private void backfillSnapshotIfMissing(
            PaymentEntity payment,
            String policyVersion,
            String requiredEvidenceSnapshot
    ) {
        boolean needsUpdate = false;
        if (payment.getPolicyVersion() == null || payment.getPolicyVersion().isBlank()) {
            payment.setPolicyVersion(policyVersion);
            needsUpdate = true;
        }
        if (payment.getRequiredEvidenceSnapshot() == null || payment.getRequiredEvidenceSnapshot().isBlank()) {
            payment.setRequiredEvidenceSnapshot(requiredEvidenceSnapshot);
            needsUpdate = true;
        }
        if (needsUpdate) {
            paymentRepository.save(payment);
        }
    }

    private String serializeRequiredEvidence(List<EvidenceType> requiredTypes) {
        if (requiredTypes == null || requiredTypes.isEmpty()) {
            return "";
        }
        List<String> values = requiredTypes.stream()
                .map(Enum::name)
                .distinct()
                .toList();
        return String.join(",", values);
    }

    private List<String> deserializeRequiredEvidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean isPresent(String requiredTypeName, EnumSet<EvidenceType> presentTypes) {
        try {
            return presentTypes.contains(EvidenceType.valueOf(requiredTypeName));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean supportsCheckout(CaseState state) {
        return state == CaseState.READY || state == CaseState.PAID || state == CaseState.DOWNLOADED;
    }

    private record StripeCheckoutSession(String id, String url) {
    }
}
