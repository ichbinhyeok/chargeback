package com.example.demo.dispute.domain;

public enum ProductScope {
    STRIPE_DISPUTE(Platform.STRIPE),
    SHOPIFY_PAYMENTS_CHARGEBACK(Platform.SHOPIFY),
    SHOPIFY_CREDIT_DISPUTE(Platform.SHOPIFY);

    private final Platform platform;

    ProductScope(Platform platform) {
        this.platform = platform;
    }

    public Platform platform() {
        return platform;
    }

    public static boolean matchesPlatform(Platform platform, ProductScope productScope) {
        return productScope != null && productScope.platform() == platform;
    }
}

