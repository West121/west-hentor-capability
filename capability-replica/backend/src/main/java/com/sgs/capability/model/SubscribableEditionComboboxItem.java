package com.sgs.capability.model;

/** Combobox item copied from SubscribableEditionComboboxItemDto. */
public class SubscribableEditionComboboxItem {
    public String value;
    public String displayText;
    public Boolean isFree;

    public SubscribableEditionComboboxItem(String value, String displayText, Boolean isFree) {
        this.value = value;
        this.displayText = displayText;
        this.isFree = isFree;
    }
}
