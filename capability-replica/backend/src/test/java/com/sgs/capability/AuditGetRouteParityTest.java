package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/audit-get-route-parity-store.json")
@AutoConfigureMockMvc
class AuditGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/audit-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset audit GET route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void auditLogReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        MockHttpServletRequestBuilder request = auditLogRequest("/api/services/app/AuditLog/GetAuditLogs")
                .param("UserName", "admin")
                .param("ServiceName", "AbilityAppService")
                .param("MethodName", "FindPageAblibities")
                .param("BrowserInfo", "Chrome")
                .param("HasException", "false")
                .param("MinExecutionDuration", "0")
                .param("MaxExecutionDuration", "5000")
                .param("Sorting", "executionTime DESC");

        JsonNode result = getAbp(request).path("result");

        assertThat(result.path("totalCount").asInt()).isGreaterThan(0);
        assertThat(result.path("items")).allSatisfy(item -> {
            assertThat(item.path("userName").asText()).containsIgnoringCase("admin");
            assertThat(item.path("serviceName").asText()).contains("AbilityAppService");
        });

        JsonNode file = getAbp(auditLogRequest("/api/services/app/AuditLog/GetAuditLogsToExcel")).path("result");
        assertThat(file.path("fileName").asText()).isEqualTo("AuditLogs.xlsx");
        assertThat(file.path("fileToken").asText()).isNotBlank();
    }

    @Test
    void auditLogExcelKeepsOriginalDateFormatWithoutExtraInteractiveHeader() throws Exception {
        JsonNode file = getAbp(auditLogRequest("/api/services/app/AuditLog/GetAuditLogsToExcel")).path("result");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(file)))) {
            Sheet sheet = workbook.getSheet("AuditLogs");
            assertThat(sheet).isNotNull();

            PaneInformation pane = sheet.getPaneInformation();
            assertThat(pane == null || !pane.isFreezePane()).isTrue();

            assertThat(((XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter()).isFalse();

            assertThat(sheet.getRow(1)).isNull();
            assertThat(sheet.getColumnWidth(4)).isEqualTo(sheet.getDefaultColumnWidth() * 256);
            assertThat(sheet.getColumnWidth(9)).isEqualTo(sheet.getDefaultColumnWidth() * 256);

            Cell timeCell = sheet.getRow(2).getCell(0);
            assertThat(timeCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(timeCell)).isTrue();
            assertThat(timeCell.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm:ss");
        }
    }

    @Test
    void entityChangeReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        JsonNode changes = getAbp(entityChangeRequest("/api/services/app/AuditLog/GetEntityChanges")
                .param("Sorting", "changeTime DESC")).path("result");
        assertThat(changes.path("totalCount").asInt()).isGreaterThan(0);

        JsonNode first = changes.path("items").get(0);
        String entityTypeFullName = first.path("entityTypeFullName").asText();
        String entityId = first.path("entityId").asText();
        long entityChangeId = first.path("id").asLong();

        JsonNode typeChanges = getAbp(get("/api/services/app/AuditLog/GetEntityTypeChanges")
                .param("EntityTypeFullName", entityTypeFullName)
                .param("EntityId", entityId)
                .param("Sorting", "changeTime DESC")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result");
        assertThat(typeChanges.path("items")).anySatisfy(item -> assertThat(item.path("id").asLong()).isEqualTo(entityChangeId));

        JsonNode file = getAbp(entityChangeRequest("/api/services/app/AuditLog/GetEntityChangesToExcel")).path("result");
        assertThat(file.path("fileName").asText()).isEqualTo("DetailedLogs.xlsx");
        assertThat(file.path("fileToken").asText()).isNotBlank();

        JsonNode properties = getAbp(get("/api/services/app/AuditLog/GetEntityPropertyChanges")
                .param("entityChangeId", String.valueOf(entityChangeId))).path("result");
        assertThat(properties.isArray()).isTrue();
    }

    @Test
    void auditPagingInputsRejectOriginalRangeViolations() throws Exception {
        assertValidationFailure(raw(get("/api/services/app/AuditLog/GetAuditLogs")
                .param("SkipCount", "0")
                .param("MaxResultCount", "0")));
        assertValidationFailure(raw(postJson("/api/services/app/AuditLog/GetAuditLogs", Map.of(
                "skipCount", 0,
                "maxResultCount", 1001
        ))));
        assertValidationFailure(raw(get("/api/services/app/AuditLog/GetAuditLogsToExcel")
                .param("SkipCount", "-1")
                .param("MaxResultCount", "10")));

        assertValidationFailure(raw(get("/api/services/app/AuditLog/GetEntityChanges")
                .param("SkipCount", "0")
                .param("MaxResultCount", "0")));
        assertValidationFailure(raw(postJson("/api/services/app/AuditLog/GetEntityChanges", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        ))));
        assertValidationFailure(raw(get("/api/services/app/AuditLog/GetEntityChangesToExcel")
                .param("SkipCount", "0")
                .param("MaxResultCount", "1001")));

        assertValidationFailure(raw(get("/api/services/app/AuditLog/GetEntityTypeChanges")
                .param("SkipCount", "0")
                .param("MaxResultCount", "0")));
        assertValidationFailure(raw(postJson("/api/services/app/AuditLog/GetEntityTypeChanges", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        ))));
    }

    private MockHttpServletRequestBuilder auditLogRequest(String url) {
        return get(url)
                .param("StartDate", "2020-01-01T00:00:00.000Z")
                .param("EndDate", "2030-12-31T23:59:59.000Z")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0");
    }

    private MockHttpServletRequestBuilder entityChangeRequest(String url) {
        return get(url)
                .param("StartDate", "2020-01-01T00:00:00.000Z")
                .param("EndDate", "2030-12-31T23:59:59.000Z")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0");
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

    private MockHttpServletRequestBuilder postJson(String url, Object payload) throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload));
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private JsonNode raw(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private byte[] download(JsonNode file) throws Exception {
        return mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.path("fileToken").asText())
                        .param("fileName", file.path("fileName").asText())
                        .param("fileType", file.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
