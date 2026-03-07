param(
    [string]$PdfPath = "",
    [string]$OutputDir = "output/pdf/render-checks",
    [string]$BaselineDir = "src/test/resources/pdf-baselines",
    [switch]$UpdateBaselines
)

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $gradleArgs = @("--quiet", "verifySummaryPdfBaseline", "-PpngOutputDir=$OutputDir", "-PbaselineDir=$BaselineDir")
    if ($PdfPath -and $PdfPath.Trim()) {
        $gradleArgs += "-PpdfPath=$PdfPath"
    }
    if ($UpdateBaselines) {
        $gradleArgs += "-PupdateBaselines=true"
    }
    & .\gradlew.bat @gradleArgs
} finally {
    Pop-Location
}
