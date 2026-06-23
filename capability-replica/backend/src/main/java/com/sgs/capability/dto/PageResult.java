package com.sgs.capability.dto;

import java.util.List;

/** Paged result shape compatible with ABP application services. */
public class PageResult<T> {
    public long totalCount;
    public List<T> items;

    public PageResult(long totalCount, List<T> items) {
        this.totalCount = totalCount;
        this.items = items;
    }
}
