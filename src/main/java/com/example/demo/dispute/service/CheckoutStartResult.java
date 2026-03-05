package com.example.demo.dispute.service;

public record CheckoutStartResult(
        String redirectUrl,
        boolean alreadyPaid
) {
}
