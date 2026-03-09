package com.example.demo.dispute.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class EvidenceTextExtractionService {

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");

    public String extractPdfPreview(MultipartFile file, int maxPages, int maxChars) {
        try {
            return extractPdfPreview(file.getBytes(), maxPages, maxChars);
        } catch (IOException ex) {
            return null;
        }
    }

    public String extractPdfPreview(Path path, int maxPages, int maxChars) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return extractPdfPreview(Files.readAllBytes(path), maxPages, maxChars);
        } catch (IOException ex) {
            return null;
        }
    }

    public boolean shouldAttemptImageOcr(int width, int height) {
        return WINDOWS && width >= 500 && height >= 500;
    }

    public String extractImageOcrText(MultipartFile file, String originalName, int maxChars) {
        Path tempFile = null;
        try {
            String extension = normalizeImageExtension(originalName);
            tempFile = Files.createTempFile("chargeback-ocr-", extension);
            try (var in = file.getInputStream()) {
                Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return extractImageOcrText(tempFile, maxChars);
        } catch (IOException ex) {
            return null;
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    public String extractImageOcrText(Path path, int maxChars) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        ImageDimensions dimensions = readImageDimensions(path);
        if (dimensions == null || !shouldAttemptImageOcr(dimensions.width(), dimensions.height())) {
            return null;
        }
        return runWindowsOcr(path, maxChars);
    }

    private String extractPdfPreview(byte[] pdfBytes, int maxPages, int maxChars) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(maxPages, Math.max(1, document.getNumberOfPages())));
            return normalizeText(stripper.getText(document), maxChars);
        } catch (IOException ex) {
            return null;
        }
    }

    private String runWindowsOcr(Path path, int maxChars) {
        if (!WINDOWS) {
            return null;
        }
        try {
            String escapedPath = path.toAbsolutePath().toString().replace("'", "''");
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
            return normalizeText(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8), maxChars);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private ImageDimensions readImageDimensions(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException ex) {
            return null;
        }
    }

    private String normalizeImageExtension(String originalName) {
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return ".jpg";
        }
        return ".png";
    }

    private String normalizeText(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxChars) {
            return normalized.substring(0, maxChars);
        }
        return normalized;
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }

    private record ImageDimensions(int width, int height) {
    }
}
