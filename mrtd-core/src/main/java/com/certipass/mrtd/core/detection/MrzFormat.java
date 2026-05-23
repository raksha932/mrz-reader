package com.certipass.mrtd.core.detection;

/**
 * Supported ICAO MRZ line layouts.
 */
public enum MrzFormat {
    TD1(3, 30),
    TD2(2, 36),
    TD3(2, 44);

    private final int lineCount;
    private final int charactersPerLine;

    MrzFormat(int lineCount, int charactersPerLine) {
        this.lineCount = lineCount;
        this.charactersPerLine = charactersPerLine;
    }

    public int lineCount() {
        return lineCount;
    }

    public int charactersPerLine() {
        return charactersPerLine;
    }
}
