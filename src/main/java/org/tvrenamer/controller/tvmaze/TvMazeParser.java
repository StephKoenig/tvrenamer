package org.tvrenamer.controller.tvmaze;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.tvrenamer.model.EpisodeInfo;

/** Pure JSON -> model parsing for TVMaze responses (top-level arrays). No I/O. */
public final class TvMazeParser {

    private static final Gson GSON = new Gson();

    private TvMazeParser() {}

    public record TvMazeResult(String id, String name, Integer year, List<String> aliases) {}

    public static List<TvMazeResult> parseSearchShows(String json) {
        List<TvMazeResult> out = new ArrayList<>();
        JsonArray arr = asArray(json);
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject wrapper = el.getAsJsonObject();
            if (!wrapper.has("show") || !wrapper.get("show").isJsonObject()) {
                continue;
            }
            JsonObject show = wrapper.getAsJsonObject("show");
            String id = intAsString(show, "id");
            String name = str(show, "name");
            if (id == null || name == null) {
                continue;
            }
            Integer year = null;
            String premiered = str(show, "premiered");
            if (premiered != null && premiered.length() >= 4) {
                try {
                    year = Integer.parseInt(premiered.substring(0, 4));
                } catch (NumberFormatException ignored) {
                    year = null;
                }
            }
            out.add(new TvMazeResult(id, name, year, Collections.emptyList()));
        }
        return out;
    }

    public static List<EpisodeInfo> parseEpisodes(String json) {
        List<EpisodeInfo> out = new ArrayList<>();
        JsonArray arr = asArray(json);
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject e = el.getAsJsonObject();
            String season = intAsString(e, "season");
            String number = intAsString(e, "number");
            if (season == null || number == null) {
                continue;
            }
            out.add(new EpisodeInfo.Builder()
                .episodeId(intAsString(e, "id"))
                .seasonNumber(season)
                .episodeNumber(number)
                .episodeName(str(e, "name"))
                .firstAired(str(e, "airdate"))
                .build());
        }
        return out;
    }

    private static JsonArray asArray(String json) {
        try {
            JsonElement root = GSON.fromJson(json, JsonElement.class);
            return (root != null && root.isJsonArray()) ? root.getAsJsonArray() : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /** Trimmed string for key, or null if absent/JSON-null/blank/non-primitive. */
    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) {
            return null;
        }
        String s = el.getAsString();
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** Numeric field rendered as a String (TVMaze ids/season/number are integers), or null. */
    private static String intAsString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) {
            return null;
        }
        try {
            return String.valueOf(el.getAsInt());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
