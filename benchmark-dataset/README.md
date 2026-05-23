# Benchmark Dataset

This directory defines the synthetic benchmark dataset used for MRZ OCR accuracy work.

The generated dataset contains 576 labeled PNG images with the default settings:

- TD3 passport-style MRZ images
- TD2 document-style MRZ images
- TD1 identity-card-style MRZ images
- MRV-style visa images for unsupported-format behavior
- clean, skewed, blurred, low-contrast, low-resolution, glare, and occlusion variants

Generated output is written to `benchmark-dataset/generated/` and is ignored by Git. The generator is deterministic, so the same seed and case definitions produce the same dataset.

## Generate

From the repository root:

```powershell
.\scripts\generate-benchmark-dataset.ps1
```

Custom output directory:

```powershell
.\scripts\generate-benchmark-dataset.ps1 -OutputDir C:\tmp\mrtd-benchmark
```

Custom case count per base document and scenario:

```powershell
.\scripts\generate-benchmark-dataset.ps1 -CopiesPerScenario 6
```

## Output

```text
benchmark-dataset/generated/
|-- images/
|   |-- td3/
|   |-- td2/
|   |-- td1/
|   `-- mrv/
`-- manifest.json
```

The manifest contains:

- dataset metadata
- one record per generated image
- MRZ format and scenario labels
- expected pipeline outcome
- ground-truth MRZ lines
- parsed ground-truth identity fields where applicable

## Intended Use

Use this dataset for repeatable Phase 6 work:

- character-level OCR accuracy
- field-level parsing accuracy
- strict document-level pass/fail accuracy, requiring exact corrected MRZ text, fields, and outcome
- semantic document-level pass/fail accuracy, requiring the expected parsed fields and outcome even when correction changes OCR text
- scenario breakdowns such as blur, skew, low contrast, and occlusion

This synthetic dataset does not replace anonymized real document photos. It gives us a stable baseline while the real benchmark corpus is collected.

## Measure Accuracy

Start the API and OCR sidecar, then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-accuracy-harness.ps1
```

The Docker Compose stack defines `benchmark-api-key` with a high rate-limit tier so a full benchmark run is not throttled.

The report is written to:

```text
benchmark-dataset/reports/accuracy-report.json
```

Failed cases include diagnostics for the next tuning pass:

- expected and actual MRZ lines
- original OCR MRZ lines before core normalization/correction
- original OCR character accuracy before core line correction
- whether the core changed MRZ lines before parsing
- status, verdict, error code, and error message
- confidence score and latency
- field-level expected/actual mismatches

The report also includes aggregate tuning sections:

- `by_field`: pass/fail accuracy for each parsed identity field
- `correction_metrics`: how often MRZ line correction ran, whether corrected lines improved or regressed character accuracy, and strict/semantic document and outcome accuracy split by corrected vs uncorrected cases

For fatal pipeline errors that occur after OCR extraction, such as `MRZ_INCOMPLETE`, the API returns available MRZ diagnostics in `raw_mrz.lines`, `raw_mrz.ocr_lines`, and `raw_mrz.corrected`; the harness copies them into the failure report.

To validate the manifest and report math without a running API:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-accuracy-harness.ps1 -DryRun
```
