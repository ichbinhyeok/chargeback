package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.InputFingerprintService;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputFingerprintServiceTest {

    private final InputFingerprintService service = new InputFingerprintService();

    @Test
    void fingerprintIsStableForSameInputsRegardlessOfOrder() {
        DisputeCase disputeCase = stripeCase("base_reason");
        EvidenceFileInput fileA = new EvidenceFileInput(
                EvidenceType.ORDER_RECEIPT,
                FileFormat.PDF,
                1000,
                2,
                false,
                true,
                false
        );
        EvidenceFileInput fileB = new EvidenceFileInput(
                EvidenceType.CUSTOMER_DETAILS,
                FileFormat.PNG,
                2000,
                1,
                false,
                true,
                false
        );

        String first = service.fingerprint(disputeCase, List.of(fileA, fileB), false);
        String second = service.fingerprint(disputeCase, List.of(fileB, fileA), false);

        assertEquals(first, second);
    }

    @Test
    void fingerprintChangesWhenContextChanges() {
        DisputeCase initial = stripeCase("reason_a");
        DisputeCase changed = stripeCase("reason_b");

        String initialFingerprint = service.fingerprint(initial, List.of(singleFile()), false);
        String changedFingerprint = service.fingerprint(changed, List.of(singleFile()), false);

        assertNotEquals(initialFingerprint, changedFingerprint);
    }

    @Test
    void fingerprintChangesWhenEarlySubmitChanges() {
        DisputeCase disputeCase = stripeCase("same_reason");

        String normal = service.fingerprint(disputeCase, List.of(singleFile()), false);
        String earlySubmit = service.fingerprint(disputeCase, List.of(singleFile()), true);

        assertNotEquals(normal, earlySubmit);
    }

    private DisputeCase stripeCase(String reasonCode) {
        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setPlatform(Platform.STRIPE);
        disputeCase.setProductScope(ProductScope.STRIPE_DISPUTE);
        disputeCase.setReasonCode(reasonCode);
        disputeCase.setState(CaseState.CASE_CREATED);
        return disputeCase;
    }

    private EvidenceFileInput singleFile() {
        return new EvidenceFileInput(
                EvidenceType.ORDER_RECEIPT,
                FileFormat.PDF,
                1000,
                2,
                false,
                true,
                false
        );
    }
}
