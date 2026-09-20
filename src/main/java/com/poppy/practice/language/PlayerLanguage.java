package com.poppy.practice.language;

/** Stable stored locale codes; gameplay identifiers and scoreboards are not translated. */
public enum PlayerLanguage {
    JAPANESE("ja", "日本語"), ENGLISH("en", "English");

    private final String code;
    private final String displayName;

    PlayerLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public PlayerLanguage next() { return this == JAPANESE ? ENGLISH : JAPANESE; }
    public String choose(String japanese, String english) { return this == ENGLISH ? english : japanese; }

    public static PlayerLanguage fromCode(String code) {
        for (PlayerLanguage language : values()) {
            if (language.code.equals(code)) return language;
        }
        throw new IllegalArgumentException("Unsupported locale: " + code);
    }
}
