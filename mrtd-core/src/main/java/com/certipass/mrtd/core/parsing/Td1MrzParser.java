package com.certipass.mrtd.core.parsing;

import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import java.util.List;

/**
 * Parser for TD1 identity card MRZs, three lines of 30 characters.
 */
public final class Td1MrzParser implements MrzParser {
    private final NameParser nameParser;
    private final MrzDateParser dateParser;

    public Td1MrzParser() {
        this(new NameParser(), new MrzDateParser());
    }

    public Td1MrzParser(NameParser nameParser, MrzDateParser dateParser) {
        this.nameParser = nameParser;
        this.dateParser = dateParser;
    }

    @Override
    public IdentityData parse(List<String> mrzLines) {
        String line1 = mrzLines.get(0);
        String line2 = mrzLines.get(1);
        String line3 = mrzLines.get(2);
        ParsedName name = nameParser.parse(line3);

        return new IdentityData(
                DocumentType.ID_CARD,
                MrzFieldCleaner.documentCode(line1.substring(0, 2)),
                MrzFieldCleaner.stateCode(line1.substring(2, 5)),
                name.surname(),
                name.givenNames(),
                MrzFieldCleaner.compact(line1.substring(5, 14)),
                MrzFieldCleaner.stateCode(line2.substring(15, 18)),
                dateParser.parseBirthDate(line2.substring(0, 6)),
                MrzFieldCleaner.text(line2.substring(7, 8)),
                dateParser.parseExpiryDate(line2.substring(8, 14)),
                MrzFieldCleaner.compact(line1.substring(15, 30) + line2.substring(18, 29)),
                false);
    }
}
