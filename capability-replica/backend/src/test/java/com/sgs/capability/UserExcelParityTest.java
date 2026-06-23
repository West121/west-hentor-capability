package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/user-excel-parity-store.json")
@AutoConfigureMockMvc
class UserExcelParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/user-excel-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset user Excel parity test store", ex);
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
    void userExportKeepsOriginalFileHeadersAndCreationDateFormat() throws Exception {
        JsonNode file = postAbp("/api/services/app/User/GetUsersToExcel", Map.of("filter", "admin")).path("result");

        assertThat(file.path("fileName").asText()).isEqualTo("UserList.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(file)))) {
            Sheet sheet = workbook.getSheet("Users");
            assertThat(sheet).isNotNull();

            List<String> headers = List.of("Name", "Surname", "User name", "Phone number", "Email address",
                    "Email confirm", "Roles", "Active", "Creation time");
            Row header = sheet.getRow(0);
            for (int index = 0; index < headers.size(); index++) {
                assertThat(text(header.getCell(index))).isEqualTo(headers.get(index));
            }

            assertThat(sheet.getRow(1)).isNull();
            Row firstUser = sheet.getRow(2);
            assertThat(text(firstUser.getCell(2))).isEqualTo("admin");

            Cell creationTimeCell = firstUser.getCell(8);
            assertThat(creationTimeCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(creationTimeCell)).isTrue();
            assertThat(creationTimeCell.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd");
        }
    }

    @Test
    void getUsersRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(postAbpRaw("/api/services/app/User/GetUsers", Map.of(
                "skipCount", 0,
                "maxResultCount", 0
        )));
        assertValidationFailure(postAbpRaw("/api/services/app/User/GetUsers", Map.of(
                "skipCount", 0,
                "maxResultCount", 1001
        )));
        assertValidationFailure(postAbpRaw("/api/services/app/User/GetUsers", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        )));
        assertValidationFailure(getAbpRaw("/api/services/app/User/GetUsers", -1, 10));
    }

    @Test
    void userExportKeepsOriginalNonPagedFilterInput() throws Exception {
        JsonNode file = postAbp("/api/services/app/User/GetUsersToExcel", Map.of(
                "filter", "admin",
                "skipCount", -1,
                "maxResultCount", 0
        )).path("result");

        assertThat(file.path("fileName").asText()).isEqualTo("UserList.xlsx");
        assertThat(file.path("fileToken").asText()).isNotBlank();
    }

    @Test
    void userExportDefaultsToOriginalNameSurnameSorting() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        user("excel_sort_z_" + suffix, "Same " + suffix, "Zulu");
        user("excel_sort_a_" + suffix, "Same " + suffix, "Alpha");

        JsonNode file = postAbp("/api/services/app/User/GetUsersToExcel", Map.of("filter", suffix)).path("result");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(file)))) {
            Row firstUser = workbook.getSheet("Users").getRow(2);
            assertThat(text(firstUser.getCell(0))).isEqualTo("Same " + suffix);
            assertThat(text(firstUser.getCell(1))).isEqualTo("Alpha");
        }
    }

    @Test
    void invalidUserImportReportKeepsOriginalFileSheetHeadersAndDataStartRow() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", invalidUserWorkbook());

        JsonNode response = uploadUsers(file);

        JsonNode errorFile = response.path("result").path("errorFile");
        assertThat(errorFile.path("fileName").asText()).isEqualTo("InvalidUserImportList.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download(errorFile)))) {
            Sheet sheet = workbook.getSheet("Invalid user imports");
            assertThat(sheet).isNotNull();

            List<String> headers = List.of("UserName", "Name", "Surname", "EmailAddress",
                    "PhoneNumber", "Password", "Roles", "Refuse Reason");
            Row header = sheet.getRow(0);
            for (int index = 0; index < headers.size(); index++) {
                assertThat(text(header.getCell(index))).isEqualTo(headers.get(index));
            }

            assertThat(sheet.getRow(1)).isNull();
            Row invalid = sheet.getRow(2);
            assertThat(text(invalid.getCell(0))).isEqualTo("bad-user");
            assertThat(text(invalid.getCell(3))).isEqualTo("not-an-email");
            assertThat(text(invalid.getCell(6))).isEqualTo("Admin");
            assertThat(text(invalid.getCell(7))).contains("EmailAddress格式不正确");
        }
    }

    @Test
    void invalidUserImportPublishesOriginalDownloadNotificationData() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", invalidUserWorkbook());

        JsonNode response = uploadUsers(file);
        JsonNode errorFile = response.path("result").path("errorFile");

        JsonNode notifications = postAbp("/api/services/app/Notification/GetUserNotifications", Map.of(
                "filter", "DownloadInvalidImportUsers",
                "maxResultCount", 10
        )).path("result").path("items");
        JsonNode notification = notifications.get(0);
        assertThat(notification.path("notificationName").asText()).isEqualTo("App.DownloadInvalidImportUsers");
        assertThat(notification.path("message").asText()).isEqualTo("ClickToSeeInvalidUsers");
        assertThat(notification.path("severity").asText()).isEqualTo("Info");
        assertThat(notification.path("data").path("fileToken").asText()).isEqualTo(errorFile.path("fileToken").asText());
        assertThat(notification.path("data").path("fileType").asText()).isEqualTo(errorFile.path("fileType").asText());
        assertThat(notification.path("data").path("fileName").asText()).isEqualTo("InvalidUserImportList.xlsx");
    }

    @Test
    void validUserImportPublishesOriginalSuccessNotification() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validUserWorkbook());

        JsonNode response = uploadUsers(file);
        assertThat(response.path("result").path("importedCount").asInt()).isEqualTo(1);

        JsonNode notifications = postAbp("/api/services/app/Notification/GetUserNotifications", Map.of(
                "filter", "SimpleMessage",
                "maxResultCount", 10
        )).path("result").path("items");
        JsonNode notification = notifications.get(0);
        assertThat(notification.path("notificationName").asText()).isEqualTo("App.SimpleMessage");
        assertThat(notification.path("message").asText())
                .isEqualTo("User import process has been completed successfully. All users in file are imported.");
        assertThat(notification.path("severity").asText()).isEqualTo("Success");
    }

    @Test
    void userImportAcceptsOriginalAnyMultipartFieldName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("usersExcel", "users-any-field.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                userWorkbook(originalUserImportHeaders(), "any-field-user", "Any", "Field",
                        "any-field-user@example.com", "Admin"));

        JsonNode response = uploadUsers(file);

        assertThat(response.path("result").path("importedCount").asInt()).isEqualTo(1);
        JsonNode users = postAbp("/api/services/app/User/GetUsers", Map.of(
                "filter", "any-field-user",
                "maxResultCount", 10
        )).path("result").path("items");
        assertThat(users.get(0).path("userName").asText()).isEqualTo("any-field-user");
    }

    @Test
    void userImportReadsOriginalFixedColumnsEvenWhenHeadersAreRenamed() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                userWorkbook(List.of("Login", "Given", "Family", "Mail", "Mobile", "Secret", "RoleList"),
                        "fixed-column-user", "Fixed", "Column", "fixed-column-user@example.com", "Admin"));

        JsonNode response = uploadUsers(file);

        assertThat(response.path("result").path("importedCount").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("errorCount").asInt()).isZero();
        JsonNode users = postAbp("/api/services/app/User/GetUsers", Map.of(
                "filter", "fixed-column-user",
                "maxResultCount", 10
        )).path("result").path("items");
        assertThat(users.get(0).path("userName").asText()).isEqualTo("fixed-column-user");
        assertThat(users.get(0).path("emailAddress").asText()).isEqualTo("fixed-column-user@example.com");
    }

    @Test
    void userImportReadsRowsFromAllWorksheetsLikeOriginalImporter() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", multiSheetUserWorkbook());

        JsonNode response = uploadUsers(file);

        assertThat(response.path("result").path("importedCount").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("errorCount").asInt()).isZero();
        JsonNode users = postAbp("/api/services/app/User/GetUsers", Map.of(
                "filter", "second-sheet-user",
                "maxResultCount", 10
        )).path("result").path("items");
        assertThat(users.get(0).path("userName").asText()).isEqualTo("second-sheet-user");
    }

    @Test
    void invalidExcelUserImportPublishesOriginalWarnSimpleMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "this is not an excel workbook".getBytes(StandardCharsets.UTF_8));

        uploadUsers(file);

        String message = "User import process has failed. File is invalid.";
        JsonNode notifications = postAbp("/api/services/app/Notification/GetUserNotifications", Map.of(
                "filter", "File is invalid",
                "maxResultCount", 10
        )).path("result").path("items");
        JsonNode notification = notificationWithMessage(notifications, message);
        assertThat(notification).as("original invalid Excel notification").isNotNull();
        assertThat(notification.path("notificationName").asText()).isEqualTo("App.SimpleMessage");
        assertThat(notification.path("message").asText()).isEqualTo(message);
        assertThat(notification.path("severity").asText()).isEqualTo("Warn");
    }

    @Test
    void userImportTreatsBlankFirstColumnRowsAsEmptyLikeOriginalReader() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", blankFirstColumnUserWorkbook());

        JsonNode response = uploadUsers(file);

        JsonNode result = response.path("result");
        assertThat(result.path("invalidFile").asBoolean()).isTrue();
        assertThat(result.path("totalCount").asInt()).isZero();
        assertThat(result.path("errorFile").isMissingNode() || result.path("errorFile").isNull()).isTrue();

        String message = "User import process has failed. File is invalid.";
        JsonNode notifications = postAbp("/api/services/app/Notification/GetUserNotifications", Map.of(
                "filter", "File is invalid",
                "maxResultCount", 10
        )).path("result").path("items");
        JsonNode notification = notificationWithMessage(notifications, message);
        assertThat(notification).as("original blank first column notification").isNotNull();
        assertThat(notification.path("notificationName").asText()).isEqualTo("App.SimpleMessage");
        assertThat(notification.path("severity").asText()).isEqualTo("Warn");
    }

    private JsonNode uploadUsers(MockMultipartFile file) throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(multipart("/Users/ImportFromExcel")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode notificationWithMessage(JsonNode notifications, String message) {
        for (JsonNode notification : notifications) {
            if (message.equals(notification.path("message").asText())) {
                return notification;
            }
        }
        return null;
    }

    private byte[] invalidUserWorkbook() throws Exception {
        return userWorkbook(originalUserImportHeaders(), "bad-user", "Bad", "User", "not-an-email", "Admin");
    }

    private byte[] validUserWorkbook() throws Exception {
        return userWorkbook(originalUserImportHeaders(), "import-ok-user", "Import", "Ok", "import-ok-user@example.com", "Admin");
    }

    private byte[] multiSheetUserWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet first = workbook.createSheet("EmptyUsers");
            writeUserHeader(first.createRow(0), originalUserImportHeaders());
            Sheet second = workbook.createSheet("MoreUsers");
            writeUserHeader(second.createRow(0), originalUserImportHeaders());
            writeUserRow(second.createRow(1), "second-sheet-user", "Second", "Sheet",
                    "second-sheet-user@example.com", "Admin");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] blankFirstColumnUserWorkbook() throws Exception {
        return userWorkbook(originalUserImportHeaders(), "", "No", "Login", "no-login@example.com", "Admin");
    }

    private UserItem user(String userName, String name, String surname) {
        UserItem user = new UserItem();
        user.userName = userName;
        user.name = name;
        user.surname = surname;
        user.emailAddress = userName + "@example.local";
        user.phoneNumber = "13800000000";
        user.isActive = true;
        return store.saveUser(user, List.of("User"), List.of(), List.of());
    }

    private List<String> originalUserImportHeaders() {
        return List.of("UserName", "Name", "Surname", "EmailAddress", "PhoneNumber", "Password", "Roles");
    }

    private byte[] userWorkbook(List<String> headers, String userName, String name, String surname, String email,
                                String roles) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            writeUserHeader(sheet.createRow(0), headers);
            writeUserRow(sheet.createRow(1), userName, name, surname, email, roles);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeUserHeader(Row header, List<String> headers) {
        for (int index = 0; index < headers.size(); index++) {
            header.createCell(index).setCellValue(headers.get(index));
        }
    }

    private void writeUserRow(Row row, String userName, String name, String surname, String email, String roles) {
        row.createCell(0).setCellValue(userName);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(surname);
        row.createCell(3).setCellValue(email);
        row.createCell(4).setCellValue("13800000000");
        row.createCell(5).setCellValue("123qwe");
        row.createCell(6).setCellValue(roles);
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

    private JsonNode getAbpRaw(String url, int skipCount, int maxResultCount) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("SkipCount", String.valueOf(skipCount))
                        .param("MaxResultCount", String.valueOf(maxResultCount)))
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe")
                .orElseThrow()
                .token();
    }

    private String text(Cell cell) {
        return cell == null ? "" : cell.toString();
    }
}
