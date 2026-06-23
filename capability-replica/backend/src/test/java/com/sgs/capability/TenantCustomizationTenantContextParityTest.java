package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/tenant-customization-tenant-context-store.json")
@AutoConfigureMockMvc
class TenantCustomizationTenantContextParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/tenant-customization-tenant-context-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset tenant customization tenant-context test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void tenantBrandingUploadReadAndClearUseTokenTenant() throws Exception {
        String tenantOneToken = adminToken();
        String tenantTwoToken = impersonatedTenantToken(2);
        byte[] tenantOneLogo = tinyPng();
        byte[] tenantTwoLogo = tinyGif();
        byte[] tenantOneCss = "body{color:#111827;}".getBytes(StandardCharsets.UTF_8);
        byte[] tenantTwoCss = "body{color:#16a34a;}".getBytes(StandardCharsets.UTF_8);

        JsonNode tenantOneUpload = uploadLogo("tenant-one.png", "image/png", tenantOneLogo, tenantOneToken);
        JsonNode tenantTwoUpload = uploadLogo("tenant-two.gif", "image/gif", tenantTwoLogo, tenantTwoToken);
        JsonNode tenantOneCssUpload = uploadCustomCss("tenant-one.css", tenantOneCss, tenantOneToken);
        JsonNode tenantTwoCssUpload = uploadCustomCss("tenant-two.css", tenantTwoCss, tenantTwoToken);

        assertThat(tenantOneUpload.path("tenantId").asInt()).isEqualTo(1);
        assertThat(tenantTwoUpload.path("tenantId").asInt()).isEqualTo(2);
        assertThat(tenantOneCssUpload.path("tenantId").asInt()).isEqualTo(1);
        assertThat(tenantTwoCssUpload.path("tenantId").asInt()).isEqualTo(2);
        assertThat(downloadLogo(tenantTwoToken)).isEqualTo(tenantTwoLogo);
        assertThat(downloadLogoByTenantId(1)).isEqualTo(tenantOneLogo);
        assertThat(downloadCustomCss(tenantTwoToken)).isEqualTo(tenantTwoCss);
        assertThat(downloadCustomCssByTenantId(1)).isEqualTo(tenantOneCss);

        clearLogo(tenantTwoToken);
        clearCustomCss(tenantTwoToken);

        assertLogoMissing(tenantTwoToken);
        assertCustomCssMissing(tenantTwoToken);
        assertThat(downloadLogoByTenantId(1)).isEqualTo(tenantOneLogo);
        assertThat(downloadCustomCssByTenantId(1)).isEqualTo(tenantOneCss);
    }

    @Test
    void tenantBrandingUploadsAcceptOriginalAnyMultipartFieldName() throws Exception {
        String token = adminToken();
        byte[] logo = tinyPng();
        byte[] css = "body{background:#f8fafc;}".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile logoFile = new MockMultipartFile("tenantLogoUpload", "logo-any-field.png", "image/png", logo);
        String logoBody = mockMvc.perform(multipart("/TenantCustomization/UploadLogo")
                        .file(logoFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode logoResponse = objectMapper.readTree(logoBody);
        assertThat(logoResponse.path("success").asBoolean()).isTrue();
        assertThat(logoResponse.path("result").path("tenantId").asInt()).isEqualTo(1);
        assertThat(downloadLogo(token)).isEqualTo(logo);

        MockMultipartFile cssFile = new MockMultipartFile("tenantCssUpload", "style-any-field.css", "text/css", css);
        String cssBody = mockMvc.perform(multipart("/TenantCustomization/UploadCustomCss")
                        .file(cssFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode cssResponse = objectMapper.readTree(cssBody);
        assertThat(cssResponse.path("success").asBoolean()).isTrue();
        assertThat(cssResponse.path("result").path("tenantId").asInt()).isEqualTo(1);
        assertThat(downloadCustomCss(token)).isEqualTo(css);
    }

    private JsonNode uploadLogo(String fileName, String fileType, byte[] content, String accessToken) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, fileType, content);
        String body = mockMvc.perform(multipart("/TenantCustomization/UploadLogo")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result");
    }

    private JsonNode uploadCustomCss(String fileName, byte[] content, String accessToken) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/css", content);
        String body = mockMvc.perform(multipart("/TenantCustomization/UploadCustomCss")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result");
    }

    private byte[] downloadLogo(String accessToken) throws Exception {
        return mockMvc.perform(get("/TenantCustomization/GetLogo")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private byte[] downloadCustomCss(String accessToken) throws Exception {
        return mockMvc.perform(get("/TenantCustomization/GetCustomCss")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private byte[] downloadCustomCssByTenantId(int tenantId) throws Exception {
        return mockMvc.perform(get("/TenantCustomization/GetCustomCss").param("tenantId", String.valueOf(tenantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private void clearLogo(String accessToken) throws Exception {
        postAbp("/api/services/app/TenantSettings/ClearLogo", accessToken);
    }

    private void clearCustomCss(String accessToken) throws Exception {
        postAbp("/api/services/app/TenantSettings/ClearCustomCss", accessToken);
    }

    private void assertLogoMissing(String accessToken) throws Exception {
        mockMvc.perform(get("/TenantCustomization/GetLogo")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private void assertCustomCssMissing(String accessToken) throws Exception {
        mockMvc.perform(get("/TenantCustomization/GetCustomCss")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private void postAbp(String url, String accessToken) throws Exception {
        String body = mockMvc.perform(post(url).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
    }

    private byte[] downloadLogoByTenantId(int tenantId) throws Exception {
        return mockMvc.perform(get("/TenantCustomization/GetLogo").param("tenantId", String.valueOf(tenantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private String impersonatedTenantToken(int tenantId) throws Exception {
        String token = authService.createImpersonationToken(1L, tenantId);
        String body = mockMvc.perform(get("/api/TokenAuth/ImpersonatedAuthenticate")
                        .param("impersonationToken", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("result").path("accessToken").asText();
    }

    private byte[] tinyPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        );
    }

    private byte[] tinyGif() {
        return Base64.getDecoder().decode("R0lGODlhAQABAIABAP8AAP///yH5BAEAAAEALAAAAAABAAEAAAICRAEAOw==");
    }
}
