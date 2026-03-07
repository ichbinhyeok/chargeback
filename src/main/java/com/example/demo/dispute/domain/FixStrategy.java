package com.example.demo.dispute.domain;

public enum FixStrategy {
    NONE,
    MERGE_PER_TYPE,
    COMPRESS_SHOPIFY_IMAGE_IF_IMAGE,
    COMPRESS_STRIPE_PDF,
    CONVERT_PDF_TO_PDFA,
    FLATTEN_PDF_PORTFOLIO,
    REMOVE_EXTERNAL_LINKS_PDF,
    MANUAL
}

