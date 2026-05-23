# OCR Sidecar Deployment Notes

The OCR sidecar is a Python FastAPI service responsible for image preprocessing, MRZ zone detection, image quality scoring, and Tesseract OCR. It returns raw MRZ lines and OCR metadata to the Java core engine over HTTP.

## Runtime

- Python 3.12+
- FastAPI
- Uvicorn
- OpenCV headless
- pytesseract
- Tesseract 5.x

## Endpoints

Health:

```text
GET /health
```

Response:

```json
{
  "status": "UP"
}
```

Extract:

```text
POST /ocr/extract
Content-Type: multipart/form-data
```

Multipart part:

| Name | Required | Description |
|---|---:|---|
| `image` | yes | Document image bytes |

Success response:

```json
{
  "status": "SUCCESS",
  "mrz_lines": [
    "P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<",
    "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06"
  ],
  "confidence": 0.97,
  "zone_bbox": {
    "x": 10,
    "y": 20,
    "width": 500,
    "height": 90
  },
  "image_quality_score": 0.95,
  "preprocessing_applied": [
    "grayscale",
    "threshold"
  ]
}
```

Error response:

```json
{
  "status": "MRZ_NOT_DETECTED",
  "message": "Document found but MRZ zone could not be isolated",
  "image_quality_score": null
}
```

The Java core maps sidecar statuses into domain results. Sidecar transport failures become `OCR_SERVICE_UNAVAILABLE` at the API boundary.

## Local Development

From `ocr-sidecar`:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

Run sidecar tests:

```powershell
pytest
```

If your Python is installed at a fixed path:

```powershell
C:\Users\Hokage\AppData\Local\Programs\Python\Python313\python.exe -m pytest
```

## Docker

Build:

```powershell
docker build -t certipass/ocr-sidecar:local ocr-sidecar
```

Run:

```powershell
docker run --rm -p 8001:8001 certipass/ocr-sidecar:local
```

Health:

```powershell
curl.exe http://localhost:8001/health
```

## Configuration

| Environment variable | Default | Description |
|---|---|---|
| `OCR_QUALITY_THRESHOLD` | `0.40` | Minimum image quality score before OCR proceeds. |
| `TESSERACT_LANGUAGE` | `ocrb_int` | Tesseract language/traineddata name. |
| `TESSERACT_CONFIG` | `--psm 6 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<` | Tesseract page segmentation and character whitelist. |

## Tesseract Data

The sidecar packages `ocrb_int`, a community OCR-B traineddata file for MRZ text. The Dockerfile copies it from:

```text
ocr-sidecar/app/tessdata/ocrb_int.traineddata
```

into:

```text
/usr/share/tesseract-ocr/5/tessdata/ocrb_int.traineddata
```

Source:

```text
https://github.com/Shreeshrii/tessdata_ocrb/blob/master/ocrb_int.traineddata
```

SHA-256:

```text
814F0D862538249D4122DB8D41807B08FD7C31181455DF7F596A2ED5CE4434EE
```

## Operational Notes

- Keep the sidecar private to the internal network. External clients should call only `mrtd-api`.
- Scale the sidecar independently from the API when OCR becomes the bottleneck.
- Treat OCR output as untrusted text. Parsing, validation, check digits, verdicts, and field logic belong to the Java core.
- Tune preprocessing and Tesseract settings against the benchmark fixture set, not against one-off images.
- Readiness for the Java API depends on `GET /health` from this sidecar.

## Known Gaps

- The benchmark image dataset is not yet committed.
- The full TD1, TD2, TD3, blurry, expired, and visa Docker smoke cases need real image fixtures and ground truth.
- The packaged OCR-B model still needs accuracy validation against the benchmark image dataset.
