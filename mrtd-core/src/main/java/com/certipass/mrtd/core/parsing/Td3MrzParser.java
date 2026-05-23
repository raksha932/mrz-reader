package com.certipass.mrtd.core.parsing;

import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import java.util.List;

/**
 * Parser for TD3 passport MRZs, two lines of 44 characters.
 */
public final class Td3MrzParser implements MrzParser {
    private final NameParser nameParser;
    private final MrzDateParser dateParser;

    public Td3MrzParser() {
        this(new NameParser(), new MrzDateParser());
    }

    public Td3MrzParser(NameParser nameParser, MrzDateParser dateParser) {
        this.nameParser = nameParser;
        this.dateParser = dateParser;
    }

    @Override
    public IdentityData parse(List<String> mrzLines) {
        String line1 = mrzLines.get(0);
        String line2 = mrzLines.get(1);
        ParsedName name = nameParser.parse(line1.substring(5, 44));

        return new IdentityData(
                DocumentType.PASSPORT,
                MrzFieldCleaner.documentCode(line1.substring(0, 2)),
                MrzFieldCleaner.stateCode(line1.substring(2, 5)),
                name.surname(),
                name.givenNames(),
                MrzFieldCleaner.compact(line2.substring(0, 9)),
                MrzFieldCleaner.stateCode(line2.substring(10, 13)),
                dateParser.parseBirthDate(line2.substring(13, 19)),
                MrzFieldCleaner.text(line2.substring(20, 21)),
                dateParser.parseExpiryDate(line2.substring(21, 27)),
                MrzFieldCleaner.compact(line2.substring(28, 42)),
                false);
    }
}
