package com.certipass.mrtd.core.detection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Repairs small OCR shape errors before strict MRZ format detection.
 */
public final class MrzLineNormalizer {
    private static final int REPAIR_TOLERANCE = 4;

    public List<String> normalize(List<String> mrzLines) {
        if (mrzLines == null || mrzLines.isEmpty()) {
            return List.of();
        }

        List<String> cleaned = mrzLines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(this::cleanLine)
                .filter(line -> !line.isBlank())
                .toList();

        cleaned = selectBestMrzWindow(cleaned).orElse(cleaned);

        if (cleaned.size() == 2) {
            if (canRepair(cleaned, 44)) {
                List<String> recovered = recoverDamagedTd3OrVisaPrefix(cleaned);
                if (!recovered.equals(cleaned) && startsLikeTd3OrVisa(recovered.getFirst())) {
                    return repairToLength(recovered, 44);
                }
            }
            if (canRepair(cleaned, 44) && startsLikeTd3OrVisa(cleaned.getFirst())) {
                return repairToLength(cleaned, 44);
            }
            if (canRepair(cleaned, 36)) {
                return repairToLength(cleaned, 36);
            }
        }

        if (cleaned.size() == 3 && canRepair(cleaned, 30)) {
            return repairToLength(cleaned, 30);
        }

        return cleaned;
    }

    private List<String> recoverDamagedTd3OrVisaPrefix(List<String> lines) {
        String firstLine = lines.getFirst();
        String secondLine = lines.get(1);
        if (hasDoubleNameSeparator(firstLine) == 0) {
            return lines;
        }

        if (secondLine.startsWith("V")) {
            return replaceFirstLine(lines, replaceDocumentCode(firstLine, 'V'));
        }
        if (!looksLikeTd3DataLine(secondLine)) {
            return lines;
        }
        if (firstLine.startsWith("P") || firstLine.startsWith("V")) {
            return lines;
        }
        if (looksLikeTwoCharacterPassportCode(firstLine, secondLine)) {
            return replaceFirstLine(lines, "PP" + firstLine.substring(2));
        }
        if (firstLine.startsWith("F<")) {
            return replaceFirstLine(lines, replaceDocumentCode(firstLine, 'P'));
        }
        if (looksLikeStateCodePrefix(firstLine)) {
            return replaceFirstLine(lines, "P<" + firstLine);
        }
        return lines;
    }

    private boolean looksLikeTwoCharacterPassportCode(String firstLine, String secondLine) {
        if (firstLine.length() < 5 || secondLine.length() < 13) {
            return false;
        }
        String firstLineState = normalizeStateLikeCode(firstLine.substring(2, 5));
        String dataLineState = normalizeStateLikeCode(secondLine.substring(10, 13));
        return firstLine.charAt(1) != '<'
                && firstLineState.equals(dataLineState)
                && firstLineState.chars().allMatch(character -> character >= 'A' && character <= 'Z');
    }

    private String normalizeStateLikeCode(String value) {
        return value
                .replace('0', 'O')
                .replace('1', 'I')
                .replace('5', 'S')
                .replace('6', 'G')
                .replace('7', 'T')
                .replace('8', 'B');
    }

    private String replaceDocumentCode(String line, char documentCode) {
        if (line.length() >= 2 && line.charAt(1) == '<') {
            return documentCode + line.substring(1);
        }
        return documentCode + "<" + line;
    }

    private List<String> replaceFirstLine(List<String> lines, String firstLine) {
        return List.of(firstLine, lines.get(1));
    }

    private boolean looksLikeStateCodePrefix(String line) {
        if (line.length() < 5 || line.charAt(1) == '<') {
            return false;
        }
        int separator = line.indexOf("<<");
        if (separator < 5) {
            return false;
        }
        int stateLike = 0;
        for (int index = 0; index < 3; index++) {
            if (isLetterOrStateDigit(line.charAt(index))) {
                stateLike++;
            }
        }
        return stateLike >= 2;
    }

    private boolean isLetterOrStateDigit(char value) {
        return (value >= 'A' && value <= 'Z') || value == '0' || value == '1' || value == '5' || value == '7' || value == '8';
    }

    private boolean looksLikeTd3DataLine(String line) {
        return line.length() >= 40
                && isMrzCharacterAt(line, 9)
                && isMrzCharacterAt(line, 10)
                && isMrzCharacterAt(line, 19)
                && isMrzCharacterAt(line, 20)
                && isMrzCharacterAt(line, 27);
    }

    private Optional<List<String>> selectBestMrzWindow(List<String> lines) {
        if (lines.size() <= 2) {
            return Optional.empty();
        }

        List<WindowCandidate> candidates = new ArrayList<>();
        for (int start = 0; start <= lines.size() - 2; start++) {
            List<String> window = lines.subList(start, start + 2);
            candidate(window, start, 44, startsLikeTd3OrVisa(window.getFirst()), 30).ifPresent(candidates::add);
            candidate(window, start, 36, startsLikeTd2(window.getFirst()), 20).ifPresent(candidates::add);
        }
        for (int start = 0; start <= lines.size() - 3; start++) {
            List<String> window = lines.subList(start, start + 3);
            candidate(window, start, 30, startsLikeTd1(window.getFirst()), 10).ifPresent(candidates::add);
        }

        return candidates.stream()
                .max(Comparator.comparingInt(WindowCandidate::score)
                        .thenComparing(candidate -> -candidate.startIndex()))
                .map(WindowCandidate::lines);
    }

