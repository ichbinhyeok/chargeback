package com.example.demo.dispute.web;

public record GuideProductProof(
        String title,
        String observedOn,
        String beforeResult,
        String afterResult,
        String workflowEvidence,
        boolean resolved,
        String previewImagePath,
        String previewDocumentPath,
        String previewCaption
) {
}
