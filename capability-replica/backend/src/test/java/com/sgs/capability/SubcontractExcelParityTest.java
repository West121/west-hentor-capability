package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/subcontract-excel-parity-store.json")
@AutoConfigureMockMvc
class SubcontractExcelParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/subcontract-excel-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset subcontract Excel parity test store", ex);
        }
    }

    private static final List<String> SUBCONTRACT_HEADERS = List.of(
            "实验室名称",
            "联系方式",
            "检测/校准项目或类别",
            "CMA/CNAS No(截止日期)",
            "选定依据",
            "评估人",
            "评估结果"
    );

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void subcontractTemplateUsesOriginalTwoRowHeaderForNpoiUploadReader() throws Exception {
        JsonNode file = postAbp("/api/services/app/SubcontractAbility/GetTemplateExcel", Map.of()).path("result");

        assertThat(file.path("fileName").asText()).isEqualTo("分包能力模板.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(file)))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(workbook.getSheetName(0)).isEqualTo("分包能力");
            assertThat(text(sheet.getRow(0))).isEqualTo("分包能力");
            assertThat(hasMergedRegion(sheet, 0, 0, 0, SUBCONTRACT_HEADERS.size() - 1)).isTrue();
            Row header = sheet.getRow(1);
            for (int index = 0; index < SUBCONTRACT_HEADERS.size(); index++) {
                assertThat(text(header, index)).isEqualTo(SUBCONTRACT_HEADERS.get(index));
            }
            assertThat(text(sheet.getRow(2), 0)).isNotEqualTo("实验室名称");
        }
    }

    @Test
    void subcontractUploadUsesOriginalFixedSecondRowHeaderAndThirdRowDataStart() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "subcontract-single-row-header.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                singleRowHeaderSubcontractWorkbook());

        String body = mockMvc.perform(multipart("/AbilityTable/UploadSubcontractAbility")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode upload = objectMapper.readTree(body);
        assertThat(upload.path("totalCount").asInt()).isZero();
        assertThat(upload.path("items").size()).isZero();
    }

    @Test
    void subcontractUploadReadsFormulaDisplayValuesLikeOriginalNpoiToolkit() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "subcontract-formula.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                formulaSubcontractWorkbook());

        String body = mockMvc.perform(multipart("/AbilityTable/UploadSubcontractAbility")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode first = objectMapper.readTree(body).path("items").get(0);
        assertThat(first.path("labName").asText()).isEqualTo("TDD Formula Lab");
        assertThat(first.path("testCategory").asText()).isEqualTo("Formula Category");
        assertThat(first.path("exception").asText()).isBlank();
    }

    @Test
    void saveSubcontractExcelRejectsOriginalFileDtoRequiredFieldViolations() throws Exception {
        assertValidationFailure(postAbpRaw("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "dataList", List.of(),
                "onlySaveNew", false,
                "file", Map.of(
                        "fileType", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "fileToken", "token-without-name"
                )
        )));

        assertValidationFailure(postAbpRaw("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "dataList", List.of(),
                "onlySaveNew", false,
                "file", Map.of(
                        "fileName", "subcontract.xlsx",
                        "fileType", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
        )));

        JsonNode accepted = postAbp("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "dataList", List.of(Map.of(
                        "labName", "TDD FileType Optional Lab",
                        "testCategory", "TDD FileType Optional Category"
                )),
                "onlySaveNew", false,
                "file", Map.of(
                        "fileName", "subcontract.xlsx",
                        "fileToken", "unused-token"
                )
        ));
        assertThat(accepted.path("result").isNull()).isTrue();
    }

    @Test
    void saveSubcontractExcelReplacesExistingRowsByLabNameAndIgnoresOnlySaveNew() throws Exception {
        String marker = "TDD-SUB-REPLACE-" + System.nanoTime();
        String labName = marker + "-lab";
        postAbp("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "dataList", List.of(Map.of(
                        "labName", labName,
                        "testCategory", "old-category",
                        "gist", "old-gist"
                )),
                "onlySaveNew", false
        ));

        postAbp("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "dataList", List.of(Map.of(
                        "labName", labName,
                        "testCategory", "new-category",
                        "gist", "new-gist"
                )),
                "onlySaveNew", true
        ));

        JsonNode items = postAbp("/api/services/app/SubcontractAbility/FindList", Map.of(
                "filter", marker,
                "skipCount", 0,
                "maxResultCount", 10
        )).path("result").path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("labName").asText()).isEqualTo(labName);
        assertThat(items.get(0).path("testCategory").asText()).isEqualTo("new-category");
        assertThat(items.get(0).path("gist").asText()).isEqualTo("new-gist");
    }

    @Test
    void findListRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(postAbpRaw("/api/services/app/SubcontractAbility/FindList", Map.of(
                "skipCount", 0,
                "maxResultCount", 0
        )));
        assertValidationFailure(postAbpRaw("/api/services/app/SubcontractAbility/FindList", Map.of(
                "skipCount", 0,
                "maxResultCount", 1001
        )));
        assertValidationFailure(postAbpRaw("/api/services/app/SubcontractAbility/FindList", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        )));
    }

    @Test
    void findListDefaultsBlankSortingToOriginalId() throws Exception {
        String marker = "TDD-SUB-SORT-" + System.nanoTime();
        saveSubcontractRow("00000000-0000-0000-0000-000000000010", marker, "B");
        saveSubcontractRow("00000000-0000-0000-0000-000000000020", marker, "A");

        JsonNode items = postAbp("/api/services/app/SubcontractAbility/FindList", Map.of(
                "filter", marker,
                "skipCount", 0,
                "maxResultCount", 1
        )).path("result").path("items");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("labName").asText()).isEqualTo(marker + "-B");
    }

    @Test
    void findListHonorsExplicitOriginalSorting() throws Exception {
        String marker = "TDD-SUB-EXPLICIT-" + System.nanoTime();
        saveSubcontractRow("00000000-0000-0000-0000-000000000030", marker, "A");
        saveSubcontractRow("00000000-0000-0000-0000-000000000040", marker, "B");

        JsonNode items = postAbp("/api/services/app/SubcontractAbility/FindList", Map.of(
                "filter", marker,
                "sorting", "labName DESC",
                "skipCount", 0,
                "maxResultCount", 1
        )).path("result").path("items");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("labName").asText()).isEqualTo(marker + "-B");
    }

    @Test
    void findListOnlyFiltersOriginalFourSearchFields() throws Exception {
        String marker = "TDD-SUB-FILTER-" + System.nanoTime();
        postAbp("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "dataList", List.of(Map.of(
                        "labName", "Lab without marker",
                        "contactDetails", "400-000-0000",
                        "testCategory", "Category without marker",
                        "cmaOrCnas", "CNAS L0000/2027-12-31",
                        "gist", marker,
                        "appraiser", marker,
                        "evaluationResult", marker
                ))
        ));

        JsonNode items = postAbp("/api/services/app/SubcontractAbility/FindList", Map.of(
                "filter", marker,
                "skipCount", 0,
                "maxResultCount", 10
        )).path("result").path("items");

        assertThat(items).isEmpty();
    }

    private void saveSubcontractRow(String id, String marker, String suffix) throws Exception {
        postAbp("/api/services/app/SubcontractAbility/SaveExcelData", Map.of(
                "onlySaveNew", false,
                "dataList", List.of(Map.of(
                        "id", id,
                        "labName", marker + "-" + suffix,
                        "contactDetails", "400-000-0000",
                        "testCategory", marker + "-category-" + suffix,
                        "cmaOrCnas", "CNAS L0000/2027-12-31",
                        "gist", "年度评价",
                        "appraiser", "Admin",
                        "evaluationResult", "合格"
                ))
        ));
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

    private JsonNode postAbpRaw(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
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

    private String text(Row row) {
        return text(row, 0);
    }

    private String text(Row row, int column) {
        if (row == null || row.getCell(column) == null) {
            return "";
        }
        return row.getCell(column).toString();
    }

    private boolean hasMergedRegion(Sheet sheet, int firstRow, int lastRow, int firstColumn, int lastColumn) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getFirstRow() == firstRow
                    && region.getLastRow() == lastRow
                    && region.getFirstColumn() == firstColumn
                    && region.getLastColumn() == lastColumn) {
                return true;
            }
        }
        return false;
    }

    private byte[] singleRowHeaderSubcontractWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("分包能力");
            Row header = sheet.createRow(0);
            for (int index = 0; index < SUBCONTRACT_HEADERS.size(); index++) {
                header.createCell(index).setCellValue(SUBCONTRACT_HEADERS.get(index));
            }
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("TDD 单行表头实验室");
            data.createCell(1).setCellValue("400-100-1000");
            data.createCell(2).setCellValue("TDD 单行表头项目");
            data.createCell(3).setCellValue("CNAS-ROW1");
            data.createCell(4).setCellValue("ISO/IEC 17025");
            data.createCell(5).setCellValue("TDD");
            data.createCell(6).setCellValue("合格");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] formulaSubcontractWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("分包能力");
            sheet.createRow(0).createCell(0).setCellValue("分包能力");
            Row header = sheet.createRow(1);
            for (int index = 0; index < SUBCONTRACT_HEADERS.size(); index++) {
                header.createCell(index).setCellValue(SUBCONTRACT_HEADERS.get(index));
            }
            Row data = sheet.createRow(2);
            data.createCell(0).setCellFormula("\"TDD Formula\"&\" Lab\"");
            data.createCell(1).setCellValue("formula@example.local");
            data.createCell(2).setCellFormula("\"Formula\"&\" Category\"");
            data.createCell(3).setCellValue("CNAS-001");
            data.createCell(4).setCellValue("ISO");
            data.createCell(5).setCellValue("Admin");
            data.createCell(6).setCellValue("Pass");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
