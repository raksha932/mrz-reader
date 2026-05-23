package com.certipass.mrtd.core.parsing;

import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import java.util.List;

/**
 * Parser for TD2 official travel document MRZs, two lines of 36 characters.
 */
public final class Td2MrzParser implements MrzParser {
    private final NameParser nameParser;
    private final MrzDateParser dateParser;

    public Td2MrzParser() {
        this(new NameParser(), new MrzDateParser());
    }

    public Td2MrzParser(NameParser nameParser, MrzDateParser dateParser) {
        this.nameParser = nameParser;
        this.dateParser = dateParser;
    }

    @Override
    public IdentityData parse(List<String> mrzLines) {
        String line1 = mrzLines.get(0);
        String line2 = mrzLines.get(1);
        ParsedName name = nameParser.parse(line1.substring(5, 36));

        return new IdentityData(
                DocumentType.OFFICIAL_TRAVEL_DOCUMENT,
                MrzFieldCleaner.documentCode(line1.substring(0, 2)),
                MrzFieldCleaner.stateCode(line1.substring(2, 5)),
                name.surname(),
                name.givenNames(),
                MrzFieldCleaner.compact(line2.substring(0, 9)),
                MrzFieldCleaner.stateCode(line2.substring(10, 13)),
                dateParser.parseBirthDate(line2.substring(13, 19)),
                MrzFieldCleaner.text(line2.substring(20, 21)),
                dateParser.parseExpiryDate(line2.substring(21, 27)),
                MrzFieldCleaner.compact(line2.substring(28, 35)),
                false);
    }
}
