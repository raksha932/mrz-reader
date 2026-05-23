param(
    [string] $ApiBaseUrl = "http://localhost:8080",
    [string] $ApiKey = "dev-api-key",
    [string] $RateLimitApiKey = "smoke-rate-key",
    [string] $ComposeProjectName = "mrtd-smoke",
    [string] $FixtureDir = "smoke-tests/fixtures",
    [int] $StartupTimeoutSeconds = 120,
    [switch] $SkipCompose,
    [switch] $KeepStack
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string] $Message)
    Write-Host "==> $Message"
}

function Fail {
    param([string] $Message)
    throw "Smoke test failed: $Message"
}

function Assert-Status {
    param(
        [string] $Name,
        [int] $Actual,
        [int] $Expected
    )
    if ($Actual -ne $Expected) {
        Fail "$Name expected HTTP $Expected but got HTTP $Actual"
    }
}

function Get-JsonValue {
    param(
        [object] $Json,
        [string] $Path
    )
    $current = $Json
    foreach ($part in $Path.Split(".")) {
        if ($null -eq $current) {
            return $null
        }
        $property = $current.PSObject.Properties[$part]
        if ($null -eq $property) {
            return $null
        }
        $current = $property.Value
    }
    return $current
}

function Assert-JsonValue {
    param(
        [string] $Name,
        [object] $Json,
        [string] $Path,
        [object] $Expected
    )
    $actual = Get-JsonValue -Json $Json -Path $Path
    if ($actual -ne $Expected) {
        Fail "$Name expected '$Path' to be '$Expected' but got '$actual'"
    }
}

function Convert-ResponseJson {
    param([string] $Body)
    if ([string]::IsNullOrWhiteSpace($Body)) {
        return $null
    }
    return $Body | ConvertFrom-Json
}

function Invoke-Upload {
    param(
        [string] $FilePath,
        [string] $ContentType,
        [string] $Key
    )
    if (-not (Test-Path -LiteralPath $FilePath)) {
        Fail "fixture file does not exist: $FilePath"
    }
    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
        Fail "curl.exe is required to send multipart smoke-test requests"
    }

    $responseFile = [System.IO.Path]::GetTempFileName()
    try {
        $curlArgs = @(
            "--silent",
            "--show-error",
            "--output", $responseFile,
            "--write-out", "%{http_code}",
            "--request", "POST"
        )
        if (-not [string]::IsNullOrWhiteSpace($Key)) {
            $curlArgs += @("--header", "X-API-Key: $Key")
        }
        $curlArgs += @(
            "--form", "file=@$FilePath;type=$ContentType",
            "$ApiBaseUrl/api/v1/mrz/read"
        )

        $statusText = & curl.exe @curlArgs
        if ($LASTEXITCODE -ne 0) {
            Fail "curl failed with exit code $LASTEXITCODE"
        }
        $body = Get-Content -LiteralPath $responseFile -Raw
        return [pscustomobject]@{
            StatusCode = [int] $statusText
            Body = $body
            Json = Convert-ResponseJson -Body $body
        }
    }
    finally {
        if (Test-Path -LiteralPath $responseFile) {
            Remove-Item -LiteralPath $responseFile -Force
        }
    }
}

function Wait-ForReadiness {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri "$ApiBaseUrl/actuator/health/readiness" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $json = $response.Content | ConvertFrom-Json
                if ($json.status -eq "UP") {
                    return
                }
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    Fail "API readiness endpoint did not become UP within $StartupTimeoutSeconds seconds"
}

function Invoke-Compose {
    param([string[]] $Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Start-ComposeStack {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Fail "docker was not found on PATH"
    }
    Invoke-Compose @(
        "compose",
        "-p", $ComposeProjectName,
        "-f", "docker-compose.yml",
        "-f", "docker-compose.smoke.yml",
        "up",
        "--build",
        "-d"
    )
}

function Stop-ComposeStack {
    Invoke-Compose @(
        "compose",
        "-p", $ComposeProjectName,
        "-f", "docker-compose.yml",
        "-f", "docker-compose.smoke.yml",
        "down",
        "--remove-orphans"
    )
}

function Invoke-BoundarySmokeTests {
    $nonImage = Join-Path $FixtureDir "non-image.txt"

    Write-Step "checking missing API key"
    $missingKey = Invoke-Upload -FilePath $nonImage -ContentType "image/png" -Key ""
    Assert-Status -Name "missing API key" -Actual $missingKey.StatusCode -Expected 401
    Assert-JsonValue -Name "missing API key" -Json $missingKey.Json -Path "error.code" -Expected "UNAUTHORIZED"

    Write-Step "checking non-image upload validation"
    $nonImageUpload = Invoke-Upload -FilePath $nonImage -ContentType "text/plain" -Key $ApiKey
    Assert-Status -Name "non-image upload" -Actual $nonImageUpload.StatusCode -Expected 400
    Assert-JsonValue -Name "non-image upload" -Json $nonImageUpload.Json -Path "error.code" -Expected "INVALID_IMAGE"

    Write-Step "checking per-key rate limiting"
    $firstRateRequest = Invoke-Upload -FilePath $nonImage -ContentType "text/plain" -Key $RateLimitApiKey
    Assert-Status -Name "first rate-limit request" -Actual $firstRateRequest.StatusCode -Expected 400
    $secondRateRequest = Invoke-Upload -FilePath $nonImage -ContentType "text/plain" -Key $RateLimitApiKey
    Assert-Status -Name "second rate-limit request" -Actual $secondRateRequest.StatusCode -Expected 429
    Assert-JsonValue -Name "rate limit" -Json $secondRateRequest.Json -Path "error.code" -Expected "RATE_LIMIT_EXCEEDED"
}

function Invoke-ManifestSmokeTests {
    $manifestPath = Join-Path $FixtureDir "manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        Write-Warning "No smoke fixture manifest found at $manifestPath; skipping MRZ image cases."
        return
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    foreach ($case in $manifest.cases) {
        Write-Step "checking $($case.name)"
        $filePath = Join-Path $FixtureDir $case.file
        $response = Invoke-Upload -FilePath $filePath -ContentType $case.content_type -Key $ApiKey
        Assert-Status -Name $case.name -Actual $response.StatusCode -Expected ([int] $case.expected_status)

        foreach ($property in $case.expected.PSObject.Properties) {
            Assert-JsonValue -Name $case.name -Json $response.Json -Path $property.Name -Expected $property.Value
        }
    }
}

$startedStack = $false
try {
    if (-not $SkipCompose) {
        Write-Step "starting Docker Compose stack"
        Start-ComposeStack
        $startedStack = $true
    }

    Write-Step "waiting for API readiness"
    Wait-ForReadiness

    Invoke-BoundarySmokeTests
    Invoke-ManifestSmokeTests

    Write-Host "Docker Compose smoke tests passed."
}
finally {
    if ($startedStack -and -not $KeepStack) {
        Write-Step "stopping Docker Compose stack"
        Stop-ComposeStack
    }
}
