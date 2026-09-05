package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the preference-change events fired by the Matching preference setters.
 *
 * Saving the Preferences dialog writes both matching maps every time, so firing
 * unconditionally makes every Save trigger a re-match pass even when nothing
 * changed. Writing them separately also fires an intermediate event while the
 * second map is still stale.
 */
public class MatchingPreferenceEventsTest {

    private final UserPreferences prefs = UserPreferences.getInstance();
    private PropertyChangeListener listener;

    private AtomicInteger countEvents() {
        AtomicInteger count = new AtomicInteger();
        listener = evt -> {
            if (UserPreference.SHOW_NAME_OVERRIDES.equals(evt.getNewValue())) {
                count.incrementAndGet();
            }
        };
        prefs.addPropertyChangeListener(listener);
        return count;
    }

    @AfterEach
    public void cleanUp() {
        if (listener != null) {
            prefs.removePropertyChangeListener(listener);
            listener = null;
        }
        prefs.setShowNameOverrides(new HashMap<>());
        prefs.setShowDisambiguationOverrides(new HashMap<>());
    }

    private static Map<String, String> mapOf(String k, String v) {
        Map<String, String> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    @Test
    @DisplayName("Re-setting show name overrides to an equal map fires no event")
    public void unchangedNameOverridesFireNothing() {
        prefs.setShowNameOverrides(mapOf("westmark", "westmark academy"));

        AtomicInteger events = countEvents();
        prefs.setShowNameOverrides(mapOf("westmark", "westmark academy"));

        assertEquals(0, events.get());
    }

    @Test
    @DisplayName("Re-setting disambiguations to an equal map fires no event")
    public void unchangedDisambiguationsFireNothing() {
        prefs.setShowDisambiguationOverrides(mapOf("the quiet ones", "12345"));

        AtomicInteger events = countEvents();
        prefs.setShowDisambiguationOverrides(mapOf("the quiet ones", "12345"));

        assertEquals(0, events.get());
    }

    @Test
    @DisplayName("Changing show name overrides fires exactly one event")
    public void changedNameOverridesFireOnce() {
        AtomicInteger events = countEvents();
        prefs.setShowNameOverrides(mapOf("westmark", "westmark academy"));

        assertEquals(1, events.get());
    }

    @Test
    @DisplayName("Setting both matching maps together fires exactly one event")
    public void settingBothMapsFiresOnce() {
        AtomicInteger events = countEvents();
        prefs.setMatchingOverrides(
            mapOf("westmark", "westmark academy"),
            mapOf("the quiet ones", "12345")
        );

        assertEquals(1, events.get());
    }

    @Test
    @DisplayName("Setting both matching maps with no changes fires no event")
    public void settingBothMapsUnchangedFiresNothing() {
        prefs.setMatchingOverrides(
            mapOf("westmark", "westmark academy"),
            mapOf("the quiet ones", "12345")
        );

        AtomicInteger events = countEvents();
        prefs.setMatchingOverrides(
            mapOf("westmark", "westmark academy"),
            mapOf("the quiet ones", "12345")
        );

        assertEquals(0, events.get());
    }
}
