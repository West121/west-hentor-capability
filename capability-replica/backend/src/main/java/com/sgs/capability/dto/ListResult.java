package com.sgs.capability.dto;

import java.util.List;

/** List result shape used by many ABP endpoints. */
public class ListResult<T> {
    public List<T> items;

    public ListResult(List<T> items) {
        this.items = items;
    }
}
