# Docker Compose smoke tests

Run the compose smoke tests from the repository root:

```powershell
.\scripts\smoke-test-compose.ps1
```

The script starts `docker-compose.yml` with `docker-compose.smoke.yml`, waits for API readiness, and always verifies:

- missing API key returns `401 UNAUTHORIZED`
- non-image upload returns `400 INVALID_IMAGE`
- the smoke rate-limit key returns `429 RATE_LIMIT_EXCEEDED` on the second request

To enable the full MRZ image smoke suite, copy `smoke-tests/fixtures/manifest.example.json` to `smoke-tests/fixtures/manifest.json`, add the referenced images, and replace the expected fields with the ground truth for those fixtures. Each case uploads through the public API, so the Java API and Python OCR sidecar must communicate over the Docker Compose network.

Use `-SkipCompose` when the stack is already running, or `-KeepStack` to leave containers up after the run.
