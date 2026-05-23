# Docker Compose Runbook

This runbook starts the Spring Boot API and Python OCR sidecar together on a local Docker network.

## Services

| Service | Container port | Host port | Purpose |
|---|---:|---:|---|
| `mrtd-api` | `8080` | `8080` | Public REST API |
| `ocr-sidecar` | `8001` | `8001` | OCR extraction service |

The API calls the sidecar by service name:

```text
http://ocr-sidecar:8001/ocr/extract
```

## Start

```powershell
docker compose up --build
```

Detached mode:

```powershell
docker compose up --build -d
```

## Health Checks

API readiness:

```powershell
curl.exe http://localhost:8080/actuator/health/readiness
```

API liveness:

```powershell
curl.exe http://localhost:8080/actuator/health/liveness
```

OCR sidecar:

```powershell
curl.exe http://localhost:8001/health
```

Readiness includes the `ocrSidecar` health indicator, so the API is not ready when the sidecar health endpoint is unreachable.

## API Key

The compose stack defines a default development key:

```text
dev-api-key
```

Example request:

```powershell
curl.exe --request POST `
  --header "X-API-Key: dev-api-key" `
  --form "file=@C:\tmp\document.png;type=image/png" `
  http://localhost:8080/api/v1/mrz/read
```

## Smoke Tests

Run the smoke-test stack:

```powershell
.\scripts\smoke-test-compose.ps1
```

Useful options:

```powershell
.\scripts\smoke-test-compose.ps1 -KeepStack
.\scripts\smoke-test-compose.ps1 -SkipCompose
.\scripts\smoke-test-compose.ps1 -StartupTimeoutSeconds 180
```

The smoke override file adds a strict rate-limit key:

```text
smoke-rate-key
```

Boundary smoke checks run with only the committed text fixture. Full MRZ image cases require `smoke-tests/fixtures/manifest.json` and the referenced image files.

## Logs

All services:

```powershell
docker compose logs -f
```

API only:

```powershell
docker compose logs -f mrtd-api
```

Sidecar only:

```powershell
docker compose logs -f ocr-sidecar
```

## Stop And Clean Up

```powershell
docker compose down
```

Remove local images built by compose:

```powershell
docker compose down --rmi local
```

## Configuration

The compose file sets:

| Variable | Service | Default |
|---|---|---|
| `OCR_SIDECAR_EXTRACT_URL` | `mrtd-api` | `http://ocr-sidecar:8001/ocr/extract` |
| `OCR_SIDECAR_HEALTH_URL` | `mrtd-api` | `http://ocr-sidecar:8001/health` |
| `API_AUTH_KEYS_0_ID` | `mrtd-api` | `local-dev` |
| `API_AUTH_KEYS_0_VALUE` | `mrtd-api` | `dev-api-key` |
| `API_AUTH_KEYS_0_TIER` | `mrtd-api` | `default` |
| `API_AUTH_KEYS_1_VALUE` | `mrtd-api` | `benchmark-api-key` |
| `API_AUTH_KEYS_1_TIER` | `mrtd-api` | `benchmark` |
| `API_RATE_LIMIT_TIERS_BENCHMARK_CAPACITY` | `mrtd-api` | `10000` |

The smoke override additionally sets:

| Variable | Service | Default |
|---|---|---|
| `API_AUTH_KEYS_1_VALUE` | `mrtd-api` | `smoke-rate-key` |
| `API_AUTH_KEYS_1_TIER` | `mrtd-api` | `strict` |

The sidecar image packages `ocrb_int.traineddata`, so the default sidecar language is `ocrb_int` for both normal compose runs and smoke runs.

## Troubleshooting

`docker` is not recognized:

- Install Docker Desktop or add Docker to `PATH`.
- Open a new terminal after installation.

API readiness stays `DOWN`:

- Check `docker compose logs ocr-sidecar`.
- Verify `http://localhost:8001/health` returns `{"status":"UP"}`.
- Check that `OCR_SIDECAR_HEALTH_URL` points at `http://ocr-sidecar:8001/health` inside compose.

OCR requests fail with Tesseract language errors:

- Confirm `ocr-sidecar/app/tessdata/ocrb_int.traineddata` exists before building.
- Rebuild the image with `docker compose build --no-cache ocr-sidecar`.
- Confirm `TESSERACT_LANGUAGE` matches an installed traineddata file.
