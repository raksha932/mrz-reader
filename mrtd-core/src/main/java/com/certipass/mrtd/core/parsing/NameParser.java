package com.certipass.mrtd.core.parsing;

/**
 * Parses ICAO name fields encoded as PRIMARY&lt;&lt;GIVEN&lt;NAMES.
 */
public final class NameParser {
    private final NameOcrCorrector nameOcrCorrector;

    public NameParser() {
        this(new NameOcrCorrector());
    }

    NameParser(NameOcrCorrector nameOcrCorrector) {
        this.nameOcrCorrector = nameOcrCorrector;
    }

    public ParsedName parse(String mrzNameField) {
        String trimmed = trimFillers(mrzNameField);
        int separator = trimmed.indexOf("<<");
        if (separator < 0) {
            return new ParsedName(cleanNamePart(trimmed), "");
        }

        String surname = cleanNamePart(trimmed.substring(0, separator));
        String givenNames = cleanNamePart(trimmed.substring(separator + 2));
        return new ParsedName(surname, givenNames);
    }

    private String trimFillers(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '<') {
            end--;
        }
        return value.substring(0, end);
    }

    private String cleanNamePart(String value) {
        String cleaned = value.replace('<', ' ').trim().replaceAll(" +", " ");
        return nameOcrCorrector.correctNamePart(cleaned);
    }
}
