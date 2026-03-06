package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.persistence.DisputeCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InputFingerprintService {

    public String fingerprint(DisputeCase disputeCase, List<EvidenceFileInput> files, boolean earlySubmit) {
        MessageDigest digest = newDigest();
        update(digest, "platform", asText(disputeCase.getPlatform()));
        update(digest, "productScope", asText(disputeCase.getProductScope()));
        update(digest, "reasonCode", asText(disputeCase.getReasonCode()));
        update(digest, "cardNetwork", asText(disputeCase.getCardNetwork()));
        update(digest, "dueAt", asText(disputeCase.getDueAt()));
        update(digest, "earlySubmit", Boolean.toString(earlySubmit));

        List<String> normalizedFiles = files.stream()
                .map(this::normalizeFile)
                .sorted()
                .toList();
        update(digest, "fileCount", Integer.toString(normalizedFiles.size()));
        normalizedFiles.forEach(normalized -> update(digest, "file", normalized));

        return HexFormat.of().formatHex(digest.digest());
    }

    private String normalizeFile(EvidenceFileInput file) {
        return String.join(
                "|",
                asText(file.evidenceType()),
                asText(file.format()),
                Long.toString(file.sizeBytes()),
                Integer.toString(file.pageCount()),
                Boolean.toString(file.externalLinkDetected()),
                Boolean.toString(file.pdfACompliant()),
                Boolean.toString(file.pdfPortfolio())
        );
    }

    private void update(MessageDigest digest, String key, String value) {
        digest.update((key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString();
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
