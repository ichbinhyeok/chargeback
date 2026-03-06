package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.CaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CaseServiceStateTransitionIntegrationTest {

    @Autowired
    private CaseService caseService;

    @Test
    void rejectsIllegalStateTransition() {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "illegal_transition",
                null,
                null
        ));

        try {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> caseService.transitionState(disputeCase, CaseState.PAID)
            );
            assertTrue(ex.getMessage().contains("illegal state transition"));
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void allowsExpectedStateProgressionForExportFlow() {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "valid_transition",
                null,
                null
        ));

        try {
            disputeCase = caseService.transitionState(disputeCase, CaseState.UPLOADING);
            disputeCase = caseService.transitionState(disputeCase, CaseState.VALIDATING);
            disputeCase = caseService.transitionState(disputeCase, CaseState.READY);
            disputeCase = caseService.transitionState(disputeCase, CaseState.PAID);
            disputeCase = caseService.transitionState(disputeCase, CaseState.DOWNLOADED);
            disputeCase = caseService.transitionState(disputeCase, CaseState.ARCHIVED);

            assertEquals(CaseState.ARCHIVED, disputeCase.getState());
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }
}

