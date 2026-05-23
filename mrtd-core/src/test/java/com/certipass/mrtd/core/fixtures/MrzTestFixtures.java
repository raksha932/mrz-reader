package com.certipass.mrtd.core.fixtures;

import java.util.List;

/**
 * Static MRZ examples used by the core module test suite.
 */
public final class MrzTestFixtures {
    public static final List<List<String>> VALID_TD3 = List.of(
            List.of("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10"),
            List.of("P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<", "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06"),
            List.of("P<GBRSMITH<<JOHN<JAMES<<<<<<<<<<<<<<<<<<<<<<", "1234567897GBR8001014M3001019<<<<<<<<<<<<<<08"),
            List.of("P<USADOE<<JANE<ALICE<<<<<<<<<<<<<<<<<<<<<<<<", "9876543213USA8505051F3105054<<<<<<<<<<<<<<00"),
            List.of("P<CANMARTIN<<EMMA<ROSE<<<<<<<<<<<<<<<<<<<<<<", "AB12345671CAN9202209F3202207<<<<<<<<<<<<<<02")
    );

    public static final List<List<String>> VALID_TD2 = List.of(
            List.of("I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<", "D0012345<0UTO7408122F1204159<<<<<<<0"),
            List.of("I<NGAOKONKWO<<CHUKWUEMEKA<<<<<<<<<<<", "ABC12345<4NGA9008157M3008144<<<<<<<4"),
            List.of("I<GBRSMITH<<JOHN<JAMES<<<<<<<<<<<<<<", "12345678<8GBR8001014M3001019<<<<<<<6"),
            List.of("I<USADOE<<JANE<ALICE<<<<<<<<<<<<<<<<", "98765432<2USA8505051F3105054<<<<<<<2"),
            List.of("I<CANMARTIN<<EMMA<ROSE<<<<<<<<<<<<<<", "AB123456<4CAN9202209F3202207<<<<<<<6")
    );

    public static final List<List<String>> VALID_TD1 = List.of(
            List.of("I<UTOD231458907<<<<<<<<<<<<<<<", "7408122F1204159UTO<<<<<<<<<<<6", "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"),
            List.of("I<NGAABC1234560<<<<<<<<<<<<<<<", "9008157M3008144NGA<<<<<<<<<<<2", "OKONKWO<<CHUKWUEMEKA<JOHN<<<<<"),
            List.of("I<GBR1234567897<<<<<<<<<<<<<<<", "8001014M3001019GBR<<<<<<<<<<<8", "SMITH<<JOHN<JAMES<<<<<<<<<<<<<"),
            List.of("I<USA9876543213<<<<<<<<<<<<<<<", "8505051F3105054USA<<<<<<<<<<<0", "DOE<<JANE<ALICE<<<<<<<<<<<<<<<"),
            List.of("I<CANAB12345671<<<<<<<<<<<<<<<", "9202209F3202207CAN<<<<<<<<<<<2", "MARTIN<<EMMA<ROSE<<<<<<<<<<<<<")
    );

    private MrzTestFixtures() {
    }
}
