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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ueditor-image-upload-parity-store.json")
@AutoConfigureMockMvc
class UeditorImageUploadParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ueditor-image-upload-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ueditor image upload parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void uploadImageKeepsUeditorResponseShapeAndInlineReadUrl() throws Exception {
        MockMultipartFile image = new MockMultipartFile("upfile", "ability.png", "image/png", tinyPng());

        String body = mockMvc.perform(multipart("/UEditor/UploadImage")
                        .file(image)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("state").asText()).isEqualTo("SUCCESS");
        assertThat(response.path("url").asText()).startsWith("/UEditor/GetImage?");
        assertThat(response.path("title").asText()).isEqualTo("ability.png");
        assertThat(response.path("original").asText()).isEqualTo("ability.png");
        assertThat(response.path("type").asText()).isEqualTo(".png");
        assertThat(response.path("size").asLong()).isEqualTo(tinyPng().length);

        mockMvc.perform(get(response.path("url").asText()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"ability.png\""))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(tinyPng()));
    }

    @Test
    void uploadImageRejectsNonImagesWithUeditorState() throws Exception {
        MockMultipartFile text = new MockMultipartFile("upfile", "note.txt", "text/plain", "not an image".getBytes());

        String body = mockMvc.perform(multipart("/UEditor/UploadImage")
                        .file(text)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("state").asText()).isEqualTo("IncorrectImageFormat");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe")
                .orElseThrow()
                .token();
    }

    private byte[] tinyPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        );
    }
}
