import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class AccuracyMeasurementHarness {
    private final Path manifestPath;
    private final URI apiReadUri;
    private final String apiKey;
    private final Path reportPath;
    private final boolean dryRun;
    private final HttpClient httpClient;

    private AccuracyMeasurementHarness(Options options) {
        this.manifestPath = options.manifestPath();
        this.apiReadUri = URI.create(trimTrailingSlash(options.apiBaseUrl()) + "/api/v1/mrz/read");
        this.apiKey = options.apiKey();
        this.reportPath = options.reportPath();
        this.dryRun = options.dryRun();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(options.timeoutSeconds()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.from(args);
        AccuracyMeasurementHarness harness = new AccuracyMeasurementHarness(options);
        AccuracyReport report = harness.run();
        Files.createDirectories(options.reportPath().toAbsolutePath().getParent());
        Files.writeString(options.reportPath(), report.toJson(), StandardCharsets.UTF_8);
        report.printSummary();
    }

    private AccuracyReport run() throws IOException, InterruptedException {
        Manifest manifest = Manifest.fromJson(Files.readString(manifestPath, StandardCharsets.UTF_8));
        Path imageRoot = manifestPath.toAbsolutePath().getParent();
        List<CaseResult> caseResults = new ArrayList<>();

        for (BenchmarkCase benchmarkCase : manifest.cases()) {
            ApiResult apiResult = dryRun ? dryRunResult(benchmarkCase) : callApi(imageRoot.resolve(benchmarkCase.imagePath()), benchmarkCase.contentType());
            caseResults.add(evaluate(benchmarkCase, apiResult));
        }

        return AccuracyReport.from(manifest, caseResults, dryRun, reportPath);
    }

    private ApiResult callApi(Path imagePath, String contentType) throws IOException, InterruptedException {
        String boundary = "----mrtd-benchmark-" + System.nanoTime();
        byte[] body = multipartBody(boundary, imagePath, contentType);
        HttpRequest request = HttpRequest.newBuilder(apiReadUri)
                .timeout(Duration.ofSeconds(60))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        long started = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        Object parsed = Json.parse(response.body());
        return new ApiResult(response.statusCode(), parsed instanceof Map<?, ?> map ? castMap(map) : Map.of(), elapsedMs);
    }

    private byte[] multipartBody(String boundary, Path imagePath, String contentType) throws IOException {
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + imagePath.getFileName() + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";
        byte[] fileBytes = Files.readAllBytes(imagePath);
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];
        System.arraycopy(prefixBytes, 0, body, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, body, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, body, prefixBytes.length + fileBytes.length, suffixBytes.length);
        return body;
    }

    private ApiResult dryRunResult(BenchmarkCase benchmarkCase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if ("VALID_MRZ".equals(benchmarkCase.expectedKind())) {
            payload.put("verdict", benchmarkCase.groundTruth().expired() ? "VALID_WITH_WARNINGS" : "VALID");
            payload.put("raw_mrz", Map.of(
                    "format", benchmarkCase.format(),
                    "lines", benchmarkCase.groundTruth().mrzLines(),
                    "ocr_lines", benchmarkCase.groundTruth().mrzLines(),
                    "corrected", false));
            payload.put("identity_data", Map.of(
                    "document_type", benchmarkCase.groundTruth().documentType(),
                    "document_number", benchmarkCase.groundTruth().documentNumber(),
                    "issuing_state", benchmarkCase.groundTruth().issuingState(),
                    "nationality", benchmarkCase.groundTruth().nationality(),
                    "surname", benchmarkCase.groundTruth().surname(),
                    "given_names", benchmarkCase.groundTruth().givenNames(),
                    "expired", benchmarkCase.groundTruth().expired()));
            return new ApiResult(200, payload, 0);
        }

        String errorCode = benchmarkCase.expectedErrorCode().orElse("UNKNOWN");
        payload.put("error", Map.of("code", errorCode, "message", "dry run"));
        int status = "IMAGE_QUALITY_LOW".equals(errorCode) || "UNSUPPORTED_FORMAT".equals(errorCode) ? 422 : 500;
        return new ApiResult(status, payload, 0);
    }

    private CaseResult evaluate(BenchmarkCase benchmarkCase, ApiResult apiResult) {
        boolean expectedValid = "VALID_MRZ".equals(benchmarkCase.expectedKind());
        List<String> actualMrzLines = listAt(apiResult.payload(), "raw_mrz.lines");
        List<String> originalOcrMrzLines = listAt(apiResult.payload(), "raw_mrz.ocr_lines");
        if (originalOcrMrzLines.isEmpty()) {
            originalOcrMrzLines = actualMrzLines;
        }
        boolean mrzLinesCorrected = booleanAt(apiResult.payload(), "raw_mrz.corrected").orElse(false);
        CharacterScore characterScore = expectedValid
                ? CharacterScore.compare(benchmarkCase.groundTruth().mrzLines(), actualMrzLines)
                : new CharacterScore(0, 0, true);
        CharacterScore originalCharacterScore = expectedValid
                ? CharacterScore.compare(benchmarkCase.groundTruth().mrzLines(), originalOcrMrzLines)
                : new CharacterScore(0, 0, true);

        List<FieldComparison> fields = expectedValid
                ? compareFields(benchmarkCase.groundTruth(), apiResult.payload())
                : List.of();
        boolean fieldsPass = fields.stream().allMatch(FieldComparison::pass);

        boolean outcomePass;
        String actualErrorCode = stringAt(apiResult.payload(), "error.code").orElse(null);
        String actualErrorMessage = stringAt(apiResult.payload(), "error.message").orElse(null);
        String actualVerdict = stringAt(apiResult.payload(), "verdict").orElse(null);
        Double confidenceScore = doubleAt(apiResult.payload(), "confidence_score").orElse(null);
        if (expectedValid) {
            outcomePass = apiResult.statusCode() == 200;
        } else {
            outcomePass = benchmarkCase.expectedErrorCode().filter(code -> code.equals(actualErrorCode)).isPresent();
        }

        boolean semanticDocumentPass = expectedValid
                ? outcomePass && fieldsPass
                : outcomePass;
        boolean documentPass = expectedValid
                ? semanticDocumentPass && characterScore.exact()
                : outcomePass;

        return new CaseResult(
                benchmarkCase.id(),
                benchmarkCase.format(),
                benchmarkCase.scenario(),
                benchmarkCase.expectedKind(),
                benchmarkCase.expectedErrorCode().orElse(null),
                apiResult.statusCode(),
                actualErrorCode,
                actualErrorMessage,
                actualVerdict,
                confidenceScore,
                apiResult.elapsedMs(),
                characterScore,
                originalCharacterScore,
                fields,
                benchmarkCase.groundTruth().mrzLines(),
                actualMrzLines,
                originalOcrMrzLines,
                mrzLinesCorrected,
                outcomePass,
                semanticDocumentPass,
                documentPass);
    }

    private List<FieldComparison> compareFields(GroundTruth groundTruth, Map<String, Object> payload) {
        return List.of(
                compare("document_type", groundTruth.documentType(), stringAt(payload, "identity_data.document_type").orElse(null)),
                compare("document_number", groundTruth.documentNumber(), stringAt(payload, "identity_data.document_number").orElse(null)),
                compare("issuing_state", groundTruth.issuingState(), stringAt(payload, "identity_data.issuing_state").orElse(null)),
                compare("nationality", groundTruth.nationality(), stringAt(payload, "identity_data.nationality").orElse(null)),
                compare("surname", groundTruth.surname(), stringAt(payload, "identity_data.surname").orElse(null)),
                compare("given_names", groundTruth.givenNames(), stringAt(payload, "identity_data.given_names").orElse(null)),
                compare("expired", String.valueOf(groundTruth.expired()), stringAt(payload, "identity_data.expired").orElse(null)));
    }

    private FieldComparison compare(String field, String expected, String actual) {
        return new FieldComparison(field, expected, actual, Objects.equals(expected, actual));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> valueAt(Map<String, Object> payload, String path) {
        Object current = payload;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            current = map.get(part);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private static Optional<String> stringAt(Map<String, Object> payload, String path) {
        return valueAt(payload, path).map(String::valueOf);
    }

    private static Optional<Double> doubleAt(Map<String, Object> payload, String path) {
        return valueAt(payload, path).flatMap(value -> {
            if (value instanceof Number number) {
                return Optional.of(number.doubleValue());
            }
            try {
                return Optional.of(Double.parseDouble(String.valueOf(value)));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Boolean> booleanAt(Map<String, Object> payload, String path) {
        return valueAt(payload, path).map(value -> {
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        });
    }

    @SuppressWarnings("unchecked")
    private static List<String> listAt(Map<String, Object> payload, String path) {
        Optional<Object> value = valueAt(payload, path);
        if (value.isEmpty() || !(value.get() instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Object>) list).stream().map(String::valueOf).toList();
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private record Options(
            Path manifestPath,
            String apiBaseUrl,
            String apiKey,
            Path reportPath,
            int timeoutSeconds,
            boolean dryRun) {
        static Options from(String[] args) {
            Map<String, String> options = new LinkedHashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException("Expected --name=value but got " + arg);
                }
                int equalsIndex = arg.indexOf('=');
                options.put(arg.substring(2, equalsIndex), arg.substring(equalsIndex + 1));
            }
            return new Options(
                    Path.of(required(options, "manifest")),
                    options.getOrDefault("api-base-url", "http://localhost:8080"),
                    options.getOrDefault("api-key", "dev-api-key"),
                    Path.of(options.getOrDefault("report", "benchmark-dataset/reports/accuracy-report.json")),
                    Integer.parseInt(options.getOrDefault("timeout-seconds", "30")),
                    Boolean.parseBoolean(options.getOrDefault("dry-run", "false")));
        }

        private static String required(Map<String, String> options, String name) {
            String value = options.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required option --" + name);
            }
            return value;
        }
    }

    private record Manifest(String datasetVersion, String generator, long seed, int caseCount, List<BenchmarkCase> cases) {
        static Manifest fromJson(String json) {
            Map<String, Object> root = castMap((Map<?, ?>) Json.parse(json));
            List<BenchmarkCase> cases = new ArrayList<>();
            for (Object rawCase : (List<?>) root.get("cases")) {
                cases.add(BenchmarkCase.from(castMap((Map<?, ?>) rawCase)));
            }
            return new Manifest(
                    string(root, "dataset_version"),
                    string(root, "generator"),
                    number(root, "seed").longValue(),
                    number(root, "case_count").intValue(),
                    List.copyOf(cases));
        }
    }

    private record BenchmarkCase(
            String id,
            String imagePath,
            String contentType,
            String format,
            String scenario,
            String expectedKind,
            String errorCode,
            GroundTruth groundTruth) {
        static BenchmarkCase from(Map<String, Object> map) {
            Map<String, Object> expected = castMap((Map<?, ?>) map.get("expected_outcome"));
            Map<String, Object> groundTruth = castMap((Map<?, ?>) map.get("ground_truth"));
            return new BenchmarkCase(
                    string(map, "id"),
                    string(map, "image_path"),
                    string(map, "content_type"),
                    string(map, "format"),
                    string(map, "scenario"),
                    string(expected, "kind"),
                    nullableString(expected, "error_code"),
                    GroundTruth.from(groundTruth));
        }

        Optional<String> expectedErrorCode() {
            return Optional.ofNullable(errorCode);
        }
    }

    private record GroundTruth(
            List<String> mrzLines,
            String documentType,
            String documentNumber,
            String issuingState,
            String nationality,
            String surname,
            String givenNames,
            boolean expired) {
        static GroundTruth from(Map<String, Object> map) {
            List<String> lines = ((List<?>) map.get("mrz_lines")).stream().map(String::valueOf).toList();
            return new GroundTruth(
                    lines,
                    string(map, "document_type"),
                    string(map, "document_number"),
                    string(map, "issuing_state"),
                    string(map, "nationality"),
                    string(map, "surname"),
                    string(map, "given_names"),
                    Boolean.parseBoolean(String.valueOf(map.get("expired"))));
        }
    }

    private record ApiResult(int statusCode, Map<String, Object> payload, long elapsedMs) {
    }

    private record CharacterScore(int correct, int total, boolean exact) {
        static CharacterScore compare(List<String> expectedLines, List<String> actualLines) {
            int correct = 0;
            int total = 0;
            int maxLines = Math.max(expectedLines.size(), actualLines.size());
            for (int lineIndex = 0; lineIndex < maxLines; lineIndex++) {
                String expected = lineIndex < expectedLines.size() ? expectedLines.get(lineIndex) : "";
                String actual = lineIndex < actualLines.size() ? actualLines.get(lineIndex) : "";
                int maxChars = Math.max(expected.length(), actual.length());
                total += maxChars;
                for (int index = 0; index < maxChars; index++) {
                    char expectedChar = index < expected.length() ? expected.charAt(index) : 0;
                    char actualChar = index < actual.length() ? actual.charAt(index) : 1;
                    if (expectedChar == actualChar) {
                        correct++;
                    }
                }
            }
            return new CharacterScore(correct, total, expectedLines.equals(actualLines));
        }

        double accuracy() {
            return total == 0 ? 0.0 : (double) correct / total;
        }
    }

    private record FieldComparison(String field, String expected, String actual, boolean pass) {
    }

    private record CaseResult(
            String id,
            String format,
            String scenario,
            String expectedKind,
            String expectedErrorCode,
            int statusCode,
            String actualErrorCode,
            String actualErrorMessage,
            String actualVerdict,
            Double confidenceScore,
            long elapsedMs,
            CharacterScore characterScore,
            CharacterScore originalCharacterScore,
            List<FieldComparison> fields,
            List<String> expectedMrzLines,
            List<String> actualMrzLines,
            List<String> originalOcrMrzLines,
            boolean mrzLinesCorrected,
            boolean outcomePass,
            boolean semanticDocumentPass,
            boolean documentPass) {
        int passedFields() {
            return (int) fields.stream().filter(FieldComparison::pass).count();
        }

        List<FieldComparison> failedFields() {
            return fields.stream().filter(field -> !field.pass()).toList();
        }
    }

    private record AccuracyReport(
            Manifest manifest,
            List<CaseResult> cases,
            boolean dryRun,
            Path reportPath,
            AggregateMetrics totals,
            Map<String, AggregateMetrics> byFormat,
            Map<String, AggregateMetrics> byScenario,
            Map<String, FieldMetrics> byField,
            CorrectionMetrics correctionMetrics) {
        static AccuracyReport from(Manifest manifest, List<CaseResult> cases, boolean dryRun, Path reportPath) {
            return new AccuracyReport(
                    manifest,
                    List.copyOf(cases),
                    dryRun,
                    reportPath,
                    AggregateMetrics.from(cases),
                    grouped(cases, CaseResult::format),
                    grouped(cases, CaseResult::scenario),
                    FieldMetrics.grouped(cases),
                    CorrectionMetrics.from(cases));
        }

        private static Map<String, AggregateMetrics> grouped(List<CaseResult> cases, java.util.function.Function<CaseResult, String> key) {
            Map<String, List<CaseResult>> groups = new TreeMap<>();
            for (CaseResult result : cases) {
                groups.computeIfAbsent(key.apply(result), ignored -> new ArrayList<>()).add(result);
            }
            Map<String, AggregateMetrics> metrics = new TreeMap<>();
            groups.forEach((name, values) -> metrics.put(name, AggregateMetrics.from(values)));
            return metrics;
        }

        void printSummary() {
            System.out.printf(Locale.ROOT, "Accuracy report written to %s%n", reportPath.toAbsolutePath());
            System.out.printf(Locale.ROOT, "Cases: %d, dry_run=%s%n", cases.size(), dryRun);
            System.out.printf(Locale.ROOT, "Character accuracy: %.4f%n", totals.characterAccuracy());
            System.out.printf(Locale.ROOT, "Field accuracy: %.4f%n", totals.fieldAccuracy());
            System.out.printf(Locale.ROOT, "Document accuracy: %.4f%n", totals.documentAccuracy());
            System.out.printf(Locale.ROOT, "Semantic document accuracy: %.4f%n", totals.semanticDocumentAccuracy());
            System.out.printf(Locale.ROOT, "Outcome accuracy: %.4f%n", totals.outcomeAccuracy());
        }

        String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            appendProperty(json, "generated_at", Instant.now().toString(), 1, true);
            appendProperty(json, "dry_run", dryRun, 1, true);
            appendProperty(json, "dataset_version", manifest.datasetVersion(), 1, true);
            appendProperty(json, "case_count", cases.size(), 1, true);
            json.append("  \"totals\": ").append(totals.toJson("  ")).append(",\n");
            json.append("  \"by_format\": ").append(metricsMapToJson(byFormat, "  ")).append(",\n");
            json.append("  \"by_scenario\": ").append(metricsMapToJson(byScenario, "  ")).append(",\n");
            json.append("  \"by_field\": ").append(fieldMetricsMapToJson(byField, "  ")).append(",\n");
            json.append("  \"correction_metrics\": ").append(correctionMetrics.toJson("  ")).append(",\n");
            json.append("  \"failures\": [\n");
            List<CaseResult> failures = cases.stream().filter(result -> !result.documentPass()).toList();
            for (int i = 0; i < failures.size(); i++) {
                json.append(caseFailureToJson(failures.get(i), "    "));
                if (i < failures.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}\n");
            return json.toString();
        }
    }

    private record FieldMetrics(String field, int passed, int total, int missingActual) {
        static Map<String, FieldMetrics> grouped(List<CaseResult> cases) {
            Map<String, MutableFieldMetrics> metrics = new TreeMap<>();
            for (CaseResult result : cases) {
                for (FieldComparison field : result.fields()) {
                    metrics.computeIfAbsent(field.field(), MutableFieldMetrics::new).add(field);
                }
            }

            Map<String, FieldMetrics> immutable = new TreeMap<>();
            metrics.forEach((field, value) -> immutable.put(field, value.toFieldMetrics()));
            return immutable;
        }

        double accuracy() {
            return total == 0 ? 0.0 : (double) passed / total;
        }

        String toJson(String indent) {
            String child = indent + "  ";
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            appendProperty(json, "field", field, 2, true);
            appendProperty(json, "accuracy", accuracy(), 2, true);
            appendProperty(json, "passed", passed, 2, true);
            appendProperty(json, "total", total, 2, true);
            appendProperty(json, "failed", total - passed, 2, true);
            appendProperty(json, "missing_actual", missingActual, 2, false);
            json.append(child.substring(2)).append("}");
            return json.toString();
        }
    }

    private static final class MutableFieldMetrics {
        private final String field;
        private int passed;
        private int total;
        private int missingActual;

        private MutableFieldMetrics(String field) {
            this.field = field;
        }

        private void add(FieldComparison comparison) {
            total++;
            if (comparison.pass()) {
                passed++;
            }
            if (comparison.actual() == null) {
                missingActual++;
            }
        }

        private FieldMetrics toFieldMetrics() {
            return new FieldMetrics(field, passed, total, missingActual);
        }
    }

    private record CorrectionMetrics(
            int correctedCases,
            int uncorrectedCases,
            int correctedDocuments,
            int uncorrectedDocuments,
            int correctedSemanticDocuments,
            int uncorrectedSemanticDocuments,
            int correctedOutcomes,
            int uncorrectedOutcomes,
            int validCorrectedCases,
            int lineImproved,
            int lineRegressed,
            int lineUnchanged,
            int exactBeforeCorrection,
            int exactAfterCorrection) {
        static CorrectionMetrics from(List<CaseResult> cases) {
            int correctedCases = 0;
            int uncorrectedCases = 0;
            int correctedDocuments = 0;
            int uncorrectedDocuments = 0;
            int correctedSemanticDocuments = 0;
            int uncorrectedSemanticDocuments = 0;
            int correctedOutcomes = 0;
            int uncorrectedOutcomes = 0;
            int validCorrectedCases = 0;
            int lineImproved = 0;
            int lineRegressed = 0;
            int lineUnchanged = 0;
            int exactBeforeCorrection = 0;
            int exactAfterCorrection = 0;

            for (CaseResult result : cases) {
                if (result.mrzLinesCorrected()) {
                    correctedCases++;
                    if (result.documentPass()) {
                        correctedDocuments++;
                    }
                    if (result.semanticDocumentPass()) {
                        correctedSemanticDocuments++;
                    }
                    if (result.outcomePass()) {
                        correctedOutcomes++;
                    }
                    if ("VALID_MRZ".equals(result.expectedKind())) {
                        validCorrectedCases++;
                        int before = result.originalCharacterScore().correct();
                        int after = result.characterScore().correct();
                        if (after > before) {
                            lineImproved++;
                        } else if (after < before) {
                            lineRegressed++;
                        } else {
                            lineUnchanged++;
                        }
                        if (result.originalCharacterScore().exact()) {
                            exactBeforeCorrection++;
                        }
                        if (result.characterScore().exact()) {
                            exactAfterCorrection++;
                        }
                    }
                } else {
                    uncorrectedCases++;
                    if (result.documentPass()) {
                        uncorrectedDocuments++;
                    }
                    if (result.semanticDocumentPass()) {
                        uncorrectedSemanticDocuments++;
                    }
                    if (result.outcomePass()) {
                        uncorrectedOutcomes++;
                    }
                }
            }

            return new CorrectionMetrics(
                    correctedCases,
                    uncorrectedCases,
                    correctedDocuments,
                    uncorrectedDocuments,
                    correctedSemanticDocuments,
                    uncorrectedSemanticDocuments,
                    correctedOutcomes,
                    uncorrectedOutcomes,
                    validCorrectedCases,
                    lineImproved,
                    lineRegressed,
                    lineUnchanged,
                    exactBeforeCorrection,
                    exactAfterCorrection);
        }

        double correctedDocumentAccuracy() {
            return correctedCases == 0 ? 0.0 : (double) correctedDocuments / correctedCases;
        }

        double uncorrectedDocumentAccuracy() {
            return uncorrectedCases == 0 ? 0.0 : (double) uncorrectedDocuments / uncorrectedCases;
        }

        double correctedSemanticDocumentAccuracy() {
            return correctedCases == 0 ? 0.0 : (double) correctedSemanticDocuments / correctedCases;
        }

        double uncorrectedSemanticDocumentAccuracy() {
            return uncorrectedCases == 0 ? 0.0 : (double) uncorrectedSemanticDocuments / uncorrectedCases;
        }

        double correctedOutcomeAccuracy() {
            return correctedCases == 0 ? 0.0 : (double) correctedOutcomes / correctedCases;
        }

        double uncorrectedOutcomeAccuracy() {
            return uncorrectedCases == 0 ? 0.0 : (double) uncorrectedOutcomes / uncorrectedCases;
        }

        String toJson(String indent) {
            String child = indent + "  ";
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            appendProperty(json, "corrected_cases", correctedCases, 2, true);
            appendProperty(json, "uncorrected_cases", uncorrectedCases, 2, true);
            appendProperty(json, "corrected_document_accuracy", correctedDocumentAccuracy(), 2, true);
            appendProperty(json, "uncorrected_document_accuracy", uncorrectedDocumentAccuracy(), 2, true);
            appendProperty(json, "corrected_semantic_document_accuracy", correctedSemanticDocumentAccuracy(), 2, true);
            appendProperty(json, "uncorrected_semantic_document_accuracy", uncorrectedSemanticDocumentAccuracy(), 2, true);
            appendProperty(json, "corrected_outcome_accuracy", correctedOutcomeAccuracy(), 2, true);
            appendProperty(json, "uncorrected_outcome_accuracy", uncorrectedOutcomeAccuracy(), 2, true);
            appendProperty(json, "valid_corrected_cases", validCorrectedCases, 2, true);
            appendProperty(json, "line_improved", lineImproved, 2, true);
            appendProperty(json, "line_regressed", lineRegressed, 2, true);
            appendProperty(json, "line_unchanged", lineUnchanged, 2, true);
            appendProperty(json, "exact_before_correction", exactBeforeCorrection, 2, true);
            appendProperty(json, "exact_after_correction", exactAfterCorrection, 2, false);
            json.append(child.substring(2)).append("}");
            return json.toString();
        }
    }

    private record AggregateMetrics(
            int caseCount,
            int passedOutcomes,
            int passedDocuments,
            int passedSemanticDocuments,
            int correctCharacters,
            int totalCharacters,
            int passedFields,
            int totalFields,
            long minLatencyMs,
            long maxLatencyMs,
            double averageLatencyMs) {
        static AggregateMetrics from(List<CaseResult> cases) {
            int totalChars = 0;
            int correctChars = 0;
            int totalFields = 0;
            int passedFields = 0;
            int passedOutcomes = 0;
            int passedDocuments = 0;
            int passedSemanticDocuments = 0;
            long minLatency = cases.isEmpty() ? 0 : Long.MAX_VALUE;
            long maxLatency = 0;
            long latencySum = 0;

            for (CaseResult result : cases) {
                totalChars += result.characterScore().total();
                correctChars += result.characterScore().correct();
                totalFields += result.fields().size();
                passedFields += result.passedFields();
                if (result.outcomePass()) {
                    passedOutcomes++;
                }
                if (result.documentPass()) {
                    passedDocuments++;
                }
                if (result.semanticDocumentPass()) {
                    passedSemanticDocuments++;
                }
                minLatency = Math.min(minLatency, result.elapsedMs());
                maxLatency = Math.max(maxLatency, result.elapsedMs());
                latencySum += result.elapsedMs();
            }

            return new AggregateMetrics(
                    cases.size(),
                    passedOutcomes,
                    passedDocuments,
                    passedSemanticDocuments,
                    correctChars,
                    totalChars,
                    passedFields,
                    totalFields,
                    minLatency == Long.MAX_VALUE ? 0 : minLatency,
                    maxLatency,
                    cases.isEmpty() ? 0.0 : (double) latencySum / cases.size());
        }

        double characterAccuracy() {
            return totalCharacters == 0 ? 0.0 : (double) correctCharacters / totalCharacters;
        }

        double fieldAccuracy() {
            return totalFields == 0 ? 0.0 : (double) passedFields / totalFields;
        }

        double documentAccuracy() {
            return caseCount == 0 ? 0.0 : (double) passedDocuments / caseCount;
        }

        double semanticDocumentAccuracy() {
            return caseCount == 0 ? 0.0 : (double) passedSemanticDocuments / caseCount;
        }

        double outcomeAccuracy() {
            return caseCount == 0 ? 0.0 : (double) passedOutcomes / caseCount;
        }

        String toJson(String indent) {
            String child = indent + "  ";
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            appendProperty(json, "case_count", caseCount, 2, true);
            appendProperty(json, "character_accuracy", characterAccuracy(), 2, true);
            appendProperty(json, "correct_characters", correctCharacters, 2, true);
            appendProperty(json, "total_characters", totalCharacters, 2, true);
            appendProperty(json, "field_accuracy", fieldAccuracy(), 2, true);
            appendProperty(json, "passed_fields", passedFields, 2, true);
            appendProperty(json, "total_fields", totalFields, 2, true);
            appendProperty(json, "document_accuracy", documentAccuracy(), 2, true);
            appendProperty(json, "passed_documents", passedDocuments, 2, true);
            appendProperty(json, "semantic_document_accuracy", semanticDocumentAccuracy(), 2, true);
            appendProperty(json, "passed_semantic_documents", passedSemanticDocuments, 2, true);
            appendProperty(json, "outcome_accuracy", outcomeAccuracy(), 2, true);
            appendProperty(json, "passed_outcomes", passedOutcomes, 2, true);
            appendProperty(json, "min_latency_ms", minLatencyMs, 2, true);
            appendProperty(json, "max_latency_ms", maxLatencyMs, 2, true);
            appendProperty(json, "average_latency_ms", averageLatencyMs, 2, false);
            json.append(child.substring(2)).append("}");
            return json.toString();
        }
    }

    private static String metricsMapToJson(Map<String, AggregateMetrics> metrics, String indent) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        int index = 0;
        for (Map.Entry<String, AggregateMetrics> entry : metrics.entrySet()) {
            json.append(indent).append("  \"").append(escape(entry.getKey())).append("\": ")
                    .append(entry.getValue().toJson(indent + "  "));
            if (index < metrics.size() - 1) {
                json.append(",");
            }
            json.append("\n");
            index++;
        }
        json.append(indent).append("}");
        return json.toString();
    }

    private static String fieldMetricsMapToJson(Map<String, FieldMetrics> metrics, String indent) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        int index = 0;
        for (Map.Entry<String, FieldMetrics> entry : metrics.entrySet()) {
            json.append(indent).append("  \"").append(escape(entry.getKey())).append("\": ")
                    .append(entry.getValue().toJson(indent + "  "));
            if (index < metrics.size() - 1) {
                json.append(",");
            }
            json.append("\n");
            index++;
        }
        json.append(indent).append("}");
        return json.toString();
    }

    private static String caseFailureToJson(CaseResult result, String indent) {
        StringBuilder json = new StringBuilder();
        json.append(indent).append("{\n");
        appendProperty(json, "id", result.id(), 3, true);
        appendProperty(json, "format", result.format(), 3, true);
        appendProperty(json, "scenario", result.scenario(), 3, true);
        appendProperty(json, "expected_kind", result.expectedKind(), 3, true);
        appendProperty(json, "expected_error_code", result.expectedErrorCode(), 3, true);
        appendProperty(json, "status_code", result.statusCode(), 3, true);
        appendProperty(json, "actual_error_code", result.actualErrorCode(), 3, true);
        appendProperty(json, "actual_error_message", result.actualErrorMessage(), 3, true);
        appendProperty(json, "actual_verdict", result.actualVerdict(), 3, true);
        appendProperty(json, "confidence_score", result.confidenceScore(), 3, true);
        appendProperty(json, "latency_ms", result.elapsedMs(), 3, true);
        appendProperty(json, "character_accuracy", result.characterScore().accuracy(), 3, true);
        appendProperty(json, "original_ocr_character_accuracy", result.originalCharacterScore().accuracy(), 3, true);
        appendProperty(json, "expected_mrz_lines", result.expectedMrzLines(), 3, true);
        appendProperty(json, "actual_mrz_lines", result.actualMrzLines(), 3, true);
        appendProperty(json, "original_ocr_mrz_lines", result.originalOcrMrzLines(), 3, true);
        appendProperty(json, "mrz_lines_corrected", result.mrzLinesCorrected(), 3, true);
        appendProperty(json, "field_failures", fieldFailuresToJsonValues(result.failedFields()), 3, true);
        appendProperty(json, "outcome_pass", result.outcomePass(), 3, true);
        appendProperty(json, "semantic_document_pass", result.semanticDocumentPass(), 3, true);
        appendProperty(json, "document_pass", result.documentPass(), 3, false);
        json.append(indent).append("}");
        return json.toString();
    }

    private static List<Map<String, Object>> fieldFailuresToJsonValues(List<FieldComparison> fields) {
        List<Map<String, Object>> failures = new ArrayList<>();
        for (FieldComparison field : fields) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("field", field.field());
            values.put("expected", field.expected());
            values.put("actual", field.actual());
            failures.add(values);
        }
        return failures;
    }

    private static void appendProperty(StringBuilder json, String name, Object value, int levels, boolean comma) {
        json.append("  ".repeat(levels))
                .append("\"").append(escape(name)).append("\": ")
                .append(toJsonValue(value));
        if (comma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder json = new StringBuilder();
            json.append("{");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index > 0) {
                    json.append(", ");
                }
                json.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\": ")
                        .append(toJsonValue(entry.getValue()));
                index++;
            }
            json.append("}");
            return json.toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder json = new StringBuilder();
            json.append("[");
            int index = 0;
            for (Object item : values) {
                if (index > 0) {
                    json.append(", ");
                }
                json.append(toJsonValue(item));
                index++;
            }
            json.append("]");
            return json.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String string(Map<String, Object> map, String key) {
        return String.valueOf(map.get(key));
    }

    private static String nullableString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Number number(Map<String, Object> map, String key) {
        return (Number) map.get(key);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static final class Json {
        static Object parse(String json) {
            return new Parser(json).parse();
        }

        private static final class Parser {
            private final String json;
            private int index;

            private Parser(String json) {
                this.json = json;
            }

            Object parse() {
                Object value = parseValue();
                skipWhitespace();
                if (index != json.length()) {
                    throw error("Unexpected trailing content");
                }
                return value;
            }

            private Object parseValue() {
                skipWhitespace();
                if (index >= json.length()) {
                    throw error("Unexpected end of JSON");
                }
                char value = json.charAt(index);
                return switch (value) {
                    case '{' -> parseObject();
                    case '[' -> parseArray();
                    case '"' -> parseString();
                    case 't' -> parseLiteral("true", Boolean.TRUE);
                    case 'f' -> parseLiteral("false", Boolean.FALSE);
                    case 'n' -> parseLiteral("null", null);
                    default -> parseNumber();
                };
            }

            private Map<String, Object> parseObject() {
                expect('{');
                Map<String, Object> object = new LinkedHashMap<>();
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return object;
                }
                while (true) {
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    object.put(key, parseValue());
                    skipWhitespace();
                    if (peek('}')) {
                        expect('}');
                        return object;
                    }
                    expect(',');
                    skipWhitespace();
                }
            }

            private List<Object> parseArray() {
                expect('[');
                List<Object> array = new ArrayList<>();
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return array;
                }
                while (true) {
                    array.add(parseValue());
                    skipWhitespace();
                    if (peek(']')) {
                        expect(']');
                        return array;
                    }
                    expect(',');
                }
            }

            private String parseString() {
                expect('"');
                StringBuilder builder = new StringBuilder();
                while (index < json.length()) {
                    char value = json.charAt(index++);
                    if (value == '"') {
                        return builder.toString();
                    }
                    if (value == '\\') {
                        if (index >= json.length()) {
                            throw error("Incomplete escape sequence");
                        }
                        char escaped = json.charAt(index++);
                        switch (escaped) {
                            case '"' -> builder.append('"');
                            case '\\' -> builder.append('\\');
                            case '/' -> builder.append('/');
                            case 'b' -> builder.append('\b');
                            case 'f' -> builder.append('\f');
                            case 'n' -> builder.append('\n');
                            case 'r' -> builder.append('\r');
                            case 't' -> builder.append('\t');
                            case 'u' -> {
                                String hex = json.substring(index, index + 4);
                                builder.append((char) Integer.parseInt(hex, 16));
                                index += 4;
                            }
                            default -> throw error("Unsupported escape sequence: " + escaped);
                        }
                    } else {
                        builder.append(value);
                    }
                }
                throw error("Unterminated string");
            }

            private Object parseNumber() {
                int start = index;
                if (peek('-')) {
                    index++;
                }
                while (index < json.length() && Character.isDigit(json.charAt(index))) {
                    index++;
                }
                boolean decimal = false;
                if (peek('.')) {
                    decimal = true;
                    index++;
                    while (index < json.length() && Character.isDigit(json.charAt(index))) {
                        index++;
                    }
                }
                if (peek('e') || peek('E')) {
                    decimal = true;
                    index++;
                    if (peek('+') || peek('-')) {
                        index++;
                    }
                    while (index < json.length() && Character.isDigit(json.charAt(index))) {
                        index++;
                    }
                }
                String number = json.substring(start, index);
                return decimal ? Double.parseDouble(number) : Long.parseLong(number);
            }

            private Object parseLiteral(String literal, Object value) {
                if (!json.startsWith(literal, index)) {
                    throw error("Expected " + literal);
                }
                index += literal.length();
                return value;
            }

            private void skipWhitespace() {
                while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                    index++;
                }
            }

            private boolean peek(char expected) {
                return index < json.length() && json.charAt(index) == expected;
            }

            private void expect(char expected) {
                if (!peek(expected)) {
                    throw error("Expected '" + expected + "'");
                }
                index++;
            }

            private IllegalArgumentException error(String message) {
                return new IllegalArgumentException(message + " at JSON offset " + index);
            }
        }
    }
}
