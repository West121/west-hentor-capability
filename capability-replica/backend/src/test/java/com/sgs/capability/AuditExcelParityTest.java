package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/audit-excel-parity-store.json")
@AutoConfigureMockMvc
class AuditExcelParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/audit-excel-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset audit Excel parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void auditLogExportKeepsOriginalFileHeadersAndDateFormat() throws Exception {
        JsonNode file = postAbp("/api/services/app/AuditLog/GetAuditLogsToExcel", Map.of()).path("result");

        assertThat(file.path("fileName").asText()).isEqualTo("AuditLogs.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(file)))) {
            Sheet sheet = workbook.getSheet("AuditLogs");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);

            assertThat(text(header.getCell(0))).isEqualTo("Time");
            assertThat(text(header.getCell(5))).isEqualTo("Duration");
            assertThat(text(header.getCell(9))).isEqualTo("Error state");

            assertThat(sheet.getRow(1)).isNull();
            Row firstLog = sheet.getRow(2);
            assertThat(text(firstLog.getCell(3))).isNotBlank();

            Cell timeCell = firstLog.getCell(0);
            assertThat(timeCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(timeCell)).isTrue();
            assertThat(timeCell.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm:ss");
        }
    }

    @Test
    void entityChangeExportKeepsOriginalFileHeadersAndDateFormat() throws Exception {
        JsonNode file = postAbp("/api/services/app/AuditLog/GetEntityChangesToExcel", Map.of()).path("result");

        assertThat(file.path("fileName").asText()).isEqualTo("DetailedLogs.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(file)))) {
            Sheet sheet = workbook.getSheet("DetailedLogs");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);

            assertThat(text(header.getCell(0))).isEqualTo("Action");
            assertThat(text(header.getCell(1))).isEqualTo("Object");
            assertThat(text(header.getCell(2))).isEqualTo("User name");
            assertThat(text(header.getCell(3))).isEqualTo("Time");

            assertThat(sheet.getRow(1)).isNull();
            Row firstChange = sheet.getRow(2);
            assertThat(text(firstChange.getCell(0))).isNotBlank();

            Cell timeCell = firstChange.getCell(3);
            assertThat(timeCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(timeCell)).isTrue();
            assertThat(timeCell.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm:ss");
        }
    }

    private byte[] download(JsonNode file) throws Exception {
        return mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.path("fileToken").asText())
                        .param("fileName", file.path("fileName").asText())
                        .param("fileType", file.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
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
        return authService.authenticate("admin", "123qwe")
                .orElseThrow()
                .token();
    }

    private String text(Cell cell) {
        return cell == null ? "" : cell.toString();
    }
}
