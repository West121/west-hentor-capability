package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.ComboboxItem;
import com.sgs.capability.model.NameValueItem;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mirrors TimingAppService timezone lookup endpoints. */
@RestController
@RequestMapping("/api/services/app/Timing")
public class TimingController {
    @GetMapping("/GetTimezones")
    public AbpResponse<ListResult<NameValueItem>> getTimezones(
            @RequestParam(name = "DefaultTimezoneScope", required = false) Integer defaultTimezoneScope,
            @RequestParam(name = "defaultTimezoneScope", required = false) Integer localDefaultTimezoneScope) {
        return AbpResponse.ok(new ListResult<>(timezoneItems()));
    }

    @PostMapping("/GetTimezones")
    public AbpResponse<ListResult<NameValueItem>> postTimezones(@RequestBody(required = false) TimezoneInput input) {
        return getTimezones(input == null ? null : input.defaultTimezoneScope, null);
    }

    @GetMapping("/GetTimezoneComboboxItems")
    public AbpResponse<List<ComboboxItem>> getTimezoneComboboxItems(
            @RequestParam(name = "SelectedTimezoneId", required = false) String selectedTimezoneId,
            @RequestParam(name = "selectedTimezoneId", required = false) String localSelectedTimezoneId,
            @RequestParam(name = "DefaultTimezoneScope", required = false) Integer defaultTimezoneScope,
            @RequestParam(name = "defaultTimezoneScope", required = false) Integer localDefaultTimezoneScope) {
        // Original Angular proxies use PascalCase query names; local camelCase is kept for compatibility.
        return AbpResponse.ok(comboboxItems(firstText(selectedTimezoneId, localSelectedTimezoneId)));
    }

    @PostMapping("/GetTimezoneComboboxItems")
    public AbpResponse<List<ComboboxItem>> postTimezoneComboboxItems(@RequestBody(required = false) TimezoneInput input) {
        return getTimezoneComboboxItems(input == null ? null : input.selectedTimezoneId, null,
                input == null ? null : input.defaultTimezoneScope, null);
    }

    private List<NameValueItem> timezoneItems() {
        List<NameValueItem> items = new ArrayList<>();
        items.add(nameValue("Default [" + defaultWindowsTimezoneId() + "]", ""));
        items.add(nameValue("(UTC) Coordinated Universal Time", "UTC"));
        items.add(nameValue("(UTC+08:00) Beijing, Chongqing, Hong Kong, Urumqi", "China Standard Time"));
        items.add(nameValue("(UTC+08:00) Kuala Lumpur, Singapore", "Singapore Standard Time"));
        items.add(nameValue("(UTC+09:00) Osaka, Sapporo, Tokyo", "Tokyo Standard Time"));
        items.add(nameValue("(UTC+00:00) Dublin, Edinburgh, Lisbon, London", "GMT Standard Time"));
        items.add(nameValue("(UTC+01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna", "W. Europe Standard Time"));
        items.add(nameValue("(UTC-05:00) Eastern Time (US & Canada)", "Eastern Standard Time"));
        items.add(nameValue("(UTC-06:00) Central Time (US & Canada)", "Central Standard Time"));
        items.add(nameValue("(UTC-08:00) Pacific Time (US & Canada)", "Pacific Standard Time"));
        return items;
    }

    private List<ComboboxItem> comboboxItems(String selectedTimezoneId) {
        return timezoneItems().stream()
                .map(item -> new ComboboxItem(item.value, item.name, equalsText(item.value, selectedTimezoneId)))
                .toList();
    }

    private NameValueItem nameValue(String name, String value) {
        NameValueItem item = new NameValueItem();
        item.name = name;
        item.value = value;
        return item;
    }

    private String defaultWindowsTimezoneId() {
        String zone = ZoneId.systemDefault().getId().toLowerCase(Locale.ROOT);
        if (zone.contains("shanghai") || zone.contains("chongqing") || zone.contains("hong_kong")) {
            return "China Standard Time";
        }
        if (zone.contains("tokyo")) {
            return "Tokyo Standard Time";
        }
        if (zone.contains("london")) {
            return "GMT Standard Time";
        }
        if (zone.contains("new_york")) {
            return "Eastern Standard Time";
        }
        if (zone.contains("los_angeles")) {
            return "Pacific Standard Time";
        }
        return "UTC";
    }

    private boolean equalsText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private String firstText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    public static class TimezoneInput {
        public Integer defaultTimezoneScope;
        public String selectedTimezoneId;
    }
}
