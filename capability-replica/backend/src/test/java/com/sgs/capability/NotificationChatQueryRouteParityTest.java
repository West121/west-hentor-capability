package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/notification-chat-query-route-parity-store.json")
@AutoConfigureMockMvc
class NotificationChatQueryRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/notification-chat-query-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset notification/chat query route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void unblockSeedFriendship() throws Exception {
        raw(post("/api/services/app/Friendship/UnblockUser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userId", 2))));
        rawAs(queryToken(), post("/api/services/app/Friendship/UnblockUser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userId", 1))));
    }

    @Test
    void notificationRoutesAcceptOriginalGetQueryAndPutContracts() throws Exception {
        JsonNode notifications = abp(get("/api/services/app/Notification/GetUserNotifications")
                .param("State", "UNREAD")
                .param("StartDate", "2026-01-01T00:00:00.000Z")
                .param("EndDate", "2026-12-31T23:59:59.999Z")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result");

        assertThat(notifications.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(notifications.path("items")).hasSize(1);
        assertThat(notifications.path("items").get(0).path("readState").asInt()).isZero();
        assertThat(notifications.path("unreadCount").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode update = abp(put("/api/services/app/Notification/UpdateNotificationSettings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "receiveNotifications", true,
                        "notifications", List.of(Map.of(
                                "name", "Capability.AbilityChanged",
                                "displayName", "能力表变更",
                                "isSubscribed", false
                        ))
                ))));

        assertThat(update.path("result").isNull()).isTrue();
        JsonNode settings = abp(get("/api/services/app/Notification/GetNotificationSettings")).path("result");
        assertThat(settings.path("receiveNotifications").asBoolean()).isTrue();
        assertThat(settings.has("desktopNotifications")).isFalse();
        assertThat(settings.has("emailNotifications")).isFalse();
        assertThat(settings.has("smsNotifications")).isFalse();
        assertThat(settings.path("notifications").get(0).path("isSubscribed").asBoolean()).isFalse();
    }

    @Test
    void userNotificationsRejectOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(raw(post("/api/services/app/Notification/GetUserNotifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", 0,
                        "maxResultCount", 0
                )))));

        assertValidationFailure(raw(post("/api/services/app/Notification/GetUserNotifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", 0,
                        "maxResultCount", 1001
                )))));

        assertValidationFailure(raw(post("/api/services/app/Notification/GetUserNotifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", -1,
                        "maxResultCount", 10
                )))));

        assertValidationFailure(raw(get("/api/services/app/Notification/GetUserNotifications")
                .param("SkipCount", "-1")
                .param("MaxResultCount", "10")));
    }

    @Test
    void chatMessagesAcceptOriginalGetQueryContract() throws Exception {
        JsonNode friends = abp(get("/api/services/app/Chat/GetUserChatFriendsWithSettings")).path("result");
        assertThat(friends.path("friends")).anySatisfy(friend ->
                assertThat(friend.path("friendUserId").asLong()).isEqualTo(2L));

        JsonNode messages = abp(get("/api/services/app/Chat/GetUserChatMessages")
                .param("UserId", "2")).path("result").path("items");

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).path("targetUserId").asLong()).isEqualTo(2L);
    }

    @Test
    void chatMessagesUseOriginalMinMessageIdAsOlderThanCursor() throws Exception {
        abp(post("/api/services/app/Chat/SendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "userId", 2,
                        "message", "TDD older cursor first"
                ))));
        abp(post("/api/services/app/Chat/SendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "userId", 2,
                        "message", "TDD older cursor second"
                ))));

        JsonNode allMessages = abp(get("/api/services/app/Chat/GetUserChatMessages")
                .param("UserId", "2")).path("result").path("items");
        long newestId = allMessages.get(allMessages.size() - 1).path("id").asLong();

        JsonNode olderMessages = abp(get("/api/services/app/Chat/GetUserChatMessages")
                .param("UserId", "2")
                .param("MinMessageId", String.valueOf(newestId))).path("result").path("items");

        assertThat(olderMessages).isNotEmpty();
        assertThat(olderMessages.get(olderMessages.size() - 1).path("id").asLong()).isLessThan(newestId);
        assertThat(olderMessages).allSatisfy(message ->
                assertThat(message.path("id").asLong()).isLessThan(newestId));
    }

    @Test
    void chatMessagesRejectOriginalUserIdRangeViolations() throws Exception {
        assertValidationFailure(raw(get("/api/services/app/Chat/GetUserChatMessages")
                .param("UserId", "0")));

        assertValidationFailure(raw(post("/api/services/app/Chat/GetUserChatMessages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userId", 0)))));
    }

    @Test
    void sendMessageToBlockedFriendReturnsOriginalError() throws Exception {
        abp(post("/api/services/app/Friendship/BlockUser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userId", 2))));

        JsonNode response = raw(post("/api/services/app/Chat/SendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "userId", 2,
                        "message", "blocked message"
                ))));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("User is blocked.");
    }

    @Test
    void sendMessageToMissingUserReturnsOriginalDeletedUserError() throws Exception {
        JsonNode response = raw(post("/api/services/app/Chat/SendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "userId", 999999,
                        "message", "missing target"
                ))));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Target user could not be found. It's probably deleted.");
    }

    @Test
    void sendMessageWhenReceiverBlockedSenderKeepsMessageOnlyForSender() throws Exception {
        String marker = "receiver blocked sender " + System.nanoTime();
        abpAs(queryToken(), post("/api/services/app/Friendship/BlockUser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userId", 1))));

        JsonNode sent = abp(post("/api/services/app/Chat/SendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "userId", 2,
                        "message", marker
                )))).path("result");
        assertThat(sent.path("message").asText()).isEqualTo(marker);

        JsonNode senderMessages = abp(get("/api/services/app/Chat/GetUserChatMessages")
                .param("UserId", "2")).path("result").path("items");
        assertThat(senderMessages).anySatisfy(message ->
                assertThat(message.path("message").asText()).isEqualTo(marker));

        JsonNode receiverMessages = abpAs(queryToken(), get("/api/services/app/Chat/GetUserChatMessages")
                .param("UserId", "1")).path("result").path("items");
        assertThat(receiverMessages).noneSatisfy(message ->
                assertThat(message.path("message").asText()).isEqualTo(marker));
    }

    private JsonNode abp(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode response = rawAs(adminToken(), request);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode abpAs(String token, MockHttpServletRequestBuilder request) throws Exception {
        JsonNode response = rawAs(token, request);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode raw(MockHttpServletRequestBuilder request) throws Exception {
        return rawAs(adminToken(), request);
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private JsonNode rawAs(String token, MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private String queryToken() {
        return authService.authenticate("query", "123qwe").orElseThrow().token();
    }
}
