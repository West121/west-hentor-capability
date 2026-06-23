package com.sgs.capability.model;

/** Generic ABP combobox item with optional selected state. */
public class ComboboxItem {
    public String value;
    public String displayText;
    public boolean isSelected;

    public ComboboxItem(String value, String displayText, boolean isSelected) {
        this.value = value;
        this.displayText = displayText;
        this.isSelected = isSelected;
    }
}
