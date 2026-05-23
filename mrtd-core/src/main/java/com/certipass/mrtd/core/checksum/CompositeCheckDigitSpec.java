package com.certipass.mrtd.core.checksum;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.util.List;

/**
 * Substring ranges used to compute a format-specific composite check digit.
 */
public record CompositeCheckDigitSpec(MrzFormat format, List<FieldSlice> slices) {
    public CompositeCheckDigitSpec {
        slices = List.copyOf(slices);
    }

    public record FieldSlice(int lineIndex, int startInclusive, int endExclusive) {
    }
}
