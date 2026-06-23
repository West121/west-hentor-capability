package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.UserItem;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-email-code-parity-store.json")
@AutoConfigureMockMvc
class AccountEmailCodeParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-email-code-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account email-code parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CapabilityStore store;

    @Test
    void sendPasswordResetCodeRejectsUnknownEmailLikeOriginalGetUserByChecking() throws Exception {
        JsonNode response = postEmail("/api/services/app/Account/SendPasswordResetCode", "missing-reset@example.local");

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidEmailAddress");
    }

    @Test
    void sendPasswordResetCodeRejectsEmailLongerThanOriginalInputLimitBeforeIssuingCode() throws Exception {
        String longEmail = "r".repeat(245) + "@example.local";
        UserItem user = new UserItem();
        user.name = "Long";
        user.surname = "Email";
        user.userName = "longreset" + System.nanoTime();
        user.emailAddress = longEmail;
        UserItem created = store.registerUser(user, "123qwe", true, true);

        JsonNode response = postEmail("/api/services/app/Account/SendPasswordResetCode", longEmail);

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.user(created.id).orElseThrow().passwordResetCode).isNull();
    }

    @Test
    void sendEmailActivationLinkRejectsUnknownEmailLikeOriginalGetUserByChecking() throws Exception {
        JsonNode response = postEmail("/api/services/app/Account/SendEmailActivationLink", "missing-activation@example.local");

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidEmailAddress");
    }

    @Test
    void sendEmailActivationLinkRejectsOriginalRequiredEmailViolation() throws Exception {
        assertValidationFailure(postJson("/api/services/app/Account/SendEmailActivationLink", Map.of()));

        assertValidationFailure(postEmail("/api/services/app/Account/SendEmailActivationLink", " "));
    }

    @Test
    void resetPasswordRejectsInvalidCodeWithOriginalUserFriendlyError() throws Exception {
        JsonNode response = postJson("/api/services/app/Account/ResetPassword", Map.of(
                "userId", 999999,
                "resetCode", "wrong-code",
                "password", "123qwe"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidPasswordResetCode");
    }

    @Test
    void resetPasswordDoesNotAcceptLocalFixedCodeWhenStoredCodeDiffers() throws Exception {
        UserItem user = registeredUser(false);
        user.passwordResetCode = "real-reset-code";

        JsonNode response = postJson("/api/services/app/Account/ResetPassword", Map.of(
                "userId", user.id,
                "resetCode", "123456",
                "password", "newPassword1"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidPasswordResetCode");
    }

    @Test
    void resetPasswordResolvesOriginalEncryptedCParameter() throws Exception {
        UserItem user = store.user(2L).orElseThrow();
        user.passwordResetCode = "cipher-reset-code";

        JsonNode response = postJson("/api/services/app/Account/ResetPassword", Map.of(
                "c", "jfAOkzUApglqPz+jU5yb56TYOQsXTmZ1iD95xkzpCsQsqf5RE8RxOksr0YlmRtoe",
                "password", "cipherNew1"
        ));

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("userName").asText()).isEqualTo(user.userName);
        assertThat(store.passwordMatches(user.id, "cipherNew1")).isTrue();
    }

    @Test
    void activateEmailRejectsInvalidCodeWithOriginalMessage() throws Exception {
        JsonNode response = postJson("/api/services/app/Account/ActivateEmail", Map.of(
                "userId", 999999,
                "confirmationCode", "wrong-code"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidEmailConfirmationCode");
    }

    @Test
    void activateEmailDoesNotAcceptLocalFixedCodeWhenStoredCodeDiffers() throws Exception {
        UserItem user = registeredUser(false);
        user.emailConfirmationCode = "real-activation-code";

        JsonNode response = postJson("/api/services/app/Account/ActivateEmail", Map.of(
                "userId", user.id,
                "confirmationCode", "123456"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidEmailConfirmationCode");
    }

    @Test
    void activateEmailResolvesOriginalEncryptedCParameter() throws Exception {
        UserItem user = store.user(2L).orElseThrow();
        user.isEmailConfirmed = false;
        user.emailConfirmationCode = "cipher-activation-code";

        JsonNode response = postJson("/api/services/app/Account/ActivateEmail", Map.of(
                "c", "fByruW3gfqCybI3y56xa9QKMeuuwKCz5sG5RUZICgk6Fk3mZ7uneSy95zP3OTV5DTyofXRYf7KatYx2FFJp9TQ=="
        ));

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(store.user(user.id).orElseThrow().isEmailConfirmed).isTrue();
        assertThat(store.user(user.id).orElseThrow().emailConfirmationCode).isNull();
    }

    @Test
    void activateEmailKeepsAlreadyConfirmedUsersSuccessfulWithoutCheckingCode() throws Exception {
        UserItem user = registeredUser(true);

        JsonNode response = postJson("/api/services/app/Account/ActivateEmail", Map.of(
                "userId", user.id,
                "confirmationCode", "wrong-code"
        ));

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").isNull()).isTrue();
    }

    private UserItem registeredUser(boolean isEmailConfirmed) {
        UserItem input = new UserItem();
        input.name = "Confirmed";
        input.surname = "User";
        input.userName = "accountcode" + System.nanoTime();
        input.emailAddress = input.userName + "@example.local";
        return store.registerUser(input, "123qwe", true, isEmailConfirmed);
    }

    private JsonNode postEmail(String path, String emailAddress) throws Exception {
        return postJson(path, Map.of("emailAddress", emailAddress));
    }

    private JsonNode postJson(String path, Object payload) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
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
}
