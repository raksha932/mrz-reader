package com.certipass.mrtd.api;

import com.certipass.mrtd.api.auth.ApiKeyProperties;
import com.certipass.mrtd.api.config.OcrSidecarProperties;
import com.certipass.mrtd.api.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        OcrSidecarProperties.class,
        ApiKeyProperties.class,
        RateLimitProperties.class
})
public class MrtdApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MrtdApiApplication.class, args);
    }
}
