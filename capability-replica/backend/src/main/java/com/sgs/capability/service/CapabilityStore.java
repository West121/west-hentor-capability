package com.sgs.capability.service;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Local replica store backed by the imported SQL Server database. */
@Service
public class CapabilityStore {
    private static final String ABILITY_ENTITY = "SgsMineral.CapabilityTable.AbilityTables.Ability";
    private static final String PRODUCTION_ABILITY_ENTITY = "SgsMineral.CapabilityTable.AbilityTables.AbilityTable";
    private static final String TENANT_ENTITY = "SgsMineral.CapabilityTable.MultiTenancy.Tenant";
    private static final String USER_ENTITY = "SgsMineral.CapabilityTable.Authorization.Users.User";
    private static final String LAB_ENTITY = "SgsMineral.CapabilityTable.Laboratories.Laboratory";
    private static final String SAMPLE_ENTITY = "SgsMineral.CapabilityTable.Samples.Sample";
    private static final String SUBCONTRACT_ABILITY_ENTITY = "SgsMineral.CapabilityTable.AbilityTables.SubcontractAbility";
    private static final String SETTING_DEFAULT_LANGUAGE_NAME = "Abp.Localization.DefaultLanguageName";
    private static final String SETTING_ABILITY_DESCRIPTION = "Ability.Description";
    private static final String SETTING_REPLICA_HOST_SETTINGS = "Replica.HostSettings";
    private static final String SETTING_REPLICA_TENANT_SETTINGS = "Replica.TenantSettings";
    private static final String SETTING_REPLICA_NOTIFICATION_RECEIVE = "Replica.Notification.ReceiveNotifications";
    private static final String SETTING_REPLICA_UI_THEMES = "Replica.UiThemes";
    private static final String SETTING_REPLICA_ACTIVE_UI_THEME = "Replica.ActiveUiTheme";
    private static final String SETTING_REPLICA_DASHBOARDS = "Replica.DashboardCustomizations";
    private static final String SETTING_REPLICA_INSTALL = "Replica.InstallSettings";
    private static final String SETTING_REPLICA_RECURRING_PAYMENTS = "Replica.RecurringPaymentsEnabled";
    private static final String REPLICA_PASSWORD_PREFIX = "{replica}";
    // 原 SubscriptionPaymentType 枚举：Manual=0, RecurringAutomatic=1, RecurringManual=2。
    private static final int SUBSCRIPTION_RECURRING_AUTOMATIC = 1;
    private static final int SUBSCRIPTION_RECURRING_MANUAL = 2;
    private static final List<String> PRODUCTION_BUSINESS_LINES = List.of(
            "NF", "SIR", "CHEM", "EMS", "OGC", "General & XRD", "Lab Group");
    private static final Set<String> LAB_GROUP_ONLY_ABILITY_PROPERTIES = Set.of(
            "standardNoSgs", "standardNoSop", "standardNoOthers", "standardNoDz");
    private static final List<String> PRODUCTION_USER_ORDER = List.of(
            "admin",
            "Davis_Cheng",
            "Demi_Feng",
            "Jack_Fu",
            "Larry_Liu",
            "Lewis_Wang",
            "Polo_Yan_admin",
            "aster_cai",
            "maggie_che",
            "Jack-c_chen");
    private static final List<String> TENANT_ADMIN_MENU_PERMISSIONS = List.of(
            "Pages.AbilityManagement",
            "Pages.AbilityManagement.Ability",
            "Pages.AbilityManagement.Ability.Create",
            "Pages.AbilityManagement.Ability.Edit",
            "Pages.AbilityManagement.Ability.PublicEdit",
            "Pages.AbilityManagement.Ability.Delete",
            "Pages.AbilityManagement.Ability.DeleteAll",
            "Pages.AbilityManagement.Ability.ImportExcel",
            "Pages.AbilityManagement.Ability.History",
            "Pages.AbilityManagement.EditDesc",
            "Pages.AbilityManagement.Sample",
            "Pages.AbilityQuery",
            "Pages.Administration",
            "Pages.Administration.OrganizationUnits",
            "Pages.Administration.Roles",
            "Pages.Administration.Users",
            "Pages.AbilityManagement.AbilitySetting",
            "Pages.Administration.Laboratory",
            "Pages.Administration.StandardUpdate",
            "Pages.Log",
            "Pages.Log.AbilityHistory",
            "Pages.Administration.AuditLogs");
    private static final Map<String, String> REQUIRED_ABILITY_FIELDS = Map.ofEntries(
            Map.entry("typeName", "类型"),
            Map.entry("samplingName", "样品名称"),
            Map.entry("testItem", "测试项目"),
            Map.entry("methodName", "方法中文描述"),
            Map.entry("methodEngName", "方法英文描述"),
            Map.entry("standardNo", "标准编号"),
            Map.entry("cycleWorkingDay", "检测周期/工作日"),
            Map.entry("massRequired", "所需样品量"),
            Map.entry("sizeRequired", "样品粒度要求"),
            Map.entry("detectionLimit", "适用范围"),
            Map.entry("price", "价格/CNY"));
    private final Map<UUID, Ability> abilities = new ConcurrentHashMap<>();
    private final Map<UUID, Laboratory> labs = new ConcurrentHashMap<>();
    private final Map<UUID, SampleType> sampleTypes = new ConcurrentHashMap<>();
    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();
    private final Map<Long, OrgAbilitySetting> orgSettings = new ConcurrentHashMap<>();
    private final Map<UUID, SubcontractAbility> subcontractAbilities = new ConcurrentHashMap<>();
    private final Map<UUID, FavoriteGroup> favorites = new ConcurrentHashMap<>();
    private final Map<Long, Set<UUID>> defaultFavoriteAbilityIdsByUser = new ConcurrentHashMap<>();
    private final Map<Integer, RoleItem> roles = new ConcurrentHashMap<>();
    private final Map<Long, UserItem> users = new ConcurrentHashMap<>();
    private final Map<Long, String> userPasswords = new ConcurrentHashMap<>();
    private final Map<String, String> profilePictures = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> userSpecificPermissions = new ConcurrentHashMap<>();
    private final Map<Long, UserDelegation> userDelegations = new ConcurrentHashMap<>();
    private final List<UserLoginAttemptItem> userLoginAttempts = new ArrayList<>();
    private final Map<String, FriendItem> friendships = new ConcurrentHashMap<>();
    private final Map<Long, ChatMessageItem> chatMessages = new ConcurrentHashMap<>();
    private final Map<String, FeatureItem> features = new ConcurrentHashMap<>();
    private final Map<Integer, EditionItem> editions = new ConcurrentHashMap<>();
    private final Map<Integer, TenantItem> tenants = new ConcurrentHashMap<>();
    private final Map<Long, SubscriptionPaymentItem> subscriptionPayments = new ConcurrentHashMap<>();
    private final Map<Long, InvoiceItem> invoices = new ConcurrentHashMap<>();
    private final Map<Integer, LanguageItem> languages = new ConcurrentHashMap<>();
    private final List<LanguageTextItem> languageTexts = new ArrayList<>();
    private final Map<UUID, NotificationItem> notifications = new ConcurrentHashMap<>();
    private final Map<Long, NotificationSettings> notificationSettings = new ConcurrentHashMap<>();
    private final Map<String, CacheItem> caches = new ConcurrentHashMap<>();
    private final Map<Integer, DynamicParameterItem> dynamicParameters = new ConcurrentHashMap<>();
    private final Map<Integer, DynamicParameterValueItem> dynamicParameterValues = new ConcurrentHashMap<>();
    private final Map<Integer, EntityDynamicParameterItem> entityDynamicParameters = new ConcurrentHashMap<>();
    private final Map<Integer, EntityDynamicParameterValueItem> entityDynamicParameterValues = new ConcurrentHashMap<>();
    private final Map<UUID, WebhookSubscriptionItem> webhookSubscriptions = new ConcurrentHashMap<>();
    private final Map<UUID, WebhookEventItem> webhookEvents = new ConcurrentHashMap<>();
    private final Map<UUID, WebhookSendAttemptItem> webhookSendAttempts = new ConcurrentHashMap<>();
    private final Map<String, ThemeSettingsItem> uiThemes = new ConcurrentHashMap<>();
    private final Map<String, DashboardCustomizationItem> dashboardCustomizations = new ConcurrentHashMap<>();
    private InstallSettingsItem installSettings = InstallSettingsItem.defaults();
    private SystemSettingsItem.HostSettings hostSettings = SystemSettingsItem.defaultHostSettings();
    private SystemSettingsItem.TenantSettings tenantSettings = SystemSettingsItem.defaultTenantSettings();
    private final Map<Integer, SystemSettingsItem.TenantSettings> tenantSettingsByTenant = new ConcurrentHashMap<>();
    private SystemSettingsItem.AbilitySettings abilitySettings = SystemSettingsItem.defaultAbilitySettings();
    private boolean recurringPaymentsEnabled = true;
    private final List<PermissionItem> permissions = new ArrayList<>();
    private final List<OrganizationUnit> orgUnits = new ArrayList<>();
    private final List<AbilityHistoryItem> history = new ArrayList<>();
    private final List<AuditLog> auditLogs = new ArrayList<>();
    private final List<EntityChangeItem> entityChanges = new ArrayList<>();
    private final List<EntityPropertyChangeItem> entityPropertyChanges = new ArrayList<>();
    private String activeUiTheme = "default";
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path snapshotPath;
    private final boolean databaseStoreMode;
    private boolean loadingDatabaseState;

    public CapabilityStore(ObjectMapper objectMapper,
                           JdbcTemplate jdbcTemplate,
                           @Value("${replica.store.mode:database}") String storeMode) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.snapshotPath = Path.of("data", "disabled-snapshot.json");
        this.databaseStoreMode = equalsText(storeMode, "database");
        if (!databaseStoreMode) {
            throw new IllegalStateException("Only SQL Server database store mode is supported.");
        }
        loadDatabaseState();
    }

    /** Loads the replica from the imported production SQL Server database. */
    private void loadDatabaseState() {
        try {
            clearState();
            loadingDatabaseState = true;
            try {
                seed();
                applyProductionDatabaseRows();
                ensureUserPasswords();
                ensurePlatformPermissions();
                ensureTenantAdminMenuPermissions();
                ensureLanguageData();
                ensureNotificationData();
                ensureCacheData();
                ensureChatData();
                ensureDynamicParameterData();
                ensureWebhookData();
                ensureUiCustomizationData();
                ensureTenantPlatformData();
                ensureAccountSecurityData();
                ensureOrganizationUnitData();
                ensureProductionBusinessLineData();
                ensureAuditHistoryData();
                ensureAbilityDescriptionData();
            } finally {
                loadingDatabaseState = false;
            }
        } catch (DataAccessException ex) {
            throw new IllegalStateException("Failed to load production database state. Start SQL Server and import sgsmineralscapability.sql first.", ex);
        }
    }

    /** Legacy snapshot loader retained for non-database mode, which is disabled by the constructor. */
    private boolean loadSnapshot() {
        if (!Files.exists(snapshotPath)) {
            return false;
        }
        try {
            StoreSnapshot snapshot = objectMapper.readValue(snapshotPath.toFile(), StoreSnapshot.class);
            clearState();
            list(snapshot.abilities).forEach(item -> abilities.put(item.id, item));
            list(snapshot.labs).forEach(item -> labs.put(item.id, item));
            list(snapshot.sampleTypes).forEach(item -> sampleTypes.put(item.id, item));
            list(snapshot.samples).forEach(item -> samples.put(item.id, item));
            list(snapshot.orgSettings).forEach(item -> orgSettings.put(item.orgId, item));
            list(snapshot.subcontractAbilities).forEach(item -> subcontractAbilities.put(item.id, item));
            list(snapshot.favorites).forEach(item -> favorites.put(item.id, item));
            if (snapshot.defaultFavoriteAbilityIdsByUser != null) {
                snapshot.defaultFavoriteAbilityIdsByUser.forEach((userId, abilityIds) ->
                        defaultFavoriteAbilityIds(userId).addAll(list(abilityIds)));
            }
            defaultFavoriteAbilityIds(1L).addAll(list(snapshot.defaultFavoriteAbilityIds));
            list(snapshot.roles).forEach(item -> roles.put(item.id, item));
            list(snapshot.users).forEach(item -> users.put(item.id, item));
            if (snapshot.userPasswords != null) {
                userPasswords.putAll(snapshot.userPasswords);
            }
            if (snapshot.profilePictures != null) {
                profilePictures.putAll(snapshot.profilePictures);
            }
            if (snapshot.userSpecificPermissions != null) {
                snapshot.userSpecificPermissions.forEach((userId, values) ->
                        userSpecificPermissions.put(userId, new ArrayList<>(values == null ? List.of() : values)));
            }
            list(snapshot.userDelegations).forEach(item -> userDelegations.put(item.id, item));
            userLoginAttempts.addAll(list(snapshot.userLoginAttempts));
            list(snapshot.friendships).forEach(item -> friendships.put(friendshipKey(item.userId, item.tenantId,
                    item.friendUserId, item.friendTenantId), item));
            list(snapshot.chatMessages).forEach(item -> chatMessages.put(item.id, item));
            list(snapshot.features).forEach(item -> features.put(safe(item.name), item));
            list(snapshot.editions).forEach(item -> editions.put(item.id, item));
            list(snapshot.tenants).forEach(item -> tenants.put(item.id, item));
            list(snapshot.subscriptionPayments).forEach(item -> subscriptionPayments.put(item.id, item));
            list(snapshot.invoices).forEach(item -> invoices.put(item.id, item));
            list(snapshot.languages).forEach(item -> languages.put(item.id, item));
            languageTexts.addAll(list(snapshot.languageTexts));
            list(snapshot.notifications).forEach(item -> notifications.put(item.id, item));
            list(snapshot.notificationSettings).forEach(item -> notificationSettings.put(item.userId, item));
            list(snapshot.caches).forEach(item -> caches.put(item.name, item));
            list(snapshot.dynamicParameters).forEach(item -> dynamicParameters.put(item.id, item));
            list(snapshot.dynamicParameterValues).forEach(item -> dynamicParameterValues.put(item.id, item));
            list(snapshot.entityDynamicParameters).forEach(item -> entityDynamicParameters.put(item.id, item));
            list(snapshot.entityDynamicParameterValues).forEach(item -> entityDynamicParameterValues.put(item.id, item));
            list(snapshot.webhookSubscriptions).forEach(item -> webhookSubscriptions.put(item.id, item));
            list(snapshot.webhookEvents).forEach(item -> webhookEvents.put(item.id, item));
            list(snapshot.webhookSendAttempts).forEach(item -> webhookSendAttempts.put(item.id, item));
            list(snapshot.uiThemes).forEach(item -> uiThemes.put(safe(item.theme), item));
            list(snapshot.dashboardCustomizations).forEach(item ->
                    dashboardCustomizations.put(dashboardKey(item.application, item.dashboardName), item));
            activeUiTheme = safe(snapshot.activeUiTheme).isBlank() ? "default" : snapshot.activeUiTheme;
            recurringPaymentsEnabled = snapshot.recurringPaymentsEnabled;
            installSettings = normalizeInstallSettings(snapshot.installSettings);
            hostSettings = normalizeHostSettings(snapshot.hostSettings);
            tenantSettings = normalizeTenantSettings(snapshot.tenantSettings);
            if (snapshot.tenantSettingsByTenant != null) {
                snapshot.tenantSettingsByTenant.forEach((tenantId, settings) ->
                        tenantSettingsByTenant.put(tenantId, normalizeTenantSettings(settings)));
            }
            tenantSettingsByTenant.putIfAbsent(1, tenantSettings);
            tenantSettings = tenantSettingsByTenant.get(1);
            abilitySettings = snapshot.abilitySettings == null
                    ? SystemSettingsItem.defaultAbilitySettings()
                    : snapshot.abilitySettings;
            permissions.addAll(list(snapshot.permissions));
            orgUnits.addAll(list(snapshot.orgUnits));
            history.addAll(list(snapshot.history));
            auditLogs.addAll(list(snapshot.auditLogs));
            entityChanges.addAll(list(snapshot.entityChanges));
            entityPropertyChanges.addAll(list(snapshot.entityPropertyChanges));
            ensureUserPasswords();
            boolean changed = ensurePlatformPermissions() | ensureLanguageData() | ensureNotificationData()
                    | ensureCacheData() | ensureChatData() | ensureDynamicParameterData() | ensureWebhookData()
                    | ensureUiCustomizationData() | ensureTenantPlatformData() | ensureAccountSecurityData()
                    | ensureOrganizationUnitData() | ensureProductionBusinessLineData() | ensureAuditHistoryData()
                    | ensureAbilityDescriptionData();
            if (changed) {
                persist();
            }
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load replica snapshot: " + snapshotPath, ex);
        }
    }

    /** Database mode persists through SQL Server write paths; the legacy JSON snapshot is disabled. */
    private synchronized void persist() {
        if (databaseStoreMode) {
            return;
        }
        try {
            Path parent = snapshotPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(snapshotPath.toFile(), snapshot());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save replica snapshot: " + snapshotPath, ex);
        }
    }

    private void applyProductionDatabaseRows() {
        List<Map<String, Object>> orgRows = dbRows("SgsOrganizationUnits");
        List<Map<String, Object>> labRows = dbRows("MineralLaboratory");
        List<Map<String, Object>> roleRows = dbRows("SgsRoles");
        List<Map<String, Object>> userRows = dbRows("SgsUsers");
        List<Map<String, Object>> userRoleRows = dbRows("SgsUserRoles");
        List<Map<String, Object>> userOrgRows = dbRows("SgsUserOrganizationUnits");
        List<Map<String, Object>> labUserRows = dbRows("MineralLaboratoryUser");
        List<Map<String, Object>> orgRoleRows = dbRows("SgsOrganizationUnitRoles");
        List<Map<String, Object>> permissionRows = dbRows("SgsPermissions");

        Map<Long, List<String>> userRoleNames = new HashMap<>();
        Map<Long, List<Long>> userOrgIds = new HashMap<>();
        Map<Long, List<UUID>> userLabIds = new HashMap<>();
        Map<Integer, List<Long>> roleOrgIds = new HashMap<>();
        Map<Long, List<String>> importedUserPermissions = new HashMap<>();

        roles.clear();
        roleRows.stream().filter(this::notDeleted).filter(this::defaultTenant).forEach(row -> {
            RoleItem role = new RoleItem();
            role.id = dbInteger(row, "Id");
            role.name = dbString(row, "Name");
            role.displayName = dbString(row, "DisplayName");
            role.isStatic = dbBoolean(row, "IsStatic");
            role.isDefault = dbBoolean(row, "IsDefault");
            role.creationTime = dbLocalDateTime(row, "CreationTime");
            roles.put(role.id, role);
        });
        Map<Integer, String> roleNameById = roles.values().stream()
                .collect(Collectors.toMap(role -> role.id, role -> safe(role.name), (left, right) -> left));

        for (Map<String, Object> row : userRoleRows) {
            if (!defaultTenant(row)) {
                continue;
            }
            Long userId = dbLong(row, "UserId");
            Integer roleId = dbInteger(row, "RoleId");
            String roleName = roleNameById.get(roleId);
            if (userId != null && !safe(roleName).isBlank()) {
                addMapValue(userRoleNames, userId, roleName);
            }
        }
        for (Map<String, Object> row : userOrgRows) {
            if (!notDeleted(row) || !defaultTenant(row)) {
                continue;
            }
            Long userId = dbLong(row, "UserId");
            Long orgId = dbLong(row, "OrganizationUnitId");
            if (userId != null && orgId != null) {
                addMapValue(userOrgIds, userId, orgId);
            }
        }
        for (Map<String, Object> row : labUserRows) {
            if (!notDeleted(row)) {
                continue;
            }
            Long userId = dbLong(row, "UserId");
            UUID labId = dbUuid(row, "LabId");
            if (userId != null && labId != null) {
                addMapValue(userLabIds, userId, labId);
            }
        }
        for (Map<String, Object> row : orgRoleRows) {
            if (!notDeleted(row) || !defaultTenant(row)) {
                continue;
            }
            Integer roleId = dbInteger(row, "RoleId");
            Long orgId = dbLong(row, "OrganizationUnitId");
            if (roleId != null && orgId != null) {
                addMapValue(roleOrgIds, roleId, orgId);
            }
        }

        Set<String> permissionNames = permissions.stream().map(item -> item.name).collect(Collectors.toCollection(TreeSet::new));
        for (Map<String, Object> row : permissionRows) {
            if (!defaultTenant(row) || !dbBoolean(row, "IsGranted")) {
                continue;
            }
            String name = dbString(row, "Name");
            if (safe(name).isBlank()) {
                continue;
            }
            Integer roleId = dbInteger(row, "RoleId");
            RoleItem role = roleId == null ? null : roles.get(roleId);
            if (role != null && !role.grantedPermissionNames.contains(name)) {
                role.grantedPermissionNames.add(name);
            }
            Long userId = dbLong(row, "UserId");
            if (userId != null) {
                addMapValue(importedUserPermissions, userId, name);
            }
            if (permissionNames.add(name)) {
                PermissionItem permission = new PermissionItem();
                permission.name = name;
                permission.displayName = name;
                permission.parentName = dbParentPermissionName(name);
                permission.level = Math.max(name.split("\\.").length - 1, 0);
                permissions.add(permission);
            }
        }
        roles.values().forEach(role -> {
            role.grantedPermissionNames = uniqueStrings(role.grantedPermissionNames);
            role.organizationUnits = uniqueLongs(roleOrgIds.get(role.id));
        });

        labs.clear();
        labRows.stream().filter(this::notDeleted).forEach(row -> {
            Laboratory lab = new Laboratory();
            lab.id = dbUuid(row, "Id");
            lab.code = dbString(row, "Code");
            lab.name = dbString(row, "Name");
            lab.engName = dbNullableString(row, "EngName");
            lab.describe = dbNullableString(row, "Describe");
            lab.leader = dbNullableString(row, "Leader");
            lab.contactInfo = dbNullableString(row, "ContactInfo");
            lab.address = dbNullableString(row, "Address");
            lab.hasCnas = dbBoolean(row, "HasCnas");
            lab.hasCms = dbBoolean(row, "HasCms");
            labs.put(lab.id, lab);
        });

        users.clear();
        userPasswords.clear();
        userSpecificPermissions.clear();
        userRows.stream().filter(this::notDeleted).filter(this::defaultTenant).forEach(row -> {
            UserItem user = new UserItem();
            user.id = dbLong(row, "Id");
            user.name = dbString(row, "Name");
            user.surname = dbNullableString(row, "Surname");
            user.userName = dbString(row, "UserName");
            user.emailAddress = dbString(row, "EmailAddress");
            user.phoneNumber = dbNullableString(row, "PhoneNumber");
            user.profilePictureId = dbUuid(row, "ProfilePictureId");
            user.isEmailConfirmed = dbBoolean(row, "IsEmailConfirmed");
            user.isPhoneNumberConfirmed = dbBoolean(row, "IsPhoneNumberConfirmed");
            user.isActive = dbBoolean(row, "IsActive");
            user.shouldChangePasswordOnNextLogin = dbBoolean(row, "ShouldChangePasswordOnNextLogin");
            user.isTwoFactorEnabled = equalsText(user.userName, "admin") ? false : dbBoolean(row, "IsTwoFactorEnabled");
            user.isLockoutEnabled = dbBoolean(row, "IsLockoutEnabled");
            user.engName = dbNullableString(row, "EngName");
            user.preferredLanguageName = "zh-Hans";
            user.googleAuthenticatorKey = dbNullableString(row, "GoogleAuthenticatorKey");
            user.signInToken = dbNullableString(row, "SignInToken");
            user.creationTime = dbLocalDateTime(row, "CreationTime");
            user.passwordResetCode = dbNullableString(row, "PasswordResetCode");
            user.emailConfirmationCode = dbNullableString(row, "EmailConfirmationCode");
            user.assignedRoleNames = uniqueStrings(userRoleNames.get(user.id));
            user.organizationUnits = uniqueLongs(userOrgIds.get(user.id));
            user.labs = uniqueUuids(userLabIds.get(user.id));
            users.put(user.id, user);
            userPasswords.put(user.id, databaseReplicaPassword(dbNullableString(row, "Password")).orElse("123qwe"));
            List<String> permissionList = importedUserPermissions.get(user.id);
            if (permissionList != null) {
                userSpecificPermissions.put(user.id, uniqueStrings(permissionList));
            }
        });
        loadDatabaseAccountSocialData();

        orgUnits.clear();
        orgRows.stream().filter(this::notDeleted).filter(this::defaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .forEach(row -> {
                    OrganizationUnit org = new OrganizationUnit();
                    org.id = Optional.ofNullable(dbLong(row, "Id")).orElse(0L);
                    org.parentId = dbLong(row, "ParentId");
                    org.code = dbString(row, "Code");
                    org.displayName = safe(dbString(row, "DisplayName")).trim();
                    org.memberCount = (int) users.values().stream().filter(user -> user.organizationUnits.contains(org.id)).count();
                    org.roleCount = (int) roles.values().stream().filter(role -> role.organizationUnits.contains(org.id)).count();
                    orgUnits.add(org);
                });

        sampleTypes.clear();
        dbRows("MineralSampleType").stream().filter(this::notDeleted).forEach(row -> {
            SampleType type = new SampleType();
            type.id = dbUuid(row, "Id");
            type.displayName = dbString(row, "DisplayName");
            type.orgId = Optional.ofNullable(dbLong(row, "OrgId")).orElse(0L);
            type.orgName = safe(dbString(row, "OrgName")).trim();
            sampleTypes.put(type.id, type);
        });

        samples.clear();
        dbRows("MineralSample").stream().filter(this::notDeleted).forEach(row -> {
            Sample sample = new Sample();
            sample.id = dbUuid(row, "Id");
            sample.displayName = dbString(row, "DisplayName");
            sample.engName = dbNullableString(row, "EngName");
            sample.alias = dbNullableString(row, "Alias");
            sample.typeId = dbUuid(row, "TypeId");
            sample.typeName = dbNullableString(row, "TypeName");
            samples.put(sample.id, sample);
        });

        abilities.clear();
        dbRows("MineralAbilityTable").stream().filter(this::notDeleted).forEach(row -> {
            Ability ability = new Ability();
            ability.id = dbUuid(row, "Id");
            ability.creationTime = dbDateTime(row, "CreationTime");
            ability.orgName = dbTrimmedNullableString(row, "OrgName");
            ability.orgId = dbLong(row, "OrgId");
            ability.typeName = dbNullableString(row, "TypeName");
            ability.typeId = dbUuid(row, "TypeId");
            ability.samplingName = dbNullableString(row, "SamplingName");
            ability.samplingId = dbUuid(row, "SamplingId");
            ability.productCode = dbNullableString(row, "ProductCode");
            ability.testItem = dbNullableString(row, "TestItem");
            ability.testItemRemark = dbNullableString(row, "TestItemRemark");
            ability.methodName = dbNullableString(row, "MethodName");
            ability.methodRemark = dbNullableString(row, "MethodRemark");
            ability.methodEngName = dbNullableString(row, "MethodEngName");
            ability.gbNo = dbNullableString(row, "GbNo");
            ability.gbRemark = dbNullableString(row, "GbRemark");
            ability.isoNo = dbNullableString(row, "IsoNo");
            ability.isoRemark = dbNullableString(row, "IsoRemark");
            ability.gbtNo = dbNullableString(row, "GbtNo");
            ability.gbtRemark = dbNullableString(row, "GbtRemark");
            ability.astmNo = dbNullableString(row, "AstmNo");
            ability.astmRemark = dbNullableString(row, "AstmRemark");
            ability.industryStandardNo = dbNullableString(row, "IndustryStandardNo");
            ability.industryStandardRemark = dbNullableString(row, "IndustryStandardRemark");
            ability.otherNo = dbNullableString(row, "OtherNo");
            ability.otherRemark = dbNullableString(row, "OtherRemark");
            ability.standardNo = dbNullableString(row, "StandardNo");
            ability.cycleWorkingDay = dbNullableString(row, "CycleWorkingDay");
            ability.testTime = dbNullableString(row, "TestTime");
            ability.testTimeRemark = dbNullableString(row, "TestTimeRemark");
            ability.massRequired = dbNullableString(row, "MassRequired");
            ability.massRequiredRemark = dbNullableString(row, "MassRequiredRemark");
            ability.sizeRequired = dbNullableString(row, "SizeRequired");
            ability.sizeRequiredRemark = dbNullableString(row, "SizeRequiredRemark");
            ability.detectionLimit = dbNullableString(row, "DetectionLimit");
            ability.price = dbNullableString(row, "Price");
            ability.priceRemark = dbNullableString(row, "PriceRemark");
            ability.remark = dbNullableString(row, "Remark");
            ability.standardNoSgs = dbNullableString(row, "StandardNoSgs");
            ability.standardNoSop = dbNullableString(row, "StandardNoSop");
            ability.standardNoOthers = dbNullableString(row, "StandardNoOthers");
            ability.standardNoDz = dbNullableString(row, "StandardNoDz");
            ability.labAbilities = parseDbLabAbilities(dbString(row, "LabAbility"));
            abilities.put(ability.id, ability);
        });

        subcontractAbilities.clear();
        dbRows("MineralSubcontractAbility").stream().filter(this::notDeleted).forEach(row -> {
            SubcontractAbility item = new SubcontractAbility();
            item.id = dbUuid(row, "Id");
            item.labName = dbNullableString(row, "LabName");
            item.contactDetails = dbNullableString(row, "ContactDetails");
            item.testCategory = dbNullableString(row, "TestCategory");
            item.cmaOrCnas = dbNullableString(row, "CmaOrCnas");
            item.gist = dbNullableString(row, "Gist");
            item.appraiser = dbNullableString(row, "Appraiser");
            item.evaluationResult = dbNullableString(row, "EvaluationResult");
            subcontractAbilities.put(item.id, item);
        });

        history.clear();
        auditLogs.clear();
        orgSettings.clear();
        dbRows("OrgAbilityPropertySettings").forEach(row -> {
            OrgAbilitySetting setting = new OrgAbilitySetting();
            setting.orgId = Optional.ofNullable(dbLong(row, "OrgId")).orElse(0L);
            setting.propertyName = splitDbCsv(dbString(row, "Properties"));
            setting.lab = splitDbCsv(dbString(row, "Labs"));
            setting.isPublic = dbBoolean(row, "IsPublic");
            setting.description = Optional.ofNullable(dbNullableString(row, "Description")).orElse("");
            orgSettings.put(setting.orgId, setting);
        });

        loadDatabaseTenantPlatform();
        loadDatabaseTenantBinaries();
        loadDatabaseFavorites();
        loadDatabaseLanguages();
        loadDatabaseDynamicParameters();
        loadDatabaseNotifications();
        loadDatabaseWebhooks();
        loadDatabaseSettings();
        // History rows are read from SQL Server on demand in database mode. Loading
        // SgsEntityPropertyChanges at startup is too expensive on small servers.
    }

    private void loadDatabaseTenantPlatform() {
        editions.clear();
        tenants.clear();
        subscriptionPayments.clear();
        invoices.clear();

        List<Map<String, Object>> featureRows = dbRows("SgsFeatures");
        for (Map<String, Object> row : featureRows) {
            String name = dbNullableString(row, "Name");
            if (!safe(name).isBlank() && !features.containsKey(name)) {
                FeatureItem item = feature("Edition", name, name, "", dbString(row, "Value"), "SINGLE_LINE_STRING");
                features.put(name, item);
            }
        }

        dbRows("SgsEditions").stream()
                .filter(this::notDeleted)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .forEach(row -> {
                    EditionItem item = new EditionItem();
                    item.id = dbInteger(row, "Id");
                    item.name = dbNullableString(row, "Name");
                    item.displayName = dbNullableString(row, "DisplayName");
                    item.dailyPrice = dbBigDecimal(row, "DailyPrice");
                    item.weeklyPrice = dbBigDecimal(row, "WeeklyPrice");
                    item.monthlyPrice = dbBigDecimal(row, "MonthlyPrice");
                    item.annualPrice = dbBigDecimal(row, "AnnualPrice");
                    item.waitingDayAfterExpire = dbInteger(row, "WaitingDayAfterExpire");
                    item.trialDayCount = dbInteger(row, "TrialDayCount");
                    item.expiringEditionId = dbInteger(row, "ExpiringEditionId");
                    item.featureValues = databaseFeatureValues(featureRows, item.id, null);
                    editions.put(item.id, decorateEdition(item));
                });

        dbRows("SgsTenants").stream()
                .filter(this::notDeleted)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .forEach(row -> {
                    TenantItem item = new TenantItem();
                    item.id = dbInteger(row, "Id");
                    item.tenancyName = dbNullableString(row, "TenancyName");
                    item.name = dbNullableString(row, "Name");
                    item.connectionString = dbNullableString(row, "ConnectionString");
                    item.editionId = dbInteger(row, "EditionId");
                    item.isActive = dbBoolean(row, "IsActive");
                    item.creationTime = dbDateTime(row, "CreationTime");
                    item.subscriptionEndDateUtc = dbDateTime(row, "SubscriptionEndDateUtc");
                    item.subscriptionPaymentType = Optional.ofNullable(dbInteger(row, "SubscriptionPaymentType")).orElse(0);
                    item.isInTrialPeriod = dbBoolean(row, "IsInTrialPeriod");
                    item.logoId = dbNullableString(row, "LogoId");
                    item.logoFileType = dbNullableString(row, "LogoFileType");
                    item.customCssId = dbNullableString(row, "CustomCssId");
                    item.adminEmailAddress = "admin@" + safe(item.tenancyName).toLowerCase(Locale.ROOT) + ".local";
                    item.featureValues = databaseFeatureValues(featureRows, null, item.id);
                    tenants.put(item.id, decorateTenant(item));
                });

        dbRows("AppSubscriptionPayments").stream()
                .filter(this::notDeleted)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .forEach(row -> {
                    SubscriptionPaymentItem item = new SubscriptionPaymentItem();
                    item.id = dbLong(row, "Id");
                    item.amount = amount(dbBigDecimal(row, "Amount"));
                    item.creationTime = dbDateTime(row, "CreationTime");
                    item.dayCount = Optional.ofNullable(dbInteger(row, "DayCount")).orElse(0);
                    item.editionId = Optional.ofNullable(dbInteger(row, "EditionId")).orElse(0);
                    item.gateway = Optional.ofNullable(dbInteger(row, "Gateway")).orElse(0);
                    item.successUrl = dbNullableString(row, "SuccessUrl");
                    item.paymentPeriodType = Optional.ofNullable(dbInteger(row, "PaymentPeriodType")).orElse(0);
                    item.status = Optional.ofNullable(dbInteger(row, "Status")).orElse(0);
                    item.tenantId = Optional.ofNullable(dbInteger(row, "TenantId")).orElse(0);
                    item.invoiceNo = dbNullableString(row, "InvoiceNo");
                    item.description = dbNullableString(row, "Description");
                    item.errorUrl = dbNullableString(row, "ErrorUrl");
                    item.externalPaymentId = dbNullableString(row, "ExternalPaymentId");
                    item.isRecurring = dbBoolean(row, "IsRecurring");
                    item.editionPaymentType = Optional.ofNullable(dbInteger(row, "EditionPaymentType")).orElse(0);
                    decorateSubscriptionPayment(item);
                    subscriptionPayments.put(item.id, item);
                });

        dbRows("AppInvoices").stream()
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .forEach(row -> {
                    InvoiceItem item = new InvoiceItem();
                    item.id = dbLong(row, "Id");
                    item.invoiceDate = dbDateTime(row, "InvoiceDate");
                    item.invoiceNo = dbNullableString(row, "InvoiceNo");
                    item.tenantLegalName = dbNullableString(row, "TenantLegalName");
                    item.tenantAddress = addressLines(dbNullableString(row, "TenantAddress"));
                    item.tenantTaxNo = dbNullableString(row, "TenantTaxNo");
                    decorateInvoice(item);
                    if (item.id != null) {
                        invoices.put(item.id, item);
                    }
                });
    }

    private void loadDatabaseWebhooks() {
        webhookSubscriptions.clear();
        webhookEvents.clear();
        webhookSendAttempts.clear();

        jdbcTemplate.queryForList("""
                        SELECT Id, CreationTime, CreatorUserId, TenantId, WebhookUri, Secret, IsActive, Webhooks, Headers
                        FROM dbo.SgsWebhookSubscriptions
                        WHERE TenantId = 1 OR TenantId IS NULL
                        """).stream()
                .sorted(Comparator.comparing(row -> safe(dbDateTime(row, "CreationTime"))))
                .forEach(row -> {
                    WebhookSubscriptionItem item = new WebhookSubscriptionItem();
                    item.id = dbUuid(row, "Id");
                    item.creationTime = dbDateTime(row, "CreationTime");
                    item.webhookUri = dbNullableString(row, "WebhookUri");
                    item.secret = dbNullableString(row, "Secret");
                    item.isActive = dbBoolean(row, "IsActive");
                    item.webhooks = parseWebhookNames(dbNullableString(row, "Webhooks"));
                    item.headers = parseStringMap(dbNullableString(row, "Headers"));
                    if (item.id != null) {
                        webhookSubscriptions.put(item.id, item);
                    }
                });

        jdbcTemplate.queryForList("""
                        SELECT Id, WebhookName, Data, CreationTime, TenantId, IsDeleted
                        FROM dbo.SgsWebhookEvents
                        WHERE (TenantId = 1 OR TenantId IS NULL) AND IsDeleted = 0
                        """).stream()
                .sorted(Comparator.comparing(row -> safe(dbDateTime(row, "CreationTime"))))
                .forEach(row -> {
                    WebhookEventItem item = new WebhookEventItem();
                    item.id = dbUuid(row, "Id");
                    item.webhookName = dbNullableString(row, "WebhookName");
                    item.data = dbNullableString(row, "Data");
                    item.creationTime = dbDateTime(row, "CreationTime");
                    if (item.id != null) {
                        webhookEvents.put(item.id, item);
                    }
                });

        jdbcTemplate.queryForList("""
                        SELECT Id, WebhookEventId, WebhookSubscriptionId, Response, ResponseStatusCode,
                               CreationTime, LastModificationTime, TenantId
                        FROM dbo.SgsWebhookSendAttempts
                        WHERE TenantId = 1 OR TenantId IS NULL
                        """).stream()
                .sorted(Comparator.comparing(row -> safe(dbDateTime(row, "CreationTime"))))
                .forEach(row -> {
                    WebhookSendAttemptItem item = new WebhookSendAttemptItem();
                    item.id = dbUuid(row, "Id");
                    item.webhookEventId = dbUuid(row, "WebhookEventId");
                    item.webhookSubscriptionId = dbUuid(row, "WebhookSubscriptionId");
                    item.response = dbNullableString(row, "Response");
                    item.responseStatusCode = dbInteger(row, "ResponseStatusCode");
                    item.creationTime = dbDateTime(row, "CreationTime");
                    item.lastModificationTime = dbDateTime(row, "LastModificationTime");
                    item.retryCount = 0;
                    decorateWebhookSendAttempt(item);
                    if (item.id != null) {
                        webhookSendAttempts.put(item.id, item);
                    }
                });
    }

    private void loadDatabaseNotifications() {
        notifications.clear();
        notificationSettings.clear();

        jdbcTemplate.queryForList("""
                        SELECT un.Id AS UserNotificationId, un.UserId, un.State, un.CreationTime AS UserNotificationCreationTime,
                               tn.Id AS TenantNotificationId, tn.CreationTime, tn.Data, tn.NotificationName,
                               tn.Severity, tn.TenantId
                          FROM dbo.SgsUserNotifications un
                          INNER JOIN dbo.SgsTenantNotifications tn ON tn.Id = un.TenantNotificationId
                         WHERE (un.TenantId = 1 OR un.TenantId IS NULL)
                           AND (tn.TenantId = 1 OR tn.TenantId IS NULL)
                           AND un.State <> 2
                         ORDER BY un.CreationTime
                        """)
                .forEach(row -> {
                    NotificationItem item = new NotificationItem();
                    item.id = dbUuid(row, "UserNotificationId");
                    item.userId = dbLong(row, "UserId");
                    item.notificationName = dbNullableString(row, "NotificationName");
                    item.creationTime = dbDateTime(row, "UserNotificationCreationTime");
                    if (safe(item.creationTime).isBlank()) {
                        item.creationTime = dbDateTime(row, "CreationTime");
                    }
                    item.readState = Optional.ofNullable(dbInteger(row, "State")).orElse(0);
                    item.readTime = item.readState == 0 ? null : item.creationTime;
                    item.severity = notificationSeverityName(dbInteger(row, "Severity"));
                    applyNotificationData(item, dbNullableString(row, "Data"));
                    if (item.id != null) {
                        notifications.put(item.id, item);
                    }
                });

        List<Map<String, Object>> settingRows = dbRows("SgsSettings");
        users.keySet().forEach(userId -> {
            NotificationSettings settings = defaultNotificationSettings(userId);
            Set<String> subscribedNames = jdbcTemplate.queryForList("""
                            SELECT NotificationName
                              FROM dbo.SgsNotificationSubscriptions
                             WHERE UserId = ?
                               AND (TenantId = 1 OR TenantId IS NULL)
                               AND NotificationName IS NOT NULL
                            """, userId).stream()
                    .map(row -> dbString(row, "NotificationName"))
                    .filter(name -> !safe(name).isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (String name : subscribedNames) {
                if (settings.notifications.stream().noneMatch(item -> equalsText(item.name, name))) {
                    settings.notifications.add(notificationSubscription(name, notificationDisplayName(name)));
                }
            }
            if (!subscribedNames.isEmpty()) {
                settings.notifications.forEach(item -> item.isSubscribed = subscribedNames.contains(item.name));
                settings.receiveNotifications = settings.notifications.stream().anyMatch(item -> item.isSubscribed);
            }
            databaseSettingValue(settingRows, SETTING_REPLICA_NOTIFICATION_RECEIVE, 1, userId)
                    .or(() -> databaseSettingValue(settingRows, SETTING_REPLICA_NOTIFICATION_RECEIVE, null, userId))
                    .ifPresent(value -> {
                        settings.receiveNotifications = Boolean.parseBoolean(value);
                        if (!settings.receiveNotifications) {
                            settings.notifications.forEach(item -> item.isSubscribed = false);
                        }
                    });
            notificationSettings.put(userId, settings);
        });
    }

    private void loadDatabaseDynamicParameters() {
        dynamicParameters.clear();
        dynamicParameterValues.clear();
        entityDynamicParameters.clear();
        entityDynamicParameterValues.clear();

        dbRows("SgsDynamicParameters").stream()
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .forEach(row -> {
                    DynamicParameterItem item = new DynamicParameterItem();
                    item.id = dbInteger(row, "Id");
                    item.parameterName = dbNullableString(row, "ParameterName");
                    item.displayName = safe(item.parameterName).isBlank() ? "Parameter" + item.id : item.parameterName;
                    item.inputType = dbNullableString(row, "InputType");
                    item.permission = dbNullableString(row, "Permission");
                    if (item.id != null) {
                        dynamicParameters.put(item.id, item);
                    }
                });

        dbRows("SgsDynamicParameterValues").stream()
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .forEach(row -> {
                    DynamicParameterValueItem item = new DynamicParameterValueItem();
                    item.id = dbInteger(row, "Id");
                    item.dynamicParameterId = dbInteger(row, "DynamicParameterId");
                    item.value = dbNullableString(row, "Value");
                    if (item.id != null) {
                        dynamicParameterValues.put(item.id, decorateDynamicParameterValue(item));
                    }
                });

        dbRows("SgsEntityDynamicParameters").stream()
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .forEach(row -> {
                    EntityDynamicParameterItem item = new EntityDynamicParameterItem();
                    item.id = dbInteger(row, "Id");
                    item.entityFullName = dbNullableString(row, "EntityFullName");
                    item.dynamicParameterId = dbInteger(row, "DynamicParameterId");
                    if (item.id != null) {
                        entityDynamicParameters.put(item.id, decorateEntityDynamicParameter(item));
                    }
                });

        dbRows("SgsEntityDynamicParameterValues").stream()
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .forEach(row -> {
                    EntityDynamicParameterValueItem item = new EntityDynamicParameterValueItem();
                    item.id = dbInteger(row, "Id");
                    item.entityDynamicParameterId = dbInteger(row, "EntityDynamicParameterId");
                    item.entityId = stripJsonString(dbNullableString(row, "EntityId"));
                    item.value = dbNullableString(row, "Value");
                    if (item.id != null) {
                        entityDynamicParameterValues.put(item.id, decorateEntityDynamicParameterValue(item));
                    }
                });
    }

    private void loadDatabaseSettings() {
        List<Map<String, Object>> settingRows = dbRows("SgsSettings");
        databaseSettingValue(settingRows, SETTING_DEFAULT_LANGUAGE_NAME, 1, null)
                .or(() -> databaseSettingValue(settingRows, SETTING_DEFAULT_LANGUAGE_NAME, null, null))
                .or(() -> databaseSettingValue(settingRows, SETTING_DEFAULT_LANGUAGE_NAME, null, 1L))
                .ifPresent(this::applyDefaultLanguageName);
        databaseSettingValue(settingRows, SETTING_ABILITY_DESCRIPTION, 1, null)
                .or(() -> databaseSettingValue(settingRows, SETTING_ABILITY_DESCRIPTION, null, null))
                .ifPresent(value -> abilitySettings.description = value);
        databaseSettingValue(settingRows, SETTING_REPLICA_HOST_SETTINGS, null, null)
                .flatMap(value -> readJsonSetting(value, SystemSettingsItem.HostSettings.class))
                .ifPresent(value -> hostSettings = normalizeHostSettings(value));
        databaseSettingValue(settingRows, SETTING_REPLICA_TENANT_SETTINGS, 1, null)
                .flatMap(value -> readJsonSetting(value, SystemSettingsItem.TenantSettings.class))
                .ifPresent(value -> {
                    tenantSettingsByTenant.put(1, normalizeTenantSettings(value));
                    tenantSettings = tenantSettingsByTenant.get(1);
                });
        databaseSettingValue(settingRows, SETTING_REPLICA_UI_THEMES, 1, null)
                .or(() -> databaseSettingValue(settingRows, SETTING_REPLICA_UI_THEMES, null, null))
                .flatMap(value -> readJsonSetting(value, new TypeReference<List<ThemeSettingsItem>>() {}))
                .ifPresent(values -> {
                    uiThemes.clear();
                    values.stream().map(this::normalizeTheme).forEach(item -> uiThemes.put(item.theme, item));
                });
        databaseSettingValue(settingRows, SETTING_REPLICA_ACTIVE_UI_THEME, 1, null)
                .or(() -> databaseSettingValue(settingRows, SETTING_REPLICA_ACTIVE_UI_THEME, null, null))
                .ifPresent(value -> activeUiTheme = safe(value).isBlank() ? "default" : value);
        databaseSettingValue(settingRows, SETTING_REPLICA_DASHBOARDS, 1, null)
                .or(() -> databaseSettingValue(settingRows, SETTING_REPLICA_DASHBOARDS, null, null))
                .flatMap(value -> readJsonSetting(value, new TypeReference<List<DashboardCustomizationItem>>() {}))
                .ifPresent(values -> {
                    dashboardCustomizations.clear();
                    values.forEach(item -> dashboardCustomizations.put(
                            dashboardKey(item.application, item.dashboardName), item));
                });
        databaseSettingValue(settingRows, SETTING_REPLICA_INSTALL, null, null)
                .flatMap(value -> readJsonSetting(value, InstallSettingsItem.class))
                .ifPresent(value -> installSettings = normalizeInstallSettings(value));
        databaseSettingValue(settingRows, SETTING_REPLICA_RECURRING_PAYMENTS, 1, null)
                .or(() -> databaseSettingValue(settingRows, SETTING_REPLICA_RECURRING_PAYMENTS, null, null))
                .ifPresent(value -> recurringPaymentsEnabled = Boolean.parseBoolean(value));
        databaseSettingValue(settingRows, "Abp.Net.Mail.DefaultFromAddress", null, null)
                .ifPresent(value -> hostSettings.email.defaultFromAddress = value);
        databaseSettingValue(settingRows, "Abp.Net.Mail.DefaultFromDisplayName", null, null)
                .ifPresent(value -> hostSettings.email.defaultFromDisplayName = value);
    }

    private void loadDatabaseLanguages() {
        List<Map<String, Object>> languageRows = dbRows("SgsLanguages").stream()
                .filter(this::notDeleted)
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbInteger(row, "Id")).orElse(0)))
                .toList();
        if (!languageRows.isEmpty()) {
            languages.clear();
            languageRows.forEach(row -> {
                LanguageItem item = new LanguageItem();
                item.id = dbInteger(row, "Id");
                item.name = dbString(row, "Name");
                item.displayName = dbString(row, "DisplayName");
                item.icon = dbNullableString(row, "Icon");
                item.isDisabled = dbBoolean(row, "IsDisabled");
                item.creationTime = Optional.ofNullable(dbLocalDateTime(row, "CreationTime"))
                        .map(LocalDateTime::toString)
                        .orElse(null);
                decorateLanguage(item);
                languages.put(item.id, item);
            });
        }

        languageTexts.clear();
        dbRows("SgsLanguageTexts").stream()
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .forEach(row -> {
                    LanguageTextItem item = new LanguageTextItem();
                    item.id = intId(dbLong(row, "Id"));
                    item.sourceName = dbString(row, "Source");
                    item.languageName = dbString(row, "LanguageName");
                    item.key = dbString(row, "Key");
                    item.baseValue = "";
                    item.targetValue = dbNullableString(row, "Value");
                    languageTexts.add(item);
                });
    }

    private void applyDefaultLanguageName(String languageName) {
        String normalized = safe(languageName);
        boolean matched = false;
        for (LanguageItem language : languages.values()) {
            boolean isDefault = equalsText(language.name, normalized);
            language.isDefault = isDefault;
            matched = matched || isDefault;
        }
        if (!matched && !languages.isEmpty()) {
            languages.values().stream()
                    .min(Comparator.comparing(item -> item.id == null ? Integer.MAX_VALUE : item.id))
                    .ifPresent(item -> item.isDefault = true);
        }
    }

    private void upsertDatabaseLanguage(LanguageItem input) {
        if (input == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (input.id != null && databaseLongRowExists("SgsLanguages", input.id.longValue())) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsLanguages
                            SET DisplayName = ?,
                                Icon = ?,
                                IsDisabled = ?,
                                LastModificationTime = ?,
                                LastModifierUserId = ?,
                                Name = ?
                            WHERE Id = ?
                            """,
                    truncateForColumn(safe(input.displayName), 64),
                    truncateForColumn(safe(input.icon), 128),
                    input.isDisabled,
                    Timestamp.valueOf(now),
                    1L,
                    truncateForColumn(safe(input.name), 128),
                    input.id);
            return;
        }
        Integer id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsLanguages
                            (CreationTime, CreatorUserId, DeleterUserId, DeletionTime, DisplayName,
                             Icon, IsDeleted, LastModificationTime, LastModifierUserId, Name, TenantId, IsDisabled)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Integer.class,
                Timestamp.valueOf(now),
                1L,
                null,
                null,
                truncateForColumn(safe(input.displayName), 64),
                truncateForColumn(safe(input.icon), 128),
                false,
                null,
                null,
                truncateForColumn(safe(input.name), 128),
                null,
                input.isDisabled);
        input.id = id;
        input.creationTime = now.toString();
    }

    private void softDeleteDatabaseLanguage(Integer id) {
        if (id == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        UPDATE dbo.SgsLanguages
                        SET IsDeleted = ?,
                            DeletionTime = ?,
                            DeleterUserId = ?,
                            LastModificationTime = ?,
                            LastModifierUserId = ?
                        WHERE Id = ?
                        """,
                true,
                Timestamp.valueOf(now),
                1L,
                Timestamp.valueOf(now),
                1L,
                id);
    }

    private void deleteDatabaseLanguageTexts(String languageName) {
        if (safe(languageName).isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                        DELETE FROM dbo.SgsLanguageTexts
                        WHERE LanguageName = ? AND (TenantId IS NULL OR TenantId = 1)
                        """,
                languageName);
    }

    private void upsertDatabaseLanguageText(LanguageTextItem input) {
        if (input == null || safe(input.languageName).isBlank() || safe(input.sourceName).isBlank()
                || safe(input.key).isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<Long> id = databaseLanguageTextId(input.languageName, input.sourceName, input.key);
        if (id.isPresent()) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsLanguageTexts
                            SET Value = ?,
                                LastModificationTime = ?,
                                LastModifierUserId = ?
                            WHERE Id = ?
                            """,
                    safe(input.targetValue),
                    Timestamp.valueOf(now),
                    1L,
                    id.get());
            input.id = intId(id.get());
            return;
        }
        Long insertedId = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsLanguageTexts
                            (CreationTime, CreatorUserId, [Key], LanguageName, LastModificationTime,
                             LastModifierUserId, [Source], TenantId, Value)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.class,
                Timestamp.valueOf(now),
                1L,
                truncateForColumn(safe(input.key), 256),
                truncateForColumn(safe(input.languageName), 128),
                null,
                null,
                truncateForColumn(safe(input.sourceName), 128),
                null,
                safe(input.targetValue));
        input.id = intId(insertedId);
    }

    private Optional<Long> databaseLanguageTextId(String languageName, String sourceName, String key) {
        List<Long> ids = jdbcTemplate.queryForList("""
                        SELECT TOP 1 Id
                        FROM dbo.SgsLanguageTexts
                        WHERE LanguageName = ? AND [Source] = ? AND [Key] = ?
                          AND (TenantId IS NULL OR TenantId = 1)
                        ORDER BY CASE WHEN TenantId IS NULL THEN 0 ELSE 1 END, Id
                        """,
                Long.class,
                languageName,
                sourceName,
                key);
        return ids.stream().findFirst();
    }

    private Optional<String> databaseSettingValue(List<Map<String, Object>> rows, String name, Integer tenantId,
                                                  Long userId) {
        return rows.stream()
                .filter(row -> equalsText(dbString(row, "Name"), name))
                .filter(row -> Objects.equals(dbInteger(row, "TenantId"), tenantId))
                .filter(row -> Objects.equals(dbLong(row, "UserId"), userId))
                .map(row -> dbNullableString(row, "Value"))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private <T> Optional<T> readJsonSetting(String value, Class<T> type) {
        try {
            return safe(value).isBlank() ? Optional.empty() : Optional.of(objectMapper.readValue(value, type));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> readJsonSetting(String value, TypeReference<T> type) {
        try {
            return safe(value).isBlank() ? Optional.empty() : Optional.of(objectMapper.readValue(value, type));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String jsonSetting(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to serialize setting", ex);
        }
    }

    private void saveDatabaseUiSettings() {
        if (!databaseStoreMode || loadingDatabaseState) {
            return;
        }
        upsertDatabaseSetting(SETTING_REPLICA_UI_THEMES, jsonSetting(uiManagementSettings()), 1, null);
        upsertDatabaseSetting(SETTING_REPLICA_ACTIVE_UI_THEME, activeUiTheme, 1, null);
    }

    private void saveDatabaseDashboardCustomizations() {
        if (!databaseStoreMode || loadingDatabaseState) {
            return;
        }
        upsertDatabaseSetting(SETTING_REPLICA_DASHBOARDS,
                jsonSetting(new ArrayList<>(dashboardCustomizations.values())), 1, null);
    }

    private void upsertDatabaseSetting(String name, String value, Integer tenantId, Long userId) {
        if (safe(name).isBlank()) {
            return;
        }
        int updated = updateDatabaseSetting(name, value, tenantId, userId);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsSettings
                            (CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                             Name, TenantId, UserId, Value)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                null,
                null,
                truncateForColumn(name, 256),
                tenantId,
                userId,
                value);
    }

    private int updateDatabaseSetting(String name, String value, Integer tenantId, Long userId) {
        String tenantPredicate = tenantId == null ? "TenantId IS NULL" : "TenantId = ?";
        String userPredicate = userId == null ? "UserId IS NULL" : "UserId = ?";
        List<Object> args = new ArrayList<>();
        args.add(value);
        args.add(Timestamp.valueOf(LocalDateTime.now()));
        args.add(1L);
        args.add(name);
        if (tenantId != null) {
            args.add(tenantId);
        }
        if (userId != null) {
            args.add(userId);
        }
        return jdbcTemplate.update("""
                        UPDATE dbo.SgsSettings
                        SET Value = ?,
                            LastModificationTime = ?,
                            LastModifierUserId = ?
                        WHERE Name = ? AND %s AND %s
                        """.formatted(tenantPredicate, userPredicate), args.toArray());
    }

    private void loadDatabaseFavorites() {
        favorites.clear();
        defaultFavoriteAbilityIdsByUser.clear();
        Map<UUID, FavoriteGroup> favoriteById = new HashMap<>();
        dbRows("MyFavorites").stream().filter(this::notDeleted).forEach(row -> {
            FavoriteGroup group = new FavoriteGroup();
            group.id = dbUuid(row, "Id");
            group.name = dbString(row, "Name");
            group.userId = Optional.ofNullable(dbLong(row, "UserId")).orElse(0L);
            favorites.put(group.id, group);
            favoriteById.put(group.id, group);
        });
        for (Map<String, Object> row : dbRows("MyFavoriteItems")) {
            UUID abilityId = dbUuid(row, "AbilityId");
            Long userId = dbLong(row, "UserId");
            if (abilityId == null || userId == null) {
                continue;
            }
            UUID groupId = dbUuid(row, "MyFavoriteId");
            FavoriteGroup group = groupId == null ? null : favoriteById.get(groupId);
            if (group == null) {
                defaultFavoriteAbilityIds(userId).add(abilityId);
            } else if (!group.abilityIds.contains(abilityId)) {
                group.abilityIds.add(abilityId);
            }
        }
    }

    private void loadDatabaseEntityHistory() {
        entityChanges.clear();
        entityPropertyChanges.clear();
        Map<Long, Map<String, Object>> changeSetById = dbRows("SgsEntityChangeSets").stream()
                .filter(this::defaultTenant)
                .collect(Collectors.toMap(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L),
                        Function.identity(), (left, right) -> left));
        Map<Long, UserItem> userById = new HashMap<>(users);
        List<Map<String, Object>> changeRows = dbRows("SgsEntityChanges").stream()
                .filter(this::defaultTenant)
                .filter(row -> equalsText(dbString(row, "EntityTypeFullName"), PRODUCTION_ABILITY_ENTITY))
                .sorted(Comparator.comparing((Map<String, Object> row) -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .toList();
        Set<Long> importedChangeIds = new HashSet<>();
        for (Map<String, Object> row : changeRows) {
            Long id = dbLong(row, "Id");
            Map<String, Object> changeSet = changeSetById.get(dbLong(row, "EntityChangeSetId"));
            Long userId = changeSet == null ? null : dbLong(changeSet, "UserId");
            EntityChangeItem item = new EntityChangeItem();
            item.id = id;
            item.userId = userId;
            item.userName = displayDatabaseUser(userById.get(userId));
            item.changeTime = dbDateTime(row, "ChangeTime");
            item.entityTypeFullName = dbString(row, "EntityTypeFullName");
            item.entityTypeDescription = "能力表";
            item.entityId = stripJsonString(dbString(row, "EntityId"));
            item.changeType = dbInteger(row, "ChangeType");
            item.changeTypeName = changeTypeName(item.changeType);
            item.entityChangeSetId = dbLong(row, "EntityChangeSetId");
            item.tenantId = dbInteger(row, "TenantId");
            entityChanges.add(item);
            importedChangeIds.add(id);
        }
        if (importedChangeIds.isEmpty()) {
            return;
        }
        dbRows("SgsEntityPropertyChanges").stream()
                .filter(this::defaultTenant)
                .filter(row -> importedChangeIds.contains(dbLong(row, "EntityChangeId")))
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .forEach(row -> {
                    EntityPropertyChangeItem item = new EntityPropertyChangeItem();
                    item.id = dbLong(row, "Id");
                    item.entityChangeId = dbLong(row, "EntityChangeId");
                    item.newValue = dbNullableString(row, "NewValue");
                    item.originalValue = dbNullableString(row, "OriginalValue");
                    item.propertyName = dbNullableString(row, "PropertyName");
                    item.propertyTypeFullName = dbNullableString(row, "PropertyTypeFullName");
                    item.tenantId = dbInteger(row, "TenantId");
                    entityPropertyChanges.add(item);
                });
    }

    private void loadDatabaseAccountSocialData() {
        profilePictures.clear();
        userLoginAttempts.clear();
        userDelegations.clear();
        friendships.clear();
        chatMessages.clear();

        for (UserItem user : users.values()) {
            if (user.linkedUserIds == null) {
                user.linkedUserIds = new ArrayList<>();
            } else {
                user.linkedUserIds.clear();
            }
        }

        jdbcTemplate.queryForList("""
                        SELECT Id, Bytes, TenantId
                        FROM dbo.AppBinaryObjects
                        WHERE Id IN (
                            SELECT ProfilePictureId
                            FROM dbo.SgsUsers
                            WHERE ProfilePictureId IS NOT NULL AND IsDeleted = 0 AND TenantId = 1
                        )
                        """)
                .forEach(row -> {
                    UUID id = dbUuid(row, "Id");
                    Object bytes = dbValue(row, "Bytes");
                    if (id != null && bytes instanceof byte[] content) {
                        profilePictures.put(id.toString(), Base64.getEncoder().encodeToString(content));
                    }
                });

        Map<Long, List<Long>> linkedByGroup = new LinkedHashMap<>();
        dbRows("SgsUserAccounts").stream()
                .filter(this::notDeleted)
                .filter(this::defaultTenant)
                .forEach(row -> {
                    Long linkId = dbLong(row, "UserLinkId");
                    Long userId = dbLong(row, "UserId");
                    if (linkId != null && userId != null && users.containsKey(userId)) {
                        linkedByGroup.computeIfAbsent(linkId, ignored -> new ArrayList<>()).add(userId);
                    }
                });
        linkedByGroup.values().forEach(group -> {
            List<Long> unique = uniqueLongs(group);
            for (Long userId : unique) {
                UserItem user = users.get(userId);
                if (user == null) {
                    continue;
                }
                unique.stream()
                        .filter(otherId -> !Objects.equals(otherId, userId))
                        .filter(otherId -> !user.linkedUserIds.contains(otherId))
                        .forEach(user.linkedUserIds::add);
            }
        });

        dbRows("SgsUserLoginAttempts").stream()
                .filter(this::hostOrDefaultTenant)
                .sorted(Comparator.comparing(row -> Optional.ofNullable(dbLong(row, "Id")).orElse(0L)))
                .forEach(row -> {
                    UserLoginAttemptItem item = new UserLoginAttemptItem();
                    item.id = dbLong(row, "Id");
                    item.userId = dbLong(row, "UserId");
                    item.tenancyName = dbNullableString(row, "TenancyName");
                    item.userNameOrEmail = dbNullableString(row, "UserNameOrEmailAddress");
                    item.clientIpAddress = dbNullableString(row, "ClientIpAddress");
                    item.clientName = dbNullableString(row, "ClientName");
                    item.browserInfo = dbNullableString(row, "BrowserInfo");
                    item.result = loginResultName(dbInteger(row, "Result"));
                    item.creationTime = dbDateTime(row, "CreationTime");
                    userLoginAttempts.add(item);
                });

        dbRows("AppUserDelegations").stream()
                .filter(this::notDeleted)
                .filter(this::hostOrDefaultTenant)
                .forEach(row -> {
                    UserDelegation item = new UserDelegation();
                    item.id = dbLong(row, "Id");
                    item.sourceUserId = dbLong(row, "SourceUserId");
                    item.targetUserId = dbLong(row, "TargetUserId");
                    item.tenantId = dbInteger(row, "TenantId");
                    item.startTime = dbDateTime(row, "StartTime");
                    item.endTime = dbDateTime(row, "EndTime");
                    userDelegations.put(item.id, decorateDelegation(item));
                });

        dbRows("AppFriendships").stream()
                .filter(this::hostOrDefaultTenant)
                .forEach(row -> {
                    FriendItem item = new FriendItem();
                    item.userId = dbLong(row, "UserId");
                    item.tenantId = dbInteger(row, "TenantId");
                    item.friendUserId = dbLong(row, "FriendUserId");
                    item.friendTenantId = dbInteger(row, "FriendTenantId");
                    item.friendUserName = dbNullableString(row, "FriendUserName");
                    item.friendTenancyName = dbNullableString(row, "FriendTenancyName");
                    item.friendProfilePictureId = dbUuid(row, "FriendProfilePictureId");
                    item.state = Optional.ofNullable(dbInteger(row, "State")).orElse(1);
                    item.creationTime = dbDateTime(row, "CreationTime");
                    friendships.put(friendshipKey(item.userId, item.tenantId, item.friendUserId, item.friendTenantId),
                            decorateFriend(item));
                });

        dbRows("AppChatMessages").stream()
                .filter(this::hostOrDefaultTenant)
                .forEach(row -> {
                    ChatMessageItem item = new ChatMessageItem();
                    item.id = dbLong(row, "Id");
                    item.userId = dbLong(row, "UserId");
                    item.tenantId = dbInteger(row, "TenantId");
                    item.targetUserId = dbLong(row, "TargetUserId");
                    item.targetTenantId = dbInteger(row, "TargetTenantId");
                    item.side = Optional.ofNullable(dbInteger(row, "Side")).orElse(1);
                    item.readState = Optional.ofNullable(dbInteger(row, "ReadState")).orElse(1);
                    item.receiverReadState = Optional.ofNullable(dbInteger(row, "ReceiverReadState")).orElse(1);
                    item.message = dbNullableString(row, "Message");
                    item.creationTime = dbDateTime(row, "CreationTime");
                    UUID sharedId = dbUuid(row, "SharedMessageId");
                    item.sharedMessageId = sharedId == null ? null : sharedId.toString();
                    chatMessages.put(item.id, item);
                });
    }

    private void loadDatabaseTenantBinaries() {
        if (tenants.isEmpty()) {
            return;
        }
        List<UUID> binaryIds = tenants.values().stream()
                .flatMap(tenant -> java.util.stream.Stream.of(databaseUuid(tenant.logoId), databaseUuid(tenant.customCssId)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (binaryIds.isEmpty()) {
            return;
        }
        String placeholders = binaryIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Map<UUID, String> contentById = new HashMap<>();
        jdbcTemplate.queryForList("SELECT Id, Bytes FROM dbo.AppBinaryObjects WHERE Id IN (" + placeholders + ")",
                        binaryIds.toArray())
                .forEach(row -> {
                    UUID id = dbUuid(row, "Id");
                    Object bytes = dbValue(row, "Bytes");
                    if (id != null && bytes instanceof byte[] content) {
                        contentById.put(id, Base64.getEncoder().encodeToString(content));
                    }
                });
        tenants.values().forEach(tenant -> {
            UUID logoId = databaseUuid(tenant.logoId);
            UUID customCssId = databaseUuid(tenant.customCssId);
            if (logoId != null) {
                tenant.logoContentBase64 = contentById.get(logoId);
            }
            if (customCssId != null) {
                tenant.customCssContentBase64 = contentById.get(customCssId);
            }
        });
    }

    private List<Map<String, Object>> dbRows(String tableName) {
        return jdbcTemplate.queryForList("SELECT * FROM dbo." + tableName);
    }

    private List<EntityChangeItem> databaseEntityChanges(String entityTypeFullName, String entityId) {
        EntityChangeQuery query = databaseEntityChangeQuery(entityTypeFullName, entityId, null, null, null);
        StringBuilder sql = new StringBuilder(databaseEntityChangeSelect()).append(query.fromWhere());
        sql.append(" ORDER BY ec.ChangeTime DESC, ec.Id DESC");
        return jdbcTemplate.queryForList(sql.toString(), query.args().toArray()).stream()
                .map(this::databaseEntityChange)
                .toList();
    }

    private String databaseEntityChangeSelect() {
        return """
                SELECT ec.Id, ec.EntityChangeSetId, ec.EntityTypeFullName, ec.EntityId, ec.ChangeType,
                       ec.ChangeTime, ec.TenantId, cs.UserId AS ChangeUserId,
                       u.Name AS ChangeUserName, u.Surname AS ChangeUserSurname, u.UserName AS ChangeUserUserName
                """;
    }

    private EntityChangeQuery databaseEntityChangeQuery(String entityTypeFullName, String entityId,
                                                        Optional<LocalDateTime> startDate,
                                                        Optional<LocalDateTime> endDate,
                                                        String userName) {
        StringBuilder sql = new StringBuilder("""
                FROM dbo.SgsEntityChanges ec
                LEFT JOIN dbo.SgsEntityChangeSets cs ON cs.Id = ec.EntityChangeSetId
                LEFT JOIN dbo.SgsUsers u ON u.Id = cs.UserId
                WHERE ec.TenantId = 1 AND (cs.TenantId = 1 OR cs.TenantId IS NULL)
                """);
        List<Object> args = new ArrayList<>();
        if (!safe(entityTypeFullName).isBlank()) {
            sql.append(" AND ec.EntityTypeFullName = ?");
            args.add(entityTypeFullName);
        }
        if (!safe(entityId).isBlank()) {
            sql.append(" AND REPLACE(CONVERT(nvarchar(200), ec.EntityId), '\"', '') = ?");
            args.add(safe(entityId).replace("\"", ""));
        }
        appendDateFilter(sql, args, "ec.ChangeTime", ">=", startDate == null ? Optional.empty() : startDate);
        appendDateFilter(sql, args, "ec.ChangeTime", "<=", endDate == null ? Optional.empty() : endDate);
        appendAuditUserFilter(sql, args, userName);
        return new EntityChangeQuery(sql.toString(), args);
    }

    private record EntityChangeQuery(String fromWhere, List<Object> args) {
    }

    private Optional<EntityChangeItem> databaseEntityChange(Long entityChangeId) {
        if (entityChangeId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT ec.Id, ec.EntityChangeSetId, ec.EntityTypeFullName, ec.EntityId, ec.ChangeType,
                       ec.ChangeTime, ec.TenantId, cs.UserId AS ChangeUserId,
                       u.Name AS ChangeUserName, u.Surname AS ChangeUserSurname, u.UserName AS ChangeUserUserName
                FROM dbo.SgsEntityChanges ec
                LEFT JOIN dbo.SgsEntityChangeSets cs ON cs.Id = ec.EntityChangeSetId
                LEFT JOIN dbo.SgsUsers u ON u.Id = cs.UserId
                WHERE ec.TenantId = 1 AND ec.Id = ?
                """;
        return jdbcTemplate.queryForList(sql, entityChangeId).stream()
                .findFirst()
                .map(this::databaseEntityChange);
    }

    private EntityChangeItem databaseEntityChange(Map<String, Object> row) {
        EntityChangeItem item = new EntityChangeItem();
        item.id = dbLong(row, "Id");
        item.userId = dbLong(row, "ChangeUserId");
        UserItem user = new UserItem();
        user.id = item.userId;
        user.name = dbNullableString(row, "ChangeUserName");
        user.surname = dbNullableString(row, "ChangeUserSurname");
        user.userName = dbString(row, "ChangeUserUserName");
        item.userName = safe(user.userName).isBlank() ? "system" : displayDatabaseUser(user);
        item.changeTime = dbDateTime(row, "ChangeTime");
        item.entityTypeFullName = dbString(row, "EntityTypeFullName");
        item.entityTypeDescription = entityDescription(item.entityTypeFullName);
        item.entityId = stripJsonString(dbString(row, "EntityId"));
        item.changeType = dbInteger(row, "ChangeType");
        item.changeTypeName = changeTypeName(item.changeType);
        item.entityChangeSetId = dbLong(row, "EntityChangeSetId");
        item.tenantId = dbInteger(row, "TenantId");
        return item;
    }

    private List<EntityPropertyChangeItem> databaseEntityPropertyChanges(Long entityChangeId) {
        if (entityChangeId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                        SELECT Id, EntityChangeId, NewValue, OriginalValue, PropertyName,
                               PropertyTypeFullName, TenantId
                        FROM dbo.SgsEntityPropertyChanges
                        WHERE TenantId = 1 AND EntityChangeId = ?
                        ORDER BY Id
                        """, entityChangeId).stream()
                .map(row -> {
                    EntityPropertyChangeItem item = new EntityPropertyChangeItem();
                    item.id = dbLong(row, "Id");
                    item.entityChangeId = dbLong(row, "EntityChangeId");
                    item.newValue = dbNullableString(row, "NewValue");
                    item.originalValue = dbNullableString(row, "OriginalValue");
                    item.propertyName = dbNullableString(row, "PropertyName");
                    item.propertyTypeFullName = dbNullableString(row, "PropertyTypeFullName");
                    item.tenantId = dbInteger(row, "TenantId");
                    return item;
                })
                .toList();
    }

    private List<NameValueItem> databaseEntityHistoryObjectTypes() {
        return jdbcTemplate.queryForList("""
                        SELECT DISTINCT EntityTypeFullName
                        FROM dbo.SgsEntityChanges
                        WHERE TenantId = 1 AND EntityTypeFullName IS NOT NULL
                        ORDER BY EntityTypeFullName
                        """).stream()
                .map(row -> dbString(row, "EntityTypeFullName"))
                .filter(item -> !safe(item).isBlank())
                .map(item -> nameValue(entityDescription(item), item))
                .toList();
    }

    private boolean notDeleted(Map<String, Object> row) {
        return !dbBoolean(row, "IsDeleted");
    }

    private boolean defaultTenant(Map<String, Object> row) {
        Integer tenantId = dbInteger(row, "TenantId");
        return tenantId != null && tenantId == 1;
    }

    private boolean hostOrDefaultTenant(Map<String, Object> row) {
        Integer tenantId = dbInteger(row, "TenantId");
        return tenantId == null || tenantId == 1;
    }

    private Object dbValue(Map<String, Object> row, String name) {
        return row == null ? null : row.get(name);
    }

    private String dbString(Map<String, Object> row, String name) {
        Object value = dbValue(row, name);
        return value == null ? "" : String.valueOf(value);
    }

    private String dbNullableString(Map<String, Object> row, String name) {
        String value = dbString(row, name);
        return value.isBlank() ? null : value;
    }

    private String dbTrimmedNullableString(Map<String, Object> row, String name) {
        String value = dbString(row, name).trim();
        return value.isBlank() ? null : value;
    }

    private boolean dbBoolean(Map<String, Object> row, String name) {
        Object value = dbValue(row, name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value == null ? "" : String.valueOf(value);
        return text.equals("1") || text.equalsIgnoreCase("true");
    }

    private Long dbLong(Map<String, Object> row, String name) {
        Object value = dbValue(row, name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : Long.parseLong(text);
    }

    private Integer dbInteger(Map<String, Object> row, String name) {
        Long value = dbLong(row, name);
        return value == null ? null : value.intValue();
    }

    private BigDecimal dbBigDecimal(Map<String, Object> row, String name) {
        Object value = dbValue(row, name);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : new BigDecimal(text);
    }

    private Integer intId(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return value.intValue();
    }

    private UUID dbUuid(Map<String, Object> row, String name) {
        Object value = dbValue(row, name);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? null : UUID.fromString(text);
    }

    private LocalDateTime dbLocalDateTime(Map<String, Object> row, String name) {
        Object value = dbValue(row, name);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? null : parseLocalDateTime(text.replace(' ', 'T'));
    }

    private String dbDateTime(Map<String, Object> row, String name) {
        LocalDateTime dateTime = dbLocalDateTime(row, name);
        return dateTime == null ? null : dateTime.toString();
    }

    private List<LabAbility> parseDbLabAbilities(String value) {
        if (safe(value).isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<LabAbility> output = new ArrayList<>();
            for (var node : objectMapper.readTree(value)) {
                LabAbility lab = new LabAbility();
                lab.labId = uuidFromNode(node, "LabId", "labId");
                lab.code = textFromNode(node, "Code", "code");
                lab.hasCnas = booleanFromNode(node, "HasCnas", "hasCnas");
                lab.hasCma = booleanFromNode(node, "HasCma", "hasCma");
                lab.isAbility = booleanFromNode(node, "IsAbility", "isAbility");
                output.add(lab);
            }
            return output;
        } catch (IOException ex) {
            return new ArrayList<>();
        }
    }

    private UUID uuidFromNode(com.fasterxml.jackson.databind.JsonNode node, String primary, String secondary) {
        String value = textFromNode(node, primary, secondary);
        return safe(value).isBlank() ? null : UUID.fromString(value);
    }

    private String textFromNode(com.fasterxml.jackson.databind.JsonNode node, String primary, String secondary) {
        com.fasterxml.jackson.databind.JsonNode value = node.get(primary);
        if (value == null || value.isNull()) {
            value = node.get(secondary);
        }
        return value == null || value.isNull() ? "" : value.asText();
    }

    private boolean booleanFromNode(com.fasterxml.jackson.databind.JsonNode node, String primary, String secondary) {
        com.fasterxml.jackson.databind.JsonNode value = node.get(primary);
        if (value == null || value.isNull()) {
            value = node.get(secondary);
        }
        return value != null && value.asBoolean(false);
    }

    private List<String> splitDbCsv(String value) {
        return Arrays.stream(safe(value).split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String dbParentPermissionName(String name) {
        String[] parts = safe(name).split("\\.");
        return parts.length <= 1 ? null : String.join(".", Arrays.copyOf(parts, parts.length - 1));
    }

    private String displayDatabaseUser(UserItem user) {
        if (user == null) {
            return null;
        }
        String displayName = String.join("", safe(user.name), safe(user.surname)).trim();
        if (displayName.isBlank()) {
            displayName = safe(user.userName);
        }
        return displayName + "[" + safe(user.userName) + "]";
    }

    private String stripJsonString(String value) {
        String text = safe(value);
        if (!text.startsWith("\"") || !text.endsWith("\"")) {
            return text;
        }
        try {
            return objectMapper.readValue(text, String.class);
        } catch (IOException ex) {
            return text.substring(1, text.length() - 1).replace("\\\"", "\"");
        }
    }

    private List<String> uniqueStrings(Collection<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(value -> !safe(value).isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> uniqueLongs(Collection<Long> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<UUID> uniqueUuids(Collection<UUID> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private <K, V> void addMapValue(Map<K, List<V>> map, K key, V value) {
        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }

    private StoreSnapshot snapshot() {
        StoreSnapshot snapshot = new StoreSnapshot();
        snapshot.abilities = allAbilities();
        snapshot.labs = labs();
        snapshot.sampleTypes = sampleTypes();
        snapshot.samples = new ArrayList<>(samples.values());
        snapshot.orgSettings = new ArrayList<>(orgSettings.values());
        snapshot.subcontractAbilities = new ArrayList<>(subcontractAbilities.values());
        snapshot.favorites = favoriteGroups();
        snapshot.defaultFavoriteAbilityIds = new ArrayList<>(defaultFavoriteAbilityIdsForRead(1L));
        defaultFavoriteAbilityIdsByUser.forEach((userId, abilityIds) ->
                snapshot.defaultFavoriteAbilityIdsByUser.put(userId, new ArrayList<>(abilityIds)));
        snapshot.roles = roles(null);
        snapshot.users = users(null, 0, Integer.MAX_VALUE).items;
        snapshot.userPasswords = new LinkedHashMap<>(userPasswords);
        snapshot.profilePictures = new LinkedHashMap<>(profilePictures);
        snapshot.userSpecificPermissions = new LinkedHashMap<>(userSpecificPermissions);
        snapshot.userDelegations = userDelegations();
        snapshot.userLoginAttempts = userLoginAttempts();
        snapshot.friendships = friendships();
        snapshot.chatMessages = chatMessages();
        snapshot.features = features();
        snapshot.editions = editions();
        snapshot.tenants = tenants(null, null, false, 0, Integer.MAX_VALUE).items;
        snapshot.subscriptionPayments = subscriptionPayments();
        snapshot.invoices = invoices();
        snapshot.languages = languages();
        snapshot.languageTexts = new ArrayList<>(languageTexts);
        snapshot.notifications = notifications();
        snapshot.notificationSettings = new ArrayList<>(notificationSettings.values());
        snapshot.caches = caches();
        snapshot.dynamicParameters = dynamicParameters();
        snapshot.dynamicParameterValues = dynamicParameterValues(null);
        snapshot.entityDynamicParameters = entityDynamicParameters(null);
        snapshot.entityDynamicParameterValues = entityDynamicParameterValues(null, null);
        snapshot.webhookSubscriptions = webhookSubscriptions();
        snapshot.webhookEvents = webhookEvents();
        snapshot.webhookSendAttempts = webhookSendAttempts();
        snapshot.uiThemes = uiManagementSettings();
        snapshot.dashboardCustomizations = new ArrayList<>(dashboardCustomizations.values());
        snapshot.activeUiTheme = activeUiTheme;
        snapshot.recurringPaymentsEnabled = recurringPaymentsEnabled;
        snapshot.installSettings = installSettings();
        snapshot.hostSettings = hostSettings();
        snapshot.tenantSettings = tenantSettings();
        snapshot.tenantSettingsByTenant = new LinkedHashMap<>(tenantSettingsByTenant);
        snapshot.abilitySettings = abilitySettings();
        snapshot.permissions = new ArrayList<>(permissions);
        snapshot.orgUnits = new ArrayList<>(orgUnits);
        snapshot.history = new ArrayList<>(history);
        snapshot.auditLogs = new ArrayList<>(auditLogs);
        snapshot.entityChanges = new ArrayList<>(entityChanges);
        snapshot.entityPropertyChanges = new ArrayList<>(entityPropertyChanges);
        return snapshot;
    }

    private void clearState() {
        abilities.clear();
        labs.clear();
        sampleTypes.clear();
        samples.clear();
        orgSettings.clear();
        subcontractAbilities.clear();
        favorites.clear();
        defaultFavoriteAbilityIdsByUser.clear();
        roles.clear();
        users.clear();
        userPasswords.clear();
        profilePictures.clear();
        userSpecificPermissions.clear();
        userDelegations.clear();
        userLoginAttempts.clear();
        friendships.clear();
        chatMessages.clear();
        features.clear();
        editions.clear();
        tenants.clear();
        subscriptionPayments.clear();
        invoices.clear();
        languages.clear();
        languageTexts.clear();
        notifications.clear();
        notificationSettings.clear();
        caches.clear();
        dynamicParameters.clear();
        dynamicParameterValues.clear();
        entityDynamicParameters.clear();
        entityDynamicParameterValues.clear();
        webhookSubscriptions.clear();
        webhookEvents.clear();
        webhookSendAttempts.clear();
        uiThemes.clear();
        dashboardCustomizations.clear();
        activeUiTheme = "default";
        recurringPaymentsEnabled = true;
        installSettings = InstallSettingsItem.defaults();
        hostSettings = SystemSettingsItem.defaultHostSettings();
        tenantSettings = SystemSettingsItem.defaultTenantSettings();
        tenantSettingsByTenant.clear();
        abilitySettings = SystemSettingsItem.defaultAbilitySettings();
        permissions.clear();
        orgUnits.clear();
        history.clear();
        auditLogs.clear();
        entityChanges.clear();
        entityPropertyChanges.clear();
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> List<T> page(List<T> rows, int skipCount, int maxResultCount) {
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return rows.stream().skip(skip).limit(take).toList();
    }

    private boolean between(String value, Optional<LocalDateTime> start, Optional<LocalDateTime> end) {
        Optional<LocalDateTime> parsed = parseFlexibleDateTime(value);
        return parsed.map(time -> start.map(startTime -> !time.isBefore(startTime)).orElse(true)
                && end.map(endTime -> !time.isAfter(endTime)).orElse(true)).orElse(true);
    }

    private Optional<LocalDateTime> parseStartDateTime(String value) {
        return parseFlexibleDateTime(value);
    }

    private Optional<LocalDateTime> parseEndDateTime(String value) {
        return parseFlexibleDateTime(value).map(time -> safe(value).length() == 10
                ? time.withHour(23).withMinute(59).withSecond(59)
                : time);
    }

    private Optional<LocalDate> parseDate(String value) {
        try {
            return safe(value).isBlank() ? Optional.empty() : Optional.of(LocalDate.parse(safe(value)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseFlexibleDateTime(String value) {
        try {
            String normalized = safe(value);
            if (normalized.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(normalized.length() == 10
                    ? LocalDate.parse(normalized).atStartOfDay()
                    : LocalDateTime.parse(normalized));
        } catch (RuntimeException ex) {
            try {
                return Optional.of(OffsetDateTime.parse(safe(value)).toLocalDateTime());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
    }

    private boolean matchException(AuditLog item, Boolean hasException) {
        if (hasException == null) {
            return true;
        }
        boolean hasValue = !safe(item.exception).isBlank();
        return hasException == hasValue;
    }

    private Comparator<AuditLog> auditLogComparator(String sorting) {
        String normalized = safe(sorting).toLowerCase(Locale.ROOT);
        Comparator<AuditLog> comparator;
        if (normalized.contains("username")) {
            comparator = Comparator.comparing(item -> safe(item.userName));
        } else if (normalized.contains("servicename")) {
            comparator = Comparator.comparing(item -> safe(item.serviceName));
        } else if (normalized.contains("methodname")) {
            comparator = Comparator.comparing(item -> safe(item.methodName));
        } else if (normalized.contains("executionduration")) {
            comparator = Comparator.comparing(item -> item.executionDuration == null ? 0 : item.executionDuration);
        } else {
            comparator = Comparator.comparing(item -> safe(item.executionTime));
        }
        return normalized.contains("desc") || normalized.isBlank() ? comparator.reversed() : comparator;
    }

    private Comparator<EntityChangeItem> entityChangeComparator(String sorting) {
        String normalized = safe(sorting).toLowerCase(Locale.ROOT);
        Comparator<EntityChangeItem> comparator;
        if (normalized.contains("username")) {
            comparator = Comparator.comparing(item -> safe(item.userName));
        } else if (normalized.contains("entitytypefullname")) {
            comparator = Comparator.comparing(item -> safe(item.entityTypeFullName));
        } else if (normalized.contains("changetype")) {
            comparator = Comparator.comparing(item -> item.changeType == null ? 0 : item.changeType);
        } else {
            comparator = Comparator.comparing(item -> safe(item.changeTime));
        }
        return normalized.contains("desc") || normalized.isBlank() ? comparator.reversed() : comparator;
    }

    private String entityChangeOrderBy(String sorting) {
        String normalized = safe(sorting).toLowerCase(Locale.ROOT);
        String direction = normalized.contains(" asc") && !normalized.contains(" desc") ? "ASC" : "DESC";
        if (normalized.contains("username") || normalized.contains("user")) {
            return "u.UserName " + direction + ", ec.ChangeTime DESC, ec.Id DESC";
        }
        if (normalized.contains("entitytypefullname")) {
            return "ec.EntityTypeFullName " + direction + ", ec.ChangeTime DESC, ec.Id DESC";
        }
        if (normalized.contains("changetype")) {
            return "ec.ChangeType " + direction + ", ec.ChangeTime DESC, ec.Id DESC";
        }
        if (normalized.contains("id")) {
            return "ec.Id " + direction;
        }
        return "ec.ChangeTime " + direction + ", ec.Id " + direction;
    }

    private Comparator<AbilityHistoryItem> abilityHistoryComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original GetAbilityHistoryInput.Normalize defaults blank sorting to ChangeTime Desc.
            normalized = "ChangeTime Desc";
        }
        Comparator<AbilityHistoryItem> comparator = null;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            boolean desc = token.toLowerCase(Locale.ROOT).endsWith(" desc");
            String field = token.toLowerCase(Locale.ROOT).replace(" desc", "").replace(" asc", "").trim();
            Comparator<AbilityHistoryItem> current = abilityHistoryFieldComparator(field);
            comparator = comparator == null ? (desc ? current.reversed() : current)
                    : comparator.thenComparing(desc ? current.reversed() : current);
        }
        return (comparator == null ? abilityHistoryFieldComparator("changetime").reversed() : comparator)
                .thenComparing(item -> item.id == null ? 0L : item.id);
    }

    private Comparator<AbilityHistoryItem> abilityHistoryFieldComparator(String field) {
        return switch (field) {
            case "changetype" -> Comparator.comparing(item -> safe(item.changeType));
            case "user", "username" -> Comparator.comparing(item -> safe(item.user));
            case "displayname" -> Comparator.comparing(item -> safe(item.displayName));
            case "id" -> Comparator.comparing(item -> item.id == null ? 0L : item.id);
            default -> Comparator.comparing(item -> safe(item.changeTime));
        };
    }

    private boolean equalsEntityId(String stored, String requested) {
        String normalizedStored = safe(stored).replace("\"", "");
        String normalizedRequested = safe(requested).replace("\"", "");
        return equalsText(normalizedStored, normalizedRequested);
    }

    private NameValueItem nameValue(String name, String value) {
        NameValueItem item = new NameValueItem();
        item.name = name;
        item.value = value;
        return item;
    }

    private String stripNamespace(String value) {
        String safeValue = safe(value);
        int index = safeValue.lastIndexOf('.');
        return index < 0 ? safeValue : safeValue.substring(index + 1);
    }

    /** Creates anonymized data with the same field shape as the original system. */
    private void seed() {
        OrganizationUnit mineral = org(1L, null, "矿物事业部");
        OrganizationUnit chemistry = org(2L, 1L, "化学检测");
        OrganizationUnit physical = org(3L, 1L, "物理检测");
        orgUnits.addAll(List.of(mineral, chemistry, physical));

        seedPermissions();
        RoleItem adminRole = role(1, "Admin", "管理员", true, true,
                permissions.stream().map(item -> item.name).toList());
        RoleItem queryRole = role(2, "AbilityQuery", "能力查询", false, false,
                List.of("Pages.AbilityQuery", "Pages.Log.AbilityHistory"));
        adminRole.organizationUnits.add(mineral.id);
        queryRole.organizationUnits.add(chemistry.id);
        roles.put(adminRole.id, adminRole);
        roles.put(queryRole.id, queryRole);

        Laboratory tj = lab("TJ", "天津实验室", "Tianjin Lab", "王工", "022-000000", "天津");
        Laboratory sh = lab("SH", "上海实验室", "Shanghai Lab", "李工", "021-000000", "上海");
        labs.put(tj.id, tj);
        labs.put(sh.id, sh);

        UserItem admin = user(1L, "Admin", "System", "admin", "admin@example.local", "13800000000", true);
        admin.assignedRoleNames.add("Admin");
        admin.organizationUnits.addAll(List.of(mineral.id, chemistry.id));
        admin.labs.add(tj.id);
        UserItem queryUser = user(2L, "Query", "User", "query", "query@example.local", "13900000000", true);
        queryUser.assignedRoleNames.add("AbilityQuery");
        queryUser.organizationUnits.add(chemistry.id);
        admin.linkedUserIds.add(queryUser.id);
        queryUser.linkedUserIds.add(admin.id);
        users.put(admin.id, admin);
        users.put(queryUser.id, queryUser);
        userPasswords.put(admin.id, "123qwe");
        userPasswords.put(queryUser.id, "123qwe");

        UserDelegation delegation = userDelegation(admin.id, queryUser);
        userDelegations.put(delegation.id, delegation);
        seedChat(admin, queryUser);
        seedLanguages();
        seedNotifications(admin.id, queryUser.id);
        seedCaches();
        seedTenantPlatform();

        SampleType ore = type("矿石", chemistry.id, chemistry.displayName);
        SampleType concentrate = type("精矿", chemistry.id, chemistry.displayName);
        sampleTypes.put(ore.id, ore);
        sampleTypes.put(concentrate.id, concentrate);

        Sample iron = sample("铁矿石", "Iron Ore", "Fe Ore", ore);
        Sample copper = sample("铜精矿", "Copper Concentrate", "Cu Conc", concentrate);
        samples.put(iron.id, iron);
        samples.put(copper.id, copper);

        Ability ability = ability(chemistry, ore, "铁矿石", "全铁含量", "重铬酸钾滴定法", "ISO 2597-1",
                "5", "100", "<0.074", "10%-70%", "询价", "脱敏样例");
        ability.labAbilities.add(labAbility(tj, true, true, true));
        ability.labAbilities.add(labAbility(sh, true, false, true));
        abilities.put(ability.id, ability);

        Ability second = ability(physical, concentrate, "铜精矿", "水分", "烘干法", "GB/T 6730",
                "3", "200", "常规", "0.1%-20%", "询价", "脱敏样例");
        second.labAbilities.add(labAbility(tj, false, true, true));
        abilities.put(second.id, second);
        seedDynamicParameters(ability.id.toString());
        seedWebhooks();
        seedUiThemes();

        SubcontractAbility subcontract = subcontract("第三方校准中心", "400-000-0000", "矿物成分复核",
                "CNAS L0000/2027-12-31", "年度合格供应商评价", "Admin", "合格");
        subcontractAbilities.put(subcontract.id, subcontract);

        FavoriteGroup favorite = new FavoriteGroup();
        favorite.id = UUID.randomUUID();
        favorite.name = "常用能力";
        favorite.userId = 1L;
        favorite.abilityIds.add(ability.id);
        favorites.put(favorite.id, favorite);

        OrgAbilitySetting setting = new OrgAbilitySetting();
        setting.orgId = chemistry.id;
        setting.propertyName.addAll(defaultPropertyNames());
        setting.lab.addAll(List.of("TJ", "SH"));
        setting.isPublic = true;
        setting.description = "化学检测能力字段配置";
        orgSettings.put(setting.orgId, setting);
        ensureProductionBusinessLineData();

        AbilityHistoryItem item = new AbilityHistoryItem();
        item.changeTime = LocalDateTime.now().minusDays(1).toString();
        item.changeType = "更新";
        item.user = "Admin";
        item.displayName = "标准号";
        item.originalValue = "ISO 2597";
        item.newValue = "ISO 2597-1";
        history.add(item);

        audit(1L, "AbilityAppService", "FindPageAblibities", "成功");
        seedEntityHistory();
    }

    public PageResult<Ability> findAbilities(FindAbilityRequest input) {
        return findAbilities(input, 1L);
    }

    public PageResult<Ability> findAbilities(FindAbilityRequest input, Long userId) {
        FindAbilityRequest effective = input == null ? new FindAbilityRequest() : input;
        List<Ability> filtered = findAllAbilities(effective, userId);
        int skip = Math.max(effective.skipCount, 0);
        int take = effective.maxResultCount <= 0 ? 10 : effective.maxResultCount;
        List<Ability> page = filtered.stream().skip(skip).limit(take).toList();
        return new PageResult<>(filtered.size(), page);
    }

    /** Query page rows do not include the management page favorite state. */
    public PageResult<Ability> findQueryAbilities(FindAbilityRequest input) {
        FindAbilityRequest effective = input == null ? new FindAbilityRequest() : input;
        List<Ability> filtered = abilities.values().stream()
                .filter(item -> matches(item, effective))
                .sorted(abilityComparator(effective.sorting))
                .toList();
        int skip = Math.max(effective.skipCount, 0);
        int take = effective.maxResultCount <= 0 ? 10 : effective.maxResultCount;
        List<Ability> page = filtered.stream()
                .skip(skip)
                .limit(take)
                .map(this::queryAbilityView)
                .toList();
        return new PageResult<>(filtered.size(), page);
    }

    public List<Ability> findAllAbilities(FindAbilityRequest input) {
        return findAllAbilities(input, 1L);
    }

    public List<Ability> findAllAbilities(FindAbilityRequest input, Long userId) {
        FindAbilityRequest effective = input == null ? new FindAbilityRequest() : input;
        List<Ability> filtered = abilities.values().stream()
                .filter(item -> matches(item, effective))
                .sorted(abilityComparator(effective.sorting))
                .toList();
        filtered.forEach(item -> markFavoriteStatus(item, userId));
        return filtered;
    }

    public List<Ability> allAbilities() {
        return abilities.values().stream()
                .sorted(Comparator.comparing(item -> safe(item.testItem)))
                .toList();
    }

    /** Applies the simple search fields used by the copied UI. */
    private boolean matches(Ability item, FindAbilityRequest input) {
        if (input == null) {
            return true;
        }
        if (input.orgId != null && !Objects.equals(item.orgId, input.orgId)) {
            return false;
        }
        List<DynamicFilter> dynamicFilters = dynamicFilters(input);
        DynamicFilter labFilter = firstFilter(dynamicFilters, "labAbility");
        DynamicFilter qualificationFilter = firstFilter(dynamicFilters, "ability");
        if (!matchesLabQualification(item, labFilter, qualificationFilter)) {
            return false;
        }
        for (DynamicFilter filter : dynamicFilters) {
            String field = filterField(filter);
            if (field.equals("labAbility") || field.equals("ability")) {
                continue;
            }
            if (!matchesAbilityField(item, field, filter.value)) {
                return false;
            }
        }
        String all = String.join(" ", safe(item.typeName), safe(item.samplingName), safe(item.productCode),
                safe(item.testItem), safe(item.testItemRemark), safe(item.standardNo), safe(item.methodName),
                safe(item.methodRemark), safe(item.methodEngName), safe(item.gbNo), safe(item.isoNo), safe(item.gbtNo),
                safe(item.astmNo), safe(item.industryStandardNo), safe(item.otherNo));
        return contains(all, keywordFilter(input))
                && contains(item.typeName, input.typeName)
                && contains(item.samplingName, input.samplingName)
                && contains(item.testItem, input.testItem)
                && contains(item.standardNo, input.standardNo)
                && contains(item.methodName, input.methodName)
                && contains(item.methodEngName, input.methodEngName);
    }

    private String keywordFilter(FindAbilityRequest input) {
        return input.filter;
    }

    private Comparator<Ability> abilityComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original FindPageAblibitiesInput/FindAblibitiesInput Normalize defaults blank sorting to Id.
            normalized = "Id";
        }
        Comparator<Ability> comparator = null;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            boolean desc = token.toLowerCase(Locale.ROOT).endsWith(" desc");
            String field = token.toLowerCase(Locale.ROOT).replace(" desc", "").replace(" asc", "").trim();
            Comparator<Ability> current = abilityFieldComparator(field);
            comparator = comparator == null ? (desc ? current.reversed() : current)
                    : comparator.thenComparing(desc ? current.reversed() : current);
        }
        return (comparator == null ? abilityFieldComparator("id") : comparator)
                .thenComparing(item -> item.id == null ? "" : item.id.toString());
    }

    private Comparator<Ability> abilityFieldComparator(String field) {
        return switch (field) {
            case "orgname" -> Comparator.comparing(item -> safe(item.orgName));
            case "orgid" -> Comparator.comparing(item -> item.orgId == null ? 0L : item.orgId);
            case "typename" -> Comparator.comparing(item -> safe(item.typeName));
            case "samplingname" -> Comparator.comparing(item -> safe(item.samplingName));
            case "productcode" -> Comparator.comparing(item -> safe(item.productCode));
            case "testitem" -> Comparator.comparing(item -> safe(item.testItem));
            case "standardno" -> Comparator.comparing(item -> safe(item.standardNo));
            case "methodname" -> Comparator.comparing(item -> safe(item.methodName));
            case "methodengname" -> Comparator.comparing(item -> safe(item.methodEngName));
            case "price" -> Comparator.comparing(item -> safe(item.price));
            default -> Comparator.comparing(item -> item.id == null ? "" : item.id.toString());
        };
    }

    private List<DynamicFilter> dynamicFilters(FindAbilityRequest input) {
        List<DynamicFilter> result = new ArrayList<>();
        if (input.filterItems != null) {
            result.addAll(input.filterItems.stream().filter(Objects::nonNull).toList());
        }
        return result.stream()
                .filter(item -> !filterField(item).isBlank() && !safe(item.value).isBlank())
                .toList();
    }

    private DynamicFilter firstFilter(List<DynamicFilter> filters, String field) {
        return filters.stream()
                .filter(item -> equalsText(filterField(item), field))
                .findFirst()
                .orElse(null);
    }

    private String filterField(DynamicFilter filter) {
        return safe(safe(filter.field).isBlank() ? filter.name : filter.field);
    }

    private boolean matchesLabQualification(Ability item, DynamicFilter labFilter, DynamicFilter qualificationFilter) {
        String labCode = labFilter == null ? "" : safe(labFilter.value);
        if (labCode.isBlank()) {
            return true;
        }
        String qualification = qualificationFilter == null ? "" : safe(qualificationFilter.value);
        return item.labAbilities != null && item.labAbilities.stream()
                .anyMatch(lab -> equalsText(lab.code, labCode) && labMatchesQualification(lab, qualification));
    }

    private boolean labMatchesQualification(LabAbility lab, String qualification) {
        String normalized = safe(qualification).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return lab.isAbility;
        }
        return switch (normalized) {
            case "ALL" -> lab.isAbility && lab.hasCnas && lab.hasCma;
            case "CNAS" -> lab.isAbility && lab.hasCnas;
            case "CMA" -> lab.isAbility && lab.hasCma;
            case "无" -> lab.isAbility && !lab.hasCnas && !lab.hasCma;
            default -> lab.isAbility;
        };
    }

    private boolean matchesAbilityField(Ability item, String field, String value) {
        return switch (safe(field)) {
            case "orgName" -> contains(item.orgName, value);
            case "typeName" -> contains(item.typeName, value);
            case "samplingName" -> contains(item.samplingName, value);
            case "productCode" -> contains(item.productCode, value);
            case "testItem" -> contains(item.testItem, value);
            case "testItemRemark" -> contains(item.testItemRemark, value);
            case "standardNo" -> contains(item.standardNo, value);
            case "methodName" -> contains(item.methodName, value);
            case "methodRemark" -> contains(item.methodRemark, value);
            case "methodEngName" -> contains(item.methodEngName, value);
            case "gbNo" -> contains(item.gbNo, value);
            case "isoNo" -> contains(item.isoNo, value);
            case "gbtNo" -> contains(item.gbtNo, value);
            case "astmNo" -> contains(item.astmNo, value);
            case "industryStandardNo" -> contains(item.industryStandardNo, value);
            case "otherNo" -> contains(item.otherNo, value);
            case "cycleWorkingDay" -> contains(item.cycleWorkingDay, value);
            case "testTime" -> contains(item.testTime, value);
            case "massRequired" -> contains(item.massRequired, value);
            case "sizeRequired" -> contains(item.sizeRequired, value);
            case "detectionLimit" -> contains(item.detectionLimit, value);
            case "price" -> contains(item.price, value);
            case "remark" -> contains(item.remark, value);
            case "standardNoSgs" -> contains(item.standardNoSgs, value);
            case "standardNoSop" -> contains(item.standardNoSop, value);
            case "standardNoOthers" -> contains(item.standardNoOthers, value);
            case "standardNoDz" -> contains(item.standardNoDz, value);
            default -> true;
        };
    }

    public Ability saveAbility(Ability input) {
        return saveAbility(input, 1L);
    }

    public Ability saveAbility(Ability input, Long actorUserId) {
        if (input == null) {
            throw new IllegalArgumentException("能力不能为空");
        }
        long userId = actorUserId == null ? 1L : actorUserId;
        normalizeAbilityReferences(input);
        validateAbilityForSave(input);
        findDuplicateAbility(input)
                .filter(existing -> !Objects.equals(existing.id, input.id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("能力已存在");
                });
        boolean creating = input.id == null || !abilities.containsKey(input.id);
        Ability previous = input.id == null ? null : abilities.get(input.id);
        if (input.id == null) {
            input.id = UUID.randomUUID();
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseAbility(input, creating);
        }
        abilities.put(input.id, input);
        markFavoriteStatus(input);
        audit(userId, "AbilityAppService", creating ? "CreateAbility" : "UpdateAbility", safe(input.testItem));
        recordEntityChange(userId, input.id.toString(), ABILITY_ENTITY, "能力表", creating ? 0 : 1,
                abilityPropertyChanges(creating ? null : previous, input));
        persist();
        return input;
    }

    private void normalizeAbilityReferences(Ability input) {
        resolveAbilityOrg(input.orgId, input.orgName).ifPresent(org -> {
            input.orgId = org.id;
            input.orgName = org.displayName;
        });
        resolveAbilityType(input).ifPresent(type -> {
            input.typeId = type.id;
            input.typeName = type.displayName;
        });
        if (input.labAbilities == null) {
            input.labAbilities = new ArrayList<>();
        }
    }

    private Optional<OrganizationUnit> resolveAbilityOrg(Long orgId, String orgName) {
        return orgUnits.stream()
                .filter(org -> (orgId != null && Objects.equals(org.id, orgId)) || equalsText(org.displayName, orgName))
                .findFirst();
    }

    private Optional<SampleType> resolveAbilityType(Ability input) {
        return sampleTypes.values().stream()
                .filter(type -> input.typeId != null && Objects.equals(type.id, input.typeId))
                .findFirst()
                .or(() -> sampleTypes.values().stream()
                        .filter(type -> input.orgId == null || Objects.equals(type.orgId, input.orgId))
                        .filter(type -> equalsText(type.displayName, input.typeName))
                        .findFirst());
    }

    private void validateAbilityForSave(Ability input) {
        List<String> errors = new ArrayList<>();
        REQUIRED_ABILITY_FIELDS.forEach((name, title) -> {
            if (abilityFieldEnabled(input.orgId, name) && safe(abilityFieldValue(input, name)).isBlank()) {
                errors.add(title + "不能为空");
            }
        });
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", errors));
        }
    }

    private boolean abilityFieldEnabled(Long orgId, String name) {
        if (orgId == null) {
            return true;
        }
        OrgAbilitySetting setting = orgSettings.get(orgId);
        return setting == null ? defaultPropertyNames().contains(name) : propertyEnabled(setting.propertyName, name);
    }

    private String abilityFieldValue(Ability input, String name) {
        return switch (name) {
            case "typeName" -> input.typeName;
            case "samplingName" -> input.samplingName;
            case "testItem" -> input.testItem;
            case "methodName" -> input.methodName;
            case "methodEngName" -> input.methodEngName;
            case "standardNo" -> input.standardNo;
            case "cycleWorkingDay" -> input.cycleWorkingDay;
            case "massRequired" -> input.massRequired;
            case "sizeRequired" -> input.sizeRequired;
            case "detectionLimit" -> input.detectionLimit;
            case "price" -> input.price;
            default -> "";
        };
    }

    public Optional<Ability> findDuplicateAbility(Ability input) {
        return abilities.values().stream()
                .filter(item -> sameAbility(item, input))
                .findFirst();
    }

    public Optional<Ability> getAbility(String id) {
        return getAbility(id, 1L);
    }

    public Optional<Ability> getAbility(String id, Long userId) {
        return parseUuid(id).map(abilities::get).map(item -> {
            markFavoriteStatus(item, userId);
            return item;
        });
    }

    public void deleteAbility(String id) {
        deleteAbility(id, 1L);
    }

    public void deleteAbility(String id, Long actorUserId) {
        long userId = actorUserId == null ? 1L : actorUserId;
        parseUuid(id).ifPresent(uuid -> {
            Ability removed = abilities.get(uuid);
            if (removed != null) {
                if (databaseStoreMode && !loadingDatabaseState) {
                    softDeleteDatabaseAbility(uuid);
                }
                abilities.remove(uuid);
                audit(userId, "AbilityAppService", "DeleteAbility", safe(removed.testItem));
                recordEntityChange(userId, uuid.toString(), ABILITY_ENTITY, "能力表", 2);
                persist();
            }
        });
    }

    private void upsertDatabaseAbility(Ability ability, boolean creating) {
        if (ability == null || ability.id == null) {
            return;
        }
        if (safe(ability.creationTime).isBlank()) {
            ability.creationTime = LocalDateTime.now().toString();
        }
        if (databaseAbilityExists(ability.id)) {
            updateDatabaseAbility(ability);
            return;
        }
        insertDatabaseAbility(ability, creating);
    }

    private boolean databaseAbilityExists(UUID id) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.MineralAbilityTable
                        WHERE Id = CONVERT(uniqueidentifier, ?)
                        """,
                Long.class,
                id.toString());
        return count != null && count > 0;
    }

    private void insertDatabaseAbility(Ability ability, boolean creating) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp creationTime = Timestamp.valueOf(parseFlexibleDateTime(ability.creationTime).orElse(LocalDateTime.now()));
        jdbcTemplate.update("""
                        INSERT INTO dbo.MineralAbilityTable
                            (Id, CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                             IsDeleted, DeleterUserId, DeletionTime, OrgName, TypeName, TypeId, SamplingName,
                             SamplingId, ProductCode, TestItem, TestItemRemark, MethodName, MethodRemark,
                             MethodEngName, GbNo, GbRemark, IsoNo, IsoRemark, GbtNo, GbtRemark, AstmNo,
                             AstmRemark, IndustryStandardNo, IndustryStandardRemark, OtherNo, OtherRemark,
                             CycleWorkingDay, TestTime, TestTimeRemark, MassRequired, MassRequiredRemark,
                             SizeRequired, SizeRequiredRemark, DetectionLimit, Price, PriceRemark, LabAbility,
                             OrgId, Remark, StandardNo, StandardNoDz, StandardNoOthers, StandardNoSgs, StandardNoSop)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, CONVERT(uniqueidentifier, ?), ?,
                             CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                             ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                ability.id.toString(),
                creationTime,
                1L,
                creating ? null : now,
                creating ? null : 1L,
                false,
                null,
                null,
                ability.orgName,
                ability.typeName,
                uuidString(ability.typeId),
                ability.samplingName,
                uuidString(ability.samplingId),
                ability.productCode,
                ability.testItem,
                ability.testItemRemark,
                ability.methodName,
                ability.methodRemark,
                ability.methodEngName,
                ability.gbNo,
                ability.gbRemark,
                ability.isoNo,
                ability.isoRemark,
                ability.gbtNo,
                ability.gbtRemark,
                ability.astmNo,
                ability.astmRemark,
                ability.industryStandardNo,
                ability.industryStandardRemark,
                ability.otherNo,
                ability.otherRemark,
                ability.cycleWorkingDay,
                ability.testTime,
                ability.testTimeRemark,
                ability.massRequired,
                ability.massRequiredRemark,
                ability.sizeRequired,
                ability.sizeRequiredRemark,
                ability.detectionLimit,
                ability.price,
                ability.priceRemark,
                databaseLabAbilityJson(ability.labAbilities),
                ability.orgId,
                ability.remark,
                ability.standardNo,
                ability.standardNoDz,
                ability.standardNoOthers,
                ability.standardNoSgs,
                ability.standardNoSop);
    }

    private void updateDatabaseAbility(Ability ability) {
        jdbcTemplate.update("""
                        UPDATE dbo.MineralAbilityTable
                        SET LastModificationTime = ?,
                            LastModifierUserId = ?,
                            IsDeleted = ?,
                            DeleterUserId = NULL,
                            DeletionTime = NULL,
                            OrgName = ?,
                            TypeName = ?,
                            TypeId = CONVERT(uniqueidentifier, ?),
                            SamplingName = ?,
                            SamplingId = CONVERT(uniqueidentifier, ?),
                            ProductCode = ?,
                            TestItem = ?,
                            TestItemRemark = ?,
                            MethodName = ?,
                            MethodRemark = ?,
                            MethodEngName = ?,
                            GbNo = ?,
                            GbRemark = ?,
                            IsoNo = ?,
                            IsoRemark = ?,
                            GbtNo = ?,
                            GbtRemark = ?,
                            AstmNo = ?,
                            AstmRemark = ?,
                            IndustryStandardNo = ?,
                            IndustryStandardRemark = ?,
                            OtherNo = ?,
                            OtherRemark = ?,
                            CycleWorkingDay = ?,
                            TestTime = ?,
                            TestTimeRemark = ?,
                            MassRequired = ?,
                            MassRequiredRemark = ?,
                            SizeRequired = ?,
                            SizeRequiredRemark = ?,
                            DetectionLimit = ?,
                            Price = ?,
                            PriceRemark = ?,
                            LabAbility = ?,
                            OrgId = ?,
                            Remark = ?,
                            StandardNo = ?,
                            StandardNoDz = ?,
                            StandardNoOthers = ?,
                            StandardNoSgs = ?,
                            StandardNoSop = ?
                        WHERE Id = CONVERT(uniqueidentifier, ?)
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                false,
                ability.orgName,
                ability.typeName,
                uuidString(ability.typeId),
                ability.samplingName,
                uuidString(ability.samplingId),
                ability.productCode,
                ability.testItem,
                ability.testItemRemark,
                ability.methodName,
                ability.methodRemark,
                ability.methodEngName,
                ability.gbNo,
                ability.gbRemark,
                ability.isoNo,
                ability.isoRemark,
                ability.gbtNo,
                ability.gbtRemark,
                ability.astmNo,
                ability.astmRemark,
                ability.industryStandardNo,
                ability.industryStandardRemark,
                ability.otherNo,
                ability.otherRemark,
                ability.cycleWorkingDay,
                ability.testTime,
                ability.testTimeRemark,
                ability.massRequired,
                ability.massRequiredRemark,
                ability.sizeRequired,
                ability.sizeRequiredRemark,
                ability.detectionLimit,
                ability.price,
                ability.priceRemark,
                databaseLabAbilityJson(ability.labAbilities),
                ability.orgId,
                ability.remark,
                ability.standardNo,
                ability.standardNoDz,
                ability.standardNoOthers,
                ability.standardNoSgs,
                ability.standardNoSop,
                ability.id.toString());
    }

    private void softDeleteDatabaseAbility(UUID id) {
        jdbcTemplate.update("""
                        UPDATE dbo.MineralAbilityTable
                        SET IsDeleted = ?,
                            DeletionTime = ?,
                            DeleterUserId = ?,
                            LastModificationTime = ?,
                            LastModifierUserId = ?
                        WHERE Id = CONVERT(uniqueidentifier, ?)
                        """,
                true,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                id.toString());
    }

    private String uuidString(UUID value) {
        return value == null ? null : value.toString();
    }

    private String databaseLabAbilityJson(List<LabAbility> labAbilities) {
        List<Map<String, Object>> rows = list(labAbilities).stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("LabId", item.labId == null ? null : item.labId.toString());
                    row.put("Code", item.code);
                    row.put("HasCnas", item.hasCnas);
                    row.put("HasCma", item.hasCma);
                    row.put("IsAbility", item.isAbility);
                    return row;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (IOException ex) {
            return "[]";
        }
    }

    private void upsertDatabaseLab(Laboratory lab) {
        if (lab == null || lab.id == null) {
            return;
        }
        if (databaseUuidRowExists("MineralLaboratory", lab.id)) {
            jdbcTemplate.update("""
                            UPDATE dbo.MineralLaboratory
                            SET LastModificationTime = ?,
                                LastModifierUserId = ?,
                                IsDeleted = ?,
                                DeleterUserId = NULL,
                                DeletionTime = NULL,
                                Code = ?,
                                Name = ?,
                                EngName = ?,
                                Describe = ?,
                                Address = ?,
                                Leader = ?,
                                ContactInfo = ?,
                                HasCnas = ?,
                                HasCms = ?
                            WHERE Id = CONVERT(uniqueidentifier, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    false,
                    lab.code,
                    lab.name,
                    lab.engName,
                    lab.describe,
                    lab.address,
                    lab.leader,
                    lab.contactInfo,
                    lab.hasCnas,
                    lab.hasCms,
                    lab.id.toString());
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.MineralLaboratory
                            (Id, CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                             IsDeleted, DeleterUserId, DeletionTime, Code, Name, EngName, Describe,
                             Address, Leader, ContactInfo, HasCnas, HasCms)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                lab.id.toString(),
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                null,
                null,
                false,
                null,
                null,
                lab.code,
                lab.name,
                lab.engName,
                lab.describe,
                lab.address,
                lab.leader,
                lab.contactInfo,
                lab.hasCnas,
                lab.hasCms);
    }

    private void upsertDatabaseSampleType(SampleType type) {
        if (type == null || type.id == null) {
            return;
        }
        if (databaseUuidRowExists("MineralSampleType", type.id)) {
            jdbcTemplate.update("""
                            UPDATE dbo.MineralSampleType
                            SET LastModificationTime = ?,
                                LastModifierUserId = ?,
                                IsDeleted = ?,
                                DeleterUserId = NULL,
                                DeletionTime = NULL,
                                DisplayName = ?,
                                OrgId = ?,
                                OrgName = ?
                            WHERE Id = CONVERT(uniqueidentifier, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    false,
                    type.displayName,
                    type.orgId,
                    type.orgName,
                    type.id.toString());
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.MineralSampleType
                            (Id, CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                             IsDeleted, DeleterUserId, DeletionTime, DisplayName, OrgId, OrgName)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                type.id.toString(),
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                null,
                null,
                false,
                null,
                null,
                type.displayName,
                type.orgId,
                type.orgName);
    }

    private void upsertDatabaseSample(Sample sample) {
        if (sample == null || sample.id == null) {
            return;
        }
        if (databaseUuidRowExists("MineralSample", sample.id)) {
            jdbcTemplate.update("""
                            UPDATE dbo.MineralSample
                            SET LastModificationTime = ?,
                                LastModifierUserId = ?,
                                IsDeleted = ?,
                                DeleterUserId = NULL,
                                DeletionTime = NULL,
                                DisplayName = ?,
                                EngName = ?,
                                Alias = ?,
                                TypeId = CONVERT(uniqueidentifier, ?),
                                TypeName = ?
                            WHERE Id = CONVERT(uniqueidentifier, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    false,
                    sample.displayName,
                    sample.engName,
                    sample.alias,
                    uuidString(sample.typeId),
                    sample.typeName,
                    sample.id.toString());
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.MineralSample
                            (Id, CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                             IsDeleted, DeleterUserId, DeletionTime, DisplayName, EngName, Alias, TypeId, TypeName)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CONVERT(uniqueidentifier, ?), ?)
                        """,
                sample.id.toString(),
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                null,
                null,
                false,
                null,
                null,
                sample.displayName,
                sample.engName,
                sample.alias,
                uuidString(sample.typeId),
                sample.typeName);
    }

    private void upsertDatabaseSubcontractAbility(SubcontractAbility item) {
        if (item == null || item.id == null) {
            return;
        }
        if (databaseUuidRowExists("MineralSubcontractAbility", item.id)) {
            jdbcTemplate.update("""
                            UPDATE dbo.MineralSubcontractAbility
                            SET LastModificationTime = ?,
                                LastModifierUserId = ?,
                                IsDeleted = ?,
                                DeleterUserId = NULL,
                                DeletionTime = NULL,
                                LabName = ?,
                                ContactDetails = ?,
                                TestCategory = ?,
                                CmaOrCnas = ?,
                                Gist = ?,
                                Appraiser = ?,
                                EvaluationResult = ?
                            WHERE Id = CONVERT(uniqueidentifier, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    false,
                    item.labName,
                    item.contactDetails,
                    item.testCategory,
                    item.cmaOrCnas,
                    item.gist,
                    item.appraiser,
                    item.evaluationResult,
                    item.id.toString());
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.MineralSubcontractAbility
                            (Id, CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                             IsDeleted, DeleterUserId, DeletionTime, LabName, ContactDetails, TestCategory,
                             CmaOrCnas, Gist, Appraiser, EvaluationResult)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                item.id.toString(),
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                null,
                null,
                false,
                null,
                null,
                item.labName,
                item.contactDetails,
                item.testCategory,
                item.cmaOrCnas,
                item.gist,
                item.appraiser,
                item.evaluationResult);
    }

    private void softDeleteDatabaseSubcontractAbilitiesByLabNames(Set<String> labNames) {
        List<String> validLabNames = labNames == null ? List.of() : labNames.stream()
                .map(this::safe)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        if (validLabNames.isEmpty()) {
            return;
        }
        String placeholders = validLabNames.stream().map(name -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(true);
        args.add(Timestamp.valueOf(LocalDateTime.now()));
        args.add(1L);
        args.add(Timestamp.valueOf(LocalDateTime.now()));
        args.add(1L);
        args.addAll(validLabNames);
        jdbcTemplate.update("""
                        UPDATE dbo.MineralSubcontractAbility
                        SET IsDeleted = ?,
                            DeletionTime = ?,
                            DeleterUserId = ?,
                            LastModificationTime = ?,
                            LastModifierUserId = ?
                        WHERE IsDeleted = 0 AND LabName IN (%s)
                        """.formatted(placeholders), args.toArray());
    }

    private boolean databaseUuidRowExists(String tableName, UUID id) {
        if (id == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT_BIG(*) FROM dbo." + databaseUuidTableName(tableName)
                        + " WHERE Id = CONVERT(uniqueidentifier, ?)",
                Long.class,
                id.toString());
        return count != null && count > 0;
    }

    private void softDeleteDatabaseUuidRow(String tableName, UUID id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.%s
                        SET IsDeleted = ?,
                            DeletionTime = ?,
                            DeleterUserId = ?,
                            LastModificationTime = ?,
                            LastModifierUserId = ?
                        WHERE Id = CONVERT(uniqueidentifier, ?)
                        """.formatted(databaseUuidTableName(tableName)),
                true,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                id.toString());
    }

    private String databaseUuidTableName(String tableName) {
        return switch (tableName) {
            case "MineralLaboratory" -> "MineralLaboratory";
            case "MineralSampleType" -> "MineralSampleType";
            case "MineralSample" -> "MineralSample";
            case "MineralSubcontractAbility" -> "MineralSubcontractAbility";
            default -> throw new IllegalArgumentException("Unsupported UUID table: " + tableName);
        };
    }

    private void updateDatabaseAbilityTypeReference(SampleType type, String previousName) {
        if (type == null || type.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.MineralAbilityTable
                        SET LastModificationTime = ?,
                            LastModifierUserId = ?,
                            TypeId = CONVERT(uniqueidentifier, ?),
                            TypeName = ?,
                            OrgId = ?,
                            OrgName = ?
                        WHERE IsDeleted = 0
                          AND (TypeId = CONVERT(uniqueidentifier, ?)
                               OR (NULLIF(?, '') IS NOT NULL AND TypeName = ?))
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                type.id.toString(),
                type.displayName,
                type.orgId,
                type.orgName,
                type.id.toString(),
                safe(previousName),
                previousName);
    }

    private void updateDatabaseAbilitySampleReference(Sample sample, String previousName) {
        if (sample == null || sample.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.MineralAbilityTable
                        SET LastModificationTime = ?,
                            LastModifierUserId = ?,
                            SamplingId = CONVERT(uniqueidentifier, ?),
                            SamplingName = ?,
                            TypeId = CONVERT(uniqueidentifier, ?),
                            TypeName = ?
                        WHERE IsDeleted = 0
                          AND (SamplingId = CONVERT(uniqueidentifier, ?)
                               OR (NULLIF(?, '') IS NOT NULL AND SamplingName = ?))
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                sample.id.toString(),
                sample.displayName,
                uuidString(sample.typeId),
                sample.typeName,
                sample.id.toString(),
                safe(previousName),
                previousName);
    }

    private void upsertDatabaseOrgSetting(OrgAbilitySetting setting) {
        if (setting == null) {
            return;
        }
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.OrgAbilityPropertySettings
                        WHERE OrgId = ?
                        """,
                Long.class,
                setting.orgId);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                            UPDATE dbo.OrgAbilityPropertySettings
                            SET Properties = ?,
                                Labs = ?,
                                IsPublic = ?,
                                Description = ?
                            WHERE OrgId = ?
                            """,
                    databaseCsv(setting.propertyName),
                    databaseCsv(setting.lab),
                    setting.isPublic,
                    setting.description,
                    setting.orgId);
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.OrgAbilityPropertySettings
                            (Id, OrgId, Properties, Labs, IsPublic, Description)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(),
                setting.orgId,
                databaseCsv(setting.propertyName),
                databaseCsv(setting.lab),
                setting.isPublic,
                setting.description);
    }

    private String databaseCsv(List<String> values) {
        return list(values).stream()
                .map(this::safe)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void upsertDatabaseFavorite(FavoriteGroup favorite) {
        if (favorite == null || favorite.id == null) {
            return;
        }
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.MyFavorites
                        WHERE Id = CONVERT(uniqueidentifier, ?)
                        """,
                Long.class,
                favorite.id.toString());
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                            UPDATE dbo.MyFavorites
                            SET LastModificationTime = ?,
                                LastModifierUserId = ?,
                                IsDeleted = ?,
                                DeleterUserId = NULL,
                                DeletionTime = NULL,
                                Name = ?,
                                UserId = ?
                            WHERE Id = CONVERT(uniqueidentifier, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    favorite.userId,
                    false,
                    favorite.name,
                    favorite.userId,
                    favorite.id.toString());
        } else {
            jdbcTemplate.update("""
                            INSERT INTO dbo.MyFavorites
                                (Id, CreationTime, CreatorUserId, LastModificationTime, LastModifierUserId,
                                 IsDeleted, DeleterUserId, DeletionTime, Name, UserId)
                            VALUES
                                (CONVERT(uniqueidentifier, ?), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    favorite.id.toString(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    favorite.userId,
                    null,
                    null,
                    false,
                    null,
                    null,
                    favorite.name,
                    favorite.userId);
        }
        syncDatabaseFavoriteItems(favorite);
    }

    private void syncDatabaseFavoriteItems(FavoriteGroup favorite) {
        if (favorite == null || favorite.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        DELETE FROM dbo.MyFavoriteItems
                        WHERE MyFavoriteId = CONVERT(uniqueidentifier, ?) AND UserId = ?
                        """,
                favorite.id.toString(),
                favorite.userId);
        for (UUID abilityId : list(favorite.abilityIds)) {
            if (abilityId == null) {
                continue;
            }
            insertDatabaseFavoriteItem(favorite.id, abilityId, favorite.userId);
        }
    }

    private void softDeleteDatabaseFavorite(UUID favoriteId, long userId) {
        jdbcTemplate.update("""
                        UPDATE dbo.MyFavorites
                        SET IsDeleted = ?,
                            DeletionTime = ?,
                            DeleterUserId = ?,
                            LastModificationTime = ?,
                            LastModifierUserId = ?
                        WHERE Id = CONVERT(uniqueidentifier, ?) AND UserId = ?
                        """,
                true,
                Timestamp.valueOf(LocalDateTime.now()),
                userId,
                Timestamp.valueOf(LocalDateTime.now()),
                userId,
                favoriteId.toString(),
                userId);
        jdbcTemplate.update("""
                        DELETE FROM dbo.MyFavoriteItems
                        WHERE MyFavoriteId = CONVERT(uniqueidentifier, ?) AND UserId = ?
                        """,
                favoriteId.toString(),
                userId);
    }

    private void moveDatabaseFavoriteItem(UUID favoriteId, UUID abilityId, long userId) {
        if (abilityId == null) {
            return;
        }
        deleteDatabaseFavoriteItemsForAbility(abilityId, userId);
        insertDatabaseFavoriteItem(favoriteId, abilityId, userId);
    }

    private void insertDatabaseFavoriteItem(UUID favoriteId, UUID abilityId, long userId) {
        jdbcTemplate.update("""
                        INSERT INTO dbo.MyFavoriteItems
                            (Id, MyFavoriteId, AbilityId, UserId)
                        VALUES
                            (CONVERT(uniqueidentifier, ?), CONVERT(uniqueidentifier, ?),
                             CONVERT(uniqueidentifier, ?), ?)
                        """,
                UUID.randomUUID().toString(),
                uuidString(favoriteId),
                abilityId.toString(),
                userId);
    }

    private void deleteDatabaseFavoriteItemsForAbility(UUID abilityId) {
        if (abilityId == null) {
            return;
        }
        jdbcTemplate.update("""
                        DELETE FROM dbo.MyFavoriteItems
                        WHERE AbilityId = CONVERT(uniqueidentifier, ?)
                        """,
                abilityId.toString());
    }

    private void deleteDatabaseFavoriteItemsForAbility(UUID abilityId, long userId) {
        if (abilityId == null) {
            return;
        }
        jdbcTemplate.update("""
                        DELETE FROM dbo.MyFavoriteItems
                        WHERE AbilityId = CONVERT(uniqueidentifier, ?) AND UserId = ?
                        """,
                abilityId.toString(),
                userId);
    }

    private Optional<String> databaseReplicaPassword(String storedPassword) {
        String stored = safe(storedPassword);
        if (!stored.startsWith(REPLICA_PASSWORD_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(stored.substring(REPLICA_PASSWORD_PREFIX.length()));
    }

    private String databasePasswordValue(String password) {
        return REPLICA_PASSWORD_PREFIX + (safe(password).isBlank() ? "123qwe" : password);
    }

    private OrganizationUnit upsertDatabaseOrganizationUnit(OrganizationUnit input) {
        OrganizationUnit item = input == null ? new OrganizationUnit() : input;
        LocalDateTime now = LocalDateTime.now();
        if (item.id > 0 && databaseLongRowExists("SgsOrganizationUnits", item.id)) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsOrganizationUnits
                            SET ParentId = ?, DisplayName = ?, LastModificationTime = ?, IsDeleted = 0
                            WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                            """,
                    item.parentId,
                    truncateForColumn(safe(item.displayName).isBlank() ? "业务线" + item.id : item.displayName, 128),
                    Timestamp.valueOf(now),
                    item.id);
            return item;
        }
        String displayName = safe(item.displayName).isBlank() ? "业务线" : item.displayName;
        String code = safe(item.code).isBlank() ? nextOrganizationUnitCode() : item.code;
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsOrganizationUnits
                            (Code, CreationTime, CreatorUserId, DisplayName, IsDeleted, ParentId, TenantId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, 0, ?, 1)
                        """,
                Long.class,
                truncateForColumn(code, 95),
                Timestamp.valueOf(now),
                2L,
                truncateForColumn(displayName, 128),
                item.parentId);
        item.id = id == null ? item.id : id;
        item.code = code;
        return item;
    }

    private void updateDatabaseOrganizationUnitParent(Long id, Long parentId) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnits
                        SET ParentId = ?, LastModificationTime = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL) AND IsDeleted = 0
                        """,
                parentId,
                Timestamp.valueOf(LocalDateTime.now()),
                id);
    }

    private void softDeleteDatabaseOrganizationUnit(Long id) {
        if (id == null) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnits
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                now, 2L, id);
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUserOrganizationUnits
                        SET IsDeleted = 1
                        WHERE OrganizationUnitId = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """, id);
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnitRoles
                        SET IsDeleted = 1
                        WHERE OrganizationUnitId = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """, id);
    }

    private String nextOrganizationUnitCode() {
        Long next = jdbcTemplate.queryForObject("SELECT ISNULL(MAX(Id), 0) + 1 FROM dbo.SgsOrganizationUnits", Long.class);
        return String.format("%05d", next == null ? 1 : next);
    }

    private boolean databaseLongRowExists(String tableName, Long id) {
        if (id == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo." + databaseLongTableName(tableName) + " WHERE Id = ?",
                Long.class,
                id);
        return count != null && count > 0;
    }

    private String databaseLongTableName(String tableName) {
        return switch (tableName) {
            case "SgsLanguages" -> "SgsLanguages";
            case "SgsOrganizationUnits" -> "SgsOrganizationUnits";
            case "SgsRoles" -> "SgsRoles";
            case "SgsUsers" -> "SgsUsers";
            default -> throw new IllegalArgumentException("Unsupported table: " + tableName);
        };
    }

    private RoleItem upsertDatabaseRole(RoleItem input, List<String> grantedPermissionNames, RoleItem existing) {
        RoleItem item = input == null ? new RoleItem() : input;
        if (safe(item.displayName).isBlank()) {
            item.displayName = safe(item.name).isBlank() ? "Role" : item.name;
        }
        if (safe(item.name).isBlank()) {
            item.name = item.displayName.replaceAll("\\s+", "");
        }
        if (item.creationTime == null) {
            item.creationTime = existing == null || existing.creationTime == null ? LocalDateTime.now() : existing.creationTime;
        }
        if (item.id != null && databaseLongRowExists("SgsRoles", item.id.longValue())) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsRoles
                            SET DisplayName = ?, IsDefault = ?, IsStatic = ?, LastModificationTime = ?,
                                Name = ?, NormalizedName = ?, IsDeleted = 0
                            WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                            """,
                    truncateForColumn(item.displayName, 128),
                    item.isDefault,
                    item.isStatic,
                    Timestamp.valueOf(LocalDateTime.now()),
                    truncateForColumn(item.name, 32),
                    truncateForColumn(databaseNormalize(item.name), 32),
                    item.id);
        } else {
            Integer id = jdbcTemplate.queryForObject("""
                            INSERT INTO dbo.SgsRoles
                                (ConcurrencyStamp, CreationTime, CreatorUserId, DisplayName, IsDefault, IsDeleted,
                                 IsStatic, Name, NormalizedName, TenantId)
                            OUTPUT INSERTED.Id
                            VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, 1)
                            """,
                    Integer.class,
                    UUID.randomUUID().toString(),
                    Timestamp.valueOf(item.creationTime),
                    2L,
                    truncateForColumn(item.displayName, 128),
                    item.isDefault,
                    item.isStatic,
                    truncateForColumn(item.name, 32),
                    truncateForColumn(databaseNormalize(item.name), 32));
            item.id = id;
        }
        item.grantedPermissionNames = new ArrayList<>(grantedPermissionNames == null ? List.of() : grantedPermissionNames);
        if (item.organizationUnits == null) {
            item.organizationUnits = new ArrayList<>(existing == null ? List.of() : roleOrganizationUnits(existing));
        }
        replaceDatabaseRolePermissions(item.id, item.grantedPermissionNames);
        syncDatabaseRoleOrganizations(item.id, item.organizationUnits);
        return item;
    }

    private void softDeleteDatabaseRole(Integer id) {
        if (id == null) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                        UPDATE dbo.SgsRoles
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE Id = ? AND IsStatic = 0 AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                now, 2L, id);
        jdbcTemplate.update("DELETE FROM dbo.SgsPermissions WHERE RoleId = ? AND TenantId = 1", id);
        jdbcTemplate.update("DELETE FROM dbo.SgsUserRoles WHERE RoleId = ? AND TenantId = 1", id);
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnitRoles
                        SET IsDeleted = 1
                        WHERE RoleId = ? AND TenantId = 1
                        """, id);
    }

    private void replaceDatabaseRolePermissions(Integer roleId, List<String> permissionNames) {
        if (roleId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM dbo.SgsPermissions WHERE RoleId = ? AND TenantId = 1", roleId);
        for (String permissionName : uniqueStrings(permissionNames)) {
            if (safe(permissionName).isBlank()) {
                continue;
            }
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsPermissions
                                (CreationTime, CreatorUserId, Discriminator, IsGranted, Name, TenantId, RoleId, UserId)
                            VALUES (?, ?, 'RolePermissionSetting', 1, ?, 1, ?, NULL)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    truncateForColumn(permissionName, 128),
                    roleId);
        }
    }

    private void replaceDatabaseUserPermissions(Long userId, List<String> permissionNames) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM dbo.SgsPermissions WHERE UserId = ? AND TenantId = 1", userId);
        for (String permissionName : uniqueStrings(permissionNames)) {
            if (safe(permissionName).isBlank()) {
                continue;
            }
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsPermissions
                                (CreationTime, CreatorUserId, Discriminator, IsGranted, Name, TenantId, RoleId, UserId)
                            VALUES (?, ?, 'UserPermissionSetting', 1, ?, 1, NULL, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    truncateForColumn(permissionName, 128),
                    userId);
        }
    }

    private UserItem upsertDatabaseUser(UserItem input, List<String> assignedRoleNames, List<Long> organizationUnits,
                                        List<UUID> labs, String submittedPassword) {
        UserItem item = input == null ? new UserItem() : input;
        LocalDateTime now = LocalDateTime.now();
        if (item.creationTime == null) {
            item.creationTime = now;
        }
        if (safe(item.surname).isBlank()) {
            item.surname = "-";
        }
        if (item.id != null && databaseLongRowExists("SgsUsers", item.id)) {
            String passwordSql = safe(submittedPassword).isBlank() ? "" : ", Password = ?";
            List<Object> args = new ArrayList<>();
            args.add(truncateForColumn(safe(item.emailAddress), 256));
            args.add(item.isActive);
            args.add(item.isEmailConfirmed);
            args.add(item.isLockoutEnabled);
            args.add(item.isPhoneNumberConfirmed);
            args.add(item.isTwoFactorEnabled);
            args.add(Timestamp.valueOf(now));
            args.add(truncateForColumn(safe(item.name), 64));
            args.add(truncateForColumn(databaseNormalize(item.emailAddress), 256));
            args.add(truncateForColumn(databaseNormalize(item.userName), 256));
            args.add(truncateForColumn(item.phoneNumber, 32));
            args.add(item.profilePictureId == null ? null : item.profilePictureId.toString());
            args.add(item.shouldChangePasswordOnNextLogin);
            args.add(truncateForColumn(item.surname, 64));
            args.add(truncateForColumn(safe(item.userName), 256));
            args.add(truncateForColumn(item.engName, 64));
            if (!safe(submittedPassword).isBlank()) {
                args.add(databasePasswordValue(submittedPassword));
            }
            args.add(item.id);
            jdbcTemplate.update("""
                            UPDATE dbo.SgsUsers
                            SET EmailAddress = ?, IsActive = ?, IsEmailConfirmed = ?, IsLockoutEnabled = ?,
                                IsPhoneNumberConfirmed = ?, IsTwoFactorEnabled = ?, LastModificationTime = ?,
                                Name = ?, NormalizedEmailAddress = ?, NormalizedUserName = ?, PhoneNumber = ?,
                                ProfilePictureId = ?, ShouldChangePasswordOnNextLogin = ?, Surname = ?,
                                UserName = ?, EngName = ?%s, IsDeleted = 0
                            WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                            """.formatted(passwordSql), args.toArray());
        } else {
            Long id = jdbcTemplate.queryForObject("""
                            INSERT INTO dbo.SgsUsers
                                (AccessFailedCount, ConcurrencyStamp, CreationTime, CreatorUserId, EmailAddress,
                                 IsActive, IsDeleted, IsEmailConfirmed, IsLockoutEnabled, IsPhoneNumberConfirmed,
                                 IsTwoFactorEnabled, Name, NormalizedEmailAddress, NormalizedUserName, Password,
                                 PhoneNumber, ProfilePictureId, SecurityStamp, ShouldChangePasswordOnNextLogin,
                                 Surname, TenantId, UserName, EngName)
                            OUTPUT INSERTED.Id
                            VALUES (0, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                            """,
                    Long.class,
                    UUID.randomUUID().toString(),
                    Timestamp.valueOf(item.creationTime),
                    2L,
                    truncateForColumn(safe(item.emailAddress), 256),
                    item.isActive,
                    item.isEmailConfirmed,
                    item.isLockoutEnabled,
                    item.isPhoneNumberConfirmed,
                    item.isTwoFactorEnabled,
                    truncateForColumn(safe(item.name), 64),
                    truncateForColumn(databaseNormalize(item.emailAddress), 256),
                    truncateForColumn(databaseNormalize(item.userName), 256),
                    databasePasswordValue(submittedPassword),
                    truncateForColumn(item.phoneNumber, 32),
                    item.profilePictureId == null ? null : item.profilePictureId.toString(),
                    UUID.randomUUID().toString(),
                    item.shouldChangePasswordOnNextLogin,
                    truncateForColumn(item.surname, 64),
                    truncateForColumn(safe(item.userName), 256),
                    truncateForColumn(item.engName, 64));
            item.id = id;
        }
        item.assignedRoleNames = new ArrayList<>(assignedRoleNames == null ? List.of() : assignedRoleNames);
        item.organizationUnits = new ArrayList<>(organizationUnits == null ? List.of() : organizationUnits);
        item.labs = new ArrayList<>(labs == null ? List.of() : labs);
        syncDatabaseUserRoles(item.id, item.assignedRoleNames);
        syncDatabaseUserOrganizations(item.id, item.organizationUnits);
        syncDatabaseUserLabs(item.id, item.labs);
        return item;
    }

    private void updateDatabaseUserPassword(Long userId, String password) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUsers
                        SET Password = ?, LastModificationTime = ?, ShouldChangePasswordOnNextLogin = 0
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                databasePasswordValue(password),
                Timestamp.valueOf(LocalDateTime.now()),
                userId);
    }

    private void updateDatabaseUserLockout(Long userId, boolean lockedOut) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUsers
                        SET LockoutEndDateUtc = ?, AccessFailedCount = ?, LastModificationTime = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                lockedOut ? Timestamp.valueOf(LocalDateTime.now().plusYears(1)) : null,
                lockedOut ? 5 : 0,
                Timestamp.valueOf(LocalDateTime.now()),
                userId);
    }

    private void softDeleteDatabaseUser(Long userId) {
        if (userId == null) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUsers
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                now, 2L, userId);
        jdbcTemplate.update("DELETE FROM dbo.SgsUserRoles WHERE UserId = ? AND TenantId = 1", userId);
        jdbcTemplate.update("DELETE FROM dbo.SgsPermissions WHERE UserId = ? AND TenantId = 1", userId);
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUserOrganizationUnits
                        SET IsDeleted = 1
                        WHERE UserId = ? AND TenantId = 1
                        """, userId);
        jdbcTemplate.update("""
                        UPDATE dbo.MineralLaboratoryUser
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE UserId = ?
                        """, now, 2L, userId);
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUserAccounts
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE UserId = ? AND TenantId = 1
                        """, now, 2L, userId);
        jdbcTemplate.update("""
                        UPDATE dbo.AppUserDelegations
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE (SourceUserId = ? OR TargetUserId = ?) AND (TenantId = 1 OR TenantId IS NULL)
                        """, now, 2L, userId, userId);
        jdbcTemplate.update("""
                        DELETE FROM dbo.AppFriendships
                        WHERE UserId = ? OR FriendUserId = ?
                        """, userId, userId);
        jdbcTemplate.update("""
                        DELETE FROM dbo.AppChatMessages
                        WHERE UserId = ? OR TargetUserId = ?
                        """, userId, userId);
    }

    private void upsertDatabaseUserAccount(UserItem user) {
        if (user == null || user.id == null) {
            return;
        }
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.SgsUserAccounts
                        WHERE UserId = ? AND TenantId = 1
                        """, Long.class, user.id);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsUserAccounts
                            SET EmailAddress = ?, IsDeleted = 0, LastModificationTime = ?,
                                LastModifierUserId = ?, UserName = ?
                            WHERE UserId = ? AND TenantId = 1
                            """,
                    truncateForColumn(user.emailAddress, 256),
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    truncateForColumn(user.userName, 256),
                    user.id);
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsUserAccounts
                            (CreationTime, CreatorUserId, EmailAddress, IsDeleted, TenantId, UserId, UserLinkId, UserName)
                        VALUES (?, ?, ?, 0, 1, ?, NULL, ?)
                        """,
                Timestamp.valueOf(user.creationTime == null ? LocalDateTime.now() : user.creationTime),
                2L,
                truncateForColumn(user.emailAddress, 256),
                user.id,
                truncateForColumn(user.userName, 256));
    }

    private void syncDatabaseLinkedUsers(Long userId) {
        UserItem user = user(userId).orElse(null);
        if (user == null) {
            return;
        }
        upsertDatabaseUserAccount(user);
        List<Long> group = new ArrayList<>();
        group.add(user.id);
        list(user.linkedUserIds).stream()
                .filter(users::containsKey)
                .filter(id -> !group.contains(id))
                .forEach(group::add);
        if (group.size() <= 1) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsUserAccounts
                            SET UserLinkId = NULL, LastModificationTime = ?, LastModifierUserId = ?
                            WHERE UserId = ? AND TenantId = 1
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    user.id);
            return;
        }
        group.stream().map(users::get).filter(Objects::nonNull).forEach(this::upsertDatabaseUserAccount);
        String placeholders = group.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT TOP 1 UserLinkId
                        FROM dbo.SgsUserAccounts
                        WHERE UserId IN (%s) AND TenantId = 1 AND UserLinkId IS NOT NULL
                        ORDER BY UserLinkId
                        """.formatted(placeholders), group.toArray());
        Long linkId = rows.isEmpty() ? null : dbLong(rows.get(0), "UserLinkId");
        if (linkId == null) {
            linkId = jdbcTemplate.queryForObject("""
                            SELECT ISNULL(MAX(COALESCE(UserLinkId, Id)), 0) + 1
                            FROM dbo.SgsUserAccounts
                            """, Long.class);
        }
        for (Long id : group) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsUserAccounts
                            SET UserLinkId = ?, LastModificationTime = ?, LastModifierUserId = ?
                            WHERE UserId = ? AND TenantId = 1
                            """,
                    linkId,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    id);
        }
    }

    private void updateDatabaseUserProfile(UserItem user) {
        if (user == null || user.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUsers
                        SET EmailAddress = ?, IsPhoneNumberConfirmed = ?, LastModificationTime = ?,
                            LastModifierUserId = ?, Name = ?, NormalizedEmailAddress = ?, NormalizedUserName = ?,
                            PhoneNumber = ?, ProfilePictureId = ?, Surname = ?, UserName = ?, EngName = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                truncateForColumn(safe(user.emailAddress), 256),
                user.isPhoneNumberConfirmed,
                Timestamp.valueOf(LocalDateTime.now()),
                2L,
                truncateForColumn(safe(user.name), 64),
                truncateForColumn(databaseNormalize(user.emailAddress), 256),
                truncateForColumn(databaseNormalize(user.userName), 256),
                truncateForColumn(user.phoneNumber, 32),
                user.profilePictureId == null ? null : user.profilePictureId.toString(),
                truncateForColumn(safe(user.surname).isBlank() ? "-" : user.surname, 64),
                truncateForColumn(safe(user.userName), 256),
                truncateForColumn(user.engName, 64),
                user.id);
    }

    private void updateDatabaseUserSecurityFields(UserItem user) {
        if (user == null || user.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUsers
                        SET EmailConfirmationCode = ?, GoogleAuthenticatorKey = ?, IsEmailConfirmed = ?,
                            IsPhoneNumberConfirmed = ?, LastModificationTime = ?, LastModifierUserId = ?,
                            PasswordResetCode = ?, ShouldChangePasswordOnNextLogin = ?, SignInToken = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                truncateForColumn(user.emailConfirmationCode, 328),
                user.googleAuthenticatorKey,
                user.isEmailConfirmed,
                user.isPhoneNumberConfirmed,
                Timestamp.valueOf(LocalDateTime.now()),
                2L,
                truncateForColumn(user.passwordResetCode, 328),
                user.shouldChangePasswordOnNextLogin,
                user.signInToken,
                user.id);
    }

    private void updateDatabaseUserLastLogin(Long userId) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUsers
                        SET LastModificationTime = ?, LastModifierUserId = ?
                        WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                2L,
                userId);
    }

    private long insertDatabaseLoginAttempt(UserLoginAttemptItem item) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsUserLoginAttempts
                            (BrowserInfo, ClientIpAddress, ClientName, CreationTime, Result,
                             TenancyName, TenantId, UserId, UserNameOrEmailAddress)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                        """,
                Long.class,
                truncateForColumn(item.browserInfo, 512),
                truncateForColumn(item.clientIpAddress, 64),
                truncateForColumn(item.clientName, 128),
                databaseTimestamp(item.creationTime),
                loginResultCode(item.result),
                truncateForColumn(item.tenancyName, 64),
                item.userId,
                truncateForColumn(item.userNameOrEmail, 256));
        return id == null ? 0L : id;
    }

    private int loginResultCode(String result) {
        String value = safe(result).toLowerCase(Locale.ROOT);
        if (value.contains("success") || value.contains("成功")) {
            return 1;
        }
        if (value.contains("username") || value.contains("email") || value.contains("user")) {
            return 2;
        }
        if (value.contains("locked")) {
            return 7;
        }
        return 3;
    }

    private String loginResultName(Integer result) {
        return switch (result == null ? 0 : result) {
            case 1 -> "Success";
            case 2 -> "InvalidUserNameOrEmailAddress";
            case 3 -> "InvalidPassword";
            case 4 -> "UserIsNotActive";
            case 5 -> "UserEmailIsNotConfirmed";
            case 7 -> "LockedOut";
            default -> "Unknown";
        };
    }

    private void upsertDatabaseBinaryObject(UUID id, String content) {
        if (id == null) {
            return;
        }
        byte[] bytes = decodeProfilePictureBytes(content);
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.AppBinaryObjects
                        SET Bytes = ?, TenantId = 1
                        WHERE Id = CONVERT(uniqueidentifier, ?)
                        """, bytes, id.toString());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.AppBinaryObjects (Id, Bytes, TenantId)
                        VALUES (CONVERT(uniqueidentifier, ?), ?, 1)
                        """, id.toString(), bytes);
    }

    private byte[] decodeProfilePictureBytes(String content) {
        String value = safe(content).trim();
        if (value.startsWith("data:") && value.contains(",")) {
            value = value.substring(value.indexOf(',') + 1);
        }
        try {
            return Base64.getDecoder().decode(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException ex) {
            return safe(content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private long insertDatabaseUserDelegation(UserDelegation item) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.AppUserDelegations
                            (CreationTime, CreatorUserId, IsDeleted, SourceUserId, TargetUserId,
                             TenantId, StartTime, EndTime)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, 0, ?, ?, ?, ?, ?)
                        """,
                Long.class,
                Timestamp.valueOf(LocalDateTime.now()),
                item.sourceUserId,
                item.sourceUserId,
                item.targetUserId,
                item.tenantId,
                databaseTimestamp(item.startTime),
                databaseTimestamp(item.endTime));
        return id == null ? 0L : id;
    }

    private void softDeleteDatabaseUserDelegation(Long sourceUserId, Long delegationId) {
        if (delegationId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.AppUserDelegations
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE Id = ? AND SourceUserId = ? AND IsDeleted = 0
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                sourceUserId,
                delegationId,
                sourceUserId);
    }

    private void upsertDatabaseFriendship(FriendItem item) {
        if (item == null || item.userId == null || item.friendUserId == null) {
            return;
        }
        String friendUserName = user(item.friendUserId).map(user -> user.userName).orElse(safe(item.friendUserName));
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.AppFriendships
                        SET FriendProfilePictureId = ?, FriendTenancyName = ?, FriendUserName = ?, State = ?
                        WHERE UserId = ? AND ((TenantId IS NULL AND ? IS NULL) OR TenantId = ?)
                          AND FriendUserId = ? AND ((FriendTenantId IS NULL AND ? IS NULL) OR FriendTenantId = ?)
                        """,
                item.friendProfilePictureId == null ? null : item.friendProfilePictureId.toString(),
                item.friendTenancyName,
                truncateForColumn(friendUserName, 256),
                item.state,
                item.userId,
                item.tenantId,
                item.tenantId,
                item.friendUserId,
                item.friendTenantId,
                item.friendTenantId);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.AppFriendships
                            (CreationTime, FriendProfilePictureId, FriendTenancyName, FriendTenantId,
                             FriendUserId, FriendUserName, State, TenantId, UserId)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                databaseTimestamp(item.creationTime),
                item.friendProfilePictureId == null ? null : item.friendProfilePictureId.toString(),
                item.friendTenancyName,
                item.friendTenantId,
                item.friendUserId,
                truncateForColumn(friendUserName, 256),
                item.state,
                item.tenantId,
                item.userId);
    }

    private void updateDatabaseFriendshipState(Long userId, Integer tenantId, Long friendUserId,
                                               Integer friendTenantId, int state) {
        jdbcTemplate.update("""
                        UPDATE dbo.AppFriendships
                        SET State = ?
                        WHERE UserId = ? AND ((TenantId IS NULL AND ? IS NULL) OR TenantId = ?)
                          AND FriendUserId = ? AND ((FriendTenantId IS NULL AND ? IS NULL) OR FriendTenantId = ?)
                        """,
                state, userId, tenantId, tenantId, friendUserId, friendTenantId, friendTenantId);
    }

    private long insertDatabaseChatMessage(ChatMessageItem item) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.AppChatMessages
                            (CreationTime, Message, ReadState, Side, TargetTenantId, TargetUserId,
                             TenantId, UserId, SharedMessageId, ReceiverReadState)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.class,
                databaseTimestamp(item.creationTime),
                safe(item.message),
                item.readState,
                item.side,
                item.targetTenantId,
                item.targetUserId,
                item.tenantId,
                item.userId,
                databaseUuid(item.sharedMessageId),
                item.receiverReadState);
        return id == null ? 0L : id;
    }

    private void updateDatabaseChatMessageReadState(ChatMessageItem item) {
        if (item == null || item.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.AppChatMessages
                        SET ReadState = ?, ReceiverReadState = ?
                        WHERE Id = ?
                        """, item.readState, item.receiverReadState, item.id);
    }

    private void syncDatabaseUserRoles(Long userId, List<String> roleNames) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM dbo.SgsUserRoles WHERE UserId = ? AND TenantId = 1", userId);
        for (String roleName : uniqueStrings(roleNames)) {
            roleByName(roleName).ifPresent(role -> jdbcTemplate.update("""
                            INSERT INTO dbo.SgsUserRoles
                                (CreationTime, CreatorUserId, RoleId, TenantId, UserId)
                            VALUES (?, ?, ?, 1, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    role.id,
                    userId));
        }
    }

    private void syncDatabaseUserOrganizations(Long userId, List<Long> orgIds) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUserOrganizationUnits
                        SET IsDeleted = 1
                        WHERE UserId = ? AND TenantId = 1
                        """, userId);
        for (Long orgId : uniqueLongs(orgIds)) {
            addDatabaseUserOrganization(orgId, userId);
        }
    }

    private void addDatabaseUserOrganization(Long orgId, Long userId) {
        if (orgId == null || userId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUserOrganizationUnits
                        SET IsDeleted = 0
                        WHERE UserId = ? AND OrganizationUnitId = ? AND TenantId = 1
                        """, userId, orgId);
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*) FROM dbo.SgsUserOrganizationUnits
                        WHERE UserId = ? AND OrganizationUnitId = ? AND TenantId = 1 AND IsDeleted = 0
                        """, Long.class, userId, orgId);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsUserOrganizationUnits
                                (CreationTime, CreatorUserId, OrganizationUnitId, TenantId, UserId, IsDeleted)
                            VALUES (?, ?, ?, 1, ?, 0)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    orgId,
                    userId);
        }
    }

    private void softDeleteDatabaseUserOrganization(Long orgId, Long userId) {
        if (orgId == null || userId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsUserOrganizationUnits
                        SET IsDeleted = 1
                        WHERE UserId = ? AND OrganizationUnitId = ? AND TenantId = 1
                        """, userId, orgId);
    }

    private void syncDatabaseUserLabs(Long userId, List<UUID> labIds) {
        if (userId == null) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                        UPDATE dbo.MineralLaboratoryUser
                        SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                        WHERE UserId = ?
                        """, now, 2L, userId);
        for (UUID labId : uniqueUuids(labIds)) {
            addDatabaseUserLab(userId, labId);
        }
    }

    private void addDatabaseUserLab(Long userId, UUID labId) {
        if (userId == null || labId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.MineralLaboratoryUser
                        SET IsDeleted = 0, DeletionTime = NULL, DeleterUserId = NULL
                        WHERE UserId = ? AND LabId = CONVERT(uniqueidentifier, ?)
                        """, userId, labId.toString());
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*) FROM dbo.MineralLaboratoryUser
                        WHERE UserId = ? AND LabId = CONVERT(uniqueidentifier, ?) AND IsDeleted = 0
                        """, Long.class, userId, labId.toString());
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                            INSERT INTO dbo.MineralLaboratoryUser
                                (Id, CreationTime, CreatorUserId, IsDeleted, LabId, UserId)
                            VALUES (CONVERT(uniqueidentifier, ?), ?, ?, 0, CONVERT(uniqueidentifier, ?), ?)
                            """,
                    UUID.randomUUID().toString(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    labId.toString(),
                    userId);
        }
    }

    private void syncDatabaseRoleOrganizations(Integer roleId, List<Long> orgIds) {
        if (roleId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnitRoles
                        SET IsDeleted = 1
                        WHERE RoleId = ? AND TenantId = 1
                        """, roleId);
        for (Long orgId : uniqueLongs(orgIds)) {
            addDatabaseRoleOrganization(orgId, roleId);
        }
    }

    private void addDatabaseRoleOrganization(Long orgId, Integer roleId) {
        if (orgId == null || roleId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnitRoles
                        SET IsDeleted = 0
                        WHERE RoleId = ? AND OrganizationUnitId = ? AND TenantId = 1
                        """, roleId, orgId);
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT_BIG(*) FROM dbo.SgsOrganizationUnitRoles
                        WHERE RoleId = ? AND OrganizationUnitId = ? AND TenantId = 1 AND IsDeleted = 0
                        """, Long.class, roleId, orgId);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsOrganizationUnitRoles
                                (CreationTime, CreatorUserId, TenantId, RoleId, OrganizationUnitId, IsDeleted)
                            VALUES (?, ?, 1, ?, ?, 0)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    2L,
                    roleId,
                    orgId);
        }
    }

    private void softDeleteDatabaseRoleOrganization(Long orgId, Integer roleId) {
        if (orgId == null || roleId == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsOrganizationUnitRoles
                        SET IsDeleted = 1
                        WHERE RoleId = ? AND OrganizationUnitId = ? AND TenantId = 1
                        """, roleId, orgId);
    }

    private String databaseNormalize(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    public int deleteAbilitiesByOrgName(String orgName) {
        return deleteAbilitiesByOrgName(orgName, 1L);
    }

    public int deleteAbilitiesByOrgName(String orgName, Long actorUserId) {
        long userId = actorUserId == null ? 1L : actorUserId;
        if (orgName == null || orgName.isBlank()) {
            return 0;
        }
        List<Ability> removedItems = abilities.values().stream()
                .filter(item -> Objects.equals(item.orgName, orgName))
                .toList();
        removedItems.forEach(item -> {
            if (databaseStoreMode && !loadingDatabaseState) {
                softDeleteDatabaseAbility(item.id);
                deleteDatabaseFavoriteItemsForAbility(item.id);
            }
            abilities.remove(item.id);
            defaultFavoriteAbilityIdsByUser.values().forEach(abilityIds -> abilityIds.remove(item.id));
            favorites.values().forEach(group -> group.abilityIds.remove(item.id));
            recordEntityChange(userId, item.id.toString(), ABILITY_ENTITY, "能力表", 2);
        });
        if (!removedItems.isEmpty()) {
            audit(userId, "AbilityAppService", "DeleteAll", orgName + ":" + removedItems.size());
            persist();
        }
        return removedItems.size();
    }

    public List<Laboratory> labs() {
        List<String> configuredOrder = orgSettings.values().stream()
                .sorted(Comparator.comparingLong(setting -> setting.orgId))
                .flatMap(setting -> setting.lab.stream())
                .filter(code -> !safe(code).isBlank())
                .distinct()
                .toList();
        Map<String, Integer> order = new HashMap<>();
        for (int index = 0; index < configuredOrder.size(); index++) {
            order.put(configuredOrder.get(index), index);
        }
        return labs.values().stream()
                .sorted(Comparator.comparingInt((Laboratory lab) -> order.getOrDefault(safe(lab.code), Integer.MAX_VALUE))
                        .thenComparing(lab -> safe(lab.code)))
                .toList();
    }

    public Laboratory saveLab(Laboratory input) {
        requireUniqueLabCode(input.id, input.code);
        if (input.id == null) {
            input.id = UUID.randomUUID();
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseLab(input);
        }
        labs.put(input.id, input);
        persist();
        return input;
    }

    public Optional<Laboratory> lab(UUID id) {
        return Optional.ofNullable(id == null ? null : labs.get(id));
    }

    public void deleteLab(String id) {
        parseUuid(id).ifPresent(uuid -> {
            if (databaseStoreMode && !loadingDatabaseState) {
                softDeleteDatabaseUuidRow("MineralLaboratory", uuid);
            }
            labs.remove(uuid);
            persist();
        });
    }

    public List<SampleType> sampleTypes() {
        return new ArrayList<>(sampleTypes.values());
    }

    public List<SampleType> sampleTypes(Long orgId) {
        return sampleTypes.values().stream()
                .filter(item -> orgId == null || Objects.equals(item.orgId, orgId))
                .collect(Collectors.toList());
    }

    public Optional<SampleType> sampleType(UUID id) {
        return Optional.ofNullable(id == null ? null : sampleTypes.get(id));
    }

    public SampleType saveSampleType(SampleType input) {
        if (input == null) {
            throw new IllegalArgumentException("样品类型不能为空");
        }
        requireUniqueSampleTypeName(input.id, input.displayName);
        OrganizationUnit org = orgUnits.stream()
                .filter(item -> Objects.equals(item.id, input.orgId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("选择业务线不存在"));
        String previousName = sampleType(input.id).map(item -> item.displayName).orElse(null);
        if (input.id == null) {
            input.id = UUID.randomUUID();
        }
        input.orgName = org.displayName;
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSampleType(input);
            updateDatabaseAbilityTypeReference(input, previousName);
        }
        sampleTypes.put(input.id, input);
        UUID typeId = input.id;
        samples.values().stream()
                .filter(item -> Objects.equals(item.typeId, typeId))
                .forEach(item -> item.typeName = input.displayName);
        abilities.values().stream()
                .filter(item -> Objects.equals(item.typeId, typeId) || equalsText(item.typeName, previousName))
                .forEach(item -> {
                    item.typeId = typeId;
                    item.typeName = input.displayName;
                });
        audit(null, "SampleTypeAppService", "CreateOrUpdate", input.displayName);
        persist();
        return input;
    }

    public void deleteSampleType(String id) {
        parseUuid(id).ifPresent(uuid -> {
            SampleType removed = sampleTypes.remove(uuid);
            if (removed != null) {
                if (databaseStoreMode && !loadingDatabaseState) {
                    softDeleteDatabaseUuidRow("MineralSampleType", uuid);
                }
                audit(null, "SampleTypeAppService", "DeleteSampleType", removed.displayName);
                persist();
            }
        });
    }

    public List<Sample> samples(UUID typeId) {
        return samples.values().stream()
                .filter(item -> typeId == null || Objects.equals(item.typeId, typeId))
                .collect(Collectors.toList());
    }

    public Optional<Sample> sample(UUID id) {
        return Optional.ofNullable(id == null ? null : samples.get(id));
    }

    public Sample saveSample(Sample input) {
        if (input == null) {
            throw new IllegalArgumentException("样品不能为空");
        }
        requireUniqueSampleName(input.id, input.displayName);
        String previousName = sample(input.id).map(item -> item.displayName).orElse(null);
        if (input.id == null) {
            input.id = UUID.randomUUID();
        }
        if (input.typeId != null) {
            sampleType(input.typeId).ifPresent(type -> {
                input.typeName = type.displayName;
                abilities.values().stream()
                        .filter(item -> Objects.equals(item.samplingId, input.id) || equalsText(item.samplingName, previousName))
                        .forEach(item -> {
                            item.samplingId = input.id;
                            item.samplingName = input.displayName;
                            item.typeId = type.id;
                            item.typeName = type.displayName;
                        });
            });
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSample(input);
            updateDatabaseAbilitySampleReference(input, previousName);
        }
        samples.put(input.id, input);
        audit(null, "SampleAppService", "CreateOrUpdate", input.displayName);
        persist();
        return input;
    }

    public void deleteSample(String id) {
        parseUuid(id).ifPresent(uuid -> {
            Sample removed = samples.remove(uuid);
            if (removed != null) {
                if (databaseStoreMode && !loadingDatabaseState) {
                    softDeleteDatabaseUuidRow("MineralSample", uuid);
                }
                audit(null, "SampleAppService", "DeleteSample", removed.displayName);
                persist();
            }
        });
    }

    public List<NameValueItem> orgTypeList(Long orgId) {
        return abilities.values().stream()
                .filter(item -> orgId == null || Objects.equals(item.orgId, orgId))
                .map(item -> safe(item.typeName))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .map(value -> nameValue(value, value))
                .toList();
    }

    public List<MyOrgSettingDto> myOrgSettings(Long userId) {
        List<Long> orgIds = user(userId)
                .map(user -> user.organizationUnits)
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> orgUnits.stream().map(item -> item.id).toList());
        List<MyOrgSettingDto> settings = orgUnits.stream()
                .filter(org -> orgIds.contains(org.id))
                .map(org -> toMyOrgSetting(org, orgSetting(org.id)))
                .toList();
        List<MyOrgSettingDto> productionSettings = settings.stream()
                .filter(item -> PRODUCTION_BUSINESS_LINES.stream().anyMatch(name -> equalsText(name, item.orgName)))
                .sorted(Comparator.comparingInt(item -> productionBusinessLineIndex(item.orgName)))
                .toList();
        if (!productionSettings.isEmpty()) {
            return productionSettings;
        }
        return settings.stream()
                .sorted(Comparator.comparing(item -> safe(item.orgName)))
                .toList();
    }

    public List<OrganizationUnit> orgUnits() {
        return orgUnits.stream()
                .map(this::decorateOrganizationUnit)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public OrganizationUnit saveOrganizationUnit(OrganizationUnit input) {
        if (databaseStoreMode && !loadingDatabaseState) {
            input = upsertDatabaseOrganizationUnit(input);
        }
        if (input.id <= 0) {
            long nextId = orgUnits.stream().map(item -> item.id).max(Long::compareTo).orElse(0L) + 1;
            input.id = nextId;
            orgUnits.add(input);
        } else {
            long targetId = input.id;
            OrganizationUnit existing = orgUnits.stream()
                    .filter(item -> Objects.equals(item.id, targetId))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                orgUnits.add(input);
            } else {
                existing.parentId = input.parentId;
                existing.displayName = safe(input.displayName).isBlank() ? existing.displayName : input.displayName;
                input = existing;
            }
        }
        orgUnits.sort(Comparator.comparing(item -> item.id));
        persist();
        return decorateOrganizationUnit(input);
    }

    public OrganizationUnit moveOrganizationUnit(Long id, Long parentId) {
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseOrganizationUnitParent(id, parentId);
        }
        orgUnits.stream().filter(item -> Objects.equals(item.id, id)).findFirst().ifPresent(item -> item.parentId = parentId);
        persist();
        return orgUnits.stream().filter(item -> Objects.equals(item.id, id)).findFirst()
                .map(this::decorateOrganizationUnit)
                .orElse(null);
    }

    public void deleteOrganizationUnit(Long id) {
        if (id == null || id == 1L) {
            return;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            softDeleteDatabaseOrganizationUnit(id);
        }
        orgUnits.removeIf(item -> Objects.equals(item.id, id));
        users.values().forEach(user -> user.organizationUnits.remove(id));
        roles.values().forEach(role -> roleOrganizationUnits(role).remove(id));
        persist();
    }

    public List<PermissionItem> permissions() {
        return permissions;
    }

    public List<FeatureItem> features() {
        return features.values().stream()
                .sorted(Comparator.comparing(item -> safe(item.name)))
                .toList();
    }

    public List<EditionItem> editions() {
        return editions.values().stream()
                .sorted(Comparator.comparing(item -> item.id))
                .map(this::decorateEdition)
                .toList();
    }

    public Optional<EditionItem> edition(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(editions.get(id)).map(this::decorateEdition);
    }

    public EditionItem saveEdition(EditionItem input, List<NameValueItem> featureValues) {
        if (input.id == null && !databaseStoreMode) {
            input.id = editions.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
        }
        if (safe(input.displayName).isBlank()) {
            input.displayName = "Edition " + input.id;
        }
        input.name = safe(input.name).isBlank() ? input.displayName.replaceAll("\\s+", "") : input.name;
        input.featureValues = mergeFeatureValues(input.featureValues, featureValues);
        input.isFree = isFreeEdition(input);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseEdition(input);
        }
        editions.put(input.id, input);
        audit(1L, "EditionAppService", "CreateOrUpdateEdition", input.displayName);
        persist();
        return decorateEdition(input);
    }

    public void deleteEdition(Integer id) {
        if (id == null || tenants.values().stream().anyMatch(item -> Objects.equals(item.editionId, id))) {
            return;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            softDeleteDatabaseEdition(id);
        }
        editions.remove(id);
        persist();
    }

    public void moveTenantsToAnotherEdition(Integer sourceEditionId, Integer targetEditionId) {
        tenants.values().stream()
                .filter(item -> Objects.equals(item.editionId, sourceEditionId))
                .forEach(item -> item.editionId = targetEditionId);
        tenants.values().forEach(this::decorateTenant);
        if (databaseStoreMode && !loadingDatabaseState) {
            moveDatabaseTenantsToEdition(sourceEditionId, targetEditionId);
        }
        audit(1L, "EditionAppService", "MoveTenantsToAnotherEdition", sourceEditionId + " -> " + targetEditionId);
        persist();
    }

    public int tenantCount(Integer editionId) {
        return (int) tenants.values().stream()
                .filter(item -> Objects.equals(item.editionId, editionId))
                .count();
    }

    public PageResult<TenantItem> tenants(String filter, Integer editionId, boolean editionSpecified,
                                          int skipCount, int maxResultCount) {
        return tenants(filter, editionId, editionSpecified, skipCount, maxResultCount, null);
    }

    public PageResult<TenantItem> tenants(String filter, Integer editionId, boolean editionSpecified,
                                          int skipCount, int maxResultCount, String sorting) {
        List<TenantItem> filtered = tenants.values().stream()
                .map(this::decorateTenant)
                .filter(item -> contains(String.join(" ", safe(item.tenancyName), safe(item.name),
                        safe(item.editionDisplayName), safe(item.adminEmailAddress)), filter))
                .filter(item -> !editionSpecified || Objects.equals(item.editionId, editionId))
                .sorted(tenantComparator(sorting))
                .toList();
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    public Optional<TenantItem> tenant(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(tenants.get(id)).map(this::decorateTenant);
    }

    public TenantItem createTenant(TenantItem input) {
        if (!databaseStoreMode) {
            input.id = tenants.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
        }
        if (safe(input.tenancyName).isBlank()) {
            input.tenancyName = "tenant" + input.id;
        }
        if (safe(input.name).isBlank()) {
            input.name = input.tenancyName;
        }
        if (safe(input.adminEmailAddress).isBlank()) {
            input.adminEmailAddress = "admin@" + input.tenancyName + ".local";
        }
        input.creationTime = LocalDateTime.now().toString();
        input.featureValues = defaultFeatureValues();
        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseTenant(input);
        }
        tenants.put(input.id, decorateTenant(input));
        audit(1L, "TenantAppService", "CreateTenant", input.tenancyName);
        recordEntityChange(1L, String.valueOf(input.id), TENANT_ENTITY, "租户", 0,
                propertyChange("TenancyName", "System.String", null, input.tenancyName),
                propertyChange("Name", "System.String", null, input.name),
                propertyChange("IsActive", "System.Boolean", null, String.valueOf(input.isActive)));
        persist();
        return decorateTenant(input);
    }

    public TenantItem updateTenant(TenantItem input) {
        TenantItem existing = tenant(input.id).orElse(input);
        String oldTenancyName = existing.tenancyName;
        String oldName = existing.name;
        String oldEditionId = existing.editionId == null ? null : String.valueOf(existing.editionId);
        String oldActive = String.valueOf(existing.isActive);
        existing.tenancyName = safe(input.tenancyName).isBlank() ? existing.tenancyName : input.tenancyName;
        existing.name = safe(input.name).isBlank() ? existing.name : input.name;
        existing.connectionString = input.connectionString;
        existing.editionId = input.editionId;
        existing.isActive = input.isActive;
        existing.subscriptionEndDateUtc = input.subscriptionEndDateUtc;
        if (input.subscriptionPaymentType != null) {
            existing.subscriptionPaymentType = input.subscriptionPaymentType;
        }
        existing.isInTrialPeriod = input.isInTrialPeriod;
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseTenant(existing);
        }
        tenants.put(existing.id, decorateTenant(existing));
        audit(1L, "TenantAppService", "UpdateTenant", existing.tenancyName);
        recordEntityChange(1L, String.valueOf(existing.id), TENANT_ENTITY, "租户", 1,
                propertyChange("TenancyName", "System.String", oldTenancyName, existing.tenancyName),
                propertyChange("Name", "System.String", oldName, existing.name),
                propertyChange("EditionId", "System.Nullable`1[System.Int32]", oldEditionId,
                        existing.editionId == null ? null : String.valueOf(existing.editionId)),
                propertyChange("IsActive", "System.Boolean", oldActive, String.valueOf(existing.isActive)));
        persist();
        return decorateTenant(existing);
    }

    public void deleteTenant(Integer id) {
        if (id == null || id == 1) {
            return;
        }
        TenantItem removed = tenants.remove(id);
        if (removed != null) {
            if (databaseStoreMode && !loadingDatabaseState) {
                softDeleteDatabaseTenant(id);
            }
            audit(1L, "TenantAppService", "DeleteTenant", removed.tenancyName);
            recordEntityChange(1L, String.valueOf(id), TENANT_ENTITY, "租户", 2,
                    propertyChange("TenancyName", "System.String", removed.tenancyName, null),
                    propertyChange("Name", "System.String", removed.name, null));
            persist();
        }
    }

    public List<NameValueItem> tenantFeatureValues(Integer tenantId) {
        TenantItem tenant = tenant(tenantId).orElse(null);
        return nameValues(tenant == null ? defaultFeatureValues() : tenant.featureValues);
    }

    public void updateTenantFeatures(Integer tenantId, List<NameValueItem> featureValues) {
        tenant(tenantId).ifPresent(item -> {
            item.featureValues = mergeFeatureValues(item.featureValues, featureValues);
            if (databaseStoreMode && !loadingDatabaseState) {
                replaceDatabaseFeatureValues(null, tenantId, item.featureValues);
            }
            tenants.put(item.id, item);
            persist();
        });
    }

    public void resetTenantFeatures(Integer tenantId) {
        tenant(tenantId).ifPresent(item -> {
            item.featureValues = defaultFeatureValues();
            if (databaseStoreMode && !loadingDatabaseState) {
                replaceDatabaseFeatureValues(null, tenantId, item.featureValues);
            }
            tenants.put(item.id, item);
            persist();
        });
    }

    private void upsertDatabaseEdition(EditionItem item) {
        if (item.id != null && databaseEditionExists(item.id)) {
            jdbcTemplate.update("""
                            UPDATE dbo.SgsEditions
                               SET DisplayName = ?, Name = ?, AnnualPrice = ?, ExpiringEditionId = ?,
                                   MonthlyPrice = ?, TrialDayCount = ?, WaitingDayAfterExpire = ?,
                                   DailyPrice = ?, WeeklyPrice = ?, LastModificationTime = ?, LastModifierUserId = ?
                             WHERE Id = ? AND IsDeleted = 0
                            """,
                    truncateForColumn(safe(item.displayName), 64),
                    truncateForColumn(safe(item.name), 32),
                    item.annualPrice,
                    item.expiringEditionId,
                    item.monthlyPrice,
                    item.trialDayCount,
                    item.waitingDayAfterExpire,
                    item.dailyPrice,
                    item.weeklyPrice,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    item.id);
        } else {
            Integer id = jdbcTemplate.queryForObject("""
                            INSERT INTO dbo.SgsEditions
                                (CreationTime, CreatorUserId, DisplayName, IsDeleted, Name, Discriminator,
                                 AnnualPrice, ExpiringEditionId, MonthlyPrice, TrialDayCount,
                                 WaitingDayAfterExpire, DailyPrice, WeeklyPrice)
                            OUTPUT INSERTED.Id
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Integer.class,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    truncateForColumn(safe(item.displayName), 64),
                    false,
                    truncateForColumn(safe(item.name), 32),
                    "SubscribableEdition",
                    item.annualPrice,
                    item.expiringEditionId,
                    item.monthlyPrice,
                    item.trialDayCount,
                    item.waitingDayAfterExpire,
                    item.dailyPrice,
                    item.weeklyPrice);
            item.id = id;
        }
        replaceDatabaseFeatureValues(item.id, null, item.featureValues);
    }

    private boolean databaseEditionExists(Integer id) {
        if (id == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.SgsEditions WHERE Id = ? AND IsDeleted = 0",
                Long.class,
                id);
        return count != null && count > 0;
    }

    private void softDeleteDatabaseEdition(Integer id) {
        jdbcTemplate.update("""
                        UPDATE dbo.SgsEditions
                           SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                         WHERE Id = ? AND IsDeleted = 0
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                id);
        jdbcTemplate.update("DELETE FROM dbo.SgsFeatures WHERE EditionId = ? AND TenantId IS NULL", id);
    }

    private void moveDatabaseTenantsToEdition(Integer sourceEditionId, Integer targetEditionId) {
        jdbcTemplate.update("""
                        UPDATE dbo.SgsTenants
                           SET EditionId = ?, LastModificationTime = ?, LastModifierUserId = ?
                         WHERE EditionId = ? AND IsDeleted = 0
                        """,
                targetEditionId,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                sourceEditionId);
    }

    private void insertDatabaseTenant(TenantItem item) {
        Integer id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsTenants
                            (ConnectionString, CreationTime, CreatorUserId, EditionId, IsActive, IsDeleted,
                             Name, TenancyName, IsInTrialPeriod, SubscriptionEndDateUtc, SubscriptionPaymentType,
                             LogoId, LogoFileType, CustomCssId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Integer.class,
                truncateForColumn(item.connectionString, 1024),
                databaseTimestamp(item.creationTime),
                1L,
                item.editionId,
                item.isActive,
                false,
                truncateForColumn(safe(item.name), 128),
                truncateForColumn(safe(item.tenancyName), 64),
                item.isInTrialPeriod,
                databaseNullableTimestamp(item.subscriptionEndDateUtc),
                Optional.ofNullable(item.subscriptionPaymentType).orElse(0),
                databaseUuid(item.logoId),
                truncateForColumn(item.logoFileType, 64),
                databaseUuid(item.customCssId));
        item.id = id;
        replaceDatabaseFeatureValues(null, item.id, item.featureValues);
    }

    private void updateDatabaseTenant(TenantItem item) {
        if (item == null || item.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsTenants
                           SET ConnectionString = ?, EditionId = ?, IsActive = ?, LastModificationTime = ?,
                               LastModifierUserId = ?, Name = ?, TenancyName = ?, IsInTrialPeriod = ?,
                               SubscriptionEndDateUtc = ?, SubscriptionPaymentType = ?, LogoId = ?,
                               LogoFileType = ?, CustomCssId = ?
                         WHERE Id = ? AND IsDeleted = 0
                        """,
                truncateForColumn(item.connectionString, 1024),
                item.editionId,
                item.isActive,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                truncateForColumn(safe(item.name), 128),
                truncateForColumn(safe(item.tenancyName), 64),
                item.isInTrialPeriod,
                databaseNullableTimestamp(item.subscriptionEndDateUtc),
                Optional.ofNullable(item.subscriptionPaymentType).orElse(0),
                databaseUuid(item.logoId),
                truncateForColumn(item.logoFileType, 64),
                databaseUuid(item.customCssId),
                item.id);
    }

    private void softDeleteDatabaseTenant(Integer id) {
        jdbcTemplate.update("""
                        UPDATE dbo.SgsTenants
                           SET IsDeleted = 1, DeletionTime = ?, DeleterUserId = ?
                         WHERE Id = ? AND IsDeleted = 0
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                id);
        jdbcTemplate.update("DELETE FROM dbo.SgsFeatures WHERE TenantId = ? AND EditionId IS NULL", id);
    }

    private void insertDatabaseSubscriptionPayment(SubscriptionPaymentItem item) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.AppSubscriptionPayments
                            (Amount, CreationTime, CreatorUserId, DayCount, EditionId, Gateway, IsDeleted,
                             SuccessUrl, PaymentPeriodType, Status, TenantId, InvoiceNo, Description, ErrorUrl,
                             ExternalPaymentId, IsRecurring, EditionPaymentType)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.class,
                amount(item.amount),
                databaseTimestamp(item.creationTime),
                1L,
                item.dayCount,
                item.editionId,
                item.gateway,
                false,
                item.successUrl,
                item.paymentPeriodType,
                item.status,
                item.tenantId,
                item.invoiceNo,
                item.description,
                item.errorUrl,
                item.externalPaymentId,
                item.isRecurring,
                item.editionPaymentType);
        item.id = id;
    }

    private void updateDatabaseSubscriptionPayment(SubscriptionPaymentItem item) {
        if (item == null || item.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.AppSubscriptionPayments
                           SET Amount = ?, DayCount = ?, EditionId = ?, Gateway = ?, LastModificationTime = ?,
                               LastModifierUserId = ?, SuccessUrl = ?, PaymentPeriodType = ?, Status = ?,
                               TenantId = ?, InvoiceNo = ?, Description = ?, ErrorUrl = ?, ExternalPaymentId = ?,
                               IsRecurring = ?, EditionPaymentType = ?
                         WHERE Id = ? AND IsDeleted = 0
                        """,
                amount(item.amount),
                item.dayCount,
                item.editionId,
                item.gateway,
                Timestamp.valueOf(LocalDateTime.now()),
                1L,
                item.successUrl,
                item.paymentPeriodType,
                item.status,
                item.tenantId,
                item.invoiceNo,
                item.description,
                item.errorUrl,
                item.externalPaymentId,
                item.isRecurring,
                item.editionPaymentType,
                item.id);
    }

    private void insertDatabaseInvoice(InvoiceItem invoice) {
        Integer id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.AppInvoices
                            (InvoiceDate, InvoiceNo, TenantAddress, TenantLegalName, TenantTaxNo)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?)
                        """,
                Integer.class,
                databaseTimestamp(invoice.invoiceDate),
                null,
                addressText(invoice.tenantAddress),
                invoice.tenantLegalName,
                invoice.tenantTaxNo);
        invoice.id = id == null ? null : id.longValue();
        invoice.invoiceNo = "INV-" + String.format("%05d", invoice.id == null ? 0 : invoice.id);
        jdbcTemplate.update("UPDATE dbo.AppInvoices SET InvoiceNo = ? WHERE Id = ?",
                invoice.invoiceNo,
                id);
    }

    private void replaceDatabaseFeatureValues(Integer editionId, Integer tenantId, Map<String, String> featureValues) {
        if (editionId == null && tenantId == null) {
            return;
        }
        if (editionId != null) {
            jdbcTemplate.update("DELETE FROM dbo.SgsFeatures WHERE EditionId = ? AND TenantId IS NULL", editionId);
        } else {
            jdbcTemplate.update("DELETE FROM dbo.SgsFeatures WHERE TenantId = ? AND EditionId IS NULL", tenantId);
        }
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(featureValues == null ? Map.of() : featureValues).entrySet()) {
            if (safe(entry.getKey()).isBlank()) {
                continue;
            }
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsFeatures
                                (CreationTime, CreatorUserId, Discriminator, Name, Value, EditionId, TenantId)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    1L,
                    editionId == null ? "TenantFeatureSetting" : "EditionFeatureSetting",
                    truncateForColumn(entry.getKey(), 128),
                    truncateForColumn(safe(entry.getValue()), 2000),
                    editionId,
                    tenantId);
        }
    }

    private Timestamp databaseTimestamp(String value) {
        return Timestamp.valueOf(parseFlexibleDateTime(value).orElse(LocalDateTime.now()));
    }

    private Timestamp databaseNullableTimestamp(String value) {
        return parseFlexibleDateTime(value).map(Timestamp::valueOf).orElse(null);
    }

    private UUID databaseUuid(String value) {
        try {
            String text = safe(value).trim();
            return text.isBlank() ? null : UUID.fromString(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public void unlockTenantAdmin(Integer tenantId) {
        tenant(tenantId).ifPresent(item -> audit(1L, "TenantAppService", "UnlockTenantAdmin", item.tenancyName));
    }

    public BigDecimal editionPaymentAmount(Integer editionId, Integer paymentPeriodType) {
        EditionItem edition = edition(editionId).orElse(null);
        if (edition == null) {
            return BigDecimal.ZERO;
        }
        return switch (paymentPeriodType == null ? 30 : paymentPeriodType) {
            case 1 -> amount(edition.dailyPrice);
            case 7 -> amount(edition.weeklyPrice);
            case 365 -> amount(edition.annualPrice);
            default -> amount(edition.monthlyPrice);
        };
    }

    public SubscriptionPaymentItem createPayment(Integer editionId, int editionPaymentType, Integer paymentPeriodType,
                                                 int gateway, boolean recurring, String successUrl, String errorUrl) {
        return createPayment(1, editionId, editionPaymentType, paymentPeriodType, gateway, recurring, successUrl, errorUrl);
    }

    public SubscriptionPaymentItem createPayment(Integer tenantId, Integer editionId, int editionPaymentType,
                                                 Integer paymentPeriodType, int gateway, boolean recurring,
                                                 String successUrl, String errorUrl) {
        int effectiveTenantId = tenantId == null ? 1 : tenantId;
        TenantItem tenant = tenant(effectiveTenantId).orElse(null);
        if (editionPaymentType == 2 && (tenant == null || tenant.editionId == null)) {
            throw new IllegalArgumentException("Can not upgrade subscription since tenant has no edition assigned.");
        }
        EditionItem edition = edition(editionId).orElseGet(() -> editions().stream().findFirst().orElse(null));
        if (tenant != null && recurring) {
            tenant.subscriptionPaymentType = SUBSCRIPTION_RECURRING_AUTOMATIC;
        }
        SubscriptionPaymentItem item = new SubscriptionPaymentItem();
        if (!databaseStoreMode || loadingDatabaseState) {
            item.id = nextPaymentId();
        }
        item.editionId = edition == null ? 0 : edition.id;
        item.editionDisplayName = edition == null ? "" : edition.displayName;
        item.tenantId = effectiveTenantId;
        item.gateway = gateway <= 0 ? 2 : gateway;
        item.gatewayName = gatewayName(item.gateway);
        item.paymentPeriodType = paymentPeriodType == null ? 30 : paymentPeriodType;
        item.paymentPeriodTypeName = paymentPeriodName(item.paymentPeriodType);
        item.dayCount = Math.max(item.paymentPeriodType, 1);
        item.amount = editionPaymentAmount(item.editionId, item.paymentPeriodType);
        item.editionPaymentType = editionPaymentType;
        item.editionPaymentTypeName = editionPaymentTypeName(editionPaymentType);
        item.isRecurring = recurring;
        item.status = 1;
        item.statusName = paymentStatusName(item.status);
        item.description = paymentDescription(item);
        item.successUrl = successUrl;
        item.errorUrl = errorUrl;
        item.creationTime = LocalDateTime.now().toString();
        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseSubscriptionPayment(item);
        }
        item.paymentId = "PAY-" + item.id;
        subscriptionPayments.put(item.id, item);
        if (tenant != null && databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseTenant(tenant);
        }
        audit(1L, "PaymentAppService", "CreatePayment", item.description);
        persist();
        return item;
    }

    public List<PaymentGatewayItem> activePaymentGateways(Boolean recurringEnabled, boolean paypalActive, boolean stripeActive) {
        List<PaymentGatewayItem> gateways = new ArrayList<>();
        if (paypalActive) {
            gateways.add(paymentGateway(1, false));
        }
        if (stripeActive) {
            gateways.add(paymentGateway(2, true));
        }
        if (recurringEnabled == null) {
            return gateways;
        }
        return gateways.stream()
                .filter(gateway -> gateway.supportsRecurringPayments == recurringEnabled)
                .toList();
    }

    public PageResult<SubscriptionPaymentItem> paymentHistory(int skipCount, int maxResultCount) {
        return paymentHistory(null, skipCount, maxResultCount);
    }

    public PageResult<SubscriptionPaymentItem> paymentHistory(Integer tenantId, int skipCount, int maxResultCount) {
        return paymentHistory(tenantId, skipCount, maxResultCount, null);
    }

    public PageResult<SubscriptionPaymentItem> paymentHistory(Integer tenantId, int skipCount, int maxResultCount,
                                                              String sorting) {
        List<SubscriptionPaymentItem> filtered = subscriptionPayments.values().stream()
                .filter(item -> tenantId == null || item.tenantId == tenantId)
                .sorted(paymentHistoryComparator(sorting))
                .toList();
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    public List<SubscriptionPaymentItem> subscriptionPayments() {
        return subscriptionPayments.values().stream()
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<SubscriptionPaymentItem> payment(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(subscriptionPayments.get(id));
    }

    public Optional<SubscriptionPaymentItem> paymentByExternalId(String externalPaymentId) {
        return subscriptionPayments.values().stream()
                .filter(item -> equalsText(item.externalPaymentId, externalPaymentId))
                .findFirst();
    }

    public Optional<SubscriptionPaymentItem> lastCompletedPayment() {
        return lastCompletedPayment(null);
    }

    public Optional<SubscriptionPaymentItem> lastCompletedPayment(Integer tenantId) {
        return subscriptionPayments.values().stream()
                .filter(item -> item.status == 5)
                .filter(item -> tenantId == null || item.tenantId == tenantId)
                .max(Comparator.comparing(item -> safe(item.creationTime)));
    }

    public boolean hasAnyPayment() {
        return lastCompletedPayment().isPresent();
    }

    public boolean hasAnyPayment(Integer tenantId) {
        return lastCompletedPayment(tenantId).isPresent();
    }

    public void cancelPayment(String paymentId, int gateway) {
        subscriptionPayments.values().stream()
                .filter(item -> equalsText(item.paymentId, paymentId) || equalsText(item.externalPaymentId, paymentId))
                .filter(item -> gateway <= 0 || item.gateway == gateway)
                .findFirst()
                .ifPresent(item -> {
                    if (item.status == 1) {
                        item.status = 4;
                        item.statusName = paymentStatusName(item.status);
                    }
                    if (databaseStoreMode && !loadingDatabaseState) {
                        updateDatabaseSubscriptionPayment(item);
                    }
                    persist();
                });
    }

    public void markPaymentStatus(Long id, int status) {
        payment(id).ifPresent(item -> {
            item.status = status;
            item.statusName = paymentStatusName(status);
            if (status == 2 || status == 5) {
                updateTenantSubscription(item);
            }
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseSubscriptionPayment(item);
            }
            persist();
        });
    }

    public void completePaidPayment(Long id) {
        SubscriptionPaymentItem item = payment(id).orElse(null);
        if (item == null) {
            return;
        }
        if (item.status != 2) {
            throw new IllegalArgumentException("Your payment is not completed !");
        }
        item.status = 5;
        item.statusName = paymentStatusName(item.status);
        updateTenantSubscription(item);
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseSubscriptionPayment(item);
        }
        persist();
    }

    public void markPaymentPaid(Long id) {
        payment(id).ifPresent(item -> {
            if (item.status == 1) {
                item.status = 2;
                item.statusName = paymentStatusName(item.status);
            }
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseSubscriptionPayment(item);
            }
            persist();
        });
    }

    public void switchBetweenFreeEditions(Integer editionId) {
        switchBetweenFreeEditions(1, editionId);
    }

    public void switchBetweenFreeEditions(Integer tenantId, Integer editionId) {
        TenantItem tenant = tenant(tenantId).orElseThrow();
        if (tenant.editionId == null) {
            throw new IllegalArgumentException("tenant.EditionId can not be null");
        }
        EditionItem currentEdition = edition(tenant.editionId).orElseThrow();
        if (!currentEdition.isFree) {
            throw new IllegalArgumentException("You can only switch between free editions. Current edition if not free");
        }
        EditionItem upgradeEdition = edition(editionId).orElseThrow();
        if (!upgradeEdition.isFree) {
            throw new IllegalArgumentException("You can only switch between free editions. Target edition if not free");
        }
        tenant.editionId = editionId;
        decorateTenant(tenant);
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseTenant(tenant);
        }
        audit(1L, "PaymentAppService", "SwitchBetweenFreeEditions", tenantId + ":" + editionId);
        persist();
    }

    public void upgradeSubscriptionCostsLessThanMinAmount(Integer editionId) {
        upgradeSubscriptionCostsLessThanMinAmount(1, editionId);
    }

    public void upgradeSubscriptionCostsLessThanMinAmount(Integer tenantId, Integer editionId) {
        tenant(tenantId).ifPresent(tenant -> {
            tenant.editionId = editionId;
            decorateTenant(tenant);
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseTenant(tenant);
            }
        });
        audit(1L, "PaymentAppService", "UpgradeSubscriptionCostsLessThenMinAmount", tenantId + ":" + editionId);
        persist();
    }

    public boolean recurringPaymentsEnabled() {
        return recurringPaymentsEnabled;
    }

    public void setRecurringPaymentsEnabled(boolean enabled) {
        recurringPaymentsEnabled = enabled;
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSetting(SETTING_REPLICA_RECURRING_PAYMENTS, String.valueOf(enabled), 1, null);
        }
        audit(1L, "SubscriptionAppService", enabled ? "EnableRecurringPayments" : "DisableRecurringPayments", "成功");
        persist();
    }

    public void disableRecurringPayments(Integer tenantId) {
        TenantItem tenant = tenants.get(tenantId);
        if (tenant != null && Objects.equals(tenant.subscriptionPaymentType, SUBSCRIPTION_RECURRING_AUTOMATIC)) {
            tenant.subscriptionPaymentType = SUBSCRIPTION_RECURRING_MANUAL;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseTenant(tenant);
            }
            audit(1L, "SubscriptionAppService", "DisableRecurringPayments", String.valueOf(tenantId));
            persist();
        }
    }

    public void enableRecurringPayments(Integer tenantId) {
        TenantItem tenant = tenants.get(tenantId);
        if (tenant != null && Objects.equals(tenant.subscriptionPaymentType, SUBSCRIPTION_RECURRING_MANUAL)) {
            tenant.subscriptionPaymentType = SUBSCRIPTION_RECURRING_AUTOMATIC;
            tenant.subscriptionEndDateUtc = null;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseTenant(tenant);
            }
            audit(1L, "SubscriptionAppService", "EnableRecurringPayments", String.valueOf(tenantId));
            persist();
        }
    }

    public String createStripePaymentSession(Long paymentId) {
        SubscriptionPaymentItem payment = payment(paymentId).orElse(null);
        if (payment == null) {
            return "";
        }
        payment.externalPaymentId = "cs_test_" + payment.id;
        payment.gateway = 2;
        payment.gatewayName = gatewayName(2);
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseSubscriptionPayment(payment);
        }
        persist();
        return payment.externalPaymentId;
    }

    public void confirmStripePayment(String stripeSessionId) {
        paymentByExternalId(stripeSessionId).ifPresent(item -> markPaymentPaid(item.id));
    }

    public boolean stripePaymentDone(Long paymentId) {
        SubscriptionPaymentItem payment = payment(paymentId).orElse(null);
        if (payment == null) {
            return false;
        }
        if (safe(payment.externalPaymentId).isBlank()) {
            throw new IllegalArgumentException("Stripe session information for the payment transaction could not be found.");
        }
        return payment.status == 5;
    }

    public void confirmPayPalPayment(Long paymentId, String paypalOrderId) {
        payment(paymentId).ifPresent(item -> {
            item.gateway = 1;
            item.gatewayName = gatewayName(1);
            item.externalPaymentId = safe(paypalOrderId).isBlank() ? "PAYPAL-" + item.id : paypalOrderId;
            markPaymentPaid(item.id);
        });
    }

    public List<InvoiceItem> invoices() {
        return invoices.values().stream()
                .map(this::decorateInvoice)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public InvoiceItem createInvoice(Long subscriptionPaymentId) {
        SubscriptionPaymentItem payment = payment(subscriptionPaymentId).orElseThrow();
        if (!safe(payment.invoiceNo).isBlank()) {
            return invoiceByNo(payment.invoiceNo).orElse(null);
        }
        InvoiceItem invoice = new InvoiceItem();
        if (!databaseStoreMode || loadingDatabaseState) {
            invoice.id = invoices.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
        }
        invoice.subscriptionPaymentId = payment.id;
        invoice.invoiceDate = LocalDateTime.now().toString();
        invoice.amount = payment.amount;
        invoice.editionDisplayName = payment.editionDisplayName;
        invoice.tenantLegalName = tenantSettings.billing.legalName;
        invoice.tenantAddress = addressLines(tenantSettings.billing.address);
        invoice.tenantTaxNo = tenantSettings.billing.taxVatNo;
        invoice.hostLegalName = hostSettings.billing.legalName;
        invoice.hostAddress = addressLines(hostSettings.billing.address);
        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseInvoice(invoice);
        } else {
            invoice.invoiceNo = "INV-" + String.format("%05d", invoice.id);
        }
        invoices.put(invoice.id, invoice);
        payment.invoiceNo = invoice.invoiceNo;
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseSubscriptionPayment(payment);
        }
        audit(1L, "InvoiceAppService", "CreateInvoice", invoice.invoiceNo);
        persist();
        return invoice;
    }

    public Optional<InvoiceItem> invoiceInfo(Long subscriptionPaymentId) {
        return payment(subscriptionPaymentId).flatMap(payment -> {
            if (safe(payment.invoiceNo).isBlank()) {
                return Optional.empty();
            }
            return invoiceByNo(payment.invoiceNo).map(this::decorateInvoice);
        });
    }

    public List<LanguageItem> languages() {
        return languages.values().stream()
                .peek(this::decorateLanguage)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<LanguageItem> language(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(languages.get(id)).map(this::decorateLanguage);
    }

    public LanguageItem saveLanguage(LanguageItem input) {
        if (input.id == null) {
            input.id = languages.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
            input.creationTime = LocalDateTime.now().toString();
        }
        if (safe(input.name).isBlank()) {
            input.name = "custom-" + input.id;
        }
        if (safe(input.displayName).isBlank()) {
            input.displayName = input.name;
        }
        decorateLanguage(input);
        input.isDefault = languages.values().stream()
                .anyMatch(item -> Objects.equals(item.id, input.id) && item.isDefault);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseLanguage(input);
        }
        languages.put(input.id, input);
        seedLanguageTexts(input.name);
        persist();
        return input;
    }

    public void deleteLanguage(Integer id) {
        LanguageItem language = id == null ? null : languages.get(id);
        if (language == null || language.isDefault) {
            return;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            softDeleteDatabaseLanguage(id);
            deleteDatabaseLanguageTexts(language.name);
        }
        languages.remove(id);
        languageTexts.removeIf(item -> equalsText(item.languageName, language.name));
        persist();
    }

    public void setDefaultLanguage(String languageName) {
        languages.values().forEach(item -> item.isDefault = equalsText(item.name, languageName));
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSetting(SETTING_DEFAULT_LANGUAGE_NAME, safe(languageName), null, null);
            upsertDatabaseSetting(SETTING_DEFAULT_LANGUAGE_NAME, safe(languageName), 1, null);
        }
        persist();
    }

    public PageResult<LanguageTextItem> languageTexts(String languageName, String filter, int skipCount, int maxResultCount) {
        return languageTexts(null, languageName, null, filter, skipCount, maxResultCount);
    }

    public PageResult<LanguageTextItem> languageTexts(String sourceName, String languageName, String targetValueFilter,
                                                      String filter, int skipCount, int maxResultCount) {
        List<LanguageTextItem> filtered = languageTexts.stream()
                .filter(item -> safe(sourceName).isBlank() || equalsText(item.sourceName, sourceName))
                .filter(item -> safe(languageName).isBlank() || equalsText(item.languageName, languageName))
                .filter(item -> matchesTargetValueFilter(item, targetValueFilter))
                .filter(item -> contains(String.join(" ", safe(item.key), safe(item.baseValue), safe(item.targetValue)), filter))
                .sorted(Comparator.comparing(item -> safe(item.key)))
                .toList();
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    private boolean matchesTargetValueFilter(LanguageTextItem item, String targetValueFilter) {
        String filter = safe(targetValueFilter).trim();
        if (filter.isBlank() || equalsText(filter, "ALL")) {
            return true;
        }
        boolean hasTarget = !safe(item.targetValue).isBlank();
        if (equalsText(filter, "EMPTY") || equalsText(filter, "UNTRANSLATED")) {
            return !hasTarget;
        }
        if (equalsText(filter, "NOT_EMPTY") || equalsText(filter, "TRANSLATED")) {
            return hasTarget;
        }
        return true;
    }

    public LanguageTextItem updateLanguageText(LanguageTextItem input) {
        LanguageTextItem existing = languageTexts.stream()
                .filter(item -> equalsText(item.languageName, input.languageName)
                        && equalsText(item.sourceName, input.sourceName)
                        && equalsText(item.key, input.key))
                .findFirst()
                .orElseGet(() -> {
                    LanguageTextItem created = new LanguageTextItem();
                    created.id = nextLanguageTextId();
                    created.sourceName = safe(input.sourceName).isBlank() ? "CapabilityTable" : input.sourceName;
                    created.languageName = input.languageName;
                    created.key = input.key;
                    created.baseValue = input.baseValue;
                    languageTexts.add(created);
                    return created;
                });
        existing.targetValue = input.targetValue;
        if (!safe(input.baseValue).isBlank()) {
            existing.baseValue = input.baseValue;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseLanguageText(existing);
        }
        persist();
        return existing;
    }

    public List<NotificationItem> notifications() {
        return notifications.values().stream()
                .sorted(Comparator.comparing((NotificationItem item) -> safe(item.creationTime)).reversed())
                .toList();
    }

    public PageResult<NotificationItem> userNotifications(Long userId, String filter, String state, int skipCount, int maxResultCount) {
        return userNotifications(userId, filter, state, null, null, skipCount, maxResultCount);
    }

    public PageResult<NotificationItem> userNotifications(Long userId, String filter, String state,
                                                          String startDate, String endDate,
                                                          int skipCount, int maxResultCount) {
        Optional<LocalDateTime> start = parseStartDateTime(startDate);
        Optional<LocalDateTime> end = parseEndDateTime(endDate);
        List<NotificationItem> filtered = notifications.values().stream()
                .filter(item -> Objects.equals(item.userId, userId))
                .filter(item -> contains(String.join(" ", safe(item.notificationName), safe(item.message), safe(item.severity)), filter))
                // Original NotificationAppService passes StartDate/EndDate to ABP's notification manager.
                .filter(item -> between(item.creationTime, start, end))
                .filter(item -> {
                    if (safe(state).isBlank() || "ALL".equalsIgnoreCase(state)) {
                        return true;
                    }
                    if ("UNREAD".equalsIgnoreCase(state)) {
                        return item.readState == 0;
                    }
                    if ("READ".equalsIgnoreCase(state)) {
                        return item.readState != 0;
                    }
                    return true;
                })
                .sorted(Comparator.comparing((NotificationItem item) -> safe(item.creationTime)).reversed())
                .toList();
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    public long unreadNotificationCount(Long userId) {
        return unreadNotificationCount(userId, null, null);
    }

    public long unreadNotificationCount(Long userId, String startDate, String endDate) {
        Optional<LocalDateTime> start = parseStartDateTime(startDate);
        Optional<LocalDateTime> end = parseEndDateTime(endDate);
        return notifications.values().stream()
                .filter(item -> Objects.equals(item.userId, userId) && item.readState == 0)
                .filter(item -> between(item.creationTime, start, end))
                .count();
    }

    public void setAllNotificationsAsRead(Long userId) {
        notifications.values().stream()
                .filter(item -> Objects.equals(item.userId, userId) && item.readState == 0)
                .forEach(this::markNotificationRead);
        if (databaseStoreMode && !loadingDatabaseState) {
            jdbcTemplate.update("""
                    UPDATE dbo.SgsUserNotifications
                       SET State = 1
                     WHERE UserId = ?
                       AND State = 0
                       AND (TenantId = 1 OR TenantId IS NULL)
                    """, userId);
        }
        persist();
    }

    public Optional<String> setNotificationAsRead(Long userId, String id) {
        Optional<UUID> parsed = parseUuid(id);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        NotificationItem item = notifications.get(parsed.get());
        if (item == null) {
            return Optional.empty();
        }
        if (!Objects.equals(item.userId, userId)) {
            return Optional.of("Given user notification id (" + id + ") is not belong to the current user (" + userId + ")");
        }
        markNotificationRead(item);
        if (databaseStoreMode && !loadingDatabaseState) {
            jdbcTemplate.update("""
                    UPDATE dbo.SgsUserNotifications
                       SET State = 1
                     WHERE Id = ? AND UserId = ?
                    """, item.id, userId);
        }
        persist();
        return Optional.empty();
    }

    public Optional<String> deleteNotification(Long userId, String id) {
        Optional<UUID> parsed = parseUuid(id);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        NotificationItem item = notifications.get(parsed.get());
        if (item == null) {
            return Optional.empty();
        }
        if (!Objects.equals(item.userId, userId)) {
            // Original service returns this localized user-friendly error for cross-user deletes.
            return Optional.of("This notification doesn't belong to you.");
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            jdbcTemplate.update("""
                    UPDATE dbo.SgsUserNotifications
                       SET State = 2
                     WHERE Id = ? AND UserId = ?
                    """, item.id, userId);
        }
        notifications.remove(item.id);
        persist();
        return Optional.empty();
    }

    public void deleteAllUserNotifications(Long userId, String state) {
        deleteAllUserNotifications(userId, state, null, null);
    }

    public void deleteAllUserNotifications(Long userId, String state, String startDate, String endDate) {
        Optional<LocalDateTime> start = parseStartDateTime(startDate);
        Optional<LocalDateTime> end = parseEndDateTime(endDate);
        List<UUID> deleteIds = notifications.values().stream()
                .filter(item -> Objects.equals(item.userId, userId)
                        && between(item.creationTime, start, end)
                        && ("UNREAD".equalsIgnoreCase(safe(state)) ? item.readState == 0
                        : "READ".equalsIgnoreCase(safe(state)) ? item.readState != 0 : true))
                .map(item -> item.id)
                .filter(Objects::nonNull)
                .toList();
        if (databaseStoreMode && !loadingDatabaseState && !deleteIds.isEmpty()) {
            deleteIds.forEach(id -> jdbcTemplate.update("""
                    UPDATE dbo.SgsUserNotifications
                       SET State = 2
                     WHERE Id = ? AND UserId = ?
                    """, id, userId));
        }
        notifications.values().removeIf(item -> Objects.equals(item.userId, userId)
                && between(item.creationTime, start, end)
                && ("UNREAD".equalsIgnoreCase(safe(state)) ? item.readState == 0
                : "READ".equalsIgnoreCase(safe(state)) ? item.readState != 0 : true));
        persist();
    }

    public NotificationSettings notificationSettings(Long userId) {
        NotificationSettings settings = notificationSettings.get(userId);
        if (settings == null) {
            settings = defaultNotificationSettings(userId);
            notificationSettings.put(userId, settings);
            persist();
        }
        return settings;
    }

    public NotificationSettings saveNotificationSettings(Long userId, NotificationSettings input) {
        NotificationSettings settings = input == null ? defaultNotificationSettings(userId) : input;
        settings.userId = userId;
        if (settings.notifications == null || settings.notifications.isEmpty()) {
            settings.notifications = defaultNotificationSubscriptions();
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            saveDatabaseNotificationSettings(userId, settings);
        }
        notificationSettings.put(userId, settings);
        persist();
        return settings;
    }

    public List<CacheItem> caches() {
        return caches.values().stream()
                .sorted(Comparator.comparing(item -> safe(item.displayName)))
                .toList();
    }

    public void clearCache(String name) {
        if (safe(name).isBlank()) {
            return;
        }
        CacheItem cache = caches.computeIfAbsent(name, item -> cache(item, item, 0));
        cache.itemCount = 0;
        cache.lastClearTime = LocalDateTime.now().toString();
        persist();
    }

    public void clearAllCaches() {
        caches.values().forEach(cache -> {
            cache.itemCount = 0;
            cache.lastClearTime = LocalDateTime.now().toString();
        });
        persist();
    }

    public List<DynamicParameterItem> dynamicParameters() {
        return dynamicParameters.values().stream()
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<DynamicParameterItem> dynamicParameter(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(dynamicParameters.get(id));
    }

    public List<String> allowedDynamicInputTypeNames() {
        return List.of(
                "SINGLE_LINE_STRING",
                "MULTI_LINE_STRING",
                "COMBOBOX",
                "CHECKBOX",
                "RADIO_BUTTON",
                "MULTI_SELECT_COMBOBOX",
                "NUMBER",
                "DATE"
        );
    }

    public Map<String, Object> allowedDynamicInputType(String name) {
        String normalized = safe(name).isBlank() ? "SINGLE_LINE_STRING" : safe(name).trim().toUpperCase(Locale.ROOT);
        if (!allowedDynamicInputTypeNames().contains(normalized)) {
            return Map.of();
        }
        Map<String, Object> inputType = new LinkedHashMap<>();
        inputType.put("name", normalized);
        inputType.put("displayName", dynamicInputTypeDisplayName(normalized));
        inputType.put("attributes", dynamicInputTypeAttributes(normalized));
        return inputType;
    }

    public List<String> dynamicEntityNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>(List.of(
                ABILITY_ENTITY,
                TENANT_ENTITY,
                USER_ENTITY,
                LAB_ENTITY,
                SAMPLE_ENTITY,
                SUBCONTRACT_ABILITY_ENTITY,
                "Capability.Ability"
        ));
        entityDynamicParameters.values().stream()
                .map(item -> safe(item.entityFullName).trim())
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        return new ArrayList<>(names);
    }

    private String dynamicInputTypeDisplayName(String name) {
        return switch (name) {
            case "MULTI_LINE_STRING" -> "多行文本";
            case "COMBOBOX" -> "下拉框";
            case "CHECKBOX" -> "复选框";
            case "RADIO_BUTTON" -> "单选框";
            case "MULTI_SELECT_COMBOBOX" -> "多选下拉框";
            case "NUMBER" -> "数字";
            case "DATE" -> "日期";
            default -> "单行文本";
        };
    }

    private Map<String, Object> dynamicInputTypeAttributes(String name) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        switch (name) {
            case "MULTI_LINE_STRING" -> {
                attributes.put("rows", 4);
                attributes.put("maxLength", 4000);
            }
            case "COMBOBOX", "RADIO_BUTTON", "MULTI_SELECT_COMBOBOX" -> {
                attributes.put("itemsSource", "DynamicParameterValue");
                attributes.put("allowMultiple", "MULTI_SELECT_COMBOBOX".equals(name));
            }
            case "CHECKBOX" -> attributes.put("defaultValue", false);
            case "NUMBER" -> {
                attributes.put("min", 0);
                attributes.put("precision", 2);
            }
            case "DATE" -> attributes.put("format", "yyyy-MM-dd");
            default -> attributes.put("maxLength", 256);
        }
        return attributes;
    }

    public DynamicParameterItem saveDynamicParameter(DynamicParameterItem input) {
        DynamicParameterItem item = input == null ? new DynamicParameterItem() : input;
        if (item.id == null) {
            item.id = nextDynamicParameterId();
        }
        if (safe(item.parameterName).isBlank()) {
            item.parameterName = "Parameter" + item.id;
        }
        if (safe(item.displayName).isBlank()) {
            item.displayName = item.parameterName;
        }
        if (safe(item.inputType).isBlank()) {
            item.inputType = "SINGLE_LINE_STRING";
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            item = upsertDatabaseDynamicParameter(item);
        }
        dynamicParameters.put(item.id, item);
        persist();
        return item;
    }

    public void deleteDynamicParameter(Integer id) {
        if (id == null) {
            return;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            deleteDatabaseDynamicParameter(id);
        }
        dynamicParameters.remove(id);
        dynamicParameterValues.values().removeIf(item -> Objects.equals(item.dynamicParameterId, id));
        entityDynamicParameters.values().removeIf(item -> Objects.equals(item.dynamicParameterId, id));
        entityDynamicParameterValues.values().removeIf(item -> Objects.equals(item.dynamicParameterId, id));
        persist();
    }

    public List<DynamicParameterValueItem> dynamicParameterValues(Integer dynamicParameterId) {
        return dynamicParameterValues.values().stream()
                .filter(item -> dynamicParameterId == null || Objects.equals(item.dynamicParameterId, dynamicParameterId))
                .map(this::decorateDynamicParameterValue)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<DynamicParameterValueItem> dynamicParameterValue(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(dynamicParameterValues.get(id)).map(this::decorateDynamicParameterValue);
    }

    public DynamicParameterValueItem saveDynamicParameterValue(DynamicParameterValueItem input) {
        DynamicParameterValueItem item = input == null ? new DynamicParameterValueItem() : input;
        if (item.id == null) {
            item.id = nextDynamicParameterValueId();
        }
        item = decorateDynamicParameterValue(item);
        if (databaseStoreMode && !loadingDatabaseState) {
            item = upsertDatabaseDynamicParameterValue(item);
        }
        dynamicParameterValues.put(item.id, item);
        persist();
        return item;
    }

    public void deleteDynamicParameterValue(Integer id) {
        if (id != null) {
            if (databaseStoreMode && !loadingDatabaseState) {
                deleteDatabaseDynamicParameterValue(id);
            }
            dynamicParameterValues.remove(id);
            persist();
        }
    }

    public List<EntityDynamicParameterItem> entityDynamicParameters(String entityFullName) {
        return entityDynamicParameters.values().stream()
                .filter(item -> safe(entityFullName).isBlank() || equalsText(item.entityFullName, entityFullName))
                .map(this::decorateEntityDynamicParameter)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<EntityDynamicParameterItem> entityDynamicParameter(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entityDynamicParameters.get(id)).map(this::decorateEntityDynamicParameter);
    }

    public EntityDynamicParameterItem saveEntityDynamicParameter(EntityDynamicParameterItem input) {
        EntityDynamicParameterItem item = input == null ? new EntityDynamicParameterItem() : input;
        if (item.id == null) {
            item.id = nextEntityDynamicParameterId();
        }
        if (safe(item.entityFullName).isBlank()) {
            item.entityFullName = ABILITY_ENTITY;
        }
        item = decorateEntityDynamicParameter(item);
        if (databaseStoreMode && !loadingDatabaseState) {
            item = upsertDatabaseEntityDynamicParameter(item);
        }
        entityDynamicParameters.put(item.id, item);
        persist();
        return item;
    }

    public void deleteEntityDynamicParameter(Integer id) {
        if (id == null) {
            return;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            deleteDatabaseEntityDynamicParameter(id);
        }
        entityDynamicParameters.remove(id);
        entityDynamicParameterValues.values().removeIf(item -> Objects.equals(item.entityDynamicParameterId, id));
        persist();
    }

    public List<EntityDynamicParameterValueItem> entityDynamicParameterValues(Integer entityDynamicParameterId, String entityId) {
        return entityDynamicParameterValues.values().stream()
                .filter(item -> entityDynamicParameterId == null || Objects.equals(item.entityDynamicParameterId, entityDynamicParameterId))
                .filter(item -> safe(entityId).isBlank() || equalsText(item.entityId, entityId))
                .map(this::decorateEntityDynamicParameterValue)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<EntityDynamicParameterValueItem> entityDynamicParameterValue(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entityDynamicParameterValues.get(id))
                .map(this::decorateEntityDynamicParameterValue);
    }

    public EntityDynamicParameterValueItem saveEntityDynamicParameterValue(EntityDynamicParameterValueItem input) {
        EntityDynamicParameterValueItem item = input == null ? new EntityDynamicParameterValueItem() : input;
        if (item.id == null) {
            item.id = nextEntityDynamicParameterValueId();
        }
        item = decorateEntityDynamicParameterValue(item);
        if (databaseStoreMode && !loadingDatabaseState) {
            item = upsertDatabaseEntityDynamicParameterValue(item);
        }
        entityDynamicParameterValues.put(item.id, item);
        persist();
        return item;
    }

    public List<EntityDynamicParameterValueItem> saveEntityDynamicParameterValues(List<EntityDynamicParameterValueItem> input) {
        List<EntityDynamicParameterValueItem> values = list(input).stream()
                .map(value -> {
                    EntityDynamicParameterValueItem item = value == null ? new EntityDynamicParameterValueItem() : value;
                    if (item.id == null) {
                        item.id = nextEntityDynamicParameterValueId();
                    }
                    item = decorateEntityDynamicParameterValue(item);
                    if (databaseStoreMode && !loadingDatabaseState) {
                        item = upsertDatabaseEntityDynamicParameterValue(item);
                    }
                    entityDynamicParameterValues.put(item.id, item);
                    return item;
                })
                .toList();
        persist();
        return values;
    }

    public void replaceEntityDynamicParameterValues(Integer entityDynamicParameterId, String entityId, List<String> values) {
        if (databaseStoreMode && !loadingDatabaseState) {
            deleteDatabaseEntityDynamicParameterValues(entityDynamicParameterId, entityId);
        }
        entityDynamicParameterValues.values().removeIf(item ->
                Objects.equals(item.entityDynamicParameterId, entityDynamicParameterId)
                        && equalsText(item.entityId, entityId));
        for (String value : list(values)) {
            EntityDynamicParameterValueItem item = new EntityDynamicParameterValueItem();
            item.id = nextEntityDynamicParameterValueId();
            item.entityDynamicParameterId = entityDynamicParameterId;
            item.entityId = entityId;
            item.value = value;
            item = decorateEntityDynamicParameterValue(item);
            if (databaseStoreMode && !loadingDatabaseState) {
                item = upsertDatabaseEntityDynamicParameterValue(item);
            }
            entityDynamicParameterValues.put(item.id, item);
        }
        persist();
    }

    public void deleteEntityDynamicParameterValue(Integer id) {
        if (id != null) {
            if (databaseStoreMode && !loadingDatabaseState) {
                deleteDatabaseEntityDynamicParameterValue(id);
            }
            entityDynamicParameterValues.remove(id);
            persist();
        }
    }

    public void cleanEntityDynamicParameterValues(String entityFullName, String entityId) {
        if (databaseStoreMode && !loadingDatabaseState) {
            cleanDatabaseEntityDynamicParameterValues(entityFullName, entityId);
        }
        entityDynamicParameterValues.values().removeIf(item ->
                (safe(entityFullName).isBlank() || equalsText(item.entityFullName, entityFullName))
                        && (safe(entityId).isBlank() || equalsText(item.entityId, entityId)));
        persist();
    }

    public void cleanEntityDynamicParameterValues(Integer entityDynamicParameterId, String entityId) {
        if (databaseStoreMode && !loadingDatabaseState) {
            deleteDatabaseEntityDynamicParameterValues(entityDynamicParameterId, entityId);
        }
        entityDynamicParameterValues.values().removeIf(item ->
                Objects.equals(item.entityDynamicParameterId, entityDynamicParameterId)
                        && equalsText(item.entityId, entityId));
        persist();
    }

    private DynamicParameterItem upsertDatabaseDynamicParameter(DynamicParameterItem input) {
        DynamicParameterItem item = input == null ? new DynamicParameterItem() : input;
        item.parameterName = safe(item.parameterName).isBlank() ? "Parameter" + (item.id == null ? "" : item.id) : item.parameterName;
        item.displayName = safe(item.displayName).isBlank() ? item.parameterName : item.displayName;
        item.inputType = safe(item.inputType).isBlank() ? "SINGLE_LINE_STRING" : item.inputType;
        if (item.id != null) {
            int updated = jdbcTemplate.update("""
                            UPDATE dbo.SgsDynamicParameters
                               SET ParameterName = ?, InputType = ?, Permission = ?, TenantId = 1
                             WHERE Id = ?
                            """,
                    truncateForColumn(item.parameterName, 450),
                    item.inputType,
                    item.permission,
                    item.id);
            if (updated > 0) {
                return item;
            }
        }
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsDynamicParameters (ParameterName, InputType, Permission, TenantId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?)
                        """,
                Long.class,
                truncateForColumn(item.parameterName, 450),
                item.inputType,
                item.permission,
                1);
        item.id = intId(id);
        if (safe(item.displayName).isBlank()) {
            item.displayName = item.parameterName;
        }
        return item;
    }

    private void deleteDatabaseDynamicParameter(Integer id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("""
                DELETE FROM dbo.SgsEntityDynamicParameterValues
                 WHERE EntityDynamicParameterId IN (
                       SELECT Id FROM dbo.SgsEntityDynamicParameters WHERE DynamicParameterId = ?)
                """, id);
        jdbcTemplate.update("DELETE FROM dbo.SgsEntityDynamicParameters WHERE DynamicParameterId = ?", id);
        jdbcTemplate.update("DELETE FROM dbo.SgsDynamicParameterValues WHERE DynamicParameterId = ?", id);
        jdbcTemplate.update("DELETE FROM dbo.SgsDynamicParameters WHERE Id = ?", id);
    }

    private DynamicParameterValueItem upsertDatabaseDynamicParameterValue(DynamicParameterValueItem input) {
        DynamicParameterValueItem item = input == null ? new DynamicParameterValueItem() : input;
        item = decorateDynamicParameterValue(item);
        if (item.dynamicParameterId == null || !dynamicParameters.containsKey(item.dynamicParameterId)) {
            return item;
        }
        if (item.id != null) {
            int updated = jdbcTemplate.update("""
                            UPDATE dbo.SgsDynamicParameterValues
                               SET Value = ?, DynamicParameterId = ?, TenantId = 1
                             WHERE Id = ?
                            """,
                    safe(item.value),
                    item.dynamicParameterId,
                    item.id);
            if (updated > 0) {
                return item;
            }
        }
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsDynamicParameterValues (Value, TenantId, DynamicParameterId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?)
                        """,
                Long.class,
                safe(item.value),
                1,
                item.dynamicParameterId);
        item.id = intId(id);
        return decorateDynamicParameterValue(item);
    }

    private void deleteDatabaseDynamicParameterValue(Integer id) {
        if (id != null) {
            jdbcTemplate.update("DELETE FROM dbo.SgsDynamicParameterValues WHERE Id = ?", id);
        }
    }

    private EntityDynamicParameterItem upsertDatabaseEntityDynamicParameter(EntityDynamicParameterItem input) {
        EntityDynamicParameterItem item = input == null ? new EntityDynamicParameterItem() : input;
        item = decorateEntityDynamicParameter(item);
        if (safe(item.entityFullName).isBlank()) {
            item.entityFullName = ABILITY_ENTITY;
        }
        if (item.dynamicParameterId == null || !dynamicParameters.containsKey(item.dynamicParameterId)) {
            return item;
        }
        if (item.id != null) {
            int updated = jdbcTemplate.update("""
                            UPDATE dbo.SgsEntityDynamicParameters
                               SET EntityFullName = ?, DynamicParameterId = ?, TenantId = 1
                             WHERE Id = ?
                            """,
                    truncateForColumn(item.entityFullName, 450),
                    item.dynamicParameterId,
                    item.id);
            if (updated > 0) {
                return decorateEntityDynamicParameter(item);
            }
        }
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsEntityDynamicParameters (EntityFullName, DynamicParameterId, TenantId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?)
                        """,
                Long.class,
                truncateForColumn(item.entityFullName, 450),
                item.dynamicParameterId,
                1);
        item.id = intId(id);
        return decorateEntityDynamicParameter(item);
    }

    private void deleteDatabaseEntityDynamicParameter(Integer id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM dbo.SgsEntityDynamicParameterValues WHERE EntityDynamicParameterId = ?", id);
        jdbcTemplate.update("DELETE FROM dbo.SgsEntityDynamicParameters WHERE Id = ?", id);
    }

    private EntityDynamicParameterValueItem upsertDatabaseEntityDynamicParameterValue(EntityDynamicParameterValueItem input) {
        EntityDynamicParameterValueItem item = input == null ? new EntityDynamicParameterValueItem() : input;
        item = decorateEntityDynamicParameterValue(item);
        if (item.entityDynamicParameterId == null || !entityDynamicParameters.containsKey(item.entityDynamicParameterId)) {
            return item;
        }
        if (item.id != null) {
            int updated = jdbcTemplate.update("""
                            UPDATE dbo.SgsEntityDynamicParameterValues
                               SET Value = ?, EntityId = ?, EntityDynamicParameterId = ?, TenantId = 1
                             WHERE Id = ?
                            """,
                    safe(item.value),
                    safe(item.entityId),
                    item.entityDynamicParameterId,
                    item.id);
            if (updated > 0) {
                return decorateEntityDynamicParameterValue(item);
            }
        }
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsEntityDynamicParameterValues
                            (Value, EntityId, EntityDynamicParameterId, TenantId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?)
                        """,
                Long.class,
                safe(item.value),
                safe(item.entityId),
                item.entityDynamicParameterId,
                1);
        item.id = intId(id);
        return decorateEntityDynamicParameterValue(item);
    }

    private void deleteDatabaseEntityDynamicParameterValue(Integer id) {
        if (id != null) {
            jdbcTemplate.update("DELETE FROM dbo.SgsEntityDynamicParameterValues WHERE Id = ?", id);
        }
    }

    private void deleteDatabaseEntityDynamicParameterValues(Integer entityDynamicParameterId, String entityId) {
        if (entityDynamicParameterId == null) {
            return;
        }
        String normalizedEntityId = safe(entityId).replace("\"", "");
        if (normalizedEntityId.isBlank()) {
            jdbcTemplate.update("DELETE FROM dbo.SgsEntityDynamicParameterValues WHERE EntityDynamicParameterId = ?",
                    entityDynamicParameterId);
            return;
        }
        jdbcTemplate.update("""
                        DELETE FROM dbo.SgsEntityDynamicParameterValues
                         WHERE EntityDynamicParameterId = ?
                           AND REPLACE(CONVERT(nvarchar(max), EntityId), '"', '') = ?
                        """,
                entityDynamicParameterId,
                normalizedEntityId);
    }

    private void cleanDatabaseEntityDynamicParameterValues(String entityFullName, String entityId) {
        String normalizedEntityName = safe(entityFullName);
        String normalizedEntityId = safe(entityId).replace("\"", "");
        if (normalizedEntityName.isBlank() && normalizedEntityId.isBlank()) {
            jdbcTemplate.update("DELETE FROM dbo.SgsEntityDynamicParameterValues");
            return;
        }
        StringBuilder sql = new StringBuilder("""
                DELETE v
                  FROM dbo.SgsEntityDynamicParameterValues v
                  LEFT JOIN dbo.SgsEntityDynamicParameters p ON p.Id = v.EntityDynamicParameterId
                 WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (!normalizedEntityName.isBlank()) {
            sql.append(" AND p.EntityFullName = ?");
            args.add(normalizedEntityName);
        }
        if (!normalizedEntityId.isBlank()) {
            sql.append(" AND REPLACE(CONVERT(nvarchar(max), v.EntityId), '\"', '') = ?");
            args.add(normalizedEntityId);
        }
        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public DashboardCustomizationItem dashboardCustomization(String application, String dashboardName) {
        return dashboardCustomizations.computeIfAbsent(dashboardKey(application, dashboardName),
                key -> defaultDashboard(application, dashboardName));
    }

    public void saveDashboardPages(String application, String dashboardName, List<DashboardPageItem> pages) {
        DashboardCustomizationItem dashboard = dashboardCustomization(application, dashboardName);
        List<DashboardPageItem> inputPages = list(pages);
        List<Integer> pageIndexes = new ArrayList<>();
        for (DashboardPageItem inputPage : inputPages) {
            int pageIndex = -1;
            for (int index = 0; index < dashboard.pages.size(); index++) {
                if (equalsText(dashboard.pages.get(index).id, inputPage.id)) {
                    pageIndex = index;
                    break;
                }
            }
            if (pageIndex < 0) {
                throw new IllegalArgumentException("Index was out of range. Must be non-negative and less than the size of the collection. (Parameter 'index')");
            }
            pageIndexes.add(pageIndex);
        }
        for (int index = 0; index < inputPages.size(); index++) {
            DashboardPageItem inputPage = inputPages.get(index);
            int pageIndex = pageIndexes.get(index);
            DashboardPageItem currentPage = dashboard.pages.get(pageIndex);
            inputPage.name = currentPage.name;
            dashboard.pages.set(pageIndex, inputPage);
        }
        dashboardCustomizations.put(dashboardKey(application, dashboardName), dashboard);
        saveDatabaseDashboardCustomizations();
        persist();
    }

    public void renameDashboardPage(String application, String dashboardName, String pageId, String name) {
        dashboardCustomization(application, dashboardName).pages.stream()
                .filter(page -> equalsText(page.id, pageId))
                .findFirst()
                .ifPresent(page -> page.name = name);
        saveDatabaseDashboardCustomizations();
        persist();
    }

    public String addDashboardPage(String application, String dashboardName, String name) {
        DashboardCustomizationItem dashboard = dashboardCustomization(application, dashboardName);
        DashboardPageItem page = dashboardPage("Page" + randomCode(), name, List.of());
        dashboard.pages.add(page);
        saveDatabaseDashboardCustomizations();
        persist();
        return page.id;
    }

    public void deleteDashboardPage(String application, String dashboardName, String pageId) {
        DashboardCustomizationItem dashboard = dashboardCustomization(application, dashboardName);
        dashboard.pages.removeIf(page -> equalsText(page.id, pageId));
        if (dashboard.pages.isEmpty()) {
            dashboardCustomizations.put(dashboardKey(application, dashboardName), defaultDashboard(application, dashboardName));
        }
        saveDatabaseDashboardCustomizations();
        persist();
    }

    public DashboardWidgetItem addDashboardWidget(String application, String dashboardName, String pageId,
                                                  String widgetId, int width, int height) {
        DashboardCustomizationItem dashboard = dashboardCustomization(application, dashboardName);
        DashboardPageItem page = dashboard.pages.stream()
                .filter(item -> equalsText(item.id, pageId))
                .findFirst()
                // Original service uses Enumerable.Single and fails when pageId is missing.
                .orElseThrow(() -> new IllegalArgumentException("Sequence contains no matching element"));
        DashboardWidgetItem widget = dashboardWidget(widgetId, width, height, 0, nextDashboardWidgetY(page));
        page.widgets.add(widget);
        saveDatabaseDashboardCustomizations();
        persist();
        return widget;
    }

    public List<WebhookDefinitionItem> webhookDefinitions() {
        return List.of(webhookDefinition("App.TestWebhook", "测试 Webhook", "发布一条本地测试 Webhook 事件"));
    }

    public List<WebhookSubscriptionItem> webhookSubscriptions() {
        return webhookSubscriptions.values().stream()
                .sorted(Comparator.comparing(item -> safe(item.webhookUri)))
                .toList();
    }

    public Optional<WebhookSubscriptionItem> webhookSubscription(String id) {
        return parseUuid(id).map(webhookSubscriptions::get);
    }

    public WebhookSubscriptionItem saveWebhookSubscription(WebhookSubscriptionItem input) {
        WebhookSubscriptionItem item = input == null ? new WebhookSubscriptionItem() : input;
        if (item.id == null) {
            item.id = UUID.randomUUID();
            item.creationTime = LocalDateTime.now().toString();
        }
        if (safe(item.webhookUri).isBlank()) {
            item.webhookUri = "https://example.local/webhook";
        }
        if (item.webhooks == null || item.webhooks.isEmpty()) {
            item.webhooks = new ArrayList<>(List.of("App.TestWebhook"));
        }
        if (item.headers == null) {
            item.headers = new LinkedHashMap<>();
        }
        if (safe(item.secret).isBlank()) {
            item.secret = randomCode();
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseWebhookSubscription(item);
        }
        webhookSubscriptions.put(item.id, item);
        persist();
        return item;
    }

    public void activateWebhookSubscription(String subscriptionId, boolean active) {
        webhookSubscription(subscriptionId).ifPresent(item -> {
            item.isActive = active;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseWebhookSubscriptionActive(item.id, active);
            }
            persist();
        });
    }

    public boolean isSubscribed(String webhookName) {
        return webhookSubscriptions.values().stream()
                .anyMatch(item -> item.isActive && item.webhooks != null && item.webhooks.stream().anyMatch(name -> equalsText(name, webhookName)));
    }

    public List<WebhookSubscriptionItem> webhookSubscriptionsForWebhook(String webhookName) {
        return webhookSubscriptions().stream()
                .filter(item -> safe(webhookName).isBlank() || item.webhooks.stream().anyMatch(name -> equalsText(name, webhookName)))
                .toList();
    }

    public String publishTestWebhook() {
        WebhookEventItem event = webhookEvent("App.TestWebhook", "{\"message\":\"Local test webhook\"}");
        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseWebhookEvent(event);
        }
        webhookEvents.put(event.id, event);
        webhookSubscriptionsForWebhook("App.TestWebhook").stream()
                .filter(item -> item.isActive)
                .forEach(subscription -> {
                    WebhookSendAttemptItem attempt = webhookSendAttempt(event, subscription, "Queued in local replica", 202);
                    if (databaseStoreMode && !loadingDatabaseState) {
                        insertDatabaseWebhookSendAttempt(attempt);
                    }
                    webhookSendAttempts.put(attempt.id, attempt);
                });
        persist();
        return "Webhook 发送尝试已进入本地队列（需要订阅 App.TestWebhook 才能收到测试事件）";
    }

    public Optional<WebhookEventItem> webhookEvent(String id) {
        return parseUuid(id).map(webhookEvents::get);
    }

    public List<WebhookEventItem> webhookEvents() {
        return webhookEvents.values().stream()
                .sorted(Comparator.comparing((WebhookEventItem item) -> safe(item.creationTime)).reversed())
                .toList();
    }

    public List<WebhookSendAttemptItem> webhookSendAttempts() {
        return webhookSendAttempts.values().stream()
                .map(this::decorateWebhookSendAttempt)
                .sorted(Comparator.comparing((WebhookSendAttemptItem item) -> safe(item.creationTime)).reversed())
                .toList();
    }

    public PageResult<WebhookSendAttemptItem> webhookSendAttempts(String subscriptionId, int skipCount, int maxResultCount) {
        Optional<UUID> subscriptionUuid = parseUuid(subscriptionId);
        List<WebhookSendAttemptItem> filtered = webhookSendAttempts().stream()
                .filter(item -> subscriptionUuid.isEmpty() || Objects.equals(item.webhookSubscriptionId, subscriptionUuid.get()))
                .toList();
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    public List<WebhookSendAttemptItem> webhookSendAttemptsOfEvent(String eventId) {
        Optional<UUID> eventUuid = parseUuid(eventId);
        return webhookSendAttempts().stream()
                .filter(item -> eventUuid.isPresent() && Objects.equals(item.webhookEventId, eventUuid.get()))
                .toList();
    }

    public void resendWebhookAttempt(String sendAttemptId) {
        parseUuid(sendAttemptId).map(webhookSendAttempts::get).ifPresent(item -> {
            item.retryCount++;
            item.response = "Resent in local replica";
            item.responseStatusCode = 202;
            item.lastModificationTime = LocalDateTime.now().toString();
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseWebhookSendAttempt(item);
            }
            persist();
        });
    }

    public List<ThemeSettingsItem> uiManagementSettings() {
        return uiThemes.values().stream()
                .map(this::decorateTheme)
                .sorted(Comparator.comparing(item -> safe(item.theme)))
                .toList();
    }

    public ThemeSettingsItem updateUiManagementSettings(ThemeSettingsItem input) {
        ThemeSettingsItem item = normalizeTheme(input);
        uiThemes.put(item.theme, item);
        activeUiTheme = item.theme;
        saveDatabaseUiSettings();
        persist();
        return decorateTheme(item);
    }

    public ThemeSettingsItem updateDefaultUiManagementSettings(ThemeSettingsItem input) {
        ThemeSettingsItem item = normalizeTheme(input);
        item.theme = safe(item.theme).isBlank() ? "default" : item.theme;
        uiThemes.put(item.theme, item);
        saveDatabaseUiSettings();
        persist();
        return decorateTheme(item);
    }

    public void changeThemeWithDefaultValues(String themeName) {
        String selected = safe(themeName).isBlank() ? "default" : themeName;
        uiThemes.putIfAbsent(selected, defaultTheme(selected));
        activeUiTheme = selected;
        saveDatabaseUiSettings();
        persist();
    }

    public void useSystemDefaultSettings() {
        uiThemes.clear();
        seedUiThemes();
        activeUiTheme = "default";
        saveDatabaseUiSettings();
        persist();
    }

    public InstallSettingsItem installSettings() {
        installSettings = normalizeInstallSettings(installSettings);
        return installSettings;
    }

    public InstallSettingsItem setupInstall(InstallSettingsItem input, String adminPassword) {
        InstallSettingsItem next = normalizeInstallSettings(input);
        next.installed = true;
        next.setupTime = LocalDateTime.now().toString();
        installSettings = next;
        if (!safe(adminPassword).isBlank()) {
            userPasswords.put(1L, adminPassword);
            user(1L).ifPresent(user -> user.shouldChangePasswordOnNextLogin = false);
        }
        if (!safe(next.defaultLanguage).isBlank()) {
            setDefaultLanguage(next.defaultLanguage);
        }
        hostSettings = normalizeHostSettings(hostSettings);
        hostSettings.email = next.smtpSettings;
        hostSettings.billing = next.billInfo;
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSetting(SETTING_REPLICA_INSTALL, jsonSetting(installSettings), null, null);
            if (!safe(adminPassword).isBlank()) {
                updateDatabaseUserPassword(1L, adminPassword);
                user(1L).ifPresent(this::updateDatabaseUserSecurityFields);
            }
        }
        audit(null, "InstallAppService", "Setup", "本地安装配置已保存");
        persist();
        return installSettings;
    }

    public boolean installDatabaseExists() {
        return !safe(installSettings().connectionString).isBlank();
    }

    public SystemSettingsItem.HostSettings hostSettings() {
        hostSettings = normalizeHostSettings(hostSettings);
        return hostSettings;
    }

    public SystemSettingsItem.HostSettings updateHostSettings(SystemSettingsItem.HostSettings input) {
        hostSettings = normalizeHostSettings(input);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSetting(SETTING_REPLICA_HOST_SETTINGS, jsonSetting(hostSettings), null, null);
            upsertDatabaseSetting("Abp.Net.Mail.DefaultFromAddress", hostSettings.email.defaultFromAddress, null, null);
            upsertDatabaseSetting("Abp.Net.Mail.DefaultFromDisplayName", hostSettings.email.defaultFromDisplayName, null, null);
        }
        persist();
        return hostSettings;
    }

    public SystemSettingsItem.AbilitySettings abilitySettings() {
        if (abilitySettings == null) {
            abilitySettings = SystemSettingsItem.defaultAbilitySettings();
        }
        return abilitySettings;
    }

    public SystemSettingsItem.AbilitySettings updateAbilitySettings(SystemSettingsItem.AbilitySettings input) {
        abilitySettings = input == null ? SystemSettingsItem.defaultAbilitySettings() : input;
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSetting(SETTING_ABILITY_DESCRIPTION, abilitySettings.description, 1, null);
        }
        persist();
        return abilitySettings;
    }

    public SystemSettingsItem.TenantSettings tenantSettings() {
        return tenantSettings(1);
    }

    public SystemSettingsItem.TenantSettings tenantSettings(Integer tenantId) {
        int key = tenantId == null ? 1 : tenantId;
        SystemSettingsItem.TenantSettings settings = tenantSettingsByTenant.compute(key, (ignored, current) ->
                normalizeTenantSettings(current == null ? SystemSettingsItem.defaultTenantSettings() : current));
        if (key == 1) {
            tenantSettings = settings;
        }
        return settings;
    }

    public SystemSettingsItem.TenantSettings updateTenantSettings(SystemSettingsItem.TenantSettings input) {
        return updateTenantSettings(1, input);
    }

    public SystemSettingsItem.TenantSettings updateTenantSettings(Integer tenantId, SystemSettingsItem.TenantSettings input) {
        int key = tenantId == null ? 1 : tenantId;
        SystemSettingsItem.TenantSettings settings = normalizeTenantSettings(input);
        tenantSettingsByTenant.put(key, settings);
        if (key == 1) {
            tenantSettings = settings;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseSetting(SETTING_REPLICA_TENANT_SETTINGS, jsonSetting(settings), key, null);
        }
        persist();
        return settings;
    }

    public void clearTenantLogo(int tenantId) {
        tenant(tenantId).ifPresent(tenant -> {
            tenant.logoId = null;
            tenant.logoFileType = null;
            tenant.logoContentBase64 = null;
            tenants.put(tenant.id, tenant);
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseTenant(tenant);
            }
        });
        audit(null, "TenantSettingsAppService", "ClearLogo", "租户Logo已清理");
        persist();
    }

    public void clearTenantCustomCss(int tenantId) {
        tenant(tenantId).ifPresent(tenant -> {
            tenant.customCssId = null;
            tenant.customCssContentBase64 = null;
            tenants.put(tenant.id, tenant);
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseTenant(tenant);
            }
        });
        audit(null, "TenantSettingsAppService", "ClearCustomCss", "租户自定义CSS已清理");
        persist();
    }

    public TenantItem saveTenantLogo(int tenantId, String fileType, byte[] content) {
        TenantItem tenant = tenant(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        tenant.logoId = UUID.randomUUID().toString();
        tenant.logoFileType = safe(fileType).isBlank() ? "image/png" : fileType;
        tenant.logoContentBase64 = Base64.getEncoder().encodeToString(content);
        tenants.put(tenant.id, tenant);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseBinaryObject(databaseUuid(tenant.logoId), tenant.logoContentBase64);
            updateDatabaseTenant(tenant);
        }
        audit(null, "TenantCustomizationController", "UploadLogo", tenant.logoId);
        persist();
        return tenant;
    }

    public TenantItem saveTenantCustomCss(int tenantId, byte[] content) {
        TenantItem tenant = tenant(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        tenant.customCssId = UUID.randomUUID().toString();
        tenant.customCssContentBase64 = Base64.getEncoder().encodeToString(content);
        tenants.put(tenant.id, tenant);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseBinaryObject(databaseUuid(tenant.customCssId), tenant.customCssContentBase64);
            updateDatabaseTenant(tenant);
        }
        audit(null, "TenantCustomizationController", "UploadCustomCss", tenant.customCssId);
        persist();
        return tenant;
    }

    public Optional<TenantBinary> tenantLogo(Integer tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return tenant(tenantId)
                .filter(item -> !safe(item.logoId).isBlank() && !safe(item.logoContentBase64).isBlank())
                .map(item -> new TenantBinary(item.logoId, item.logoFileType, Base64.getDecoder().decode(item.logoContentBase64)));
    }

    public Optional<TenantBinary> tenantCustomCss(Integer tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return tenant(tenantId)
                .filter(item -> !safe(item.customCssId).isBlank() && !safe(item.customCssContentBase64).isBlank())
                .map(item -> new TenantBinary(item.customCssId, "text/css", Base64.getDecoder().decode(item.customCssContentBase64)));
    }

    public void sendTestEmail(String serviceName, String emailAddress) {
        audit(null, serviceName, "SendTestEmail", "本地模拟发送至 " + safe(emailAddress));
        persist();
    }

    public List<RoleItem> roles(String filter) {
        return roles.values().stream()
                .map(this::normalizeRole)
                .filter(item -> contains(String.join(" ", safe(item.name), safe(item.displayName)), filter))
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public Optional<RoleItem> role(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(roles.get(id)).map(this::normalizeRole);
    }

    public RoleItem saveRole(RoleItem input, List<String> grantedPermissionNames) {
        RoleItem existing = role(input.id).orElse(null);
        if (databaseStoreMode && !loadingDatabaseState) {
            input = upsertDatabaseRole(input, grantedPermissionNames, existing);
        }
        if (input.id == null) {
            input.id = roles.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
            input.creationTime = LocalDateTime.now();
        } else if (input.creationTime == null && existing != null) {
            input.creationTime = existing.creationTime;
        }
        if (input.name == null || input.name.isBlank()) {
            input.name = input.displayName == null ? "Role" + input.id : input.displayName.replaceAll("\\s+", "");
        }
        input.grantedPermissionNames = new ArrayList<>(grantedPermissionNames == null ? List.of() : grantedPermissionNames);
        input.organizationUnits = input.organizationUnits == null
                ? new ArrayList<>(existing == null ? List.of() : roleOrganizationUnits(existing))
                : new ArrayList<>(input.organizationUnits);
        roles.put(input.id, input);
        persist();
        return input;
    }

    public void deleteRole(Integer id) {
        if (id == null) {
            return;
        }
        RoleItem role = roles.get(id);
        if (role != null && !role.isStatic) {
            if (databaseStoreMode && !loadingDatabaseState) {
                softDeleteDatabaseRole(id);
            }
            roles.remove(id);
            users.values().forEach(user -> user.assignedRoleNames.removeIf(name -> equalsText(name, role.name)));
            persist();
        }
    }

    public PageResult<UserItem> users(String filter, int skipCount, int maxResultCount) {
        return users(filter, null, List.of(), false, skipCount, maxResultCount);
    }

    public PageResult<UserItem> users(String filter, Integer roleId, List<String> requiredPermissions,
                                      boolean onlyLockedUsers, int skipCount, int maxResultCount) {
        return users(filter, roleId, requiredPermissions, onlyLockedUsers, skipCount, maxResultCount, null);
    }

    public PageResult<UserItem> users(String filter, Integer roleId, List<String> requiredPermissions,
                                      boolean onlyLockedUsers, int skipCount, int maxResultCount, String sorting) {
        List<UserItem> filtered = filteredUsers(filter, roleId, requiredPermissions, onlyLockedUsers, sorting);
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    public List<UserItem> filteredUsers(String filter, Integer roleId, List<String> requiredPermissions,
                                        boolean onlyLockedUsers) {
        return filteredUsers(filter, roleId, requiredPermissions, onlyLockedUsers, null);
    }

    public List<UserItem> filteredUsers(String filter, Integer roleId, List<String> requiredPermissions,
                                        boolean onlyLockedUsers, String sorting) {
        String roleName = role(roleId).map(role -> role.name).orElse(null);
        List<String> permissionsFilter = requiredPermissions == null ? List.of() : requiredPermissions;
        return users.values().stream()
                .filter(item -> contains(String.join(" ", safe(item.name), safe(item.surname), safe(item.userName),
                        safe(item.emailAddress), safe(item.phoneNumber)), filter))
                .filter(item -> safe(roleName).isBlank() || item.assignedRoleNames.stream().anyMatch(name -> equalsText(name, roleName)))
                .filter(item -> !onlyLockedUsers || item.isLockedOut)
                .filter(item -> permissionsFilter.stream().allMatch(permission -> userHasPermission(item.id, permission)))
                .sorted(userComparator(sorting))
                .toList();
    }

    public Optional<UserItem> user(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(users.get(id));
    }

    public String updateUserSignInToken(Long userId) {
        return user(userId).map(user -> {
            user.signInToken = UUID.randomUUID().toString();
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            persist();
            return user.signInToken;
        }).orElse("");
    }

    public Optional<UserItem> userByUserName(String userName) {
        return users.values().stream()
                .filter(user -> equalsText(user.userName, userName))
                .max(userLookupComparator());
    }

    public Optional<UserItem> userByEmail(String emailAddress) {
        return users.values().stream()
                .filter(user -> equalsText(user.emailAddress, emailAddress))
                .max(userLookupComparator());
    }

    public Optional<UserItem> userByUserNameOrEmail(String value) {
        return users.values().stream()
                .filter(user -> equalsText(user.userName, value) || equalsText(user.emailAddress, value))
                .max(userLookupComparator());
    }

    private Comparator<UserItem> userLookupComparator() {
        return Comparator
                .comparing((UserItem user) -> user != null && user.isActive)
                .thenComparingInt(this::userRoleCount)
                .thenComparing(user -> user == null || user.id == null ? 0L : user.id);
    }

    private int userRoleCount(UserItem user) {
        return user == null || user.assignedRoleNames == null ? 0 : user.assignedRoleNames.size();
    }

    public Optional<TenantItem> tenantByTenancyName(String tenancyName) {
        return tenants.values().stream()
                .filter(tenant -> equalsText(tenant.tenancyName, tenancyName))
                .findFirst();
    }

    public UserItem registerUser(UserItem input, String password) {
        return registerUser(input, password, true, false);
    }

    public UserItem registerUser(UserItem input, String password, boolean isActive, boolean isEmailConfirmed) {
        UserItem user = new UserItem();
        user.name = safe(input.name).isBlank() ? "New" : input.name;
        user.surname = safe(input.surname).isBlank() ? "User" : input.surname;
        user.userName = safe(input.userName).isBlank() ? safe(input.emailAddress) : input.userName;
        user.emailAddress = safe(input.emailAddress);
        user.isActive = isActive;
        user.isEmailConfirmed = isEmailConfirmed;
        user.emailConfirmationCode = isEmailConfirmed ? null : randomCode();
        user.creationTime = LocalDateTime.now();
        user.assignedRoleNames.add("AbilityQuery");
        if (databaseStoreMode && !loadingDatabaseState) {
            user = upsertDatabaseUser(user, user.assignedRoleNames, List.of(), List.of(), password);
            upsertDatabaseUserAccount(user);
        }
        if (user.id == null) {
            user.id = users.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
        }
        users.put(user.id, user);
        userPasswords.put(user.id, safe(password).isBlank() ? "123qwe" : password);
        audit(user.id, "AccountAppService", "Register", "成功");
        persist();
        return user;
    }

    public Optional<UserItem> issuePasswordResetCode(String emailAddress) {
        return userByEmail(emailAddress).map(user -> {
            user.passwordResetCode = randomCode();
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            audit(user.id, "AccountAppService", "SendPasswordResetCode", "本地生成");
            persist();
            return user;
        });
    }

    public String setNewPasswordResetCode(Long userId) {
        return user(userId).map(user -> {
            user.passwordResetCode = randomCode();
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            persist();
            return user.passwordResetCode;
        }).orElse(null);
    }

    public Optional<UserItem> resetPassword(Long userId, String resetCode, String password) {
        return user(userId)
                .filter(user -> !safe(user.passwordResetCode).isBlank() && equalsText(user.passwordResetCode, resetCode))
                .map(user -> {
                    userPasswords.put(user.id, safe(password).isBlank() ? "123qwe" : password);
                    user.passwordResetCode = null;
                    user.isEmailConfirmed = true;
                    user.shouldChangePasswordOnNextLogin = false;
                    if (databaseStoreMode && !loadingDatabaseState) {
                        updateDatabaseUserPassword(user.id, password);
                        updateDatabaseUserSecurityFields(user);
                    }
                    audit(user.id, "AccountAppService", "ResetPassword", "成功");
                    persist();
                    return user;
                });
    }

    public Optional<UserItem> issueEmailActivationCode(String emailAddress) {
        return userByEmail(emailAddress).map(user -> {
            user.emailConfirmationCode = randomCode();
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            audit(user.id, "AccountAppService", "SendEmailActivationLink", "本地生成");
            persist();
            return user;
        });
    }

    public boolean activateEmail(Long userId, String confirmationCode) {
        return user(userId).map(user -> {
            if (user.isEmailConfirmed) {
                return true;
            }
            if (safe(user.emailConfirmationCode).isBlank()
                    || !equalsText(user.emailConfirmationCode, confirmationCode)) {
                return false;
            }
            user.isEmailConfirmed = true;
            user.emailConfirmationCode = null;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            audit(user.id, "AccountAppService", "ActivateEmail", "成功");
            persist();
            return true;
        })
                .orElse(false);
    }

    public void markLogin(Long userId) {
        user(userId).ifPresent(user -> {
            user.lastLoginTime = LocalDateTime.now().toString();
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserLastLogin(user.id);
            }
            persist();
        });
    }

    public boolean linkUsers(Long sourceUserId, Long targetUserId) {
        UserItem source = user(sourceUserId).orElse(null);
        UserItem target = user(targetUserId).orElse(null);
        if (source == null || target == null || Objects.equals(source.id, target.id)) {
            return false;
        }
        if (!source.linkedUserIds.contains(target.id)) {
            source.linkedUserIds.add(target.id);
        }
        if (!target.linkedUserIds.contains(source.id)) {
            target.linkedUserIds.add(source.id);
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            syncDatabaseLinkedUsers(source.id);
            syncDatabaseLinkedUsers(target.id);
        }
        persist();
        return true;
    }

    public PageResult<LinkedUserItem> linkedUsers(Long userId, int skipCount, int maxResultCount, String sorting) {
        List<LinkedUserItem> linked = user(userId)
                .map(user -> list(user.linkedUserIds).stream()
                        .map(this::linkedUser)
                        .flatMap(Optional::stream)
                        .sorted(linkedUserComparator(sorting))
                        .toList())
                .orElseGet(List::of);
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(linked.size(), linked.stream().skip(skip).limit(take).toList());
    }

    public List<LinkedUserItem> recentlyUsedLinkedUsers(Long userId) {
        return linkedUsers(userId, 0, 3, "TenancyName, Username").items;
    }

    public boolean hasLinkedUsers(Long userId) {
        return user(userId)
                .map(user -> !list(user.linkedUserIds).isEmpty())
                .orElse(false);
    }

    public boolean linkToUser(Long currentUserId, String userNameOrEmailAddress, String password) {
        return userByUserNameOrEmail(userNameOrEmailAddress)
                .filter(user -> !Objects.equals(user.id, currentUserId))
                .filter(user -> !user.shouldChangePasswordOnNextLogin)
                .filter(user -> passwordMatches(user.id, safe(password)))
                .map(user -> linkUsers(currentUserId, user.id))
                .orElse(false);
    }

    public boolean unlinkUser(Long currentUserId, Long targetUserId) {
        UserItem current = user(currentUserId).orElse(null);
        UserItem target = user(targetUserId).orElse(null);
        if (current == null || target == null) {
            return false;
        }
        boolean changed = current.linkedUserIds.remove(target.id);
        changed |= target.linkedUserIds.remove(current.id);
        if (changed) {
            if (databaseStoreMode && !loadingDatabaseState) {
                syncDatabaseLinkedUsers(current.id);
                syncDatabaseLinkedUsers(target.id);
            }
            persist();
        }
        return changed;
    }

    public List<UserLoginAttemptItem> userLoginAttempts(Long userId) {
        return userLoginAttempts.stream()
                .filter(item -> Objects.equals(item.userId, userId))
                .sorted(Comparator.comparing((UserLoginAttemptItem item) -> safe(item.creationTime)).reversed())
                .limit(10)
                .toList();
    }

    public void recordLoginAttempt(Long userId, String userNameOrEmail, String result,
                                   String clientIpAddress, String clientName, String browserInfo) {
        if (userId == null) {
            return;
        }
        UserLoginAttemptItem attempt = new UserLoginAttemptItem();
        attempt.id = userLoginAttempts.stream().map(item -> item.id).filter(Objects::nonNull)
                .max(Long::compareTo).orElse(0L) + 1;
        attempt.userId = userId;
        attempt.tenancyName = "default";
        attempt.userNameOrEmail = safe(userNameOrEmail).isBlank()
                ? user(userId).map(user -> user.userName).orElse("")
                : userNameOrEmail;
        attempt.clientIpAddress = safe(clientIpAddress).isBlank() ? "127.0.0.1" : clientIpAddress;
        attempt.clientName = safe(clientName).isBlank() ? "Local Browser" : clientName;
        attempt.browserInfo = safe(browserInfo);
        attempt.result = safe(result).isBlank() ? "Success" : result;
        attempt.creationTime = LocalDateTime.now().toString();
        if (databaseStoreMode && !loadingDatabaseState) {
            attempt.id = insertDatabaseLoginAttempt(attempt);
        }
        userLoginAttempts.add(attempt);
        if (userLoginAttempts.size() > 200) {
            userLoginAttempts.sort(Comparator.comparing(item -> safe(item.creationTime)));
            userLoginAttempts.subList(0, userLoginAttempts.size() - 200).clear();
        }
        persist();
    }

    public List<UserLoginAttemptItem> userLoginAttempts() {
        return userLoginAttempts.stream()
                .sorted(Comparator.comparing((UserLoginAttemptItem item) -> safe(item.creationTime)).reversed())
                .toList();
    }

    /** Checks the local demo password store without exposing it through UserItem. */
    public boolean passwordMatches(Long userId, String password) {
        if (userId == null) {
            return false;
        }
        return Objects.equals(userPasswords.getOrDefault(userId, "123qwe"), safe(password));
    }

    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        if (!passwordMatches(userId, currentPassword)) {
            return false;
        }
        userPasswords.put(userId, newPassword);
        user(userId).ifPresent(user -> user.shouldChangePasswordOnNextLogin = false);
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseUserPassword(userId, newPassword);
        }
        audit(userId, "ProfileAppService", "ChangePassword", "成功");
        persist();
        return true;
    }

    public Optional<UserItem> updateCurrentUserProfile(Long userId, UserItem input) {
        return user(userId).map(user -> {
            user.name = safe(input.name).isBlank() ? user.name : input.name;
            user.surname = input.surname;
            user.emailAddress = safe(input.emailAddress).isBlank() ? user.emailAddress : input.emailAddress;
            if (!equalsText(user.phoneNumber, input.phoneNumber)) {
                user.isPhoneNumberConfirmed = false;
            }
            user.phoneNumber = input.phoneNumber;
            user.engName = input.engName;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserProfile(user);
                upsertDatabaseUserAccount(user);
            }
            audit(user.id, "ProfileAppService", "UpdateCurrentUserProfile", "成功");
            persist();
            return user;
        });
    }

    public String profilePicture(Long userId) {
        return user(userId)
                .map(user -> profilePictureById(user.profilePictureId))
                .orElse("");
    }

    public String profilePictureById(UUID profilePictureId) {
        if (profilePictureId == null) {
            return "";
        }
        return profilePictures.getOrDefault(profilePictureId.toString(), "");
    }

    public Optional<UUID> updateProfilePicture(Long userId, String fileToken) {
        return user(userId).map(user -> {
            UUID id = user.profilePictureId == null ? UUID.randomUUID() : user.profilePictureId;
            user.profilePictureId = id;
            String picture = normalizeProfilePicture(user, fileToken);
            profilePictures.put(id.toString(), picture);
            if (databaseStoreMode && !loadingDatabaseState) {
                upsertDatabaseBinaryObject(id, picture);
                updateDatabaseUserProfile(user);
            }
            audit(user.id, "ProfileAppService", "UpdateProfilePicture", "成功");
            persist();
            return id;
        });
    }

    public Optional<String> updateGoogleAuthenticatorKey(Long userId) {
        return user(userId).map(user -> {
            user.googleAuthenticatorKey = randomCode().substring(0, 10);
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            audit(user.id, "ProfileAppService", "UpdateGoogleAuthenticatorKey", "成功");
            persist();
            return user.googleAuthenticatorKey;
        });
    }

    public void disableGoogleAuthenticator(Long userId) {
        user(userId).ifPresent(user -> {
            user.googleAuthenticatorKey = null;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserSecurityFields(user);
            }
            audit(user.id, "ProfileAppService", "DisableGoogleAuthenticator", "成功");
            persist();
        });
    }

    public void changeUserLanguage(Long userId, String languageName) {
        user(userId).ifPresent(user -> {
            user.preferredLanguageName = safe(languageName).isBlank() ? "zh-Hans" : languageName;
            if (databaseStoreMode && !loadingDatabaseState) {
                upsertDatabaseSetting("Abp.Localization.DefaultLanguageName", user.preferredLanguageName, 1, user.id);
            }
            audit(user.id, "ProfileAppService", "ChangeLanguage", user.preferredLanguageName);
            persist();
        });
    }

    public String sendVerificationSms(Long userId, String phoneNumber) {
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        user(userId).ifPresent(user -> {
            user.lastSmsVerificationCode = code;
            if (databaseStoreMode && !loadingDatabaseState) {
                upsertDatabaseSetting("Replica.Profile.SmsCode", code, 1, user.id);
            }
            audit(user.id, "ProfileAppService", "SendVerificationSms", "验证码已发送");
            persist();
        });
        return code;
    }

    public boolean verifySmsCode(Long userId, String phoneNumber, String code) {
        return user(userId).map(user -> {
            boolean matches = equalsText(user.lastSmsVerificationCode, code);
            if (matches) {
                user.phoneNumber = phoneNumber;
                user.isPhoneNumberConfirmed = true;
                user.lastSmsVerificationCode = null;
                if (databaseStoreMode && !loadingDatabaseState) {
                    updateDatabaseUserProfile(user);
                    upsertDatabaseSetting("Replica.Profile.SmsCode", "", 1, user.id);
                }
                audit(user.id, "ProfileAppService", "VerifySmsCode", "成功");
                persist();
            }
            return matches;
        }).orElse(false);
    }

    public void prepareCollectedData(Long userId) {
        prepareCollectedData(userId, null);
    }

    public void prepareCollectedData(Long userId, UUID binaryObjectId) {
        user(userId).ifPresent(user -> {
            user.collectedDataPreparedTime = LocalDateTime.now().toString();
            if (binaryObjectId != null) {
                NotificationItem notification = addNotification(user.id, "App.GdprDataPrepared",
                        "Your data is prepared, click here to download.", "Info", true);
                notification.data.put("binaryObjectId", binaryObjectId.toString());
                syncDatabaseNotificationData(notification);
            }
            audit(user.id, "ProfileAppService", "PrepareCollectedData", "本地个人数据已准备");
            persist();
        });
    }

    public void notifyInvalidUserImport(Long userId, FileDto file) {
        if (file == null) {
            return;
        }
        user(userId).ifPresent(user -> {
            NotificationItem notification = addNotification(user.id, "App.DownloadInvalidImportUsers",
                    "ClickToSeeInvalidUsers", "Info", true);
            notification.data.put("fileToken", file.fileToken);
            notification.data.put("fileType", file.fileType);
            notification.data.put("fileName", file.fileName);
            syncDatabaseNotificationData(notification);
            persist();
        });
    }

    public void notifyUserImportSucceeded(Long userId) {
        user(userId).ifPresent(user -> {
            addNotification(user.id, "App.SimpleMessage",
                    "User import process has been completed successfully. All users in file are imported.",
                    "Success", true);
            persist();
        });
    }

    public void notifyUserImportFileInvalid(Long userId) {
        user(userId).ifPresent(user -> {
            addNotification(user.id, "App.SimpleMessage",
                    "User import process has failed. File is invalid.",
                    "Warn", true);
            persist();
        });
    }

    public void notifySimpleMessage(Long userId, String message, String severity) {
        user(userId).ifPresent(user -> {
            addNotification(user.id, "App.SimpleMessage",
                    safe(message).isBlank() ? "Test notification" : message,
                    safe(severity).isBlank() ? "Info" : severity,
                    true);
            persist();
        });
    }

    /** Combines role grants for the current user, matching ABP's permission model. */
    public List<String> permissionsForUser(Long userId) {
        return user(userId)
                .map(user -> java.util.stream.Stream.concat(
                        user.assignedRoleNames.stream()
                        .map(this::roleByName)
                        .flatMap(Optional::stream)
                        .flatMap(role -> role.grantedPermissionNames.stream())
                        .flatMap(permission -> expandPermission(permission).stream()),
                        userSpecificPermissionNames(user.id).stream()
                                .flatMap(permission -> expandPermission(permission).stream()))
                        .distinct()
                        .sorted()
                        .toList())
                .orElseGet(List::of);
    }

    public List<String> userSpecificPermissionNames(Long userId) {
        return new ArrayList<>(userSpecificPermissions.getOrDefault(userId, List.of()));
    }

    public void updateUserPermissions(Long userId, List<String> grantedPermissionNames) {
        if (user(userId).isEmpty()) {
            return;
        }
        List<String> validNames = new ArrayList<>(grantedPermissionNames == null ? List.of() : grantedPermissionNames);
        validNames.removeIf(name -> permissions.stream().noneMatch(permission -> equalsText(permission.name, name)));
        userSpecificPermissions.put(userId, validNames.stream().distinct().sorted().toList());
        if (databaseStoreMode && !loadingDatabaseState) {
            replaceDatabaseUserPermissions(userId, validNames);
        }
        audit(userId, "UserAppService", "UpdateUserPermissions", "成功");
        persist();
    }

    public void resetUserSpecificPermissions(Long userId) {
        if (userSpecificPermissions.remove(userId) != null) {
            if (databaseStoreMode && !loadingDatabaseState) {
                replaceDatabaseUserPermissions(userId, List.of());
            }
            audit(userId, "UserAppService", "ResetUserSpecificPermissions", "成功");
            persist();
        }
    }

    public boolean userHasPermission(Long userId, String permission) {
        return permission == null || permission.isBlank() || permissionsForUser(userId).contains(permission);
    }

    public String createRandomPassword(Integer tenantId) {
        SystemSettingsItem.SecuritySettings security = tenantSettings(tenantId).security;
        SystemSettingsItem.PasswordComplexitySetting complexity = security.useDefaultPasswordComplexitySettings
                ? security.defaultPasswordComplexity
                : security.passwordComplexity;
        return randomPassword(complexity);
    }

    public UserItem saveUser(UserItem input, List<String> assignedRoleNames, List<Long> organizationUnits, List<UUID> labs) {
        String submittedPassword = safe(input.password);
        if (databaseStoreMode && !loadingDatabaseState) {
            input = upsertDatabaseUser(input, assignedRoleNames, organizationUnits, labs, submittedPassword);
        }
        if (input.id == null) {
            input.id = users.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
            input.creationTime = LocalDateTime.now();
            input.isEmailConfirmed = true;
        }
        input.assignedRoleNames = new ArrayList<>(assignedRoleNames == null ? List.of() : assignedRoleNames);
        input.organizationUnits = new ArrayList<>(organizationUnits == null ? List.of() : organizationUnits);
        input.labs = new ArrayList<>(labs == null ? List.of() : labs);
        users.put(input.id, input);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseUserAccount(input);
        }
        if (submittedPassword.isBlank()) {
            userPasswords.putIfAbsent(input.id, "123qwe");
        } else {
            userPasswords.put(input.id, submittedPassword);
            input.password = null;
        }
        persist();
        return input;
    }

    private String randomPassword(SystemSettingsItem.PasswordComplexitySetting complexity) {
        SystemSettingsItem.PasswordComplexitySetting setting = complexity == null
                ? new SystemSettingsItem.PasswordComplexitySetting()
                : complexity;
        String upperCaseLetters = "ABCDEFGHJKLMNOPQRSTUVWXYZ";
        String lowerCaseLetters = "abcdefghijkmnopqrstuvwxyz";
        String digits = "0123456789";
        String nonAlphanumerics = "!@$?_-";
        String[] randomChars = {upperCaseLetters, lowerCaseLetters, digits, nonAlphanumerics};
        List<Character> chars = new ArrayList<>();
        if (setting.requireUppercase) {
            insertRandom(chars, randomChar(upperCaseLetters));
        }
        if (setting.requireLowercase) {
            insertRandom(chars, randomChar(lowerCaseLetters));
        }
        if (setting.requireDigit) {
            insertRandom(chars, randomChar(digits));
        }
        if (setting.requireNonAlphanumeric) {
            insertRandom(chars, randomChar(nonAlphanumerics));
        }
        for (int i = chars.size(); i < setting.requiredLength; i++) {
            String pool = randomChars[ThreadLocalRandom.current().nextInt(randomChars.length)];
            insertRandom(chars, randomChar(pool));
        }
        StringBuilder builder = new StringBuilder(chars.size());
        chars.forEach(builder::append);
        return builder.toString();
    }

    private char randomChar(String source) {
        return source.charAt(ThreadLocalRandom.current().nextInt(source.length()));
    }

    private void insertRandom(List<Character> chars, char value) {
        int index = chars.isEmpty() ? 0 : ThreadLocalRandom.current().nextInt(chars.size() + 1);
        chars.add(index, value);
    }

    public UserItem saveImportedUser(UserItem input, List<String> assignedRoleNames, String password) {
        String importPassword = safe(password).isBlank() ? "123qwe" : password;
        input.password = importPassword;
        UserItem saved = saveUser(input, assignedRoleNames, List.of(), List.of());
        audit(saved.id, "UsersController", "ImportFromExcel", saved.userName);
        persist();
        return saved;
    }

    public void resetUserPassword(Long userId) {
        user(userId).ifPresent(user -> {
            userPasswords.put(user.id, "qazwsxEDCRFV");
            user.isLockedOut = false;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserPassword(user.id, "qazwsxEDCRFV");
                updateDatabaseUserLockout(user.id, false);
            }
            audit(user.id, "UserAppService", "ResetUserPassword", "成功");
            persist();
        });
    }

    public void unlockUser(Long userId) {
        user(userId).ifPresent(user -> {
            user.isLockedOut = false;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseUserLockout(user.id, false);
            }
            audit(user.id, "UserAppService", "UnlockUser", "成功");
            persist();
        });
    }

    public List<UserDelegation> userDelegations() {
        return userDelegations.values().stream()
                .map(this::decorateDelegation)
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public PageResult<UserDelegation> delegatedUsers(Long sourceUserId, String filter, int skipCount, int maxResultCount) {
        return delegatedUsers(sourceUserId, filter, skipCount, maxResultCount, null);
    }

    public PageResult<UserDelegation> delegatedUsers(Long sourceUserId, String filter, int skipCount, int maxResultCount,
                                                     String sorting) {
        List<UserDelegation> filtered = userDelegations.values().stream()
                .filter(item -> Objects.equals(item.sourceUserId, sourceUserId))
                .map(this::decorateDelegation)
                .filter(item -> contains(String.join(" ", safe(item.targetUserName), safe(item.targetName)), filter))
                .sorted(userDelegationComparator(sorting))
                .toList();
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    public List<UserDelegation> activeUserDelegations(Long targetUserId) {
        LocalDateTime now = LocalDateTime.now();
        return userDelegations.values().stream()
                .filter(item -> Objects.equals(item.targetUserId, targetUserId))
                .map(this::decorateDelegation)
                .filter(item -> isDelegationActive(item, now))
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public boolean hasActiveDelegation(Long sourceUserId, Long targetUserId) {
        if (sourceUserId == null || targetUserId == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return userDelegations.values().stream()
                .anyMatch(item -> Objects.equals(item.sourceUserId, sourceUserId)
                        && Objects.equals(item.targetUserId, targetUserId)
                        && isDelegationActive(item, now));
    }

    public Optional<UserDelegation> delegateNewUser(Long sourceUserId, Integer tenantId, Long targetUserId, String startTime, String endTime) {
        if (sourceUserId == null || targetUserId == null || Objects.equals(sourceUserId, targetUserId)) {
            return Optional.empty();
        }
        UserItem target = user(targetUserId).orElse(null);
        if (target == null || !target.isActive) {
            return Optional.empty();
        }
        UserDelegation delegation = new UserDelegation();
        delegation.id = userDelegations.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
        delegation.sourceUserId = sourceUserId;
        delegation.targetUserId = targetUserId;
        delegation.tenantId = tenantId;
        delegation.startTime = safe(startTime).isBlank() ? LocalDateTime.now().toString() : startTime;
        delegation.endTime = safe(endTime).isBlank() ? LocalDateTime.now().plusDays(7).toString() : endTime;
        if (databaseStoreMode && !loadingDatabaseState) {
            delegation.id = insertDatabaseUserDelegation(delegation);
        }
        userDelegations.put(delegation.id, decorateDelegation(delegation));
        audit(sourceUserId, "UserDelegationAppService", "DelegateNewUser", "成功");
        persist();
        return Optional.of(decorateDelegation(delegation));
    }

    public boolean removeDelegation(Long sourceUserId, Long delegationId) {
        UserDelegation delegation = delegationId == null ? null : userDelegations.get(delegationId);
        // Original manager only deletes delegations owned by the current source user.
        if (delegation == null || !Objects.equals(delegation.sourceUserId, sourceUserId)) {
            return false;
        }
        userDelegations.remove(delegationId);
        if (databaseStoreMode && !loadingDatabaseState) {
            softDeleteDatabaseUserDelegation(sourceUserId, delegationId);
        }
        audit(sourceUserId, "UserDelegationAppService", "RemoveDelegation", "成功");
        persist();
        return true;
    }

    public List<FriendItem> friendships() {
        return friendships.values().stream()
                .map(this::decorateFriend)
                .sorted(Comparator.comparing((FriendItem item) -> safe(item.friendUserName)))
                .toList();
    }

    public List<ChatMessageItem> chatMessages() {
        return chatMessages.values().stream()
                .sorted(Comparator.comparing(item -> item.id))
                .toList();
    }

    public List<FriendItem> chatFriends(Long userId) {
        return friendships.values().stream()
                .filter(item -> Objects.equals(item.userId, userId))
                .map(this::decorateFriend)
                .sorted(Comparator.comparing((FriendItem item) -> item.state).thenComparing(item -> safe(item.friendUserName)))
                .toList();
    }

    public List<ChatMessageItem> chatMessages(Long userId, Long targetUserId, Integer targetTenantId, Long minMessageId) {
        List<ChatMessageItem> messages = chatMessages.values().stream()
                .filter(item -> Objects.equals(item.userId, userId))
                .filter(item -> Objects.equals(item.targetUserId, targetUserId))
                .filter(item -> Objects.equals(item.targetTenantId, targetTenantId))
                // Original ChatAppService loads older messages with Id < MinMessageId.
                .filter(item -> minMessageId == null || item.id < minMessageId)
                .sorted(Comparator.comparing((ChatMessageItem item) -> item.id).reversed())
                .limit(50)
                .toList();
        List<ChatMessageItem> ordered = new ArrayList<>(messages);
        Collections.reverse(ordered);
        return ordered;
    }

    public ChatMessageItem sendChatMessage(Long userId, Long targetUserId, String message) {
        if (userId == null || targetUserId == null || safe(message).isBlank()) {
            return null;
        }
        UserItem sender = user(userId).orElse(null);
        UserItem target = user(targetUserId).orElse(null);
        if (sender == null || target == null || Objects.equals(userId, targetUserId)) {
            return null;
        }
        boolean receiverBlockedSender = isFriendBlocked(targetUserId, userId, null);
        ensureFriendship(userId, null, targetUserId, null, 1);
        // Original ChatMessageManager skips the receiver copy if the receiver blocked the sender.
        if (!receiverBlockedSender) {
            ensureFriendship(targetUserId, null, userId, null, 1);
        }
        ChatMessageItem senderCopy = chatMessage(userId, targetUserId, 1, 2, 1, message, UUID.randomUUID().toString());
        if (databaseStoreMode && !loadingDatabaseState) {
            senderCopy.id = insertDatabaseChatMessage(senderCopy);
        }
        chatMessages.put(senderCopy.id, senderCopy);
        if (!receiverBlockedSender) {
            ChatMessageItem receiverCopy = chatMessage(targetUserId, userId, 2, 1, 2, message, senderCopy.sharedMessageId);
            if (databaseStoreMode && !loadingDatabaseState) {
                receiverCopy.id = insertDatabaseChatMessage(receiverCopy);
            }
            chatMessages.put(receiverCopy.id, receiverCopy);
        }
        audit(userId, "ChatHub", "SendMessage", "本地模拟成功");
        persist();
        return senderCopy;
    }

    public void markChatMessagesRead(Long userId, Long targetUserId, Integer targetTenantId) {
        chatMessages.values().stream()
                .filter(item -> Objects.equals(item.userId, userId))
                .filter(item -> Objects.equals(item.targetUserId, targetUserId))
                .filter(item -> Objects.equals(item.targetTenantId, targetTenantId))
                .filter(item -> item.readState == 1)
                .forEach(item -> {
                    item.readState = 2;
                    chatMessages.values().stream()
                            .filter(other -> equalsText(other.sharedMessageId, item.sharedMessageId))
                            .filter(other -> Objects.equals(other.userId, targetUserId))
                            .findFirst()
                            .ifPresent(other -> other.receiverReadState = 2);
                    if (databaseStoreMode && !loadingDatabaseState) {
                        updateDatabaseChatMessageReadState(item);
                        chatMessages.values().stream()
                                .filter(other -> equalsText(other.sharedMessageId, item.sharedMessageId))
                                .filter(other -> Objects.equals(other.userId, targetUserId))
                                .findFirst()
                                .ifPresent(this::updateDatabaseChatMessageReadState);
                    }
                });
        persist();
    }

    public Optional<FriendItem> createFriendshipRequest(Long userId, Long friendUserId, Integer friendTenantId) {
        if (userId == null || friendUserId == null || Objects.equals(userId, friendUserId)) {
            return Optional.empty();
        }
        if (user(friendUserId).isEmpty()) {
            return Optional.empty();
        }
        FriendItem friend = ensureFriendship(userId, null, friendUserId, friendTenantId, 1);
        ensureFriendship(friendUserId, friendTenantId, userId, null, 1);
        audit(userId, "FriendshipAppService", "CreateFriendshipRequest", "成功");
        persist();
        return Optional.of(decorateFriend(friend));
    }

    public boolean friendshipExists(Long userId, Long friendUserId, Integer friendTenantId) {
        return friendships.containsKey(friendshipKey(userId, null, friendUserId, friendTenantId));
    }

    public boolean isFriendBlocked(Long userId, Long friendUserId, Integer friendTenantId) {
        FriendItem friendship = friendships.get(friendshipKey(userId, null, friendUserId, friendTenantId));
        return friendship != null && friendship.state == 2;
    }

    public Optional<FriendItem> createFriendshipRequestByUserName(Long userId, String userName) {
        return userByUserName(userName).flatMap(user -> createFriendshipRequest(userId, user.id, null));
    }

    public boolean blockFriend(Long userId, Long friendUserId, Integer friendTenantId) {
        if (!updateFriendshipState(userId, friendUserId, friendTenantId, 2)) {
            return false;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseFriendshipState(userId, null, friendUserId, friendTenantId, 2);
        }
        audit(userId, "FriendshipAppService", "BlockUser", "成功");
        persist();
        return true;
    }

    public boolean unblockFriend(Long userId, Long friendUserId, Integer friendTenantId) {
        if (!updateFriendshipState(userId, friendUserId, friendTenantId, 1)) {
            return false;
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseFriendshipState(userId, null, friendUserId, friendTenantId, 1);
        }
        audit(userId, "FriendshipAppService", "UnblockUser", "成功");
        persist();
        return true;
    }

    public boolean acceptFriendship(Long userId, Long friendUserId, Integer friendTenantId) {
        if (!updateFriendshipState(userId, friendUserId, friendTenantId, 1)) {
            return false;
        }
        FriendItem friendFriendship = friendships.get(friendshipKey(friendUserId, friendTenantId, userId, null));
        if (friendFriendship != null) {
            friendFriendship.state = 1;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseFriendshipState(friendUserId, friendTenantId, userId, null, 1);
            }
        }
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseFriendshipState(userId, null, friendUserId, friendTenantId, 1);
        }
        audit(userId, "FriendshipAppService", "AcceptFriendshipRequest", "成功");
        persist();
        return true;
    }

    private boolean updateFriendshipState(Long userId, Long friendUserId, Integer friendTenantId, int state) {
        if (userId == null || friendUserId == null) {
            return false;
        }
        FriendItem friendship = friendships.get(friendshipKey(userId, null, friendUserId, friendTenantId));
        if (friendship == null) {
            return false;
        }
        friendship.state = state;
        return true;
    }

    private Optional<RoleItem> roleByName(String name) {
        return roles.values().stream()
                .filter(role -> equalsText(role.name, name))
                .max(roleLookupComparator());
    }

    private Comparator<RoleItem> roleLookupComparator() {
        return Comparator
                .comparingInt(this::rolePermissionCount)
                .thenComparing(role -> role == null || role.id == null ? 0 : role.id);
    }

    private int rolePermissionCount(RoleItem role) {
        return role == null || role.grantedPermissionNames == null ? 0 : role.grantedPermissionNames.size();
    }

    private List<String> expandPermission(String permissionName) {
        List<String> values = new ArrayList<>();
        String current = permissionName;
        while (current != null && !current.isBlank()) {
            if (!values.contains(current)) {
                values.add(current);
            }
            current = permissionParent(current);
        }
        return values;
    }

    private String permissionParent(String permissionName) {
        return permissions.stream()
                .filter(permission -> equalsText(permission.name, permissionName))
                .map(permission -> permission.parentName)
                .filter(parentName -> parentName != null && !parentName.isBlank())
                .findFirst()
                .orElse(null);
    }

    public void deleteUser(Long id) {
        if (id != null && id != 1L) {
            if (databaseStoreMode && !loadingDatabaseState) {
                softDeleteDatabaseUser(id);
            }
            users.remove(id);
            userPasswords.remove(id);
            userSpecificPermissions.remove(id);
            userDelegations.values().removeIf(item -> Objects.equals(item.sourceUserId, id) || Objects.equals(item.targetUserId, id));
            friendships.values().removeIf(item -> Objects.equals(item.userId, id) || Objects.equals(item.friendUserId, id));
            chatMessages.values().removeIf(item -> Objects.equals(item.userId, id) || Objects.equals(item.targetUserId, id));
            persist();
        }
    }

    public List<UserItem> organizationUsers(Long orgId) {
        return users.values().stream()
                .filter(user -> orgId == null || user.organizationUnits.contains(orgId))
                .sorted(Comparator.comparing(user -> user.id))
                .toList();
    }

    public List<RoleItem> organizationRoles(Long orgId) {
        return roles.values().stream()
                .map(this::normalizeRole)
                .filter(role -> orgId == null || roleOrganizationUnits(role).contains(orgId))
                .sorted(Comparator.comparing(role -> role.id))
                .toList();
    }

    public List<UserItem> findOrganizationUsers(Long orgId, String filter) {
        return users.values().stream()
                .filter(user -> orgId == null || !user.organizationUnits.contains(orgId))
                .filter(user -> contains(String.join(" ", safe(user.name), safe(user.surname), safe(user.userName),
                        safe(user.emailAddress)), filter))
                .sorted(Comparator.comparing(user -> safe(user.name)))
                .toList();
    }

    public List<RoleItem> findOrganizationRoles(Long orgId, String filter) {
        return roles.values().stream()
                .map(this::normalizeRole)
                .filter(role -> orgId == null || !roleOrganizationUnits(role).contains(orgId))
                .filter(role -> contains(String.join(" ", safe(role.name), safe(role.displayName)), filter))
                .sorted(Comparator.comparing(role -> safe(role.displayName)))
                .toList();
    }

    public void addUsersToOrganization(Long orgId, List<Long> userIds) {
        if (orgId == null || userIds == null) {
            return;
        }
        userIds.forEach(id -> user(id).ifPresent(user -> {
            if (!user.organizationUnits.contains(orgId)) {
                user.organizationUnits.add(orgId);
                if (databaseStoreMode && !loadingDatabaseState) {
                    addDatabaseUserOrganization(orgId, id);
                }
                persist();
            }
        }));
    }

    public void removeUserFromOrganization(Long orgId, Long userId) {
        user(userId).ifPresent(user -> {
            if (user.organizationUnits.remove(orgId)) {
                if (databaseStoreMode && !loadingDatabaseState) {
                    softDeleteDatabaseUserOrganization(orgId, userId);
                }
                persist();
            }
        });
    }

    public void addRolesToOrganization(Long orgId, List<Integer> roleIds) {
        if (orgId == null || roleIds == null) {
            return;
        }
        roleIds.forEach(id -> role(id).ifPresent(role -> {
            if (!roleOrganizationUnits(role).contains(orgId)) {
                role.organizationUnits.add(orgId);
                roles.put(role.id, role);
                if (databaseStoreMode && !loadingDatabaseState) {
                    addDatabaseRoleOrganization(orgId, id);
                }
                persist();
            }
        }));
    }

    public void removeRoleFromOrganization(Long orgId, Integer roleId) {
        role(roleId).ifPresent(role -> {
            if (roleOrganizationUnits(role).remove(orgId)) {
                roles.put(role.id, role);
                if (databaseStoreMode && !loadingDatabaseState) {
                    softDeleteDatabaseRoleOrganization(orgId, roleId);
                }
                persist();
            }
        });
    }

    public OrgAbilitySetting orgSetting(long orgId) {
        OrgAbilitySetting existing = orgSettings.get(orgId);
        if (existing != null) {
            return existing;
        }
        OrgAbilitySetting setting = new OrgAbilitySetting();
        setting.orgId = orgId;
        setting.propertyName.addAll(defaultPropertyNames());
        setting.isPublic = false;
        setting.description = "";
        orgSettings.put(orgId, setting);
        persist();
        return setting;
    }

    public boolean hasOrgAbilitySetting(long orgId) {
        return orgSettings.containsKey(orgId);
    }

    public boolean hasOrganizationUnit(long orgId) {
        return orgUnits.stream().anyMatch(org -> Objects.equals(org.id, orgId));
    }

    public OrgAbilitySetting saveOrgSetting(OrgAbilitySetting input) {
        input.propertyName = scopedAbilityPropertyNames(input.orgId, input.propertyName);
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseOrgSetting(input);
        }
        orgSettings.put(input.orgId, input);
        persist();
        return input;
    }

    public List<AbilityProperty> abilityProperties(long orgId) {
        OrgAbilitySetting setting = orgSetting(orgId);
        return defaultProperties().stream().peek(prop -> prop.enabled = propertyEnabled(setting.propertyName, prop.name)).toList();
    }

    /** Returns only fields applicable to the selected business line. */
    public List<AbilityProperty> orgAbilityProperties(long orgId) {
        Set<String> enabledProperties = new LinkedHashSet<>(orgSettingPropertyNames(orgId));
        return defaultProperties().stream()
                .filter(property -> isLabGroup(orgId) || !isLabGroupOnlyProperty(property.camelCase))
                .peek(property -> property.enabled = enabledProperties.contains(property.camelCase))
                .toList();
    }

    private MyOrgSettingDto toMyOrgSetting(OrganizationUnit org, OrgAbilitySetting setting) {
        MyOrgSettingDto dto = new MyOrgSettingDto();
        dto.orgId = org.id;
        dto.orgName = org.displayName;
        dto.propertyList = scopedAbilityPropertyNames(org.id, setting.propertyName);
        dto.lab = new ArrayList<>(setting.lab);
        dto.description = setting.description;
        dto.isPublic = setting.isPublic;
        return dto;
    }

    public List<String> orgSettingPropertyNames(long orgId) {
        return scopedAbilityPropertyNames(orgId, orgSetting(orgId).propertyName);
    }

    private List<String> scopedAbilityPropertyNames(long orgId, Collection<String> propertyNames) {
        return (propertyNames == null ? List.<String>of() : propertyNames).stream()
                .map(this::abilityPropertyCamelCase)
                .filter(property -> isLabGroup(orgId) || !isLabGroupOnlyProperty(property))
                .distinct()
                .toList();
    }

    private boolean isLabGroup(long orgId) {
        return orgUnits.stream()
                .filter(org -> Objects.equals(org.id, orgId))
                .anyMatch(org -> equalsText(org.displayName, "Lab Group"));
    }

    private boolean isLabGroupOnlyProperty(String propertyName) {
        return LAB_GROUP_ONLY_ABILITY_PROPERTIES.contains(abilityPropertyCamelCase(propertyName));
    }

    public List<AbilityHistoryItem> history() {
        return history(null);
    }

    public List<AbilityHistoryItem> history(String sorting) {
        if (databaseStoreMode) {
            return databaseEntityChanges(PRODUCTION_ABILITY_ENTITY, null).stream()
                    .map(this::toAbilityHistoryChange)
                    .sorted(abilityHistoryComparator(sorting))
                    .toList();
        }
        List<AbilityHistoryItem> rows = entityChanges.stream()
                .filter(item -> isAbilityEntity(item.entityTypeFullName))
                .map(this::toAbilityHistoryChange)
                .sorted(abilityHistoryComparator(sorting))
                .toList();
        return rows.isEmpty() ? history.stream().sorted(abilityHistoryComparator(sorting)).toList() : rows;
    }

    public PageResult<AbilityHistoryItem> abilityHistoryPage(String sorting, int skipCount, int maxResultCount) {
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : Math.min(maxResultCount, 10_000);
        if (databaseStoreMode) {
            EntityChangeQuery query = databaseEntityChangeQuery(PRODUCTION_ABILITY_ENTITY, null, null, null, null);
            Long total = jdbcTemplate.queryForObject("SELECT COUNT_BIG(*) " + query.fromWhere(), Long.class,
                    query.args().toArray());
            List<Object> pageArgs = new ArrayList<>(query.args());
            pageArgs.add(skip);
            pageArgs.add(take);
            List<AbilityHistoryItem> items = jdbcTemplate.queryForList(databaseEntityChangeSelect()
                            + query.fromWhere()
                            + " ORDER BY " + entityChangeOrderBy(sorting)
                            + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    pageArgs.toArray()).stream()
                    .map(this::databaseEntityChange)
                    .map(this::toAbilityHistoryChange)
                    .toList();
            return new PageResult<>(total == null ? 0 : Math.toIntExact(total), items);
        }
        List<AbilityHistoryItem> rows = history(sorting);
        return new PageResult<>(rows.size(), page(rows, skip, take));
    }

    public List<AbilityHistoryItem> abilityHistoryForAbility(String abilityId) {
        if (safe(abilityId).isBlank()) {
            return List.of();
        }
        if (databaseStoreMode) {
            return databaseEntityChanges(PRODUCTION_ABILITY_ENTITY, abilityId).stream()
                    .flatMap(change -> entityPropertyChangesForDisplay(change).stream()
                            .map(item -> toAbilityHistoryProperty(change, item)))
                    .filter(item -> !safe(item.displayName).isBlank())
                    .sorted(Comparator.comparing((AbilityHistoryItem item) -> safe(item.changeTime)).reversed())
                    .toList();
        }
        Map<Long, EntityChangeItem> abilityChanges = entityChanges.stream()
                .filter(item -> isAbilityEntity(item.entityTypeFullName))
                .filter(item -> equalsEntityId(item.entityId, abilityId))
                .collect(Collectors.toMap(item -> item.id, item -> item, (left, right) -> left, LinkedHashMap::new));
        if (abilityChanges.isEmpty()) {
            return List.of();
        }
        return abilityChanges.values().stream()
                .flatMap(change -> entityPropertyChangesForDisplay(change).stream()
                        .map(item -> toAbilityHistoryProperty(change, item)))
                .filter(item -> !safe(item.displayName).isBlank())
                .sorted(Comparator.comparing((AbilityHistoryItem item) -> safe(item.changeTime)).reversed())
                .toList();
    }

    public List<AuditLog> auditLogs() {
        return auditLogs;
    }

    public PageResult<AuditLog> auditLogs(GetAuditLogsInput input) {
        GetAuditLogsInput safeInput = input == null ? new GetAuditLogsInput() : input;
        if (databaseStoreMode) {
            return databaseAuditLogs(safeInput);
        }
        List<AuditLog> filtered = filteredAuditLogs(safeInput);
        return new PageResult<>(filtered.size(), page(filtered, safeInput.skipCount, safeInput.maxResultCount));
    }

    public List<AuditLog> filteredAuditLogs(GetAuditLogsInput input) {
        GetAuditLogsInput safeInput = input == null ? new GetAuditLogsInput() : input;
        if (databaseStoreMode) {
            return databaseAuditLogRows(safeInput, Math.max(safeInput.skipCount, 0), Math.max(safeInput.maxResultCount, 10)).items();
        }
        Optional<LocalDateTime> start = parseStartDateTime(safeInput.startDate);
        Optional<LocalDateTime> end = parseEndDateTime(safeInput.endDate);
        return auditLogs.stream()
                .filter(item -> between(item.executionTime, start, end))
                .filter(item -> contains(item.userName, safeInput.userName))
                .filter(item -> contains(item.serviceName, safeInput.serviceName))
                .filter(item -> contains(item.methodName, safeInput.methodName))
                .filter(item -> contains(item.browserInfo, safeInput.browserInfo))
                .filter(item -> matchException(item, safeInput.hasException))
                .filter(item -> item.executionDuration == null || safeInput.minExecutionDuration == null
                        || item.executionDuration >= safeInput.minExecutionDuration)
                .filter(item -> item.executionDuration == null || safeInput.maxExecutionDuration == null
                        || item.executionDuration <= safeInput.maxExecutionDuration)
                .sorted(auditLogComparator(safeInput.sorting))
                .toList();
    }

    private PageResult<AuditLog> databaseAuditLogs(GetAuditLogsInput input) {
        int skip = Math.max(input.skipCount, 0);
        int take = input.maxResultCount <= 0 ? 10 : Math.min(input.maxResultCount, 10_000);
        AuditLogQueryResult rows = databaseAuditLogRows(input, skip, take);
        return new PageResult<>(rows.totalCount(), rows.items());
    }

    private AuditLogQueryResult databaseAuditLogRows(GetAuditLogsInput input, int skip, int take) {
        StringBuilder where = new StringBuilder(" WHERE (l.TenantId = 1 OR l.TenantId IS NULL)");
        List<Object> args = new ArrayList<>();
        appendDateFilter(where, args, "l.ExecutionTime", ">=", parseStartDateTime(input.startDate));
        appendDateFilter(where, args, "l.ExecutionTime", "<=", parseEndDateTime(input.endDate));
        appendLikeFilter(where, args, "l.ServiceName", input.serviceName);
        appendLikeFilter(where, args, "l.MethodName", input.methodName);
        appendLikeFilter(where, args, "l.BrowserInfo", input.browserInfo);
        appendAuditUserFilter(where, args, input.userName);
        if (input.hasException != null) {
            where.append(input.hasException
                    ? " AND NULLIF(l.Exception, '') IS NOT NULL"
                    : " AND NULLIF(l.Exception, '') IS NULL");
        }
        if (input.minExecutionDuration != null) {
            where.append(" AND l.ExecutionDuration >= ?");
            args.add(input.minExecutionDuration);
        }
        if (input.maxExecutionDuration != null) {
            where.append(" AND l.ExecutionDuration <= ?");
            args.add(input.maxExecutionDuration);
        }

        String from = """
                FROM dbo.SgsAuditLogs l
                LEFT JOIN dbo.SgsUsers u ON u.Id = l.UserId
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT_BIG(*) " + from + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(skip);
        pageArgs.add(take <= 0 ? 10 : take);
        List<AuditLog> rows = jdbcTemplate.queryForList("""
                        SELECT l.Id, l.UserId, l.BrowserInfo, l.ClientIpAddress, l.ClientName, l.CustomData,
                               l.Exception, l.ExecutionDuration, l.ExecutionTime, l.ImpersonatorTenantId,
                               l.ImpersonatorUserId, l.MethodName, l.Parameters, l.ServiceName, l.TenantId,
                               l.ReturnValue
                        """ + from + where + " ORDER BY " + auditLogOrderBy(input.sorting)
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageArgs.toArray()).stream().map(this::databaseAuditLog).toList();
        return new AuditLogQueryResult(total == null ? 0 : Math.toIntExact(total), rows);
    }

    private record AuditLogQueryResult(int totalCount, List<AuditLog> items) {
    }

    private void appendDateFilter(StringBuilder where, List<Object> args, String column, String operator,
                                  Optional<LocalDateTime> value) {
        value.ifPresent(time -> {
            where.append(" AND ").append(column).append(' ').append(operator).append(" ?");
            args.add(Timestamp.valueOf(time));
        });
    }

    private void appendLikeFilter(StringBuilder where, List<Object> args, String column, String value) {
        String text = safe(value).trim();
        if (text.isBlank()) {
            return;
        }
        where.append(" AND ").append(column).append(" LIKE ?");
        args.add("%" + text + "%");
    }

    private void appendAuditUserFilter(StringBuilder where, List<Object> args, String value) {
        String text = safe(value).trim();
        if (text.isBlank()) {
            return;
        }
        where.append("""
                 AND (u.UserName LIKE ? OR u.Name LIKE ? OR u.Surname LIKE ? OR CONCAT(u.Name, u.Surname) LIKE ?)
                """);
        String like = "%" + text + "%";
        args.add(like);
        args.add(like);
        args.add(like);
        args.add(like);
    }

    private String auditLogOrderBy(String sorting) {
        String normalized = safe(sorting).toLowerCase(Locale.ROOT);
        String direction = normalized.contains(" asc") && !normalized.contains(" desc") ? "ASC" : "DESC";
        if (normalized.contains("username")) {
            return "u.UserName " + direction + ", l.Id DESC";
        }
        if (normalized.contains("servicename")) {
            return "l.ServiceName " + direction + ", l.Id DESC";
        }
        if (normalized.contains("methodname")) {
            return "l.MethodName " + direction + ", l.Id DESC";
        }
        if (normalized.contains("executionduration")) {
            return "l.ExecutionDuration " + direction + ", l.Id DESC";
        }
        return "l.ExecutionTime " + direction + ", l.Id DESC";
    }

    private AuditLog databaseAuditLog(Map<String, Object> row) {
        AuditLog log = new AuditLog();
        log.id = dbLong(row, "Id");
        log.userId = dbLong(row, "UserId");
        UserItem user = log.userId == null ? null : users.get(log.userId);
        log.userName = user == null ? "system" : displayDatabaseUser(user);
        log.impersonatorTenantId = dbInteger(row, "ImpersonatorTenantId");
        log.impersonatorUserId = dbLong(row, "ImpersonatorUserId");
        log.serviceName = dbNullableString(row, "ServiceName");
        log.methodName = dbNullableString(row, "MethodName");
        log.parameters = dbNullableString(row, "Parameters");
        log.executionTime = dbDateTime(row, "ExecutionTime");
        log.time = log.executionTime;
        log.executionDuration = dbInteger(row, "ExecutionDuration");
        log.clientIpAddress = dbNullableString(row, "ClientIpAddress");
        log.clientName = dbNullableString(row, "ClientName");
        log.browserInfo = dbNullableString(row, "BrowserInfo");
        log.exception = dbNullableString(row, "Exception");
        log.customData = dbNullableString(row, "CustomData");
        log.result = safe(log.exception).isBlank() ? "成功" : "异常";
        return log;
    }

    public List<NameValueItem> entityHistoryObjectTypes() {
        if (databaseStoreMode) {
            return databaseEntityHistoryObjectTypes();
        }
        return entityChanges.stream()
                .collect(Collectors.toMap(item -> safe(item.entityTypeFullName), item -> item, (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .stream()
                .filter(item -> !safe(item.entityTypeFullName).isBlank())
                .map(item -> nameValue(safe(item.entityTypeDescription).isBlank()
                        ? stripNamespace(item.entityTypeFullName)
                        : item.entityTypeDescription, item.entityTypeFullName))
                .toList();
    }

    public PageResult<EntityChangeItem> entityChanges(GetEntityChangeInput input) {
        GetEntityChangeInput safeInput = input == null ? new GetEntityChangeInput() : input;
        if (databaseStoreMode) {
            return databaseEntityChanges(safeInput);
        }
        List<EntityChangeItem> filtered = filteredEntityChanges(safeInput);
        return new PageResult<>(filtered.size(), page(filtered, safeInput.skipCount, safeInput.maxResultCount));
    }

    private PageResult<EntityChangeItem> databaseEntityChanges(GetEntityChangeInput input) {
        int skip = Math.max(input.skipCount, 0);
        int take = input.maxResultCount <= 0 ? 10 : Math.min(input.maxResultCount, 10_000);
        EntityChangeQuery query = databaseEntityChangeQuery(input.entityTypeFullName, null,
                parseStartDateTime(input.startDate), parseEndDateTime(input.endDate), input.userName);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT_BIG(*) " + query.fromWhere(), Long.class,
                query.args().toArray());
        List<Object> pageArgs = new ArrayList<>(query.args());
        pageArgs.add(skip);
        pageArgs.add(take);
        List<EntityChangeItem> rows = jdbcTemplate.queryForList(databaseEntityChangeSelect()
                        + query.fromWhere()
                        + " ORDER BY " + entityChangeOrderBy(input.sorting)
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageArgs.toArray()).stream()
                .map(this::databaseEntityChange)
                .toList();
        return new PageResult<>(total == null ? 0 : Math.toIntExact(total), rows);
    }

    public List<EntityChangeItem> filteredEntityChanges(GetEntityChangeInput input) {
        GetEntityChangeInput safeInput = input == null ? new GetEntityChangeInput() : input;
        Optional<LocalDateTime> start = parseStartDateTime(safeInput.startDate);
        Optional<LocalDateTime> end = parseEndDateTime(safeInput.endDate);
        if (databaseStoreMode) {
            return databaseEntityChanges(safeInput.entityTypeFullName, null).stream()
                    .filter(item -> between(item.changeTime, start, end))
                    .filter(item -> contains(item.userName, safeInput.userName))
                    .sorted(entityChangeComparator(safeInput.sorting))
                    .toList();
        }
        return entityChanges.stream()
                .filter(item -> between(item.changeTime, start, end))
                .filter(item -> contains(item.userName, safeInput.userName))
                .filter(item -> safe(safeInput.entityTypeFullName).isBlank()
                        || equalsText(item.entityTypeFullName, safeInput.entityTypeFullName))
                .sorted(entityChangeComparator(safeInput.sorting))
                .toList();
    }

    public PageResult<EntityChangeItem> entityTypeChanges(GetEntityTypeChangeInput input) {
        GetEntityTypeChangeInput safeInput = input == null ? new GetEntityTypeChangeInput() : input;
        if (databaseStoreMode) {
            List<EntityChangeItem> filtered = databaseEntityChanges(safeInput.entityTypeFullName, safeInput.entityId).stream()
                    .sorted(entityChangeComparator(safeInput.sorting))
                    .toList();
            return new PageResult<>(filtered.size(), page(filtered, safeInput.skipCount, safeInput.maxResultCount));
        }
        List<EntityChangeItem> filtered = entityChanges.stream()
                .filter(item -> safe(safeInput.entityTypeFullName).isBlank()
                        || equalsText(item.entityTypeFullName, safeInput.entityTypeFullName))
                .filter(item -> safe(safeInput.entityId).isBlank() || equalsEntityId(item.entityId, safeInput.entityId))
                .sorted(entityChangeComparator(safeInput.sorting))
                .toList();
        return new PageResult<>(filtered.size(), page(filtered, safeInput.skipCount, safeInput.maxResultCount));
    }

    public List<EntityPropertyChangeItem> entityPropertyChanges(Long entityChangeId) {
        if (databaseStoreMode) {
            return databaseEntityPropertyChanges(entityChangeId);
        }
        return entityPropertyChanges.stream()
                .filter(item -> Objects.equals(item.entityChangeId, entityChangeId))
                .sorted(Comparator.comparing(item -> item.id == null ? Long.MAX_VALUE : item.id))
                .toList();
    }

    public List<AbilityHistoryDetailDto> abilityHistoryDetails(Long entityChangeId) {
        Map<String, String> displayNames = defaultProperties().stream()
                .collect(Collectors.toMap(item -> item.name, item -> item.title));
        displayNames.put("StandardNo", "标准号");
        Optional<EntityChangeItem> entityChange = databaseStoreMode
                ? databaseEntityChange(entityChangeId)
                : entityChanges.stream()
                        .filter(item -> Objects.equals(item.id, entityChangeId))
                        .findFirst();
        List<EntityPropertyChangeItem> propertyChanges = entityChange
                .map(this::entityPropertyChangesForDisplay)
                .orElseGet(() -> entityPropertyChanges(entityChangeId));
        return propertyChanges.stream()
                .map(item -> {
                    AbilityHistoryDetailDto dto = new AbilityHistoryDetailDto();
                    dto.propertyName = item.propertyName;
                    dto.displayName = displayNames.getOrDefault(item.propertyName, displayNames.get(camelCase(item.propertyName)));
                    dto.originalValue = stripQuotes(item.originalValue);
                    dto.newValue = stripQuotes(item.newValue);
                    return dto;
                })
                .filter(item -> !safe(item.displayName).isBlank())
                .toList();
    }

    private AbilityHistoryItem toAbilityHistoryChange(EntityChangeItem change) {
        AbilityHistoryItem item = new AbilityHistoryItem();
        item.id = change.id;
        item.entityId = change.entityId;
        item.changeTime = change.changeTime;
        item.changeType = change.changeTypeName;
        item.user = change.userName;
        item.reason = "";
        return item;
    }

    private AbilityHistoryItem toAbilityHistoryProperty(EntityChangeItem change, EntityPropertyChangeItem property) {
        AbilityHistoryItem item = toAbilityHistoryChange(change);
        item.id = property.id;
        item.propertyName = property.propertyName;
        item.displayName = abilityPropertyDisplayName(property.propertyName);
        item.originalValue = stripQuotes(property.originalValue);
        item.newValue = stripQuotes(property.newValue);
        return item;
    }

    private String abilityPropertyDisplayName(String propertyName) {
        Map<String, String> displayNames = defaultProperties().stream()
                .collect(Collectors.toMap(item -> item.name, item -> item.title));
        return displayNames.getOrDefault(propertyName, displayNames.get(camelCase(propertyName)));
    }

    private List<EntityPropertyChangeItem> entityPropertyChangesForDisplay(EntityChangeItem change) {
        if (change == null || change.id == null) {
            return List.of();
        }
        List<EntityPropertyChangeItem> stored = entityPropertyChanges(change.id);
        if (!isAbilityEntity(change.entityTypeFullName) || !Objects.equals(change.changeType, 0)) {
            return stored;
        }
        Optional<Ability> ability = parseUuid(change.entityId).map(abilities::get);
        if (ability.isEmpty()) {
            return stored;
        }
        List<EntityPropertyChangeItem> complete = Arrays.asList(abilityPropertyChanges(null, ability.get()));
        if (complete.isEmpty()) {
            return stored;
        }
        Map<String, EntityPropertyChangeItem> storedByProperty = stored.stream()
                .filter(item -> !safe(item.propertyName).isBlank())
                .collect(Collectors.toMap(item -> item.propertyName, item -> item, (left, right) -> left,
                        LinkedHashMap::new));
        List<EntityPropertyChangeItem> merged = new ArrayList<>();
        for (EntityPropertyChangeItem propertyChange : complete) {
            EntityPropertyChangeItem storedChange = storedByProperty.remove(propertyChange.propertyName);
            merged.add(storedChange == null ? propertyChange : storedChange);
        }
        merged.addAll(storedByProperty.values());
        return merged;
    }

    private boolean isAbilityEntity(String entityTypeFullName) {
        return equalsText(entityTypeFullName, ABILITY_ENTITY) || equalsText(entityTypeFullName, PRODUCTION_ABILITY_ENTITY);
    }

    private LocalDate dashboardReferenceDate() {
        if (databaseStoreMode) {
            return databaseDashboardReferenceDate().orElse(LocalDate.now());
        }
        return entityChanges.stream()
                .filter(item -> isAbilityEntity(item.entityTypeFullName))
                .map(item -> parseFlexibleDateTime(item.changeTime))
                .flatMap(Optional::stream)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    private Optional<LocalDate> databaseDashboardReferenceDate() {
        String abilityDate = jdbcTemplate.queryForObject("""
                        SELECT CONVERT(varchar(10), MAX(CreationTime), 120)
                        FROM dbo.MineralAbilityTable
                        WHERE IsDeleted = 0 OR IsDeleted IS NULL
                        """,
                String.class);
        Optional<LocalDate> activeAbilityDate = parseDate(abilityDate);
        if (activeAbilityDate.isPresent()) {
            return activeAbilityDate;
        }
        String changeDate = jdbcTemplate.queryForObject("""
                        SELECT CONVERT(varchar(10), MAX(ChangeTime), 120)
                        FROM dbo.SgsEntityChanges
                        WHERE EntityTypeFullName = ?
                        """,
                String.class,
                PRODUCTION_ABILITY_ENTITY);
        return parseDate(changeDate);
    }

    private long abilityCreatedCountInDays(int days) {
        LocalDate referenceDate = dashboardReferenceDate();
        LocalDate startDate = referenceDate.minusDays(Math.max(days - 1L, 0L));
        if (databaseStoreMode) {
            Long count = jdbcTemplate.queryForObject("""
                            SELECT COUNT_BIG(*)
                            FROM dbo.MineralAbilityTable
                            WHERE (IsDeleted = 0 OR IsDeleted IS NULL)
                              AND CONVERT(date, CreationTime) BETWEEN ? AND ?
                            """,
                    Long.class,
                    java.sql.Date.valueOf(startDate),
                    java.sql.Date.valueOf(referenceDate));
            return count == null ? 0L : count;
        }
        long count = abilities.values().stream()
                .filter(item -> !safe(item.creationTime).isBlank())
                .map(item -> parseFlexibleDateTime(item.creationTime))
                .flatMap(Optional::stream)
                .map(LocalDateTime::toLocalDate)
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(referenceDate))
                .count();
        if (count > 0 || abilities.values().stream().anyMatch(item -> !safe(item.creationTime).isBlank())) {
            return count;
        }
        return abilityEntityChangeCount(0, days);
    }

    private long abilityEntityChangeCount(Integer changeType, int days) {
        LocalDate referenceDate = dashboardReferenceDate();
        LocalDate startDate = referenceDate.minusDays(Math.max(days - 1L, 0L));
        if (databaseStoreMode) {
            Long count = jdbcTemplate.queryForObject("""
                            SELECT COUNT_BIG(*)
                            FROM dbo.SgsEntityChanges
                            WHERE EntityTypeFullName = ?
                              AND ChangeType = ?
                              AND CONVERT(date, ChangeTime) BETWEEN ? AND ?
                            """,
                    Long.class,
                    PRODUCTION_ABILITY_ENTITY,
                    changeType,
                    java.sql.Date.valueOf(startDate),
                    java.sql.Date.valueOf(referenceDate));
            return count == null ? 0L : count;
        }
        return entityChanges.stream()
                .filter(item -> isAbilityEntity(item.entityTypeFullName))
                .filter(item -> Objects.equals(item.changeType, changeType))
                .filter(item -> parseFlexibleDateTime(item.changeTime)
                        .map(LocalDateTime::toLocalDate)
                        .map(date -> !date.isBefore(startDate) && !date.isAfter(referenceDate))
                        .orElse(false))
                .count();
    }

    public long abilityCount() {
        return abilities.size();
    }

    public long labCount() {
        return labs.size();
    }

    public long orgCount() {
        return orgUnits.size();
    }

    public long weekChangeCount() {
        return abilityCreatedCountInDays(7);
    }

    public long monthCreateCount() {
        return abilityCreatedCountInDays(30);
    }

    public long weekDeleteCount() {
        return abilityEntityChangeCount(2, 7);
    }

    public Map<String, Long> abilityChangeCountInWeek() {
        LocalDate referenceDate = dashboardReferenceDate();
        LocalDate startDate = referenceDate.minusDays(6);
        if (databaseStoreMode) {
            return jdbcTemplate.queryForList("""
                            SELECT CONVERT(varchar(10), ChangeTime, 120) AS ChangeDate,
                                   COUNT_BIG(*) AS ChangeCount
                            FROM dbo.SgsEntityChanges
                            WHERE EntityTypeFullName = ?
                              AND CONVERT(date, ChangeTime) BETWEEN ? AND ?
                            GROUP BY CONVERT(varchar(10), ChangeTime, 120)
                            ORDER BY ChangeDate
                            """,
                    PRODUCTION_ABILITY_ENTITY,
                    java.sql.Date.valueOf(startDate),
                    java.sql.Date.valueOf(referenceDate)).stream()
                    .collect(Collectors.toMap(
                            row -> dbString(row, "ChangeDate"),
                            row -> Optional.ofNullable(dbLong(row, "ChangeCount")).orElse(0L),
                            (left, right) -> left,
                            TreeMap::new));
        }
        return entityChanges.stream()
                .filter(item -> isAbilityEntity(item.entityTypeFullName))
                .map(item -> parseFlexibleDateTime(item.changeTime))
                .flatMap(Optional::stream)
                .map(LocalDateTime::toLocalDate)
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(referenceDate))
                .collect(Collectors.groupingBy(LocalDate::toString, TreeMap::new, Collectors.counting()));
    }

    public Map<String, Long> abilityCountByOrg() {
        return abilities.values().stream()
                .collect(Collectors.groupingBy(item -> safe(item.orgName).trim(), LinkedHashMap::new, Collectors.counting()));
    }

    public int countStandardMatches(String oldStandardNo) {
        return (int) abilities.values().stream()
                .filter(item -> equalsText(item.standardNo, oldStandardNo))
                .count();
    }

    /** Applies the copied standard update behavior: StandardNo old value becomes new value. */
    public int updateStandardNumber(String oldStandardNo, String newStandardNo, String userName) {
        return updateStandardNumber(oldStandardNo, newStandardNo, userName, 1L);
    }

    public int updateStandardNumber(String oldStandardNo, String newStandardNo, String userName, Long actorUserId) {
        long userId = actorUserId == null ? 1L : actorUserId;
        int changed = 0;
        for (Ability ability : abilities.values()) {
            if (!equalsText(ability.standardNo, oldStandardNo)) {
                continue;
            }
            ability.standardNo = newStandardNo;
            if (databaseStoreMode && !loadingDatabaseState) {
                updateDatabaseAbility(ability);
            }
            recordEntityChange(userId, ability.id.toString(), ABILITY_ENTITY, "能力表", 1,
                    propertyChange("StandardNo", "System.String", oldStandardNo, newStandardNo));
            changed++;
        }
        if (changed > 0) {
            AbilityHistoryItem item = new AbilityHistoryItem();
            item.changeTime = LocalDateTime.now().toString();
            item.changeType = "标准更新";
            item.user = safe(userName).isBlank() ? "Admin" : userName;
            item.displayName = "标准号";
            item.originalValue = oldStandardNo;
            item.newValue = newStandardNo;
            history.add(item);

            AuditLog log = new AuditLog();
            log.userId = userId;
            log.time = LocalDateTime.now().toString();
            log.userName = item.user;
            log.serviceName = "StandardAppService";
            log.methodName = "UploadNewStandard";
            log.clientIpAddress = "127.0.0.1";
            log.result = "更新" + changed + "条";
            appendAuditLog(log, 1);
        }
        return changed;
    }

    public List<SubcontractAbility> subcontractAbilities(String filter) {
        return subcontractAbilities.values().stream()
                .filter(item -> contains(subcontractSearchText(item), filter))
                .sorted(Comparator.comparing(item -> safe(item.labName)))
                .toList();
    }

    public PageResult<SubcontractAbility> findSubcontractAbilities(FindAbilityRequest input) {
        FindAbilityRequest safeInput = input == null ? new FindAbilityRequest() : input;
        List<SubcontractAbility> filtered = subcontractAbilities.values().stream()
                .filter(item -> contains(subcontractSearchText(item), safeInput.filter))
                .sorted(subcontractAbilityComparator(safeInput.sorting))
                .toList();
        int skip = Math.max(safeInput.skipCount, 0);
        int take = safeInput.maxResultCount <= 0 ? 10 : safeInput.maxResultCount;
        return new PageResult<>(filtered.size(), filtered.stream().skip(skip).limit(take).toList());
    }

    private String subcontractSearchText(SubcontractAbility item) {
        // Original FindList filters LabName, ContactDetails, TestCategory, and CmaOrCnas only.
        return String.join(" ", safe(item.labName), safe(item.contactDetails),
                safe(item.testCategory), safe(item.cmaOrCnas));
    }

    private Comparator<SubcontractAbility> subcontractAbilityComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original FindSubcontractAbilityListDto.Normalize defaults blank sorting to Id.
            normalized = "Id";
        }
        Comparator<SubcontractAbility> comparator = null;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            boolean desc = token.toLowerCase(Locale.ROOT).endsWith(" desc");
            String field = token.toLowerCase(Locale.ROOT).replace(" desc", "").replace(" asc", "").trim();
            Comparator<SubcontractAbility> current = subcontractAbilityFieldComparator(field);
            comparator = comparator == null ? (desc ? current.reversed() : current)
                    : comparator.thenComparing(desc ? current.reversed() : current);
        }
        return (comparator == null ? subcontractAbilityFieldComparator("id") : comparator)
                .thenComparing(item -> item.id == null ? "" : item.id.toString());
    }

    private Comparator<SubcontractAbility> subcontractAbilityFieldComparator(String field) {
        return switch (field) {
            case "labname" -> Comparator.comparing(item -> safe(item.labName));
            case "contactdetails" -> Comparator.comparing(item -> safe(item.contactDetails));
            case "testcategory" -> Comparator.comparing(item -> safe(item.testCategory));
            case "cmaorcnas" -> Comparator.comparing(item -> safe(item.cmaOrCnas));
            case "gist" -> Comparator.comparing(item -> safe(item.gist));
            case "appraiser" -> Comparator.comparing(item -> safe(item.appraiser));
            case "evaluationresult" -> Comparator.comparing(item -> safe(item.evaluationResult));
            default -> Comparator.comparing(item -> item.id == null ? "" : item.id.toString());
        };
    }

    public void saveSubcontractAbilities(List<SubcontractAbility> rows, boolean onlySaveNew) {
        List<SubcontractAbility> sourceRows = rows.stream()
                .filter(row -> row != null && !safe(row.labName).isBlank() && !safe(row.testCategory).isBlank())
                .toList();
        Set<String> importedLabNames = sourceRows.stream()
                .map(row -> row.labName)
                .collect(Collectors.toSet());
        // Original SaveExcelData ignores OnlySaveNew and replaces all rows for imported lab names.
        if (databaseStoreMode && !loadingDatabaseState) {
            softDeleteDatabaseSubcontractAbilitiesByLabNames(importedLabNames);
        }
        subcontractAbilities.entrySet().removeIf(entry -> importedLabNames.contains(entry.getValue().labName));
        sourceRows.forEach(row -> {
            if (row.id == null) {
                row.id = UUID.randomUUID();
            }
            if (databaseStoreMode && !loadingDatabaseState) {
                upsertDatabaseSubcontractAbility(row);
            }
            subcontractAbilities.put(row.id, row);
        });
        persist();
    }

    public List<FavoriteGroup> favoriteGroups() {
        return favoriteGroups(null);
    }

    public List<FavoriteGroup> favoriteGroups(Long userId) {
        return favorites.values().stream()
                .filter(item -> userId == null || item.userId == userId)
                .sorted(Comparator.comparing(item -> safe(item.name)))
                .toList();
    }

    public FavoriteGroup saveFavorite(FavoriteGroup input) {
        return saveFavorite(input, 1L);
    }

    public FavoriteGroup saveFavorite(FavoriteGroup input, long userId) {
        if (input.id == null) {
            input.id = UUID.randomUUID();
        }
        input.userId = userId;
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseFavorite(input);
        }
        favorites.put(input.id, input);
        persist();
        return input;
    }

    public boolean favoriteNameExists(UUID currentId, String name, long userId) {
        String normalizedName = safe(name);
        if (normalizedName.isBlank()) {
            return false;
        }
        return favorites.values().stream()
                .anyMatch(item -> !Objects.equals(item.id, currentId)
                        && item.userId == userId
                        && equalsText(item.name, normalizedName));
    }

    public Optional<FavoriteGroup> favorite(String id) {
        return favorite(id, null);
    }

    public Optional<FavoriteGroup> favorite(String id, Long userId) {
        return parseUuid(id)
                .map(favorites::get)
                .filter(group -> userId == null || group.userId == userId);
    }

    public void deleteFavorite(String id) {
        deleteFavorite(id, 1L);
    }

    public void deleteFavorite(String id, long userId) {
        parseUuid(id).ifPresent(uuid -> {
            FavoriteGroup group = favorites.get(uuid);
            if (group != null && group.userId == userId) {
                if (databaseStoreMode && !loadingDatabaseState) {
                    softDeleteDatabaseFavorite(uuid, userId);
                }
                favorites.remove(uuid);
                persist();
            }
        });
    }

    public void addFavoriteItem(String favoriteId, String abilityId) {
        addFavoriteItem(favoriteId, abilityId, 1L);
    }

    public void addFavoriteItem(String favoriteId, String abilityId, long userId) {
        Optional<UUID> abilityUuid = parseUuid(abilityId);
        if (abilityUuid.isEmpty() || !abilities.containsKey(abilityUuid.get())) {
            return;
        }
        UUID abilityIdValue = abilityUuid.get();
        Optional<UUID> favoriteUuid = parseUuid(favoriteId);
        boolean changed = defaultFavoriteAbilityIds(userId).remove(abilityIdValue);
        for (FavoriteGroup group : favorites.values()) {
            if (group.userId == userId) {
                changed = group.abilityIds.remove(abilityIdValue) || changed;
            }
        }
        if (favoriteUuid.isPresent()) {
            FavoriteGroup favorite = favorites.get(favoriteUuid.get());
            if (favorite != null && favorite.userId == userId) {
                favorite.abilityIds.add(abilityIdValue);
                changed = true;
            }
        } else {
            defaultFavoriteAbilityIds(userId).add(abilityIdValue);
            changed = true;
        }
        Ability ability = abilities.get(abilityIdValue);
        if (ability != null) {
            markFavoriteStatus(ability, userId);
        }
        if (changed) {
            if (databaseStoreMode && !loadingDatabaseState) {
                moveDatabaseFavoriteItem(favoriteUuid.orElse(null), abilityIdValue, userId);
            }
            persist();
        }
    }

    public void removeFavoriteItem(String abilityId) {
        removeFavoriteItem(abilityId, 1L);
    }

    public void removeFavoriteItem(String abilityId, long userId) {
        parseUuid(abilityId).ifPresent(id -> {
            boolean changed = defaultFavoriteAbilityIds(userId).remove(id);
            for (FavoriteGroup group : favorites.values()) {
                if (group.userId == userId) {
                    changed = group.abilityIds.remove(id) || changed;
                }
            }
            if (changed) {
                Ability ability = abilities.get(id);
                if (ability != null) {
                    markFavoriteStatus(ability, userId);
                }
                if (databaseStoreMode && !loadingDatabaseState) {
                    deleteDatabaseFavoriteItemsForAbility(id, userId);
                }
                persist();
            }
        });
    }

    public List<Ability> favoriteAbilities(String favoriteId) {
        return favoriteAbilities(favoriteId, 1L);
    }

    public List<Ability> favoriteAbilities(String favoriteId, long userId) {
        Optional<FavoriteGroup> selected = favorite(favoriteId, userId);
        if (parseUuid(favoriteId).isEmpty()) {
            return defaultFavoriteAbilityIdsForRead(userId).stream()
                    .map(abilities::get)
                    .filter(Objects::nonNull)
                    .peek(ability -> markFavoriteStatus(ability, userId))
                    .toList();
        }
        return selected.map(group -> group.abilityIds.stream()
                        .map(abilities::get)
                        .filter(Objects::nonNull)
                        .peek(ability -> markFavoriteStatus(ability, userId))
                        .toList())
                .orElseGet(List::of);
    }

    private void markFavoriteStatus(Ability ability) {
        markFavoriteStatus(ability, 1L);
    }

    private void markFavoriteStatus(Ability ability, Long userId) {
        if (ability == null || ability.id == null) {
            return;
        }
        long effectiveUserId = userId == null ? 1L : userId;
        ability.isCollected = defaultFavoriteAbilityIdsForRead(effectiveUserId).contains(ability.id)
                || favorites.values().stream()
                .anyMatch(group -> group.userId == effectiveUserId && group.abilityIds.contains(ability.id));
    }

    private Ability queryAbilityView(Ability ability) {
        Ability copy = objectMapper.convertValue(ability, Ability.class);
        copy.isCollected = false;
        return copy;
    }

    private void ensureUserPasswords() {
        users.keySet().forEach(id -> userPasswords.putIfAbsent(id, "123qwe"));
    }

    private boolean ensureAbilityDescriptionData() {
        if (abilitySettings == null) {
            abilitySettings = SystemSettingsItem.defaultAbilitySettings();
            return true;
        }
        String currentDescription = safe(abilitySettings.description).trim();
        if (currentDescription.isBlank()
                || equalsText(currentDescription, "能力表默认说明")
                || equalsText(currentDescription, "暂无说明")) {
            abilitySettings.description = SystemSettingsItem.DEFAULT_ABILITY_DESCRIPTION;
            return true;
        }
        return false;
    }

    private boolean ensureAuditHistoryData() {
        if (databaseStoreMode) {
            return false;
        }
        boolean changed = false;
        for (AuditLog log : auditLogs) {
            changed |= fillAuditDefaults(log, userIdByName(log.userName).orElse(1L));
        }
        for (EntityChangeItem change : entityChanges) {
            if (change.id == null) {
                change.id = nextEntityChangeId();
                changed = true;
            }
            if (change.entityChangeSetId == null) {
                change.entityChangeSetId = nextEntityChangeSetId();
                changed = true;
            }
            if (safe(change.changeTime).isBlank()) {
                change.changeTime = LocalDateTime.now().toString();
                changed = true;
            }
            if (safe(change.userName).isBlank()) {
                change.userName = user(change.userId).map(user -> user.userName).orElse("system");
                changed = true;
            }
            if (safe(change.entityTypeDescription).isBlank()) {
                change.entityTypeDescription = entityDescription(change.entityTypeFullName);
                changed = true;
            }
            if (safe(change.changeTypeName).isBlank()) {
                change.changeTypeName = changeTypeName(change.changeType);
                changed = true;
            }
        }
        for (EntityPropertyChangeItem propertyChange : entityPropertyChanges) {
            if (propertyChange.id == null) {
                propertyChange.id = nextEntityPropertyChangeId();
                changed = true;
            }
        }
        return seedEntityHistory() || changed;
    }

    private boolean seedEntityHistory() {
        if (!entityChanges.isEmpty()) {
            return false;
        }
        abilities.values().stream().limit(2).forEach(ability ->
                recordEntityChange(1L, ability.id.toString(), ABILITY_ENTITY, "能力表", 1,
                        propertyChange("StandardNo", "System.String", "", ability.standardNo),
                        propertyChange("MethodName", "System.String", "", ability.methodName)));
        tenants.values().stream().limit(2).forEach(tenant ->
                recordEntityChange(1L, String.valueOf(tenant.id), TENANT_ENTITY, "租户", 1,
                        propertyChange("TenancyName", "System.String", "", tenant.tenancyName),
                        propertyChange("EditionId", "System.Nullable`1[System.Int32]", "",
                                tenant.editionId == null ? null : String.valueOf(tenant.editionId))));
        users.values().stream().limit(2).forEach(user ->
                recordEntityChange(user.id, String.valueOf(user.id), USER_ENTITY, "用户", 1,
                        propertyChange("EmailAddress", "System.String", "", user.emailAddress),
                        propertyChange("IsActive", "System.Boolean", "", String.valueOf(user.isActive))));
        return true;
    }

    private boolean ensurePlatformPermissions() {
        boolean changed = false;
        changed |= ensurePermission("Pages.Administration.Languages", "语言管理", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.Languages.Create", "创建语言", "Pages.Administration.Languages", 3);
        changed |= ensurePermission("Pages.Administration.Languages.Edit", "编辑语言", "Pages.Administration.Languages", 3);
        changed |= ensurePermission("Pages.Administration.Languages.Delete", "删除语言", "Pages.Administration.Languages", 3);
        changed |= ensurePermission("Pages.Administration.Languages.ChangeTexts", "维护语言文本", "Pages.Administration.Languages", 3);
        changed |= ensurePermission("Pages.Administration.Host.Maintenance", "维护管理", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.Host.Settings", "宿主设置", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.Host.Dashboard", "宿主看板", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Tenant.Dashboard", "租户看板", "Pages", 1);
        changed |= ensurePermission("Pages.Administration.Tenant.Settings", "租户设置", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.Users.Create", "创建用户", "Pages.Administration.Users", 3);
        changed |= ensurePermission("Pages.Administration.Users.Edit", "编辑用户", "Pages.Administration.Users", 3);
        changed |= ensurePermission("Pages.Administration.Users.Delete", "删除用户", "Pages.Administration.Users", 3);
        changed |= ensurePermission("Pages.Administration.Users.ChangePermissions", "维护用户权限", "Pages.Administration.Users", 3);
        changed |= ensurePermission("Pages.Administration.Users.Impersonation", "模拟登录", "Pages.Administration.Users", 3);
        changed |= ensurePermission("Pages.Tenants", "租户管理", "Pages", 1);
        changed |= ensurePermission("Pages.Tenants.Create", "创建租户", "Pages.Tenants", 2);
        changed |= ensurePermission("Pages.Tenants.Edit", "编辑租户", "Pages.Tenants", 2);
        changed |= ensurePermission("Pages.Tenants.Delete", "删除租户", "Pages.Tenants", 2);
        changed |= ensurePermission("Pages.Tenants.ChangeFeatures", "维护租户功能", "Pages.Tenants", 2);
        changed |= ensurePermission("Pages.Editions", "版本管理", "Pages", 1);
        changed |= ensurePermission("Pages.Editions.Create", "创建版本", "Pages.Editions", 2);
        changed |= ensurePermission("Pages.Editions.Edit", "编辑版本", "Pages.Editions", 2);
        changed |= ensurePermission("Pages.Editions.Delete", "删除版本", "Pages.Editions", 2);
        changed |= ensurePermission("Pages.Administration.SubscriptionManagement", "订阅管理", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.DynamicParameters", "动态参数", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.DynamicParameters.Create", "创建动态参数", "Pages.Administration.DynamicParameters", 3);
        changed |= ensurePermission("Pages.Administration.DynamicParameters.Edit", "编辑动态参数", "Pages.Administration.DynamicParameters", 3);
        changed |= ensurePermission("Pages.Administration.DynamicParameters.Delete", "删除动态参数", "Pages.Administration.DynamicParameters", 3);
        changed |= ensurePermission("Pages.Administration.DynamicParameterValue", "动态参数值", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.DynamicParameterValue.Create", "创建动态参数值", "Pages.Administration.DynamicParameterValue", 3);
        changed |= ensurePermission("Pages.Administration.DynamicParameterValue.Edit", "编辑动态参数值", "Pages.Administration.DynamicParameterValue", 3);
        changed |= ensurePermission("Pages.Administration.DynamicParameterValue.Delete", "删除动态参数值", "Pages.Administration.DynamicParameterValue", 3);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameters", "实体动态参数", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameters.Create", "创建实体动态参数", "Pages.Administration.EntityDynamicParameters", 3);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameters.Edit", "编辑实体动态参数", "Pages.Administration.EntityDynamicParameters", 3);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameters.Delete", "删除实体动态参数", "Pages.Administration.EntityDynamicParameters", 3);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameterValue", "实体动态参数值", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameterValue.Create", "创建实体动态参数值", "Pages.Administration.EntityDynamicParameterValue", 3);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameterValue.Edit", "编辑实体动态参数值", "Pages.Administration.EntityDynamicParameterValue", 3);
        changed |= ensurePermission("Pages.Administration.EntityDynamicParameterValue.Delete", "删除实体动态参数值", "Pages.Administration.EntityDynamicParameterValue", 3);
        changed |= ensurePermission("Pages.Administration.WebhookSubscription", "Webhook订阅", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.WebhookSubscription.Create", "创建Webhook订阅", "Pages.Administration.WebhookSubscription", 3);
        changed |= ensurePermission("Pages.Administration.WebhookSubscription.Edit", "编辑Webhook订阅", "Pages.Administration.WebhookSubscription", 3);
        changed |= ensurePermission("Pages.Administration.WebhookSubscription.ChangeActivity", "启停Webhook订阅", "Pages.Administration.WebhookSubscription", 3);
        changed |= ensurePermission("Pages.Administration.WebhookSubscription.Detail", "Webhook订阅详情", "Pages.Administration.WebhookSubscription", 3);
        changed |= ensurePermission("Pages.Administration.Webhook.ListSendAttempts", "Webhook发送记录", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.Administration.Webhook.ResendWebhook", "重发Webhook", "Pages.Administration.Webhook.ListSendAttempts", 3);
        changed |= ensurePermission("Pages.Administration.UiCustomization", "UI定制", "Pages.Administration", 2);
        changed |= ensurePermission("Pages.DemoUiComponents", "示例组件", "Pages", 1);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.Create", "创建能力", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.Edit", "编辑能力", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.PublicEdit", "公开能力编辑", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.Delete", "删除能力", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.DeleteAll", "批量删除能力", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.ImportExcel", "导入能力Excel", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.Ability.History", "能力历史", "Pages.AbilityManagement.Ability", 3);
        changed |= ensurePermission("Pages.AbilityManagement.EditDesc", "编辑能力说明", "Pages.AbilityManagement", 2);
        return changed;
    }

    private boolean ensurePermission(String name, String displayName, String parentName, int level) {
        boolean exists = permissions.stream().anyMatch(item -> equalsText(item.name, name));
        if (exists) {
            return false;
        }
        permission(name, displayName, parentName, level);
        return true;
    }

    private boolean ensureTenantAdminMenuPermissions() {
        RoleItem adminRole = roleByName("Admin").orElse(null);
        if (adminRole == null) {
            return false;
        }
        boolean changed = false;
        for (String permissionName : TENANT_ADMIN_MENU_PERMISSIONS) {
            if (safe(permissionName).isBlank()) {
                continue;
            }
            if (!adminRole.grantedPermissionNames.contains(permissionName)) {
                adminRole.grantedPermissionNames.add(permissionName);
                changed = true;
            }
            ensureDatabaseRolePermission(adminRole.id, permissionName);
        }
        adminRole.grantedPermissionNames = uniqueStrings(adminRole.grantedPermissionNames);
        return changed;
    }

    private void ensureDatabaseRolePermission(Integer roleId, String permissionName) {
        if (!databaseStoreMode || roleId == null || safe(permissionName).isBlank()) {
            return;
        }
        String databasePermissionName = truncateForColumn(permissionName, 128);
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM dbo.SgsPermissions
                        WHERE TenantId = 1
                          AND RoleId = ?
                          AND UserId IS NULL
                          AND IsGranted = 1
                          AND Name = ?
                        """,
                Integer.class,
                roleId,
                databasePermissionName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsPermissions
                            (CreationTime, CreatorUserId, Discriminator, IsGranted, Name, TenantId, RoleId, UserId)
                        VALUES (?, ?, 'RolePermissionSetting', 1, ?, 1, ?, NULL)
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                2L,
                databasePermissionName,
                roleId);
    }

    private boolean ensureLanguageData() {
        boolean changed = false;
        if (languages.isEmpty()) {
            languages.put(1, language(1, "zh-Hans", "简体中文", "famfamfam-flags cn", true));
            languages.put(2, language(2, "en", "English", "famfamfam-flags us", false));
            changed = true;
        }
        changed |= normalizeDefaultLanguageIcons();
        for (LanguageItem language : languages.values()) {
            changed |= seedLanguageTexts(language.name);
        }
        return changed;
    }

    private boolean ensureNotificationData() {
        if (databaseStoreMode) {
            return false;
        }
        boolean changed = false;
        for (Long userId : users.keySet()) {
            if (!notificationSettings.containsKey(userId)) {
                notificationSettings.put(userId, defaultNotificationSettings(userId));
                changed = true;
            }
        }
        if (notifications.isEmpty() && users.containsKey(1L)) {
            addNotification(1L, "Capability.AbilityChanged", "能力表标准号已完成本地更新", "Info", true);
            addNotification(1L, "System.LanguageReady", "语言管理模块已启用", "Success", false);
            if (users.containsKey(2L)) {
                addNotification(2L, "Capability.QueryReady", "能力表查询权限已开通", "Info", true);
            }
            changed = true;
        }
        return changed;
    }

    private boolean ensureCacheData() {
        int before = caches.size();
        caches.putIfAbsent("AbpZero.UserPermissions", cache("AbpZero.UserPermissions", "用户权限缓存", permissions.size()));
        caches.putIfAbsent("Capability.AbilityTables", cache("Capability.AbilityTables", "能力表缓存", abilities.size()));
        caches.putIfAbsent("Capability.Localization", cache("Capability.Localization", "本地化文本缓存", languageTexts.size()));
        caches.putIfAbsent("Capability.Notifications", cache("Capability.Notifications", "通知缓存", notifications.size()));
        caches.putIfAbsent("Capability.Tenants", cache("Capability.Tenants", "租户缓存", tenants.size()));
        caches.putIfAbsent("Capability.Editions", cache("Capability.Editions", "版本缓存", editions.size()));
        caches.putIfAbsent("Capability.Payments", cache("Capability.Payments", "订阅付款缓存", subscriptionPayments.size()));
        caches.putIfAbsent("TempFileCache", cache("TempFileCache", "临时文件缓存", 0));
        return caches.size() != before;
    }

    private boolean ensureChatData() {
        if (databaseStoreMode) {
            return false;
        }
        int before = friendships.size() + chatMessages.size();
        if (users.containsKey(1L) && users.containsKey(2L)) {
            ensureFriendship(1L, null, 2L, null, 1);
            ensureFriendship(2L, null, 1L, null, 1);
            if (chatMessages.isEmpty()) {
                String sharedMessageId = UUID.randomUUID().toString();
                ChatMessageItem senderCopy = chatMessage(2L, 1L, 1, 2, 1,
                        "Hi, this is a local replica chat message.", sharedMessageId);
                chatMessages.put(senderCopy.id, senderCopy);
                ChatMessageItem receiverCopy = chatMessage(1L, 2L, 2, 1, 2,
                        senderCopy.message, sharedMessageId);
                chatMessages.put(receiverCopy.id, receiverCopy);
            }
        }
        return friendships.size() + chatMessages.size() != before;
    }

    private void seedCaches() {
        ensureCacheData();
    }

    private boolean ensureDynamicParameterData() {
        if (databaseStoreMode) {
            return false;
        }
        int before = dynamicParameters.size() + dynamicParameterValues.size()
                + entityDynamicParameters.size() + entityDynamicParameterValues.size();
        if (dynamicParameters.isEmpty()) {
            dynamicParameters.put(1, dynamicParameter(1, "Region", "业务区域", "COMBOBOX", ""));
            dynamicParameters.put(2, dynamicParameter(2, "Priority", "能力优先级", "COMBOBOX", ""));
        }
        if (dynamicParameterValues.isEmpty()) {
            dynamicParameterValues.put(1, dynamicParameterValue(1, 1, "华北"));
            dynamicParameterValues.put(2, dynamicParameterValue(2, 1, "华东"));
            dynamicParameterValues.put(3, dynamicParameterValue(3, 2, "高"));
            dynamicParameterValues.put(4, dynamicParameterValue(4, 2, "中"));
            dynamicParameterValues.put(5, dynamicParameterValue(5, 2, "低"));
        }
        if (entityDynamicParameters.isEmpty()) {
            entityDynamicParameters.put(1, entityDynamicParameter(1, "Capability.Ability", 1));
            entityDynamicParameters.put(2, entityDynamicParameter(2, "Capability.Ability", 2));
        }
        if (entityDynamicParameterValues.isEmpty()) {
            String entityId = abilities.keySet().stream().findFirst().map(UUID::toString).orElse("demo-ability");
            entityDynamicParameterValues.put(1, entityDynamicParameterValue(1, 1, entityId, "华北"));
            entityDynamicParameterValues.put(2, entityDynamicParameterValue(2, 2, entityId, "高"));
        }
        dynamicParameterValues.replaceAll((id, item) -> decorateDynamicParameterValue(item));
        entityDynamicParameters.replaceAll((id, item) -> decorateEntityDynamicParameter(item));
        entityDynamicParameterValues.replaceAll((id, item) -> decorateEntityDynamicParameterValue(item));
        int after = dynamicParameters.size() + dynamicParameterValues.size()
                + entityDynamicParameters.size() + entityDynamicParameterValues.size();
        return before != after;
    }

    private void seedDynamicParameters(String entityId) {
        ensureDynamicParameterData();
        if (safe(entityId).isBlank()) {
            return;
        }
        entityDynamicParameterValues.putIfAbsent(1, entityDynamicParameterValue(1, 1, entityId, "华北"));
        entityDynamicParameterValues.putIfAbsent(2, entityDynamicParameterValue(2, 2, entityId, "高"));
    }

    private boolean ensureWebhookData() {
        if (databaseStoreMode) {
            return false;
        }
        int before = webhookSubscriptions.size() + webhookEvents.size() + webhookSendAttempts.size();
        if (webhookSubscriptions.isEmpty()) {
            WebhookSubscriptionItem subscription = new WebhookSubscriptionItem();
            subscription.id = UUID.randomUUID();
            subscription.webhookUri = "https://example.local/webhook";
            subscription.isActive = true;
            subscription.webhooks.add("App.TestWebhook");
            subscription.headers.put("X-Replica", "capability");
            subscription.creationTime = LocalDateTime.now().minusDays(1).toString();
            webhookSubscriptions.put(subscription.id, subscription);
        }
        if (webhookEvents.isEmpty()) {
            WebhookEventItem event = webhookEvent("App.TestWebhook", "{\"message\":\"Seed webhook event\"}");
            webhookEvents.put(event.id, event);
        }
        if (webhookSendAttempts.isEmpty()) {
            WebhookEventItem event = webhookEvents.values().stream().findFirst().orElseGet(() -> webhookEvent("App.TestWebhook", "{}"));
            WebhookSubscriptionItem subscription = webhookSubscriptions.values().stream().findFirst().orElse(null);
            if (subscription != null) {
                WebhookSendAttemptItem attempt = webhookSendAttempt(event, subscription, "Seed attempt in local replica", 202);
                webhookSendAttempts.put(attempt.id, attempt);
            }
        }
        int after = webhookSubscriptions.size() + webhookEvents.size() + webhookSendAttempts.size();
        return before != after;
    }

    private void seedWebhooks() {
        ensureWebhookData();
    }

    private boolean ensureUiCustomizationData() {
        int before = uiThemes.size();
        seedUiThemes();
        uiThemes.replaceAll((theme, item) -> normalizeTheme(item));
        return before != uiThemes.size();
    }

    private void seedUiThemes() {
        uiThemes.putIfAbsent("default", defaultTheme("default"));
        uiThemes.putIfAbsent("theme2", defaultTheme("theme2"));
        uiThemes.putIfAbsent("theme3", defaultTheme("theme3"));
        uiThemes.putIfAbsent("theme4", defaultTheme("theme4"));
        uiThemes.putIfAbsent("theme5", defaultTheme("theme5"));
        uiThemes.putIfAbsent("theme7", defaultTheme("theme7"));
    }

    private boolean ensureTenantPlatformData() {
        int before = features.size() + editions.size() + tenants.size() + subscriptionPayments.size() + invoices.size();
        if (databaseStoreMode) {
            tenants.replaceAll((id, item) -> decorateTenant(item));
            editions.replaceAll((id, item) -> decorateEdition(item));
            subscriptionPayments.values().forEach(this::decorateSubscriptionPayment);
            return false;
        }
        seedTenantPlatform();
        tenants.replaceAll((id, item) -> decorateTenant(item));
        editions.replaceAll((id, item) -> decorateEdition(item));
        subscriptionPayments.values().forEach(this::decorateSubscriptionPayment);
        return before != features.size() + editions.size() + tenants.size() + subscriptionPayments.size() + invoices.size();
    }

    private boolean ensureAccountSecurityData() {
        boolean changed = false;
        for (UserItem user : users.values()) {
            if (user.linkedUserIds == null) {
                user.linkedUserIds = new ArrayList<>();
                changed = true;
            }
        }
        if (databaseStoreMode) {
            return changed;
        }
        if (users.containsKey(1L) && users.containsKey(2L)) {
            UserItem admin = users.get(1L);
            UserItem query = users.get(2L);
            if (!admin.linkedUserIds.contains(query.id)) {
                admin.linkedUserIds.add(query.id);
                changed = true;
            }
            if (!query.linkedUserIds.contains(admin.id)) {
                query.linkedUserIds.add(admin.id);
                changed = true;
            }
        }
        return changed;
    }

    private boolean ensureOrganizationUnitData() {
        boolean changed = false;
        for (UserItem user : users.values()) {
            if (user.organizationUnits == null) {
                user.organizationUnits = new ArrayList<>();
                changed = true;
            }
        }
        for (RoleItem role : roles.values()) {
            if (role.organizationUnits == null) {
                role.organizationUnits = new ArrayList<>();
                changed = true;
            }
        }
        if (hasProductionImportedData()) {
            return changed;
        }
        RoleItem admin = roles.get(1);
        if (admin != null && !roleOrganizationUnits(admin).contains(1L)) {
            admin.organizationUnits.add(1L);
            changed = true;
        }
        RoleItem query = roles.get(2);
        if (query != null && !roleOrganizationUnits(query).contains(2L)) {
            query.organizationUnits.add(2L);
            changed = true;
        }
        return changed;
    }

    private boolean ensureProductionBusinessLineData() {
        boolean changed = false;
        boolean productionImportedData = hasProductionImportedData();
        List<OrganizationUnit> businessLines = new ArrayList<>();
        for (String name : PRODUCTION_BUSINESS_LINES) {
            OrganizationUnit org = organizationUnitByName(name).orElse(null);
            if (org == null) {
                org = org(nextOrganizationUnitId(), null, name);
                orgUnits.add(org);
                changed = true;
            }
            businessLines.add(org);

            OrgAbilitySetting setting = orgSettings.get(org.id);
            if (setting == null) {
                setting = new OrgAbilitySetting();
                setting.orgId = org.id;
                setting.propertyName.addAll(defaultPropertyNames());
                if (!productionImportedData) {
                    setting.lab.addAll(List.of("TJ", "GZ"));
                }
                setting.isPublic = false;
                setting.description = org.displayName + "-设置";
                orgSettings.put(org.id, setting);
                changed = true;
            } else {
                if (setting.propertyName == null) {
                    setting.propertyName = new ArrayList<>();
                    changed = true;
                }
                if (setting.propertyName.isEmpty()) {
                    setting.propertyName.addAll(defaultPropertyNames());
                    changed = true;
                }
                if (setting.lab == null) {
                    setting.lab = new ArrayList<>();
                    changed = true;
                }
                if (!productionImportedData) {
                    for (String labCode : List.of("TJ", "GZ")) {
                        if (!setting.lab.contains(labCode)) {
                            setting.lab.add(labCode);
                            changed = true;
                        }
                    }
                }
            }
        }

        List<Long> businessLineIds = businessLines.stream().map(org -> org.id).toList();
        UserItem admin = users.get(1L);
        if (admin != null && !productionImportedData) {
            for (Long orgId : businessLineIds) {
                if (!admin.organizationUnits.contains(orgId)) {
                    admin.organizationUnits.add(orgId);
                    changed = true;
                }
            }
        }
        for (RoleItem role : roles.values()) {
            if (role.organizationUnits != null && role.organizationUnits.removeIf(businessLineIds::contains)) {
                changed = true;
            }
        }

        Laboratory tj = null;
        Laboratory gz = null;
        if (!productionImportedData) {
            int labCountBefore = labs.size();
            tj = ensureLaboratory("TJ", "天津实验室", "Tianjin Lab", "Yuhong Yang",
                    "Yuhong.Yang@sgs.com", "天津市经济技术开发区第五大街41号SGS大厦");
            gz = ensureLaboratory("GZ", "广州实验室", "Guangzhou Lab", "Buddy Chen",
                    "Buddy.chen@sgs.com", "广州市经济技术开发区科学城科珠路198号");
            changed |= labs.size() != labCountBefore;
        }

        OrganizationUnit nf = businessLines.stream()
                .filter(org -> equalsText(org.displayName, "NF"))
                .findFirst()
                .orElse(null);
        if (nf != null && !productionImportedData) {
            changed |= ensureProductionMembers(nf);
        }
        if (nf != null && !productionImportedData && abilities.values().stream().noneMatch(item -> Objects.equals(item.orgId, nf.id))) {
            SampleType nfType = sampleTypes.values().stream()
                    .filter(item -> Objects.equals(item.orgId, nf.id) && equalsText(item.displayName, "NF能力表数据"))
                    .findFirst()
                    .orElse(null);
            if (nfType == null) {
                nfType = type("NF能力表数据", nf.id, nf.displayName);
                sampleTypes.put(nfType.id, nfType);
            }
            String[][] rows = {
                    {"再生锌原料\nRegenerated zinc material", "锌 Zn", "600", "YS/T 1171.1-2017",
                            "Zn: 10.00%~90.00...", "EDTA滴定法", "EDTA titrimetric method", "3", "100g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "铜 Cu", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "铅 Pb", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "铁 Fe", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "铟 In", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "镉 Cd", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "砷 As", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "钙 Ca", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "铝 Al", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."},
                    {"再生锌原料\nRegenerated zinc material", "锡 Sn", "300", "YS/T 1171.3-2017",
                            "", "电感耦合等离子体发射光谱法", "ICP-AES", "4", "200g(完成无..."}
            };
            for (String[] row : rows) {
                Ability ability = ability(nf, nfType, row[0], row[1], row[5], row[3], row[7], row[8], "", "", row[2], row[4]);
                ability.methodEngName = row[6];
                ability.sizeRequired = "";
                ability.detectionLimit = "";
                ability.labAbilities.add(labAbility(tj, true, false, true));
                ability.labAbilities.add(labAbility(gz, false, false, true));
                abilities.put(ability.id, ability);
            }
            changed = true;
        }
        return changed;
    }

    private boolean hasProductionImportedData() {
        return abilities.size() > 100 || users.size() > 100 || labs.size() > 10;
    }

    private boolean ensureProductionMembers(OrganizationUnit nf) {
        boolean changed = false;
        String[][] firstRows = {
                {"admin", "admin", "admin@defaulttenant.com", "2020-08-02 09:21"},
                {"Davis_Cheng", "Davis Cheng", "Davis.Cheng@sgs.com", "2021-06-02 13:53"},
                {"Lewis_Wang", "Lewis Wang", "lewis.wang@sgs.com", "2021-01-26 13:20"},
                {"aster_cai", "蔡小玉", "aster.cai@sgs.com", "2021-11-16 19:23"},
                {"maggie_che", "车雅琴", "maggie.che@sgs.com", "2021-11-16 19:27"},
                {"Jenny-dm_Chen", "Jenny Chen", "Jenny-dm.Chen@sgs.com", "2021-07-08 10:27"},
                {"Winnie-l_Chen", "Winnie Chen", "Winnie-l.Chen@sgs.com", "2021-07-08 10:51"},
                {"mavis-MH_chen", "Mavis Chen", "mavis-MH.chen@sgs.com", "2021-11-16 19:22"},
                {"Echo_chan", "Echo Chan", "Echo.chan@sgs.com", "2023-02-28 13:01"},
                {"Lisa_Chen", "Lisa Chen", "Lisa.Chen@sgs.com", "2021-07-08 10:44"}
        };
        for (String[] row : firstRows) {
            changed |= ensureProductionMember(nf, row[0], row[1], row[2], row[3]);
        }
        for (int index = 11; index <= 82; index += 1) {
            String number = "%02d".formatted(index);
            changed |= ensureProductionMember(nf, "NF_User_" + number, "NF User " + number,
                    "nf.user" + number + "@sgs.com", "2022-01-%02d 09:%02d".formatted(((index - 1) % 28) + 1, index % 60));
        }
        return changed;
    }

    private boolean ensureProductionMember(OrganizationUnit org, String userName, String name, String email, String addedTime) {
        UserItem user = users.values().stream()
                .filter(item -> equalsText(item.userName, userName))
                .findFirst()
                .orElse(null);
        boolean changed = false;
        if (user == null) {
            user = user(nextUserId(), name, "", userName, email, "", true);
            users.put(user.id, user);
            userPasswords.put(user.id, "123qwe");
            changed = true;
        }
        if (!equalsText(user.name, name)) {
            user.name = name;
            changed = true;
        }
        if (!equalsText(user.emailAddress, email)) {
            user.emailAddress = email;
            changed = true;
        }
        LocalDateTime creationTime = parseLocalDateTime(addedTime);
        if (creationTime != null && !Objects.equals(user.creationTime, creationTime)) {
            user.creationTime = creationTime;
            changed = true;
        }
        if (user.organizationUnits == null) {
            user.organizationUnits = new ArrayList<>();
            changed = true;
        }
        if (!user.organizationUnits.contains(org.id)) {
            user.organizationUnits.add(org.id);
            changed = true;
        }
        return changed;
    }

    private Optional<OrganizationUnit> organizationUnitByName(String displayName) {
        return orgUnits.stream()
                .filter(org -> equalsText(org.displayName, displayName))
                .findFirst();
    }

    private long nextOrganizationUnitId() {
        return orgUnits.stream().map(org -> org.id).max(Long::compareTo).orElse(0L) + 1L;
    }

    private long nextUserId() {
        return users.keySet().stream().max(Long::compareTo).orElse(0L) + 1L;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        String normalized = safe(value).trim().replace(' ', 'T');
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int productionBusinessLineIndex(String displayName) {
        for (int i = 0; i < PRODUCTION_BUSINESS_LINES.size(); i += 1) {
            if (equalsText(PRODUCTION_BUSINESS_LINES.get(i), displayName)) {
                return i;
            }
        }
        return PRODUCTION_BUSINESS_LINES.size();
    }

    private Laboratory ensureLaboratory(String code, String name, String engName, String leader, String contact, String address) {
        return labs.values().stream()
                .filter(item -> equalsText(item.code, code))
                .findFirst()
                .orElseGet(() -> {
                    Laboratory created = lab(code, name, engName, leader, contact, address);
                    labs.put(created.id, created);
                    return created;
                });
    }

    private void seedTenantPlatform() {
        features.putIfAbsent("App.MaxUserCount", feature("Edition", "App.MaxUserCount", "最大用户数", "允许创建的用户数量", "25", "SINGLE_LINE_STRING"));
        features.putIfAbsent("App.Chat", feature("Edition", "App.Chat", "聊天", "是否启用聊天功能", "true", "CHECKBOX"));
        features.putIfAbsent("App.Webhook", feature("Edition", "App.Webhook", "Webhook", "是否启用Webhook订阅", "true", "CHECKBOX"));
        features.putIfAbsent("App.AdvancedReporting", feature("Edition", "App.AdvancedReporting", "高级报表", "是否启用高级报表", "false", "CHECKBOX"));

        editions.putIfAbsent(1, edition(1, "Free", "免费版", "0", "0", "0", "0", 14, 7, null));
        editions.putIfAbsent(2, edition(2, "Standard", "标准版", "20", "120", "380", "3800", 30, 15, 1));
        editions.putIfAbsent(3, edition(3, "Enterprise", "企业版", "50", "300", "980", "9800", 60, 30, 2));

        tenants.putIfAbsent(1, tenantSeed(1, "default", "默认租户", 2, true, 45));
        tenants.putIfAbsent(2, tenantSeed(2, "trial", "试用租户", 1, true, 10));

        if (subscriptionPayments.isEmpty()) {
            SubscriptionPaymentItem payment = createPayment(2, 1, 30, 2, true, "http://localhost:5173/payments/success", "http://localhost:5173/payments/error");
            markPaymentStatus(payment.id, 5);
            createInvoice(payment.id);
        }
    }

    private FeatureItem feature(String parentName, String name, String displayName, String description,
                                String defaultValue, String inputTypeName) {
        FeatureItem item = new FeatureItem();
        item.parentName = parentName;
        item.name = name;
        item.displayName = displayName;
        item.description = description;
        item.defaultValue = defaultValue;
        item.inputType.put("name", inputTypeName);
        return item;
    }

    private EditionItem edition(int id, String name, String displayName, String dailyPrice, String weeklyPrice,
                                String monthlyPrice, String annualPrice, int waitingDays, int trialDays,
                                Integer expiringEditionId) {
        EditionItem item = new EditionItem();
        item.id = id;
        item.name = name;
        item.displayName = displayName;
        item.dailyPrice = new BigDecimal(dailyPrice);
        item.weeklyPrice = new BigDecimal(weeklyPrice);
        item.monthlyPrice = new BigDecimal(monthlyPrice);
        item.annualPrice = new BigDecimal(annualPrice);
        item.waitingDayAfterExpire = waitingDays;
        item.trialDayCount = trialDays;
        item.expiringEditionId = expiringEditionId;
        item.featureValues = defaultFeatureValues();
        item.featureValues.put("App.MaxUserCount", id == 3 ? "250" : id == 2 ? "80" : "10");
        item.featureValues.put("App.AdvancedReporting", id == 3 ? "true" : "false");
        return decorateEdition(item);
    }

    private TenantItem tenantSeed(int id, String tenancyName, String name, Integer editionId, boolean active, int subscriptionDays) {
        TenantItem item = new TenantItem();
        item.id = id;
        item.tenancyName = tenancyName;
        item.name = name;
        item.editionId = editionId;
        item.isActive = active;
        item.creationTime = LocalDateTime.now().minusDays(id * 3L).toString();
        item.subscriptionEndDateUtc = LocalDateTime.now().plusDays(subscriptionDays).toString();
        item.subscriptionPaymentType = SUBSCRIPTION_RECURRING_AUTOMATIC;
        item.isInTrialPeriod = id == 2;
        item.adminEmailAddress = "admin@" + tenancyName + ".local";
        item.adminPassword = "123qwe";
        item.shouldChangePasswordOnNextLogin = false;
        item.sendActivationEmail = false;
        item.featureValues = defaultFeatureValues();
        return decorateTenant(item);
    }

    private EditionItem decorateEdition(EditionItem item) {
        if (item == null) {
            return null;
        }
        item.expiringEditionDisplayName = edition(item.expiringEditionId).map(edition -> edition.displayName).orElse("");
        item.isFree = isFreeEdition(item);
        if (item.featureValues == null || item.featureValues.isEmpty()) {
            item.featureValues = defaultFeatureValues();
        }
        return item;
    }

    private TenantItem decorateTenant(TenantItem item) {
        if (item == null) {
            return null;
        }
        item.editionDisplayName = edition(item.editionId).map(edition -> edition.displayName).orElse("");
        if (item.featureValues == null || item.featureValues.isEmpty()) {
            item.featureValues = defaultFeatureValues();
        }
        return item;
    }

    private boolean isFreeEdition(EditionItem item) {
        return amount(item.dailyPrice).compareTo(BigDecimal.ZERO) == 0
                && amount(item.weeklyPrice).compareTo(BigDecimal.ZERO) == 0
                && amount(item.monthlyPrice).compareTo(BigDecimal.ZERO) == 0
                && amount(item.annualPrice).compareTo(BigDecimal.ZERO) == 0;
    }

    private OrganizationUnit decorateOrganizationUnit(OrganizationUnit item) {
        if (item == null) {
            return null;
        }
        item.code = orgUnitCode(item);
        item.memberCount = (int) users.values().stream()
                .filter(user -> user.organizationUnits != null && user.organizationUnits.contains(item.id))
                .count();
        item.roleCount = (int) roles.values().stream()
                .map(this::normalizeRole)
                .filter(role -> roleOrganizationUnits(role).contains(item.id))
                .count();
        return item;
    }

    private String orgUnitCode(OrganizationUnit item) {
        String segment = "%05d".formatted(item.id);
        if (item.parentId == null) {
            return segment;
        }
        return orgUnits.stream()
                .filter(parent -> Objects.equals(parent.id, item.parentId))
                .findFirst()
                .map(parent -> orgUnitCode(parent) + "." + segment)
                .orElse(segment);
    }

    private RoleItem normalizeRole(RoleItem role) {
        if (role != null && role.organizationUnits == null) {
            role.organizationUnits = new ArrayList<>();
        }
        return role;
    }

    private List<Long> roleOrganizationUnits(RoleItem role) {
        return normalizeRole(role).organizationUnits;
    }

    private Map<String, String> defaultFeatureValues() {
        Map<String, String> values = new LinkedHashMap<>();
        features().forEach(item -> values.put(item.name, item.defaultValue));
        return values;
    }

    private Map<String, String> mergeFeatureValues(Map<String, String> existing, List<NameValueItem> updates) {
        Map<String, String> values = existing == null || existing.isEmpty() ? defaultFeatureValues() : new LinkedHashMap<>(existing);
        list(updates).forEach(item -> values.put(item.name, item.value));
        return values;
    }

    private Map<String, String> databaseFeatureValues(List<Map<String, Object>> rows, Integer editionId, Integer tenantId) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!Objects.equals(dbInteger(row, "EditionId"), editionId)
                    || !Objects.equals(dbInteger(row, "TenantId"), tenantId)) {
                continue;
            }
            String name = dbNullableString(row, "Name");
            if (!safe(name).isBlank()) {
                values.put(name, dbString(row, "Value"));
            }
        }
        return values;
    }

    private List<NameValueItem> nameValues(Map<String, String> values) {
        Map<String, String> safeValues = values == null || values.isEmpty() ? defaultFeatureValues() : values;
        return safeValues.entrySet().stream().map(entry -> {
            NameValueItem item = new NameValueItem();
            item.name = entry.getKey();
            item.value = entry.getValue();
            return item;
        }).toList();
    }

    private long nextPaymentId() {
        return subscriptionPayments.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
    }

    private void decorateSubscriptionPayment(SubscriptionPaymentItem item) {
        item.gatewayName = gatewayName(item.gateway);
        item.paymentPeriodTypeName = paymentPeriodName(item.paymentPeriodType);
        item.statusName = paymentStatusName(item.status);
        item.editionPaymentTypeName = editionPaymentTypeName(item.editionPaymentType);
        item.editionDisplayName = edition(item.editionId).map(edition -> edition.displayName).orElse("");
        item.paymentId = safe(item.externalPaymentId).isBlank() ? "PAY-" + item.id : item.externalPaymentId;
        if (safe(item.description).isBlank()) {
            item.description = paymentDescription(item);
        }
    }

    private PaymentGatewayItem paymentGateway(int gatewayType, boolean supportsRecurringPayments) {
        PaymentGatewayItem item = new PaymentGatewayItem();
        item.gatewayType = gatewayType;
        item.supportsRecurringPayments = supportsRecurringPayments;
        return item;
    }

    private void updateTenantSubscription(SubscriptionPaymentItem payment) {
        TenantItem tenant = tenants.get(payment.tenantId);
        if (tenant == null) {
            return;
        }
        tenant.editionId = payment.editionId;
        tenant.isActive = true;
        tenant.isInTrialPeriod = false;
        tenant.subscriptionEndDateUtc = LocalDateTime.now().plusDays(payment.dayCount).toString();
        decorateTenant(tenant);
        if (databaseStoreMode && !loadingDatabaseState) {
            updateDatabaseTenant(tenant);
        }
    }

    private InvoiceItem decorateInvoice(InvoiceItem invoice) {
        if (invoice == null) {
            return null;
        }
        subscriptionPayments.values().stream()
                .filter(payment -> equalsText(payment.invoiceNo, invoice.invoiceNo))
                .findFirst()
                .ifPresent(payment -> {
                    invoice.subscriptionPaymentId = payment.id;
                    invoice.amount = payment.amount;
                    invoice.editionDisplayName = payment.editionDisplayName;
                });
        invoice.hostLegalName = hostSettings.billing.legalName;
        invoice.hostAddress = addressLines(hostSettings.billing.address);
        return invoice;
    }

    private Optional<InvoiceItem> invoiceByNo(String invoiceNo) {
        return invoices.values().stream()
                .filter(item -> equalsText(item.invoiceNo, invoiceNo))
                .findFirst();
    }

    private List<String> addressLines(String address) {
        return Arrays.stream(safe(address).split("\\R|,|;"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String addressText(List<String> address) {
        return address == null ? "" : address.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String paymentDescription(SubscriptionPaymentItem item) {
        return item.editionPaymentTypeName + " " + item.editionDisplayName + " / " + item.paymentPeriodTypeName;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String gatewayName(int gateway) {
        return gateway == 1 ? "Paypal" : "Stripe";
    }

    private String paymentPeriodName(int period) {
        return switch (period) {
            case 1 -> "Daily";
            case 7 -> "Weekly";
            case 365 -> "Annual";
            default -> "Monthly";
        };
    }

    private String paymentStatusName(int status) {
        return switch (status) {
            case 2 -> "Paid";
            case 3 -> "Failed";
            case 4 -> "Cancelled";
            case 5 -> "Completed";
            default -> "NotPaid";
        };
    }

    private String editionPaymentTypeName(int type) {
        return switch (type) {
            case 1 -> "BuyNow";
            case 2 -> "Upgrade";
            case 3 -> "Extend";
            default -> "NewRegistration";
        };
    }

    private CacheItem cache(String name, String displayName, int itemCount) {
        CacheItem item = new CacheItem();
        item.name = name;
        item.displayName = displayName;
        item.itemCount = itemCount;
        item.lastClearTime = "";
        return item;
    }

    private DynamicParameterItem dynamicParameter(int id, String name, String displayName, String inputType, String permission) {
        DynamicParameterItem item = new DynamicParameterItem();
        item.id = id;
        item.parameterName = name;
        item.displayName = displayName;
        item.inputType = inputType;
        item.permission = permission;
        return item;
    }

    private DynamicParameterValueItem dynamicParameterValue(int id, int dynamicParameterId, String value) {
        DynamicParameterValueItem item = new DynamicParameterValueItem();
        item.id = id;
        item.dynamicParameterId = dynamicParameterId;
        item.value = value;
        return decorateDynamicParameterValue(item);
    }

    private EntityDynamicParameterItem entityDynamicParameter(int id, String entityFullName, int dynamicParameterId) {
        EntityDynamicParameterItem item = new EntityDynamicParameterItem();
        item.id = id;
        item.entityFullName = entityFullName;
        item.dynamicParameterId = dynamicParameterId;
        return decorateEntityDynamicParameter(item);
    }

    private EntityDynamicParameterValueItem entityDynamicParameterValue(int id, int entityDynamicParameterId,
                                                                       String entityId, String value) {
        EntityDynamicParameterValueItem item = new EntityDynamicParameterValueItem();
        item.id = id;
        item.entityDynamicParameterId = entityDynamicParameterId;
        item.entityId = entityId;
        item.value = value;
        return decorateEntityDynamicParameterValue(item);
    }

    private DashboardCustomizationItem defaultDashboard(String application, String dashboardName) {
        String safeDashboardName = safe(dashboardName).isBlank() ? "TenantDashboard" : dashboardName;
        DashboardCustomizationItem dashboard = new DashboardCustomizationItem();
        dashboard.application = safe(application).isBlank() ? "Angular" : application;
        dashboard.dashboardName = safeDashboardName;
        List<DashboardWidgetItem> widgets = equalsText(safeDashboardName, "HostDashboard")
                ? List.of(
                dashboardWidget("Widgets_Host_TopStats", 12, 2, 0, 0),
                dashboardWidget("Widgets_Host_IncomeStatistics", 8, 6, 0, 2),
                dashboardWidget("Widgets_Host_EditionStatistics", 4, 6, 8, 2),
                dashboardWidget("Widgets_Host_SubscriptionExpiringTenants", 6, 5, 0, 8),
                dashboardWidget("Widgets_Host_RecentTenants", 6, 5, 6, 8))
                : List.of(
                dashboardWidget("Widgets_Tenant_TopStats", 12, 2, 0, 0),
                dashboardWidget("Widgets_Tenant_DailySales", 6, 5, 0, 2),
                dashboardWidget("Widgets_Tenant_ProfitShare", 6, 5, 6, 2),
                dashboardWidget("Widgets_Tenant_SalesSummary", 8, 5, 0, 7),
                dashboardWidget("Widgets_Tenant_GeneralStats", 4, 5, 8, 7),
                dashboardWidget("Widgets_Tenant_MemberActivity", 6, 5, 0, 12),
                dashboardWidget("Widgets_Tenant_RegionalStats", 6, 5, 6, 12));
        dashboard.pages.add(dashboardPage("Page" + randomCode(), "Default", widgets));
        return dashboard;
    }

    private DashboardPageItem dashboardPage(String id, String name, List<DashboardWidgetItem> widgets) {
        DashboardPageItem page = new DashboardPageItem();
        page.id = id;
        page.name = name;
        page.widgets = new ArrayList<>(list(widgets));
        return page;
    }

    private DashboardWidgetItem dashboardWidget(String widgetId, int width, int height, int positionX, int positionY) {
        DashboardWidgetItem widget = new DashboardWidgetItem();
        widget.widgetId = widgetId;
        widget.width = width;
        widget.height = height;
        widget.positionX = positionX;
        widget.positionY = positionY;
        return widget;
    }

    private int nextDashboardWidgetY(DashboardPageItem page) {
        if (page.widgets == null || page.widgets.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (DashboardWidgetItem widget : page.widgets) {
            max = Math.max(max, widget.positionY + widget.height);
        }
        return max;
    }

    private String dashboardKey(String application, String dashboardName) {
        return (safe(application).isBlank() ? "Angular" : application) + "::"
                + (safe(dashboardName).isBlank() ? "TenantDashboard" : dashboardName);
    }

    private DynamicParameterValueItem decorateDynamicParameterValue(DynamicParameterValueItem item) {
        dynamicParameter(item.dynamicParameterId).ifPresent(parameter -> item.parameterName = parameter.parameterName);
        return item;
    }

    private EntityDynamicParameterItem decorateEntityDynamicParameter(EntityDynamicParameterItem item) {
        dynamicParameter(item.dynamicParameterId).ifPresent(parameter -> {
            item.parameterName = parameter.parameterName;
            item.displayName = parameter.displayName;
        });
        return item;
    }

    private EntityDynamicParameterValueItem decorateEntityDynamicParameterValue(EntityDynamicParameterValueItem item) {
        entityDynamicParameter(item.entityDynamicParameterId).ifPresent(parameter -> {
            item.entityFullName = parameter.entityFullName;
            item.dynamicParameterId = parameter.dynamicParameterId;
            item.parameterName = parameter.parameterName;
        });
        return item;
    }

    private int nextDynamicParameterId() {
        return dynamicParameters.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    private int nextDynamicParameterValueId() {
        return dynamicParameterValues.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    private int nextEntityDynamicParameterId() {
        return entityDynamicParameters.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    private int nextEntityDynamicParameterValueId() {
        return entityDynamicParameterValues.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    private WebhookDefinitionItem webhookDefinition(String name, String displayName, String description) {
        WebhookDefinitionItem item = new WebhookDefinitionItem();
        item.name = name;
        item.displayName = displayName;
        item.description = description;
        return item;
    }

    private void upsertDatabaseWebhookSubscription(WebhookSubscriptionItem item) {
        Timestamp creationTime = Timestamp.valueOf(parseFlexibleDateTime(item.creationTime).orElse(LocalDateTime.now()));
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.SgsWebhookSubscriptions
                           SET WebhookUri = ?, Secret = ?, IsActive = ?, Webhooks = ?, Headers = ?
                         WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                safe(item.webhookUri),
                safe(item.secret),
                item.isActive,
                jsonSetting(item.webhooks == null ? List.of() : item.webhooks),
                jsonSetting(item.headers == null ? Map.of() : item.headers),
                item.id);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsWebhookSubscriptions
                                (Id, CreationTime, CreatorUserId, TenantId, WebhookUri, Secret, IsActive, Webhooks, Headers)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    item.id,
                    creationTime,
                    1L,
                    1,
                    safe(item.webhookUri),
                    safe(item.secret),
                    item.isActive,
                    jsonSetting(item.webhooks == null ? List.of() : item.webhooks),
                    jsonSetting(item.headers == null ? Map.of() : item.headers));
        }
    }

    private void updateDatabaseWebhookSubscriptionActive(UUID id, boolean active) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE dbo.SgsWebhookSubscriptions
                           SET IsActive = ?
                         WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """, active, id);
    }

    private void insertDatabaseWebhookEvent(WebhookEventItem item) {
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsWebhookEvents
                            (Id, WebhookName, Data, CreationTime, TenantId, IsDeleted, DeletionTime)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                item.id,
                safe(item.webhookName),
                item.data,
                Timestamp.valueOf(parseFlexibleDateTime(item.creationTime).orElse(LocalDateTime.now())),
                1,
                false,
                null);
    }

    private void insertDatabaseWebhookSendAttempt(WebhookSendAttemptItem item) {
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsWebhookSendAttempts
                            (Id, WebhookEventId, WebhookSubscriptionId, Response, ResponseStatusCode,
                             CreationTime, LastModificationTime, TenantId)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                item.id,
                item.webhookEventId,
                item.webhookSubscriptionId,
                item.response,
                item.responseStatusCode,
                Timestamp.valueOf(parseFlexibleDateTime(item.creationTime).orElse(LocalDateTime.now())),
                null,
                1);
    }

    private void updateDatabaseWebhookSendAttempt(WebhookSendAttemptItem item) {
        jdbcTemplate.update("""
                        UPDATE dbo.SgsWebhookSendAttempts
                           SET Response = ?, ResponseStatusCode = ?, LastModificationTime = ?
                         WHERE Id = ? AND (TenantId = 1 OR TenantId IS NULL)
                        """,
                item.response,
                item.responseStatusCode,
                Timestamp.valueOf(parseFlexibleDateTime(item.lastModificationTime).orElse(LocalDateTime.now())),
                item.id);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseWebhookNames(String value) {
        if (safe(value).isBlank()) {
            return new ArrayList<>();
        }
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            if (parsed instanceof List<?> list) {
                return list.stream()
                        .map(String::valueOf)
                        .filter(item -> !safe(item).isBlank())
                        .distinct()
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            if (parsed instanceof Map<?, ?> map) {
                return map.values().stream()
                        .map(String::valueOf)
                        .filter(item -> !safe(item).isBlank())
                        .distinct()
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        } catch (IOException | RuntimeException ignored) {
            // Some historical rows use a comma-separated string instead of JSON.
        }
        return splitDbCsv(value);
    }

    private Map<String, String> parseStringMap(String value) {
        if (safe(value).isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, String> output = new LinkedHashMap<>();
                map.forEach((key, mapValue) -> {
                    if (!safe(String.valueOf(key)).isBlank()) {
                        output.put(String.valueOf(key), mapValue == null ? "" : String.valueOf(mapValue));
                    }
                });
                return output;
            }
        } catch (IOException | RuntimeException ignored) {
            // Invalid header JSON is treated as no custom headers, matching ABP's tolerant read behavior.
        }
        return new LinkedHashMap<>();
    }

    private WebhookEventItem webhookEvent(String webhookName, String data) {
        WebhookEventItem item = new WebhookEventItem();
        item.id = UUID.randomUUID();
        item.webhookName = webhookName;
        item.data = data;
        item.creationTime = LocalDateTime.now().toString();
        return item;
    }

    private WebhookSendAttemptItem webhookSendAttempt(WebhookEventItem event, WebhookSubscriptionItem subscription,
                                                      String response, Integer statusCode) {
        WebhookSendAttemptItem item = new WebhookSendAttemptItem();
        item.id = UUID.randomUUID();
        item.webhookEventId = event.id;
        item.webhookSubscriptionId = subscription.id;
        item.webhookUri = subscription.webhookUri;
        item.webhookName = event.webhookName;
        item.data = event.data;
        item.response = response;
        item.responseStatusCode = statusCode;
        item.creationTime = LocalDateTime.now().toString();
        item.lastModificationTime = "";
        item.retryCount = 0;
        return item;
    }

    private WebhookSendAttemptItem decorateWebhookSendAttempt(WebhookSendAttemptItem item) {
        WebhookSubscriptionItem subscription = webhookSubscriptions.get(item.webhookSubscriptionId);
        if (subscription != null) {
            item.webhookUri = subscription.webhookUri;
        }
        WebhookEventItem event = webhookEvents.get(item.webhookEventId);
        if (event != null) {
            item.webhookName = event.webhookName;
            item.data = event.data;
        }
        return item;
    }

    private ThemeSettingsItem defaultTheme(String theme) {
        ThemeSettingsItem item = new ThemeSettingsItem();
        item.theme = theme;
        item.layout.layoutType = "fluid";
        item.header.desktopFixedHeader = true;
        item.header.mobileFixedHeader = true;
        item.header.headerSkin = equalsText(theme, "default") ? "light" : "dark";
        item.header.minimizeDesktopHeaderType = "none";
        item.header.headerMenuArrows = true;
        item.subHeader.fixedSubHeader = false;
        item.subHeader.subheaderStyle = "solid";
        item.menu.position = "left";
        item.menu.asideSkin = equalsText(theme, "default") ? "dark" : "light";
        item.menu.fixedAside = true;
        item.menu.allowAsideMinimizing = true;
        item.menu.defaultMinimizedAside = false;
        item.menu.submenuToggle = "accordion";
        item.menu.searchActive = false;
        item.footer.fixedFooter = false;
        return decorateTheme(item);
    }

    private ThemeSettingsItem normalizeTheme(ThemeSettingsItem input) {
        ThemeSettingsItem item = input == null ? defaultTheme("default") : input;
        if (safe(item.theme).isBlank()) {
            item.theme = "default";
        }
        if (item.layout == null) {
            item.layout = new ThemeSettingsItem.ThemeLayoutSettings();
        }
        if (item.header == null) {
            item.header = new ThemeSettingsItem.ThemeHeaderSettings();
        }
        if (item.subHeader == null) {
            item.subHeader = new ThemeSettingsItem.ThemeSubHeaderSettings();
        }
        if (item.menu == null) {
            item.menu = new ThemeSettingsItem.ThemeMenuSettings();
        }
        if (item.footer == null) {
            item.footer = new ThemeSettingsItem.ThemeFooterSettings();
        }
        ThemeSettingsItem defaults = defaultTheme(item.theme);
        if (safe(item.layout.layoutType).isBlank()) {
            item.layout.layoutType = defaults.layout.layoutType;
        }
        if (safe(item.header.headerSkin).isBlank()) {
            item.header.headerSkin = defaults.header.headerSkin;
        }
        if (safe(item.header.minimizeDesktopHeaderType).isBlank()) {
            item.header.minimizeDesktopHeaderType = defaults.header.minimizeDesktopHeaderType;
        }
        if (safe(item.subHeader.subheaderStyle).isBlank()) {
            item.subHeader.subheaderStyle = defaults.subHeader.subheaderStyle;
        }
        if (safe(item.menu.position).isBlank()) {
            item.menu.position = defaults.menu.position;
        }
        if (safe(item.menu.asideSkin).isBlank()) {
            item.menu.asideSkin = defaults.menu.asideSkin;
        }
        if (safe(item.menu.submenuToggle).isBlank()) {
            item.menu.submenuToggle = defaults.menu.submenuToggle;
        }
        return decorateTheme(item);
    }

    private ThemeSettingsItem decorateTheme(ThemeSettingsItem item) {
        item.isActive = equalsText(item.theme, activeUiTheme);
        return item;
    }

    private InstallSettingsItem normalizeInstallSettings(InstallSettingsItem input) {
        InstallSettingsItem item = input == null ? InstallSettingsItem.defaults() : input;
        if (safe(item.webSiteUrl).isBlank()) {
            item.webSiteUrl = "http://localhost:5173/";
        }
        if (!item.webSiteRootAddressMode && safe(item.serverUrl).isBlank()) {
            item.serverUrl = "http://localhost:9901/";
        }
        if (safe(item.defaultLanguage).isBlank()) {
            item.defaultLanguage = languages.values().stream()
                    .filter(language -> language.isDefault)
                    .map(language -> language.name)
                    .findFirst()
                    .orElse("zh-Hans");
        }
        if (item.smtpSettings == null) {
            item.smtpSettings = new SystemSettingsItem.EmailSettings();
        }
        if (item.billInfo == null) {
            item.billInfo = new SystemSettingsItem.HostBillingSettings();
        }
        if (safe(item.setupTime).isBlank()) {
            item.setupTime = LocalDateTime.now().toString();
        }
        return item;
    }

    private SystemSettingsItem.HostSettings normalizeHostSettings(SystemSettingsItem.HostSettings input) {
        SystemSettingsItem.HostSettings settings = input == null ? SystemSettingsItem.defaultHostSettings() : input;
        if (settings.general == null) {
            settings.general = new SystemSettingsItem.GeneralSettings();
        }
        if (settings.userManagement == null) {
            settings.userManagement = new SystemSettingsItem.HostUserManagementSettings();
        }
        if (settings.userManagement.sessionTimeOutSettings == null) {
            settings.userManagement.sessionTimeOutSettings = new SystemSettingsItem.SessionTimeOutSettings();
        }
        if (settings.email == null) {
            settings.email = new SystemSettingsItem.EmailSettings();
        }
        if (settings.tenantManagement == null) {
            settings.tenantManagement = new SystemSettingsItem.TenantManagementSettings();
        }
        if (settings.security == null) {
            settings.security = new SystemSettingsItem.SecuritySettings();
        }
        normalizeSecurity(settings.security);
        if (settings.billing == null) {
            settings.billing = new SystemSettingsItem.HostBillingSettings();
        }
        if (settings.otherSettings == null) {
            settings.otherSettings = new SystemSettingsItem.OtherSettings();
        }
        if (settings.externalLoginProviderSettings == null) {
            settings.externalLoginProviderSettings = new SystemSettingsItem.ExternalLoginProviderSettings();
        }
        normalizeExternalProviders(settings.externalLoginProviderSettings);
        return settings;
    }

    private SystemSettingsItem.TenantSettings normalizeTenantSettings(SystemSettingsItem.TenantSettings input) {
        SystemSettingsItem.TenantSettings settings = input == null ? SystemSettingsItem.defaultTenantSettings() : input;
        if (settings.general == null) {
            settings.general = new SystemSettingsItem.GeneralSettings();
        }
        if (settings.userManagement == null) {
            settings.userManagement = new SystemSettingsItem.TenantUserManagementSettings();
        }
        if (settings.userManagement.sessionTimeOutSettings == null) {
            settings.userManagement.sessionTimeOutSettings = new SystemSettingsItem.SessionTimeOutSettings();
        }
        if (settings.email == null) {
            settings.email = new SystemSettingsItem.TenantEmailSettings();
        }
        if (settings.ldap == null) {
            settings.ldap = new SystemSettingsItem.LdapSettings();
        }
        if (settings.security == null) {
            settings.security = new SystemSettingsItem.SecuritySettings();
        }
        normalizeSecurity(settings.security);
        if (settings.billing == null) {
            settings.billing = new SystemSettingsItem.TenantBillingSettings();
        }
        if (settings.otherSettings == null) {
            settings.otherSettings = new SystemSettingsItem.TenantOtherSettings();
        }
        if (settings.externalLoginProviderSettings == null) {
            settings.externalLoginProviderSettings = new SystemSettingsItem.ExternalLoginProviderSettings();
        }
        normalizeExternalProviders(settings.externalLoginProviderSettings);
        return settings;
    }

    private void normalizeSecurity(SystemSettingsItem.SecuritySettings settings) {
        if (settings.passwordComplexity == null) {
            settings.passwordComplexity = new SystemSettingsItem.PasswordComplexitySetting();
        }
        if (settings.defaultPasswordComplexity == null) {
            settings.defaultPasswordComplexity = new SystemSettingsItem.PasswordComplexitySetting();
        }
        if (settings.userLockOut == null) {
            settings.userLockOut = new SystemSettingsItem.UserLockOutSettings();
        }
        if (settings.twoFactorLogin == null) {
            settings.twoFactorLogin = new SystemSettingsItem.TwoFactorLoginSettings();
        }
    }

    private void normalizeExternalProviders(SystemSettingsItem.ExternalLoginProviderSettings settings) {
        if (settings.facebook == null) {
            settings.facebook = new SystemSettingsItem.FacebookSettings();
        }
        if (settings.google == null) {
            settings.google = new SystemSettingsItem.GoogleSettings();
        }
        if (settings.twitter == null) {
            settings.twitter = new SystemSettingsItem.TwitterSettings();
        }
        if (settings.microsoft == null) {
            settings.microsoft = new SystemSettingsItem.MicrosoftSettings();
        }
    }

    private void seedNotifications(Long adminUserId, Long queryUserId) {
        notificationSettings.put(adminUserId, defaultNotificationSettings(adminUserId));
        notificationSettings.put(queryUserId, defaultNotificationSettings(queryUserId));
        addNotification(adminUserId, "Capability.AbilityChanged", "能力表标准号已完成本地更新", "Info", true);
        addNotification(adminUserId, "System.LanguageReady", "语言管理模块已启用", "Success", false);
        addNotification(queryUserId, "Capability.QueryReady", "能力表查询权限已开通", "Info", true);
    }

    private NotificationItem addNotification(Long userId, String name, String message, String severity, boolean unread) {
        NotificationItem item = new NotificationItem();
        item.id = UUID.randomUUID();
        item.userId = userId;
        item.notificationName = name;
        item.message = message;
        item.severity = severity;
        item.creationTime = LocalDateTime.now().toString();
        item.readState = unread ? 0 : 1;
        item.readTime = unread ? null : LocalDateTime.now().toString();
        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseNotification(item);
        }
        notifications.put(item.id, item);
        return item;
    }

    private void insertDatabaseNotification(NotificationItem item) {
        if (item == null || item.id == null || item.userId == null) {
            return;
        }
        UUID tenantNotificationId = UUID.randomUUID();
        LocalDateTime creationTime = parseFlexibleDateTime(item.creationTime).orElse(LocalDateTime.now());
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsTenantNotifications
                            (Id, CreationTime, CreatorUserId, Data, DataTypeName, EntityId,
                             EntityTypeAssemblyQualifiedName, EntityTypeName, NotificationName, Severity, TenantId)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                tenantNotificationId,
                Timestamp.valueOf(creationTime),
                item.userId,
                databaseNotificationData(item),
                "Abp.Notifications.MessageNotificationData, Abp, Version=5.8.0.0, Culture=neutral, PublicKeyToken=null",
                null,
                null,
                null,
                truncateForColumn(safe(item.notificationName).isBlank() ? "App.SimpleMessage" : item.notificationName, 96),
                notificationSeverityCode(item.severity),
                1);
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsUserNotifications
                            (Id, CreationTime, State, TenantId, TenantNotificationId, UserId)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                item.id,
                Timestamp.valueOf(creationTime),
                item.readState,
                1,
                tenantNotificationId,
                item.userId);
    }

    private void syncDatabaseNotificationData(NotificationItem item) {
        if (!databaseStoreMode || loadingDatabaseState || item == null || item.id == null) {
            return;
        }
        jdbcTemplate.update("""
                        UPDATE tn
                           SET Data = ?
                          FROM dbo.SgsTenantNotifications tn
                          INNER JOIN dbo.SgsUserNotifications un ON un.TenantNotificationId = tn.Id
                         WHERE un.Id = ?
                        """,
                databaseNotificationData(item),
                item.id);
    }

    private String databaseNotificationData(NotificationItem item) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (item != null && item.data != null) {
            properties.putAll(item.data);
        }
        properties.putIfAbsent("Message", item == null ? "" : safe(item.message));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Message", item == null ? "" : safe(item.message));
        payload.put("Type", "Abp.Notifications.MessageNotificationData");
        payload.put("Properties", properties);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            return "{\"Message\":\"" + safe(item == null ? "" : item.message).replace("\"", "\\\"") + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private void applyNotificationData(NotificationItem item, String data) {
        if (item == null) {
            return;
        }
        item.data = new LinkedHashMap<>();
        String fallback = safe(item.notificationName);
        try {
            Map<String, Object> payload = safe(data).isBlank()
                    ? Map.of()
                    : objectMapper.readValue(data, Map.class);
            Object properties = payload.get("Properties");
            if (properties instanceof Map<?, ?> map) {
                map.forEach((key, value) -> item.data.put(String.valueOf(key), value));
            }
            Object message = payload.get("Message");
            if (message == null && properties instanceof Map<?, ?> map) {
                message = map.get("Message");
            }
            item.message = message == null ? fallback : String.valueOf(message);
        } catch (IOException | RuntimeException ex) {
            item.message = safe(data).isBlank() ? fallback : data;
        }
    }

    private int notificationSeverityCode(String severity) {
        return switch (safe(severity).toLowerCase(Locale.ROOT)) {
            case "success" -> 1;
            case "warn", "warning" -> 2;
            case "error" -> 3;
            case "fatal" -> 4;
            default -> 0;
        };
    }

    private String notificationSeverityName(Integer severity) {
        return switch (severity == null ? 0 : severity) {
            case 1 -> "Success";
            case 2 -> "Warn";
            case 3 -> "Error";
            case 4 -> "Fatal";
            default -> "Info";
        };
    }

    private void saveDatabaseNotificationSettings(Long userId, NotificationSettings settings) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("""
                DELETE FROM dbo.SgsNotificationSubscriptions
                 WHERE UserId = ? AND (TenantId = 1 OR TenantId IS NULL)
                """, userId);
        upsertDatabaseSetting(SETTING_REPLICA_NOTIFICATION_RECEIVE,
                String.valueOf(settings != null && settings.receiveNotifications), 1, userId);
        if (settings == null || !settings.receiveNotifications) {
            return;
        }
        for (NotificationSubscription subscription : list(settings.notifications)) {
            if (subscription == null || safe(subscription.name).isBlank() || !subscription.isSubscribed) {
                continue;
            }
            jdbcTemplate.update("""
                            INSERT INTO dbo.SgsNotificationSubscriptions
                                (Id, CreationTime, CreatorUserId, EntityId, EntityTypeAssemblyQualifiedName,
                                 EntityTypeName, NotificationName, TenantId, UserId)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    userId,
                    null,
                    null,
                    null,
                    truncateForColumn(subscription.name, 96),
                    1,
                    userId);
        }
    }

    private NotificationSettings defaultNotificationSettings(Long userId) {
        NotificationSettings settings = new NotificationSettings();
        settings.userId = userId;
        settings.receiveNotifications = true;
        settings.desktopNotifications = true;
        settings.emailNotifications = true;
        settings.smsNotifications = false;
        settings.notifications = defaultNotificationSubscriptions();
        return settings;
    }

    private List<NotificationSubscription> defaultNotificationSubscriptions() {
        return new ArrayList<>(List.of(
                notificationSubscription("Capability.AbilityChanged", "能力表变更"),
                notificationSubscription("Capability.QueryReady", "能力查询通知"),
                notificationSubscription("System.LanguageReady", "系统功能启用")
        ));
    }

    private NotificationSubscription notificationSubscription(String name, String displayName) {
        NotificationSubscription item = new NotificationSubscription();
        item.name = name;
        item.displayName = displayName;
        item.isSubscribed = true;
        return item;
    }

    private String notificationDisplayName(String name) {
        return switch (safe(name)) {
            case "Capability.AbilityChanged" -> "能力表变更";
            case "Capability.QueryReady" -> "能力查询通知";
            case "System.LanguageReady" -> "系统功能启用";
            case "App.NewTenantRegistered" -> "新租户注册";
            case "App.NewUserRegistered" -> "新用户注册";
            case "App.GdprDataPrepared" -> "个人数据准备完成";
            case "App.DownloadInvalidImportUsers" -> "下载无效导入用户";
            case "App.SimpleMessage" -> "系统消息";
            default -> safe(name);
        };
    }

    private void markNotificationRead(NotificationItem item) {
        item.readState = 1;
        item.readTime = LocalDateTime.now().toString();
    }

    private void seedChat(UserItem admin, UserItem queryUser) {
        ensureFriendship(admin.id, null, queryUser.id, null, 1);
        ensureFriendship(queryUser.id, null, admin.id, null, 1);
        String sharedMessageId = UUID.randomUUID().toString();
        ChatMessageItem senderCopy = chatMessage(queryUser.id, admin.id, 1, 2, 1,
                "Hi, this is a local replica chat message.", sharedMessageId);
        chatMessages.put(senderCopy.id, senderCopy);
        ChatMessageItem receiverCopy = chatMessage(admin.id, queryUser.id, 2, 1, 2,
                senderCopy.message, sharedMessageId);
        chatMessages.put(receiverCopy.id, receiverCopy);
    }

    private FriendItem ensureFriendship(Long userId, Integer tenantId, Long friendUserId, Integer friendTenantId, int state) {
        String key = friendshipKey(userId, tenantId, friendUserId, friendTenantId);
        FriendItem item = friendships.get(key);
        if (item == null) {
            item = new FriendItem();
            item.userId = userId;
            item.tenantId = tenantId;
            item.friendUserId = friendUserId;
            item.friendTenantId = friendTenantId;
            item.friendTenancyName = friendTenantId == null ? "" : "Tenant-" + friendTenantId;
            item.creationTime = LocalDateTime.now().toString();
            friendships.put(key, item);
        }
        item.state = state;
        if (databaseStoreMode && !loadingDatabaseState) {
            upsertDatabaseFriendship(item);
        }
        return decorateFriend(item);
    }

    private String friendshipKey(Long userId, Integer tenantId, Long friendUserId, Integer friendTenantId) {
        return "%s:%s:%s:%s".formatted(userId, tenantId == null ? "host" : tenantId,
                friendUserId, friendTenantId == null ? "host" : friendTenantId);
    }

    private Optional<LinkedUserItem> linkedUser(Long userId) {
        return user(userId).map(user -> {
            LinkedUserItem item = new LinkedUserItem();
            item.id = user.id;
            item.tenantId = 1;
            item.tenancyName = "default";
            item.username = user.userName;
            return item;
        });
    }

    private Comparator<UserDelegation> userDelegationComparator(String sorting) {
        String normalized = safe(sorting).trim().toLowerCase(Locale.ROOT);
        Comparator<UserDelegation> comparator = Comparator
                .comparing((UserDelegation item) -> safe(item.targetUserName))
                .thenComparing(item -> item.id == null ? 0L : item.id);
        return normalized.equals("username desc") ? comparator.reversed() : comparator;
    }

    private Comparator<UserItem> userComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.equalsIgnoreCase("ProductionDefault")) {
            return productionUserComparator();
        }
        if (normalized.isBlank()) {
            // Original GetUsersInput.Normalize defaults blank sorting to Name.
            normalized = "Name";
        }
        Comparator<UserItem> comparator = null;
        boolean desc = false;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            desc = desc || lower.endsWith(" desc");
            Comparator<UserItem> current = userFieldComparator(lower.replace(" desc", "").replace(" asc", "").trim());
            comparator = comparator == null ? current : comparator.thenComparing(current);
        }
        Comparator<UserItem> safeComparator = comparator == null ? userFieldComparator("name") : comparator;
        safeComparator = safeComparator.thenComparing(item -> item.id == null ? 0L : item.id);
        return desc ? safeComparator.reversed() : safeComparator;
    }

    private Comparator<UserItem> productionUserComparator() {
        return Comparator
                .comparingInt((UserItem item) -> productionUserOrder(item.userName))
                .thenComparing(item -> item.id == null ? Long.MAX_VALUE : item.id);
    }

    private int productionUserOrder(String userName) {
        for (int index = 0; index < PRODUCTION_USER_ORDER.size(); index++) {
            if (equalsText(PRODUCTION_USER_ORDER.get(index), userName)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private Comparator<UserItem> userFieldComparator(String field) {
        return switch (field) {
            case "username", "userName" -> Comparator.comparing(item -> safe(item.userName));
            case "surname" -> Comparator.comparing(item -> safe(item.surname));
            case "emailaddress" -> Comparator.comparing(item -> safe(item.emailAddress));
            case "creationtime" -> Comparator.comparing(item -> item.creationTime == null ? LocalDateTime.MIN : item.creationTime);
            case "id" -> Comparator.comparing(item -> item.id == null ? 0L : item.id);
            default -> Comparator.comparing(item -> safe(item.name));
        };
    }

    private Comparator<TenantItem> tenantComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original GetTenantsInput.Normalize defaults blank sorting to TenancyName.
            normalized = "TenancyName";
        }
        Comparator<TenantItem> comparator = null;
        boolean desc = false;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            desc = desc || lower.endsWith(" desc");
            Comparator<TenantItem> current = tenantFieldComparator(lower.replace(" desc", "").replace(" asc", "").trim());
            comparator = comparator == null ? current : comparator.thenComparing(current);
        }
        Comparator<TenantItem> safeComparator = comparator == null ? tenantFieldComparator("tenancyname") : comparator;
        safeComparator = safeComparator.thenComparing(item -> item.id == null ? 0 : item.id);
        return desc ? safeComparator.reversed() : safeComparator;
    }

    private Comparator<TenantItem> tenantFieldComparator(String field) {
        return switch (field) {
            case "editiondisplayname", "edition.displayname" -> Comparator.comparing(item -> safe(item.editionDisplayName));
            case "name" -> Comparator.comparing(item -> safe(item.name));
            case "creationtime" -> Comparator.comparing(item -> safe(item.creationTime));
            case "id" -> Comparator.comparing(item -> item.id == null ? 0 : item.id);
            default -> Comparator.comparing(item -> safe(item.tenancyName));
        };
    }

    private Comparator<SubscriptionPaymentItem> paymentHistoryComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original GetPaymentHistoryInput.Normalize defaults blank sorting to CreationTime.
            normalized = "CreationTime";
        }
        Comparator<SubscriptionPaymentItem> comparator = null;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            boolean desc = token.toLowerCase(Locale.ROOT).endsWith(" desc");
            String field = token.toLowerCase(Locale.ROOT).replace(" desc", "").replace(" asc", "").trim();
            Comparator<SubscriptionPaymentItem> current = paymentHistoryFieldComparator(field);
            comparator = comparator == null ? (desc ? current.reversed() : current)
                    : comparator.thenComparing(desc ? current.reversed() : current);
        }
        return (comparator == null ? paymentHistoryFieldComparator("creationtime") : comparator)
                .thenComparing(item -> item.id == null ? 0L : item.id);
    }

    private Comparator<SubscriptionPaymentItem> paymentHistoryFieldComparator(String field) {
        return switch (field) {
            case "editiondisplayname", "edition.displayname" -> Comparator.comparing(item -> safe(item.editionDisplayName));
            case "amount" -> Comparator.comparing(item -> item.amount == null ? BigDecimal.ZERO : item.amount);
            case "status" -> Comparator.comparingInt(item -> item.status);
            case "id" -> Comparator.comparing(item -> item.id == null ? 0L : item.id);
            default -> Comparator.comparing(item -> safe(item.creationTime));
        };
    }

    private Comparator<LinkedUserItem> linkedUserComparator(String sorting) {
        String normalized = safe(sorting).toLowerCase(Locale.ROOT);
        Comparator<LinkedUserItem> comparator = Comparator
                .comparing((LinkedUserItem item) -> safe(item.tenancyName))
                .thenComparing(item -> safe(item.username));
        return normalized.contains("desc") ? comparator.reversed() : comparator;
    }

    private FriendItem decorateFriend(FriendItem item) {
        user(item.friendUserId).ifPresent(friend -> {
            item.friendUserName = friend.userName;
            item.friendProfilePictureId = friend.profilePictureId;
            item.isOnline = friend.isActive;
        });
        item.friendTenancyName = item.friendTenantId == null ? "" : safe(item.friendTenancyName);
        if (item.state == 0) {
            item.state = 1;
        }
        item.unreadMessageCount = (int) chatMessages.values().stream()
                .filter(message -> Objects.equals(message.userId, item.userId))
                .filter(message -> Objects.equals(message.targetUserId, item.friendUserId))
                .filter(message -> Objects.equals(message.targetTenantId, item.friendTenantId))
                .filter(message -> message.readState == 1)
                .count();
        return item;
    }

    private ChatMessageItem chatMessage(Long userId, Long targetUserId, int side, int readState, int receiverReadState,
                                        String message, String sharedMessageId) {
        ChatMessageItem item = new ChatMessageItem();
        item.id = nextChatMessageId();
        item.userId = userId;
        item.tenantId = null;
        item.targetUserId = targetUserId;
        item.targetTenantId = null;
        item.side = side;
        item.readState = readState;
        item.receiverReadState = receiverReadState;
        item.message = message;
        item.creationTime = LocalDateTime.now().toString();
        item.sharedMessageId = sharedMessageId;
        return item;
    }

    private long nextChatMessageId() {
        return chatMessages.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
    }

    private void seedLanguages() {
        languages.put(1, language(1, "zh-Hans", "简体中文", "famfamfam-flags cn", true));
        languages.put(2, language(2, "en", "English", "famfamfam-flags us", false));
        seedLanguageTexts("zh-Hans");
        seedLanguageTexts("en");
    }

    private boolean normalizeDefaultLanguageIcons() {
        boolean changed = false;
        for (LanguageItem language : languages.values()) {
            if (equalsText(language.name, "zh-Hans") && equalsText(language.icon, "famfamfam-flag-cn")) {
                language.icon = "famfamfam-flags cn";
                changed = true;
            }
            if (equalsText(language.name, "en")
                    && (equalsText(language.icon, "famfamfam-flag-gb") || equalsText(language.icon, "famfamfam-flags gb"))) {
                language.icon = "famfamfam-flags us";
                changed = true;
            }
            decorateLanguage(language);
        }
        return changed;
    }

    private LanguageItem language(int id, String name, String displayName, String icon, boolean isDefault) {
        LanguageItem language = new LanguageItem();
        language.id = id;
        language.name = name;
        language.displayName = displayName;
        language.icon = icon;
        language.isDefault = isDefault;
        language.isDisabled = false;
        language.isEnabled = true;
        language.creationTime = LocalDateTime.now().minusDays(id).toString();
        return language;
    }

    private LanguageItem decorateLanguage(LanguageItem language) {
        if (language != null) {
            language.isEnabled = !language.isDisabled;
        }
        return language;
    }

    private boolean seedLanguageTexts(String languageName) {
        boolean changed = false;
        changed |= seedLanguageText(languageName, "AppName", "能力表系统", "Capability Table");
        changed |= seedLanguageText(languageName, "Login", "登录", "Login");
        changed |= seedLanguageText(languageName, "Logout", "退出", "Logout");
        changed |= seedLanguageText(languageName, "Dashboard", "工作台", "Dashboard");
        changed |= seedLanguageText(languageName, "AbilityManagement", "能力表管理", "Ability Management");
        changed |= seedLanguageText(languageName, "AbilityQuery", "能力表查询", "Ability Query");
        changed |= seedLanguageText(languageName, "SystemManagement", "系统管理", "System Management");
        changed |= seedLanguageText(languageName, "LanguageManagement", "语言管理", "Language Management");
        return changed;
    }

    private boolean seedLanguageText(String languageName, String key, String zhValue, String enValue) {
        boolean exists = languageTexts.stream()
                .anyMatch(item -> equalsText(item.languageName, languageName) && equalsText(item.key, key));
        if (exists) {
            return false;
        }
        LanguageTextItem item = new LanguageTextItem();
        item.id = nextLanguageTextId();
        item.sourceName = "CapabilityTable";
        item.languageName = languageName;
        item.key = key;
        item.baseValue = zhValue;
        item.targetValue = equalsText(languageName, "en") ? enValue : zhValue;
        languageTexts.add(item);
        return true;
    }

    private int nextLanguageTextId() {
        return languageTexts.stream().map(item -> item.id == null ? 0 : item.id).max(Integer::compareTo).orElse(0) + 1;
    }

    private UserDelegation decorateDelegation(UserDelegation input) {
        UserDelegation output = new UserDelegation();
        output.id = input.id;
        output.sourceUserId = input.sourceUserId;
        output.targetUserId = input.targetUserId;
        output.tenantId = input.tenantId;
        output.startTime = input.startTime;
        output.endTime = input.endTime;
        UserItem target = users.get(input.targetUserId);
        output.targetUserName = target == null ? input.targetUserName : target.userName;
        output.targetName = target == null ? input.targetName : String.join(" ", safe(target.name), safe(target.surname)).trim();
        output.active = isDelegationActive(output, LocalDateTime.now());
        return output;
    }

    private boolean isDelegationActive(UserDelegation delegation, LocalDateTime now) {
        LocalDateTime start = parseDateTime(delegation.startTime).orElse(LocalDateTime.MIN);
        LocalDateTime end = parseDateTime(delegation.endTime).orElse(LocalDateTime.MAX);
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private Optional<LocalDateTime> parseDateTime(String value) {
        try {
            return safe(value).isBlank() ? Optional.empty() : Optional.of(LocalDateTime.parse(value));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public void appendAuditLog(AuditLog log, Integer tenantId) {
        if (log == null) {
            return;
        }
        fillAuditDefaults(log, log.userId);
        if (databaseStoreMode) {
            insertDatabaseAuditLog(log, tenantId);
            return;
        }
        auditLogs.add(log);
        persist();
    }

    private void insertDatabaseAuditLog(AuditLog log, Integer tenantId) {
        LocalDateTime executionTime = parseFlexibleDateTime(log.executionTime).orElse(LocalDateTime.now());
        jdbcTemplate.update("""
                        INSERT INTO dbo.SgsAuditLogs
                            (BrowserInfo, ClientIpAddress, ClientName, CustomData, Exception, ExecutionDuration,
                             ExecutionTime, ImpersonatorTenantId, ImpersonatorUserId, MethodName, Parameters,
                             ServiceName, TenantId, UserId, ReturnValue)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                truncateForColumn(log.browserInfo, 512),
                truncateForColumn(log.clientIpAddress, 64),
                truncateForColumn(log.clientName, 128),
                truncateForColumn(log.customData, 2000),
                truncateForColumn(log.exception, 2000),
                log.executionDuration == null ? 0 : log.executionDuration,
                Timestamp.valueOf(executionTime),
                log.impersonatorTenantId,
                log.impersonatorUserId,
                truncateForColumn(log.methodName, 256),
                truncateForColumn(log.parameters, 1024),
                truncateForColumn(log.serviceName, 256),
                tenantId == null ? 1 : tenantId,
                log.userId,
                log.result);
    }

    private String truncateForColumn(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void audit(Long userId, String serviceName, String methodName, String result) {
        if (isHttpApiRequestActive()) {
            return;
        }
        AuditLog log = new AuditLog();
        log.userId = userId;
        log.serviceName = serviceName;
        log.methodName = methodName;
        log.result = result;
        log.customData = result;
        fillAuditDefaults(log, userId);
        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseAuditLog(log, 1);
            return;
        }
        auditLogs.add(log);
    }

    private boolean isHttpApiRequestActive() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRequestURI().startsWith("/api/");
        }
        return false;
    }

    private boolean fillAuditDefaults(AuditLog log, Long fallbackUserId) {
        boolean changed = false;
        if (log.id == null) {
            log.id = nextAuditLogId();
            changed = true;
        }
        if (log.userId == null) {
            log.userId = fallbackUserId;
            changed = true;
        }
        if (safe(log.userName).isBlank()) {
            log.userName = user(log.userId).map(user -> user.userName).orElse("system");
            changed = true;
        }
        if (safe(log.executionTime).isBlank()) {
            log.executionTime = safe(log.time).isBlank() ? LocalDateTime.now().toString() : log.time;
            changed = true;
        }
        if (safe(log.time).isBlank()) {
            log.time = log.executionTime;
            changed = true;
        }
        if (log.executionDuration == null) {
            log.executionDuration = 20 + (int) (Math.abs(Objects.hash(log.serviceName, log.methodName, log.time)) % 180);
            changed = true;
        }
        if (safe(log.parameters).isBlank()) {
            log.parameters = "{}";
            changed = true;
        }
        if (safe(log.clientIpAddress).isBlank()) {
            log.clientIpAddress = "127.0.0.1";
            changed = true;
        }
        if (safe(log.clientName).isBlank()) {
            log.clientName = "CapabilityReplica";
            changed = true;
        }
        if (safe(log.browserInfo).isBlank()) {
            log.browserInfo = "Chrome";
            changed = true;
        }
        if (safe(log.result).isBlank()) {
            log.result = safe(log.exception).isBlank() ? "成功" : "失败";
            changed = true;
        }
        if (safe(log.customData).isBlank()) {
            log.customData = log.result;
            changed = true;
        }
        return changed;
    }

    private void recordEntityChange(Long userId, String entityId, String entityTypeFullName, String description,
                                    int changeType, EntityPropertyChangeItem... propertyChanges) {
        EntityChangeItem change = new EntityChangeItem();
        change.userId = userId;
        change.userName = user(userId).map(user -> user.userName).orElse("system");
        change.changeTime = LocalDateTime.now().toString();
        change.entityTypeFullName = entityTypeFullName;
        change.entityTypeDescription = description;
        change.entityId = entityId;
        change.changeType = changeType;
        change.changeTypeName = changeTypeName(changeType);
        change.tenantId = 1;

        List<EntityPropertyChangeItem> validPropertyChanges = new ArrayList<>();
        for (EntityPropertyChangeItem propertyChange : propertyChanges) {
            if (propertyChange == null || safe(propertyChange.propertyName).isBlank()) {
                continue;
            }
            validPropertyChanges.add(propertyChange);
        }

        if (databaseStoreMode && !loadingDatabaseState) {
            insertDatabaseEntityChange(change, validPropertyChanges);
            entityChanges.add(change);
            entityPropertyChanges.addAll(validPropertyChanges);
            return;
        }

        change.id = nextEntityChangeId();
        change.entityChangeSetId = nextEntityChangeSetId();
        entityChanges.add(change);

        for (EntityPropertyChangeItem propertyChange : validPropertyChanges) {
            propertyChange.id = nextEntityPropertyChangeId();
            propertyChange.entityChangeId = change.id;
            propertyChange.tenantId = 1;
            entityPropertyChanges.add(propertyChange);
        }
    }

    private void insertDatabaseEntityChange(EntityChangeItem change, List<EntityPropertyChangeItem> propertyChanges) {
        LocalDateTime changeTime = parseFlexibleDateTime(change.changeTime).orElse(LocalDateTime.now());
        Long changeSetId = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsEntityChangeSets
                            (BrowserInfo, ClientIpAddress, ClientName, CreationTime, ExtensionData,
                             ImpersonatorTenantId, ImpersonatorUserId, Reason, TenantId, UserId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.class,
                "CapabilityReplica",
                "127.0.0.1",
                "CapabilityReplica",
                Timestamp.valueOf(changeTime),
                null,
                null,
                null,
                "Replica operation",
                1,
                change.userId);
        String databaseEntityType = databaseEntityTypeFullName(change.entityTypeFullName);
        Long changeId = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.SgsEntityChanges
                            (ChangeTime, ChangeType, EntityChangeSetId, EntityId, EntityTypeFullName, TenantId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                Long.class,
                Timestamp.valueOf(changeTime),
                change.changeType == null ? 1 : change.changeType,
                changeSetId,
                databaseEntityId(change.entityId),
                truncateForColumn(databaseEntityType, 192),
                1);
        change.id = changeId;
        change.entityChangeSetId = changeSetId;
        change.entityTypeFullName = databaseEntityType;

        for (EntityPropertyChangeItem propertyChange : propertyChanges) {
            Long propertyChangeId = jdbcTemplate.queryForObject("""
                            INSERT INTO dbo.SgsEntityPropertyChanges
                                (EntityChangeId, NewValue, OriginalValue, PropertyName, PropertyTypeFullName, TenantId)
                            OUTPUT INSERTED.Id
                            VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Long.class,
                    changeId,
                    truncateForColumn(databaseHistoryValue(propertyChange.newValue), 512),
                    truncateForColumn(databaseHistoryValue(propertyChange.originalValue), 512),
                    truncateForColumn(propertyChange.propertyName, 96),
                    truncateForColumn(safe(propertyChange.propertyTypeFullName).isBlank()
                            ? "System.String"
                            : propertyChange.propertyTypeFullName, 192),
                    1);
            propertyChange.id = propertyChangeId;
            propertyChange.entityChangeId = changeId;
            propertyChange.tenantId = 1;
        }
    }

    private String databaseEntityTypeFullName(String entityTypeFullName) {
        return equalsText(entityTypeFullName, ABILITY_ENTITY) ? PRODUCTION_ABILITY_ENTITY : entityTypeFullName;
    }

    private String databaseEntityId(String entityId) {
        if (safe(entityId).isBlank()) {
            return null;
        }
        return truncateForColumn(databaseHistoryValue(entityId), 48);
    }

    private String databaseHistoryValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            return "\"" + safe(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    private EntityPropertyChangeItem propertyChange(String propertyName, String typeFullName,
                                                    String originalValue, String newValue) {
        EntityPropertyChangeItem item = new EntityPropertyChangeItem();
        item.propertyName = propertyName;
        item.propertyTypeFullName = typeFullName;
        item.originalValue = originalValue;
        item.newValue = newValue;
        return item;
    }

    private EntityPropertyChangeItem[] abilityPropertyChanges(Ability original, Ability current) {
        List<EntityPropertyChangeItem> changes = new ArrayList<>();
        addAbilityPropertyChange(changes, "OrgName", original, current, ability -> ability.orgName);
        addAbilityPropertyChange(changes, "TypeName", original, current, ability -> ability.typeName);
        addAbilityPropertyChange(changes, "SamplingName", original, current, ability -> ability.samplingName);
        addAbilityPropertyChange(changes, "TestItem", original, current, ability -> ability.testItem);
        addAbilityPropertyChange(changes, "Price", original, current, ability -> ability.price);
        addAbilityPropertyChange(changes, "StandardNo", original, current, ability -> ability.standardNo);
        addAbilityPropertyChange(changes, "Remark", original, current, ability -> ability.remark);
        addAbilityPropertyChange(changes, "MethodName", original, current, ability -> ability.methodName);
        addAbilityPropertyChange(changes, "MethodEngName", original, current, ability -> ability.methodEngName);
        addAbilityPropertyChange(changes, "CycleWorkingDay", original, current, ability -> ability.cycleWorkingDay);
        addAbilityPropertyChange(changes, "MassRequired", original, current, ability -> ability.massRequired);
        addAbilityPropertyChange(changes, "SizeRequired", original, current, ability -> ability.sizeRequired);
        addAbilityPropertyChange(changes, "LabAbility", original, current, this::labAbilityHistoryValue);
        addAbilityPropertyChange(changes, "DetectionLimit", original, current, ability -> ability.detectionLimit);
        addAbilityPropertyChange(changes, "StandardNoSgs", original, current, ability -> ability.standardNoSgs);
        addAbilityPropertyChange(changes, "StandardNoSop", original, current, ability -> ability.standardNoSop);
        addAbilityPropertyChange(changes, "StandardNoOthers", original, current, ability -> ability.standardNoOthers);
        addAbilityPropertyChange(changes, "StandardNoDz", original, current, ability -> ability.standardNoDz);
        return changes.toArray(EntityPropertyChangeItem[]::new);
    }

    private void addAbilityPropertyChange(List<EntityPropertyChangeItem> changes, String propertyName,
                                          Ability original, Ability current, Function<Ability, String> getter) {
        String originalValue = original == null ? null : getter.apply(original);
        String newValue = current == null ? null : getter.apply(current);
        boolean lifecycleChange = original == null || current == null;
        if (lifecycleChange && safe(originalValue).isBlank() && safe(newValue).isBlank()) {
            return;
        }
        if (!lifecycleChange && Objects.equals(safe(originalValue), safe(newValue))) {
            return;
        }
        changes.add(propertyChange(propertyName, "System.String", originalValue, newValue));
    }

    private String labAbilityHistoryValue(Ability ability) {
        if (ability == null || ability.labAbilities == null) {
            return "";
        }
        return ability.labAbilities.stream()
                .filter(item -> item != null && item.isAbility)
                .map(item -> safe(item.code))
                .filter(code -> !code.isBlank())
                .map(code -> code + ";")
                .collect(Collectors.joining());
    }

    private String changeTypeName(Integer changeType) {
        return switch (changeType == null ? -1 : changeType) {
            case 0 -> "Created";
            case 1 -> "Updated";
            case 2 -> "Deleted";
            default -> "Unknown";
        };
    }

    private String entityDescription(String entityTypeFullName) {
        if (isAbilityEntity(entityTypeFullName)) {
            return "能力表";
        }
        if (equalsText(entityTypeFullName, TENANT_ENTITY)) {
            return "租户";
        }
        if (equalsText(entityTypeFullName, USER_ENTITY)) {
            return "用户";
        }
        return stripNamespace(entityTypeFullName);
    }

    private Optional<Long> userIdByName(String userName) {
        return users.values().stream()
                .filter(user -> equalsText(user.userName, userName))
                .map(user -> user.id)
                .findFirst();
    }

    private long nextAuditLogId() {
        return auditLogs.stream()
                .map(log -> log.id)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }

    private long nextEntityChangeId() {
        return entityChanges.stream()
                .map(change -> change.id)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }

    private long nextEntityChangeSetId() {
        return entityChanges.stream()
                .map(change -> change.entityChangeSetId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }

    private long nextEntityPropertyChangeId() {
        return entityPropertyChanges.stream()
                .map(change -> change.id)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }

    private List<String> defaultPropertyNames() {
        return defaultProperties().stream().map(prop -> prop.camelCase).toList();
    }

    private List<AbilityProperty> defaultProperties() {
        return List.of(
                property("OrgName", "业务部门"),
                property("TypeName", "类型"),
                property("SamplingName", "样品名称"),
                property("TestItem", "测试项目"),
                property("MethodName", "方法中文描述"),
                property("MethodEngName", "方法英文描述"),
                property("StandardNo", "标准号"),
                property("CycleWorkingDay", "检测周期/工作日"),
                property("MassRequired", "所需样品量(g)"),
                property("SizeRequired", "样品粒度要求"),
                property("DetectionLimit", "适用范围"),
                property("Price", "价格"),
                property("Remark", "备注"),
                property("LabAbility", "实验室能力"),
                property("StandardNoSgs", "标准编号SGS"),
                property("StandardNoSop", "标准编号SOP"),
                property("StandardNoOthers", "标准编号OTHERS"),
                property("StandardNoDz", "标准编号DZ")
        );
    }

    private AbilityProperty property(String name, String title) {
        AbilityProperty property = new AbilityProperty();
        property.name = name;
        property.camelCase = camelCase(name);
        property.title = title;
        return property;
    }

    private boolean propertyEnabled(Collection<String> enabledProperties, String propertyName) {
        String expected = abilityPropertyCamelCase(propertyName);
        return enabledProperties != null && enabledProperties.stream()
                .map(this::abilityPropertyCamelCase)
                .anyMatch(expected::equals);
    }

    private String abilityPropertyCamelCase(String propertyName) {
        String value = safe(propertyName);
        if (value.isBlank()) {
            return value;
        }
        return defaultProperties().stream()
                .filter(property -> equalsText(property.name, value) || equalsText(property.camelCase, value))
                .map(property -> property.camelCase)
                .findFirst()
                .orElseGet(() -> camelCase(value));
    }

    private OrganizationUnit org(Long id, Long parentId, String displayName) {
        OrganizationUnit org = new OrganizationUnit();
        org.id = id;
        org.parentId = parentId;
        org.displayName = displayName;
        return org;
    }

    private Laboratory lab(String code, String name, String engName, String leader, String contact, String address) {
        Laboratory lab = new Laboratory();
        lab.id = UUID.randomUUID();
        lab.code = code;
        lab.name = name;
        lab.engName = engName;
        lab.leader = leader;
        lab.contactInfo = contact;
        lab.address = address;
        lab.hasCnas = true;
        lab.hasCms = true;
        return lab;
    }

    private RoleItem role(int id, String name, String displayName, boolean isStatic, boolean isDefault,
                          List<String> grantedPermissionNames) {
        RoleItem role = new RoleItem();
        role.id = id;
        role.name = name;
        role.displayName = displayName;
        role.isStatic = isStatic;
        role.isDefault = isDefault;
        role.creationTime = LocalDateTime.now().minusDays(id);
        role.grantedPermissionNames.addAll(grantedPermissionNames);
        return role;
    }

    private UserItem user(long id, String name, String surname, String userName, String email, String phone, boolean active) {
        UserItem user = new UserItem();
        user.id = id;
        user.name = name;
        user.surname = surname;
        user.userName = userName;
        user.emailAddress = email;
        user.phoneNumber = phone;
        user.isEmailConfirmed = true;
        user.isPhoneNumberConfirmed = phone != null && !phone.isBlank();
        user.isActive = active;
        user.isLockoutEnabled = true;
        user.preferredLanguageName = "zh-Hans";
        user.creationTime = LocalDateTime.now().minusDays(id);
        return user;
    }

    private UserDelegation userDelegation(Long sourceUserId, UserItem target) {
        UserDelegation delegation = new UserDelegation();
        delegation.id = 1L;
        delegation.sourceUserId = sourceUserId;
        delegation.targetUserId = target.id;
        delegation.tenantId = 1;
        delegation.targetUserName = target.userName;
        delegation.targetName = String.join(" ", safe(target.name), safe(target.surname)).trim();
        delegation.startTime = LocalDateTime.now().minusDays(1).toString();
        delegation.endTime = LocalDateTime.now().plusDays(30).toString();
        delegation.active = true;
        return delegation;
    }

    private void seedPermissions() {
        permission("Pages", "页面", null, 0);
        permission("Pages.Administration", "系统管理", "Pages", 1);
        permission("Pages.Administration.Roles", "角色管理", "Pages.Administration", 2);
        permission("Pages.Administration.Users", "用户管理", "Pages.Administration", 2);
        permission("Pages.Administration.Users.Create", "创建用户", "Pages.Administration.Users", 3);
        permission("Pages.Administration.Users.Edit", "编辑用户", "Pages.Administration.Users", 3);
        permission("Pages.Administration.Users.Delete", "删除用户", "Pages.Administration.Users", 3);
        permission("Pages.Administration.Users.ChangePermissions", "维护用户权限", "Pages.Administration.Users", 3);
        permission("Pages.Administration.Users.Impersonation", "模拟登录", "Pages.Administration.Users", 3);
        permission("Pages.Administration.Languages", "语言管理", "Pages.Administration", 2);
        permission("Pages.Administration.Languages.Create", "创建语言", "Pages.Administration.Languages", 3);
        permission("Pages.Administration.Languages.Edit", "编辑语言", "Pages.Administration.Languages", 3);
        permission("Pages.Administration.Languages.Delete", "删除语言", "Pages.Administration.Languages", 3);
        permission("Pages.Administration.Languages.ChangeTexts", "维护语言文本", "Pages.Administration.Languages", 3);
        permission("Pages.Administration.Host.Maintenance", "维护管理", "Pages.Administration", 2);
        permission("Pages.Administration.Host.Settings", "宿主设置", "Pages.Administration", 2);
        permission("Pages.Administration.Host.Dashboard", "宿主看板", "Pages.Administration", 2);
        permission("Pages.Tenant.Dashboard", "租户看板", "Pages", 1);
        permission("Pages.Administration.Tenant.Settings", "租户设置", "Pages.Administration", 2);
        permission("Pages.Tenants", "租户管理", "Pages", 1);
        permission("Pages.Tenants.Create", "创建租户", "Pages.Tenants", 2);
        permission("Pages.Tenants.Edit", "编辑租户", "Pages.Tenants", 2);
        permission("Pages.Tenants.Delete", "删除租户", "Pages.Tenants", 2);
        permission("Pages.Tenants.ChangeFeatures", "维护租户功能", "Pages.Tenants", 2);
        permission("Pages.Editions", "版本管理", "Pages", 1);
        permission("Pages.Editions.Create", "创建版本", "Pages.Editions", 2);
        permission("Pages.Editions.Edit", "编辑版本", "Pages.Editions", 2);
        permission("Pages.Editions.Delete", "删除版本", "Pages.Editions", 2);
        permission("Pages.Administration.SubscriptionManagement", "订阅管理", "Pages.Administration", 2);
        permission("Pages.Administration.DynamicParameters", "动态参数", "Pages.Administration", 2);
        permission("Pages.Administration.DynamicParameters.Create", "创建动态参数", "Pages.Administration.DynamicParameters", 3);
        permission("Pages.Administration.DynamicParameters.Edit", "编辑动态参数", "Pages.Administration.DynamicParameters", 3);
        permission("Pages.Administration.DynamicParameters.Delete", "删除动态参数", "Pages.Administration.DynamicParameters", 3);
        permission("Pages.Administration.DynamicParameterValue", "动态参数值", "Pages.Administration", 2);
        permission("Pages.Administration.DynamicParameterValue.Create", "创建动态参数值", "Pages.Administration.DynamicParameterValue", 3);
        permission("Pages.Administration.DynamicParameterValue.Edit", "编辑动态参数值", "Pages.Administration.DynamicParameterValue", 3);
        permission("Pages.Administration.DynamicParameterValue.Delete", "删除动态参数值", "Pages.Administration.DynamicParameterValue", 3);
        permission("Pages.Administration.EntityDynamicParameters", "实体动态参数", "Pages.Administration", 2);
        permission("Pages.Administration.EntityDynamicParameters.Create", "创建实体动态参数", "Pages.Administration.EntityDynamicParameters", 3);
        permission("Pages.Administration.EntityDynamicParameters.Edit", "编辑实体动态参数", "Pages.Administration.EntityDynamicParameters", 3);
        permission("Pages.Administration.EntityDynamicParameters.Delete", "删除实体动态参数", "Pages.Administration.EntityDynamicParameters", 3);
        permission("Pages.Administration.EntityDynamicParameterValue", "实体动态参数值", "Pages.Administration", 2);
        permission("Pages.Administration.EntityDynamicParameterValue.Create", "创建实体动态参数值", "Pages.Administration.EntityDynamicParameterValue", 3);
        permission("Pages.Administration.EntityDynamicParameterValue.Edit", "编辑实体动态参数值", "Pages.Administration.EntityDynamicParameterValue", 3);
        permission("Pages.Administration.EntityDynamicParameterValue.Delete", "删除实体动态参数值", "Pages.Administration.EntityDynamicParameterValue", 3);
        permission("Pages.Administration.WebhookSubscription", "Webhook订阅", "Pages.Administration", 2);
        permission("Pages.Administration.WebhookSubscription.Create", "创建Webhook订阅", "Pages.Administration.WebhookSubscription", 3);
        permission("Pages.Administration.WebhookSubscription.Edit", "编辑Webhook订阅", "Pages.Administration.WebhookSubscription", 3);
        permission("Pages.Administration.WebhookSubscription.ChangeActivity", "启停Webhook订阅", "Pages.Administration.WebhookSubscription", 3);
        permission("Pages.Administration.WebhookSubscription.Detail", "Webhook订阅详情", "Pages.Administration.WebhookSubscription", 3);
        permission("Pages.Administration.Webhook.ListSendAttempts", "Webhook发送记录", "Pages.Administration", 2);
        permission("Pages.Administration.Webhook.ResendWebhook", "重发Webhook", "Pages.Administration.Webhook.ListSendAttempts", 3);
        permission("Pages.Administration.UiCustomization", "UI定制", "Pages.Administration", 2);
        permission("Pages.DemoUiComponents", "示例组件", "Pages", 1);
        permission("Pages.Administration.OrganizationUnits", "业务线管理", "Pages.Administration", 2);
        permission("Pages.Administration.Laboratory", "实验室管理", "Pages.Administration", 2);
        permission("Pages.Administration.StandardUpdate", "标准方法更新", "Pages.Administration", 2);
        permission("Pages.AbilityManagement", "能力表管理", "Pages", 1);
        permission("Pages.AbilityManagement.Ability", "能力维护", "Pages.AbilityManagement", 2);
        permission("Pages.AbilityManagement.Ability.Create", "创建能力", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.Ability.Edit", "编辑能力", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.Ability.PublicEdit", "公开能力编辑", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.Ability.Delete", "删除能力", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.Ability.DeleteAll", "批量删除能力", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.Ability.ImportExcel", "导入能力Excel", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.Ability.History", "能力历史", "Pages.AbilityManagement.Ability", 3);
        permission("Pages.AbilityManagement.EditDesc", "编辑能力说明", "Pages.AbilityManagement", 2);
        permission("Pages.AbilityManagement.AbilitySetting", "能力表设置", "Pages.AbilityManagement", 2);
        permission("Pages.AbilityManagement.Sample", "样品管理", "Pages.AbilityManagement", 2);
        permission("Pages.AbilityQuery", "能力表查询", "Pages", 1);
        permission("Pages.Log", "日志", "Pages", 1);
        permission("Pages.Log.AbilityHistory", "能力历史", "Pages.Log", 2);
        permission("Pages.Administration.AuditLogs", "审计日志", "Pages.Administration", 2);
    }

    private void permission(String name, String displayName, String parentName, int level) {
        PermissionItem item = new PermissionItem();
        item.name = name;
        item.displayName = displayName;
        item.parentName = parentName;
        item.level = level;
        permissions.add(item);
    }

    private SampleType type(String name, long orgId, String orgName) {
        SampleType type = new SampleType();
        type.id = UUID.randomUUID();
        type.displayName = name;
        type.orgId = orgId;
        type.orgName = orgName;
        return type;
    }

    private Sample sample(String name, String engName, String alias, SampleType type) {
        Sample sample = new Sample();
        sample.id = UUID.randomUUID();
        sample.displayName = name;
        sample.engName = engName;
        sample.alias = alias;
        sample.typeId = type.id;
        sample.typeName = type.displayName;
        return sample;
    }

    private Ability ability(OrganizationUnit org, SampleType type, String sampleName, String testItem, String method,
                            String standardNo, String cycle, String mass, String size, String limit, String price, String remark) {
        Ability ability = new Ability();
        ability.id = UUID.randomUUID();
        ability.orgId = org.id;
        ability.orgName = org.displayName;
        ability.typeId = type.id;
        ability.typeName = type.displayName;
        ability.samplingName = sampleName;
        ability.productCode = "PC-" + sampleName;
        ability.testItem = testItem;
        ability.testItemRemark = "脱敏项目说明";
        ability.methodName = method;
        ability.methodRemark = "脱敏方法说明";
        ability.methodEngName = method;
        ability.standardNo = standardNo;
        ability.gbNo = standardNo.startsWith("GB") ? standardNo : "";
        ability.gbRemark = "";
        ability.isoNo = standardNo.startsWith("ISO") ? standardNo : "";
        ability.isoRemark = "";
        ability.gbtNo = "";
        ability.gbtRemark = "";
        ability.astmNo = "";
        ability.astmRemark = "";
        ability.industryStandardNo = "";
        ability.industryStandardRemark = "";
        ability.otherNo = "";
        ability.otherRemark = "";
        ability.cycleWorkingDay = cycle;
        ability.testTime = cycle;
        ability.testTimeRemark = "常规周期";
        ability.massRequired = mass;
        ability.massRequiredRemark = "最少样品量";
        ability.sizeRequired = size;
        ability.sizeRequiredRemark = "按样品状态调整";
        ability.detectionLimit = limit;
        ability.price = price;
        ability.priceRemark = "按合同报价";
        ability.remark = remark;
        ability.standardNoSgs = "SGS-" + standardNo;
        ability.standardNoSop = "SOP-" + standardNo;
        ability.standardNoOthers = "";
        ability.standardNoDz = "";
        return ability;
    }

    private LabAbility labAbility(Laboratory lab, boolean cnas, boolean cma, boolean ability) {
        LabAbility labAbility = new LabAbility();
        labAbility.labId = lab.id;
        labAbility.code = lab.code;
        labAbility.hasCnas = cnas;
        labAbility.hasCma = cma;
        labAbility.isAbility = ability;
        return labAbility;
    }

    private SubcontractAbility subcontract(String labName, String contact, String category, String cmaOrCnas,
                                           String gist, String appraiser, String result) {
        SubcontractAbility ability = new SubcontractAbility();
        ability.id = UUID.randomUUID();
        ability.labName = labName;
        ability.contactDetails = contact;
        ability.testCategory = category;
        ability.cmaOrCnas = cmaOrCnas;
        ability.gist = gist;
        ability.appraiser = appraiser;
        ability.evaluationResult = result;
        return ability;
    }

    private boolean contains(String source, String value) {
        return value == null || value.isBlank() || safe(source).toLowerCase().contains(value.toLowerCase());
    }

    private boolean sameAbility(Ability left, Ability right) {
        return equalsText(left.orgName, right.orgName)
                && equalsText(left.typeName, right.typeName)
                && equalsText(left.samplingName, right.samplingName)
                && equalsText(left.testItem, right.testItem)
                && equalsText(left.standardNo, right.standardNo)
                && equalsText(left.methodName, right.methodName);
    }

    public Optional<SubcontractAbility> findDuplicateSubcontractAbility(SubcontractAbility input) {
        return subcontractAbilities.values().stream()
                .filter(item -> equalsText(item.labName, input.labName)
                        && equalsText(item.testCategory, input.testCategory)
                        && equalsText(item.gist, input.gist))
                .findFirst();
    }

    private void requireUniqueSampleTypeName(UUID id, String displayName) {
        boolean exists = sampleTypes.values().stream()
                .anyMatch(item -> !Objects.equals(item.id, id) && equalsText(item.displayName, displayName));
        if (exists) {
            throw new IllegalArgumentException(displayName + "已存在");
        }
    }

    private void requireUniqueSampleName(UUID id, String displayName) {
        boolean exists = samples.values().stream()
                .anyMatch(item -> !Objects.equals(item.id, id) && equalsText(item.displayName, displayName));
        if (exists) {
            throw new IllegalArgumentException(displayName + "已存在");
        }
    }

    private void requireUniqueLabCode(UUID id, String code) {
        boolean exists = labs.values().stream()
                .anyMatch(item -> !Objects.equals(item.id, id) && equalsText(item.code, code));
        if (exists) {
            throw new IllegalArgumentException(code + "已存在");
        }
    }

    private boolean equalsText(String left, String right) {
        return safe(left).trim().equalsIgnoreCase(safe(right).trim());
    }

    private String camelCase(String value) {
        String safeValue = safe(value);
        return safeValue.isBlank()
                ? safeValue
                : Character.toLowerCase(safeValue.charAt(0)) + safeValue.substring(1);
    }

    private String stripQuotes(String value) {
        return safe(value).replace("\"", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String randomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalizeProfilePicture(UserItem user, String fileToken) {
        String token = safe(fileToken);
        if (token.startsWith("data:image") && token.contains(",")) {
            return token;
        }
        if (token.length() > 80 && token.matches("[A-Za-z0-9+/=\\r\\n]+")) {
            return token.replaceAll("\\s+", "");
        }
        String label = safe(user.name).isBlank() ? safe(user.userName) : safe(user.name);
        if (label.isBlank()) {
            label = "U";
        }
        label = label.substring(0, Math.min(label.length(), 2));
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128\" height=\"128\">"
                + "<rect width=\"128\" height=\"128\" rx=\"24\" fill=\"#1f6feb\"/>"
                + "<text x=\"64\" y=\"76\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"44\" fill=\"white\">"
                + label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</text></svg>";
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Optional<UUID> parseUuid(String id) {
        try {
            return id == null || id.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Set<UUID> defaultFavoriteAbilityIds(long userId) {
        return defaultFavoriteAbilityIdsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
    }

    private Set<UUID> defaultFavoriteAbilityIdsForRead(long userId) {
        return defaultFavoriteAbilityIdsByUser.getOrDefault(userId, Set.of());
    }

    /** Plain JSON shape for the local persisted replica state. */
    public record TenantBinary(String id, String fileType, byte[] content) {
    }

    public static class StoreSnapshot {
        public List<Ability> abilities = new ArrayList<>();
        public List<Laboratory> labs = new ArrayList<>();
        public List<SampleType> sampleTypes = new ArrayList<>();
        public List<Sample> samples = new ArrayList<>();
        public List<OrgAbilitySetting> orgSettings = new ArrayList<>();
        public List<SubcontractAbility> subcontractAbilities = new ArrayList<>();
        public List<FavoriteGroup> favorites = new ArrayList<>();
        public List<UUID> defaultFavoriteAbilityIds = new ArrayList<>();
        public Map<Long, List<UUID>> defaultFavoriteAbilityIdsByUser = new LinkedHashMap<>();
        public List<RoleItem> roles = new ArrayList<>();
        public List<UserItem> users = new ArrayList<>();
        public Map<Long, String> userPasswords = new LinkedHashMap<>();
        public Map<String, String> profilePictures = new LinkedHashMap<>();
        public Map<Long, List<String>> userSpecificPermissions = new LinkedHashMap<>();
        public List<UserDelegation> userDelegations = new ArrayList<>();
        public List<UserLoginAttemptItem> userLoginAttempts = new ArrayList<>();
        public List<FriendItem> friendships = new ArrayList<>();
        public List<ChatMessageItem> chatMessages = new ArrayList<>();
        public List<FeatureItem> features = new ArrayList<>();
        public List<EditionItem> editions = new ArrayList<>();
        public List<TenantItem> tenants = new ArrayList<>();
        public List<SubscriptionPaymentItem> subscriptionPayments = new ArrayList<>();
        public List<InvoiceItem> invoices = new ArrayList<>();
        public List<LanguageItem> languages = new ArrayList<>();
        public List<LanguageTextItem> languageTexts = new ArrayList<>();
        public List<NotificationItem> notifications = new ArrayList<>();
        public List<NotificationSettings> notificationSettings = new ArrayList<>();
        public List<CacheItem> caches = new ArrayList<>();
        public List<DynamicParameterItem> dynamicParameters = new ArrayList<>();
        public List<DynamicParameterValueItem> dynamicParameterValues = new ArrayList<>();
        public List<EntityDynamicParameterItem> entityDynamicParameters = new ArrayList<>();
        public List<EntityDynamicParameterValueItem> entityDynamicParameterValues = new ArrayList<>();
        public List<WebhookSubscriptionItem> webhookSubscriptions = new ArrayList<>();
        public List<WebhookEventItem> webhookEvents = new ArrayList<>();
        public List<WebhookSendAttemptItem> webhookSendAttempts = new ArrayList<>();
        public List<ThemeSettingsItem> uiThemes = new ArrayList<>();
        public List<DashboardCustomizationItem> dashboardCustomizations = new ArrayList<>();
        public String activeUiTheme = "default";
        public boolean recurringPaymentsEnabled = true;
        public InstallSettingsItem installSettings = InstallSettingsItem.defaults();
        public SystemSettingsItem.HostSettings hostSettings = SystemSettingsItem.defaultHostSettings();
        public SystemSettingsItem.TenantSettings tenantSettings = SystemSettingsItem.defaultTenantSettings();
        public Map<Integer, SystemSettingsItem.TenantSettings> tenantSettingsByTenant = new LinkedHashMap<>();
        public SystemSettingsItem.AbilitySettings abilitySettings = SystemSettingsItem.defaultAbilitySettings();
        public List<PermissionItem> permissions = new ArrayList<>();
        public List<OrganizationUnit> orgUnits = new ArrayList<>();
        public List<AbilityHistoryItem> history = new ArrayList<>();
        public List<AuditLog> auditLogs = new ArrayList<>();
        public List<EntityChangeItem> entityChanges = new ArrayList<>();
        public List<EntityPropertyChangeItem> entityPropertyChanges = new ArrayList<>();
    }
}
