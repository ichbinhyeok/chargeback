package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.FixJobResponse;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.FixJobStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.persistence.FixJobEntity;
import com.example.demo.dispute.persistence.FixJobRepository;
import com.example.demo.dispute.service.pdf.PdfMetadata;
import com.example.demo.dispute.service.pdf.PdfMetadataExtractor;
import jakarta.persistence.EntityNotFoundException;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutoFixService {

    private static final String FAIL_CODE_NOTHING_TO_FIX = "ERR_FIX_NOTHING_TO_FIX";
    private static final String FAIL_CODE_INTERNAL = "ERR_FIX_INTERNAL";
    private static final long MB = 1024L * 1024L;
    private static final long SHOPIFY_FILE_LIMIT_BYTES = 2L * 1024L * 1024L;
    private static final long STRIPE_DEFAULT_TOTAL_SIZE_LIMIT_BYTES = (long) (4.5d * 1024d * 1024d);
    private static final long SHOPIFY_DEFAULT_TOTAL_SIZE_LIMIT_BYTES = 4L * MB;
    private static final long[] TOTAL_SIZE_IMAGE_TARGET_FLOORS = new long[] {
            SHOPIFY_FILE_LIMIT_BYTES,
            MB,
            512L * 1024L,
            256L * 1024L
    };
    private static final long MIN_TARGET_PDF_BYTES = 256L * 1024L;
    private static final long STRIPE_MIN_BYTES_PER_PAGE_AFTER_COMPRESSION = 4_000L;
    private static final int STRIPE_MIN_DPI = 115;
    private static final int[] STRIPE_COMPRESSION_DPIS = new int[] {170, 150, 130, 115};
    private static final float[] STRIPE_COMPRESSION_QUALITIES = new float[] {0.9f, 0.82f, 0.74f, 0.65f};
    private static final int STRIPE_TEXT_HEAVY_CHAR_THRESHOLD = 800;
    private static final int[] SHOPIFY_PDFA_DPIS = new int[] {170, 150, 130, 115};
    private static final float[] SHOPIFY_PDFA_QUALITIES = new float[] {0.9f, 0.82f, 0.74f, 0.65f};

    private final CaseService caseService;
    private final FixJobRepository fixJobRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceFileService evidenceFileService;
    private final ValidationService validationService;
    private final ValidationHistoryService validationHistoryService;
    private final AuditLogService auditLogService;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final PolicyCatalogService policyCatalogService;

    public AutoFixService(
            CaseService caseService,
            FixJobRepository fixJobRepository,
            EvidenceFileRepository evidenceFileRepository,
            EvidenceFileService evidenceFileService,
            ValidationService validationService,
            ValidationHistoryService validationHistoryService,
            AuditLogService auditLogService,
            PdfMetadataExtractor pdfMetadataExtractor,
            PolicyCatalogService policyCatalogService
    ) {
        this.caseService = caseService;
        this.fixJobRepository = fixJobRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceFileService = evidenceFileService;
        this.validationService = validationService;
        this.validationHistoryService = validationHistoryService;
        this.auditLogService = auditLogService;
        this.pdfMetadataExtractor = pdfMetadataExtractor;
        this.policyCatalogService = policyCatalogService;
    }

    public FixJobResponse requestAutoFix(UUID caseId) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        caseService.transitionState(disputeCase, CaseState.FIXING);

        FixJobEntity job = new FixJobEntity();
        job.setDisputeCase(disputeCase);
        job.setStatus(FixJobStatus.QUEUED);
        job.setSummary("Auto-fix job queued.");
        FixJobEntity saved = fixJobRepository.save(job);

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "AUTO_FIX_REQUESTED",
                "jobId=" + saved.getId()
        );

        FixJobEntity processed = processFixJob(saved.getId());
        return toResponse(processed);
    }

    @Transactional(readOnly = true)
    public FixJobResponse getFixJob(UUID caseId, UUID jobId) {
        caseService.getCase(caseId);
        FixJobEntity job = fixJobRepository.findByIdAndDisputeCaseId(jobId, caseId)
                .orElseThrow(() -> new EntityNotFoundException("fix job not found: " + jobId));
        return toResponse(job);
    }

    private FixJobEntity processFixJob(UUID jobId) {
        FixJobEntity job = fixJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("fix job not found: " + jobId));
        DisputeCase disputeCase = job.getDisputeCase();

        try {
            job.setStatus(FixJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            job.setSummary("Auto-fix in progress.");
            fixJobRepository.save(job);

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_STARTED",
                    "jobId=" + job.getId()
            );

            FixChange change = applySupportedFixes(disputeCase);
            if (change.isNoop()) {
                List<EvidenceFileInput> unchangedFiles = evidenceFileService.listAsValidationInputs(disputeCase.getId());
                ValidateCaseResponse unchangedValidation = validationService.validate(disputeCase, unchangedFiles, false);
                validationHistoryService.record(disputeCase, unchangedValidation, ValidationSource.AUTO_FIX, false, unchangedFiles);
                caseService.transitionState(disputeCase, unchangedValidation.passed() ? CaseState.READY : CaseState.BLOCKED);

                return failJob(
                        job,
                        FAIL_CODE_NOTHING_TO_FIX,
                        "No supported auto-fix issue found. Supported: merge multi-file-per-type, PDF blank/duplicate page"
                                + " cleanup, duplicate image removal, image total-size compression, Shopify oversized image"
                                + " compression, PDF external-link removal, Shopify PDF/A conversion + portfolio flattening,"
                                + " and platform PDF total-size compression."
                );
            }

            List<EvidenceFileInput> files = evidenceFileService.listAsValidationInputs(disputeCase.getId());
            ValidateCaseResponse response = validationService.validate(disputeCase, files, false);
            validationHistoryService.record(disputeCase, response, ValidationSource.AUTO_FIX, false, files);
            caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);

            job.setStatus(FixJobStatus.SUCCEEDED);
            job.setSummary(
                    fixSummary(change)
                            + " Merged " + change.mergedTypes() + " type(s), compressed " + change.compressedImages()
                            + " image(s), removed " + change.removedDuplicateImages()
                            + " duplicate image(s), compressed " + change.compressedPdfFiles()
                            + " PDF file(s), converted " + change.convertedPdfaFiles()
                            + " PDF/A file(s), flattened " + change.flattenedPortfolioFiles()
                            + " portfolio PDF file(s), sanitized " + change.linkSanitizedFiles()
                            + " PDF file(s). Validation passed=" + response.passed() + "."
            );
            job.setFailCode(null);
            job.setFailMessage(null);
            job.setFinishedAt(Instant.now());
            FixJobEntity saved = fixJobRepository.save(job);

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_COMPLETED",
                    "jobId=" + saved.getId() + ",mergedTypes=" + change.mergedTypes()
                            + ",removedPdfPages=" + change.pagesReduced()
                            + ",removedDuplicateImages=" + change.removedDuplicateImages()
                            + ",compressedImages=" + change.compressedImages()
                            + ",compressedPdfFiles=" + change.compressedPdfFiles()
                            + ",convertedPdfaFiles=" + change.convertedPdfaFiles()
                            + ",flattenedPortfolioFiles=" + change.flattenedPortfolioFiles()
                            + ",linkSanitizedFiles=" + change.linkSanitizedFiles()
                            + ",passed=" + response.passed()
            );
            return saved;
        } catch (RuntimeException ex) {
            caseService.transitionState(disputeCase, CaseState.BLOCKED);
            return failJob(job, FAIL_CODE_INTERNAL, "Auto-fix failed: " + ex.getMessage());
        }
    }

    private FixChange applySupportedFixes(DisputeCase disputeCase) {
        long totalBytesBefore = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToLong(EvidenceFileEntity::getSizeBytes)
                .sum();
        int totalPagesBefore = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToInt(EvidenceFileEntity::getPageCount)
                .sum();
        int mergedTypes = mergeFilesByEvidenceType(disputeCase);
        int reducedPdfPages = reducePdfPagesForLimits(disputeCase);
        int removedDuplicateImages = deduplicateImagesForTotalSize(disputeCase);
        int compressedImages = compressOversizedShopifyImages(disputeCase);
        compressedImages += compressImagesForTotalSize(disputeCase);
        ShopifyPdfFixChange shopifyPdfFix = normalizeShopifyPdfFiles(disputeCase);
        int compressedPdfFiles = compressPdfFilesForTotalSize(disputeCase);
        int linkSanitizedFiles = removeExternalLinksFromPdfFiles(disputeCase);
        long totalBytesAfter = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToLong(EvidenceFileEntity::getSizeBytes)
                .sum();
        int totalPagesAfter = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToInt(EvidenceFileEntity::getPageCount)
                .sum();
        return new FixChange(
                totalBytesBefore,
                totalBytesAfter,
                totalPagesBefore,
                totalPagesAfter,
                mergedTypes,
                reducedPdfPages,
                removedDuplicateImages,
                compressedImages,
                compressedPdfFiles,
                shopifyPdfFix.convertedPdfaFiles(),
                shopifyPdfFix.flattenedPortfolioFiles(),
                linkSanitizedFiles
        );
    }

    private ShopifyPdfFixChange normalizeShopifyPdfFiles(DisputeCase disputeCase) {
        if (disputeCase.getPlatform() != Platform.SHOPIFY
                || disputeCase.getProductScope() != ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK) {
            return new ShopifyPdfFixChange(0, 0);
        }

        int convertedPdfa = 0;
        int flattenedPortfolio = 0;
        List<EvidenceFileEntity> files = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId());
        for (EvidenceFileEntity file : files) {
            if (file.getFileFormat() != FileFormat.PDF) {
                continue;
            }
            if (file.isPdfACompliant() && !file.isPdfPortfolio()) {
                continue;
            }

            Path originalPath = Path.of(file.getStoragePath());
            Path normalizedPath = buildTargetPdfPath(file.getStoragePath());
            boolean converted = !file.isPdfACompliant();
            boolean flattened = file.isPdfPortfolio();

            byte[] normalizedBytes = normalizeShopifyPdfToPdfaBytes(originalPath, SHOPIFY_FILE_LIMIT_BYTES);
            if (normalizedBytes.length == 0) {
                continue;
            }

            try {
                Files.write(normalizedPath, normalizedBytes);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to write normalized Shopify PDF", ex);
            }

            PdfMetadata metadata = pdfMetadataExtractor.extract(normalizedPath);
            if (converted && !metadata.pdfACompliant()) {
                deletePathQuietly(normalizedPath);
                continue;
            }
            if (flattened && metadata.pdfPortfolio()) {
                deletePathQuietly(normalizedPath);
                continue;
            }

            long normalizedSize;
            try {
                normalizedSize = Files.size(normalizedPath);
            } catch (IOException ex) {
                deletePathQuietly(normalizedPath);
                throw new IllegalStateException("failed to read normalized Shopify PDF size", ex);
            }

            file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.pdf");
            file.setContentType("application/pdf");
            file.setStoragePath(normalizedPath.toString());
            file.setSizeBytes(normalizedSize);
            file.setPageCount(metadata.pageCount());
            file.setFileFormat(FileFormat.PDF);
            file.setPdfACompliant(metadata.pdfACompliant());
            file.setPdfPortfolio(metadata.pdfPortfolio());
            file.setExternalLinkDetected(metadata.externalLinkDetected());
            evidenceFileRepository.save(file);
            deletePathQuietly(originalPath);

            if (converted && metadata.pdfACompliant()) {
                convertedPdfa++;
            }
            if (flattened && !metadata.pdfPortfolio()) {
                flattenedPortfolio++;
            }

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_NORMALIZED_SHOPIFY_PDF",
                    "fileId=" + file.getId()
                            + ",convertedPdfa=" + converted
                            + ",flattenedPortfolio=" + flattened
                            + ",newSize=" + normalizedSize
            );
        }

        return new ShopifyPdfFixChange(convertedPdfa, flattenedPortfolio);
    }

    private int mergeFilesByEvidenceType(DisputeCase disputeCase) {
        UUID caseId = disputeCase.getId();
        List<EvidenceFileEntity> allFiles = evidenceFileRepository.findByDisputeCaseId(caseId);
        Map<EvidenceType, List<EvidenceFileEntity>> grouped = new EnumMap<>(EvidenceType.class);
        for (EvidenceFileEntity file : allFiles) {
            grouped.computeIfAbsent(file.getEvidenceType(), ignored -> new ArrayList<>()).add(file);
        }

        int mergedTypeCount = 0;
        for (Map.Entry<EvidenceType, List<EvidenceFileEntity>> entry : grouped.entrySet()) {
            EvidenceType type = entry.getKey();
            List<EvidenceFileEntity> filesByType = entry.getValue();
            if (filesByType.size() <= 1) {
                continue;
            }

            List<EvidenceFileEntity> ordered = filesByType.stream()
                    .sorted(Comparator.comparing(EvidenceFileEntity::getCreatedAt).thenComparing(EvidenceFileEntity::getId))
                    .toList();

            Path mergedPath = buildTargetPdfPath(ordered.get(ordered.size() - 1).getStoragePath());
            mergeEvidenceFilesToPdf(ordered, mergedPath);
            PdfMetadata metadata = pdfMetadataExtractor.extract(mergedPath);
            long mergedSize;
            try {
                mergedSize = Files.size(mergedPath);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to read merged file size", ex);
            }

            EvidenceFileEntity merged = new EvidenceFileEntity();
            merged.setDisputeCase(disputeCase);
            merged.setEvidenceType(type);
            merged.setOriginalName(type.name().toLowerCase(Locale.ROOT) + "_merged.pdf");
            merged.setContentType("application/pdf");
            merged.setStoragePath(mergedPath.toString());
            merged.setSizeBytes(mergedSize);
            merged.setPageCount(metadata.pageCount());
            merged.setFileFormat(FileFormat.PDF);
            merged.setPdfACompliant(metadata.pdfACompliant());
            merged.setPdfPortfolio(metadata.pdfPortfolio());
            merged.setExternalLinkDetected(metadata.externalLinkDetected());
            evidenceFileRepository.save(merged);

            for (EvidenceFileEntity existing : ordered) {
                deletePathQuietly(Path.of(existing.getStoragePath()));
                evidenceFileRepository.delete(existing);
            }

            mergedTypeCount++;
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_MERGED_TYPE",
                    "evidenceType=" + type + ",inputCount=" + ordered.size() + ",mergedFileId=" + merged.getId()
            );
        }

        return mergedTypeCount;
    }

    private int compressOversizedShopifyImages(DisputeCase disputeCase) {
        if (disputeCase.getPlatform() != Platform.SHOPIFY
                || disputeCase.getProductScope() != ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK) {
            return 0;
        }

        int compressed = 0;
        List<EvidenceFileEntity> files = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId());
        for (EvidenceFileEntity file : files) {
            if (!isImage(file.getFileFormat())) {
                continue;
            }
            if (file.getSizeBytes() <= SHOPIFY_FILE_LIMIT_BYTES) {
                continue;
            }

            Path originalPath = Path.of(file.getStoragePath());
            Path compressedPath = buildTargetJpegPath(file.getStoragePath());
            byte[] compressedBytes = compressImageToTargetSize(originalPath, SHOPIFY_FILE_LIMIT_BYTES);
            if (compressedBytes.length == 0 || compressedBytes.length >= file.getSizeBytes()) {
                continue;
            }
            if (compressedBytes.length > SHOPIFY_FILE_LIMIT_BYTES) {
                continue;
            }

            try {
                Files.write(compressedPath, compressedBytes);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to write compressed image", ex);
            }

            file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.jpg");
            file.setContentType("image/jpeg");
            file.setStoragePath(compressedPath.toString());
            file.setSizeBytes(compressedBytes.length);
            file.setPageCount(1);
            file.setFileFormat(FileFormat.JPEG);
            file.setPdfACompliant(true);
            file.setPdfPortfolio(false);
            file.setExternalLinkDetected(false);
            evidenceFileRepository.save(file);
            deletePathQuietly(originalPath);

            compressed++;
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_COMPRESSED_IMAGE",
                    "fileId=" + file.getId() + ",newSize=" + compressedBytes.length
            );
        }

        return compressed;
    }

    private int compressImagesForTotalSize(DisputeCase disputeCase) {
        long totalLimit = resolveTotalSizeLimitBytes(disputeCase);
        if (totalLimit <= 0) {
            return 0;
        }

        long totalSize = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToLong(EvidenceFileEntity::getSizeBytes)
                .sum();
        if (totalSize <= totalLimit) {
            return 0;
        }

        int compressed = 0;
        for (long floorBytes : TOTAL_SIZE_IMAGE_TARGET_FLOORS) {
            if (totalSize <= totalLimit) {
                break;
            }

            List<EvidenceFileEntity> imageFiles = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                    .filter(file -> isImage(file.getFileFormat()))
                    .sorted(Comparator.comparingLong(EvidenceFileEntity::getSizeBytes).reversed())
                    .toList();

            for (EvidenceFileEntity file : imageFiles) {
                if (totalSize <= totalLimit) {
                    break;
                }

                long neededReduction = totalSize - totalLimit;
                long preferredMaxBytes = Math.max(floorBytes, file.getSizeBytes() - neededReduction);
                if (preferredMaxBytes >= file.getSizeBytes()) {
                    continue;
                }

                Path originalPath = Path.of(file.getStoragePath());
                Path compressedPath = buildTargetJpegPath(file.getStoragePath());
                byte[] compressedBytes = compressImageToTargetSize(originalPath, preferredMaxBytes);
                if (compressedBytes.length == 0 || compressedBytes.length >= file.getSizeBytes()) {
                    continue;
                }
                if (disputeCase.getPlatform() == Platform.SHOPIFY
                        && disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK
                        && compressedBytes.length > SHOPIFY_FILE_LIMIT_BYTES) {
                    continue;
                }

                long originalSize = file.getSizeBytes();
                try {
                    Files.write(compressedPath, compressedBytes);
                } catch (IOException ex) {
                    throw new IllegalStateException("failed to write total-size compressed image", ex);
                }

                file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.jpg");
                file.setContentType("image/jpeg");
                file.setStoragePath(compressedPath.toString());
                file.setSizeBytes(compressedBytes.length);
                file.setPageCount(1);
                file.setFileFormat(FileFormat.JPEG);
                file.setPdfACompliant(true);
                file.setPdfPortfolio(false);
                file.setExternalLinkDetected(false);
                evidenceFileRepository.save(file);
                deletePathQuietly(originalPath);

                totalSize -= (originalSize - compressedBytes.length);
                compressed++;
                auditLogService.log(
                        disputeCase,
                        "SYSTEM",
                        "AUTO_FIX_COMPRESSED_IMAGE_TOTAL_SIZE",
                        "fileId=" + file.getId() + ",newSize=" + compressedBytes.length + ",remainingTotal=" + totalSize
                );
            }
        }

        return compressed;
    }

    private int deduplicateImagesForTotalSize(DisputeCase disputeCase) {
        long totalLimit = resolveTotalSizeLimitBytes(disputeCase);
        if (totalLimit <= 0) {
            return 0;
        }

        long totalSize = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToLong(EvidenceFileEntity::getSizeBytes)
                .sum();
        if (totalSize <= totalLimit) {
            return 0;
        }

        int removed = 0;
        Map<EvidenceType, Map<String, UUID>> seenByType = new EnumMap<>(EvidenceType.class);
        List<EvidenceFileEntity> imageFiles = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .filter(file -> isImage(file.getFileFormat()))
                .sorted(Comparator.comparing(EvidenceFileEntity::getCreatedAt).thenComparing(EvidenceFileEntity::getId))
                .toList();

        for (EvidenceFileEntity file : imageFiles) {
            if (totalSize <= totalLimit) {
                break;
            }

            String fingerprint = fingerprintImage(Path.of(file.getStoragePath()));
            if (fingerprint == null || fingerprint.isBlank()) {
                continue;
            }

            Map<String, UUID> seenForType = seenByType.computeIfAbsent(file.getEvidenceType(), ignored -> new HashMap<>());
            UUID keptFileId = seenForType.putIfAbsent(fingerprint, file.getId());
            if (keptFileId == null) {
                continue;
            }

            long originalSize = file.getSizeBytes();
            deletePathQuietly(Path.of(file.getStoragePath()));
            evidenceFileRepository.delete(file);
            totalSize -= originalSize;
            removed++;

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_REMOVED_DUPLICATE_IMAGE",
                    "fileId=" + file.getId()
                            + ",keptFileId=" + keptFileId
                            + ",evidenceType=" + file.getEvidenceType()
                            + ",remainingTotal=" + totalSize
            );
        }

        return removed;
    }

    private int compressPdfFilesForTotalSize(DisputeCase disputeCase) {
        long totalSize = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .mapToLong(EvidenceFileEntity::getSizeBytes)
                .sum();
        long totalLimit = resolveTotalSizeLimitBytes(disputeCase);
        if (totalSize <= totalLimit) {
            return 0;
        }

        int compressed = 0;
        List<EvidenceFileEntity> pdfFiles = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .filter(file -> file.getFileFormat() == FileFormat.PDF)
                .sorted(Comparator.comparingLong(EvidenceFileEntity::getSizeBytes).reversed())
                .toList();

        for (EvidenceFileEntity file : pdfFiles) {
            if (totalSize <= totalLimit) {
                break;
            }

            long neededReduction = totalSize - totalLimit;
            long preferredMaxBytes = Math.max(MIN_TARGET_PDF_BYTES, file.getSizeBytes() - neededReduction);

            Path originalPath = Path.of(file.getStoragePath());
            if (isTextHeavyPdf(originalPath)) {
                auditLogService.log(
                        disputeCase,
                        "SYSTEM",
                        "AUTO_FIX_SKIPPED_PDF_TEXT_HEAVY",
                        "fileId=" + file.getId()
                );
                continue;
            }

            Path compressedPath = buildTargetPdfPath(file.getStoragePath());
            byte[] compressedBytes = compressPdfForTotalSize(disputeCase, originalPath, preferredMaxBytes);
            if (compressedBytes.length == 0 || compressedBytes.length >= file.getSizeBytes()) {
                continue;
            }
            long originalSize = file.getSizeBytes();

            try {
                Files.write(compressedPath, compressedBytes);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to write compressed Stripe PDF", ex);
            }

            PdfMetadata metadata = pdfMetadataExtractor.extract(compressedPath);
            file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.pdf");
            file.setContentType("application/pdf");
            file.setStoragePath(compressedPath.toString());
            file.setSizeBytes(compressedBytes.length);
            file.setPageCount(metadata.pageCount());
            file.setFileFormat(FileFormat.PDF);
            file.setPdfACompliant(metadata.pdfACompliant());
            file.setPdfPortfolio(metadata.pdfPortfolio());
            file.setExternalLinkDetected(metadata.externalLinkDetected());
            evidenceFileRepository.save(file);
            deletePathQuietly(originalPath);

            totalSize -= (originalSize - compressedBytes.length);
            compressed++;

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_COMPRESSED_PDF_TOTAL_SIZE",
                    "fileId=" + file.getId() + ",newSize=" + compressedBytes.length + ",remainingTotal=" + totalSize
            );
        }

        return compressed;
    }

    private byte[] compressPdfForTotalSize(DisputeCase disputeCase, Path sourcePath, long preferredMaxBytes) {
        if (disputeCase.getPlatform() == Platform.SHOPIFY
                && disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK) {
            return normalizeShopifyPdfToPdfaBytes(sourcePath, preferredMaxBytes);
        }
        return compressPdfToSmallerBytes(sourcePath, preferredMaxBytes);
    }

    private long resolveTotalSizeLimitBytes(DisputeCase disputeCase) {
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork()
        );
        if (policy == null || policy.totalSizeLimitBytes() == null || policy.totalSizeLimitBytes() <= 0) {
            return switch (disputeCase.getPlatform()) {
                case STRIPE -> STRIPE_DEFAULT_TOTAL_SIZE_LIMIT_BYTES;
                case SHOPIFY -> disputeCase.getProductScope() == ProductScope.SHOPIFY_CREDIT_DISPUTE
                        ? STRIPE_DEFAULT_TOTAL_SIZE_LIMIT_BYTES
                        : SHOPIFY_DEFAULT_TOTAL_SIZE_LIMIT_BYTES;
            };
        }
        return policy.totalSizeLimitBytes();
    }

    private byte[] compressPdfToSmallerBytes(Path sourcePath, long preferredMaxBytes) {
        int pageCount = readPdfPageCount(sourcePath);
        byte[] bestGuarded = new byte[0];

        for (int dpi : STRIPE_COMPRESSION_DPIS) {
            for (float quality : STRIPE_COMPRESSION_QUALITIES) {
                byte[] candidate = renderPdfAsImagePdf(sourcePath, dpi, quality);
                if (candidate.length == 0) {
                    continue;
                }
                if (!passesStripeCompressionGuard(pageCount, dpi, candidate.length)) {
                    continue;
                }
                if (bestGuarded.length == 0 || candidate.length < bestGuarded.length) {
                    bestGuarded = candidate;
                }
                if (candidate.length <= preferredMaxBytes) {
                    return candidate;
                }
            }
        }

        return bestGuarded.length > 0 ? bestGuarded : new byte[0];
    }

    private byte[] renderPdfAsImagePdf(Path sourcePath, int dpi, float jpegQuality) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile()); PDDocument compressed = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                BufferedImage rendered = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                PDRectangle sourceBox = source.getPage(i).getMediaBox();
                float width = sourceBox != null && sourceBox.getWidth() > 0
                        ? sourceBox.getWidth()
                        : (float) rendered.getWidth() * 72f / dpi;
                float height = sourceBox != null && sourceBox.getHeight() > 0
                        ? sourceBox.getHeight()
                        : (float) rendered.getHeight() * 72f / dpi;

                PDPage page = new PDPage(new PDRectangle(width, height));
                compressed.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(compressed, rendered, jpegQuality);
                try (PDPageContentStream content = new PDPageContentStream(compressed, page)) {
                    content.drawImage(image, 0, 0, width, height);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            compressed.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private byte[] normalizeShopifyPdfToPdfaBytes(Path sourcePath, long preferredMaxBytes) {
        byte[] best = new byte[0];

        for (int dpi : SHOPIFY_PDFA_DPIS) {
            for (float quality : SHOPIFY_PDFA_QUALITIES) {
                byte[] candidate = renderPdfAsPdfABytes(sourcePath, dpi, quality);
                if (candidate.length == 0) {
                    continue;
                }
                if (best.length == 0 || candidate.length < best.length) {
                    best = candidate;
                }
                if (preferredMaxBytes > 0 && candidate.length <= preferredMaxBytes) {
                    return candidate;
                }
            }
        }

        return best;
    }

    private byte[] renderPdfAsPdfABytes(Path sourcePath, int dpi, float jpegQuality) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile()); PDDocument normalized = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                BufferedImage rendered = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                PDRectangle sourceBox = source.getPage(i).getMediaBox();
                float width = sourceBox != null && sourceBox.getWidth() > 0
                        ? sourceBox.getWidth()
                        : (float) rendered.getWidth() * 72f / dpi;
                float height = sourceBox != null && sourceBox.getHeight() > 0
                        ? sourceBox.getHeight()
                        : (float) rendered.getHeight() * 72f / dpi;

                PDPage page = new PDPage(new PDRectangle(width, height));
                normalized.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(normalized, rendered, jpegQuality);
                try (PDPageContentStream content = new PDPageContentStream(normalized, page)) {
                    content.drawImage(image, 0, 0, width, height);
                }
            }

            applyPdfaProfile(normalized);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            normalized.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private void applyPdfaProfile(PDDocument document) throws IOException {
        String xmp = """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                      xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
                      xmlns:cb="urn:chargeback:autofix"
                      pdfaid:part="1"
                      pdfaid:conformance="B"
                      cb:marker="autofix_pdfa_candidate"/>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """;
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        document.getDocumentCatalog().setMetadata(metadata);

        ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        PDOutputIntent outputIntent = new PDOutputIntent(document, new ByteArrayInputStream(profile.getData()));
        outputIntent.setInfo("sRGB IEC61966-2.1");
        outputIntent.setOutputCondition("sRGB IEC61966-2.1");
        outputIntent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        outputIntent.setRegistryName("https://www.color.org");
        document.getDocumentCatalog().addOutputIntent(outputIntent);
    }

    private int readPdfPageCount(Path sourcePath) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile())) {
            return Math.max(1, source.getNumberOfPages());
        } catch (IOException ex) {
            return 1;
        }
    }

    private boolean isTextHeavyPdf(Path sourcePath) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(source);
            if (text == null) {
                return false;
            }
            return text.replaceAll("\\s+", "").length() >= STRIPE_TEXT_HEAVY_CHAR_THRESHOLD;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean passesStripeCompressionGuard(int pageCount, int dpi, long sizeBytes) {
        if (dpi < STRIPE_MIN_DPI) {
            return false;
        }
        long perPageBytes = sizeBytes / Math.max(1, pageCount);
        return perPageBytes >= STRIPE_MIN_BYTES_PER_PAGE_AFTER_COMPRESSION;
    }

    private int reducePdfPagesForLimits(DisputeCase disputeCase) {
        int pageLimit = resolvePageLimit(disputeCase);
        if (pageLimit <= 0) {
            return 0;
        }

        List<EvidenceFileEntity> pdfFiles = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId()).stream()
                .filter(file -> file.getFileFormat() == FileFormat.PDF)
                .sorted(Comparator.comparingInt(EvidenceFileEntity::getPageCount).reversed())
                .toList();
        if (pdfFiles.isEmpty()) {
            return 0;
        }

        boolean stripeTotalLimit = disputeCase.getPlatform() == Platform.STRIPE;
        boolean shopifyPerFileLimit = disputeCase.getPlatform() == Platform.SHOPIFY;
        int totalPages = pdfFiles.stream().mapToInt(EvidenceFileEntity::getPageCount).sum();
        int reducedPages = 0;

        for (EvidenceFileEntity file : pdfFiles) {
            if (stripeTotalLimit && totalPages <= pageLimit) {
                break;
            }
            if (!stripeTotalLimit && !needsShopifyPageReduction(file)) {
                continue;
            }

            Path originalPath = Path.of(file.getStoragePath());
            PdfPageReductionChange reduction = removeBlankAndDuplicatePdfPages(originalPath);
            if (!reduction.changed()) {
                continue;
            }

            Path reducedPath = buildTargetPdfPath(file.getStoragePath());
            try {
                Files.write(reducedPath, reduction.pdfBytes());
            } catch (IOException ex) {
                throw new IllegalStateException("failed to write reduced-page PDF", ex);
            }

            PdfMetadata metadata = pdfMetadataExtractor.extract(reducedPath);
            long reducedSize;
            try {
                reducedSize = Files.size(reducedPath);
            } catch (IOException ex) {
                deletePathQuietly(reducedPath);
                throw new IllegalStateException("failed to read reduced-page PDF size", ex);
            }

            long originalSize = file.getSizeBytes();
            int originalPages = file.getPageCount();
            file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.pdf");
            file.setContentType("application/pdf");
            file.setStoragePath(reducedPath.toString());
            file.setSizeBytes(reducedSize);
            file.setPageCount(metadata.pageCount());
            file.setFileFormat(FileFormat.PDF);
            file.setPdfACompliant(metadata.pdfACompliant());
            file.setPdfPortfolio(metadata.pdfPortfolio());
            file.setExternalLinkDetected(metadata.externalLinkDetected());
            evidenceFileRepository.save(file);
            deletePathQuietly(originalPath);

            int removedFromFile = Math.max(0, originalPages - metadata.pageCount());
            totalPages -= removedFromFile;
            reducedPages += removedFromFile;

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_REDUCED_PDF_PAGES",
                    "fileId=" + file.getId()
                            + ",pagesBefore=" + originalPages
                            + ",pagesAfter=" + metadata.pageCount()
                            + ",removedBlankPages=" + reduction.removedBlankPages()
                            + ",removedDuplicatePages=" + reduction.removedDuplicatePages()
                            + ",sizeBefore=" + originalSize
                            + ",sizeAfter=" + reducedSize
            );

            if (shopifyPerFileLimit && !needsShopifyPageReduction(file) && !stripeTotalLimit) {
                continue;
            }
        }

        return reducedPages;
    }

    private int resolvePageLimit(DisputeCase disputeCase) {
        if (disputeCase.getPlatform() == Platform.STRIPE) {
            return disputeCase.getCardNetwork() == CardNetwork.MASTERCARD ? 19 : 49;
        }
        if (disputeCase.getPlatform() == Platform.SHOPIFY) {
            return 49;
        }
        return 0;
    }

    private boolean needsShopifyPageReduction(EvidenceFileEntity file) {
        return file.getFileFormat() == FileFormat.PDF && file.getPageCount() >= 50;
    }

    private PdfPageReductionChange removeBlankAndDuplicatePdfPages(Path sourcePath) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile()); PDDocument reduced = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            Map<String, Integer> seenFingerprints = new HashMap<>();
            int removedBlankPages = 0;
            int removedDuplicatePages = 0;

            for (int index = 0; index < source.getNumberOfPages(); index++) {
                PageFingerprint fingerprint = fingerprintPage(source, renderer, index);
                if (fingerprint.blank()) {
                    removedBlankPages++;
                    continue;
                }
                if (seenFingerprints.containsKey(fingerprint.value())) {
                    removedDuplicatePages++;
                    continue;
                }
                seenFingerprints.put(fingerprint.value(), index);
                reduced.importPage(source.getPage(index));
            }

            if (reduced.getNumberOfPages() == 0 && source.getNumberOfPages() > 0) {
                reduced.importPage(source.getPage(0));
                removedBlankPages = Math.max(0, removedBlankPages - 1);
            }
            if (reduced.getNumberOfPages() == source.getNumberOfPages()) {
                return PdfPageReductionChange.noChange(source.getNumberOfPages());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            reduced.save(out);
            return new PdfPageReductionChange(
                    out.toByteArray(),
                    source.getNumberOfPages(),
                    reduced.getNumberOfPages(),
                    removedBlankPages,
                    removedDuplicatePages
            );
        } catch (IOException ex) {
            throw new IllegalStateException("failed to reduce duplicate or blank PDF pages", ex);
        }
    }

    private PageFingerprint fingerprintPage(PDDocument document, PDFRenderer renderer, int pageIndex) throws IOException {
        String normalizedText = normalizePageText(extractSinglePageText(document, pageIndex));
        if (!normalizedText.isBlank()) {
            return new PageFingerprint(false, "text:" + normalizedText);
        }

        BufferedImage rendered = renderer.renderImageWithDPI(pageIndex, 72, ImageType.GRAY);
        if (isVisuallyBlank(rendered)) {
            return new PageFingerprint(true, "blank");
        }
        return new PageFingerprint(false, "image:" + hashRenderedPage(rendered));
    }

    private String extractSinglePageText(PDDocument document, int pageIndex) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        String text = stripper.getText(document);
        return text == null ? "" : text;
    }

    private String normalizePageText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private boolean isVisuallyBlank(BufferedImage image) {
        int nonWhiteSamples = 0;
        int totalSamples = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int gray = image.getRGB(x, y) & 0xFF;
                totalSamples++;
                if (gray < 245) {
                    nonWhiteSamples++;
                }
            }
        }
        return nonWhiteSamples <= Math.max(12, totalSamples / 500);
    }

    private String hashRenderedPage(BufferedImage image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int y = 0; y < image.getHeight(); y += 2) {
                for (int x = 0; x < image.getWidth(); x += 2) {
                    int gray = image.getRGB(x, y) & 0xFF;
                    digest.update((byte) gray);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("missing SHA-256 support", ex);
        }
    }

    private int removeExternalLinksFromPdfFiles(DisputeCase disputeCase) {
        int sanitized = 0;
        List<EvidenceFileEntity> files = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId());
        for (EvidenceFileEntity file : files) {
            if (file.getFileFormat() != FileFormat.PDF || !file.isExternalLinkDetected()) {
                continue;
            }

            Path originalPath = Path.of(file.getStoragePath());
            Path sanitizedPath = buildTargetPdfPath(file.getStoragePath());
            if (!stripExternalLinks(originalPath, sanitizedPath)) {
                continue;
            }

            PdfMetadata metadata = pdfMetadataExtractor.extract(sanitizedPath);
            if (metadata.externalLinkDetected()) {
                deletePathQuietly(sanitizedPath);
                continue;
            }

            long sanitizedSize;
            try {
                sanitizedSize = Files.size(sanitizedPath);
            } catch (IOException ex) {
                deletePathQuietly(sanitizedPath);
                throw new IllegalStateException("failed to read sanitized file size", ex);
            }

            file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.pdf");
            file.setContentType("application/pdf");
            file.setStoragePath(sanitizedPath.toString());
            file.setSizeBytes(sanitizedSize);
            file.setPageCount(metadata.pageCount());
            file.setFileFormat(FileFormat.PDF);
            file.setPdfACompliant(metadata.pdfACompliant());
            file.setPdfPortfolio(metadata.pdfPortfolio());
            file.setExternalLinkDetected(metadata.externalLinkDetected());
            evidenceFileRepository.save(file);
            deletePathQuietly(originalPath);

            sanitized++;
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_SANITIZED_EXTERNAL_LINKS",
                    "fileId=" + file.getId() + ",newSize=" + sanitizedSize
            );
        }
        return sanitized;
    }

    private boolean stripExternalLinks(Path sourcePath, Path targetPath) {
        try (PDDocument document = Loader.loadPDF(sourcePath.toFile())) {
            boolean changed = false;
            for (PDPage page : document.getPages()) {
                List<PDAnnotation> annotations = page.getAnnotations();
                int before = annotations.size();
                annotations.removeIf(this::isExternalLinkAnnotation);
                if (before != annotations.size()) {
                    page.setAnnotations(annotations);
                    changed = true;
                }
            }

            if (!changed) {
                return false;
            }

            Files.createDirectories(targetPath.getParent());
            document.save(targetPath.toFile());
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to sanitize PDF external links", ex);
        }
    }

    private boolean isExternalLinkAnnotation(PDAnnotation annotation) {
        if (annotation == null) {
            return false;
        }
        if ("Link".equalsIgnoreCase(annotation.getSubtype())) {
            return true;
        }
        COSDictionary dictionary = annotation.getCOSObject();
        if (dictionary == null) {
            return false;
        }
        if (dictionary.containsKey(COSName.URI)) {
            return true;
        }
        COSBase action = dictionary.getDictionaryObject(COSName.A);
        if (action instanceof COSDictionary actionDictionary && actionDictionary.containsKey(COSName.URI)) {
            return true;
        }
        return false;
    }

    private void mergeEvidenceFilesToPdf(List<EvidenceFileEntity> files, Path targetPath) {
        try (PDDocument merged = new PDDocument()) {
            for (EvidenceFileEntity file : files) {
                Path sourcePath = Path.of(file.getStoragePath());
                if (file.getFileFormat() == FileFormat.PDF) {
                    try (PDDocument source = Loader.loadPDF(sourcePath.toFile())) {
                        for (PDPage page : source.getPages()) {
                            merged.importPage(page);
                        }
                    }
                } else if (isImage(file.getFileFormat())) {
                    appendImageAsPage(merged, sourcePath);
                }
            }
            Files.createDirectories(targetPath.getParent());
            merged.save(targetPath.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to merge files into PDF", ex);
        }
    }

    private void appendImageAsPage(PDDocument merged, Path imagePath) throws IOException {
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IllegalStateException("unsupported image content for file: " + imagePath);
        }

        PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
        merged.addPage(page);

        PDImageXObject pdImage = JPEGFactory.createFromImage(merged, image, 0.9f);
        try (PDPageContentStream content = new PDPageContentStream(merged, page)) {
            content.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
        }
    }

    private byte[] compressImageToTargetSize(Path sourcePath, long maxBytes) {
        BufferedImage original;
        try {
            original = ImageIO.read(sourcePath.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read image for compression", ex);
        }
        if (original == null) {
            throw new IllegalStateException("unsupported image file: " + sourcePath);
        }

        float[] scales = new float[] {1.0f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f};
        float[] qualities = new float[] {0.9f, 0.75f, 0.6f, 0.45f, 0.3f, 0.22f};
        byte[] best = null;

        for (float scale : scales) {
            BufferedImage scaled = scaleImage(original, scale);
            for (float quality : qualities) {
                byte[] candidate = writeJpeg(scaled, quality);
                if (best == null || candidate.length < best.length) {
                    best = candidate;
                }
                if (candidate.length <= maxBytes) {
                    return candidate;
                }
            }
        }

        return best == null ? new byte[0] : best;
    }

    private String fingerprintImage(Path sourcePath) {
        try {
            BufferedImage image = ImageIO.read(sourcePath.toFile());
            if (image == null) {
                return null;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigestInt(digest, image.getWidth());
            updateDigestInt(digest, image.getHeight());
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    updateDigestInt(digest, image.getRGB(x, y));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            return null;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("missing SHA-256 support", ex);
        }
    }

    private void updateDigestInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private byte[] writeJpeg(BufferedImage image, float quality) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to encode JPEG", ex);
        }
    }

    private BufferedImage scaleImage(BufferedImage source, float scale) {
        int targetWidth = Math.max(1, Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private boolean isImage(FileFormat fileFormat) {
        return fileFormat == FileFormat.JPEG || fileFormat == FileFormat.PNG;
    }

    private Path buildTargetPdfPath(String referencePath) {
        Path parent = Path.of(referencePath).getParent();
        return parent.resolve(UUID.randomUUID() + "_merged.pdf");
    }

    private Path buildTargetJpegPath(String referencePath) {
        Path parent = Path.of(referencePath).getParent();
        return parent.resolve(UUID.randomUUID() + "_compressed.jpg");
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "file";
        }
        int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    private String fixSummary(FixChange change) {
        long reducedBytes = change.bytesReduced();
        int reducedPages = change.pagesReduced();
        if (reducedBytes <= 0 && reducedPages <= 0) {
            return "Auto-fix updated supported blockers without reducing total pack size or page count.";
        }
        if (reducedBytes > 0 && reducedPages > 0) {
            return "Reduced pack by " + formatBytesLabel(reducedBytes)
                    + " (" + formatBytesLabel(change.totalBytesBefore()) + " -> " + formatBytesLabel(change.totalBytesAfter()) + ")"
                    + " and removed " + reducedPages + " page(s) (" + change.totalPagesBefore() + " -> "
                    + change.totalPagesAfter() + ").";
        }
        if (reducedBytes > 0) {
            return "Reduced pack by " + formatBytesLabel(reducedBytes)
                    + " (" + formatBytesLabel(change.totalBytesBefore()) + " -> " + formatBytesLabel(change.totalBytesAfter()) + ").";
        }
        return "Removed " + reducedPages + " page(s) (" + change.totalPagesBefore() + " -> " + change.totalPagesAfter() + ").";
    }

    private String formatBytesLabel(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytes / (double) MB);
    }

    private FixJobEntity failJob(FixJobEntity job, String failCode, String failMessage) {
        DisputeCase disputeCase = job.getDisputeCase();
        job.setStatus(FixJobStatus.FAILED);
        job.setSummary("Auto-fix failed.");
        job.setFailCode(failCode);
        job.setFailMessage(failMessage);
        job.setFinishedAt(Instant.now());
        FixJobEntity saved = fixJobRepository.save(job);

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "AUTO_FIX_FAILED",
                "jobId=" + saved.getId() + ",failCode=" + failCode
        );
        return saved;
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Ignore cleanup failures; retention sweep handles leftovers.
        }
    }

    private FixJobResponse toResponse(FixJobEntity job) {
        return new FixJobResponse(
                job.getId(),
                job.getDisputeCase().getId(),
                job.getStatus(),
                job.getSummary(),
                job.getFailCode(),
                job.getFailMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }

    private record FixChange(
            long totalBytesBefore,
            long totalBytesAfter,
            int totalPagesBefore,
            int totalPagesAfter,
            int mergedTypes,
            int reducedPdfPages,
            int removedDuplicateImages,
            int compressedImages,
            int compressedPdfFiles,
            int convertedPdfaFiles,
            int flattenedPortfolioFiles,
            int linkSanitizedFiles
    ) {
        long bytesReduced() {
            return Math.max(0L, totalBytesBefore - totalBytesAfter);
        }

        int pagesReduced() {
            return Math.max(0, totalPagesBefore - totalPagesAfter);
        }

        boolean isNoop() {
            return mergedTypes <= 0
                    && reducedPdfPages <= 0
                    && removedDuplicateImages <= 0
                    && compressedImages <= 0
                    && compressedPdfFiles <= 0
                    && convertedPdfaFiles <= 0
                    && flattenedPortfolioFiles <= 0
                    && linkSanitizedFiles <= 0;
        }
    }

    private record PdfPageReductionChange(
            byte[] pdfBytes,
            int originalPageCount,
            int reducedPageCount,
            int removedBlankPages,
            int removedDuplicatePages
    ) {
        static PdfPageReductionChange noChange(int pageCount) {
            return new PdfPageReductionChange(new byte[0], pageCount, pageCount, 0, 0);
        }

        boolean changed() {
            return reducedPageCount < originalPageCount;
        }
    }

    private record PageFingerprint(boolean blank, String value) {
    }

    private record ShopifyPdfFixChange(int convertedPdfaFiles, int flattenedPortfolioFiles) {
    }
}
