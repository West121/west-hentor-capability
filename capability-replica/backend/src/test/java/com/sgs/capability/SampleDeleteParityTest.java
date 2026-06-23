package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.Sample;
import com.sgs.capability.model.SampleType;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/sample-delete-parity-store.json")
@AutoConfigureMockMvc
class SampleDeleteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/sample-delete-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset sample delete parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void deleteSampleTypeAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        SampleType existing = sampleType("TDD-DELETE-TYPE-" + System.nanoTime());

        JsonNode response = deleteAbp("/api/services/app/SampleType/DeleteSampleType", existing.id.toString());

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.sampleType(existing.id)).isEmpty();
    }

    @Test
    void deleteSampleAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        SampleType type = sampleType("TDD-DELETE-SAMPLE-TYPE-" + System.nanoTime());
        Sample existing = sample("TDD-DELETE-SAMPLE-" + System.nanoTime(), type);

        JsonNode response = deleteAbp("/api/services/app/Sample/DeleteSample", existing.id.toString());

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.sample(existing.id)).isEmpty();
    }

    private SampleType sampleType(String displayName) {
        SampleType type = new SampleType();
        type.displayName = displayName;
        type.orgId = 2L;
        return store.saveSampleType(type);
    }

    private Sample sample(String displayName, SampleType type) {
        Sample sample = new Sample();
        sample.displayName = displayName;
        sample.engName = displayName;
        sample.alias = displayName;
        sample.typeId = type.id;
        return store.saveSample(sample);
    }

    private JsonNode deleteAbp(String url, String id) throws Exception {
        String body = mockMvc.perform(delete(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("Id", id))
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
