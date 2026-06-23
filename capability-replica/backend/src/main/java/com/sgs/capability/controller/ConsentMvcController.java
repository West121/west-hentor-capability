package com.sgs.capability.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Copies the Web.Host IdentityServer consent controller contract for local flows. */
@Controller
public class ConsentMvcController {
    private static final boolean ENABLE_OFFLINE_ACCESS = true;
    private static final String OFFLINE_ACCESS_DISPLAY_NAME = "Offline Access";
    private static final String OFFLINE_ACCESS_DESCRIPTION =
            "Access to your applications and resources, even when you are offline";
    private static final String MUST_CHOOSE_ONE_ERROR = "You must pick at least one permission";
    private static final String INVALID_SELECTION_ERROR = "Invalid selection";

    private static final Map<String, ScopeDefinition> IDENTITY_SCOPES = new LinkedHashMap<>();
    private static final Map<String, ScopeDefinition> RESOURCE_SCOPES = new LinkedHashMap<>();

    static {
        IDENTITY_SCOPES.put("openid", new ScopeDefinition("openid", "User identifier",
                "Your user identifier", false, true));
        IDENTITY_SCOPES.put("profile", new ScopeDefinition("profile", "User profile",
                "Your user profile information", false, false));
        IDENTITY_SCOPES.put("email", new ScopeDefinition("email", "Email address",
                "Your email address", false, false));
        IDENTITY_SCOPES.put("phone", new ScopeDefinition("phone", "Phone number",
                "Your phone number", false, false));
        RESOURCE_SCOPES.put("default-api", new ScopeDefinition("default-api", "Default (all) API",
                "AllFunctionalityYouHaveInTheApplication", false, false));
    }

    @GetMapping({"/Consent", "/Consent/Index"})
    @ResponseBody
    public ResponseEntity<ConsentViewModel> index(@RequestParam(name = "returnUrl", required = false) String returnUrl,
                                                  @RequestParam(name = "ReturnUrl", required = false) String upperReturnUrl) {
        ConsentViewModel model = buildViewModel(firstNonBlank(returnUrl, upperReturnUrl), null);
        return ResponseEntity.ok(model);
    }

    @PostMapping({"/Consent", "/Consent/Index"})
    @ResponseBody
    public ResponseEntity<?> postIndex(@RequestParam MultiValueMap<String, String> params) {
        ProcessConsentResult result = processConsent(inputFromParams(params));
        if (result.isRedirect()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, result.redirectUri)
                    .build();
        }
        if (result.isShowView()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    private ProcessConsentResult processConsent(ConsentInputModel input) {
        ProcessConsentResult result = new ProcessConsentResult();
        boolean grantedConsent = false;
        if ("no".equals(input.button)) {
            grantedConsent = true;
        } else if ("yes".equals(input.button)) {
            if (input.scopesConsented != null && !input.scopesConsented.isEmpty()) {
                grantedConsent = true;
            } else {
                result.validationError = MUST_CHOOSE_ONE_ERROR;
            }
        } else {
            result.validationError = INVALID_SELECTION_ERROR;
        }

        if (grantedConsent) {
            // The original only redirects when IdentityServer can resolve the returnUrl context.
            if (authorizationContext(input.returnUrl) != null) {
                result.redirectUri = input.returnUrl;
            }
            return result;
        }
        result.viewModel = buildViewModel(input.returnUrl, input);
        return result;
    }

    private ConsentViewModel buildViewModel(String returnUrl, ConsentInputModel input) {
        ConsentContext context = authorizationContext(returnUrl);
        if (context == null) {
            return null;
        }

        ConsentViewModel model = new ConsentViewModel();
        model.button = input == null ? null : input.button;
        model.scopesConsented = input == null ? List.of() : List.copyOf(input.scopesConsented);
        model.rememberConsent = input == null || input.rememberConsent;
        model.returnUrl = returnUrl;
        model.clientName = context.clientId;
        model.clientUrl = "";
        model.clientLogoUrl = "";
        model.allowRememberConsent = true;
        model.identityScopes = requestedScopes(context, IDENTITY_SCOPES, model.scopesConsented, input == null);
        model.resourceScopes = requestedScopes(context, RESOURCE_SCOPES, model.scopesConsented, input == null);
        if (ENABLE_OFFLINE_ACCESS && context.scopes.contains("offline_access")) {
            model.resourceScopes = new ArrayList<>(model.resourceScopes);
            model.resourceScopes.add(offlineAccessScope(input == null || model.scopesConsented.contains("offline_access")));
        }
        return model;
    }

    private List<ScopeViewModel> requestedScopes(ConsentContext context, Map<String, ScopeDefinition> definitions,
                                                 List<String> selectedScopes, boolean defaultChecked) {
        List<ScopeViewModel> scopes = new ArrayList<>();
        for (String requestedScope : context.scopes) {
            ScopeDefinition definition = definitions.get(requestedScope);
            if (definition != null) {
                scopes.add(createScopeViewModel(definition, defaultChecked || selectedScopes.contains(definition.name)));
            }
        }
        return scopes;
    }

    private ScopeViewModel createScopeViewModel(ScopeDefinition definition, boolean checked) {
        return new ScopeViewModel(definition.name, definition.displayName, definition.description,
                definition.emphasize, definition.required, checked || definition.required);
    }

    private ScopeViewModel offlineAccessScope(boolean checked) {
        return new ScopeViewModel("offline_access", OFFLINE_ACCESS_DISPLAY_NAME, OFFLINE_ACCESS_DESCRIPTION,
                true, false, checked);
    }

    private ConsentContext authorizationContext(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank() || !returnUrl.startsWith("/") || returnUrl.startsWith("//")) {
            return null;
        }
        Map<String, String> query = parseQuery(returnUrl);
        String clientId = query.get("client_id");
        String scope = query.get("scope");
        if (clientId == null || clientId.isBlank() || scope == null || scope.isBlank()) {
            return null;
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String item : scope.split("\\s+")) {
            if (!item.isBlank()) {
                scopes.add(item);
            }
        }
        if (scopes.isEmpty()) {
            return null;
        }
        return new ConsentContext(returnUrl, clientId, scopes);
    }

