package com.sgs.capability.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.OrganizationUnit;
import com.sgs.capability.model.RoleItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal copy of the original GraphQL query container. */
@RestController
@RequestMapping("/graphql")
public class GraphqlController {
    private static final String NO_PERMISSION = "[ERR001] You don't have permission to access this resource! You need to be granted access the permission %s.";
    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("(\\w+)\\s*:\\s*(\"[^\"]*\"|\\$\\w+|-?\\d+|true|false|null)");

    private final CapabilityStore store;
    private final AuthService auth;
    private final ObjectMapper objectMapper;

    public GraphqlController(CapabilityStore store, AuthService auth, ObjectMapper objectMapper) {
        this.store = store;
        this.auth = auth;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public Map<String, Object> execute(@RequestBody(required = false) GraphqlRequest request,
                                       @RequestHeader(name = "Authorization", required = false) String authorization) {
        String query = request == null ? "" : safe(request.query);
        Map<String, Object> variables = request == null || request.variables == null ? Map.of() : request.variables;
        String operationName = request == null ? "" : safe(request.operationName);
        return executeQuery(query, variables, operationName, authorization);
    }

    @GetMapping
    public Map<String, Object> executeGet(@RequestParam(name = "query", required = false) String query,
                                          @RequestParam(name = "variables", required = false) String variables,
                                          @RequestParam(name = "operationName", required = false) String operationName,
                                          @RequestHeader(name = "Authorization", required = false) String authorization) {
        return executeQuery(safe(query), variables(variables), safe(operationName), authorization);
    }

    private Map<String, Object> executeQuery(String query, Map<String, Object> variables, String operationName,
                                             String authorization) {
        AuthContext context = auth.currentUser(authorization).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, String>> errors = new ArrayList<>();
        Map<String, GraphqlField> fields = topLevelFields(query, variables, operationName);

        for (Map.Entry<String, GraphqlField> entry : fields.entrySet()) {
            GraphqlField field = entry.getValue();
            if ("__schema".equals(field.fieldName())) {
                data.put(entry.getKey(), project(schemaIntrospection(), field.children()));
            }
            if ("__type".equals(field.fieldName())) {
                data.put(entry.getKey(), project(typeIntrospection(field.args().get("name")), field.children()));
            }
            if ("roles".equals(field.fieldName())) {
                putField(data, errors, entry.getKey(), "Pages.Administration.Roles", context, () -> roles(field));
            }
            if ("organizationUnits".equals(field.fieldName())) {
                putField(data, errors, entry.getKey(), "Pages.Administration.OrganizationUnits", context,
                        () -> organizationUnits(field));
            }
            if ("users".equals(field.fieldName())) {
                putField(data, errors, entry.getKey(), "Pages.Administration.Users", context, () -> users(field));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }
        return response;
    }

    private Map<String, Object> variables(String variables) {
        if (variables == null || variables.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(variables, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void putField(Map<String, Object> data, List<Map<String, String>> errors, String field,
                          String permission, AuthContext context, FieldResolver resolver) {
        if (context == null || !auth.hasPermission(context, permission)) {
            data.put(field, null);
            errors.add(Map.of("message", NO_PERMISSION.formatted(permission)));
            return;
        }
        data.put(field, resolver.resolve());
    }

    private List<Map<String, Object>> roles(GraphqlField field) {
        return store.roles(null).stream()
                .filter(role -> matchesInteger(role.id, field.args().get("id")))
                .filter(role -> matchesText(role.name, field.args().get("name")))
                .filter(role -> !field.args().containsKey("tenantId"))
                .map(role -> project(roleDto(role), field.children()))
                .toList();
    }

    private List<Map<String, Object>> organizationUnits(GraphqlField field) {
        return store.orgUnits().stream()
                .filter(org -> matchesLong(org.id, field.args().get("id")))
                .filter(org -> matchesText(org.code, field.args().get("code")))
                .filter(org -> !field.args().containsKey("tenantId"))
                .map(org -> project(organizationUnitDto(org), field.children()))
                .toList();
    }

    private Map<String, Object> users(GraphqlField field) {
        Map<String, String> args = field.args();
        List<UserItem> filtered = store.filteredUsers(args.get("filter"), integerArg(args.get("roleId")), List.of(),
                        booleanArg(args.get("onlyLockedUsers")))
                .stream()
                .filter(user -> matchesLong(user.id, args.get("id")))
                .filter(user -> matchesText(user.name, args.get("name")))
                .filter(user -> matchesText(user.surname, args.get("surname")))
                .filter(user -> matchesText(user.emailAddress, args.get("emailAddress")))
                .sorted(userComparator(args.get("sorting")))
                .toList();
        int skip = Math.max(integerArg(args.get("skipCount"), 0), 0);
        int take = integerArg(args.get("MaxResultCount"), 10);
        List<Map<String, Object>> items = filtered.stream()
                .skip(skip)
                .limit(take <= 0 ? 10 : take)
                .map(user -> userDto(user, null))
                .toList();
        return project(Map.of("totalCount", filtered.size(), "items", items), field.children());
    }

    private Map<String, Object> roleDto(RoleItem role) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", role.id);
        dto.put("name", role.name);
        dto.put("displayName", role.displayName);
        dto.put("isStatic", role.isStatic);
        dto.put("isDefault", role.isDefault);
        dto.put("creationTime", stringValue(role.creationTime));
        dto.put("lastModificationTime", null);
        dto.put("creatorUserId", null);
        dto.put("lastModifierUserId", null);
        dto.put("tenantId", null);
        return dto;
    }

    private Map<String, Object> organizationUnitDto(OrganizationUnit org) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", org.id);
        dto.put("code", org.code);
        dto.put("displayName", org.displayName);
        dto.put("tenantId", null);
        return dto;
    }

    private Map<String, Object> userDto(UserItem user, GraphqlField itemField) {
        Map<String, GraphqlField> children = itemField == null ? Map.of() : itemField.children();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.id);
        dto.put("name", user.name);
        dto.put("surname", user.surname);
        dto.put("userName", user.userName);
        dto.put("emailAddress", user.emailAddress);
        dto.put("phoneNumber", user.phoneNumber);
        dto.put("isEmailConfirmed", user.isEmailConfirmed);
        dto.put("isActive", user.isActive);
        dto.put("creationTime", stringValue(user.creationTime));
        dto.put("tenantId", null);
        dto.put("profilePictureId", user.profilePictureId == null ? null : user.profilePictureId.toString());
        dto.put("roles", userRoles(user, childByFieldName(children, "roles")));
        dto.put("organizationUnits", userOrganizationUnits(user, childByFieldName(children, "organizationUnits")));
        return project(dto, children);
    }

    private List<Map<String, Object>> userRoles(UserItem user, GraphqlField roleField) {
        return store.roles(null).stream()
                .filter(role -> user.assignedRoleNames.stream().anyMatch(name -> equalsText(name, role.name)))
                .map(role -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", role.id);
                    dto.put("name", role.name);
                    dto.put("displayName", role.displayName);
                    return project(dto, roleField == null ? Map.of() : roleField.children());
                })
                .toList();
    }

    private List<Map<String, Object>> userOrganizationUnits(UserItem user, GraphqlField organizationUnitField) {
        return store.orgUnits().stream()
                .filter(org -> user.organizationUnits.contains(org.id))
                .map(org -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", org.id);
                    dto.put("code", org.code);
                    dto.put("displayName", org.displayName);
                    return project(dto, organizationUnitField == null ? Map.of() : organizationUnitField.children());
                })
                .toList();
    }

