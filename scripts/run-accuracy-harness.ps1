param(
    [string] $ManifestPath = "benchmark-dataset/generated/manifest.json",
    [string] $ApiBaseUrl = "http://localhost:8080",
    [string] $ApiKey = "benchmark-api-key",
    [string] $ReportPath = "benchmark-dataset/reports/accuracy-report.json",
    [int] $RequestTimeoutSeconds = 30,
    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaHome = "C:\Program Files\Java\jdk-25.0.3"
}

$javac = Join-Path $javaHome "bin\javac.exe"
$java = Join-Path $javaHome "bin\java.exe"

if (-not (Test-Path -LiteralPath $javac)) {
    throw "javac.exe was not found at $javac"
}
if (-not (Test-Path -LiteralPath $java)) {
    throw "java.exe was not found at $java"
}
if (-not (Test-Path -LiteralPath $ManifestPath)) {
    throw "Benchmark manifest was not found at $ManifestPath. Generate it with scripts\generate-benchmark-dataset.ps1."
}

$sourceFile = "tools\accuracy-harness\AccuracyMeasurementHarness.java"
$classDir = "tools\accuracy-harness\out"

New-Item -ItemType Directory -Force -Path $classDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ReportPath) | Out-Null

& $javac -encoding UTF-8 -d $classDir $sourceFile
if ($LASTEXITCODE -ne 0) {
    throw "Accuracy harness compilation failed"
}

$dryRunValue = if ($DryRun) { "true" } else { "false" }

& $java -cp $classDir AccuracyMeasurementHarness `
    "--manifest=$ManifestPath" `
    "--api-base-url=$ApiBaseUrl" `
    "--api-key=$ApiKey" `
    "--report=$ReportPath" `
    "--timeout-seconds=$RequestTimeoutSeconds" `
    "--dry-run=$dryRunValue"
if ($LASTEXITCODE -ne 0) {
    throw "Accuracy harness failed"
}
