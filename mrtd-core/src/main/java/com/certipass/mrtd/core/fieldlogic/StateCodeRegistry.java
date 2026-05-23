package com.certipass.mrtd.core.fieldlogic;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * ISO 3166-1 alpha-3 state codes plus ICAO/MRZ-specific exceptions accepted by this product.
 */
public final class StateCodeRegistry {
    private static final Set<String> ACCEPTED_CODES = buildAcceptedCodes();

    public boolean isAccepted(String code) {
        return code != null && ACCEPTED_CODES.contains(code);
    }

    private static Set<String> buildAcceptedCodes() {
        Set<String> codes = new HashSet<>(Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3));
        codes.add("UTO");
        codes.add("UNO");
        codes.add("UNA");
        codes.add("GBD");
        codes.add("GBN");
        codes.add("GBO");
        codes.add("GBP");
        return Set.copyOf(codes);
    }
}
