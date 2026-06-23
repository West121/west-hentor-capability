package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/language-get-route-parity-store.json")
@AutoConfigureMockMvc
class LanguageGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/language-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset language GET route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void languageTextsRouteAcceptsOriginalGetQueryParameters() throws Exception {
        // Mirrors the generated Angular proxy order and PascalCase query names.
        JsonNode firstPage = getAbp(languageTextRequest()
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")
                .param("Sorting", "key ASC")).path("result");

        assertThat(firstPage.path("totalCount").asInt()).isGreaterThan(1);
        assertThat(firstPage.path("items")).hasSize(1);
        String firstKey = firstPage.path("items").get(0).path("key").asText();
        assertThat(firstPage.path("items").get(0).path("languageName").asText()).isEqualTo("zh-Hans");

        JsonNode secondPage = getAbp(languageTextRequest()
                .param("MaxResultCount", "1")
                .param("SkipCount", "1")
                .param("Sorting", "key ASC")).path("result");
        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.path("items").get(0).path("key").asText()).isNotEqualTo(firstKey);

        JsonNode filtered = getAbp(languageTextRequest()
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")
                .param("FilterText", "Login")
                .param("TargetValueFilter", "ALL")).path("result").path("items");
        assertThat(filtered).anySatisfy(item -> assertThat(item.path("key").asText()).isEqualTo("Login"));
    }

    private MockHttpServletRequestBuilder languageTextRequest() {
        return get("/api/services/app/Language/GetLanguageTexts")
                .param("SourceName", "CapabilityTable")
                .param("BaseLanguageName", "en")
                .param("TargetLanguageName", "zh-Hans");
    }

    private JsonNode getAbp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
