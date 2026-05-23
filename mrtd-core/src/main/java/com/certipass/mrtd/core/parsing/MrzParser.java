package com.certipass.mrtd.core.parsing;

import com.certipass.mrtd.core.model.IdentityData;
import java.util.List;

/**
 * Parser for a specific MRZ format.
 */
public interface MrzParser {
    IdentityData parse(List<String> mrzLines);
}
