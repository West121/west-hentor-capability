package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.FavoriteGroup;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/my-favorite-parity-store.json")
@AutoConfigureMockMvc
class MyFavoriteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/my-favorite-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset favorite parity test store", ex);
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
    void getMyFavoriteListPrependsOriginalDefaultListItem() throws Exception {
        FavoriteGroup group = new FavoriteGroup();
        group.name = "User Favorite " + System.nanoTime();
        store.saveFavorite(group);

        JsonNode items = getAbp("/api/services/app/MyFavorite/GetMyFavoriteList").path("result").path("items");

        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
        assertThat(items.get(0).path("id").isNull()).isTrue();
        assertThat(items.get(0).path("name").asText()).isEqualTo("默认清单");
        assertThat(items).anySatisfy(item -> assertThat(item.path("name").asText()).isEqualTo(group.name));
    }

    @Test
    void favoriteReadRoutesUseOriginalMyFavoriteDtoShape() throws Exception {
        FavoriteGroup group = new FavoriteGroup();
        group.name = "Dto Shape " + System.nanoTime();
        group = store.saveFavorite(group);

        JsonNode items = getAbp("/api/services/app/MyFavorite/GetMyFavoriteList").path("result").path("items");
        JsonNode edit = getAbp("/api/services/app/MyFavorite/GetMyFavoriteForEdit?Id=" + group.id).path("result");

        assertFavoriteDtoShape(items.get(0));
        JsonNode savedItem = null;
        for (JsonNode item : items) {
            if (item.path("name").asText().equals(group.name)) {
                savedItem = item;
                break;
            }
        }
        assertThat(savedItem).isNotNull();
        assertFavoriteDtoShape(savedItem);
        assertFavoriteDtoShape(edit);
    }

    @Test
    void addItemWithoutFavoriteIdUsesOriginalDefaultListOnly() throws Exception {
        String marker = "Default Favorite Item " + System.nanoTime();
        String abilityId = createAbility(marker);
        FavoriteGroup customGroup = new FavoriteGroup();
        customGroup.name = "000 Custom Favorite " + System.nanoTime();
        customGroup = store.saveFavorite(customGroup);

        postAbp("/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        JsonNode defaultItems = getAbp("/api/services/app/MyFavorite/GetMyFavoriteAbilityList")
                .path("result").path("items");
        JsonNode customItems = getAbp("/api/services/app/MyFavorite/GetMyFavoriteAbilityList?MyFavoriteId=" + customGroup.id)
                .path("result").path("items");
        JsonNode groups = getAbp("/api/services/app/MyFavorite/GetMyFavoriteList").path("result").path("items");

        assertThat(defaultItems).anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(abilityId));
        assertThat(customItems).noneSatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(abilityId));
        assertThat(groups).noneSatisfy(item -> assertThat(item.path("name").asText()).isEqualTo("默认收藏"));
    }

    @Test
    void favoriteGroupsAndDefaultItemsAreScopedToCurrentUser() throws Exception {
        String adminToken = adminToken();
        String queryToken = tokenFor("query", "123qwe");
        String marker = "User Scoped Favorite " + System.nanoTime();
        String abilityId = createAbility(marker);
        FavoriteGroup adminGroup = new FavoriteGroup();
        adminGroup.name = "Admin Favorite " + System.nanoTime();
        adminGroup = store.saveFavorite(adminGroup);

        postAbp("/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        JsonNode queryGroups = getAbp(queryToken, "/api/services/app/MyFavorite/GetMyFavoriteList")
                .path("result").path("items");
        JsonNode queryDefaultItemsBefore = getAbp(queryToken, "/api/services/app/MyFavorite/GetMyFavoriteAbilityList")
                .path("result").path("items");

        assertThat(queryGroups).hasSize(1);
        assertThat(queryGroups.get(0).path("id").isNull()).isTrue();
        assertThat(queryGroups.get(0).path("name").asText()).isEqualTo("默认清单");
        assertThat(queryDefaultItemsBefore).noneSatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(abilityId));

        postAbp(queryToken, "/api/services/app/MyFavorite/AddItem", Map.of("abilityId", abilityId));

        JsonNode queryDefaultItemsAfter = getAbp(queryToken, "/api/services/app/MyFavorite/GetMyFavoriteAbilityList")
                .path("result").path("items");
        JsonNode adminCustomItems = getAbp(adminToken, "/api/services/app/MyFavorite/GetMyFavoriteAbilityList?MyFavoriteId=" + adminGroup.id)
                .path("result").path("items");

        assertThat(queryDefaultItemsAfter).anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(abilityId));
        assertThat(adminCustomItems).noneSatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(abilityId));
    }

    @Test
    void saveOrUpdateMyFavoriteKeepsOriginalVoidResponseWhileCreatingGroup() throws Exception {
        String name = "Void Favorite " + System.nanoTime();

        JsonNode response = postAbp("/api/services/app/MyFavorite/SaveOrUpdateMyFavorite", Map.of(
                "name", name
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.favoriteGroups()).anySatisfy(group -> {
            assertThat(group.name).isEqualTo(name);
            assertThat(group.userId).isEqualTo(1L);
        });
    }

    @Test
    void saveOrUpdateMyFavoriteKeepsOriginalVoidResponseWhileUpdatingGroup() throws Exception {
        FavoriteGroup group = new FavoriteGroup();
        group.name = "Favorite Before " + System.nanoTime();
        group = store.saveFavorite(group);

        JsonNode response = postAbp("/api/services/app/MyFavorite/SaveOrUpdateMyFavorite", Map.of(
                "id", group.id.toString(),
                "name", "Favorite After"
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.favorite(group.id.toString())).hasValueSatisfying(saved -> {
            assertThat(saved.name).isEqualTo("Favorite After");
            assertThat(saved.userId).isEqualTo(1L);
        });
    }

    @Test
    void saveOrUpdateMyFavoriteRejectsDuplicateNameWithOriginalError() throws Exception {
        String name = "Duplicate Favorite " + System.nanoTime();
        FavoriteGroup existing = new FavoriteGroup();
        existing.name = name;
        store.saveFavorite(existing);

        JsonNode response = postJson("/api/services/app/MyFavorite/SaveOrUpdateMyFavorite", Map.of(
                "name", name
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("名称已存在");
        assertThat(store.favoriteGroups().stream().filter(group -> name.equals(group.name)).count()).isEqualTo(1);
    }

    private String createAbility(String marker) throws Exception {
        postAbp("/api/services/app/Ability/CreateAbility", Map.ofEntries(
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
        JsonNode items = postAbp("/api/services/app/Ability/FindPageAblibities", Map.of(
                "filter", marker,
                "maxResultCount", 10,
                "skipCount", 0
        )).path("result").path("items");
        assertThat(items).hasSize(1);
        return items.get(0).path("id").asText();
    }

    private void assertFavoriteDtoShape(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name");
    }

    private JsonNode getAbp(String url) throws Exception {
        return getAbp(adminToken(), url);
    }

    private JsonNode getAbp(String token, String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        return postAbp(adminToken(), url, payload);
    }

    private JsonNode postAbp(String token, String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
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

    private JsonNode postJson(String url, Object payload) throws Exception {
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

    private String adminToken() {
        return tokenFor("admin", "123qwe");
    }

    private String tokenFor(String userName, String password) {
        return authService.authenticate(userName, password).orElseThrow().token();
    }
}
