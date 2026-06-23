package com.sgs.capability.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Search input for ability list and query pages. */
public class FindAbilityRequest {
    public String filter;
    public String typeName;
    public String samplingName;
    public String testItem;
    public String standardNo;
    public String methodName;
    public String methodEngName;
    public Long orgId;
    public String sorting;
    public int skipCount = 0;
    public int maxResultCount = 10;
    public List<DynamicFilter> filterItems = new ArrayList<>();

    public String validateOriginalPaging() {
        if (maxResultCount < 1 || maxResultCount > 1000 || skipCount < 0) {
            // 原 PagedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    /** Original ABP clients send `filter` as a dynamic filter array. */
    @JsonSetter("filter")
    public void setFilter(Object value) {
        if (value instanceof String text) {
            filter = text;
            return;
        }
        if (value instanceof List<?> items) {
            filterItems.clear();
            items.stream().map(this::dynamicFilter).filter(item -> item != null).forEach(filterItems::add);
            return;
        }
        filter = value == null ? null : String.valueOf(value);
    }

    private DynamicFilter dynamicFilter(Object value) {
        if (value instanceof DynamicFilter filter) {
            return filter;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        DynamicFilter filter = new DynamicFilter();
        filter.field = stringValue(map.get("field"));
        filter.name = stringValue(map.get("name"));
        filter.value = stringValue(map.get("value"));
        return filter;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
