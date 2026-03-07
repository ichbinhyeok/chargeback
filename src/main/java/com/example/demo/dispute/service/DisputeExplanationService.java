package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.domain.EvidenceType;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DisputeExplanationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private final ReasonCodeChecklistService reasonCodeChecklistService;

    public DisputeExplanationService(ReasonCodeChecklistService reasonCodeChecklistService) {
        this.reasonCodeChecklistService = reasonCodeChecklistService;
    }

    public ExplanationDraft buildDraft(CaseReportResponse report) {
        List<EvidenceType> presentEvidenceTypes = report.files().stream()
                .map(EvidenceFileReportResponse::evidenceType)
                .distinct()
                .toList();

        ReasonCodeChecklistService.ReasonChecklist checklist = reasonCodeChecklistService.resolve(
                report.platform(),
                report.productScope(),
                report.reasonCode(),
                report.cardNetwork(),
                presentEvidenceTypes
        );

        String reasonLabel = checklist.hasReasonCode() ? checklist.reasonLabel() : "Reason not specified";
        String title = "Dispute Explanation Draft - " + reasonLabel;
        String summary = reasonSpecificSummary(checklist.canonicalReasonKey());

        String context = buildContextLine(report, checklist);
        List<String> evidenceIndex = buildEvidenceIndex(report.files());
        List<String> readinessNotes = buildReadinessNotes(checklist);

        String body = buildBody(
                title,
                context,
                summary,
                evidenceIndex,
                readinessNotes,
                checklist.sourceUrls()
        );

        return new ExplanationDraft(
                title,
                PublicCaseReference.from(report),
                checklist.reasonLabel(),
                checklist.canonicalReasonKey(),
                context,
                summary,
                evidenceIndex,
                readinessNotes,
                checklist.sourceUrls(),
                body
        );
    }

    private String buildContextLine(
            CaseReportResponse report,
            ReasonCodeChecklistService.ReasonChecklist checklist
    ) {
        String publicCaseRef = PublicCaseReference.from(report);
        String createdDate = DATE_FORMAT.format(report.createdAt());
        String reasonDisplay = checklist.hasReasonCode()
                ? checklist.reasonLabel() + " (" + checklist.canonicalReasonKey() + ")"
                : "Not specified";
        String networkDisplay = report.cardNetwork() == null ? "Not set" : report.cardNetwork().name();
        return String.format(
                Locale.ROOT,
                "Case %s | Platform=%s | Scope=%s | Reason=%s | CardNetwork=%s | Created=%s",
                publicCaseRef,
                report.platform().name(),
                report.productScope().name(),
                reasonDisplay,
                networkDisplay,
                createdDate
        );
    }

    private List<String> buildEvidenceIndex(List<EvidenceFileReportResponse> files) {
        if (files == null || files.isEmpty()) {
            return List.of("No evidence files are currently uploaded.");
        }

        Map<EvidenceType, List<EvidenceFileReportResponse>> byType = files.stream()
                .collect(Collectors.groupingBy(
                        EvidenceFileReportResponse::evidenceType,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<String> lines = new ArrayList<>();
        byType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    long totalBytes = entry.getValue().stream().mapToLong(EvidenceFileReportResponse::sizeBytes).sum();
                    int totalPages = entry.getValue().stream().mapToInt(EvidenceFileReportResponse::pageCount).sum();
                    List<String> formats = entry.getValue().stream()
                            .map(item -> item.fileFormat().name())
                            .distinct()
                            .sorted(Comparator.naturalOrder())
                            .toList();
                    lines.add(String.format(
                            Locale.ROOT,
                            "- %s: files=%d, formats=%s, total_pages=%d, total_size_bytes=%d",
                            entry.getKey().name(),
                            entry.getValue().size(),
                            String.join("/", formats),
                            totalPages,
                            totalBytes
                    ));
                });
        return List.copyOf(lines);
    }

    private List<String> buildReadinessNotes(ReasonCodeChecklistService.ReasonChecklist checklist) {
        List<String> notes = new ArrayList<>();
        if (!checklist.missingRequiredEvidence().isEmpty()) {
            notes.add("Missing required evidence: " + String.join(", ", checklist.missingRequiredEvidence()) + ".");
        }
        if (!checklist.missingRecommendedEvidence().isEmpty()) {
            notes.add("Missing recommended evidence: " + String.join(", ", checklist.missingRecommendedEvidence()) + ".");
        }
        if (!checklist.priorityActions().isEmpty()) {
            notes.add("Priority actions:");
            checklist.priorityActions().forEach(action -> notes.add("- " + action));
        }
        if (notes.isEmpty()) {
            notes.add("No checklist gaps detected for current uploaded evidence types.");
        }
        return List.copyOf(notes);
    }

    private String reasonSpecificSummary(String canonicalReasonKey) {
        if (canonicalReasonKey == null || canonicalReasonKey.isBlank()) {
            return "The dispute response should tie each attached evidence file to a clear factual timeline and request reversal based on submitted records.";
        }
        return switch (canonicalReasonKey) {
            case "PRODUCT_NOT_RECEIVED" ->
                    "This response should emphasize fulfillment and delivery records, shipment timeline consistency, and customer communication demonstrating receipt or delivery attempts.";
            case "PRODUCT_UNACCEPTABLE" ->
                    "This response should align product/service descriptions, fulfillment details, and post-purchase communication to show the delivered item/service matched what was presented.";
            case "FRAUDULENT", "UNRECOGNIZED" ->
                    "This response should connect transaction legitimacy signals (customer details, delivery/access evidence, and communication) to demonstrate authorized use or account linkage.";
            case "CREDIT_NOT_PROCESSED" ->
                    "This response should document refund/cancellation handling timestamps and policy terms to show whether credit was processed correctly or not contractually due.";
            case "SUBSCRIPTION_CANCELED" ->
                    "This response should provide cancellation policy visibility, renewal/cancellation communication history, and account activity timing around the disputed period.";
            case "DUPLICATE" ->
                    "This response should clearly distinguish each charge or prove prior reversal/refund handling with receipts and customer communication.";
            case "GENERAL" ->
                    "This response should provide a concise transaction timeline, itemized pricing context, and customer communication supporting the validity of the original charge.";
            default ->
                    "This response should map each attached file to a concrete claim and explain why the submitted evidence invalidates the dispute reason.";
        };
    }

    private String buildBody(
            String title,
            String context,
            String summary,
            List<String> evidenceIndex,
            List<String> readinessNotes,
            List<String> sourceUrls
    ) {
        StringBuilder text = new StringBuilder();
        text.append(title).append('\n');
        text.append('\n');
        text.append("Case Context").append('\n');
        text.append(context).append('\n');
        text.append('\n');
        text.append("Draft Position Statement").append('\n');
        text.append(summary).append('\n');
        text.append("The attached evidence set is organized by evidence type and submitted to support this position.").append('\n');
        text.append('\n');
        text.append("Evidence Index").append('\n');
        evidenceIndex.forEach(line -> text.append(line).append('\n'));
        text.append('\n');
        text.append("Checklist Gaps and Actions").append('\n');
        readinessNotes.forEach(line -> text.append(line).append('\n'));
        if (sourceUrls != null && !sourceUrls.isEmpty()) {
            text.append('\n');
            text.append("Policy References").append('\n');
            sourceUrls.forEach(url -> text.append("- ").append(url).append('\n'));
        }
        text.append('\n');
        text.append("Disclaimer: This draft is a submission-writing aid only. It is not legal advice and does not guarantee dispute outcomes.").append('\n');
        return text.toString();
    }

    public record ExplanationDraft(
            String title,
            String publicCaseRef,
            String reasonLabel,
            String canonicalReasonKey,
            String contextLine,
            String summaryLine,
            List<String> evidenceIndex,
            List<String> readinessNotes,
            List<String> sourceUrls,
            String text
    ) {
    }
}
