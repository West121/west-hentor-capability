package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.apache.poi.ss.usermodel.Cell;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-excel-template-parity-store.json")
@AutoConfigureMockMvc
class AbilityExcelTemplateParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-excel-template-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ability Excel template parity test store", ex);
        }
    }

    private static final List<String> ORIGINAL_HEADERS = List.of(
            "样品名称",
            "测试项目",
            "价格/CNY",
            "方法中文描述",
            "标准编号",
            "检测周期/工作日",
            "备注",
            "所需样品量",
            "样品粒度要求",
            "方法英文描述",
            "适用范围",
            "实验室能力"
    );

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void abilityTemplateUsesOriginalTwoRowHeaderAndMergedLabSection() throws Exception {
        JsonNode file = postAbp("/api/services/app/Ability/GetTemplateExcel", Map.of()).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.path("fileToken").asText())
                        .param("fileName", file.path("fileName").asText())
                        .param("fileType", file.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int index = 0; index < ORIGINAL_HEADERS.size(); index++) {
                assertThat(text(sheet.getRow(0).getCell(index))).isEqualTo(ORIGINAL_HEADERS.get(index));
            }
            assertThat(text(sheet.getRow(1).getCell(0))).isEqualTo("Sample Name");
            assertThat(text(sheet.getRow(1).getCell(11))).isEqualTo("TJ");
            assertThat(text(sheet.getRow(1).getCell(12))).isEqualTo("SH");
            assertThat(hasMergedRegion(sheet, 0, 0, 11, 12)).isTrue();
        }
    }

    @Test
    void abilityTemplateUsesRequestedOrgPropertyList() throws Exception {
        postAbp("/api/services/app/AbilityProperty/SaveOrgSetting", Map.of(
                "orgId", 3,
                "propertyName", List.of("samplingName", "testItem", "standardNo"),
                "lab", List.of("TJ"),
                "isPublic", true,
                "description", "TDD scoped template"
        ));

        JsonNode file = postAbp("/api/services/app/Ability/GetTemplateExcel", Map.of("orgId", 3)).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.path("fileToken").asText())
                        .param("fileName", file.path("fileName").asText())
                        .param("fileType", file.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(file.path("fileName").asText()).matches("物理检测导入模板\\d{18}\\.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(text(sheet.getRow(0).getCell(0))).isEqualTo("样品名称");
            assertThat(text(sheet.getRow(0).getCell(1))).isEqualTo("测试项目");
            assertThat(text(sheet.getRow(0).getCell(2))).isEqualTo("标准编号");
            assertThat(text(sheet.getRow(0).getCell(3))).isEqualTo("实验室能力");
            assertThat(text(sheet.getRow(0).getCell(4))).isBlank();
            assertThat(text(sheet.getRow(1).getCell(3))).isEqualTo("TJ");
            assertThat(text(sheet.getRow(1).getCell(4))).isEqualTo("SH");
            assertThat(hasMergedRegion(sheet, 0, 0, 3, 4)).isTrue();
        }
    }

    @Test
    void abilityTemplateAcceptsOriginalPascalCaseOrgPropertyList() throws Exception {
        postAbp("/api/services/app/AbilityProperty/SaveOrgSetting", Map.of(
                "orgId", 3,
                "propertyName", List.of("SamplingName", "TestItem", "StandardNo"),
                "lab", List.of("TJ"),
                "isPublic", true,
                "description", "TDD PascalCase scoped template"
        ));

        JsonNode file = postAbp("/api/services/app/Ability/GetTemplateExcel", Map.of("orgId", 3)).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", file.path("fileToken").asText())
                        .param("fileName", file.path("fileName").asText())
                        .param("fileType", file.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(text(sheet.getRow(0).getCell(0))).isEqualTo("样品名称");
            assertThat(text(sheet.getRow(0).getCell(1))).isEqualTo("测试项目");
            assertThat(text(sheet.getRow(0).getCell(2))).isEqualTo("标准编号");
            assertThat(text(sheet.getRow(0).getCell(3))).isEqualTo("实验室能力");
            assertThat(text(sheet.getRow(1).getCell(0))).isEqualTo("Sample Name");
            assertThat(text(sheet.getRow(1).getCell(1))).isEqualTo("Testing Item");
            assertThat(text(sheet.getRow(1).getCell(2))).isEqualTo("Standard Number");
        }
    }

    @Test
    void originalTemplateUploadAcceptsAllLabValueAndSavesCnasCmaFlags() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ability-original-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", originalTemplateWorkbook());

        String uploadBody = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(file)
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode upload = objectMapper.readTree(uploadBody);
        JsonNode row = upload.path("abilityTableList").get(0);
        assertThat(row.path("exception").asText()).isBlank();
        assertThat(row.path("standardNo").asText()).isEqualTo("GB/T-ALL-001");
        assertThat(row.path("price").asText()).isEqualTo("120");
        assertThat(row.path("labData").path("TJ").asText()).isEqualTo("ALL");

        postAbp("/api/services/app/Ability/SaveExcelData", Map.of(
                "dataList", upload.path("abilityTableList"),
                "onlySaveNew", false
        ));

        JsonNode result = postAbp("/api/services/app/Ability/FindPageAblibities", Map.of(
                "filter", "TDD-ALL-LAB",
                "skipCount", 0,
                "maxResultCount", 10
        )).path("result");

        JsonNode lab = result.path("items").get(0).path("labAbilities").get(0);
        assertThat(lab.path("code").asText()).isEqualTo("TJ");
        assertThat(lab.path("isAbility").asBoolean()).isTrue();
        assertThat(lab.path("hasCnas").asBoolean()).isTrue();
        assertThat(lab.path("hasCma").asBoolean()).isTrue();
    }

    @Test
    void originalTemplateUploadReadsMergedRequiredCells() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ability-original-template-merged.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", mergedStandardTemplateWorkbook());

        String uploadBody = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(file)
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode rows = objectMapper.readTree(uploadBody).path("abilityTableList");
        assertThat(rows.size()).isEqualTo(2);
        JsonNode secondRow = rows.get(1);
        assertThat(secondRow.path("exception").asText()).isBlank();
        assertThat(secondRow.path("samplingName").asText()).isEqualTo("TDD-MERGED 第二样品");
        assertThat(secondRow.path("standardNo").asText()).isEqualTo("GB/T-MERGED-001");
        assertThat(secondRow.path("labData").path("TJ").asText()).isEqualTo("CNAS");
    }

    @Test
    void abilityExportUsesOriginalTwoRowHeaderAndCommaSeparatedLabFlagOrder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ability-export-lab-value.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                originalTemplateWorkbook("TDD-EXPORT-COMMA", "GB/T-EXPORT-COMMA-001", "ALL", "CMA"));
        String uploadBody = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(file)
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode upload = objectMapper.readTree(uploadBody);
        assertThat(upload.path("abilityTableList").get(0).path("exception").asText()).isBlank();

        postAbp("/api/services/app/Ability/SaveExcelData", Map.of(
                "dataList", upload.path("abilityTableList"),
                "onlySaveNew", false
        ));

        JsonNode exported = postAbp("/api/services/app/Ability/ExportData", Map.of(
                "orgId", 2,
                "filter", List.of(Map.of(
                        "field", "samplingName",
                        "value", "TDD-EXPORT-COMMA"
                ))
        )).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", exported.path("fileToken").asText())
                        .param("fileName", exported.path("fileName").asText())
                        .param("fileType", exported.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet.getRow(0).getCell(0))).isEqualTo("样品名称");
            assertThat(text(sheet.getRow(1).getCell(0))).isEqualTo("Sample Name");
            assertThat(text(sheet.getRow(0).getCell(1))).isEqualTo("测试项目");
            assertThat(text(sheet.getRow(1).getCell(1))).isEqualTo("Standard Number");
            assertThat(text(sheet.getRow(0).getCell(3))).isEqualTo("标准编号");
            assertThat(text(sheet.getRow(1).getCell(3))).isEqualTo("Standard Number");

            int labColumn = headerIndex(sheet.getRow(1), "TJ");
            int typeColumn = headerIndex(sheet.getRow(0), "类型");
            assertThat(text(sheet.getRow(0).getCell(labColumn))).isEqualTo("实验室能力");
            assertThat(text(sheet.getRow(1).getCell(labColumn + 1))).isEqualTo("SH");
            assertThat(text(sheet.getRow(0).getCell(labColumn + 1))).isBlank();
            assertThat(typeColumn).isGreaterThan(labColumn);

            String exportedValue = "";
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null && text(row.getCell(0)).equals("TDD-EXPORT-COMMA 样品")) {
                    exportedValue = text(row.getCell(labColumn));
                    assertThat(text(row.getCell(typeColumn))).isEqualTo("矿石");
                    break;
                }
            }
            assertThat(exportedValue).isEqualTo("CMA,CNAS");
        }
    }

    @Test
    void abilityExportUsesOriginalFilterRequestBody() throws Exception {
        saveOriginalTemplateAbility("TDD-EXPORT-FILTER-KEEP", "GB/T-EXPORT-FILTER-KEEP", "ALL");
        saveOriginalTemplateAbility("TDD-EXPORT-FILTER-SKIP", "GB/T-EXPORT-FILTER-SKIP", "ALL");

        JsonNode exported = postAbp("/api/services/app/Ability/ExportData", Map.of(
                "orgId", 2,
                "filter", List.of(Map.of(
                        "field", "samplingName",
                        "value", "TDD-EXPORT-FILTER-KEEP"
                ))
        )).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", exported.path("fileToken").asText())
                        .param("fileName", exported.path("fileName").asText())
                        .param("fileType", exported.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(containsSample(sheet, "TDD-EXPORT-FILTER-KEEP 样品")).isTrue();
            assertThat(containsSample(sheet, "TDD-EXPORT-FILTER-SKIP 样品")).isFalse();
        }
    }

    @Test
    void abilityExportUsesOriginalOrgScopedFileAndSheetNames() throws Exception {
        JsonNode exported = postAbp("/api/services/app/Ability/ExportData", Map.of("orgId", 2)).path("result");
        assertThat(exported.path("fileName").asText()).matches("化学检测能力表数据--\\d{18}\\.xlsx");

        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", exported.path("fileToken").asText())
                        .param("fileName", exported.path("fileName").asText())
                        .param("fileType", exported.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("化学检测能力表数据");
        }
    }

    @Test
    void abilityExportUsesOriginalBoldTwelvePointHeaderStyle() throws Exception {
        JsonNode exported = postAbp("/api/services/app/Ability/ExportData", Map.of("orgId", 2)).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", exported.path("fileToken").asText())
                        .param("fileName", exported.path("fileName").asText())
                        .param("fileType", exported.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertBoldTwelvePoint(workbook, sheet.getRow(0).getCell(0));
            assertBoldTwelvePoint(workbook, sheet.getRow(1).getCell(0));
        }
    }

    @Test
    void abilityExportKeepsOriginalDefaultColumnWidths() throws Exception {
        JsonNode exported = postAbp("/api/services/app/Ability/ExportData", Map.of("orgId", 2)).path("result");
        byte[] bytes = mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", exported.path("fileToken").asText())
                        .param("fileName", exported.path("fileName").asText())
                        .param("fileType", exported.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            int defaultWidth = sheet.getDefaultColumnWidth() * 256;

            for (int index = 0; index < 12; index++) {
                assertThat(sheet.getColumnWidth(index)).isEqualTo(defaultWidth);
            }
        }
    }

    @Test
    void abilityExportUsesChemAndNfSpecialRemarkColumns() throws Exception {
        long chemOrgId = createOrgUnit("CHEM");
        createAbility(chemOrgId, "CHEM", "TDD-EXPORT-CHEM", "CHEM提前备注");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exportedAbilityBytes(chemOrgId, "TDD-EXPORT-CHEM")))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row subHeader = sheet.getRow(1);
            Row data = dataRowBySample(sheet, "TDD-EXPORT-CHEM 样品");

            assertThat(text(header.getCell(2))).isEqualTo("价格/CNY");
            assertThat(text(header.getCell(3))).isEqualTo("备注");
            assertThat(text(subHeader.getCell(3))).isEqualTo("Remark");
            assertThat(text(header.getCell(4))).isEqualTo("标准编号");
            assertThat(text(data.getCell(3))).isEqualTo("CHEM提前备注");
            assertThat(text(data.getCell(4))).isEqualTo("GB/T-TDD-EXPORT-CHEM");
            assertThat(headerCount(header, "备注")).isEqualTo(1);
        }

        long nfOrgId = createOrgUnit("NF");
        createAbility(nfOrgId, "NF", "TDD-EXPORT-NF", "NF提前备注");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exportedAbilityBytes(nfOrgId, "TDD-EXPORT-NF")))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row subHeader = sheet.getRow(1);
            Row data = dataRowBySample(sheet, "TDD-EXPORT-NF 样品");

            assertThat(text(header.getCell(3))).isEqualTo("标准编号");
            assertThat(text(header.getCell(4))).isEqualTo("备注");
            assertThat(text(subHeader.getCell(4))).isEqualTo("Remark");
            assertThat(text(header.getCell(5))).isEqualTo("方法中文描述");
            assertThat(text(data.getCell(4))).isEqualTo("NF提前备注");
            assertThat(headerCount(header, "备注")).isEqualTo(1);
        }
    }

    @Test
    void abilityExportUsesLabGroupSpecialStandardColumns() throws Exception {
        long orgId = createOrgUnit("Lab Group");
        createAbility(orgId, "Lab Group", "TDD-EXPORT-LABGROUP", "Lab Group备注");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exportedAbilityBytes(orgId, "TDD-EXPORT-LABGROUP")))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row subHeader = sheet.getRow(1);
            Row data = dataRowBySample(sheet, "TDD-EXPORT-LABGROUP 样品");

            assertThat(text(header.getCell(3))).isEqualTo("标准编号");
            assertThat(text(header.getCell(4))).isEqualTo("方法中文描述");
            assertThat(text(header.getCell(5))).isEqualTo("标准编号SGS");
            assertThat(text(subHeader.getCell(5))).isEqualTo("SGS");
            assertThat(text(header.getCell(6))).isEqualTo("标准编号SOP");
            assertThat(text(subHeader.getCell(6))).isEqualTo("SOP");
            assertThat(text(header.getCell(7))).isEqualTo("标准编号OTHERS");
            assertThat(text(subHeader.getCell(7))).isEqualTo("OTHERS");
            assertThat(text(header.getCell(8))).isEqualTo("标准编号DZ");
            assertThat(text(subHeader.getCell(8))).isEqualTo("DZ");
            assertThat(text(header.getCell(9))).isEqualTo("方法英文描述");
            assertThat(text(data.getCell(5))).isEqualTo("Lab Group备注");
            assertThat(text(data.getCell(6))).isEqualTo("Lab Group备注");
            assertThat(text(data.getCell(7))).isEqualTo("Lab Group备注");
            assertThat(text(data.getCell(8))).isEqualTo("Lab Group备注");
            assertThat(headerCount(header, "备注")).isEqualTo(1);
        }
    }

    private void saveOriginalTemplateAbility(String marker, String standardNo, String labValue) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", marker + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                originalTemplateWorkbook(marker, standardNo, labValue));
        String uploadBody = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(file)
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode upload = objectMapper.readTree(uploadBody);
        assertThat(upload.path("abilityTableList").get(0).path("exception").asText()).isBlank();

        postAbp("/api/services/app/Ability/SaveExcelData", Map.of(
                "dataList", upload.path("abilityTableList"),
                "onlySaveNew", false
        ));
    }

    private long createOrgUnit(String displayName) throws Exception {
        return postAbp("/api/services/app/OrganizationUnit/CreateOrganizationUnit", Map.of(
                "parentId", 1,
                "displayName", displayName
        )).path("result").path("id").asLong();
    }

    private void createAbility(long orgId, String orgName, String marker, String remark) throws Exception {
        java.util.LinkedHashMap<String, Object> ability = new java.util.LinkedHashMap<>();
        ability.put("orgId", orgId);
        ability.put("orgName", orgName);
        ability.put("typeName", "TDD特殊导出类型");
        ability.put("samplingName", marker + " 样品");
        ability.put("testItem", marker + " 测试项目");
        ability.put("methodName", "重量法");
        ability.put("methodEngName", "Gravimetric");
        ability.put("standardNo", "GB/T-" + marker);
        ability.put("cycleWorkingDay", "5");
        ability.put("massRequired", "100");
        ability.put("sizeRequired", "0.074");
        ability.put("detectionLimit", "0.01%");
        ability.put("price", "120");
        ability.put("remark", remark);
        ability.put("standardNoSgs", "SGS-" + marker);
        ability.put("standardNoSop", "SOP-" + marker);
        ability.put("standardNoOthers", "OTHERS-" + marker);
        ability.put("standardNoDz", "DZ-" + marker);
        ability.put("labAbilities", List.of(Map.of(
                "code", "TJ",
                "isAbility", true,
                "hasCnas", true,
                "hasCma", true
        )));
        postAbp("/api/services/app/Ability/CreateAbility", ability);
    }

    private byte[] exportedAbilityBytes(long orgId, String marker) throws Exception {
        JsonNode exported = postAbp("/api/services/app/Ability/ExportData", Map.of(
                "orgId", orgId,
                "filter", List.of(Map.of(
                        "field", "samplingName",
                        "value", marker
                ))
        )).path("result");
        return mockMvc.perform(get("/File/DownloadTempFile")
                        .param("fileToken", exported.path("fileToken").asText())
                        .param("fileName", exported.path("fileName").asText())
                        .param("fileType", exported.path("fileType").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private byte[] originalTemplateWorkbook() throws Exception {
        return originalTemplateWorkbook("TDD-ALL-LAB", "GB/T-ALL-001", "ALL");
    }

    private byte[] originalTemplateWorkbook(String marker, String standardNo, String labValue) throws Exception {
        return originalTemplateWorkbook(marker, standardNo, labValue, "");
    }

    private byte[] originalTemplateWorkbook(String marker, String standardNo, String labValue, String secondLabValue) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("矿石");
            writeOriginalTemplateHeader(sheet);
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue(marker + " 样品");
            row.createCell(1).setCellValue(marker + " 测试项目");
            row.createCell(2).setCellValue(120);
            row.createCell(3).setCellValue("滴定法");
            row.createCell(4).setCellValue(standardNo);
            row.createCell(5).setCellValue(5);
            row.createCell(6).setCellValue("原模板导入");
            row.createCell(7).setCellValue(100);
            row.createCell(8).setCellValue("0.074");
            row.createCell(9).setCellValue("Titration");
            row.createCell(10).setCellValue("0.01%");
            row.createCell(11).setCellValue(labValue);
            row.createCell(12).setCellValue(secondLabValue);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] mergedStandardTemplateWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("矿石");
            writeOriginalTemplateHeader(sheet);

            Row first = sheet.createRow(2);
            first.createCell(0).setCellValue("TDD-MERGED 第一样品");
            first.createCell(1).setCellValue("TDD-MERGED 第一项目");
            first.createCell(2).setCellValue(120);
            first.createCell(3).setCellValue("重量法");
            first.createCell(4).setCellValue("GB/T-MERGED-001");
            first.createCell(5).setCellValue(5);
            first.createCell(7).setCellValue(100);
            first.createCell(8).setCellValue("0.074");
            first.createCell(9).setCellValue("Gravimetric");
            first.createCell(10).setCellValue("0.01%");
            first.createCell(11).setCellValue("ALL");

            Row second = sheet.createRow(3);
            second.createCell(0).setCellValue("TDD-MERGED 第二样品");
            second.createCell(1).setCellValue("TDD-MERGED 第二项目");
            second.createCell(2).setCellValue(130);
            second.createCell(3).setCellValue("重量法");
            second.createCell(5).setCellValue(6);
            second.createCell(7).setCellValue(150);
            second.createCell(8).setCellValue("0.050");
            second.createCell(9).setCellValue("Gravimetric");
            second.createCell(10).setCellValue("0.02%");
            second.createCell(11).setCellValue("CNAS");

            sheet.addMergedRegion(new CellRangeAddress(2, 3, 4, 4));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeOriginalTemplateHeader(Sheet sheet) {
        writeRow(sheet.createRow(0), ORIGINAL_HEADERS);
        writeRow(sheet.createRow(1), List.of(
                "Sample Name",
                "Testing Item",
                "Price/CNY",
                "Method Description in Chinese",
                "Standard Number",
                "TAT/Working Day",
                "Remark",
                "Required Sample Weight/g",
                "Size Requirement/mm",
                "Method Description in English",
                "Application Scope",
                "TJ",
                "SH"
        ));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 11, 12));
    }

    private void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
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

    private String text(Cell cell) {
        return cell == null ? "" : cell.toString().trim();
    }

    private int headerIndex(Row header, String title) {
        for (Cell cell : header) {
            if (text(cell).equals(title)) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing Excel header: " + title);
    }

    private boolean containsSample(Sheet sheet, String sampleName) {
        for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && text(row.getCell(0)).equals(sampleName)) {
                return true;
            }
        }
        return false;
    }

    private Row dataRowBySample(Sheet sheet, String sampleName) {
        for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && text(row.getCell(0)).equals(sampleName)) {
                return row;
            }
        }
        throw new AssertionError("Missing Excel sample row: " + sampleName);
    }

    private int headerCount(Row header, String title) {
        int count = 0;
        for (Cell cell : header) {
            if (text(cell).equals(title)) {
                count++;
            }
        }
        return count;
    }

    private void assertBoldTwelvePoint(Workbook workbook, Cell cell) {
        org.apache.poi.ss.usermodel.Font font = workbook.getFontAt(cell.getCellStyle().getFontIndex());
        assertThat(font.getBold()).isTrue();
        assertThat(font.getFontHeightInPoints()).isEqualTo((short) 12);
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
}
