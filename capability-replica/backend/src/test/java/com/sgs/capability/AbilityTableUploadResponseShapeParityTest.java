package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-table-upload-response-shape-store.json")
@AutoConfigureMockMvc
class AbilityTableUploadResponseShapeParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-table-upload-response-shape-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ability table upload response shape store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void uploadNewStandardMvcResponseOnlyContainsOriginalItemsAndFileFields() throws Exception {
        JsonNode response = upload("/AbilityTable/UploadNewStandard", "standard-upload.xlsx", standardWorkbook());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("items", "file");
        assertThat(fieldNames(response.path("file"))).containsExactlyInAnyOrder("fileName", "fileType", "fileToken");
        JsonNode row = response.path("items").get(0);
        assertThat(fieldNames(row)).containsExactlyInAnyOrder("old", "new", "name", "statu", "remark");
        assertThat(row.path("old").asText()).isEqualTo("GB/T-OLD-UPLOAD");
        assertThat(row.path("new").asText()).isEqualTo("GB/T-NEW-UPLOAD");
        assertThat(row.has("newValue")).isFalse();
    }

    @Test
    void uploadSubcontractAbilityMvcResponseOnlyContainsOriginalItemsAndFileFields() throws Exception {
        JsonNode response = upload("/AbilityTable/UploadSubcontractAbility", "subcontract-upload.xlsx",
                subcontractWorkbook());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("items", "file");
        assertThat(fieldNames(response.path("file"))).containsExactlyInAnyOrder("fileName", "fileType", "fileToken");
    }

    @Test
    void uploadAbilityTableMvcResponseOnlyContainsOriginalListsAndUploadedLabCodes() throws Exception {
        MockMultipartFile file = excelFile("ability-upload.xlsx", abilityWorkbook());

        String body = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(file)
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(fieldNames(response)).containsExactlyInAnyOrder("abilityTableList", "labCodeList");
        assertThat(StreamSupport.stream(response.path("labCodeList").spliterator(), false)
                .map(JsonNode::asText)
                .toList()).containsExactly("TJ");
    }

    @Test
    void mvcUploadRoutesAcceptOriginalAnyMultipartFieldName() throws Exception {
        assertThat(upload("/AbilityTable/UploadNewStandard", "upload", "standard-any-field.xlsx", standardWorkbook())
                .path("file").path("fileName").asText()).isEqualTo("standard-any-field.xlsx");
        assertThat(upload("/AbilityTable/UploadSubcontractAbility", "upload", "subcontract-any-field.xlsx",
                subcontractWorkbook()).path("file").path("fileName").asText()).isEqualTo("subcontract-any-field.xlsx");

        String body = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(excelFile("upload", "ability-any-field.xlsx", abilityWorkbook()))
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(fieldNames(objectMapper.readTree(body))).containsExactlyInAnyOrder("abilityTableList", "labCodeList");
    }

    @Test
    void uploadAbilityTableRejectsOrgWithoutOriginalAbilitySetting() throws Exception {
        String body = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(excelFile("ability-no-setting.xlsx", abilityWorkbook()))
                        .param("orgId", "987654")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("没有设置业务部门能力信息");
    }

    @Test
    void uploadAbilityTableRejectsConfiguredButMissingOriginalOrganization() throws Exception {
        long missingOrgId = 987655L;
        mockMvc.perform(post("/api/services/app/AbilityProperty/SaveOrgSetting")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "orgId", missingOrgId,
                                "propertyName", List.of("samplingName", "testItem", "standardNo"),
                                "lab", List.of("TJ"),
                                "isPublic", true,
                                "description", "missing org setting"
                        ))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(excelFile("ability-missing-org.xlsx", abilityWorkbook()))
                        .param("orgId", String.valueOf(missingOrgId))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("导入的部门不存在");
    }

    @Test
    void uploadAbilityTableUsesOriginalRequestOrgAndSheetNameInsteadOfUploadedColumns() throws Exception {
        String body = mockMvc.perform(multipart("/AbilityTable/UploadAbilityTable")
                        .file(excelFile("ability-overridden-org-type.xlsx", abilityWorkbookWithOrgAndTypeColumns()))
                        .param("orgId", "2")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode row = objectMapper.readTree(body).path("abilityTableList").get(0);
        assertThat(row.path("orgName").asText()).isEqualTo("化学检测");
        assertThat(row.path("typeName").asText()).isEqualTo("TDD-SHEET-TYPE");
        assertThat(row.path("samplingName").asText()).isEqualTo("TDD-ORG-TYPE 样品");
    }

    private JsonNode upload(String url, String fileName, byte[] bytes) throws Exception {
        return upload(url, "file", fileName, bytes);
    }

    private JsonNode upload(String url, String fieldName, String fileName, byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart(url)
                        .file(excelFile(fieldName, fileName, bytes))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private MockMultipartFile excelFile(String fileName, byte[] bytes) {
        return excelFile("file", fileName, bytes);
    }

    private MockMultipartFile excelFile(String fieldName, String fileName, byte[] bytes) {
        return new MockMultipartFile(fieldName, fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private Set<String> fieldNames(JsonNode node) {
        Iterator<String> names = node.fieldNames();
        Iterable<String> iterable = () -> names;
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toSet());
    }

    private byte[] standardWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("标准更新");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("原标准号");
            header.createCell(1).setCellValue("新标准号");
            header.createCell(2).setCellValue("标准名称");
            header.createCell(3).setCellValue("标准状态");
            header.createCell(4).setCellValue("备注");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("GB/T-OLD-UPLOAD");
            row.createCell(1).setCellValue("GB/T-NEW-UPLOAD");
            row.createCell(2).setCellValue("上传标准");
            row.createCell(3).setCellValue("现行");
            row.createCell(4).setCellValue("备注");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] subcontractWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("分包能力");
            sheet.createRow(0).createCell(0).setCellValue("分包能力");
            Row header = sheet.createRow(1);
            List<String> headers = List.of(
                    "实验室名称",
                    "联系方式",
                    "检测/校准项目或类别",
                    "CMA/CNAS No(截止日期)",
                    "选定依据",
                    "评估人",
                    "评估结果"
            );
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] abilityWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("能力导入");
            Row cn = sheet.createRow(0);
            List<String> cnHeaders = List.of(
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
            for (int index = 0; index < cnHeaders.size(); index++) {
                cn.createCell(index).setCellValue(cnHeaders.get(index));
            }

            Row en = sheet.createRow(1);
            en.createCell(0).setCellValue("Sample Name");
            en.createCell(1).setCellValue("Testing Item");
            en.createCell(2).setCellValue("Price/CNY");
            en.createCell(3).setCellValue("Method");
            en.createCell(4).setCellValue("Standard Number");
            en.createCell(5).setCellValue("Cycle");
            en.createCell(6).setCellValue("Remark");
            en.createCell(7).setCellValue("Mass Required");
            en.createCell(8).setCellValue("Size Required");
            en.createCell(9).setCellValue("Method English");
            en.createCell(10).setCellValue("Scope");
            en.createCell(11).setCellValue("TJ");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] abilityWorkbookWithOrgAndTypeColumns() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("TDD-SHEET-TYPE");
            Row header = sheet.createRow(0);
            List<String> headers = List.of(
                    "业务线",
                    "类型",
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
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }

            Row labs = sheet.createRow(1);
            labs.createCell(headers.size() - 1).setCellValue("TJ");

            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("上传业务线应被忽略");
            data.createCell(1).setCellValue("上传类型应被忽略");
            data.createCell(2).setCellValue("TDD-ORG-TYPE 样品");
            data.createCell(3).setCellValue("TDD-ORG-TYPE 测试");
            data.createCell(4).setCellValue("120");
            data.createCell(5).setCellValue("重量法");
            data.createCell(6).setCellValue("GB/T-ORG-TYPE-001");
            data.createCell(7).setCellValue("5");
            data.createCell(8).setCellValue("备注");
            data.createCell(9).setCellValue("100g");
            data.createCell(10).setCellValue("0.074mm");
            data.createCell(11).setCellValue("Gravimetric method");
            data.createCell(12).setCellValue("矿石");
            data.createCell(13).setCellValue("CNAS");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
