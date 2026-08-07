package org.tvrenamer.model;

import java.util.Locale;

/** Language used for the show name and episode titles written into renamed files (v4 provider). */
public enum TitleLanguage {
    ENGLISH("English", "eng"),
    SPANISH("Spanish", "spa"),
    FRENCH("French", "fra"),
    GERMAN("German", "deu"),
    ITALIAN("Italian", "ita"),
    PORTUGUESE("Portuguese", "por"),
    DUTCH("Dutch", "nld"),
    RUSSIAN("Russian", "rus"),
    JAPANESE("Japanese", "jpn"),
    KOREAN("Korean", "kor"),
    CHINESE("Chinese", "zho"),
    ARABIC("Arabic", "ara"),
    SWEDISH("Swedish", "swe"),
    POLISH("Polish", "pol"),
    TURKISH("Turkish", "tur");

    private final String label;
    private final String code;

    TitleLanguage(String label, String code) {
        this.label = label;
        this.code = code;
    }

    /** ISO 639-2/T code sent to the TheTVDB v4 API (e.g. "eng"). */
    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return label;
    }

    public static TitleLanguage fromString(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        String upper = v.toUpperCase(Locale.ROOT);
        for (TitleLanguage t : values()) {
            if (t.name().equals(upper)
                || t.label.toUpperCase(Locale.ROOT).equals(upper)
                || t.code.equalsIgnoreCase(v)) {
                return t;
            }
        }
        return null;
    }
}
