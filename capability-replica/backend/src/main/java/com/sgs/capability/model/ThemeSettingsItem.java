package com.sgs.capability.model;

/** UI theme settings copied from ThemeSettingsDto. */
public class ThemeSettingsItem {
    public String theme;
    public boolean isActive;
    public ThemeLayoutSettings layout = new ThemeLayoutSettings();
    public ThemeHeaderSettings header = new ThemeHeaderSettings();
    public ThemeSubHeaderSettings subHeader = new ThemeSubHeaderSettings();
    public ThemeMenuSettings menu = new ThemeMenuSettings();
    public ThemeFooterSettings footer = new ThemeFooterSettings();

    /** Layout type settings. */
    public static class ThemeLayoutSettings {
        public String layoutType;
    }

    /** Header behavior settings. */
    public static class ThemeHeaderSettings {
        public boolean desktopFixedHeader;
        public boolean mobileFixedHeader;
        public String headerSkin;
        public String minimizeDesktopHeaderType;
        public boolean headerMenuArrows;
    }

    /** Sub header behavior settings. */
    public static class ThemeSubHeaderSettings {
        public boolean fixedSubHeader;
        public String subheaderStyle;
    }

    /** Menu behavior settings. */
    public static class ThemeMenuSettings {
        public String position;
        public String asideSkin;
        public boolean fixedAside;
        public boolean allowAsideMinimizing;
        public boolean defaultMinimizedAside;
        public String submenuToggle;
        public boolean searchActive;
    }

    /** Footer behavior settings. */
    public static class ThemeFooterSettings {
        public boolean fixedFooter;
    }
}
