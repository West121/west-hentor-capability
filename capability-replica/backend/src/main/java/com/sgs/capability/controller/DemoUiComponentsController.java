package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.DateToStringOutput;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.model.StringOutput;
import com.sgs.capability.security.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Mirrors DemoUiComponentsAppService echo and lookup helpers. */
@RestController
@RequestMapping("/api/services/app/DemoUiComponents")
@RequirePermission("Pages.DemoUiComponents")
public class DemoUiComponentsController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping("/SendAndGetDate")
    public AbpResponse<DateToStringOutput> sendAndGetDate(@RequestParam(required = false) String date) {
        return AbpResponse.ok(new DateToStringOutput(formatDate(date)));
    }

    @PostMapping("/SendAndGetDate")
    public AbpResponse<DateToStringOutput> postSendAndGetDate(@RequestParam(required = false) String date,
                                                              @RequestBody(required = false) DateInput input) {
        // Original generated proxy posts date values in the query string.
        return sendAndGetDate(date == null ? input == null ? null : input.date : date);
    }

    @GetMapping("/SendAndGetDateTime")
    public AbpResponse<DateToStringOutput> sendAndGetDateTime(@RequestParam(required = false) String date) {
        return AbpResponse.ok(new DateToStringOutput(formatDateTime(date)));
    }

    @PostMapping("/SendAndGetDateTime")
    public AbpResponse<DateToStringOutput> postSendAndGetDateTime(@RequestParam(required = false) String date,
                                                                  @RequestBody(required = false) DateInput input) {
        // Original generated proxy posts date-time values in the query string.
        return sendAndGetDateTime(date == null ? input == null ? null : input.date : date);
    }

    @GetMapping("/SendAndGetDateRange")
    public AbpResponse<DateToStringOutput> sendAndGetDateRange(@RequestParam(required = false) String startDate,
                                                               @RequestParam(required = false) String endDate) {
        return AbpResponse.ok(new DateToStringOutput(formatDate(startDate) + " - " + formatDate(endDate)));
    }

    @PostMapping("/SendAndGetDateRange")
    public AbpResponse<DateToStringOutput> postSendAndGetDateRange(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestBody(required = false) DateRangeInput input) {
        // Original generated proxy posts date-range values in the query string.
        String safeStartDate = startDate == null ? input == null ? null : input.startDate : startDate;
        String safeEndDate = endDate == null ? input == null ? null : input.endDate : endDate;
        return sendAndGetDateRange(safeStartDate, safeEndDate);
    }

    @GetMapping("/GetCountries")
    public AbpResponse<List<NameValueItem>> getCountries(@RequestParam(required = false) String searchTerm) {
        String filter = safe(searchTerm).toLowerCase(Locale.ROOT);
        List<NameValueItem> countries = countries().stream()
                .filter(item -> filter.isBlank() || item.name.toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        return AbpResponse.ok(countries);
    }

    @PostMapping("/GetCountries")
    public AbpResponse<List<NameValueItem>> postGetCountries(@RequestBody(required = false) SearchInput input) {
        return getCountries(input == null ? null : input.searchTerm);
    }

    @PostMapping("/SendAndGetSelectedCountries")
    public AbpResponse<List<NameValueItem>> sendAndGetSelectedCountries(@RequestBody(required = false) SelectedCountriesInput input) {
        return AbpResponse.ok(input == null || input.selectedCountries == null ? List.of() : input.selectedCountries);
    }

    @GetMapping("/SendAndGetValue")
    public AbpResponse<StringOutput> sendAndGetValue(@RequestParam(required = false) String input) {
        return AbpResponse.ok(new StringOutput(input));
    }

    @PostMapping("/SendAndGetValue")
    public AbpResponse<StringOutput> postSendAndGetValue(@RequestParam(required = false) String input,
                                                         @RequestBody(required = false) ValueInput body) {
        // Original generated proxy posts the text value in the query string.
        return sendAndGetValue(input == null ? body == null ? null : body.input : input);
    }

    private String formatDate(String value) {
        LocalDateTime date = parseDate(value);
        return date == null ? null : date.format(DATE_FORMAT);
    }

    private String formatDateTime(String value) {
        LocalDateTime date = parseDate(value);
        return date == null ? null : date.format(DATE_TIME_FORMAT);
    }

    private LocalDateTime parseDate(String value) {
        String safeValue = safe(value);
        if (safeValue.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(safeValue.replace("Z", ""));
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(safeValue + "T00:00:00");
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private List<NameValueItem> countries() {
        return List.of(
                nameValue("Turkey", "1"),
                nameValue("United States of America", "2"),
                nameValue("Russian Federation", "3"),
                nameValue("France", "4"),
                nameValue("Spain", "5"),
                nameValue("Germany", "6"),
                nameValue("Netherlands", "7"),
                nameValue("China", "8"),
                nameValue("Italy", "9"),
                nameValue("Switzerland", "10"),
                nameValue("South Africa", "11"),
                nameValue("Belgium", "12"),
                nameValue("Brazil", "13"),
                nameValue("India", "14")
        );
    }

    private NameValueItem nameValue(String name, String value) {
        NameValueItem item = new NameValueItem();
        item.name = name;
        item.value = value;
        return item;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class DateInput {
        public String date;
    }

    public static class DateRangeInput {
        public String startDate;
        public String endDate;
    }

    public static class SearchInput {
        public String searchTerm;
    }

    public static class SelectedCountriesInput {
        public List<NameValueItem> selectedCountries;
    }

    public static class ValueInput {
        public String input;
    }
}
