param(
    [string] $OutputDir = "benchmark-dataset/generated",
    [int] $CopiesPerScenario = 4,
    [int] $Seed = 9303
)

$ErrorActionPreference = "Stop"

if ($CopiesPerScenario -lt 1) {
    throw "CopiesPerScenario must be at least 1"
}

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

$sourceFile = "tools\benchmark-dataset\BenchmarkDatasetGenerator.java"
$classDir = "tools\benchmark-dataset\out"

New-Item -ItemType Directory -Force -Path $classDir | Out-Null

& $javac -encoding UTF-8 -d $classDir $sourceFile
if ($LASTEXITCODE -ne 0) {
    throw "Benchmark dataset generator compilation failed"
}

& $java "-Djava.awt.headless=true" -cp $classDir BenchmarkDatasetGenerator `
    "--output=$OutputDir" `
    "--copies=$CopiesPerScenario" `
    "--seed=$Seed"
if ($LASTEXITCODE -ne 0) {
    throw "Benchmark dataset generation failed"
}