    private Map<String, String> parseQuery(String returnUrl) {
        int queryStart = returnUrl.indexOf('?');
        if (queryStart < 0 || queryStart == returnUrl.length() - 1) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : returnUrl.substring(queryStart + 1).split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                values.put(decode(parts[0]), decode(parts[1]));
            }
        }
        return values;
    }

    private ConsentInputModel inputFromParams(MultiValueMap<String, String> params) {
        ConsentInputModel input = new ConsentInputModel();
        input.button = first(params, "Button", "button");
        input.scopesConsented = list(params, "ScopesConsented", "scopesConsented");
        input.rememberConsent = Boolean.parseBoolean(first(params, "RememberConsent", "rememberConsent"));
        input.returnUrl = first(params, "ReturnUrl", "returnUrl");
        return input;
    }

    private String first(MultiValueMap<String, String> params, String... names) {
        for (String name : names) {
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
        }
        return null;
    }

    private List<String> list(MultiValueMap<String, String> params, String... names) {
        List<String> values = new ArrayList<>();
        for (String name : names) {
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    values.addAll(entry.getValue());
                }
            }
        }
        return values;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record ConsentContext(String returnUrl, String clientId, Set<String> scopes) {
    }

    private record ScopeDefinition(String name, String displayName, String description,
                                   boolean emphasize, boolean required) {
    }

    public static class ConsentInputModel {
        public String button;
        public List<String> scopesConsented = List.of();
        public boolean rememberConsent;
        public String returnUrl;
    }

    public static class ConsentViewModel extends ConsentInputModel {
        public String clientName;
        public String clientUrl;
        public String clientLogoUrl;
        public boolean allowRememberConsent;
        public List<ScopeViewModel> identityScopes = List.of();
        public List<ScopeViewModel> resourceScopes = List.of();
    }

    public static class ProcessConsentResult {
        public String redirectUri;
        public ConsentViewModel viewModel;
        public String validationError;

        public boolean isRedirect() {
            return redirectUri != null;
        }

        public boolean isShowView() {
            return viewModel != null;
        }

        public boolean isHasValidationError() {
            return validationError != null;
        }
    }

    public record ScopeViewModel(String name, String displayName, String description,
                                 boolean emphasize, boolean required, boolean checked) {
    }
}
