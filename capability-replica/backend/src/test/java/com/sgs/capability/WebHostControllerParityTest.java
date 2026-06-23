package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.TempFileService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/web-host-controller-parity-store.json")
@AutoConfigureMockMvc
class WebHostControllerParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/web-host-controller-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset web host controller parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TempFileService tempFiles;

    @Test
    void antiForgeryRouteIssuesXsrfCookieAndHeader() throws Exception {
        mockMvc.perform(get("/AntiForgery/GetToken"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(header().exists("X-XSRF-TOKEN"));
    }

    @Test
    void homeAndUiRoutesKeepOriginalMvcRedirectsForTheReactShell() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/Ui"));

        mockMvc.perform(get("/Ui"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/Ui/Logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void uiLoginPostKeepsOriginalMvcFormRedirectContract() throws Exception {
        mockMvc.perform(post("/Ui/Login")
                        .param("userNameOrEmailAddress", "admin")
                        .param("password", "123qwe")
                        .param("rememberMe", "true")
                        .param("returnUrl", "/dashboard/v1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/v1"));
    }

    @Test
    void uiLoginGetKeepsReturnUrlForReactLoginPage() throws Exception {
        mockMvc.perform(get("/Ui/Login")
                        .param("returnUrl", "/sys/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?returnUrl=/sys/users"));
    }

    @Test
    void errorControllerRoutesReachCopiedExceptionPages() throws Exception {
        mockMvc.perform(get("/Error").param("statusCode", "404"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exception/404"));

        mockMvc.perform(get("/Error/E403"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exception/403"));

        mockMvc.perform(get("/Error").param("statusCode", "500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exception/500"));
    }

    @Test
    void consentGetBuildsOriginalIdentityServerConsentViewModel() throws Exception {
        JsonNode model = json(mockMvc.perform(get("/Consent")
                        .param("returnUrl", consentReturnUrl()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(model.path("returnUrl").asText()).isEqualTo(consentReturnUrl());
        assertThat(model.path("rememberConsent").asBoolean()).isTrue();
        assertThat(model.path("clientName").asText()).isEqualTo("capability-react");
        assertThat(model.path("allowRememberConsent").asBoolean()).isTrue();

        JsonNode openId = model.path("identityScopes").get(0);
        assertThat(openId.path("name").asText()).isEqualTo("openid");
        assertThat(openId.path("required").asBoolean()).isTrue();
        assertThat(openId.path("checked").asBoolean()).isTrue();

        JsonNode offlineAccess = model.path("resourceScopes").get(1);
        assertThat(offlineAccess.path("name").asText()).isEqualTo("offline_access");
        assertThat(offlineAccess.path("displayName").asText()).isEqualTo("Offline Access");
        assertThat(offlineAccess.path("description").asText())
                .isEqualTo("Access to your applications and resources, even when you are offline");
        assertThat(offlineAccess.path("emphasize").asBoolean()).isTrue();
        assertThat(offlineAccess.path("checked").asBoolean()).isTrue();
    }

    @Test
    void consentPostRedirectsToReturnUrlAfterAcceptedScopesLikeOriginalController() throws Exception {
        mockMvc.perform(post("/Consent/Index")
                        .param("Button", "yes")
                        .param("ScopesConsented", "openid", "default-api", "offline_access")
                        .param("RememberConsent", "true")
                        .param("ReturnUrl", consentReturnUrl()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", consentReturnUrl()));
    }

    @Test
    void consentPostReturnsOriginalValidationErrorWhenAcceptedWithoutScopes() throws Exception {
        JsonNode response = json(mockMvc.perform(post("/Consent/Index")
                        .param("Button", "yes")
                        .param("RememberConsent", "false")
                        .param("ReturnUrl", consentReturnUrl()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(response.path("validationError").asText()).isEqualTo("You must pick at least one permission");
        assertThat(response.path("showView").asBoolean()).isTrue();
        assertThat(response.path("hasValidationError").asBoolean()).isTrue();
        assertThat(response.path("viewModel").path("rememberConsent").asBoolean()).isFalse();
        assertThat(response.path("viewModel").path("resourceScopes").get(0).path("checked").asBoolean()).isFalse();
    }

    @Test
    void profilePictureUploadReturnsOriginalTempFileShape() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "avatar.png", "image/png", tinyPng());

        JsonNode response = json(mockMvc.perform(multipart("/Profile/UploadProfilePicture")
                        .file(image)
                        .param("fileToken", "profile-picture-token")
                        .param("fileName", "avatar.png")
                        .param("fileType", "image/png")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(response.path("success").asBoolean()).isTrue();
        JsonNode result = response.path("result");
        assertThat(result.path("fileToken").asText()).isEqualTo("profile-picture-token");
        assertThat(result.path("fileName").asText()).isEqualTo("avatar.png");
        assertThat(result.path("fileType").asText()).isEqualTo("image/png");
        assertThat(result.path("width").asInt()).isEqualTo(1);
        assertThat(result.path("height").asInt()).isEqualTo(1);
    }

    @Test
    void profilePictureUploadAcceptsOriginalAnyMultipartFieldName() throws Exception {
        MockMultipartFile image = new MockMultipartFile("avatarUpload", "avatar-any-field.png", "image/png", tinyPng());

        JsonNode response = json(mockMvc.perform(multipart("/Profile/UploadProfilePicture")
                        .file(image)
                        .param("fileToken", "profile-picture-any-field-token")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("fileToken").asText()).isEqualTo("profile-picture-any-field-token");
        assertThat(response.path("result").path("fileName").asText()).isEqualTo("avatar-any-field.png");
        assertThat(response.path("result").path("width").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("height").asInt()).isEqualTo(1);
    }

    @Test
    void defaultProfilePictureRouteReturnsOriginalAnonymousPng() throws Exception {
        byte[] body = mockMvc.perform(get("/Profile/GetDefaultProfilePicture"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(body).startsWith(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    @Test
    void chatUploadCanBeDownloadedThroughOriginalChatAndFileRoutes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "chat file".getBytes(StandardCharsets.UTF_8));

        JsonNode response = json(mockMvc.perform(multipart("/Chat/UploadFile")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(response.path("success").asBoolean()).isTrue();
        String id = response.path("result").path("id").asText();
        assertThat(response.path("result").path("name").asText()).isEqualTo("note.txt");
        assertThat(response.path("result").path("contentType").asText()).isEqualTo("text/plain");

        mockMvc.perform(get("/Chat/GetUploadedObject")
                        .param("fileId", id)
                        .param("fileName", "note.txt")
                        .param("contentType", "text/plain"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"note.txt\""))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("chat file"));

        mockMvc.perform(get("/File/DownloadBinaryFile")
                        .param("id", id)
                        .param("fileName", "note.txt")
                        .param("contentType", "text/plain")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("chat file"));
    }

    @Test
    void chatUploadAcceptsOriginalAnyMultipartFieldName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("chatAttachment", "chat-any-field.txt",
                "text/plain", "chat any field".getBytes(StandardCharsets.UTF_8));

        JsonNode response = json(mockMvc.perform(multipart("/Chat/UploadFile")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(response.path("success").asBoolean()).isTrue();
        String id = response.path("result").path("id").asText();
        assertThat(response.path("result").path("name").asText()).isEqualTo("chat-any-field.txt");

        mockMvc.perform(get("/File/DownloadBinaryFile")
                        .param("id", id)
                        .param("fileName", "chat-any-field.txt")
                        .param("contentType", "text/plain")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("chat any field"));
    }

    @Test
    void fileDownloadsReturnOriginalNotFoundStatusForMissingObjects() throws Exception {
        mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", "missing-token")
                        .param("fileName", "missing.xlsx")
                        .param("fileType", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/File/DownloadBinaryFile")
                        .param("id", "00000000-0000-0000-0000-000000000001")
                        .param("fileName", "missing.zip")
                        .param("contentType", "application/zip")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void tempFileDownloadKeepsOriginalFileDtoRequiredFieldsAndRequestFileName() throws Exception {
        FileDto file = tempFiles.put("stored-name.txt", "text/plain", "temp body".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.fileToken)
                        .param("fileType", "text/plain")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.fileToken)
                        .param("fileName", "request-name.txt")
                        .param("fileType", "text/plain")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .contains("request-name.txt")
                        .doesNotContain("stored-name.txt"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("temp body"));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe")
                .orElseThrow()
                .token();
    }

    private String consentReturnUrl() {
        return "/connect/authorize/callback?client_id=capability-react&scope=openid%20default-api%20offline_access";
    }

    private byte[] tinyPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        );
    }
}
