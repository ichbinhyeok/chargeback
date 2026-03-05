package com.example.demo.dispute.web;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.AutoFixService;
import com.example.demo.dispute.service.CaseReportService;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.CheckoutStartResult;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.PaymentService;
import com.example.demo.dispute.service.SubmissionExportService;
import com.example.demo.dispute.service.ValidationHistoryService;
import com.example.demo.dispute.service.ValidationService;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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

    private final CaseService caseService;
    private final CaseReportService caseReportService;
    private final EvidenceFileService evidenceFileService;
    private final ValidationService validationService;
    private final ValidationHistoryService validationHistoryService;
    private final AutoFixService autoFixService;
    private final SubmissionExportService submissionExportService;
    private final PaymentService paymentService;
    private final int caseMaxFiles;
    private final int retentionDays;
    private final String publicBaseUrl;

    public WebCaseController(
            CaseService caseService,
            CaseReportService caseReportService,
            EvidenceFileService evidenceFileService,
            ValidationService validationService,
            ValidationHistoryService validationHistoryService,
            AutoFixService autoFixService,
            SubmissionExportService submissionExportService,
            PaymentService paymentService,
            @Value("${app.case.max-files:100}") int caseMaxFiles,
            @Value("${app.retention.days:7}") int retentionDays,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.caseService = caseService;
        this.caseReportService = caseReportService;
        this.evidenceFileService = evidenceFileService;
        this.validationService = validationService;
        this.validationHistoryService = validationHistoryService;
        this.autoFixService = autoFixService;
        this.submissionExportService = submissionExportService;
        this.paymentService = paymentService;
        this.caseMaxFiles = caseMaxFiles;
        this.retentionDays = retentionDays;
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
    public String newCase(Model model, @RequestParam(value = "error", required = false) String error) {
        model.addAttribute("platforms", Platform.values());
        model.addAttribute("productScopes", ProductScope.values());
        model.addAttribute("cardNetworks", CardNetwork.values());
        model.addAttribute("error", error);
        return "newCase";
    }

    @PostMapping("/new")
    public String createCase(
            @RequestParam("platform") Platform platform,
            @RequestParam("productScope") ProductScope productScope,
            @RequestParam(value = "reasonCode", required = false) String reasonCode,
            @RequestParam(value = "dueAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAt,
            @RequestParam(value = "cardNetwork", required = false) String cardNetwork
    ) {
        try {
            CardNetwork selectedNetwork = null;
            if (cardNetwork != null && !cardNetwork.isBlank()) {
                selectedNetwork = CardNetwork.valueOf(cardNetwork);
            }

            DisputeCase disputeCase = caseService.createCase(
                    new CreateCaseRequest(platform, productScope, reasonCode, dueAt, selectedNetwork)
            );
            return "redirect:/c/" + disputeCase.getCaseToken();
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
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "caseValidate";
    }

    @PostMapping("/c/{caseToken}/validate")
    public String validateStored(@PathVariable String caseToken) {
        try {
            DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
            List<com.example.demo.dispute.api.EvidenceFileInput> files = evidenceFileService.listAsValidationInputs(disputeCase.getId());
            if (files.isEmpty()) {
                throw new IllegalArgumentException("No uploaded files found.");
            }

            caseService.transitionState(disputeCase, CaseState.VALIDATING);
            ValidateCaseResponse response = validationService.validate(disputeCase, files, false);
            validationHistoryService.record(disputeCase, response, ValidationSource.STORED_FILES, false);
            caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);

            String message = response.passed() ? "Validation passed." : "Validation completed with issues.";
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

    @GetMapping("/c/{caseToken}/export")
    public String exportPage(
            @PathVariable String caseToken,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "paid", required = false) String paid,
            Model model
    ) {
        populateCaseModel(caseToken, model);
        if (paid != null && !paid.isBlank()) {
            model.addAttribute("message", "Payment confirmed. Downloads unlocked.");
        } else {
            model.addAttribute("message", message);
        }
        model.addAttribute("error", error);
        model.addAttribute("paymentConfigured", paymentService.isCheckoutConfigured());
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
        if (!isExportableState(disputeCase.getState())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Run validation and resolve blocked issues before export."));
            return;
        }
        if (!paymentService.isPaid(disputeCase.getId())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Payment required to download."));
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"Chargeback_Submission_Pack_" + caseToken + ".zip\"");
        submissionExportService.writeSubmissionZip(caseToken, response.getOutputStream());
        markDownloaded(caseToken);
    }

    @GetMapping("/c/{caseToken}/download/summary.pdf")
    public void downloadSummaryPdf(@PathVariable String caseToken, HttpServletResponse response) throws Exception {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        if (!isExportableState(disputeCase.getState())) {
            response.sendRedirect("/c/" + caseToken + "/export?error=" + encode("Run validation before downloading the summary guide."));
            return;
        }

        boolean paid = paymentService.isPaid(disputeCase.getId());
        response.setContentType("application/pdf");
        if (paid) {
            response.setHeader("Content-Disposition", "attachment; filename=\"Chargeback_Submission_Guide_" + caseToken + ".pdf\"");
            submissionExportService.writeSummaryPdf(caseToken, response.getOutputStream(), false);
            markDownloaded(caseToken);
        } else {
            response.setHeader("Content-Disposition", "attachment; filename=\"Chargeback_Submission_Guide_FREE_" + caseToken + ".pdf\"");
            submissionExportService.writeSummaryPdf(caseToken, response.getOutputStream(), true);
        }
    }

    @GetMapping("/c/{caseToken}/access-key.txt")
    public void downloadAccessKey(@PathVariable String caseToken, HttpServletResponse response) throws Exception {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        Instant expiresAt = disputeCase.getCreatedAt().plus(retentionDays, ChronoUnit.DAYS);
        String caseUrl = publicBaseUrl + "/c/" + disputeCase.getCaseToken();

        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"chargeback_case_" + disputeCase.getCaseToken() + ".txt\"");
        response.getWriter().print(
                "Chargeback Case Access Key\n"
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
        ReadinessSummary readiness = computeReadiness(report);
        Instant expiresAt = disputeCase.getCreatedAt().plus(retentionDays, ChronoUnit.DAYS);
        int uploadedFileCount = report.files().size();
        int remainingFileSlots = Math.max(0, caseMaxFiles - uploadedFileCount);
        long uploadedSizeBytes = report.files().stream().mapToLong(EvidenceFileReportResponse::sizeBytes).sum();
        long totalSizeLimitBytes = totalSizeLimitFor(report.productScope());
        long remainingSizeBytes = Math.max(0, totalSizeLimitBytes - uploadedSizeBytes);
        int totalPagesUsed = report.files().stream().mapToInt(EvidenceFileReportResponse::pageCount).sum();
        int stripePagesRemaining = report.productScope() == ProductScope.STRIPE_DISPUTE
                ? Math.max(0, 49 - totalPagesUsed)
                : -1;
        int mastercardPagesRemaining = report.productScope() == ProductScope.STRIPE_DISPUTE
                && disputeCase.getCardNetwork() == CardNetwork.MASTERCARD
                ? Math.max(0, 19 - totalPagesUsed)
                : -1;

        model.addAttribute("disputeCase", disputeCase);
        model.addAttribute("report", report);
        model.addAttribute("evidenceTypes", EvidenceType.values());
        model.addAttribute("isPaid", paymentService.isPaid(disputeCase.getId()));
        model.addAttribute("latestPayment", paymentService.latestPayment(disputeCase.getId()).orElse(null));
        model.addAttribute("readinessScore", readiness.score());
        model.addAttribute("readinessLabel", readiness.label());
        model.addAttribute("readinessBlocked", readiness.blockedCount());
        model.addAttribute("readinessFixable", readiness.fixableCount());
        model.addAttribute("readinessWarning", readiness.warningCount());
        model.addAttribute("missingEvidenceTypes", readiness.missingEvidenceTypes());
        model.addAttribute("retentionDays", retentionDays);
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
    }

    private ReadinessSummary computeReadiness(CaseReportResponse report) {
        int blocked = 0;
        int fixable = 0;
        int warning = 0;

        if (report.latestValidation() != null) {
            for (ValidationIssueResponse issue : report.latestValidation().issues()) {
                if (issue.severity() == IssueSeverity.BLOCKED) {
                    blocked++;
                } else if (issue.severity() == IssueSeverity.FIXABLE) {
                    fixable++;
                } else if (issue.severity() == IssueSeverity.WARNING) {
                    warning++;
                }
            }
        }

        EnumSet<EvidenceType> presentTypes = EnumSet.noneOf(EvidenceType.class);
        report.files().forEach(file -> presentTypes.add(file.evidenceType()));

        List<String> missing = new ArrayList<>();
        for (EvidenceType type : EvidenceType.values()) {
            if (!presentTypes.contains(type)) {
                missing.add(type.name());
            }
        }

        int score = 100;
        score -= blocked * 30;
        score -= fixable * 12;
        score -= warning * 5;
        score -= Math.min(20, missing.size() * 3);
        if (report.files().isEmpty()) {
            score = Math.min(score, 10);
        }
        if (report.latestValidation() == null && !report.files().isEmpty()) {
            score = Math.min(score, 70);
        }
        score = Math.max(0, Math.min(100, score));

        String label;
        if (score >= 90) {
            label = "Excellent";
        } else if (score >= 70) {
            label = "Good";
        } else if (score >= 50) {
            label = "Needs Work";
        } else {
            label = "Critical";
        }

        return new ReadinessSummary(score, label, blocked, fixable, warning, missing);
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

    private long totalSizeLimitFor(ProductScope productScope) {
        return switch (productScope) {
            case STRIPE_DISPUTE -> STRIPE_TOTAL_SIZE_LIMIT_BYTES;
            case SHOPIFY_PAYMENTS_CHARGEBACK -> SHOPIFY_PAYMENTS_TOTAL_SIZE_LIMIT_BYTES;
            case SHOPIFY_CREDIT_DISPUTE -> SHOPIFY_CREDIT_TOTAL_SIZE_LIMIT_BYTES;
        };
    }

    private String formatSizeLabel(long bytes) {
        return String.format("%.1f MB", bytes / (double) MB);
    }

    private record ReadinessSummary(
            int score,
            String label,
            int blockedCount,
            int fixableCount,
            int warningCount,
            List<String> missingEvidenceTypes
    ) {
    }
}
