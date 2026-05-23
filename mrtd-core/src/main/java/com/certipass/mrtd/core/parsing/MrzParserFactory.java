package com.certipass.mrtd.core.parsing;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry that resolves a parser for the detected MRZ format.
 */
public final class MrzParserFactory {
    private final Map<MrzFormat, MrzParser> parsers;

    public MrzParserFactory(Map<MrzFormat, MrzParser> parsers) {
        this.parsers = new EnumMap<>(parsers);
    }

    public MrzParser parserFor(MrzFormat format) {
        MrzParser parser = parsers.get(format);
        if (parser == null) {
            throw new IllegalArgumentException("No parser registered for format " + format);
        }
        return parser;
    }
}
