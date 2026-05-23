# API Reference

Base URL for local Docker Compose:

```text
http://localhost:8080
```

## Authentication

Protected endpoints require an API key in the `X-API-Key` header.

```text
X-API-Key: dev-api-key
```

Public endpoints:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /swagger-ui.html`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`

## Read MRZ

```text
POST /api/v1/mrz/read
Content-Type: multipart/form-data
```

Multipart parts:

| Name | Required | Content types | Description |
|---|---:|---|---|
| `file` | yes | `image/png`, `image/jpeg` | Document image containing a visible MRZ |

Example:

```powershell
curl.exe --request POST `
  --header "X-API-Key: dev-api-key" `
  --header "X-Request-ID: req-demo-1" `
  --form "file=@C:\tmp\passport.png;type=image/png" `
  http://localhost:8080/api/v1/mrz/read
```

Successful response shape:

```json
{
  "verdict": "VALID",
  "confidence_score": 0.97,
  "identity_data": {
    "document_type": "PASSPORT",
    "document_code": "P",
    "issuing_state": "NGA",
    "surname": "OKONKWO",
    "given_names": "CHUKWUEMEKA JOHN",
    "document_number": "A12345678",
    "nationality": "NGA",
    "date_of_birth": "1990-08-15",
    "raw_date_of_birth": "900815",
    "date_of_birth_valid": true,
    "sex": "M",
    "date_of_expiry": "2029-08-14",
    "raw_date_of_expiry": "290814",
    "date_of_expiry_valid": true,
    "optional_data": "",
    "expired": false
  },
  "validation_results": [
    {
      "check_name": "check_digit",
      "field_name": "document_number",
      "pass": true,
      "severity": "INFO",
      "message": "Check digit passed",
      "expected_digit": 4,
      "actual_digit": 4
    }
  ],
  "raw_mrz": {
    "format": "TD3",
    "lines": [
      "P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<",
      "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06"
    ]
  },
  "metadata": {
    "processing_time_ms": 5,
    "ocr_confidence": 0.97,
    "mrz_format": "TD3",
    "api_version": "1.0.0",
    "timestamp": "2026-05-21T17:24:18.073228Z",
    "request_id": "req-demo-1"
  }
}
```

## Verdicts

| Verdict | Meaning |
|---|---|
| `VALID` | MRZ parsed and validation passed. |
| `VALID_WITH_WARNINGS` | MRZ parsed, but a non-fatal warning exists, such as low OCR confidence or an expired document. |
| `INVALID` | MRZ parsed, but data validation failed, such as invalid check digits or invalid calendar dates. Full parsed data is still returned where possible. |

Fatal pipeline errors use the error envelope instead of a success body.

## Error Envelope

```json
{
  "error": {
    "code": "MRZ_NOT_DETECTED",
    "message": "Document found but MRZ zone could not be isolated",
    "timestamp": "2026-05-21T14:30:00Z",
    "request_id": "req-demo-1"
  }
}
```

## Status Codes

| HTTP status | Typical error code | Meaning |
|---:|---|---|
| `200` | none | MRZ read completed; inspect `verdict`. |
| `400` | `INVALID_IMAGE` | Empty upload, unsupported content type, malformed multipart body, or invalid image bytes. |
| `401` | `UNAUTHORIZED` | Missing or invalid API key. |
| `422` | `NO_DOCUMENT_FOUND`, `IMAGE_QUALITY_LOW`, `MRZ_NOT_DETECTED`, `MRZ_INCOMPLETE`, `UNSUPPORTED_FORMAT` | Fatal MRZ pipeline error. |
| `429` | `RATE_LIMIT_EXCEEDED` | Per-key rate limit exhausted. Response includes `Retry-After`. |
| `503` | `OCR_SERVICE_UNAVAILABLE` | OCR sidecar could not be reached or returned an unusable response. |

## Request IDs

Clients may send `X-Request-ID`. If omitted, the API creates one. The value is returned in:

- response header `X-Request-ID`
- success response `metadata.request_id`
- error response `error.request_id`

## OpenAPI

Interactive docs:

```text
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI document:

```text
http://localhost:8080/v3/api-docs
```
