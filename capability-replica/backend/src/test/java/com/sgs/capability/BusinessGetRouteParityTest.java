package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.Ability;
import com.sgs.capability.model.Laboratory;
import com.sgs.capability.model.Sample;
import com.sgs.capability.model.SampleType;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/business-get-route-parity-store.json")
@AutoConfigureMockMvc
class BusinessGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/business-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset business GET route parity test store", ex);
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
    void abilityReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        String marker = "TDD-GET-ABILITY-" + System.nanoTime();
        Ability ability = store.saveAbility(ability(marker, "矿石-" + marker));

        JsonNode edit = getAbp("/api/services/app/Ability/GetAbilityForEdit", "Id", ability.id.toString()).path("result");
        assertThat(edit.path("abilityDto").path("id").asText()).isEqualTo(ability.id.toString());
        assertThat(edit.path("abilityDto").path("testItem").asText()).isEqualTo(marker);

        JsonNode typeList = getAbp("/api/services/app/Ability/GetOrgTypeLit", "Id", "2").path("result").path("items");
        assertThat(typeList).anySatisfy(item -> assertThat(item.path("name").asText()).isEqualTo("矿石-" + marker));

        JsonNode template = getAbp("/api/services/app/Ability/GetTemplateExcel", "OrgId", "2").path("result");
        assertThat(template.path("fileName").asText()).isNotBlank();
        assertThat(template.path("fileToken").asText()).isNotBlank();
    }

    @Test
    void laboratoryReadRouteAcceptsOriginalGetQueryId() throws Exception {
        Laboratory lab = new Laboratory();
        lab.code = "GETLAB-" + System.nanoTime();
        lab.name = "GET 实验室";
        lab = store.saveLab(lab);

        JsonNode response = getAbp("/api/services/app/Laboratory/GetLabForEdit", "Id", lab.id.toString()).path("result");

        assertThat(response.path("id").asText()).isEqualTo(lab.id.toString());
        assertThat(response.path("code").asText()).isEqualTo(lab.code);
    }

    @Test
    void sampleTypeReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        SampleType type = sampleType("TDD-GET-TYPE-" + System.nanoTime());

        JsonNode edit = getAbp("/api/services/app/SampleType/GetForEdit", "Id", type.id.toString()).path("result");
        assertThat(edit.path("type").path("id").asText()).isEqualTo(type.id.toString());

        JsonNode list = getAbp("/api/services/app/SampleType/GetListByOrg", "OrgId", "2").path("result").path("items");
        assertThat(list).anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(type.id.toString()));
    }

    @Test
    void sampleReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        SampleType type = sampleType("TDD-GET-SAMPLE-TYPE-" + System.nanoTime());
        Sample sample = sample("TDD-GET-SAMPLE-" + System.nanoTime(), type);

        JsonNode edit = getAbp("/api/services/app/Sample/GetForEdit", "Id", sample.id.toString()).path("result");
        assertThat(edit.path("id").asText()).isEqualTo(sample.id.toString());

        JsonNode list = getAbp("/api/services/app/Sample/GetList", "TypeId", type.id.toString()).path("result").path("items");
        assertThat(list).anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(sample.id.toString()));
    }

    private Ability ability(String testItem, String typeName) {
        Ability ability = new Ability();
        ability.orgId = 2L;
        ability.typeName = typeName;
        ability.samplingName = testItem;
        ability.testItem = testItem;
        ability.methodName = "重量法";
        ability.methodEngName = "Gravimetric method";
        ability.standardNo = testItem;
        ability.cycleWorkingDay = "5";
        ability.massRequired = "100";
        ability.sizeRequired = "0.074";
        ability.detectionLimit = "0.01%";
        ability.price = "100";
        return ability;
    }

    private SampleType sampleType(String displayName) {
        SampleType type = new SampleType();
        type.displayName = displayName;
        type.orgId = 2L;
        return store.saveSampleType(type);
    }

    private Sample sample(String displayName, SampleType type) {
        Sample sample = new Sample();
        sample.displayName = displayName;
        sample.engName = displayName;
        sample.alias = displayName;
        sample.typeId = type.id;
        return store.saveSample(sample);
    }

    private JsonNode getAbp(String url, String paramName, String paramValue) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param(paramName, paramValue))
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
