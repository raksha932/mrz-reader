package com.certipass.mrtd.api.openapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiWithApiKeySecurityAndMrzReadContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title", is("Certipass MRZ Reader API")))
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("ApiKeyAuth")))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.type", is("apiKey")))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.in", is("header")))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.name", is("X-API-Key")))
                .andExpect(jsonPath("$.paths['/api/v1/mrz/read'].post.security[0]", hasKey("ApiKeyAuth")))
                .andExpect(jsonPath("$.paths['/api/v1/mrz/read'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mrz/read'].post.responses['200'].content['application/json'].schema['$ref']",
                        containsString("MrzReadResponse")))
                .andExpect(jsonPath("$.paths['/api/v1/mrz/read'].post.responses['401'].content['application/json'].schema['$ref']",
                        containsString("ApiErrorResponse")))
                .andExpect(jsonPath("$.components.schemas.MrzReadResponse.properties.metadata['$ref']",
                        containsString("ResultMetadata")))
                .andExpect(jsonPath("$.components.schemas.RawMrz.properties.ocr_lines").exists())
                .andExpect(jsonPath("$.components.schemas.RawMrz.properties.corrected").exists())
                .andExpect(jsonPath("$.components.schemas.ResultMetadata.properties.request_id").exists());
    }
}
