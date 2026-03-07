package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.persistence.DisputeCase;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class PublicCaseReference {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
            .withZone(ZoneId.systemDefault());

    private PublicCaseReference() {
    }

    public static String from(DisputeCase disputeCase) {
        return from(disputeCase.getId(), disputeCase.getCreatedAt());
    }

    public static String from(CaseReportResponse report) {
        return from(report.caseId(), report.createdAt());
    }

    public static String from(UUID caseId, Instant createdAt) {
        String date = DATE_FORMAT.format(createdAt);
        String idSuffix = caseId.toString().replace("-", "").substring(0, 6);
        return "cb_" + date + "_" + idSuffix;
    }
}
