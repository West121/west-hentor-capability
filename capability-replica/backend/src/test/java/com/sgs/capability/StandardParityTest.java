package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.model.Ability;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.ExcelTransferService;
import com.sgs.capability.service.TempFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "replica.store.path=target/test-data/standard-parity-store.json",
        "replica.history.path=target/test-data/standard-history"
})
@AutoConfigureMockMvc
class StandardParityTest {
    private static final Path HISTORY_PATH = Path.of("target/test-data/standard-history");

    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/standard-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset standard parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    CapabilityStore store;

    @Autowired
    TempFileService tempFiles;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clearHistoryDirectory() throws Exception {
        deleteRecursively(HISTORY_PATH);
    }

    @Test
    void uploadNewStandardKeepsOriginalVoidResponseWhileUpdatingStandardNumbers() throws Exception {
        Ability ability = ability("TDD-STANDARD-" + System.nanoTime(), "GB/T-OLD-VOID");
        ability = store.saveAbility(ability);

        JsonNode response = postAbp("/api/services/app/Standard/UploadNewStandard", Map.of(
                "items", List.of(Map.of(
                        "old", "GB/T-OLD-VOID",
                        "new", "GB/T-NEW-VOID"
                ))
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.getAbility(ability.id.toString())).hasValueSatisfying(saved ->
                assertThat(saved.standardNo).isEqualTo("GB/T-NEW-VOID"));
    }

    @Test
    void uploadNewStandardAcceptsOriginalNewFieldNameAndAllowsBlankNewStandardNumber() throws Exception {
        Ability ability = ability("TDD-STANDARD-BLANK-" + System.nanoTime(), "GB/T-OLD-BLANK");
        ability = store.saveAbility(ability);

        postAbp("/api/services/app/Standard/UploadNewStandard", Map.of(
                "items", List.of(Map.of(
                        "old", "GB/T-OLD-BLANK",
                        "new", ""
                ))
        ));

        assertThat(store.getAbility(ability.id.toString())).hasValueSatisfying(saved ->
                assertThat(saved.standardNo).isEmpty());
    }

    @Test
    void uploadNewStandardCopiesSourceFileToHistoryWithOriginalDollarSeparatedCurrentUserAndToFileTimeName() throws Exception {
        byte[] source = "standard-history-source".getBytes(StandardCharsets.UTF_8);
        FileDto uploaded = tempFiles.put("standard-update.xlsx", ExcelTransferService.EXCEL_TYPE, source);

        postAbp("/api/services/app/Standard/UploadNewStandard", Map.of(
                "file", uploaded,
                "items", List.of(Map.of(
                        "old", "GB/T-NOT-MATCHED",
                        "new", "GB/T-HISTORY"
                ))
        ));

        assertThat(Files.exists(HISTORY_PATH)).isTrue();
        try (Stream<Path> stream = Files.list(HISTORY_PATH)) {
            List<Path> files = stream.toList();
            assertThat(files).hasSize(1);
            Path historyFile = files.get(0);
            assertThat(historyFile.getFileName().toString())
                    .matches("admin\\$\\d{18}\\$standard-update\\.xlsx");
            assertThat(Files.readAllBytes(historyFile)).isEqualTo(source);
        }
    }

    private Ability ability(String marker, String standardNo) {
        return objectMapper.convertValue(Map.ofEntries(
                entry("orgId", 2),
                entry("typeName", "矿石"),
                entry("samplingName", marker),
                entry("testItem", marker),
                entry("methodName", "重量法"),
                entry("methodEngName", "Gravimetric method"),
                entry("standardNo", standardNo),
                entry("cycleWorkingDay", "5"),
                entry("massRequired", "100"),
                entry("sizeRequired", "0.074"),
                entry("detectionLimit", "0.01%"),
                entry("price", "100")
        ), Ability.class);
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path current : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
