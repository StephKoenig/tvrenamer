package org.tvrenamer.model;

import java.util.Locale;

/** Which TheTVDB API TVRenamer uses to look up shows and episodes. */
public enum EpisodeDataProviderType {
    TVMAZE("TVMaze"),
    TVDB_V4("TheTVDB (v4)");

    private final String label;

    EpisodeDataProviderType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static EpisodeDataProviderType fromString(String value) {
        if (value == null) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.isEmpty()) {
            return null;
        }
        for (EpisodeDataProviderType t : values()) {
            if (t.name().equals(upper)
                || t.label.toUpperCase(Locale.ROOT).equals(upper)) {
                return t;
            }
        }
        return null;
    }
}
