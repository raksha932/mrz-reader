package com.certipass.mrtd.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Certipass MRZ Reader API",
                version = "1.0.0",
                description = "HTTP API for extracting, parsing, and validating ICAO MRZ data from travel document images.",
                contact = @Contact(name = "Certipass Engineering"),
                license = @License(name = "Proprietary")),
        servers = @Server(url = "/", description = "Current deployment"))
@SecurityScheme(
        name = OpenApiConfig.API_KEY_SECURITY_SCHEME,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "API key issued by Certipass. Send it in the X-API-Key request header.")
public class OpenApiConfig {
    public static final String API_KEY_SECURITY_SCHEME = "ApiKeyAuth";
}
