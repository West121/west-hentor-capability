package com.sgs.capability.model;

import java.util.ArrayList;
import java.util.List;

/** Dashboard page containing a saved widget layout. */
public class DashboardPageItem {
    public String id;
    public String name;
    public List<DashboardWidgetItem> widgets = new ArrayList<>();
}
