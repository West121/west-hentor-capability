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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/demo-ui-components-query-route-parity-store.json")
@AutoConfigureMockMvc
class DemoUiComponentsQueryRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/demo-ui-components-query-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset Demo UI query route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void dateEchoRoutesAcceptOriginalPostQueryParameters() throws Exception {
        JsonNode date = getAbp(post("/api/services/app/DemoUiComponents/SendAndGetDate")
                .param("date", "2099-01-02T03:04:05.000Z")).path("result");
        assertThat(date.path("dateString").asText()).isEqualTo("2099-01-02");

        JsonNode dateTime = getAbp(post("/api/services/app/DemoUiComponents/SendAndGetDateTime")
                .param("date", "2099-01-02T03:04:05.000Z")).path("result");
        assertThat(dateTime.path("dateString").asText()).isEqualTo("2099-01-02 03:04");

        JsonNode dateRange = getAbp(post("/api/services/app/DemoUiComponents/SendAndGetDateRange")
                .param("startDate", "2099-01-01T00:00:00.000Z")
                .param("endDate", "2099-01-03T00:00:00.000Z")).path("result");
        assertThat(dateRange.path("dateString").asText()).isEqualTo("2099-01-01 - 2099-01-03");
    }

    @Test
    void countryLookupAcceptsOriginalGetSearchTermParameter() throws Exception {
        JsonNode countries = getAbp(get("/api/services/app/DemoUiComponents/GetCountries")
                .param("searchTerm", "United")).path("result");

        assertThat(countries).hasSize(1);
        assertThat(countries.get(0).path("name").asText()).isEqualTo("United States of America");
    }

    @Test
    void valueEchoRouteAcceptsOriginalPostQueryInputParameter() throws Exception {
        JsonNode value = getAbp(post("/api/services/app/DemoUiComponents/SendAndGetValue")
                .param("input", "copied value")).path("result");

        assertThat(value.path("output").asText()).isEqualTo("copied value");
    }

    @Test
    void uploadFilesReturnsOriginalBinaryObjectIds() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain",
                "demo upload".getBytes(StandardCharsets.UTF_8));

        JsonNode result = getAbp(multipart("/DemoUiComponents/UploadFiles").file(file)).path("result");
        assertThat(result).hasSize(1);
        String id = result.get(0).path("id").asText();
        assertThat(id).matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(result.get(0).path("fileName").asText()).isEqualTo("demo.txt");

        mockMvc.perform(get("/File/DownloadBinaryFile")
                        .param("id", id)
                        .param("contentType", "text/plain")
                        .param("fileName", "demo.txt")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(response -> assertThat(response.getResponse().getContentAsString()).isEqualTo("demo upload"));
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
