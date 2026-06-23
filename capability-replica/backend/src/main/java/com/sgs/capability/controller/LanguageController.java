package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.ComboboxItem;
import com.sgs.capability.model.LanguageItem;
import com.sgs.capability.model.LanguageTextItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Mirrors LanguageAppService language and text maintenance routes. */
@RestController
@RequestMapping("/api/services/app/Language")
@RequirePermission("Pages.Administration.Languages")
public class LanguageController {
    private final AuthService auth;
    private final CapabilityStore store;

    public LanguageController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @GetMapping("/GetLanguages")
    public AbpResponse<GetLanguagesOutput> languages() {
        List<LanguageItem> languages = store.languages().stream()
                .sorted(Comparator.comparing(item -> safe(item.displayName)))
                .toList();
        String defaultLanguage = languages.stream()
                .filter(item -> item.isDefault)
                .map(item -> item.name)
                .findFirst()
                .orElse("zh-Hans");
        return AbpResponse.ok(new GetLanguagesOutput(languages, defaultLanguage));
    }

    @PostMapping("/GetLanguageForEdit")
    public AbpResponse<GetLanguageForEditOutput> languageForEdit(@RequestBody(required = false) IdRequest input) {
        LanguageItem language = input == null ? null : store.language(parseInt(input.id)).orElse(null);
        LanguageItem editLanguage = language == null ? new LanguageItem() : language;
        return AbpResponse.ok(new GetLanguageForEditOutput(editLanguage, languageNames(editLanguage.name), flagItems(editLanguage.icon)));
    }