    private Optional<WindowCandidate> candidate(
            List<String> lines,
            int startIndex,
            int targetLength,
            boolean startsLikeFormat,
            int formatBonus) {
        if (!startsLikeFormat || !canRepair(lines, targetLength)) {
            return Optional.empty();
        }
        int lengthScore = lines.stream()
                .mapToInt(line -> REPAIR_TOLERANCE - Math.abs(line.length() - targetLength))
                .sum();
        int structureScore = structureScore(lines, targetLength);
        return Optional.of(new WindowCandidate(List.copyOf(lines), startIndex, formatBonus + lengthScore + structureScore));
    }

    private String cleanLine(String line) {
        StringBuilder cleaned = new StringBuilder(line.length());
        for (int index = 0; index < line.length(); index++) {
            char value = Character.toUpperCase(line.charAt(index));
            if ((value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9') || value == '<') {
                cleaned.append(value);
            }
        }
        return cleaned.toString();
    }

    private boolean canRepair(List<String> lines, int targetLength) {
        return lines.stream()
                .allMatch(line -> line.length() >= targetLength - REPAIR_TOLERANCE
                        && line.length() <= targetLength + REPAIR_TOLERANCE);
    }

    private boolean startsLikeTd3OrVisa(String firstLine) {
        return firstLine.startsWith("P") || firstLine.startsWith("V");
    }

    private boolean startsLikeTd2(String firstLine) {
        return startsLikeTd1(firstLine) || firstLine.startsWith("V");
    }

    private boolean startsLikeTd1(String firstLine) {
        return firstLine.startsWith("I") || firstLine.startsWith("A") || firstLine.startsWith("C");
    }

    private int structureScore(List<String> lines, int targetLength) {
        return switch (targetLength) {
            case 44 -> td3StructureScore(lines);
            case 36 -> td2StructureScore(lines);
            case 30 -> td1StructureScore(lines);
            default -> 0;
        };
    }

    private int td3StructureScore(List<String> lines) {
        return startsWithAt(lines.getFirst(), '<', 1)
                + hasDoubleNameSeparator(lines.getFirst())
                + hasMrzCharsAt(lines.get(1), List.of(9, 19, 27, 42, 43));
    }

    private int td2StructureScore(List<String> lines) {
        return startsWithAt(lines.getFirst(), '<', 1)
                + hasDoubleNameSeparator(lines.getFirst())
                + hasMrzCharsAt(lines.get(1), List.of(9, 19, 27, 35));
    }

    private int td1StructureScore(List<String> lines) {
        return startsWithAt(lines.getFirst(), '<', 1)
                + hasDoubleNameSeparator(lines.get(2))
                + hasMrzCharsAt(lines.getFirst(), List.of(14))
                + hasMrzCharsAt(lines.get(1), List.of(6, 14, 29));
    }

    private int startsWithAt(String line, char value, int index) {
        return line.length() > index && line.charAt(index) == value ? 2 : 0;
    }

    private int hasDoubleNameSeparator(String line) {
        return line.contains("<<") ? 2 : 0;
    }

    private int hasMrzCharsAt(String line, List<Integer> indexes) {
        int score = 0;
        for (int index : indexes) {
            if (isMrzCharacterAt(line, index)) {
                score++;
            }
        }
        return score;
    }

    private boolean isMrzCharacterAt(String line, int index) {
        return line.length() > index && isMrzCharacter(line.charAt(index));
    }

    private boolean isMrzCharacter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9') || value == '<';
    }

    private List<String> repairToLength(List<String> lines, int targetLength) {
        List<String> repaired = new java.util.ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            repaired.add(repairLine(lines.get(index), targetLength, isNameLine(index, lines.size())));
        }
        return List.copyOf(repaired);
    }

    private boolean isNameLine(int lineIndex, int lineCount) {
        return (lineCount == 2 && lineIndex == 0) || (lineCount == 3 && lineIndex == 2);
    }

    private String repairLine(String line, int targetLength, boolean nameLine) {
        if (nameLine) {
            line = replaceTrailingFillerNoise(line);
        }
        if (line.length() > targetLength) {
            return line.substring(0, targetLength);
        }
        if (line.length() < targetLength) {
            return line + "<".repeat(targetLength - line.length());
        }
        return line;
    }

    private String replaceTrailingFillerNoise(String line) {
        int fillerRunStart = firstTrailingFillerRun(line);
        if (fillerRunStart < 0) {
            return line;
        }
        return line.substring(0, fillerRunStart) + "<".repeat(line.length() - fillerRunStart);
    }

    private int firstTrailingFillerRun(String line) {
        int minimumStart = Math.max(0, line.length() - 20);
        int runLength = 0;
        for (int index = minimumStart; index < line.length(); index++) {
            if (line.charAt(index) == '<') {
                runLength++;
                if (runLength == 4) {
                    return index - 3;
                }
            } else {
                runLength = 0;
            }
        }
        return -1;
    }

    private record WindowCandidate(List<String> lines, int startIndex, int score) {
    }
}
