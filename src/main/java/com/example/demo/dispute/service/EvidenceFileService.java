package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.ReclassifyFileRequest;
import com.example.demo.dispute.api.UploadFileResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.service.pdf.PdfMetadata;
import com.example.demo.dispute.service.pdf.PdfMetadataExtractor;
import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class EvidenceFileService {

    private static final long MIN_MANUAL_COMPRESS_TARGET_BYTES = 96L * 1024L;
    private static final long MIN_MANUAL_COMPRESS_SAVINGS_BYTES = 32L * 1024L;
    private static final long MIN_MANUAL_PDF_COMPRESS_SAVINGS_BYTES = 64L * 1024L;

    private final CaseService caseService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final AuditLogService auditLogService;
    private final DocumentNormalizationService documentNormalizationService;
    private final Path storageRoot;
    private final int caseMaxFiles;

    public EvidenceFileService(
            CaseService caseService,
            EvidenceFileRepository evidenceFileRepository,
            PdfMetadataExtractor pdfMetadataExtractor,
            AuditLogService auditLogService,
            DocumentNormalizationService documentNormalizationService,
            @Value("${app.storage.root:./data/evidence}") String storageRoot,
            @Value("${app.case.max-files:100}") int caseMaxFiles
    ) {
        this.caseService = caseService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.pdfMetadataExtractor = pdfMetadataExtractor;
        this.auditLogService = auditLogService;
        this.documentNormalizationService = documentNormalizationService;
        this.storageRoot = Path.of(storageRoot);
        this.caseMaxFiles = caseMaxFiles;
    }

    public UploadFileResponse upload(UUID caseId, EvidenceType evidenceType, MultipartFile file) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        ensureUploadingAllowed(disputeCase);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file is empty");
        }
        long uploadedFileCount = evidenceFileRepository.countByDisputeCaseId(caseId);
        if (uploadedFileCount >= caseMaxFiles) {
            throw new IllegalArgumentException(
                    "file limit reached for this case (" + caseMaxFiles + "). Delete unused files before uploading more."
            );
        }

        StoredUpload storedUpload = storeUpload(disputeCase.getId(), file);
        FileFormat format = storedUpload.format();
        Path savedPath = storedUpload.savedPath();

        try {
            PdfMetadata pdfMetadata = inspectStoredUpload(format, savedPath);

            EvidenceFileEntity entity = new EvidenceFileEntity();
            entity.setDisputeCase(disputeCase);
            entity.setEvidenceType(evidenceType);
            entity.setOriginalName(storedUpload.storedOriginalName());
            entity.setContentType(storedUpload.contentType());
            entity.setStoragePath(savedPath.toString());
            entity.setSizeBytes(storedUpload.sizeBytes());
            entity.setPageCount(pdfMetadata.pageCount());
            entity.setFileFormat(format);
            entity.setPdfACompliant(pdfMetadata.pdfACompliant());
            entity.setPdfPortfolio(pdfMetadata.pdfPortfolio());
            entity.setExternalLinkDetected(pdfMetadata.externalLinkDetected());

            EvidenceFileEntity saved = evidenceFileRepository.save(entity);
            if (disputeCase.getState() != CaseState.UPLOADING) {
                caseService.transitionState(disputeCase, CaseState.UPLOADING);
            }
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "FILE_UPLOADED",
                    "fileId=" + saved.getId()
                            + ",evidenceType=" + saved.getEvidenceType()
                            + ",format=" + saved.getFileFormat()
                            + ",autoConverted=" + storedUpload.autoConverted()
                            + (storedUpload.sourceContentType() == null ? "" : ",sourceContentType=" + storedUpload.sourceContentType())
            );

            return toUploadResponse(saved);
        } catch (RuntimeException ex) {
            deleteStoredFileQuietly(savedPath);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<EvidenceFileInput> listAsValidationInputs(UUID caseId) {
        caseService.getCase(caseId);
        return evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(file -> new EvidenceFileInput(
                        file.getEvidenceType(),
                        file.getFileFormat(),
                        file.getSizeBytes(),
                        file.getPageCount(),
                        file.isExternalLinkDetected(),
                        file.isPdfACompliant(),
                        file.isPdfPortfolio()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UploadFileResponse> listFiles(UUID caseId) {
        caseService.getCase(caseId);
        return evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(this::toUploadResponse)
                .toList();
    }

    public UploadFileResponse reclassify(UUID caseId, UUID fileId, ReclassifyFileRequest request) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        ensureUploadingAllowed(disputeCase);
        EvidenceFileEntity entity = evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("file not found in case"));

        EvidenceType previousType = entity.getEvidenceType();
        entity.setEvidenceType(request.evidenceType());
        EvidenceFileEntity saved = evidenceFileRepository.save(entity);
        if (disputeCase.getState() != CaseState.UPLOADING) {
            caseService.transitionState(disputeCase, CaseState.UPLOADING);
        }
        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "FILE_RECLASSIFIED",
                "fileId=" + saved.getId() + ",from=" + previousType + ",to=" + saved.getEvidenceType()
        );
        return toUploadResponse(saved);
    }

    public UploadFileResponse trimPdfPages(UUID caseId, UUID fileId, int trimStartPage, int trimEndPage) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        ensureUploadingAllowed(disputeCase);
        EvidenceFileEntity entity = evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("file not found in case"));
        if (entity.getFileFormat() != FileFormat.PDF) {
            throw new IllegalArgumentException("only PDF files can be trimmed");
        }

        Path originalPath = Path.of(entity.getStoragePath());
        Path savedPath = null;
        try {
            byte[] trimmedBytes = trimPdfBytes(originalPath, trimStartPage, trimEndPage);
            savedPath = saveBytes(caseId, trimmedBytes, ".pdf");
            PdfMetadata pdfMetadata = inspectStoredUpload(FileFormat.PDF, savedPath);
            long trimmedSize = readStoredFileSize(savedPath);
            int removedPages = Math.max(0, entity.getPageCount() - pdfMetadata.pageCount());

            entity.setOriginalName(baseNameWithoutExtension(entity.getOriginalName()) + "_manualtrim.pdf");
            entity.setContentType("application/pdf");
            entity.setStoragePath(savedPath.toString());
            entity.setSizeBytes(trimmedSize);
            entity.setPageCount(pdfMetadata.pageCount());
            entity.setFileFormat(FileFormat.PDF);
            entity.setPdfACompliant(pdfMetadata.pdfACompliant());
            entity.setPdfPortfolio(pdfMetadata.pdfPortfolio());
            entity.setExternalLinkDetected(pdfMetadata.externalLinkDetected());

            EvidenceFileEntity saved = evidenceFileRepository.save(entity);
            if (disputeCase.getState() != CaseState.UPLOADING) {
                caseService.transitionState(disputeCase, CaseState.UPLOADING);
            }
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "FILE_MANUAL_TRIM_APPLIED",
                    "fileId=" + saved.getId()
                            + ",trimStartPage=" + trimStartPage
                            + ",trimEndPage=" + trimEndPage
                            + ",removedPages=" + removedPages
                            + ",remainingPages=" + saved.getPageCount()
            );
            deleteStoredFileQuietly(originalPath);
            return toUploadResponse(saved);
        } catch (RuntimeException ex) {
            if (savedPath != null) {
                deleteStoredFileQuietly(savedPath);
            }
            throw ex;
        }
    }

    public UploadFileResponse compressImageForCaseSizeRescue(UUID caseId, UUID fileId, long targetBytes) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        ensureUploadingAllowed(disputeCase);
        EvidenceFileEntity entity = evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("file not found in case"));
        if (entity.getFileFormat() != FileFormat.JPEG && entity.getFileFormat() != FileFormat.PNG) {
            throw new IllegalArgumentException("only JPEG or PNG files can use stronger image compression");
        }

        long currentSize = entity.getSizeBytes();
        if (currentSize <= MIN_MANUAL_COMPRESS_TARGET_BYTES) {
            throw new IllegalArgumentException("image is already small enough; replace or remove a different file first");
        }

        long boundedTargetBytes = Math.max(
                MIN_MANUAL_COMPRESS_TARGET_BYTES,
                Math.min(targetBytes, currentSize - MIN_MANUAL_COMPRESS_SAVINGS_BYTES)
        );
        if (boundedTargetBytes >= currentSize) {
            throw new IllegalArgumentException("image is already close to the target size");
        }

        Path originalPath = Path.of(entity.getStoragePath());
        Path savedPath = null;
        try {
            byte[] compressedBytes = compressStoredImageToTarget(originalPath, boundedTargetBytes);
            if (compressedBytes.length == 0 || compressedBytes.length >= currentSize) {
                throw new IllegalArgumentException("stronger compression could not shrink this image further");
            }

            savedPath = saveBytes(caseId, compressedBytes, ".jpg");
            PdfMetadata metadata = inspectStoredUpload(FileFormat.JPEG, savedPath);

            entity.setOriginalName(baseNameWithoutExtension(entity.getOriginalName()) + "_manualcompress.jpg");
            entity.setContentType("image/jpeg");
            entity.setStoragePath(savedPath.toString());
            entity.setSizeBytes(compressedBytes.length);
            entity.setPageCount(metadata.pageCount());
            entity.setFileFormat(FileFormat.JPEG);
            entity.setPdfACompliant(metadata.pdfACompliant());
            entity.setPdfPortfolio(metadata.pdfPortfolio());
            entity.setExternalLinkDetected(metadata.externalLinkDetected());

            EvidenceFileEntity saved = evidenceFileRepository.save(entity);
            if (disputeCase.getState() != CaseState.UPLOADING) {
                caseService.transitionState(disputeCase, CaseState.UPLOADING);
            }
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "FILE_MANUAL_IMAGE_COMPRESS_APPLIED",
                    "fileId=" + saved.getId()
                            + ",targetBytes=" + boundedTargetBytes
                            + ",beforeBytes=" + currentSize
                            + ",afterBytes=" + saved.getSizeBytes()
            );
            deleteStoredFileQuietly(originalPath);
            return toUploadResponse(saved);
        } catch (RuntimeException ex) {
            if (savedPath != null) {
                deleteStoredFileQuietly(savedPath);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public boolean isManualPdfCompressionLikelyUseful(UUID caseId, UUID fileId) {
        caseService.getCase(caseId);
        EvidenceFileEntity entity = evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("file not found in case"));
        if (entity.getFileFormat() != FileFormat.PDF) {
            return false;
        }
        return !isTextHeavyPdf(Path.of(entity.getStoragePath()));
    }

    public UploadFileResponse compressPdfForCaseSizeRescue(UUID caseId, UUID fileId, long targetBytes) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        ensureUploadingAllowed(disputeCase);
        EvidenceFileEntity entity = evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("file not found in case"));
        if (entity.getFileFormat() != FileFormat.PDF) {
            throw new IllegalArgumentException("only PDF files can use stronger PDF compression");
        }

        long currentSize = entity.getSizeBytes();
        if (currentSize <= documentNormalizationService.minimumPdfTargetBytes()) {
            throw new IllegalArgumentException("pdf is already small enough; replace or trim a different file first");
        }
        if (isTextHeavyPdf(Path.of(entity.getStoragePath()))) {
            throw new IllegalArgumentException("pdf is text-heavy; replace or trim pages instead of raster compression");
        }

        long boundedTargetBytes = Math.max(
                documentNormalizationService.minimumPdfTargetBytes(),
                Math.min(targetBytes, currentSize - MIN_MANUAL_PDF_COMPRESS_SAVINGS_BYTES)
        );
        if (boundedTargetBytes >= currentSize) {
            throw new IllegalArgumentException("pdf is already close to the target size");
        }

        Path originalPath = Path.of(entity.getStoragePath());
        Path savedPath = null;
        try {
            byte[] compressedBytes = compressStoredPdfToTarget(disputeCase, originalPath, boundedTargetBytes);
            if (compressedBytes.length == 0 || compressedBytes.length >= currentSize) {
                throw new IllegalArgumentException("stronger compression could not shrink this pdf further");
            }

            savedPath = saveBytes(caseId, compressedBytes, ".pdf");
            PdfMetadata metadata = inspectStoredUpload(FileFormat.PDF, savedPath);

            entity.setOriginalName(baseNameWithoutExtension(entity.getOriginalName()) + "_manualcompress.pdf");
            entity.setContentType("application/pdf");
            entity.setStoragePath(savedPath.toString());
            entity.setSizeBytes(compressedBytes.length);
            entity.setPageCount(metadata.pageCount());
            entity.setFileFormat(FileFormat.PDF);
            entity.setPdfACompliant(metadata.pdfACompliant());
            entity.setPdfPortfolio(metadata.pdfPortfolio());
            entity.setExternalLinkDetected(metadata.externalLinkDetected());

            EvidenceFileEntity saved = evidenceFileRepository.save(entity);
            if (disputeCase.getState() != CaseState.UPLOADING) {
                caseService.transitionState(disputeCase, CaseState.UPLOADING);
            }
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "FILE_MANUAL_PDF_COMPRESS_APPLIED",
                    "fileId=" + saved.getId()
                            + ",targetBytes=" + boundedTargetBytes
                            + ",beforeBytes=" + currentSize
                            + ",afterBytes=" + saved.getSizeBytes()
            );
            deleteStoredFileQuietly(originalPath);
            return toUploadResponse(saved);
        } catch (RuntimeException ex) {
            if (savedPath != null) {
                deleteStoredFileQuietly(savedPath);
            }
            throw ex;
        }
    }

    private StoredUpload storeUpload(UUID caseId, MultipartFile file) {
        FileFormat directFormat = detectDirectFormat(file.getOriginalFilename(), file.getContentType());
        if (directFormat != null) {
            String extension = extensionFor(directFormat);
            Path savedPath = saveFile(caseId, file, extension);
            return new StoredUpload(
                    savedPath,
                    directFormat,
                    defaultString(file.getOriginalFilename(), "uploaded" + extension),
                    normalizedContentType(file.getContentType(), directFormat),
                    readStoredFileSize(savedPath),
                    false,
                    file.getContentType()
            );
        }

        return convertUnsupportedImageUpload(caseId, file);
    }

    private Path saveFile(UUID caseId, MultipartFile file, String extension) {
        try {
            Path caseDir = storageRoot.resolve(caseId.toString());
            Files.createDirectories(caseDir);
            Path target = caseDir.resolve(UUID.randomUUID() + extension);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    private Path saveBytes(UUID caseId, byte[] bytes, String extension) {
        try {
            Path caseDir = storageRoot.resolve(caseId.toString());
            Files.createDirectories(caseDir);
            Path target = caseDir.resolve(UUID.randomUUID() + extension);
            Files.write(target, bytes);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    private FileFormat detectDirectFormat(String originalName, String contentType) {
        String lowerName = defaultString(originalName, "").toLowerCase(Locale.ROOT);
        String lowerContentType = defaultString(contentType, "").toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".pdf") || lowerContentType.contains("pdf")) {
            return FileFormat.PDF;
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerContentType.contains("jpeg")) {
            return FileFormat.JPEG;
        }
        if (lowerName.endsWith(".png") || lowerContentType.contains("png")) {
            return FileFormat.PNG;
        }

        return null;
    }

    private StoredUpload convertUnsupportedImageUpload(UUID caseId, MultipartFile file) {
        BufferedImage original;
        try (InputStream inputStream = file.getInputStream()) {
            original = ImageIO.read(inputStream);
        } catch (IOException ex) {
            throw unsupportedFormatException(ex);
        }

        if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
            throw unsupportedFormatException(null);
        }

        byte[] jpegBytes = writeImageAsJpeg(original);
        Path savedPath = saveBytes(caseId, jpegBytes, ".jpg");
        return new StoredUpload(
                savedPath,
                FileFormat.JPEG,
                convertedName(file.getOriginalFilename(), ".jpg"),
                "image/jpeg",
                jpegBytes.length,
                true,
                file.getContentType()
        );
    }

    private String extensionFor(FileFormat format) {
        return switch (format) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
        };
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String normalizedContentType(String contentType, FileFormat format) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (format) {
            case PDF -> "application/pdf";
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
        };
    }

    private PdfMetadata inspectStoredUpload(FileFormat format, Path savedPath) {
        if (format == FileFormat.PDF) {
            try {
                return pdfMetadataExtractor.extract(savedPath);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid pdf file: unreadable pdf content", ex);
            }
        }

        validateImageBinaryContent(savedPath);
        return new PdfMetadata(1, false, true, false);
    }

    private void validateImageBinaryContent(Path savedPath) {
        try (InputStream in = Files.newInputStream(savedPath)) {
            var image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalArgumentException("invalid image file: unreadable image content");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid image file: unreadable image content", ex);
        }
    }

    private void deleteStoredFileQuietly(Path savedPath) {
        try {
            Files.deleteIfExists(savedPath);
        } catch (IOException ignored) {
            // best-effort cleanup only
        }
    }

    private long readStoredFileSize(Path savedPath) {
        try {
            return Files.size(savedPath);
        } catch (IOException ex) {
            deleteStoredFileQuietly(savedPath);
            throw new IllegalStateException("failed to read uploaded file size", ex);
        }
    }

    private byte[] writeImageAsJpeg(BufferedImage original) {
        BufferedImage rgbImage = toRgbImage(original);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(rgbImage, "jpeg", out)) {
                throw unsupportedFormatException(null);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to auto-convert image to JPEG", ex);
        }
        return out.toByteArray();
    }

    private byte[] compressStoredImageToTarget(Path sourcePath, long targetBytes) {
        BufferedImage original;
        try {
            original = ImageIO.read(sourcePath.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read image for stronger compression", ex);
        }
        if (original == null) {
            throw new IllegalArgumentException("invalid image file: unreadable image content");
        }

        float[] scales = new float[] {1.0f, 0.9f, 0.78f, 0.66f, 0.54f, 0.42f, 0.32f, 0.24f};
        float[] qualities = new float[] {0.82f, 0.68f, 0.56f, 0.44f, 0.34f, 0.26f, 0.18f};
        byte[] best = new byte[0];

        for (float scale : scales) {
            BufferedImage scaled = scaleImage(original, scale);
            for (float quality : qualities) {
                byte[] candidate = writeRgbImageAsJpeg(scaled, quality);
                if (best.length == 0 || candidate.length < best.length) {
                    best = candidate;
                }
                if (candidate.length <= targetBytes) {
                    return candidate;
                }
            }
        }

        return best;
    }

    private BufferedImage scaleImage(BufferedImage source, float scale) {
        BufferedImage rgbSource = toRgbImage(source);
        int targetWidth = Math.max(1, Math.round(rgbSource.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(rgbSource.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(rgbSource, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private BufferedImage toRgbImage(BufferedImage original) {
        BufferedImage rgbImage = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            graphics.drawImage(original, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    private byte[] writeRgbImageAsJpeg(BufferedImage image, float quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(output);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to encode stronger compressed JPEG", ex);
        }
        return out.toByteArray();
    }

    private byte[] compressStoredPdfToTarget(DisputeCase disputeCase, Path sourcePath, long targetBytes) {
        if (disputeCase.getPlatform() == com.example.demo.dispute.domain.Platform.SHOPIFY
                && disputeCase.getProductScope() == com.example.demo.dispute.domain.ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK) {
            return normalizeShopifyPdfToPdfaBytes(sourcePath, targetBytes);
        }
        return compressPdfToSmallerBytes(sourcePath, targetBytes);
    }

    private byte[] compressPdfToSmallerBytes(Path sourcePath, long preferredMaxBytes) {
        return documentNormalizationService.compressPdfToSmallerBytes(sourcePath, preferredMaxBytes);
    }

    private byte[] normalizeShopifyPdfToPdfaBytes(Path sourcePath, long preferredMaxBytes) {
        return documentNormalizationService.normalizePdfToPdfaBytes(sourcePath, preferredMaxBytes, "manual_pdfa_candidate");
    }

    private boolean isTextHeavyPdf(Path sourcePath) {
        return documentNormalizationService.isTextHeavyPdf(sourcePath);
    }

    private String convertedName(String originalName, String extension) {
        String baseName = defaultString(originalName, "uploaded").trim();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        if (baseName.isBlank()) {
            baseName = "uploaded";
        }
        return baseName + "_autoconvert" + extension;
    }

    private String baseNameWithoutExtension(String originalName) {
        String baseName = defaultString(originalName, "uploaded").trim();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            return baseName.substring(0, dotIndex);
        }
        return baseName.isBlank() ? "uploaded" : baseName;
    }

    private byte[] trimPdfBytes(Path sourcePath, int trimStartPage, int trimEndPage) {
        if (trimStartPage < 1 || trimEndPage < trimStartPage) {
            throw new IllegalArgumentException("invalid trim range");
        }

        try (PDDocument source = Loader.loadPDF(sourcePath.toFile()); PDDocument trimmed = new PDDocument()) {
            int totalPages = source.getNumberOfPages();
            if (trimEndPage > totalPages) {
                throw new IllegalArgumentException("trim range exceeds page count");
            }
            if (trimStartPage == 1 && trimEndPage == totalPages) {
                throw new IllegalArgumentException("trim range would remove every page from the PDF");
            }

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int pageNumber = pageIndex + 1;
                if (pageNumber >= trimStartPage && pageNumber <= trimEndPage) {
                    continue;
                }
                PDPage page = source.getPage(pageIndex);
                trimmed.importPage(page);
            }
            if (trimmed.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("trim range would remove every page from the PDF");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            trimmed.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to trim PDF pages", ex);
        }
    }

    private IllegalArgumentException unsupportedFormatException(Exception cause) {
        String message = "unsupported file format. Upload PDF, JPEG, or PNG directly. GIF, BMP, and WEBP can be auto-converted to JPEG. HEIC/HEIF should be converted to JPEG in the web upload flow before submission, and AVIF still needs manual conversion to JPG or PNG first.";
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }

    private void ensureUploadingAllowed(DisputeCase disputeCase) {
        if (caseService.canTransition(disputeCase.getState(), CaseState.UPLOADING)) {
            return;
        }
        throw new IllegalArgumentException(
                "illegal state transition: " + disputeCase.getState() + " -> " + CaseState.UPLOADING
        );
    }

    private UploadFileResponse toUploadResponse(EvidenceFileEntity saved) {
        return new UploadFileResponse(
                saved.getId(),
                saved.getEvidenceType(),
                saved.getFileFormat(),
                saved.getSizeBytes(),
                saved.getPageCount(),
                saved.isExternalLinkDetected(),
                saved.isPdfACompliant(),
                saved.isPdfPortfolio()
        );
    }

    private record StoredUpload(
            Path savedPath,
            FileFormat format,
            String storedOriginalName,
            String contentType,
            long sizeBytes,
            boolean autoConverted,
            String sourceContentType
    ) {
    }
}
