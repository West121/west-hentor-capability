package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.OrgAbilitySetting;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-property-parity-store.json")
@AutoConfigureMockMvc
class AbilityPropertyParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-property-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ability property parity test store", ex);
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
    void saveOrgSettingKeepsOriginalVoidResponseWhileSavingFieldSettings() throws Exception {
        long orgId = 2L;
        List<String> properties = List.of("typeName", "testItem", "methodName", "price");

        JsonNode response = postAbp("/api/services/app/AbilityProperty/SaveOrgSetting", Map.of(
                "orgId", orgId,
                "propertyName", properties,
                "lab", List.of("TJ", "SH"),
                "isPublic", true,
                "description", "公开字段设置"
        ));

        assertThat(response.path("result").isNull()).isTrue();
        OrgAbilitySetting saved = store.orgSetting(orgId);
        assertThat(saved.propertyName).containsExactlyElementsOf(properties);
        assertThat(saved.lab).containsExactly("TJ", "SH");
        assertThat(saved.isPublic).isTrue();
        assertThat(saved.description).isEqualTo("公开字段设置");
    }

    @Test
    void getOrgAbilitySettingAcceptsOriginalGetQueryOrgId() throws Exception {
        long orgId = 2L;
        store.saveOrgSetting(setting(orgId, List.of("typeName", "methodName")));

        JsonNode result = getAbp("/api/services/app/AbilityProperty/GetOrgAbilitySetting", "OrgId", String.valueOf(orgId))
                .path("result");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).asText()).isEqualTo("typeName");
        assertThat(result.get(1).asText()).isEqualTo("methodName");
    }

    @Test
    void abilityPropertyListExposesOriginalNameAndCamelCaseFields() throws Exception {
        JsonNode result = postAbp("/api/services/app/AbilityProperty/AbilityPropertyList", Map.of())
                .path("result");
        JsonNode samplingName = findByTitle(result, "样品名称");

        assertThat(samplingName.path("name").asText()).isEqualTo("SamplingName");
        assertThat(samplingName.path("camelCase").asText()).isEqualTo("samplingName");
    }

    @Test
    void abilityPropertyListMatchesOriginalDescribedAbilityTableProperties() throws Exception {
        JsonNode result = postAbp("/api/services/app/AbilityProperty/AbilityPropertyList", Map.of())
                .path("result");

        assertThat(names(result)).containsExactly(
                "OrgName",
                "TypeName",
                "SamplingName",
                "TestItem",
                "MethodName",
                "MethodEngName",
                "StandardNo",
                "CycleWorkingDay",
                "MassRequired",
                "SizeRequired",
                "DetectionLimit",
                "Price",
                "Remark",
                "LabAbility",
                "StandardNoSgs",
                "StandardNoSop",
                "StandardNoOthers",
                "StandardNoDz"
        );
    }

    @Test
    void orgAbilitySettingAcceptsOriginalPascalCasePropertiesAndReturnsCamelCaseSettings() throws Exception {
        long orgId = 2L;
        store.saveOrgSetting(setting(orgId, List.of("SamplingName", "MethodName")));

        JsonNode orgList = postAbp("/api/services/app/AbilityProperty/OrgAbilityPropertyList", Map.of("orgId", orgId))
                .path("result")
                .path("propertyList");
        assertThat(findByName(orgList, "SamplingName").path("enabled").asBoolean()).isTrue();
        assertThat(findByName(orgList, "MethodName").path("enabled").asBoolean()).isTrue();

        JsonNode setting = getAbp("/api/services/app/AbilityProperty/GetOrgAbilitySetting", "OrgId", String.valueOf(orgId))
                .path("result");
        assertThat(setting.get(0).asText()).isEqualTo("samplingName");
        assertThat(setting.get(1).asText()).isEqualTo("methodName");
    }

    @Test
    void labGroupSpecialStandardFieldsAreScopedToLabGroupOnly() throws Exception {
        List<String> configuredProperties = List.of(
                "typeName",
                "methodName",
                "standardNoSgs",
                "standardNoSop",
                "standardNoOthers",
                "standardNoDz"
        );
        long labGroupId = createOrgUnit("Lab Group");
        long nfId = createOrgUnit("TDD-NF-SPECIAL-FIELDS");

        store.saveOrgSetting(setting(labGroupId, configuredProperties));
        store.saveOrgSetting(setting(nfId, configuredProperties));

        JsonNode labGroupProperties = postAbp("/api/services/app/AbilityProperty/OrgAbilityPropertyList", Map.of("orgId", labGroupId))
                .path("result")
                .path("propertyList");
        assertThat(names(labGroupProperties)).contains(
                "StandardNoSgs", "StandardNoSop", "StandardNoOthers", "StandardNoDz");
        assertThat(findByName(labGroupProperties, "StandardNoSgs").path("enabled").asBoolean()).isTrue();

        JsonNode regularProperties = postAbp("/api/services/app/AbilityProperty/OrgAbilityPropertyList", Map.of("orgId", nfId))
                .path("result")
                .path("propertyList");
        assertThat(names(regularProperties)).doesNotContain(
                "StandardNoSgs", "StandardNoSop", "StandardNoOthers", "StandardNoDz");

        JsonNode regularSetting = getAbp("/api/services/app/AbilityProperty/GetOrgAbilitySetting", "OrgId", String.valueOf(nfId))
                .path("result");
        assertThat(values(regularSetting)).doesNotContain(
                "standardNoSgs", "standardNoSop", "standardNoOthers", "standardNoDz");
    }

    private OrgAbilitySetting setting(long orgId, List<String> properties) {
        OrgAbilitySetting setting = new OrgAbilitySetting();
        setting.orgId = orgId;
        setting.propertyName = properties;
        setting.lab = List.of();
        setting.isPublic = true;
        setting.description = "GET query setting";
        return setting;
    }

    private long createOrgUnit(String displayName) throws Exception {
        return postAbp("/api/services/app/OrganizationUnit/CreateOrganizationUnit", Map.of(
                "parentId", 1,
                "displayName", displayName
        )).path("result").path("id").asLong();
    }

    private JsonNode findByTitle(JsonNode rows, String title) {
        for (JsonNode row : rows) {
            if (row.path("title").asText().equals(title)) {
                return row;
            }
        }
        throw new AssertionError("Ability property title not found: " + title);
    }

    private JsonNode findByName(JsonNode rows, String name) {
        for (JsonNode row : rows) {
            if (row.path("name").asText().equals(name)) {
                return row;
            }
        }
        throw new AssertionError("Ability property name not found: " + name);
    }

    private List<String> names(JsonNode rows) {
        List<String> names = new ArrayList<>();
        for (JsonNode row : rows) {
            names.add(row.path("name").asText());
        }
        return names;
    }

    private List<String> values(JsonNode rows) {
        List<String> values = new ArrayList<>();
        for (JsonNode row : rows) {
            values.add(row.asText());
        }
        return values;
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

    private JsonNode getAbp(String url, String name, String value) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param(name, value))
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
}
