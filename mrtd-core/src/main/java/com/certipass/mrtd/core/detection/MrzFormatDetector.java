package com.certipass.mrtd.core.detection;

import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.fieldlogic.StateCodeRegistry;
import java.util.List;

/**
 * Detects ICAO TD1, TD2, and TD3 MRZ formats from normalized OCR lines.
 */
public final class MrzFormatDetector {
    private final StateCodeRegistry stateCodeRegistry = new StateCodeRegistry();

    public MrzFormatDetectionResult detect(List<String> mrzLines) {
        if (mrzLines == null || mrzLines.isEmpty()) {
            return incomplete("MRZ text is empty");
        }

        if (hasLayout(mrzLines, 2, 44)) {
            String firstLine = mrzLines.get(0);
            if (startsWith(firstLine, "V")) {
                return unsupported("MRV-A visa format is not supported");
            }
            if (startsWith(firstLine, "P")) {
                return new MrzFormatDetectionResult.Detected(MrzFormat.TD3);
            }
            return incomplete("Two 44-character lines did not match TD3 document code");
        }

        if (hasLayout(mrzLines, 2, 36)) {
            String firstLine = mrzLines.get(0);
            if (startsWith(firstLine, "V")) {
                return unsupported("MRV-B visa format is not supported");
            }
            if (startsLikeTd2DocumentCode(firstLine) && hasAcceptedStateCode(firstLine, 2)) {
                return new MrzFormatDetectionResult.Detected(MrzFormat.TD2);
            }
            return incomplete("Two 36-character lines did not match TD2 document code and issuing state");
        }

        if (hasLayout(mrzLines, 3, 30)) {
            String firstLine = mrzLines.get(0);
            if (startsWith(firstLine, "I") || startsWith(firstLine, "A") || startsWith(firstLine, "C")) {
                return new MrzFormatDetectionResult.Detected(MrzFormat.TD1);
            }
            return incomplete("Three 30-character lines did not match TD1 document code");
        }

        return incomplete("MRZ line count or line length does not match TD1, TD2, or TD3");
    }

    private boolean hasLayout(List<String> lines, int lineCount, int charactersPerLine) {
        return lines.size() == lineCount && lines.stream().allMatch(line -> line.length() == charactersPerLine);
    }

    private boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private boolean startsLikeTd2DocumentCode(String value) {
        return value != null
                && !value.isEmpty()
                && (value.charAt(0) == 'I' || value.charAt(0) == 'A' || value.charAt(0) == 'C');
    }

    private boolean hasAcceptedStateCode(String value, int startIndex) {
        return value != null
                && value.length() >= startIndex + 3
                && stateCodeRegistry.isAccepted(value.substring(startIndex, startIndex + 3));
    }

    private MrzFormatDetectionResult.Failure incomplete(String message) {
        return new MrzFormatDetectionResult.Failure(ErrorCode.MRZ_INCOMPLETE, message);
    }

    private MrzFormatDetectionResult.Failure unsupported(String message) {
        return new MrzFormatDetectionResult.Failure(ErrorCode.UNSUPPORTED_FORMAT, message);
    }
}
