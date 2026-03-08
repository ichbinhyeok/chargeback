package com.example.demo.dispute.service;

import com.example.demo.dispute.api.PreviewEvidenceSuggestionResponse;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class EvidenceSuggestionService {

    private static final Pattern GENERIC_SCREENSHOT_NAME = Pattern.compile(
            "^((img|image|photo|scan|screenshot|document)[-_ ]?\\d+|document[_-]?\\d+|img_\\d+).*",
            Pattern.CASE_INSENSITIVE
    );
    private static final int PDF_PREVIEW_PAGES = 2;
    private static final int PDF_PREVIEW_CHARS = 1600;
    private static final int OCR_PREVIEW_CHARS = 600;
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");

    private final CaseService caseService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final PolicyCatalogService policyCatalogService;

    public EvidenceSuggestionService(
            CaseService caseService,
            EvidenceFileRepository evidenceFileRepository,
            PolicyCatalogService policyCatalogService
    ) {
        this.caseService = caseService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.policyCatalogService = policyCatalogService;
    }

    public List<PreviewEvidenceSuggestionResponse> previewSuggestions(UUID caseId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        DisputeCase disputeCase = caseService.getCase(caseId);
        EnumSet<EvidenceType> presentTypes = EnumSet.noneOf(EvidenceType.class);
        evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(file -> file.getEvidenceType())
                .forEach(presentTypes::add);

        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork()
        );
        List<EvidenceType> missingRequired = missingEvidenceTypes(policy.requiredEvidenceTypes(), presentTypes);
        List<EvidenceType> missingRecommended = missingEvidenceTypes(policy.recommendedEvidenceTypes(), presentTypes);

        List<FilePreviewContext> contexts = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            contexts.add(buildContext(index, files.get(index)));
        }

        Map<String, Long> imageDimensionCounts = contexts.stream()
                .filter(context -> context.imageSignal() != null)
                .collect(Collectors.groupingBy(
                        context -> context.imageSignal().width() + "x" + context.imageSignal().height(),
                        Collectors.counting()
                ));

        List<PreviewEvidenceSuggestionResponse> suggestions = new ArrayList<>(contexts.size());
        for (FilePreviewContext context : contexts) {
            PreviewSuggestion suggestion = suggestFromPdfText(context, missingRequired, missingRecommended);
            if (suggestion == null) {
                suggestion = suggestFromStrongFileName(context.lowerName());
            }
            if (suggestion == null) {
                suggestion = suggestFromImageOcrText(context, missingRequired, missingRecommended);
            }
            if (suggestion == null) {
                suggestion = suggestFromImageMetadata(context, imageDimensionCounts, missingRequired, missingRecommended);
            }
            if (suggestion == null) {
                suggestion = suggestFromGenericFileName(context.lowerName(), missingRequired, missingRecommended);
            }
            if (suggestion == null) {
                suggestion = new PreviewSuggestion(
                        EvidenceType.OTHER_SUPPORTING,
                        "No strong preview clue yet. Keep the fallback or inspect the preview manually.",
                        "fallback"
                );
            }
            suggestions.add(new PreviewEvidenceSuggestionResponse(
                    context.index(),
                    suggestion.evidenceType(),
                    suggestion.reason(),
                    suggestion.source()
            ));
        }
        return List.copyOf(suggestions);
    }

    private List<EvidenceType> missingEvidenceTypes(List<EvidenceType> expectedTypes, EnumSet<EvidenceType> presentTypes) {
        if (expectedTypes == null || expectedTypes.isEmpty()) {
            return List.of();
        }
        List<EvidenceType> missing = new ArrayList<>();
        for (EvidenceType type : expectedTypes) {
            if (!presentTypes.contains(type)) {
                missing.add(type);
            }
        }
        return List.copyOf(missing);
    }

    private FilePreviewContext buildContext(int index, MultipartFile file) {
        String fileName = defaultString(file.getOriginalFilename(), "upload");
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String contentType = defaultString(file.getContentType(), "").toLowerCase(Locale.ROOT);

        String pdfText = isPdf(lowerName, contentType) ? extractPdfText(file) : null;
        ImageSignal imageSignal = isImage(lowerName, contentType) ? extractImageSignal(file) : null;
        String imageOcrText = imageSignal != null && shouldAttemptImageOcr(imageSignal)
                ? extractImageOcrText(file, lowerName)
                : null;

        return new FilePreviewContext(
                index,
                fileName,
                lowerName,
                contentType,
                pdfText,
                imageOcrText,
                imageSignal
        );
    }

    private PreviewSuggestion suggestFromPdfText(
            FilePreviewContext context,
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended
    ) {
        if (context.pdfText() == null || context.pdfText().isBlank()) {
            return null;
        }

        return suggestFromText(context.pdfText(), missingRequired, missingRecommended, 3, "content", "Preview text mentions ", false);
    }

    private PreviewSuggestion suggestFromImageOcrText(
            FilePreviewContext context,
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended
    ) {
        if (context.imageOcrText() == null || context.imageOcrText().isBlank()) {
            return null;
        }

        return suggestFromText(context.imageOcrText(), missingRequired, missingRecommended, 2, "ocr", "OCR text mentions ", true);
    }

    private TextScore bestTextScore(List<TextScore> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.score() > 0)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .findFirst()
                .orElse(null);
    }

    private TextScore buildTextScore(
            EvidenceType evidenceType,
            String normalizedText,
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended,
            String... terms
    ) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        int score = 0;
        for (String term : terms) {
            if (normalizedText.contains(term)) {
                matches.add(term);
                score += term.contains(" ") ? 2 : 1;
            }
        }
        if (matches.isEmpty()) {
            return new TextScore(evidenceType, 0, List.of());
        }
        if (missingRequired.contains(evidenceType)) {
            score += 2;
        } else if (missingRecommended.contains(evidenceType)) {
            score += 1;
        }
        return new TextScore(evidenceType, score, List.copyOf(matches).subList(0, Math.min(3, matches.size())));
    }

    private TextScore boostScore(TextScore score, int bonus, String... extraTerms) {
        if (score == null || score.score() <= 0 || bonus <= 0) {
            return score;
        }
        LinkedHashSet<String> matches = new LinkedHashSet<>(score.matchedTerms());
        for (String extraTerm : extraTerms) {
            if (matches.size() >= 3) {
                break;
            }
            if (extraTerm != null && !extraTerm.isBlank()) {
                matches.add(extraTerm);
            }
        }
        return new TextScore(score.evidenceType(), score.score() + bonus, List.copyOf(matches));
    }

    private PreviewSuggestion suggestFromText(
            String text,
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended,
            int minimumScore,
            String source,
            String reasonPrefix,
            boolean preferConversationSignals
    ) {
        TextScore orderReceipt = buildTextScore(EvidenceType.ORDER_RECEIPT, text, missingRequired, missingRecommended,
                "order number", "invoice", "receipt", "subtotal", "total", "payment", "purchase");
        TextScore fulfillment = buildTextScore(EvidenceType.FULFILLMENT_DELIVERY, text, missingRequired, missingRecommended,
                "tracking number", "tracking", "delivered", "delivery", "shipment", "carrier", "signed");
        TextScore policies = buildTextScore(EvidenceType.POLICIES, text, missingRequired, missingRecommended,
                "terms of service", "refund policy", "return policy", "cancellation policy", "policy",
                "terms and conditions", "all sales final", "non-refundable", "non refundable", "return window",
                "delivery policy", "shipping policy", "returns", "no returns", "exchanges", "final sale");
        TextScore refund = buildTextScore(EvidenceType.REFUND_CANCELLATION, text, missingRequired, missingRecommended,
                "refund", "refunded", "cancelled", "canceled", "credit issued", "voided", "reversed");
        TextScore customerDetails = buildTextScore(EvidenceType.CUSTOMER_DETAILS, text, missingRequired, missingRecommended,
                "billing address", "shipping address", "customer name", "cardholder", "email address", "phone number");
        TextScore usageLogs = buildTextScore(EvidenceType.DIGITAL_USAGE_LOGS, text, missingRequired, missingRecommended,
                "ip address", "session", "login", "device", "download", "activity log", "accessed");
        TextScore communication = buildTextScore(EvidenceType.CUSTOMER_COMMUNICATION, text, missingRequired, missingRecommended,
                "support", "chat", "conversation", "message history", "from:", "subject:", "re:");

        if (preferConversationSignals) {
            int conversationBonus = conversationSignalBonus(text);
            communication = boostScore(communication, conversationBonus, "merchant", "replied", "where is my order");
        }
        policies = boostScore(policies, policyContextBonus(text, fulfillment), "policy heading", "help center");

        TextScore best = bestTextScore(List.of(
                orderReceipt,
                fulfillment,
                policies,
                refund,
                customerDetails,
                usageLogs,
                communication
        ));

        if (best == null || best.score() < minimumScore) {
            return null;
        }

        return new PreviewSuggestion(
                best.evidenceType(),
                reasonPrefix + String.join(", ", best.matchedTerms()) + ".",
                source
        );
    }

    private int conversationSignalBonus(String normalizedText) {
        int bonus = 0;
        if (matchesAny(normalizedText, "support", "chat", "merchant", "replied", "customer asked", "follow up", "follow-up")) {
            bonus += 2;
        }
        if (matchesAny(normalizedText, "hi ", "hello", "where is my order", "nothing was received", "carrier trace", "promised follow-up")) {
            bonus += 2;
        }
        return bonus;
    }

    private int policyContextBonus(String normalizedText, TextScore fulfillment) {
        int bonus = 0;
        if (matchesAny(normalizedText,
                "policy",
                "terms",
                "conditions",
                "delivery policy",
                "shipping policy",
                "help center",
                "storefront")) {
            bonus += 2;
        }
        if (fulfillment != null
                && fulfillment.score() > 0
                && matchesAny(normalizedText, "policy", "terms", "conditions", "returns", "exchanges", "final sale")) {
            bonus += 2;
        }
        return bonus;
    }

    private PreviewSuggestion suggestFromImageMetadata(
            FilePreviewContext context,
            Map<String, Long> imageDimensionCounts,
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended
    ) {
        if (context.imageSignal() == null) {
            return null;
        }

        ImageSignal image = context.imageSignal();
        boolean portrait = image.height() >= (int) Math.round(image.width() * 1.45d);
        boolean screenshotSized = image.width() >= 700 && image.height() >= 1200;
        boolean repeatedDimensions = imageDimensionCounts.getOrDefault(image.dimensionKey(), 0L) >= 2;
        boolean genericName = GENERIC_SCREENSHOT_NAME.matcher(context.fileName()).matches();

        if (!portrait || !screenshotSized || (!repeatedDimensions && !genericName)) {
            return null;
        }

        EvidenceType target = preferredScreenshotEvidenceType(missingRequired, missingRecommended);
        if (target == null) {
            return null;
        }

        String reason = "Preview metadata looks like a phone screenshot batch (" + image.width() + "x" + image.height()
                + ") and this case still needs " + humanizeEvidenceType(target).toLowerCase(Locale.ROOT) + ".";
        return new PreviewSuggestion(target, reason, "metadata");
    }

    private EvidenceType preferredScreenshotEvidenceType(
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended
    ) {
        List<EvidenceType> requiredCandidates = screenshotGapCandidates(missingRequired);
        if (requiredCandidates.size() == 1) {
            return requiredCandidates.getFirst();
        }
        if (!requiredCandidates.isEmpty()) {
            return null;
        }

        List<EvidenceType> recommendedCandidates = screenshotGapCandidates(missingRecommended);
        if (recommendedCandidates.size() == 1) {
            return recommendedCandidates.getFirst();
        }
        return null;
    }

    private List<EvidenceType> screenshotGapCandidates(List<EvidenceType> gaps) {
        List<EvidenceType> candidates = new ArrayList<>();
        if (gaps == null || gaps.isEmpty()) {
            return candidates;
        }
        for (EvidenceType type : List.of(
                EvidenceType.CUSTOMER_COMMUNICATION,
                EvidenceType.CUSTOMER_DETAILS,
                EvidenceType.ORDER_RECEIPT,
                EvidenceType.FULFILLMENT_DELIVERY
        )) {
            if (gaps.contains(type)) {
                candidates.add(type);
            }
        }
        return candidates;
    }

    private PreviewSuggestion suggestFromStrongFileName(String lowerName) {
        if (matchesAny(lowerName, "policy", "terms", "agreement", "contract", "conditions")) {
            return new PreviewSuggestion(EvidenceType.POLICIES, "Filename looks like policy or terms evidence.", "keyword");
        }
        if (matchesAny(lowerName, "receipt", "invoice", "order", "purchase", "checkout", "transaction", "statement")) {
            return new PreviewSuggestion(EvidenceType.ORDER_RECEIPT, "Filename looks like receipt or order evidence.", "keyword");
        }
        if (matchesAny(lowerName, "tracking", "delivery", "shipment", "shipping", "fulfillment", "carrier", "fedex", "ups", "dhl", "usps")) {
            return new PreviewSuggestion(EvidenceType.FULFILLMENT_DELIVERY, "Filename looks like delivery or tracking proof.", "keyword");
        }
        if (matchesAny(lowerName, "message", "chat", "conversation", "email", "communication", "whatsapp", "telegram", "support")) {
            return new PreviewSuggestion(EvidenceType.CUSTOMER_COMMUNICATION, "Filename looks like customer communication.", "keyword");
        }
        if (matchesAny(lowerName, "refund", "cancel", "cancellation", "reversal", "credit note", "credit_note")) {
            return new PreviewSuggestion(EvidenceType.REFUND_CANCELLATION, "Filename looks like refund or cancellation proof.", "keyword");
        }
        if (matchesAny(lowerName, "customer", "address", "profile", "billing_profile", "account", "identity")) {
            return new PreviewSuggestion(EvidenceType.CUSTOMER_DETAILS, "Filename looks like customer identity or billing details.", "keyword");
        }
        if (matchesAny(lowerName, "usage", "log", "access", "session", "device", "download", "ip", "activity", "digital")) {
            return new PreviewSuggestion(EvidenceType.DIGITAL_USAGE_LOGS, "Filename looks like digital usage or access logs.", "keyword");
        }
        return null;
    }

    private PreviewSuggestion suggestFromGenericFileName(
            String lowerName,
            List<EvidenceType> missingRequired,
            List<EvidenceType> missingRecommended
    ) {
        if (GENERIC_SCREENSHOT_NAME.matcher(lowerName).matches()) {
            EvidenceType screenshotType = preferredScreenshotEvidenceType(missingRequired, missingRecommended);
            if (screenshotType != null) {
                return new PreviewSuggestion(
                        screenshotType,
                        "Generic screenshot naming matches the remaining gaps in this case.",
                        "recommended"
                );
            }
        }
        return null;
    }

    private String extractPdfText(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(PDF_PREVIEW_PAGES, Math.max(1, document.getNumberOfPages())));
            String text = stripper.getText(document)
                    .replace('\r', ' ')
                    .replace('\n', ' ')
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (text.length() > PDF_PREVIEW_CHARS) {
                return text.substring(0, PDF_PREVIEW_CHARS);
            }
            return text;
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean shouldAttemptImageOcr(ImageSignal imageSignal) {
        return WINDOWS && imageSignal.width() >= 500 && imageSignal.height() >= 500;
    }

    private String extractImageOcrText(MultipartFile file, String lowerName) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("chargeback-ocr-", lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ? ".jpg" : ".png");
            try (var in = file.getInputStream()) {
                Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String escapedPath = tempFile.toAbsolutePath().toString().replace("'", "''");
            String script = "$ErrorActionPreference='Stop';"
                    + "Add-Type -AssemblyName System.Runtime.WindowsRuntime;"
                    + "$null=[Windows.Storage.StorageFile, Windows.Storage, ContentType=WindowsRuntime];"
                    + "$null=[Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType=WindowsRuntime];"
                    + "$null=[Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation, ContentType=WindowsRuntime];"
                    + "$null=[Windows.Storage.FileAccessMode, Windows.Storage, ContentType=WindowsRuntime];"
                    + "function Await($AsyncOperation,[Type]$ResultType){"
                    + "$asTask=[System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{$_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1}|Select-Object -First 1;"
                    + "$netTask=$asTask.MakeGenericMethod($ResultType).Invoke($null,@($AsyncOperation));"
                    + "$netTask.Wait();"
                    + "return $netTask.Result};"
                    + "$file=Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync('" + escapedPath + "')) ([Windows.Storage.StorageFile]);"
                    + "$stream=Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream]);"
                    + "$decoder=Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder]);"
                    + "$bitmap=Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap]);"
                    + "$engine=[Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages();"
                    + "if($engine -eq $null){ return };"
                    + "$result=Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult]);"
                    + "Write-Output $result.Text;";

            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }

            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace('\r', ' ')
                    .replace('\n', ' ')
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (text.isBlank()) {
                return null;
            }
            if (text.length() > OCR_PREVIEW_CHARS) {
                return text.substring(0, OCR_PREVIEW_CHARS);
            }
            return text;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            }
        }
    }

    private ImageSignal extractImageSignal(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }
            return new ImageSignal(image.getWidth(), image.getHeight());
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean isPdf(String lowerName, String contentType) {
        return lowerName.endsWith(".pdf") || contentType.contains("pdf");
    }

    private boolean isImage(String lowerName, String contentType) {
        return contentType.startsWith("image/")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".bmp")
                || lowerName.endsWith(".webp")
                || lowerName.endsWith(".heic")
                || lowerName.endsWith(".heif")
                || lowerName.endsWith(".avif");
    }

    private boolean matchesAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String humanizeEvidenceType(EvidenceType evidenceType) {
        return switch (evidenceType) {
            case ORDER_RECEIPT -> "Order receipt";
            case CUSTOMER_DETAILS -> "Customer details";
            case CUSTOMER_COMMUNICATION -> "Customer communication";
            case POLICIES -> "Policies";
            case FULFILLMENT_DELIVERY -> "Fulfillment / delivery proof";
            case DIGITAL_USAGE_LOGS -> "Digital usage logs";
            case REFUND_CANCELLATION -> "Refund / cancellation";
            case OTHER_SUPPORTING -> "Other supporting evidence";
        };
    }

    private record FilePreviewContext(
            int index,
            String fileName,
            String lowerName,
            String contentType,
            String pdfText,
            String imageOcrText,
            ImageSignal imageSignal
    ) {
    }

    private record ImageSignal(int width, int height) {
        private String dimensionKey() {
            return width + "x" + height;
        }
    }

    private record TextScore(EvidenceType evidenceType, int score, List<String> matchedTerms) {
    }

    private record PreviewSuggestion(EvidenceType evidenceType, String reason, String source) {
    }
}
