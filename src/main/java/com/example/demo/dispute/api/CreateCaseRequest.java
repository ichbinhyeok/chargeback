package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateCaseRequest(
        @NotNull Platform platform,
        @NotNull ProductScope productScope,
        String reasonCode,
        LocalDate dueAt,
        CardNetwork cardNetwork
) {
}

