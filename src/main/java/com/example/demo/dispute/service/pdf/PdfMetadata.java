package com.example.demo.dispute.service.pdf;

public record PdfMetadata(
        int pageCount,
        boolean externalLinkDetected,
        boolean pdfACompliant,
        boolean pdfPortfolio
) {
}

