package com.example.demo.dispute.api;

import java.util.List;

public record ValidateCaseResponse(
        boolean passed,
        List<ValidationIssueResponse> issues
) {
}

