# MRTD MRZ Reader

The MRZ Reader accepts an image of a machine readable travel document, extracts the MRZ text, parses it according to ICAO-style TD1, TD2, and TD3 layouts, validates check digits and field logic, and returns structured identity data with a verdict.

The product is split into two cooperating parts:

- `mrtd-core`: the pure Java core engine. This is the product logic and has no Spring, servlet, or Jakarta dependencies.
- `ocr-sidecar`: a Python FastAPI service that preprocesses images, detects the MRZ zone, and runs Tesseract OCR.

`mrtd-api` is the Spring Boot 4 delivery boundary. It handles HTTP upload, API key auth, rate limiting, OpenAPI docs, request IDs, health checks, and maps the core engine result to JSON.

## Repository Layout

```text
.
|-- mrtd-core/             Pure Java MRZ engine
|-- mrtd-api/              Spring Boot API boundary
|-- ocr-sidecar/           Python FastAPI OCR sidecar
|-- scripts/               Local and smoke-test scripts
|-- smoke-tests/           Docker Compose smoke-test fixtures and manifest template
|-- docker-compose.yml     Local API plus sidecar stack
|-- docker-compose.smoke.yml
`-- implementation_plan.md
```

## Requirements

- Java 25
- Maven 3.9+
- Python 3.12+ for local OCR sidecar development
- Tesseract 5.x for local OCR sidecar runs outside Docker
- Docker and Docker Compose for the two-service stack

## Build And Test

From the repository root:

```powershell
mvn -pl mrtd-api -am test
```

That runs the core and API test suites. The API tests include Spring Boot integration tests with a mocked OCR sidecar.

To run only the pure core:

```powershell
mvn -pl mrtd-core test
```

## Run With Docker Compose

```powershell
docker compose up --build
```

The API listens on `http://localhost:8080`.
The OCR sidecar listens on `http://localhost:8001`.

Readiness:

```powershell
curl.exe http://localhost:8080/actuator/health/readiness
```

OpenAPI:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

Default local API key:

```text
dev-api-key
```

## Read An MRZ Image

```powershell
curl.exe --request POST `
  --header "X-API-Key: dev-api-key" `
  --form "file=@C:\path\to\document.png;type=image/png" `
  http://localhost:8080/api/v1/mrz/read
```

The API accepts `image/png` and `image/jpeg` uploads under the multipart part name `file`.

## Smoke Tests

Run the Docker Compose smoke harness:

```powershell
.\scripts\smoke-test-compose.ps1
```

The harness always verifies readiness, missing API key handling, non-image upload validation, and rate limiting. Full TD1, TD2, TD3, blurry, expired, and visa smoke cases are enabled by adding real images plus `smoke-tests/fixtures/manifest.json`; start from `smoke-tests/fixtures/manifest.example.json`.

## Benchmark Dataset

Generate the deterministic synthetic benchmark dataset:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\generate-benchmark-dataset.ps1
```

This writes 576 labeled PNG images and `manifest.json` to `benchmark-dataset/generated/`. See [benchmark-dataset/README.md](benchmark-dataset/README.md).

Run the accuracy harness against a running API:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-accuracy-harness.ps1
```

The harness uses `benchmark-api-key`, which is configured by Docker Compose with a high rate-limit tier for benchmark runs.
Failed cases in `benchmark-dataset/reports/accuracy-report.json` include expected/actual MRZ lines and field mismatches for OCR tuning.

## Documentation

- [API reference and examples](docs/api.md)
- [Docker Compose runbook](docs/docker-compose.md)
- [OCR sidecar deployment notes](docs/ocr-sidecar.md)

## Key Runtime Boundary

The Java API and Python sidecar communicate over HTTP:

- API to OCR sidecar: `POST /ocr/extract`
- OCR sidecar health: `GET /health`

The core engine owns all MRZ parsing and validation decisions. The Spring API must remain a thin delivery layer around that core.
