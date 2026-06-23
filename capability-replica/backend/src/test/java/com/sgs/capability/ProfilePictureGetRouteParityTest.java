package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/profile-picture-get-route-parity-store.json")
@AutoConfigureMockMvc
class ProfilePictureGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/profile-picture-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset profile picture GET route parity test store", ex);
        }
    }

    private static final String PICTURE = "data:image/png;base64,cHJvZmlsZS1waWN0dXJl";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void profilePictureReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        postAbp("/api/services/app/Profile/UpdateProfilePicture", Map.of("fileToken", PICTURE));
        String profilePictureId = store.user(1L).orElseThrow().profilePictureId.toString();

        JsonNode friendPicture = getAbp(get("/api/services/app/Profile/GetFriendProfilePictureById")
                .param("ProfilePictureId", profilePictureId)
                .param("UserId", "1")
                .param("TenantId", "0")).path("result");
        assertThat(friendPicture.path("profilePicture").asText()).isEqualTo(PICTURE);

        JsonNode pictureById = getAbp(get("/api/services/app/Profile/GetProfilePictureById")
                .param("profilePictureId", profilePictureId)).path("result");
        assertThat(pictureById.path("profilePicture").asText()).isEqualTo(PICTURE);
    }

    @Test
    void profilePictureByIdAllowsAnonymousLikeOriginalService() throws Exception {
        postAbp("/api/services/app/Profile/UpdateProfilePicture", Map.of("fileToken", PICTURE));
        String profilePictureId = store.user(1L).orElseThrow().profilePictureId.toString();

        String body = mockMvc.perform(get("/api/services/app/Profile/GetProfilePictureById")
                        .param("profilePictureId", profilePictureId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("profilePicture").asText()).isEqualTo(PICTURE);
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
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
