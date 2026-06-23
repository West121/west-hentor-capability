package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/common-lookup-timing-get-route-parity-store.json")
@AutoConfigureMockMvc
class CommonLookupTimingGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/common-lookup-timing-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset common lookup/timing GET route parity test store", ex);
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

    @Test
    void commonLookupEditionComboboxAcceptsOriginalGetQueryParameter() throws Exception {
        JsonNode response = getAbp(get("/api/services/app/CommonLookup/GetEditionsForCombobox")
                .param("onlyFreeItems", "true")).path("result").path("items");

        assertThat(response).isNotEmpty();
        assertThat(response).allSatisfy(item -> assertThat(item.path("isFree").asBoolean()).isTrue());
    }

    @Test
    void timingRoutesAcceptOriginalPascalCaseGetQueryParameters() throws Exception {
        JsonNode timezones = getAbp(get("/api/services/app/Timing/GetTimezones")
                .param("DefaultTimezoneScope", "1")).path("result").path("items");
        assertThat(timezones).anySatisfy(item ->
                assertThat(item.path("value").asText()).isEqualTo("China Standard Time"));

        JsonNode combobox = getAbp(get("/api/services/app/Timing/GetTimezoneComboboxItems")
                .param("SelectedTimezoneId", "China Standard Time")).path("result");
        assertThat(combobox).anySatisfy(item -> {
            assertThat(item.path("value").asText()).isEqualTo("China Standard Time");
            assertThat(item.path("isSelected").asBoolean()).isTrue();
        });
    }

    @Test
    void commonLookupFindUsersDoesNotSearchPhoneNumberLikeOriginal() throws Exception {
        JsonNode result = postAbp(post("/api/services/app/CommonLookup/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "filter", "13800000000",
                        "skipCount", 0,
                        "maxResultCount", 10
                )))).path("result");

        assertThat(result.path("totalCount").asInt()).isZero();
        assertThat(result.path("items")).isEmpty();
    }

    @Test
    void commonLookupFindUsersRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(rawAbp(post("/api/services/app/CommonLookup/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", 0,
                        "maxResultCount", 0
                )))));

        assertValidationFailure(rawAbp(post("/api/services/app/CommonLookup/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", 0,
                        "maxResultCount", 1001
                )))));

        assertValidationFailure(rawAbp(post("/api/services/app/CommonLookup/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", -1,
                        "maxResultCount", 10
                )))));
    }

    @Test
    void commonLookupFindUsersKeepsOriginalNameThenSurnameOrdering() throws Exception {
        store.registerUser(user("z-lookup-sort", "LookupSort", "Aardvark"), "123qwe", true, true);
        store.registerUser(user("a-lookup-sort", "LookupSort", "Zephyr"), "123qwe", true, true);

        JsonNode items = postAbp(post("/api/services/app/CommonLookup/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "filter", "LookupSort",
                        "skipCount", 0,
                        "maxResultCount", 10
                )))).path("result").path("items");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("name").asText()).contains("LookupSort Aardvark");
        assertThat(items.get(1).path("name").asText()).contains("LookupSort Zephyr");
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
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

    private JsonNode postAbp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode rawAbp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private UserItem user(String userName, String name, String surname) {
        UserItem user = new UserItem();
        user.userName = userName;
        user.name = name;
        user.surname = surname;
        user.emailAddress = userName + "@example.com";
        return user;
    }
}
