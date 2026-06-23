package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.Ability;
import com.sgs.capability.model.FavoriteGroup;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/favorite-invoice-get-route-parity-store.json")
@AutoConfigureMockMvc
class FavoriteInvoiceGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/favorite-invoice-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset favorite/invoice GET route parity test store", ex);
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
    void favoriteReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        FavoriteGroup group = new FavoriteGroup();
        group.name = "GET Favorite " + System.nanoTime();
        group = store.saveFavorite(group);
        Ability ability = store.allAbilities().get(0);
        store.addFavoriteItem(group.id.toString(), ability.id.toString());

        JsonNode abilities = getAbp(get("/api/services/app/MyFavorite/GetMyFavoriteAbilityList")
                .param("MyFavoriteId", group.id.toString())).path("result");
        assertThat(abilities.path("items")).anySatisfy(item ->
                assertThat(item.path("id").asText()).isEqualTo(ability.id.toString()));

        JsonNode edit = getAbp(get("/api/services/app/MyFavorite/GetMyFavoriteForEdit")
                .param("Id", group.id.toString())).path("result");
        assertThat(edit.path("id").asText()).isEqualTo(group.id.toString());
        assertThat(edit.path("name").asText()).isEqualTo(group.name);
    }

    @Test
    void invoiceInfoAcceptsOriginalGetQueryId() throws Exception {
        SubscriptionPaymentItem payment = store.createPayment(null, 1, 30, 2,
                false, "http://localhost/success", "http://localhost/error");
        store.createInvoice(payment.id);

        JsonNode invoice = getAbp(get("/api/services/app/Invoice/GetInvoiceInfo")
                .param("Id", String.valueOf(payment.id))).path("result");

        assertThat(invoice.path("subscriptionPaymentId").asLong()).isEqualTo(payment.id);
        assertThat(invoice.path("invoiceNo").asText()).startsWith("INV-");
        assertThat(invoice.path("amount").decimalValue()).isEqualByComparingTo(payment.amount);
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
