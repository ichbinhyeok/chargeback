package com.example.demo.dispute.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ValidateCaseRequest(
        @NotEmpty List<@Valid EvidenceFileInput> files,
        boolean earlySubmit
) {
}

