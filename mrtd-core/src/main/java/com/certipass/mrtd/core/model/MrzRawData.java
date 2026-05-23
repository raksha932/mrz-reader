package com.certipass.mrtd.core.model;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.util.List;

/**
 * Raw MRZ text retained alongside the parsed result.
 */
public record MrzRawData(MrzFormat format, List<String> lines, List<String> ocrLines) {
    public MrzRawData(MrzFormat format, List<String> lines) {
        this(format, lines, lines);
    }

    public MrzRawData {
        lines = List.copyOf(lines);
        ocrLines = ocrLines == null || ocrLines.isEmpty() ? lines : List.copyOf(ocrLines);
    }

    public boolean corrected() {
        return !lines.equals(ocrLines);
    }
}