    private Comparator<UserItem> userComparator(String sorting) {
        String normalized = safe(sorting).toLowerCase(Locale.ROOT);
        Comparator<UserItem> comparator;
        if (normalized.contains("username")) {
            comparator = Comparator.comparing(user -> safe(user.userName));
        } else if (normalized.contains("email")) {
            comparator = Comparator.comparing(user -> safe(user.emailAddress));
        } else {
            comparator = Comparator.comparing((UserItem user) -> safe(user.name))
                    .thenComparing(user -> safe(user.surname));
        }
        return normalized.contains("desc") ? comparator.reversed() : comparator;
    }

    private Map<String, Object> schemaIntrospection() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("queryType", typeIntrospection("QueryContainer"));
        schema.put("mutationType", null);
        schema.put("subscriptionType", null);
        schema.put("types", List.of(
                typeIntrospection("QueryContainer"),
                typeIntrospection("RoleType"),
                typeIntrospection("OrganizationUnitType"),
                typeIntrospection("UserPagedResult"),
                typeIntrospection("UserType"),
                typeIntrospection("UserRoleType"),
                typeIntrospection("UserOrganizationUnitType")
        ));
        schema.put("directives", List.of());
        return schema;
    }

    private Map<String, Object> typeIntrospection(String name) {
        return switch (safe(name)) {
            case "QueryContainer" -> objectType("QueryContainer", fields(
                    fieldInfo("roles"),
                    fieldInfo("organizationUnits"),
                    fieldInfo("users")
            ));
            case "RoleType" -> objectType("RoleType", fields(
                    fieldInfo("id"),
                    fieldInfo("name"),
                    fieldInfo("displayName"),
                    fieldInfo("isStatic"),
                    fieldInfo("isDefault"),
                    fieldInfo("creationTime"),
                    fieldInfo("tenantId")
            ));
            case "OrganizationUnitType" -> objectType("OrganizationUnitType", fields(
                    fieldInfo("id"),
                    fieldInfo("code"),
                    fieldInfo("displayName"),
                    fieldInfo("tenantId")
            ));
            case "UserPagedResult" -> objectType("UserPagedResult", fields(
                    fieldInfo("totalCount"),
                    fieldInfo("items")
            ));
            case "UserType" -> objectType("UserType", fields(
                    fieldInfo("id"),
                    fieldInfo("name"),
                    fieldInfo("surname"),
                    fieldInfo("userName"),
                    fieldInfo("emailAddress"),
                    fieldInfo("phoneNumber"),
                    fieldInfo("isEmailConfirmed"),
                    fieldInfo("isActive"),
                    fieldInfo("creationTime"),
                    fieldInfo("tenantId"),
                    fieldInfo("profilePictureId"),
                    fieldInfo("roles"),
                    fieldInfo("organizationUnits")
            ));
            case "UserRoleType" -> objectType("UserRoleType", fields(
                    fieldInfo("id"),
                    fieldInfo("name"),
                    fieldInfo("displayName")
            ));
            case "UserOrganizationUnitType" -> objectType("UserOrganizationUnitType", fields(
                    fieldInfo("id"),
                    fieldInfo("code"),
                    fieldInfo("displayName")
            ));
            default -> null;
        };
    }

    private Map<String, Object> objectType(String name, List<Map<String, Object>> fields) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("kind", "OBJECT");
        type.put("name", name);
        type.put("description", null);
        type.put("fields", fields);
        return type;
    }

    @SafeVarargs
    private final List<Map<String, Object>> fields(Map<String, Object>... fields) {
        return Arrays.asList(fields);
    }

    private Map<String, Object> fieldInfo(String name) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("description", null);
        field.put("args", List.of());
        field.put("type", Map.of("kind", "SCALAR", "name", "String"));
        field.put("isDeprecated", false);
        field.put("deprecationReason", null);
        return field;
    }

    private Map<String, GraphqlField> topLevelFields(String query, Map<String, Object> variables, String operationName) {
        int bodyStart = bodyStart(query, operationName);
        int bodyEnd = bodyStart < 0 ? -1 : matching(query, bodyStart, '{', '}');
        return bodyEnd > bodyStart ? selectionFields(query.substring(bodyStart + 1, bodyEnd), variables) : Map.of();
    }

    private int bodyStart(String query, String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return query.indexOf('{');
        }
        Pattern operationPattern = Pattern.compile("\\b(?:query|mutation|subscription)\\s+"
                + Pattern.quote(operationName) + "\\b");
        Matcher matcher = operationPattern.matcher(query);
        return matcher.find() ? query.indexOf('{', matcher.end()) : -1;
    }

    private Map<String, GraphqlField> selectionFields(String text, Map<String, Object> variables) {
        Map<String, GraphqlField> fields = new LinkedHashMap<>();
        int index = 0;
        while (index < text.length()) {
            index = skipWhitespace(text, index);
            if (index >= text.length()) {
                break;
            }
            char current = text.charAt(index);
            if (!Character.isJavaIdentifierStart(current)) {
                index++;
                continue;
            }
            int wordEnd = index + 1;
            while (wordEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(wordEnd))) {
                wordEnd++;
            }
            String responseName = text.substring(index, wordEnd);
            String fieldName = responseName;
            index = skipWhitespace(text, wordEnd);
            if (index < text.length() && text.charAt(index) == ':') {
                index = skipWhitespace(text, index + 1);
                int aliasFieldEnd = index + 1;
                while (aliasFieldEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(aliasFieldEnd))) {
                    aliasFieldEnd++;
                }
                fieldName = text.substring(index, aliasFieldEnd);
                index = skipWhitespace(text, aliasFieldEnd);
            }
            Map<String, String> args = Map.of();
            if (index < text.length() && text.charAt(index) == '(') {
                int argumentEnd = matching(text, index, '(', ')');
                args = argumentEnd > index ? arguments(text.substring(index + 1, argumentEnd), variables) : Map.of();
                index = argumentEnd > index ? argumentEnd + 1 : wordEnd;
            }
            index = skipWhitespace(text, index);
            Map<String, GraphqlField> children = Map.of();
            if (index < text.length() && text.charAt(index) == '{') {
                int childEnd = matching(text, index, '{', '}');
                children = childEnd > index ? selectionFields(text.substring(index + 1, childEnd), variables) : Map.of();
                index = childEnd > index ? childEnd + 1 : index + 1;
            }
            fields.putIfAbsent(responseName, new GraphqlField(fieldName, args, children));
        }
        return fields;
    }

    private Map<String, Object> project(Map<String, Object> dto, Map<String, GraphqlField> selectedFields) {
        if (dto == null) {
            return null;
        }
        if (selectedFields == null || selectedFields.isEmpty()) {
            return dto;
        }
        Map<String, Object> selected = new LinkedHashMap<>();
        selectedFields.forEach((responseName, field) -> {
            if (dto.containsKey(field.fieldName())) {
                selected.put(responseName, projectValue(dto.get(field.fieldName()), field.children()));
            }
        });
        return selected;
    }

    private Object projectValue(Object value, Map<String, GraphqlField> selectedFields) {
        if (selectedFields == null || selectedFields.isEmpty()) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> stringMap = new LinkedHashMap<>();
            map.forEach((key, item) -> stringMap.put(String.valueOf(key), item));
            return project(stringMap, selectedFields);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> projectValue(item, selectedFields))
                    .toList();
        }
        return value;
    }

    private GraphqlField childByFieldName(Map<String, GraphqlField> children, String fieldName) {
        return children.values().stream()
                .filter(child -> fieldName.equals(child.fieldName()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> arguments(String argumentText, Map<String, Object> variables) {
        Map<String, String> args = new HashMap<>();
        Matcher argMatcher = ARGUMENT_PATTERN.matcher(argumentText);
        while (argMatcher.find()) {
            args.put(argMatcher.group(1), argumentValue(argMatcher.group(2), variables));
        }
        return args;
    }

    private String argumentValue(String value, Map<String, Object> variables) {
        String safeValue = safe(value).trim();
        if (!safeValue.startsWith("$")) {
            return unquote(safeValue);
        }
        return stringValue(variables.get(safeValue.substring(1)));
    }

    private int matching(String text, int openIndex, char open, char close) {
        int depth = 0;
        for (int index = openIndex; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == open) {
                depth++;
            } else if (current == close && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private int skipWhitespace(String text, int index) {
        int cursor = index;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private boolean matchesText(String actual, String expected) {
        return expected == null || equalsText(actual, expected);
    }

    private boolean matchesLong(Long actual, String expected) {
        return expected == null || Objects.equals(actual, longArg(expected));
    }

    private boolean matchesLong(long actual, String expected) {
        return expected == null || actual == longArg(expected);
    }

    private boolean matchesInteger(Integer actual, String expected) {
        return expected == null || Objects.equals(actual, integerArg(expected));
    }

    private Integer integerArg(String value) {
        return integerArg(value, null);
    }

    private Integer integerArg(String value, Integer fallback) {
        try {
            return value == null ? fallback : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Long longArg(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean booleanArg(String value) {
        return Boolean.parseBoolean(safe(value));
    }

    private boolean equalsText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String unquote(String value) {
        String safeValue = safe(value).trim();
        return safeValue.startsWith("\"") && safeValue.endsWith("\"")
                ? safeValue.substring(1, safeValue.length() - 1)
                : safeValue;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private interface FieldResolver {
        Object resolve();
    }

    private record GraphqlField(String fieldName, Map<String, String> args, Map<String, GraphqlField> children) {
    }

    public static class GraphqlRequest {
        public String query;
        public Map<String, Object> variables;
        public String operationName;
    }
}