    @GetMapping("/GetLanguageForEdit")
    public AbpResponse<GetLanguageForEditOutput> languageForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest request = new IdRequest();
        request.id = id;
        return languageForEdit(request);
    }

    @PostMapping("/CreateOrUpdateLanguage")
    public AbpResponse<Void> createOrUpdate(@RequestBody LanguageEditInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        // The replica treats tenant 1 as the local host/default context.
        if (context.tenantId() != null && context.tenantId() != 1) {
            return AbpResponse.failed("Tenants cannot create language.");
        }
        if (input == null || input.language == null) {
            // 原 CreateOrUpdateLanguageInput 要求外层 Language 对象必填。
            return AbpResponse.failed("Validation failed");
        }
        LanguageItem language = input.language;
        String validationError = validateLanguageInput(language);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (languageNameAlreadyExists(language)) {
            return AbpResponse.failed("This language already exists!");
        }
        normalizeLanguageForSave(language);
        store.saveLanguage(language);
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteLanguage")
    @RequirePermission("Pages.Administration.Languages.Delete")
    public AbpResponse<Void> deleteLanguage(@RequestBody IdRequest input) {
        store.deleteLanguage(parseInt(input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteLanguage")
    @RequirePermission("Pages.Administration.Languages.Delete")
    public AbpResponse<Void> deleteLanguageByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteLanguage(parseInt(id));
        return AbpResponse.ok(null);
    }

    @PostMapping("/SetDefaultLanguage")
    @RequirePermission("Pages.Administration.Languages.Edit")
    public AbpResponse<Void> setDefaultLanguage(@RequestBody SetDefaultLanguageInput input) {
        String languageName = input == null || input.languageName == null ? null : input.languageName;
        if (languageName == null && input != null) {
            languageName = input.name;
        }
        if (safe(languageName).isBlank() || safe(languageName).length() > 128) {
            // 原 SetDefaultLanguageInput.Name 必填且最多 128。
            return AbpResponse.failed("Validation failed");
        }
        store.setDefaultLanguage(languageName);
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetLanguageTexts")
    @RequirePermission("Pages.Administration.Languages.ChangeTexts")
    public AbpResponse<PageResult<LanguageTextItem>> languageTexts(@RequestBody(required = false) GetLanguageTextsInput input) {
        GetLanguageTextsInput safeInput = input == null ? new GetLanguageTextsInput() : input;
        String targetLanguageName = firstText(safeInput.targetLanguageName, safeInput.languageName);
        String filterText = firstText(safeInput.filterText, safeInput.filter);
        String validationError = validateLanguageTextsInput(safeInput.sourceName, safeInput.baseLanguageName,
                targetLanguageName, safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.languageTexts(safeInput.sourceName, targetLanguageName,
                normalizeTargetValueFilter(safeInput.targetValueFilter), filterText,
                safeInput.skipCount, safeInput.maxResultCount));
    }

    // Match the generated Angular proxy: GET with original localization query keys.
    @GetMapping("/GetLanguageTexts")
    @RequirePermission("Pages.Administration.Languages.ChangeTexts")
    public AbpResponse<PageResult<LanguageTextItem>> languageTextsByQuery(
            @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount,
            @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
            @RequestParam(name = "Sorting", required = false) String sorting,
            @RequestParam(name = "SourceName", required = false) String sourceName,
            @RequestParam(name = "BaseLanguageName", required = false) String baseLanguageName,
            @RequestParam(name = "TargetLanguageName", required = false) String targetLanguageName,
            @RequestParam(name = "TargetValueFilter", required = false) String targetValueFilter,
            @RequestParam(name = "FilterText", required = false) String filterText) {
        String validationError = validateLanguageTextsInput(sourceName, baseLanguageName, targetLanguageName,
                skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.languageTexts(sourceName, targetLanguageName, normalizeTargetValueFilter(targetValueFilter),
                filterText, skipCount, maxResultCount));
    }

    @PostMapping("/UpdateLanguageText")
    @RequirePermission("Pages.Administration.Languages.ChangeTexts")
    public AbpResponse<Void> updateLanguageText(@RequestBody UpdateLanguageTextInput input) {
        String validationError = validateLanguageTextInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        LanguageTextItem text = new LanguageTextItem();
        text.languageName = input.languageName;
        text.key = input.key;
        text.sourceName = input.sourceName;
        text.baseValue = input.baseValue;
        text.targetValue = input.targetValue == null ? input.value : input.targetValue;
        store.updateLanguageText(text);
        return AbpResponse.ok(null);
    }

    @PutMapping("/UpdateLanguageText")
    @RequirePermission("Pages.Administration.Languages.ChangeTexts")
    public AbpResponse<Void> putUpdateLanguageText(@RequestBody UpdateLanguageTextInput input) {
        return updateLanguageText(input);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean languageNameAlreadyExists(LanguageItem language) {
        return language != null && language.name != null && store.languages().stream()
                .anyMatch(item -> Objects.equals(item.name, language.name) && !Objects.equals(item.id, language.id));
    }

    private String validateLanguageInput(LanguageItem language) {
        if (language == null || safe(language.name).isBlank() || safe(language.name).length() > 128
                || safe(language.icon).length() > 128) {
            // 原 CreateOrUpdateLanguageInput.Language 必填，Name/Icon 长度最多 128。
            return "Validation failed";
        }
        return null;
    }

    private void normalizeLanguageForSave(LanguageItem language) {
        String cultureName = normalizeCultureName(language.name);
        boolean enabled = language.isEnabled == null ? !language.isDisabled : language.isEnabled;
        language.name = cultureName;
        language.displayName = languageEnglishName(cultureName);
        language.isDisabled = !enabled;
        language.isEnabled = enabled;
    }

    private String validateLanguageTextInput(UpdateLanguageTextInput input) {
        if (input == null || safe(input.languageName).isBlank() || safe(input.sourceName).isBlank()
                || safe(input.key).isBlank() || (input.value == null && input.targetValue == null)) {
            return "Validation failed";
        }
        if (safe(input.languageName).length() > 128 || safe(input.sourceName).length() > 128
                || safe(input.key).length() > 256) {
            // 原 UpdateLanguageTextInput 限制 LanguageName/SourceName/Key 长度。
            return "Validation failed";
        }
        return null;
    }

    private String validateLanguageTextsInput(String sourceName, String baseLanguageName, String targetLanguageName,
                                              int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 0) {
            // 原 GetLanguageTextsInput 分页字段只允许非负数。
            return "Validation failed";
        }
        if (safe(sourceName).isBlank() || safe(sourceName).length() > 128
                || safe(baseLanguageName).length() > 128
                || safe(targetLanguageName).isBlank()
                || safe(targetLanguageName).length() < 2
                || safe(targetLanguageName).length() > 128) {
            // 原 GetLanguageTextsInput 要求 SourceName 和 TargetLanguageName 并限制语言名长度。
            return "Validation failed";
        }
        return null;
    }

    private String normalizeTargetValueFilter(String value) {
        return safe(value).isBlank() ? "ALL" : value;
    }

    private String firstText(String first, String second) {
        return safe(first).isBlank() ? second : first;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private AuthContext current(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private List<ComboboxItem> languageNames(String selectedName) {
        List<ComboboxItem> items = new ArrayList<>();
        addLanguageName(items, "en", selectedName);
        addLanguageName(items, "zh-Hans", selectedName);
        return items;
    }

    private void addLanguageName(List<ComboboxItem> items, String tag, String selectedName) {
        items.add(new ComboboxItem(tag, languageDisplayText(tag), Objects.equals(tag, selectedName)));
    }

    private String languageDisplayText(String tag) {
        return languageEnglishName(tag) + " (" + tag + ")";
    }

    private String languageEnglishName(String tag) {
        return "zh-Hans".equals(tag)
                ? "Chinese (Simplified)"
                : Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH);
    }

    private String normalizeCultureName(String tag) {
        return "zh-Hans".equalsIgnoreCase(tag) ? "zh-Hans" : Locale.forLanguageTag(tag).toLanguageTag();
    }

    private List<ComboboxItem> flagItems(String selectedIcon) {
        String normalizedSelected = normalizeFlagIcon(selectedIcon);
        return flagClasses().stream()
                .map(flag -> new ComboboxItem(flag, flag.substring(flag.lastIndexOf(' ') + 1),
                        Objects.equals(flag, normalizedSelected)))
                .toList();
    }

    private List<String> flagClasses() {
        List<String> flags = new ArrayList<>();
        for (String country : Locale.getISOCountries()) {
            flags.add("famfamfam-flags " + country.toLowerCase(Locale.ROOT));
        }
        flags.add("famfamfam-flags scotland");
        flags.add("famfamfam-flags wales");
        return flags.stream().sorted().toList();
    }

    private String normalizeFlagIcon(String icon) {
        if (safe(icon).startsWith("famfamfam-flag-")) {
            return "famfamfam-flags " + icon.substring("famfamfam-flag-".length());
        }
        return icon;
    }

    public record GetLanguagesOutput(List<LanguageItem> languages, String defaultLanguageName) {
    }

    public record GetLanguageForEditOutput(LanguageItem language, List<ComboboxItem> languageNames,
                                           List<ComboboxItem> flags) {
    }

    public static class LanguageEditInput {
        public LanguageItem language;
        public Integer id;
        public String name;
        public String displayName;
        public String icon;
        public boolean isDisabled;

        public LanguageItem toLanguage() {
            LanguageItem item = new LanguageItem();
            item.id = id;
            item.name = name;
            item.displayName = displayName;
            item.icon = icon;
            item.isDisabled = isDisabled;
            return item;
        }
    }

    public static class SetDefaultLanguageInput {
        public String name;
        public String languageName;
    }

    public static class GetLanguageTextsInput {
        public String languageName;
        public String filter;
        public String sourceName;
        public String baseLanguageName;
        public String targetLanguageName;
        public String targetValueFilter;
        public String filterText;
        public int skipCount;
        public int maxResultCount = 10;
    }

    public static class UpdateLanguageTextInput {
        public String languageName;
        public String sourceName;
        public String key;
        public String baseValue;
        public String targetValue;
        public String value;
    }
}
