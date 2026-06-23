package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/notification-date-filter-parity-store.json")
@AutoConfigureMockMvc
class NotificationDateFilterParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/notification-date-filter-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset notification date filter parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void notificationDateRangeFiltersListsAndBulkDeletesLikeOriginalInput() throws Exception {
        JsonNode currentUnread = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "UNREAD")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result");
        int unreadBeforeDelete = currentUnread.path("totalCount").asInt();
        assertThat(unreadBeforeDelete).isGreaterThan(0);

        JsonNode futureUnread = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "UNREAD")
                .param("StartDate", "2999-01-01T00:00:00.000Z")
                .param("EndDate", "2999-12-31T23:59:59.999Z")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result");

        assertThat(futureUnread.path("totalCount").asInt()).isZero();
        assertThat(futureUnread.path("unreadCount").asInt()).isZero();
        assertThat(futureUnread.path("items")).isEmpty();

        abp(delete("/api/services/app/Notification/DeleteAllUserNotifications")
                .param("State", "UNREAD")
                .param("StartDate", "2999-01-01T00:00:00.000Z")
                .param("EndDate", "2999-12-31T23:59:59.999Z"));

        JsonNode currentUnreadAfterDelete = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "UNREAD")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result");
        assertThat(currentUnreadAfterDelete.path("totalCount").asInt()).isEqualTo(unreadBeforeDelete);
        assertThat(currentUnreadAfterDelete.path("unreadCount").asInt()).isEqualTo(unreadBeforeDelete);
    }

    @Test
    void setNotificationAsReadRejectsNotificationsOwnedByAnotherUserLikeOriginalService() throws Exception {
        JsonNode adminNotification = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "ALL")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result").path("items").get(0);
        String adminNotificationId = adminNotification.path("id").asText();

        String body = mockMvc.perform(post("/api/services/app/Notification/SetNotificationAsRead")
                        .header("Authorization", "Bearer " + queryToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("id", adminNotificationId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Given user notification id (" + adminNotificationId + ") is not belong to the current user (2)");
    }

    @Test
    void deleteNotificationRejectsNotificationsOwnedByAnotherUserLikeOriginalService() throws Exception {
        JsonNode adminNotification = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "ALL")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result").path("items").get(0);
        String adminNotificationId = adminNotification.path("id").asText();

        String body = mockMvc.perform(delete("/api/services/app/Notification/DeleteNotification")
                        .header("Authorization", "Bearer " + queryToken())
                        .param("Id", adminNotificationId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("This notification doesn't belong to you.");

        JsonNode adminNotifications = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "ALL")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result").path("items");
        assertThat(adminNotifications)
                .anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(adminNotificationId));
    }

    private JsonNode abp(MockHttpServletRequestBuilder request) throws Exception {
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

    private String queryToken() {
        return authService.authenticate("query", "123qwe").orElseThrow().token();
    }
}
