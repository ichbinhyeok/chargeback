param(
    [string]$PdfPath = "",
    [string]$OutputDir = "output/pdf/render-checks"
)

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $gradleArgs = @("--quiet", "verifySummaryPdfRender", "-PpngOutputDir=$OutputDir")
    if ($PdfPath -and $PdfPath.Trim()) {
        $gradleArgs += "-PpdfPath=$PdfPath"
    }
    & .\gradlew.bat @gradleArgs
} finally {
    Pop-Location
}
