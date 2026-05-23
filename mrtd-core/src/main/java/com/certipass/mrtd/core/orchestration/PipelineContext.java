package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.checksum.CheckDigitResult;
import com.certipass.mrtd.core.detection.MrzFormat;
import com.certipass.mrtd.core.fieldlogic.FieldLogicResult;
import com.certipass.mrtd.core.image.ImageMetadata;
import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.ocr.OcrResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state passed through the core MRZ reading pipeline.
 */
public final class PipelineContext {
    private final byte[] inputImage;
    private ImageMetadata imageMetadata;
    private OcrResult ocrResult;
    private List<String> originalOcrLines = List.of();
    private MrzFormat mrzFormat;
    private IdentityData identityData;
    private List<CheckDigitResult> checkDigitResults = List.of();
    private List<FieldLogicResult> fieldLogicResults = List.of();
    private final List<String> warnings = new ArrayList<>();
    private long processingTimeMs;

    public PipelineContext(byte[] inputImage) {
        this.inputImage = inputImage == null ? null : inputImage.clone();
    }

    public byte[] inputImage() {
        return inputImage == null ? null : inputImage.clone();
    }

    public ImageMetadata imageMetadata() {
        return imageMetadata;
    }

    public void imageMetadata(ImageMetadata imageMetadata) {
        this.imageMetadata = imageMetadata;
    }

    public OcrResult ocrResult() {
        return ocrResult;
    }

    public void ocrResult(OcrResult ocrResult) {
        this.ocrResult = ocrResult;
    }

    public List<String> originalOcrLines() {
        return originalOcrLines;
    }

    public void originalOcrLines(List<String> originalOcrLines) {
        this.originalOcrLines = originalOcrLines == null ? List.of() : List.copyOf(originalOcrLines);
    }

    public MrzFormat mrzFormat() {
        return mrzFormat;
    }

    public void mrzFormat(MrzFormat mrzFormat) {
        this.mrzFormat = mrzFormat;
    }

    public IdentityData identityData() {
        return identityData;
    }

    public void identityData(IdentityData identityData) {
        this.identityData = identityData;
    }

    public List<CheckDigitResult> checkDigitResults() {
        return checkDigitResults;
    }

    public void checkDigitResults(List<CheckDigitResult> checkDigitResults) {
        this.checkDigitResults = List.copyOf(checkDigitResults);
    }

    public List<FieldLogicResult> fieldLogicResults() {
        return fieldLogicResults;
    }

    public void fieldLogicResults(List<FieldLogicResult> fieldLogicResults) {
        this.fieldLogicResults = List.copyOf(fieldLogicResults);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public long processingTimeMs() {
        return processingTimeMs;
    }

    public void processingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
}
