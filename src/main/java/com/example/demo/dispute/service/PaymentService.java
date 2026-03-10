package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.PaymentStatus;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.PaymentEntity;
import com.example.demo.dispute.persistence.PaymentRepository;
import com.example.demo.dispute.persistence.WebhookEventReceiptEntity;
import com.example.demo.dispute.persistence.WebhookEventReceiptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.time.Instant;
import java.util.Locale;
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
    private static final String LEMON_PROVIDER = "lemonsqueezy";
    private static final String CHECKOUT_PRODUCT_NAME = "Supported blocker beta export";

    private final CaseService caseService;
    private final PaymentRepository paymentRepository;
    private final WebhookEventReceiptRepository webhookEventReceiptRepository;
    private final AuditLogService auditLogService;
    private final BetaExportEligibilityService betaExportEligibilityService;
    private final StripeWebhookVerifier stripeWebhookVerifier;
    private final LemonWebhookVerifier lemonWebhookVerifier;
    private final ValidationFreshnessService validationFreshnessService;
    private final CaseReportService caseReportService;
    private final ReadinessService readinessService;
    private final PolicyCatalogService policyCatalogService;
    private final ObjectMapper objectMapper;
    private final RestClient stripeRestClient;
    private final RestClient lemonRestClient;

    private final String billingProvider;
    private final String stripeSecretKey;
    private final String stripeWebhookSecret;
    private final String lemonApiKey;
    private final String lemonWebhookSecret;
    private final String lemonStoreId;
    private final String lemonVariantId;
    private final long amountCents;
    private final String currency;
    private final String successUrlTemplate;
    private final String cancelUrlTemplate;

    public PaymentService(
            CaseService caseService,
            PaymentRepository paymentRepository,
            WebhookEventReceiptRepository webhookEventReceiptRepository,
            AuditLogService auditLogService,
            BetaExportEligibilityService betaExportEligibilityService,
            StripeWebhookVerifier stripeWebhookVerifier,
            LemonWebhookVerifier lemonWebhookVerifier,
            ValidationFreshnessService validationFreshnessService,
            CaseReportService caseReportService,
            ReadinessService readinessService,
            PolicyCatalogService policyCatalogService,
            @Value("${app.billing.provider:lemonsqueezy}") String billingProvider,
            @Value("${app.billing.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.billing.stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${app.billing.lemonsqueezy.api-key:}") String lemonApiKey,
            @Value("${app.billing.lemonsqueezy.webhook-secret:}") String lemonWebhookSecret,
            @Value("${app.billing.lemonsqueezy.store-id:}") String lemonStoreId,
            @Value("${app.billing.lemonsqueezy.variant-id:}") String lemonVariantId,
            @Value("${app.billing.amount-cents:900}") long amountCents,
            @Value("${app.billing.currency:usd}") String currency,
            @Value("${app.billing.success-url-template:http://localhost:8080/c/{caseToken}/export?payment=success}") String successUrlTemplate,
            @Value("${app.billing.cancel-url-template:http://localhost:8080/c/{caseToken}/export?payment=cancelled}") String cancelUrlTemplate
    ) {
        this.caseService = caseService;
        this.paymentRepository = paymentRepository;
        this.webhookEventReceiptRepository = webhookEventReceiptRepository;
        this.auditLogService = auditLogService;
        this.betaExportEligibilityService = betaExportEligibilityService;
        this.stripeWebhookVerifier = stripeWebhookVerifier;
        this.lemonWebhookVerifier = lemonWebhookVerifier;
        this.validationFreshnessService = validationFreshnessService;
        this.caseReportService = caseReportService;
        this.readinessService = readinessService;
        this.policyCatalogService = policyCatalogService;
        this.objectMapper = new ObjectMapper();
        this.billingProvider = billingProvider;
        this.stripeSecretKey = stripeSecretKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.lemonApiKey = lemonApiKey;
        this.lemonWebhookSecret = lemonWebhookSecret;
        this.lemonStoreId = lemonStoreId;
        this.lemonVariantId = lemonVariantId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.successUrlTemplate = successUrlTemplate;
        this.cancelUrlTemplate = cancelUrlTemplate;
        this.stripeRestClient = RestClient.builder().baseUrl("https://api.stripe.com").build();
        this.lemonRestClient = RestClient.builder().baseUrl("https://api.lemonsqueezy.com").build();
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
        BetaExportEligibilityService.BetaExportEligibility eligibility = betaExportEligibilityService.evaluate(disputeCase.getId());
        if (!eligibility.eligible()) {
            throw new IllegalArgumentException(eligibility.message());
        }
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork()
        );
        String requiredEvidenceSnapshot = serializeRequiredEvidence(policy.requiredEvidenceTypes());
        String provider = activeProvider();
        assertCheckoutConfigured(provider);
        if (LEMON_PROVIDER.equals(provider)) {
            return startLemonCheckout(disputeCase, policy.policyVersion(), requiredEvidenceSnapshot);
        }
        CheckoutSession session = createCheckoutSession(disputeCase, provider);
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
                    "provider=" + provider + ",sessionId=" + session.id()
            );
            backfillSnapshotIfMissing(reused, policy.policyVersion(), requiredEvidenceSnapshot);
            return new CheckoutStartResult(session.url(), false);
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setDisputeCase(disputeCase);
        payment.setProvider(provider);
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
                "provider=" + provider + ",sessionId=" + session.id()
        );

        return new CheckoutStartResult(session.url(), false);
    }

    @Transactional(readOnly = true)
    public boolean isPaid(String caseToken) {
        return isPaid(caseService.getCaseByToken(caseToken).getId());
    }

    @Transactional(readOnly = true)
    public boolean isCheckoutConfigured() {
        String provider = activeProvider();
        if (LEMON_PROVIDER.equals(provider)) {
            return lemonApiKey != null
                    && !lemonApiKey.isBlank()
                    && lemonStoreId != null
                    && !lemonStoreId.isBlank()
                    && lemonVariantId != null
                    && !lemonVariantId.isBlank();
        }
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    @Transactional(readOnly = true)
    public String checkoutProviderDisplayName() {
        return LEMON_PROVIDER.equals(activeProvider()) ? "Lemon Squeezy" : "Stripe";
    }

    @Transactional(readOnly = true)
    public String checkoutPriceDisplay() {
        BigDecimal amount = BigDecimal.valueOf(amountCents, 2);
        String numeric = amount.stripTrailingZeros().toPlainString();
        if ("usd".equalsIgnoreCase(currency)) {
            return "$" + numeric;
        }
        return currency.toUpperCase(Locale.ROOT) + " " + numeric;
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

        String eventId = event.path("id").asText(null);
        JsonNode sessionNode = event.path("data").path("object");
        String sessionId = sessionNode.path("id").asText(null);
        String paymentIntentId = sessionNode.path("payment_intent").asText(null);
        String email = sessionNode.path("customer_details").path("email").asText(null);

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Optional<PaymentEntity> payment = paymentRepository.findByCheckoutSessionIdForUpdate(sessionId);
        if (payment.isEmpty()) {
            return;
        }
        if (isDuplicateWebhookEvent(STRIPE_PROVIDER, eventType, eventId)) {
            return;
        }
        markPaid(payment.get(), paymentIntentId, email);
        recordWebhookEvent(STRIPE_PROVIDER, eventType, eventId, payment.get().getDisputeCase());
    }

    public void processLemonWebhook(String payload, String signatureHeader) {
        if (lemonWebhookSecret == null || lemonWebhookSecret.isBlank()) {
            throw new IllegalStateException("lemonsqueezy webhook secret is not configured");
        }
        if (!lemonWebhookVerifier.isValid(payload, signatureHeader, lemonWebhookSecret)) {
            throw new IllegalArgumentException("invalid lemonsqueezy signature");
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid webhook payload");
        }

        String eventName = event.path("meta").path("event_name").asText("");
        if (!"order_created".equals(eventName)) {
            return;
        }

        String caseToken = event.path("meta").path("custom_data").path("case_token").asText(null);
        if (caseToken == null || caseToken.isBlank()) {
            return;
        }
        UUID paymentId = parseUuid(event.path("meta").path("custom_data").path("payment_id").asText(null));

        DisputeCase disputeCase;
        try {
            disputeCase = caseService.getCaseByToken(caseToken);
        } catch (RuntimeException ex) {
            return;
        }

        String orderId = event.path("data").path("id").asText(null);
        String email = firstNonBlank(
                event.path("data").path("attributes").path("user_email").asText(null),
                event.path("data").path("attributes").path("customer_email").asText(null)
        );

        Optional<PaymentEntity> payment = findLemonPaymentForWebhook(disputeCase.getId(), paymentId);
        if (payment.isEmpty()) {
            return;
        }
        if (isDuplicateWebhookEvent(LEMON_PROVIDER, eventName, orderId)) {
            return;
        }
        markPaid(payment.get(), orderId, email);
        recordWebhookEvent(LEMON_PROVIDER, eventName, orderId, disputeCase);
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
                "provider=" + payment.getProvider() + ",sessionId=" + payment.getCheckoutSessionId()
        );
    }

    private String activeProvider() {
        if (billingProvider == null || billingProvider.isBlank()) {
            return STRIPE_PROVIDER;
        }
        String normalized = billingProvider.trim().toLowerCase(Locale.ROOT);
        if ("lemon".equals(normalized) || "lemonsqueezy".equals(normalized) || "lemon_squeezy".equals(normalized)) {
            return LEMON_PROVIDER;
        }
        return STRIPE_PROVIDER;
    }

    private void assertCheckoutConfigured(String provider) {
        if (LEMON_PROVIDER.equals(provider)) {
            if (lemonApiKey == null || lemonApiKey.isBlank()
                    || lemonStoreId == null || lemonStoreId.isBlank()
                    || lemonVariantId == null || lemonVariantId.isBlank()) {
                throw new IllegalStateException("lemonsqueezy checkout is not configured");
            }
            return;
        }
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("stripe secret key is not configured");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private CheckoutStartResult startLemonCheckout(
            DisputeCase disputeCase,
            String policyVersion,
            String requiredEvidenceSnapshot
    ) {
        PaymentEntity payment = new PaymentEntity();
        payment.setDisputeCase(disputeCase);
        payment.setProvider(LEMON_PROVIDER);
        payment.setCheckoutSessionId(null);
        payment.setPaymentIntentId(null);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setAmountCents(amountCents);
        payment.setCurrency(currency);
        payment.setCustomerEmail(null);
        payment.setPolicyVersion(policyVersion);
        payment.setRequiredEvidenceSnapshot(requiredEvidenceSnapshot);
        PaymentEntity savedPayment = paymentRepository.save(payment);

        CheckoutSession session = createLemonCheckoutSession(disputeCase, savedPayment.getId());
        savedPayment.setCheckoutSessionId(session.id());
        paymentRepository.save(savedPayment);

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "CHECKOUT_SESSION_CREATED",
                "provider=" + LEMON_PROVIDER + ",sessionId=" + session.id()
        );

        return new CheckoutStartResult(session.url(), false);
    }

    private CheckoutSession createCheckoutSession(DisputeCase disputeCase, String provider) {
        if (LEMON_PROVIDER.equals(provider)) {
            return createLemonCheckoutSession(disputeCase, null);
        }
        return createStripeCheckoutSession(disputeCase);
    }

    private CheckoutSession createStripeCheckoutSession(DisputeCase disputeCase) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", interpolateCaseToken(successUrlTemplate, disputeCase.getCaseToken()));
        form.add("cancel_url", interpolateCaseToken(cancelUrlTemplate, disputeCase.getCaseToken()));
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amountCents));
        form.add("line_items[0][price_data][product_data][name]", CHECKOUT_PRODUCT_NAME);
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

        return new CheckoutSession(sessionId, url);
    }

    private CheckoutSession createLemonCheckoutSession(DisputeCase disputeCase, UUID paymentId) {
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "checkouts",
                        "attributes", Map.of(
                                "checkout_data", Map.of(
                                        "custom", lemonCustomData(disputeCase, paymentId)
                                ),
                                "product_options", Map.of(
                                        "redirect_url", interpolateCaseToken(successUrlTemplate, disputeCase.getCaseToken())
                                )
                        ),
                        "relationships", Map.of(
                                "store", Map.of(
                                        "data", Map.of("type", "stores", "id", lemonStoreId)
                                ),
                                "variant", Map.of(
                                        "data", Map.of("type", "variants", "id", lemonVariantId)
                                )
                        )
                )
        );

        Map<String, Object> response;
        try {
            response = lemonRestClient.post()
                    .uri("/v1/checkouts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + lemonApiKey)
                    .header(HttpHeaders.ACCEPT, "application/vnd.api+json")
                    .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create lemonsqueezy checkout: " + ex.getMessage(), ex);
        }

        if (response == null) {
            throw new IllegalStateException("lemonsqueezy returned empty response");
        }

        JsonNode node = objectMapper.valueToTree(response);
        String checkoutId = node.path("data").path("id").asText(null);
        String url = node.path("data").path("attributes").path("url").asText(null);
        if (checkoutId == null || url == null || checkoutId.isBlank() || url.isBlank()) {
            throw new IllegalStateException("lemonsqueezy response missing id/url");
        }

        return new CheckoutSession(checkoutId, url);
    }

    private Map<String, Object> lemonCustomData(DisputeCase disputeCase, UUID paymentId) {
        if (paymentId == null) {
            return Map.of("case_token", disputeCase.getCaseToken());
        }
        return Map.of(
                "case_token", disputeCase.getCaseToken(),
                "payment_id", paymentId.toString()
        );
    }

    private String interpolateCaseToken(String template, String caseToken) {
        return template.replace("{caseToken}", caseToken);
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

    private Optional<PaymentEntity> findLemonPaymentForWebhook(UUID caseId, UUID paymentId) {
        if (paymentId != null) {
            Optional<PaymentEntity> payment = paymentRepository.findByIdAndProviderForUpdate(paymentId, LEMON_PROVIDER);
            if (payment.isPresent() && payment.get().getDisputeCase().getId().equals(caseId)) {
                return payment;
            }
        }
        return paymentRepository.findLockedByDisputeCaseIdAndProviderOrderByCreatedAtDesc(caseId, LEMON_PROVIDER)
                .stream()
                .findFirst();
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

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isDuplicateWebhookEvent(String provider, String eventType, String eventId) {
        if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
            return false;
        }
        return webhookEventReceiptRepository.existsByProviderAndEventTypeAndEventId(provider, eventType, eventId);
    }

    private void recordWebhookEvent(String provider, String eventType, String eventId, DisputeCase disputeCase) {
        if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
            return;
        }
        WebhookEventReceiptEntity receipt = new WebhookEventReceiptEntity();
        receipt.setDisputeCase(disputeCase);
        receipt.setProvider(provider);
        receipt.setEventType(eventType);
        receipt.setEventId(eventId);
        webhookEventReceiptRepository.save(receipt);
    }

    private record CheckoutSession(String id, String url) {
    }
}
