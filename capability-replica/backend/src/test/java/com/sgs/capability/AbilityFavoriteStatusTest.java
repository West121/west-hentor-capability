package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static java.util.Map.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-favorite-status-store.json")
@AutoConfigureMockMvc
class AbilityFavoriteStatusTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-favorite-status-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset favorite status test store", ex);
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
    void abilityRowsExposeFavoriteStatusAfterAddAndRemove() throws Exception {
        String token = adminToken();
        String marker = "TDD收藏状态-" + System.nanoTime();
        String abilityId = createAbility(token, marker);

        assertThat(findAbility(token, marker).path("isCollected").asBoolean()).isFalse();

        postAbp(token, "/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        assertThat(findAbility(token, marker).path("isCollected").asBoolean()).isTrue();

        postAbp(token, "/api/services/app/MyFavorite/RemoveItem", Map.of("abilityId", abilityId));

        assertThat(findAbility(token, marker).path("isCollected").asBoolean()).isFalse();
    }

    @Test
    void abilityRowsExposeFavoriteStatusForCurrentUserOnly() throws Exception {
        String adminToken = adminToken();
        String queryToken = tokenFor("query", "123qwe");
        store.user(2L).ifPresent(user -> {
            if (!user.assignedRoleNames.contains("Admin")) {
                user.assignedRoleNames.add("Admin");
            }
        });
        String marker = "Scoped收藏状态-" + System.nanoTime();
        String abilityId = createAbility(adminToken, marker);

        postAbp(adminToken, "/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        assertThat(findAbility(adminToken, marker).path("isCollected").asBoolean()).isTrue();
        assertThat(findAbility(queryToken, marker).path("isCollected").asBoolean()).isFalse();

        postAbp(queryToken, "/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        assertThat(findAbility(adminToken, marker).path("isCollected").asBoolean()).isTrue();
        assertThat(findAbility(queryToken, marker).path("isCollected").asBoolean()).isTrue();
    }

    @Test
    void abilityQueryRowsDoNotExposeManagementFavoriteStatus() throws Exception {
        String token = adminToken();
        String marker = "Query收藏隔离-" + System.nanoTime();
        String abilityId = createAbility(token, marker);

        postAbp(token, "/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        assertThat(findAbility(token, marker).path("isCollected").asBoolean()).isTrue();
        assertThat(findQueryAbility(token, marker).path("isCollected").asBoolean()).isFalse();
    }

    private String createAbility(String token, String marker) throws Exception {
        postAbp(token, "/api/services/app/Ability/CreateAbility", Map.ofEntries(
                entry("orgId", 2),
                entry("typeName", "矿石"),
                entry("samplingName", marker),
                entry("testItem", marker),
                entry("methodName", "重量法"),
                entry("methodEngName", "Gravimetric method"),
                entry("standardNo", marker),
                entry("cycleWorkingDay", "5"),
                entry("massRequired", "100"),
                entry("sizeRequired", "0.074"),
                entry("detectionLimit", "0.01%"),
                entry("price", "100")
        ));
        return findAbility(token, marker).path("id").asText();
    }

    private JsonNode findAbility(String token, String marker) throws Exception {
        JsonNode response = postAbp(token, "/api/services/app/Ability/FindPageAblibities", Map.of(
                "filter", marker,
                "maxResultCount", 10,
                "skipCount", 0
        ));
        JsonNode items = response.path("result").path("items");
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    private JsonNode findQueryAbility(String token, String marker) throws Exception {
        JsonNode response = postAbp(token, "/api/services/app/AbilityQuery/FindAblibities", Map.of(
                "filter", marker,
                "maxResultCount", 10,
                "skipCount", 0
        ));
        JsonNode items = response.path("result").path("items");
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    private JsonNode postAbp(String token, String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String adminToken() {
        return tokenFor("admin", "123qwe");
    }

    private String tokenFor(String userName, String password) {
        return authService.authenticate(userName, password)
                .orElseThrow()
                .token();
    }
}
