package com.example.demo.dispute.web;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixJobStatus;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationFreshness;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.FixJobRepository;
import com.example.demo.dispute.service.AutoFixService;
import com.example.demo.dispute.service.CaseReportService;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.CheckoutStartResult;
import com.example.demo.dispute.service.DisputeExplanationService;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.ExportMetricsService;
import com.example.demo.dispute.service.PaymentService;
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.PublicCaseReference;
import com.example.demo.dispute.service.ReasonCodeChecklistService;
import com.example.demo.dispute.service.ReadinessService;
import com.example.demo.dispute.service.RetentionPolicyService;
import com.example.demo.dispute.service.SubmissionExportService;
import com.example.demo.dispute.service.TailTrimSuggestion;
import com.example.demo.dispute.service.TailTrimSuggestionService;
import com.example.demo.dispute.service.ValidationFreshnessService;
import com.example.demo.dispute.service.ValidationHistoryService;
import com.example.demo.dispute.service.ValidationService;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class WebCaseController {

    private static final long MB = 1024L * 1024L;
    private static final long STRIPE_TOTAL_SIZE_LIMIT_BYTES = (long) (4.5d * MB);
    private static final long SHOPIFY_PAYMENTS_TOTAL_SIZE_LIMIT_BYTES = 4L * MB;
    private static final long SHOPIFY_CREDIT_TOTAL_SIZE_LIMIT_BYTES = (long) (4.5d * MB);

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
            .withZone(ZoneId.systemDefault());
    private static final Pattern CASE_TOKEN_PATTERN = Pattern.compile("(case_[A-Za-z0-9]+)");
    private static final Pattern ATTRIBUTION_VALUE_PATTERN = Pattern.compile("^[a-z0-9_-]{1,80}$");
    private static final String SOURCE_GUIDE = "guide";
    private static final String SOURCE_GUIDE_ROUTER_NOMATCH = "guide_router_nomatch";

    private final CaseService caseService;
    private final CaseReportService caseReportService;
    private final EvidenceFileService evidenceFileService;
    private final ValidationService validationService;
    private final ValidationHistoryService validationHistoryService;
    private final AutoFixService autoFixService;
    private final SubmissionExportService submissionExportService;
    private final ExportMetricsService exportMetricsService;
    private final PaymentService paymentService;
    private final TailTrimSuggestionService tailTrimSuggestionService;
    private final ValidationFreshnessService validationFreshnessService;
    private final ReadinessService readinessService;
    private final FixJobRepository fixJobRepository;
    private final PolicyCatalogService policyCatalogService;
    private final ReasonCodeChecklistService reasonCodeChecklistService;
    private final DisputeExplanationService disputeExplanationService;
    private final RetentionPolicyService retentionPolicyService;
    private final int caseMaxFiles;
    private final String publicBaseUrl;

    public WebCaseController(
            CaseService caseService,
            CaseReportService caseReportService,
            EvidenceFileService evidenceFileService,
            ValidationService validationService,
            ValidationHistoryService validationHistoryService,
            AutoFixService autoFixService,
            SubmissionExportService submissionExportService,
            ExportMetricsService exportMetricsService,
            PaymentService paymentService,
            TailTrimSuggestionService tailTrimSuggestionService,
            ValidationFreshnessService validationFreshnessService,
            ReadinessService readinessService,
            FixJobRepository fixJobRepository,
            PolicyCatalogService policyCatalogService,
            ReasonCodeChecklistService reasonCodeChecklistService,
            DisputeExplanationService disputeExplanationService,
            RetentionPolicyService retentionPolicyService,
            @Value("${app.case.max-files:100}") int caseMaxFiles,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.caseService = caseService;
        this.caseReportService = caseReportService;
        this.evidenceFileService = evidenceFileService;
        this.validationService = validationService;
        this.validationHistoryService = validationHistoryService;
        this.autoFixService = autoFixService;
        this.submissionExportService = submissionExportService;
        this.exportMetricsService = exportMetricsService;
        this.paymentService = paymentService;
        this.tailTrimSuggestionService = tailTrimSuggestionService;
        this.validationFreshnessService = validationFreshnessService;
        this.readinessService = readinessService;
        this.fixJobRepository = fixJobRepository;
        this.policyCatalogService = policyCatalogService;
        this.reasonCodeChecklistService = reasonCodeChecklistService;
        this.disputeExplanationService = disputeExplanationService;
        this.retentionPolicyService = retentionPolicyService;
        this.caseMaxFiles = caseMaxFiles;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @GetMapping("/")
    public String index(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        model.addAttribute("checkoutPriceDisplay", paymentService.checkoutPriceDisplay());
        return "index";
    }

    @PostMapping("/open-case")
    public String openCase(@RequestParam("caseTokenOrUrl") String caseTokenOrUrl) {
        try {
            String token = normalizeCaseToken(caseTokenOrUrl);
            caseService.getCaseByToken(token);
            return "redirect:/c/" + token;
        } catch (RuntimeException ex) {
            return "redirect:/?error=" + encode("Case not found. Check the token or URL and try again.");
        }
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/new")
    public String newCase(
            Model model,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "src", required = false) String source,
            @RequestParam(value = "guide", required = false) String guide,
            @RequestParam(value = "platform", required = false) String attributionPlatform,
            @RequestParam(value = "q", required = false) String routerQuery
    ) {
        String normalizedSource = normalizeAttributionSource(source);
        String normalizedGuide = normalizeAttributionValue(guide);
        String normalizedPlatform = normalizeAttributionValue(attributionPlatform);
        Platform prefillPlatform = resolveAttributionPlatform(normalizedSource, normalizedPlatform);
        String prefillReasonCode = resolvePrefillReasonCode(normalizedSource, prefillPlatform, normalizedGuide);
        String normalizedRouterQuery = normalizeRouterQuery(routerQuery);

        model.addAttribute("platforms", Platform.values());
        model.addAttribute("productScopes", ProductScope.values());
        model.addAttribute("cardNetworks", CardNetwork.values());
        model.addAttribute("stripeReasonOptions", reasonCodeChecklistService.listReasonOptions(Platform.STRIPE));
        model.addAttribute("shopifyReasonOptions", reasonCodeChecklistService.listReasonOptions(Platform.SHOPIFY));
        model.addAttribute("error", error);
        model.addAttribute("attributionSource", normalizedSource);
        model.addAttribute("attributionGuide", normalizedGuide);
        model.addAttribute("attributionPlatform", normalizedPlatform);
        model.addAttribute("prefillPlatform", prefillPlatform != null ? prefillPlatform.name() : null);
        model.addAttribute("prefillReasonCode", prefillReasonCode);
        model.addAttribute("routerQuery", normalizedRouterQuery);
        model.addAttribute("retentionDays", retentionPolicyService.retentionDays());
        model.addAttribute("retentionDueDateBufferDays", retentionPolicyService.dueDateBufferDays());
        model.addAttribute("checkoutPriceDisplay", paymentService.checkoutPriceDisplay());
        return "newCase";
    }

    @PostMapping("/new")
    public String createCase(
            @RequestParam("platform") Platform platform,
            @RequestParam("productScope") ProductScope productScope,
            @RequestParam(value = "reasonCode", required = false) String reasonCode,
            @RequestParam(value = "dueAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAt,
            @RequestParam(value = "cardNetwork", required = false) String cardNetwork,
            @RequestParam(value = "src", required = false) String source,
            @RequestParam(value = "guide", required = false) String guide,
            @RequestParam(value = "sourcePlatform", required = false) String sourcePlatform,
            @RequestParam(value = "query", required = false) String query
    ) {
        try {
            CardNetwork selectedNetwork = null;
            if (cardNetwork != null && !cardNetwork.isBlank()) {
                selectedNetwork = CardNetwork.valueOf(cardNetwork);
            }

            DisputeCase disputeCase = caseService.createCase(
                    new CreateCaseRequest(platform, productScope, reasonCode, dueAt, selectedNetwork)
            );
            return "redirect:/c/" + disputeCase.getCaseToken() + buildGuideAttributionQuery(source, sourcePlatform, guide, query);
        } catch (RuntimeException ex) {
            return "redirect:/new?error=" + encode(ex.getMessage());
        }
    }

    @GetMapping("/c/{caseToken}")
    public String dashboard(
            @PathVariable String caseToken,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        populateCaseModel(caseToken, model);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "caseDashboard";
    }

    @GetMapping("/c/{caseToken}/upload")
    public String uploadPage(
            @PathVariable String caseToken,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        populateCaseModel(caseToken, model);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "caseUpload";
    }

    @PostMapping("/c/{caseToken}/upload")
    public String upload(
            @PathVariable String caseToken,
            @RequestParam("evidenceType") EvidenceType evidenceType,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            evidenceFileService.upload(disputeCase.getId(), evidenceType, file);
            return "redirect:/c/" + caseToken + "/upload?message=" + encode("File uploaded.");
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/upload?error=" + encode(ex.getMessage());
        }
    }

    @GetMapping("/c/{caseToken}/validate")
    public String validationResult(
            @PathVariable String caseToken,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        populateCaseModel(caseToken, model);
        model.addAttribute("tailTrimSuggestion", resolveTailTrimSuggestion(model));
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "caseValidate";
    }

    @PostMapping("/c/{caseToken}/validate")
    public String validateStored(@PathVariable String caseToken) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            ValidateCaseResponse response = validateStoredFilesAndTransition(disputeCase, ValidationSource.STORED_FILES);
            String message = response.passed() ? "Validation passed." : "Validation completed with issues.";
            return "redirect:/c/" + caseToken + "/validate?message=" + encode(message);
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/validate?error=" + encode(ex.getMessage());
        }
    }

    @PostMapping("/c/{caseToken}/trim-tail")
    public String trimTailPages(
            @PathVariable String caseToken,
            @RequestParam("fileId") UUID fileId,
            @RequestParam("trimStartPage") int trimStartPage,
            @RequestParam("trimEndPage") int trimEndPage
    ) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            evidenceFileService.trimPdfPages(disputeCase.getId(), fileId, trimStartPage, trimEndPage);
            ValidateCaseResponse response = validateStoredFilesAndTransition(disputeCase, ValidationSource.STORED_FILES);
            String message = response.passed()
                    ? "Approved trim applied and validation passed."
                    : "Approved trim applied. Validation completed with issues.";
            return "redirect:/c/" + caseToken + "/validate?message=" + encode(message);
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/validate?error=" + encode(ex.getMessage());
        }
    }

    @PostMapping("/c/{caseToken}/compress-image")
    public String compressImageForSizeRescue(
            @PathVariable String caseToken,
            @RequestParam("fileId") UUID fileId,
            @RequestParam("targetBytes") long targetBytes
    ) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            evidenceFileService.compressImageForCaseSizeRescue(disputeCase.getId(), fileId, targetBytes);
            ValidateCaseResponse response = validateStoredFilesAndTransition(disputeCase, ValidationSource.STORED_FILES);
            String message = response.passed()
                    ? "Stronger image compression applied and validation passed."
                    : "Stronger image compression applied. Validation completed with issues.";
            return "redirect:/c/" + caseToken + "/validate?message=" + encode(message);
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/validate?error=" + encode(ex.getMessage());
        }
    }

    @PostMapping("/c/{caseToken}/compress-pdf")
    public String compressPdfForSizeRescue(
            @PathVariable String caseToken,
            @RequestParam("fileId") UUID fileId,
            @RequestParam("targetBytes") long targetBytes
    ) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            evidenceFileService.compressPdfForCaseSizeRescue(disputeCase.getId(), fileId, targetBytes);
            ValidateCaseResponse response = validateStoredFilesAndTransition(disputeCase, ValidationSource.STORED_FILES);
            String message = response.passed()
                    ? "Stronger PDF compression applied and validation passed."
                    : "Stronger PDF compression applied. Validation completed with issues.";
            return "redirect:/c/" + caseToken + "/validate?message=" + encode(message);
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/validate?error=" + encode(ex.getMessage());
        }
    }

    @PostMapping("/c/{caseToken}/fix")
    public String autoFix(@PathVariable String caseToken) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            var job = autoFixService.requestAutoFix(disputeCase.getId());
            if ("FAILED".equals(job.status().name())) {
                String failMessage = job.failMessage() != null && !job.failMessage().isBlank()
                        ? job.failMessage()
                        : "Auto-fix failed. Please review the files and try again.";
                return "redirect:/c/" + caseToken + "/validate?error=" + encode(failMessage);
            }
            String message = "Auto-fix status: " + job.status();
            if (job.failCode() != null) {
                message += " (" + job.failCode() + ")";
            }
            return "redirect:/c/" + caseToken + "/validate?message=" + encode(message);
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/validate?error=" + encode(ex.getMessage());
        }
    }

    @GetMapping("/c/{caseToken}/report")
    public String reportPage(@PathVariable String caseToken, Model model) {
        populateCaseModel(caseToken, model);
        return "caseReport";
    }

    @GetMapping("/c/{caseToken}/explanation")
    public String explanationPage(
            @PathVariable String caseToken,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        populateCaseModel(caseToken, model);
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        DisputeExplanationService.ExplanationDraft draft = disputeExplanationService.buildDraft(report);
        model.addAttribute("explanationDraft", draft);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "caseExplanation";
    }

    @GetMapping("/c/{caseToken}/export")
    public String exportPage(
            @PathVariable String caseToken,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "payment", required = false) String payment,
            // legacy compatibility for old success_url templates
            @RequestParam(value = "paid", required = false) String paid,
            Model model
    ) {
        populateCaseModel(caseToken, model);
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        ReadinessService.ReadinessSummary readiness = readinessService.summarize(report);
        ReasonCodeChecklistService.ReasonChecklist reasonChecklist = reasonCodeChecklistService.resolve(
                report.platform(),
                report.productScope(),
                report.reasonCode(),
                report.cardNetwork(),
                report.files().stream().map(EvidenceFileReportResponse::evidenceType).distinct().toList()
        );
        ValidationFreshness freshness = validationFreshnessService.freshness(disputeCase.getId());
        model.addAttribute("validationFreshness", freshness.name());
        model.addAttribute("validationFresh", freshness == ValidationFreshness.FRESH);
        model.addAttribute("exportMetrics", exportMetricsService.summarize(report, readiness, reasonChecklist));

        boolean isPaid = paymentService.isPaid(caseToken);
        if ("cancelled".equalsIgnoreCase(payment)) {
            model.addAttribute("message", "Payment cancelled. You can retry checkout anytime.");
        } else if ("success".equalsIgnoreCase(payment) || (paid != null && !paid.isBlank())) {
            if (isPaid) {
                model.addAttribute("message", "Payment confirmed. Downloads unlocked.");
            } else {
                model.addAttribute("message", "Payment submitted. Waiting for webhook confirmation before unlock.");
            }
        } else {
            model.addAttribute("message", message);
        }
        model.addAttribute("error", error);
        model.addAttribute("paymentConfigured", paymentService.isCheckoutConfigured());
        model.addAttribute("paymentProviderLabel", paymentService.checkoutProviderDisplayName());
        model.addAttribute("checkoutPriceDisplay", paymentService.checkoutPriceDisplay());
        return "caseExport";
    }

    @PostMapping("/c/{caseToken}/pay")
    public String pay(@PathVariable String caseToken) {
        try {
            CheckoutStartResult checkout = paymentService.startCheckout(caseToken);
            if (checkout.alreadyPaid()) {
                return "redirect:/c/" + caseToken + "/export?message=" + encode("Already paid.");
            }
            return "redirect:" + checkout.redirectUrl();
        } catch (RuntimeException ex) {
            return "redirect:/c/" + caseToken + "/export?error=" + encode(ex.getMessage());
        }
    }

    @GetMapping("/c/{caseToken}/download/submission.zip")
    public void downloadSubmissionZip(@PathVariable String caseToken, HttpServletResponse response) throws Exception {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        String publicCaseRef = PublicCaseReference.from(disputeCase);
        if (!isExportableState(disputeCase.getState())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Run validation and resolve blocked issues before export."));
            return;
        }
        if (!validationFreshnessService.hasFreshPassedValidation(disputeCase.getId())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Validation is stale. Run validation again before export."));
            return;
        }
        if (!paymentService.isPaid(disputeCase.getId())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Payment required to download."));
            return;
        }
        List<String> missingRequiredEvidenceTypes = paymentService.missingRequiredEvidenceForPaidExport(disputeCase.getId());
        if (!missingRequiredEvidenceTypes.isEmpty()) {
            response.sendRedirect(
                    "/c/" + caseToken + "/export?error=" + encode(
                            "Missing required evidence captured at payment time: " + String.join(", ", missingRequiredEvidenceTypes)
                    )
            );
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"Chargeback_Submission_Pack_" + publicCaseRef + ".zip\"");
        submissionExportService.writeSubmissionZip(caseToken, response.getOutputStream());
        markDownloaded(caseToken);
    }

    @GetMapping("/c/{caseToken}/download/summary.pdf")
    public void downloadSummaryPdf(@PathVariable String caseToken, HttpServletResponse response) throws Exception {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        String publicCaseRef = PublicCaseReference.from(disputeCase);
        if (!isExportableState(disputeCase.getState())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Run validation before downloading the summary guide."));
            return;
        }

        boolean paid = paymentService.isPaid(disputeCase.getId());
        if (!paid && !readinessService.hasMinimumCoreEvidenceCoverage(report)) {
            response.sendRedirect(
                    "/c/" + caseToken + "/export?error=" + encode(
                            "Add at least one more core required evidence type before downloading the free summary guide."
                    )
            );
            return;
        }
        response.setContentType("application/pdf");
        if (paid) {
            response.setHeader("Content-Disposition", "attachment; filename=\"Chargeback_Submission_Guide_" + publicCaseRef + ".pdf\"");
            submissionExportService.writeSummaryPdf(caseToken, response.getOutputStream(), false);
            markDownloaded(caseToken);
        } else {
            response.setHeader("Content-Disposition", "attachment; filename=\"Chargeback_Submission_Guide_FREE_" + publicCaseRef + ".pdf\"");
            submissionExportService.writeSummaryPdf(caseToken, response.getOutputStream(), true);
        }
    }

    @GetMapping("/c/{caseToken}/download/explanation.txt")
    public void downloadExplanationTxt(@PathVariable String caseToken, HttpServletResponse response) throws Exception {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        DisputeExplanationService.ExplanationDraft draft = disputeExplanationService.buildDraft(report);

        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"Dispute_Explanation_Draft_" + draft.publicCaseRef() + ".txt\""
        );
        response.getWriter().print(draft.text());
    }

    @GetMapping("/c/{caseToken}/access-key.txt")
    public void downloadAccessKey(@PathVariable String caseToken, HttpServletResponse response) throws Exception {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        String publicCaseRef = PublicCaseReference.from(disputeCase);
        Instant expiresAt = retentionPolicyService.resolveExpiry(disputeCase);
        String caseUrl = publicBaseUrl + "/c/" + disputeCase.getCaseToken();

        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"chargeback_case_" + publicCaseRef + ".txt\"");
        response.getWriter().print(
                "Chargeback Case Access Key\n"
                        + "Reference: " + publicCaseRef + "\n"
                        + "Token: " + disputeCase.getCaseToken() + "\n"
                        + "URL: " + caseUrl + "\n"
                        + "CreatedAt: " + DATE_TIME_FORMAT.format(disputeCase.getCreatedAt()) + "\n"
                        + "ExpiresAt: " + DATE_TIME_FORMAT.format(expiresAt) + "\n"
                        + "\n"
                        + "Keep this file private. Anyone with this token can access your case.\n"
        );
    }

    @PostMapping("/c/{caseToken}/rotate-token")
    public String rotateToken(@PathVariable String caseToken) {
        try {
            DisputeCase rotated = caseService.rotateCaseToken(caseToken);
            return "redirect:/c/" + rotated.getCaseToken() + "?message=" + encode("Access token rotated. Save the new link now.");
        } catch (RuntimeException ex) {
            return "redirect:/?error=" + encode(ex.getMessage());
        }
    }

    @PostMapping("/c/{caseToken}/delete")
    public String deleteCase(@PathVariable String caseToken) {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        caseService.deleteCase(disputeCase.getId());
        return "redirect:/?message=" + encode("Case deleted.");
    }

    private void populateCaseModel(String caseToken, Model model) {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        ReadinessService.ReadinessSummary readiness = readinessService.summarize(report);
        Instant expiresAt = retentionPolicyService.resolveExpiry(disputeCase);
        int uploadedFileCount = report.files().size();
        EnumSet<EvidenceType> presentEvidenceTypes = EnumSet.noneOf(EvidenceType.class);
        report.files().forEach(file -> presentEvidenceTypes.add(file.evidenceType()));
        int remainingFileSlots = Math.max(0, caseMaxFiles - uploadedFileCount);
        long uploadedSizeBytes = report.files().stream().mapToLong(EvidenceFileReportResponse::sizeBytes).sum();
        long totalSizeLimitBytes = totalSizeLimitFor(report);
        long remainingSizeBytes = Math.max(0, totalSizeLimitBytes - uploadedSizeBytes);
        int totalPagesUsed = report.files().stream().mapToInt(EvidenceFileReportResponse::pageCount).sum();
        int stripePagesRemaining = report.productScope() == ProductScope.STRIPE_DISPUTE
                ? Math.max(0, 49 - totalPagesUsed)
                : -1;
        int mastercardPagesRemaining = report.productScope() == ProductScope.STRIPE_DISPUTE
                && disputeCase.getCardNetwork() == CardNetwork.MASTERCARD
                ? Math.max(0, 19 - totalPagesUsed)
                : -1;
        ValidationFreshness freshness = validationFreshnessService.freshness(disputeCase.getId());
        ReasonCodeChecklistService.ReasonChecklist reasonChecklist = reasonCodeChecklistService.resolve(
                report.platform(),
                report.productScope(),
                report.reasonCode(),
                report.cardNetwork(),
                List.copyOf(presentEvidenceTypes)
        );

        model.addAttribute("disputeCase", disputeCase);
        model.addAttribute("report", report);
        model.addAttribute("evidenceTypes", EvidenceType.values());
        model.addAttribute("isPaid", paymentService.isPaid(disputeCase.getId()));
        model.addAttribute("latestPayment", paymentService.latestPayment(disputeCase.getId()).orElse(null));
        model.addAttribute("paymentProviderLabel", paymentService.checkoutProviderDisplayName());
        model.addAttribute("readinessScore", readiness.score());
        model.addAttribute("readinessLabel", readiness.label());
        model.addAttribute("readinessBlocked", readiness.blockedCount());
        model.addAttribute("readinessFixable", readiness.fixableCount());
        model.addAttribute("readinessWarning", readiness.warningCount());
        model.addAttribute("missingEvidenceTypes", readiness.missingEvidenceTypes());
        model.addAttribute("missingRequiredEvidenceTypes", readiness.missingRequiredEvidenceTypes());
        model.addAttribute("missingRecommendedEvidenceTypes", readiness.missingRecommendedEvidenceTypes());
        model.addAttribute("retentionDays", retentionPolicyService.retentionDays());
        model.addAttribute("retentionDueDateBufferDays", retentionPolicyService.dueDateBufferDays());
        model.addAttribute("expiresAtText", DATE_TIME_FORMAT.format(expiresAt));
        model.addAttribute("casePublicUrl", publicBaseUrl + "/c/" + report.caseToken());
        model.addAttribute("exportReady", isExportableState(report.state()));
        model.addAttribute("caseMaxFiles", caseMaxFiles);
        model.addAttribute("uploadedFileCount", uploadedFileCount);
        model.addAttribute("remainingFileSlots", remainingFileSlots);
        model.addAttribute("uploadedSizeBytes", uploadedSizeBytes);
        model.addAttribute("totalSizeLimitBytes", totalSizeLimitBytes);
        model.addAttribute("remainingSizeBytes", remainingSizeBytes);
        model.addAttribute("totalPagesUsed", totalPagesUsed);
        model.addAttribute("stripePagesRemaining", stripePagesRemaining);
        model.addAttribute("mastercardPagesRemaining", mastercardPagesRemaining);
        model.addAttribute("sizeLimitLabel", formatSizeLabel(totalSizeLimitBytes));
        model.addAttribute("validationFreshness", freshness.name());
        model.addAttribute("validationFresh", freshness == ValidationFreshness.FRESH);
        model.addAttribute("reasonChecklist", reasonChecklist);
        model.addAttribute("nextStepGuidance", resolveNextStepGuidance(disputeCase.getId(), report, readiness, reasonChecklist));
        ManualImageRescueAction manualImageRescueAction = resolveManualImageRescueAction(report);
        model.addAttribute("manualImageRescueAction", manualImageRescueAction);
        model.addAttribute(
                "manualPdfRescueAction",
                manualImageRescueAction == null ? resolveManualPdfRescueAction(disputeCase.getId(), report) : null
        );
        model.addAttribute("minimumCoreEvidenceCoverageReached", readinessService.hasMinimumCoreEvidenceCoverage(report));
        model.addAttribute("minimumCoreEvidenceReadyCount", readinessService.coreRequiredEvidenceReadyCount(report));
        model.addAttribute("minimumCoreEvidenceTargetCount", readinessService.minimumCoreEvidenceTargetCount(report));
        model.addAttribute("checkoutPriceDisplay", paymentService.checkoutPriceDisplay());
        DisplayStateSummary displayState = resolveDisplayState(report, readiness, reasonChecklist);
        model.addAttribute("displayCaseState", displayState.label());
        model.addAttribute("displayCaseStateBadgeClass", displayState.badgeClass());
    }

    private ValidateCaseResponse validateStoredFilesAndTransition(
            DisputeCase disputeCase,
            ValidationSource validationSource
    ) {
        List<com.example.demo.dispute.api.EvidenceFileInput> files = evidenceFileService.listAsValidationInputs(disputeCase.getId());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No uploaded files found.");
        }

        caseService.transitionState(disputeCase, CaseState.VALIDATING);
        ValidateCaseResponse response = validationService.validate(disputeCase, files, false);
        validationHistoryService.record(disputeCase, response, validationSource, false, files);
        caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);
        return response;
    }

    private void markDownloaded(String caseToken) {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        if (disputeCase.getState() != CaseState.DOWNLOADED) {
            caseService.transitionState(disputeCase, CaseState.DOWNLOADED);
        }
    }

    private boolean isExportableState(CaseState state) {
        return state == CaseState.READY || state == CaseState.PAID || state == CaseState.DOWNLOADED;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeCaseToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Case token is required.");
        }

        String trimmed = value.trim();
        Matcher matcher = CASE_TOKEN_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new IllegalArgumentException("Invalid case token format.");
    }

    private String normalizeAttributionSource(String source) {
        if (source == null) {
            return null;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (SOURCE_GUIDE.equals(normalized) || SOURCE_GUIDE_ROUTER_NOMATCH.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String normalizeAttributionValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!ATTRIBUTION_VALUE_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private String buildGuideAttributionQuery(String source, String sourcePlatform, String guide, String query) {
        String normalizedSource = normalizeAttributionSource(source);
        String normalizedPlatform = normalizeAttributionValue(sourcePlatform);
        String normalizedGuide = normalizeAttributionValue(guide);
        String normalizedQuery = normalizeRouterQuery(query);
        if (SOURCE_GUIDE.equals(normalizedSource)) {
            if (normalizedPlatform == null || normalizedGuide == null) {
                return "";
            }
            return "?src=guide&platform=" + encode(normalizedPlatform) + "&guide=" + encode(normalizedGuide) + "&created=1";
        }
        if (SOURCE_GUIDE_ROUTER_NOMATCH.equals(normalizedSource)) {
            StringBuilder queryString = new StringBuilder("?src=").append(SOURCE_GUIDE_ROUTER_NOMATCH)
                    .append("&created=1")
                    .append("&guide=router_nomatch");
            if (normalizedPlatform != null) {
                queryString.append("&platform=").append(encode(normalizedPlatform));
            }
            if (normalizedQuery != null) {
                queryString.append("&q=").append(encode(normalizedQuery));
            }
            return queryString.toString();
        }
        return "";
    }

    private Platform resolveAttributionPlatform(String source, String platform) {
        if ((!SOURCE_GUIDE.equals(source) && !SOURCE_GUIDE_ROUTER_NOMATCH.equals(source)) || platform == null) {
            return null;
        }
        return switch (platform) {
            case "stripe" -> Platform.STRIPE;
            case "shopify" -> Platform.SHOPIFY;
            default -> null;
        };
    }

    private String resolvePrefillReasonCode(String source, Platform platform, String guide) {
        if (!SOURCE_GUIDE.equals(source) || platform == null || guide == null) {
            return null;
        }
        String guideToken = canonicalReasonToken(guide);
        if (guideToken == null) {
            return null;
        }
        return reasonCodeChecklistService.listReasonOptions(platform).stream()
                .map(ReasonCodeChecklistService.ReasonOption::code)
                .filter(code -> canonicalReasonToken(code) != null)
                .filter(code -> canonicalReasonToken(code).equals(guideToken))
                .findFirst()
                .orElse(null);
    }

    private String canonicalReasonToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        normalized = normalized.replace('-', '_');
        normalized = normalized.replace("cancelled", "canceled");
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        return normalized;
    }

    private String normalizeRouterQuery(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private long totalSizeLimitFor(CaseReportResponse report) {
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                report.platform(),
                report.productScope(),
                report.reasonCode(),
                report.cardNetwork()
        );
        if (policy.totalSizeLimitBytes() != null && policy.totalSizeLimitBytes() > 0) {
            return policy.totalSizeLimitBytes();
        }
        return switch (report.productScope()) {
            case STRIPE_DISPUTE -> STRIPE_TOTAL_SIZE_LIMIT_BYTES;
            case SHOPIFY_PAYMENTS_CHARGEBACK -> SHOPIFY_PAYMENTS_TOTAL_SIZE_LIMIT_BYTES;
            case SHOPIFY_CREDIT_DISPUTE -> SHOPIFY_CREDIT_TOTAL_SIZE_LIMIT_BYTES;
        };
    }

    private String formatSizeLabel(long bytes) {
        return String.format("%.1f MB", bytes / (double) MB);
    }

    private DisplayStateSummary resolveDisplayState(
            CaseReportResponse report,
            ReadinessService.ReadinessSummary readiness,
            ReasonCodeChecklistService.ReasonChecklist reasonChecklist
    ) {
        if (report.state() == CaseState.PAID || report.state() == CaseState.DOWNLOADED) {
            return new DisplayStateSummary(report.state().name(), badgeClassForState(report.state()));
        }
        if (report.state() == CaseState.CASE_CREATED
                || report.state() == CaseState.UPLOADING
                || report.state() == CaseState.VALIDATING
                || report.state() == CaseState.FIXING
                || report.state() == CaseState.ARCHIVED) {
            return new DisplayStateSummary(report.state().name(), badgeClassForState(report.state()));
        }
        if (readiness.blockedCount() > 0 || readiness.fixableCount() > 0) {
            return new DisplayStateSummary(CaseState.BLOCKED.name(), badgeClassForState(CaseState.BLOCKED));
        }
        if (!reasonChecklist.missingRequiredEvidence().isEmpty()) {
            return new DisplayStateSummary("NEEDS_EVIDENCE", "cb-badge-amber");
        }
        return new DisplayStateSummary(CaseState.READY.name(), badgeClassForState(CaseState.READY));
    }

    private String badgeClassForState(CaseState state) {
        return switch (state) {
            case READY, PAID, DOWNLOADED -> "cb-badge-green";
            case BLOCKED -> "cb-badge-red";
            case UPLOADING, VALIDATING, FIXING -> "cb-badge-amber";
            case ARCHIVED -> "cb-badge-gray";
            case CASE_CREATED -> "cb-badge-blue";
        };
    }

    private NextStepGuidance resolveNextStepGuidance(
            UUID caseId,
            CaseReportResponse report,
            ReadinessService.ReadinessSummary readiness,
            ReasonCodeChecklistService.ReasonChecklist reasonChecklist
    ) {
        if (report.latestValidation() == null) {
            return null;
        }

        boolean autoFixRun = report.latestValidation().source() == ValidationSource.AUTO_FIX;
        String autoFixProgressNote = autoFixRun ? latestAutoFixProgressNote(caseId) : null;
        ValidationIssueResponse primaryIssue = pickPrimaryIssue(report.latestValidation().issues());
        if (primaryIssue != null) {
            if (!autoFixRun && supportsAutoFix(primaryIssue.fixStrategy())) {
                return new NextStepGuidance(
                        "Run auto-fix next.",
                        autoFixPrompt(primaryIssue),
                        null,
                        "Run Auto-Fix",
                        "/c/" + report.caseToken() + "/fix",
                        true,
                        null,
                        List.of()
                );
            }

            if (isTotalSizeIssue(primaryIssue.code())) {
                EvidenceFileReportResponse largestFile = report.files().stream()
                        .max((left, right) -> Long.compare(left.sizeBytes(), right.sizeBytes()))
                        .orElse(null);
                String targetFileLabel = largestFile != null
                        ? fileLabel(largestFile)
                        : "largest file";
                return new NextStepGuidance(
                        autoFixRun ? "Auto-fix helped, but total size is still too high." : "Reduce one large file next.",
                        "Best next move: replace or remove " + targetFileLabel
                                + " first. It is the largest file in the pack and is the safest place to start shrinking below "
                                + formatSizeLabel(totalSizeLimitFor(report)) + ".",
                        autoFixProgressNote,
                        largestFile != null ? "Replace " + shortenFileName(largestFile.originalName()) : "Replace A Large File",
                        "/c/" + report.caseToken() + "/upload",
                        false,
                        null,
                        sizePriorityList(report)
                );
            }

            if (isPageLimitIssue(primaryIssue.code())) {
                EvidenceFileReportResponse largestPagedFile = report.files().stream()
                        .max((left, right) -> Integer.compare(left.pageCount(), right.pageCount()))
                        .orElse(null);
                String targetFileLabel = largestPagedFile != null
                        ? fileLabel(largestPagedFile)
                        : "largest PDF";
                String trimHint = largestPagedFile != null
                        ? pageTrimPrimaryHint(report, largestPagedFile)
                        : "Replace the longest PDF with a shorter export focused only on dispute-linked pages.";
                String overageSummary = pageOverageSummary(report);
                return new NextStepGuidance(
                        autoFixRun ? "Auto-fix helped, but page count is still over the limit." : "Replace one long PDF next.",
                        "Best next move: replace " + targetFileLabel
                                + " with a shorter export so the total page count drops below the platform limit. "
                                + overageSummary + " " + trimHint,
                        autoFixProgressNote,
                        largestPagedFile != null ? "Upload Trimmed PDF" : "Replace Long PDF",
                        "/c/" + report.caseToken() + "/upload",
                        false,
                        "Manual trim playbook",
                        pageManualTrimPlaybook(report, largestPagedFile)
                );
            }

            if (primaryIssue.targetEvidenceType() != null) {
                String targetType = humanizeEvidenceType(primaryIssue.targetEvidenceType());
                EvidenceFileReportResponse targetFile = report.files().stream()
                        .filter(file -> file.evidenceType() == primaryIssue.targetEvidenceType())
                        .findFirst()
                        .orElse(null);
                String targetFileLabel = targetFile != null ? fileLabel(targetFile) : targetType + " file";
                return new NextStepGuidance(
                        autoFixRun ? "One evidence type still needs manual cleanup." : "Replace the remaining blocked evidence.",
                        "Best next move: re-export or replace " + targetFileLabel + ". " + primaryIssue.message(),
                        autoFixProgressNote,
                        targetFile != null ? "Replace " + shortenFileName(targetFile.originalName()) : "Replace " + targetType,
                        "/c/" + report.caseToken() + "/upload",
                        false,
                        null,
                        matchingEvidenceTypePriorityList(report, primaryIssue.targetEvidenceType())
                );
            }

            return new NextStepGuidance(
                    autoFixRun ? "Auto-fix finished. One manual step remains." : "Resolve the remaining blocker.",
                    primaryIssue.message(),
                    autoFixProgressNote,
                    "Review Uploads",
                    "/c/" + report.caseToken() + "/upload",
                    false,
                    null,
                    List.of()
            );
        }

        if (!reasonChecklist.missingRequiredEvidence().isEmpty()) {
            EvidenceType firstMissingType = firstMissingEvidenceType(readiness);
            String firstMissing = firstMissingType != null
                    ? guidanceLabel(firstMissingType)
                    : reasonChecklist.missingRequiredEvidence().getFirst();
            String description = autoFixRun
                    ? "Auto-fix cleared the supported file blockers. Upload " + firstMissing
                            + " next so the case can move closer to export-ready."
                    : "Validation passed on file rules, but export is still blocked until you upload "
                            + firstMissing + ".";
            return new NextStepGuidance(
                    autoFixRun ? "Auto-fix finished. Upload the next required evidence." : "Upload the next required evidence.",
                    description,
                    autoFixProgressNote,
                    "Upload " + firstMissing,
                    "/c/" + report.caseToken() + "/upload",
                    false,
                    null,
                    missingEvidencePlaybook(firstMissingType, reasonChecklist)
            );
        }

        return null;
    }

    private TailTrimSuggestion resolveTailTrimSuggestion(Model model) {
        Object disputeCaseAttr = model.asMap().get("disputeCase");
        Object reportAttr = model.asMap().get("report");
        if (!(disputeCaseAttr instanceof DisputeCase disputeCase)
                || !(reportAttr instanceof CaseReportResponse report)
                || report.latestValidation() == null
                || report.latestValidation().source() != ValidationSource.AUTO_FIX) {
            return null;
        }

        ValidationIssueResponse primaryIssue = pickPrimaryIssue(report.latestValidation().issues());
        if (primaryIssue == null || !isPageLimitIssue(primaryIssue.code())) {
            return null;
        }

        EvidenceFileReportResponse largestPagedFile = report.files().stream()
                .max((left, right) -> Integer.compare(left.pageCount(), right.pageCount()))
                .orElse(null);
        if (largestPagedFile == null || largestPagedFile.fileId() == null) {
            return null;
        }

        int trimTarget = pageTrimTargetFor(report);
        int overflowPages = trimTarget > 0 ? Math.max(0, totalPagesFor(report) - trimTarget) : 0;
        if (overflowPages <= 0) {
            return null;
        }

        return tailTrimSuggestionService.suggest(disputeCase.getId(), largestPagedFile.fileId(), overflowPages)
                .orElse(null);
    }

    private ManualImageRescueAction resolveManualImageRescueAction(CaseReportResponse report) {
        if (report.latestValidation() == null || report.latestValidation().source() != ValidationSource.AUTO_FIX) {
            return null;
        }

        ValidationIssueResponse primaryIssue = pickPrimaryIssue(report.latestValidation().issues());
        if (primaryIssue == null || !isTotalSizeIssue(primaryIssue.code())) {
            return null;
        }

        EvidenceFileReportResponse largestImageFile = report.files().stream()
                .filter(this::isImageFile)
                .max((left, right) -> Long.compare(left.sizeBytes(), right.sizeBytes()))
                .orElse(null);
        if (largestImageFile == null || largestImageFile.fileId() == null) {
            return null;
        }

        long overflowBytes = Math.max(0L, totalUploadedBytes(report) - totalSizeLimitFor(report));
        if (overflowBytes <= 0L) {
            return null;
        }

        long targetBytes = suggestedManualImageRescueTargetBytes(largestImageFile, overflowBytes);
        long estimatedSavings = Math.max(0L, largestImageFile.sizeBytes() - targetBytes);
        if (estimatedSavings < 48L * 1024L) {
            return null;
        }

        long residualOverflow = Math.max(0L, overflowBytes - estimatedSavings);
        String impactSummary = estimatedSavings >= overflowBytes
                ? "This one file can clear the current overage by itself if the stronger compression keeps the image readable."
                : "This one file removes about " + formatBytesLabel(estimatedSavings)
                        + ", which still leaves roughly " + formatBytesLabel(residualOverflow)
                        + " over the current limit.";
        return new ManualImageRescueAction(
                "Optional stronger image compression",
                largestImageFile.fileId(),
                targetBytes,
                fileLabel(largestImageFile),
                formatBytesLabel(largestImageFile.sizeBytes()),
                formatBytesLabel(targetBytes),
                formatBytesLabel(estimatedSavings),
                impactSummary,
                "Use this only when the image is already the clearest candidate to shrink. Recheck readability after the new JPEG is generated."
        );
    }

    private ManualPdfRescueAction resolveManualPdfRescueAction(UUID caseId, CaseReportResponse report) {
        if (report.latestValidation() == null || report.latestValidation().source() != ValidationSource.AUTO_FIX) {
            return null;
        }

        ValidationIssueResponse primaryIssue = pickPrimaryIssue(report.latestValidation().issues());
        if (primaryIssue == null || !isTotalSizeIssue(primaryIssue.code())) {
            return null;
        }

        EvidenceFileReportResponse largestPdfFile = report.files().stream()
                .filter(this::isPdfFile)
                .filter(file -> file.fileId() != null)
                .sorted((left, right) -> Long.compare(right.sizeBytes(), left.sizeBytes()))
                .filter(file -> evidenceFileService.isManualPdfCompressionLikelyUseful(caseId, file.fileId()))
                .findFirst()
                .orElse(null);
        if (largestPdfFile == null) {
            return null;
        }

        long overflowBytes = Math.max(0L, totalUploadedBytes(report) - totalSizeLimitFor(report));
        if (overflowBytes <= 0L) {
            return null;
        }

        long targetBytes = suggestedManualPdfRescueTargetBytes(largestPdfFile, overflowBytes);
        long estimatedSavings = Math.max(0L, largestPdfFile.sizeBytes() - targetBytes);
        if (estimatedSavings < 96L * 1024L) {
            return null;
        }

        long residualOverflow = Math.max(0L, overflowBytes - estimatedSavings);
        String impactSummary = estimatedSavings >= overflowBytes
                ? "This rebuilt PDF can clear the current overage by itself if the rendered copy stays readable."
                : "This rebuilt PDF removes about " + formatBytesLabel(estimatedSavings)
                        + ", which still leaves roughly " + formatBytesLabel(residualOverflow)
                        + " over the current limit.";
        return new ManualPdfRescueAction(
                "Optional stronger PDF compression",
                largestPdfFile.fileId(),
                targetBytes,
                fileLabel(largestPdfFile),
                formatBytesLabel(largestPdfFile.sizeBytes()),
                formatBytesLabel(targetBytes),
                formatBytesLabel(estimatedSavings),
                impactSummary,
                "Use this only for image-heavy PDFs. Text-heavy PDFs should usually be replaced or page-trimmed instead."
        );
    }

    private ValidationIssueResponse pickPrimaryIssue(List<ValidationIssueResponse> issues) {
        if (issues == null || issues.isEmpty()) {
            return null;
        }
        return issues.stream()
                .sorted((left, right) -> {
                    int severityCompare = Integer.compare(issuePriority(left), issuePriority(right));
                    if (severityCompare != 0) {
                        return severityCompare;
                    }
                    return left.code().compareToIgnoreCase(right.code());
                })
                .findFirst()
                .orElse(null);
    }

    private int issuePriority(ValidationIssueResponse issue) {
        if (issue == null || issue.severity() == null) {
            return 99;
        }
        return switch (issue.severity()) {
            case BLOCKED -> 0;
            case FIXABLE -> 1;
            case WARNING -> 2;
        };
    }

    private boolean supportsAutoFix(FixStrategy fixStrategy) {
        return fixStrategy == FixStrategy.MERGE_PER_TYPE
                || fixStrategy == FixStrategy.COMPRESS_SHOPIFY_IMAGE_IF_IMAGE
                || fixStrategy == FixStrategy.COMPRESS_STRIPE_PDF
                || fixStrategy == FixStrategy.REDUCE_TOTAL_SIZE
                || fixStrategy == FixStrategy.REDUCE_TOTAL_PAGES
                || fixStrategy == FixStrategy.CONVERT_PDF_TO_PDFA
                || fixStrategy == FixStrategy.FLATTEN_PDF_PORTFOLIO
                || fixStrategy == FixStrategy.REMOVE_EXTERNAL_LINKS_PDF;
    }

    private boolean isTotalSizeIssue(String code) {
        return "ERR_STRIPE_TOTAL_SIZE".equals(code)
                || "ERR_SHPFY_TOTAL_TOO_LARGE".equals(code)
                || "ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE".equals(code);
    }

    private boolean isPageLimitIssue(String code) {
        return "ERR_STRIPE_TOTAL_PAGES".equals(code)
                || "ERR_STRIPE_MC_19P".equals(code)
                || "ERR_SHPFY_PDF_PAGES_EXCEEDED".equals(code);
    }

    private String autoFixPrompt(ValidationIssueResponse issue) {
        return switch (issue.fixStrategy()) {
            case MERGE_PER_TYPE -> "We can merge duplicate files in one evidence type automatically. Run auto-fix and then validate again.";
            case COMPRESS_SHOPIFY_IMAGE_IF_IMAGE -> "We can compress oversized Shopify image files automatically. Run auto-fix and then recheck the case.";
            case COMPRESS_STRIPE_PDF -> "We can compress oversized Stripe PDFs automatically. Run auto-fix and then recheck total size.";
            case REDUCE_TOTAL_SIZE -> "We can shrink large images and supported PDFs automatically when the pack is over the total-size limit. Run auto-fix and then recheck the case.";
            case REDUCE_TOTAL_PAGES -> "We can remove blank pages and exact duplicate PDF pages automatically. Run auto-fix and then recheck the page count.";
            case CONVERT_PDF_TO_PDFA -> "We can convert supported PDFs into PDF/A automatically. Run auto-fix and revalidate the case.";
            case FLATTEN_PDF_PORTFOLIO -> "We can flatten supported PDF portfolios automatically. Run auto-fix and then validate again.";
            case REMOVE_EXTERNAL_LINKS_PDF -> "We can remove external-link annotations from supported PDFs automatically. Run auto-fix and revalidate.";
            default -> issue.message();
        };
    }

    private String humanizeEvidenceType(EvidenceType evidenceType) {
        String[] parts = evidenceType.name().split("_");
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                text.append(' ');
            }
            String part = parts[index].toLowerCase(Locale.ROOT);
            text.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return text.toString();
    }

    private EvidenceType firstMissingEvidenceType(ReadinessService.ReadinessSummary readiness) {
        if (readiness == null || readiness.missingRequiredEvidenceTypes() == null) {
            return null;
        }
        return readiness.missingRequiredEvidenceTypes().stream()
                .map(this::parseEvidenceType)
                .filter(type -> type != null)
                .findFirst()
                .orElse(null);
    }

    private EvidenceType parseEvidenceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EvidenceType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String guidanceLabel(EvidenceType evidenceType) {
        if (evidenceType == null) {
            return "the next required evidence";
        }
        return switch (evidenceType) {
            case ORDER_RECEIPT -> "order receipt or checkout confirmation";
            case CUSTOMER_DETAILS -> "customer details or billing profile";
            case CUSTOMER_COMMUNICATION -> "customer communication or support chat";
            case POLICIES -> "refund or return policy";
            case FULFILLMENT_DELIVERY -> "delivery proof or carrier tracking page";
            case DIGITAL_USAGE_LOGS -> "digital usage logs or access records";
            case REFUND_CANCELLATION -> "refund or cancellation proof";
            case OTHER_SUPPORTING -> "supporting evidence";
        };
    }

    private List<String> missingEvidencePlaybook(
            EvidenceType evidenceType,
            ReasonCodeChecklistService.ReasonChecklist reasonChecklist
    ) {
        List<String> actions = new ArrayList<>();
        if (evidenceType != null) {
            switch (evidenceType) {
                case ORDER_RECEIPT -> actions.add("Upload a checkout receipt, invoice, or order confirmation that shows the amount, order number, and cardholder identity.");
                case CUSTOMER_DETAILS -> actions.add("Upload a customer profile, billing record, or account page that ties the order to the cardholder.");
                case CUSTOMER_COMMUNICATION -> actions.add("Upload support chat, email thread, or message screenshots showing the customer reported the problem.");
                case POLICIES -> actions.add("Upload the refund, return, cancellation, or service policy that applied at the time of purchase.");
                case FULFILLMENT_DELIVERY -> actions.add("Upload a carrier tracking page, delivery scan, or signed proof that shows shipped or delivered status.");
                case DIGITAL_USAGE_LOGS -> actions.add("Upload login history, device activity, or service access logs that show the digital product was used.");
                case REFUND_CANCELLATION -> actions.add("Upload refund approval, cancellation confirmation, or support message confirming the merchant outcome.");
                case OTHER_SUPPORTING -> actions.add("Upload the clearest supporting file that directly proves the disputed event.");
            }
        }
        if (reasonChecklist != null && reasonChecklist.priorityActions() != null) {
            reasonChecklist.priorityActions().stream()
                    .filter(action -> action != null && !action.isBlank())
                    .limit(2)
                    .forEach(actions::add);
        }
        return actions.stream()
                .distinct()
                .limit(3)
                .toList();
    }

    private String latestAutoFixProgressNote(UUID caseId) {
        if (caseId == null) {
            return null;
        }
        return fixJobRepository.findFirstByDisputeCaseIdAndStatusOrderByCreatedAtDesc(caseId, FixJobStatus.SUCCEEDED)
                .map(job -> firstSentence(job.getSummary()))
                .filter(summary -> summary != null && !summary.isBlank())
                .orElse(null);
    }

    private String firstSentence(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isBlank()) {
            return null;
        }
        int periodIndex = normalized.indexOf('.');
        if (periodIndex < 0) {
            return normalized;
        }
        return normalized.substring(0, periodIndex + 1).trim();
    }

    private String fileLabel(EvidenceFileReportResponse file) {
        return "'" + shortenFileName(file.originalName()) + "' (" + formatBytesLabel(file.sizeBytes()) + ")";
    }

    private String formatBytesLabel(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytes / 1048576.0);
    }

    private String shortenFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "selected file";
        }
        return originalName.length() > 40 ? originalName.substring(0, 37) + "..." : originalName;
    }

    private List<String> sizePriorityList(CaseReportResponse report) {
        long totalSize = report.files().stream().mapToLong(EvidenceFileReportResponse::sizeBytes).sum();
        long overflow = Math.max(0L, totalSize - totalSizeLimitFor(report));
        return report.files().stream()
                .sorted((left, right) -> Long.compare(right.sizeBytes(), left.sizeBytes()))
                .limit(3)
                .map(file -> shortenFileName(file.originalName()) + " - " + formatBytesLabel(file.sizeBytes()) + " - "
                        + sizeImpactHint(file.sizeBytes(), overflow))
                .toList();
    }

    private List<String> pagePriorityList(CaseReportResponse report) {
        int totalPages = totalPagesFor(report);
        int trimTarget = pageTrimTargetFor(report);
        int overflowPages = trimTarget > 0 ? Math.max(0, totalPages - trimTarget) : 0;
        return report.files().stream()
                .filter(file -> file.pageCount() > 0)
                .sorted((left, right) -> Integer.compare(right.pageCount(), left.pageCount()))
                .limit(3)
                .map(file -> pagePriorityLabel(file, overflowPages))
                .toList();
    }

    private List<String> pageManualTrimPlaybook(
            CaseReportResponse report,
            EvidenceFileReportResponse targetFile
    ) {
        if (targetFile == null) {
            return pagePriorityList(report);
        }

        int totalPages = totalPagesFor(report);
        int trimTarget = pageTrimTargetFor(report);
        int overflowPages = trimTarget > 0 ? Math.max(0, totalPages - trimTarget) : 0;
        if (overflowPages <= 0) {
            return List.of(
                    "The pack is already within the page limit. Re-run validation after your latest upload to confirm the current state."
                );
        }

        List<String> actions = new ArrayList<>();
        actions.add(primaryTrimAction(targetFile, overflowPages));

        String otherFilesAction = keepOtherFilesAction(report, targetFile, overflowPages);
        if (!otherFilesAction.isBlank()) {
            actions.add(otherFilesAction);
        }

        actions.add("Re-upload the trimmed PDF, then run validation again before export so the new page count is recorded.");
        return actions.stream()
                .filter(action -> action != null && !action.isBlank())
                .limit(3)
                .toList();
    }

    private String pagePriorityLabel(EvidenceFileReportResponse file, int overflowPages) {
        StringBuilder label = new StringBuilder()
                .append(shortenFileName(file.originalName()))
                .append(" - ")
                .append(file.pageCount())
                .append(" page(s) - ")
                .append(pageImpactHint(file.pageCount(), overflowPages));
        String trimHint = pageTrimListHint(file.pageCount(), overflowPages);
        if (!trimHint.isBlank()) {
            label.append(" - ").append(trimHint)
                    .append(" - ").append(pageTrimReason(file));
        }
        return label.toString();
    }

    private List<String> matchingEvidenceTypePriorityList(CaseReportResponse report, EvidenceType targetEvidenceType) {
        if (targetEvidenceType == null) {
            return List.of();
        }
        return report.files().stream()
                .filter(file -> file.evidenceType() == targetEvidenceType)
                .sorted((left, right) -> Long.compare(right.sizeBytes(), left.sizeBytes()))
                .limit(3)
                .map(file -> shortenFileName(file.originalName()) + " - " + formatBytesLabel(file.sizeBytes()))
                .toList();
    }

    private String sizeImpactHint(long fileSizeBytes, long overflowBytes) {
        if (overflowBytes <= 0) {
            return "largest size impact";
        }
        long residual = Math.max(0L, overflowBytes - fileSizeBytes);
        if (residual == 0L) {
            return "enough to clear the " + formatBytesLabel(overflowBytes) + " overage";
        }
        return "still leaves " + formatBytesLabel(residual) + " over limit";
    }

    private boolean isImageFile(EvidenceFileReportResponse file) {
        return file != null && (file.fileFormat() == com.example.demo.dispute.domain.FileFormat.JPEG
                || file.fileFormat() == com.example.demo.dispute.domain.FileFormat.PNG);
    }

    private boolean isPdfFile(EvidenceFileReportResponse file) {
        return file != null && file.fileFormat() == com.example.demo.dispute.domain.FileFormat.PDF;
    }

    private long totalUploadedBytes(CaseReportResponse report) {
        return report.files().stream().mapToLong(EvidenceFileReportResponse::sizeBytes).sum();
    }

    private long suggestedManualImageRescueTargetBytes(EvidenceFileReportResponse file, long overflowBytes) {
        long desiredSavings = overflowBytes + (96L * 1024L);
        long floor = 96L * 1024L;
        long target = file.sizeBytes() - desiredSavings;
        if (target < floor) {
            return floor;
        }
        long quarterDropTarget = Math.max(floor, Math.round(file.sizeBytes() * 0.75d));
        return Math.min(target, quarterDropTarget);
    }

    private long suggestedManualPdfRescueTargetBytes(EvidenceFileReportResponse file, long overflowBytes) {
        long desiredSavings = overflowBytes + (128L * 1024L);
        long floor = 256L * 1024L;
        long target = file.sizeBytes() - desiredSavings;
        if (target < floor) {
            return floor;
        }
        long thirtyPercentDropTarget = Math.max(floor, Math.round(file.sizeBytes() * 0.70d));
        return Math.min(target, thirtyPercentDropTarget);
    }

    private String pageImpactHint(int filePages, int overflowPages) {
        if (overflowPages <= 0) {
            return "largest page-count impact";
        }
        int residual = Math.max(0, overflowPages - filePages);
        if (residual == 0) {
            return "enough to clear the " + overflowPages + " page overage";
        }
        return "still leaves " + residual + " page(s) over limit";
    }

    private String pageTrimPrimaryHint(CaseReportResponse report, EvidenceFileReportResponse file) {
        if (file == null) {
            return "";
        }
        int totalPages = totalPagesFor(report);
        int trimTarget = pageTrimTargetFor(report);
        int overflowPages = trimTarget > 0 ? Math.max(0, totalPages - trimTarget) : 0;
        String trimHint = pageTrimListHint(file.pageCount(), overflowPages);
        if (!trimHint.isBlank()) {
            return trimHint + " because " + pageTrimReason(file) + ".";
        }
        return "Start by removing appendix, cover, or verbose event-log pages that are not needed for this dispute.";
    }

    private String primaryTrimAction(EvidenceFileReportResponse file, int overflowPages) {
        String fileName = shortenFileName(file.originalName());
        String trimHint = pageTrimListHint(file.pageCount(), overflowPages);
        if (!trimHint.isBlank()) {
            String keepAndTrim = trimHint.replaceFirst("^start with", "keep");
            return "Trim " + fileName + " first: " + keepAndTrim + ". This one file is enough to clear the "
                    + overflowPages + " page overage because " + pageTrimReason(file) + ".";
        }
        return "Shorten " + fileName + " first. It has the largest page count in the pack and is the safest file to reduce below the limit.";
    }

    private String keepOtherFilesAction(
            CaseReportResponse report,
            EvidenceFileReportResponse targetFile,
            int overflowPages
    ) {
        List<EvidenceFileReportResponse> otherFiles = report.files().stream()
                .filter(file -> file.fileId() != null && !file.fileId().equals(targetFile.fileId())
                        || file.fileId() == null && file != targetFile)
                .sorted((left, right) -> Integer.compare(left.pageCount(), right.pageCount()))
                .limit(2)
                .toList();
        if (otherFiles.isEmpty()) {
            return "";
        }

        String fileNames = otherFiles.stream()
                .map(file -> shortenFileName(file.originalName()))
                .distinct()
                .reduce((left, right) -> left + " and " + right)
                .orElse("");
        if (fileNames.isBlank()) {
            return "";
        }

        int otherPageCount = otherFiles.stream().mapToInt(EvidenceFileReportResponse::pageCount).sum();
        if (otherPageCount <= 0) {
            return "Leave the smaller supporting files alone for now. Trim the long PDF first, then revalidate.";
        }

        int residual = Math.max(0, overflowPages - otherPageCount);
        if (residual == 0) {
            return "If the long PDF still needs help after that first trim, " + fileNames
                    + " are the next-smallest files to shorten.";
        }

        return "Leave " + fileNames + " alone for now. Even trimming them first would still leave "
                + residual + " page(s) over the limit, so the long PDF is the only useful first cut.";
    }

    private String pageOverageSummary(CaseReportResponse report) {
        int displayLimit = pageLimitFor(report);
        int trimTarget = pageTrimTargetFor(report);
        if (displayLimit <= 0 || trimTarget <= 0) {
            return "This packet is over the allowed page limit.";
        }
        int totalPages = totalPagesFor(report);
        int overflowPages = Math.max(0, totalPages - trimTarget);
        if (overflowPages <= 0) {
            return "This packet is already at the allowed page limit.";
        }
        if (trimTarget == displayLimit) {
            return "Current pack is " + totalPages + " total page(s), which is " + overflowPages
                    + " page(s) over the " + pageLimitLabel(report) + ".";
        }
        return "Current pack is " + totalPages + " total page(s), which needs " + overflowPages
                + " page(s) removed to get under the " + pageLimitLabel(report) + ".";
    }

    private String pageTrimListHint(int filePages, int overflowPages) {
        if (overflowPages <= 0 || filePages <= overflowPages) {
            return "";
        }
        int keepPages = filePages - overflowPages;
        if (keepPages <= 0 || keepPages >= filePages) {
            return "";
        }
        String keepRange = pageRangeLabel(1, keepPages);
        String trimRange = pageRangeLabel(keepPages + 1, filePages);
        return "start with " + keepRange + " and trim " + trimRange;
    }

    private String pageRangeLabel(int startPage, int endPage) {
        if (startPage >= endPage) {
            return "page " + startPage;
        }
        return "pages " + startPage + "-" + endPage;
    }

    private String pageTrimReason(EvidenceFileReportResponse file) {
        if (file == null) {
            return "tail pages are usually the safest low-signal pages to cut first";
        }
        String originalName = file.originalName() == null ? "" : file.originalName().toLowerCase(Locale.ROOT);
        if (originalName.contains("dump") || originalName.contains("log") || originalName.contains("export")) {
            return "long exports usually repeat appendix or low-signal tail pages at the end";
        }
        return switch (file.evidenceType()) {
            case FULFILLMENT_DELIVERY -> "carrier proofs usually repeat low-signal scan history or appendix pages at the end";
            case ORDER_RECEIPT -> "receipt PDFs usually end with duplicate confirmation, footer, or terms pages";
            case CUSTOMER_DETAILS -> "account exports usually end with profile, privacy, or settings pages that do not help this dispute";
            case CUSTOMER_COMMUNICATION -> "chat exports usually end with repeated transcript tails, headers, or footer pages";
            case POLICIES -> "policy exports usually end with generic legal or footer pages that add little reviewer value";
            case DIGITAL_USAGE_LOGS -> "usage exports usually end with verbose event tails that are safer to trim first";
            case REFUND_CANCELLATION -> "refund exports usually end with duplicate status history or footer pages";
            case OTHER_SUPPORTING -> "tail pages are usually the safest low-signal pages to cut first";
        };
    }

    private int pageLimitFor(CaseReportResponse report) {
        if (report.productScope() == null) {
            return 0;
        }
        return switch (report.productScope()) {
            case STRIPE_DISPUTE -> report.cardNetwork() == CardNetwork.MASTERCARD ? 19 : 50;
            case SHOPIFY_PAYMENTS_CHARGEBACK, SHOPIFY_CREDIT_DISPUTE -> 50;
        };
    }

    private int pageTrimTargetFor(CaseReportResponse report) {
        if (report.productScope() == null) {
            return 0;
        }
        return switch (report.productScope()) {
            case STRIPE_DISPUTE -> report.cardNetwork() == CardNetwork.MASTERCARD ? 19 : 49;
            case SHOPIFY_PAYMENTS_CHARGEBACK, SHOPIFY_CREDIT_DISPUTE -> 49;
        };
    }

    private int totalPagesFor(CaseReportResponse report) {
        return report.files().stream().mapToInt(EvidenceFileReportResponse::pageCount).sum();
    }

    private String pageLimitLabel(CaseReportResponse report) {
        if (report.productScope() == ProductScope.STRIPE_DISPUTE) {
            return report.cardNetwork() == CardNetwork.MASTERCARD
                    ? "19-page Mastercard limit"
                    : "50-page Stripe limit";
        }
        return "50-page PDF limit";
    }

    private record DisplayStateSummary(String label, String badgeClass) {
    }

    public record NextStepGuidance(
            String title,
            String description,
            String progressNote,
            String ctaLabel,
            String ctaTarget,
            boolean postAction,
            String supportingTitle,
            List<String> supportingActions
    ) {
    }

    public record ManualImageRescueAction(
            String title,
            UUID fileId,
            long targetBytes,
            String fileLabel,
            String currentSizeLabel,
            String targetSizeLabel,
            String estimatedSavingsLabel,
            String impactSummary,
            String caution
    ) {
    }

    public record ManualPdfRescueAction(
            String title,
            UUID fileId,
            long targetBytes,
            String fileLabel,
            String currentSizeLabel,
            String targetSizeLabel,
            String estimatedSavingsLabel,
            String impactSummary,
            String caution
    ) {
    }

}
