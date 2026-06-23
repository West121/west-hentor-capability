package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.model.ChatMessageItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.ExcelTransferService;
import com.sgs.capability.service.TempFileService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/gdpr-collected-data-parity-store.json")
@AutoConfigureMockMvc
class GdprCollectedDataParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/gdpr-collected-data-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset GDPR collected data parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CapabilityStore store;

    @Autowired
    ExcelTransferService excel;

    @Autowired
    TempFileService tempFiles;

    @Test
    void prepareCollectedDataPublishesDownloadableZipWithOriginalChatExcel() throws Exception {
        postAbp("/api/services/app/Profile/PrepareCollectedData", Map.of());
        JsonNode notifications = postAbp("/api/services/app/Notification/GetUserNotifications", Map.of(
                "filter", "Gdpr",
                "maxResultCount", 10
        )).path("result").path("items");

        JsonNode notification = notifications.get(0);
        assertThat(notification.path("notificationName").asText()).isEqualTo("App.GdprDataPrepared");
        String binaryObjectId = notification.path("data").path("binaryObjectId").asText();
        assertThat(binaryObjectId).isNotBlank();

        byte[] zip = mockMvc.perform(get("/File/DownloadBinaryFile")
                        .param("id", binaryObjectId)
                        .param("contentType", "application/zip")
                        .param("fileName", "CollectedData.zip")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(zipEntry(zip, "ProfileInfo.txt")).contains("User name: admin");
        assertThat(firstZipEntryNameEndingWith(zip, ".xlsx")).isEqualTo("Chat_._query.xlsx");
        byte[] chatWorkbook = firstZipEntryEndingWith(zip, ".xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(chatWorkbook))) {
            Sheet sheet = workbook.getSheet("Messages");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(text(header.getCell(0))).isEqualTo("From");
            assertThat(text(header.getCell(1))).isEqualTo("To");
            assertThat(text(header.getCell(2))).isEqualTo("Message");
            assertThat(text(header.getCell(3))).isEqualTo("Read state");
            assertThat(text(header.getCell(4))).isEqualTo("Creation time");

            assertThat(sheet.getRow(1)).isNull();
            Row firstMessage = sheet.getRow(2);
            assertThat(text(firstMessage.getCell(0))).isEqualTo("./query");
            assertThat(text(firstMessage.getCell(1))).isEqualTo("You");
            assertThat(text(firstMessage.getCell(2))).isNotBlank();

            Cell creationTimeCell = firstMessage.getCell(4);
            assertThat(creationTimeCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(creationTimeCell)).isTrue();
            assertThat(creationTimeCell.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm:ss");
        }
    }

    @Test
    void prepareCollectedDataIncludesOriginalProfilePictureFileWhenUserHasAvatar() throws Exception {
        byte[] png = tinyPng();
        MockMultipartFile image = new MockMultipartFile("file", "avatar.png", "image/png", png);

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart("/Profile/UploadProfilePicture")
                        .file(image)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        String fileToken = upload.path("result").path("fileToken").asText();
        postAbp("/api/services/app/Profile/UpdateProfilePicture", Map.of(
                "fileToken", fileToken,
                "x", 0,
                "y", 0,
                "width", 1,
                "height", 1
        ));

        postAbp("/api/services/app/Profile/PrepareCollectedData", Map.of());
        JsonNode notifications = postAbp("/api/services/app/Notification/GetUserNotifications", Map.of(
                "filter", "Gdpr",
                "maxResultCount", 10
        )).path("result").path("items");
        String binaryObjectId = notifications.get(0).path("data").path("binaryObjectId").asText();

        byte[] zip = mockMvc.perform(get("/File/DownloadBinaryFile")
                        .param("id", binaryObjectId)
                        .param("contentType", "application/zip")
                        .param("fileName", "CollectedData.zip")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(zipEntryBytes(zip, "ProfilePicture.png")).isEqualTo(png);
    }

    @Test
    void chatExcelUsesOriginalTenancyNameForTenantConversations() throws Exception {
        TenantItem tenant = new TenantItem();
        tenant.tenancyName = "tenant-export";
        tenant.name = "Tenant Export";
        tenant.isActive = true;
        TenantItem createdTenant = store.createTenant(tenant);

        UserItem target = new UserItem();
        target.userName = "tenant-chat-user";
        target.name = "Tenant";
        target.surname = "Chat";
        target.emailAddress = "tenant-chat-user@example.com";
        UserItem createdTarget = store.registerUser(target, "123qwe", true, true);

        ChatMessageItem message = new ChatMessageItem();
        message.userId = 1L;
        message.targetUserId = createdTarget.id;
        message.targetTenantId = createdTenant.id;
        message.side = 2;
        message.readState = 1;
        message.receiverReadState = 2;
        message.message = "tenant conversation";
        message.creationTime = "2026-06-10T06:00:00";

        FileDto file = excel.chatMessagesExport(1L, createdTarget.id, createdTenant.id, List.of(message));

        assertThat(file.fileName).isEqualTo("Chat_tenant-export_tenant-chat-user.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(tempFiles.requireContent(file)))) {
            Row row = workbook.getSheet("Messages").getRow(2);
            assertThat(text(row.getCell(0))).isEqualTo("tenant-export/tenant-chat-user");
            assertThat(text(row.getCell(1))).isEqualTo("You");
        }
    }

    private String zipEntry(byte[] zip, String name) throws Exception {
        return new String(zipEntryBytes(zip, name), StandardCharsets.UTF_8);
    }

    private byte[] zipEntryBytes(byte[] zip, String name) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return input.readAllBytes();
                }
            }
        }
        throw new AssertionError("Missing zip entry: " + name);
    }

    private byte[] firstZipEntryEndingWith(byte[] zip, String suffix) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().endsWith(suffix)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    input.transferTo(output);
                    return output.toByteArray();
                }
            }
        }
        throw new AssertionError("Missing zip entry ending with: " + suffix);
    }

    private String firstZipEntryNameEndingWith(byte[] zip, String suffix) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().endsWith(suffix)) {
                    return entry.getName();
                }
            }
        }
        throw new AssertionError("Missing zip entry ending with: " + suffix);
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

    private byte[] tinyPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
    }
}
