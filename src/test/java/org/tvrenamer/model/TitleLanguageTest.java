package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tvrenamer.controller.UserPreferencesPersistence;

public class TitleLanguageTest {

    @AfterEach
    public void reset() {
        UserPreferences.getInstance().setTitleLanguage(TitleLanguage.ENGLISH);
    }

    @Test
    public void codeAndFromStringRoundTrip() {
        assertEquals("spa", TitleLanguage.SPANISH.code());
        assertEquals(TitleLanguage.SPANISH, TitleLanguage.fromString("Spanish"));
        assertEquals(TitleLanguage.SPANISH, TitleLanguage.fromString("spanish"));
        assertNull(TitleLanguage.fromString("nonsense"));
        assertEquals("eng", TitleLanguage.ENGLISH.code());
    }

    @Test
    public void defaultIsEnglish() {
        UserPreferences defaults = UserPreferences.fromParsedXml(
            new HashMap<>(), java.util.List.of(), new HashMap<>(), new HashMap<>());
        assertEquals(TitleLanguage.ENGLISH, defaults.getTitleLanguage());
    }

    @Test
    public void persistenceRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("prefs.xml");
        UserPreferences p = UserPreferences.getInstance();
        p.setTitleLanguage(TitleLanguage.JAPANESE);
        UserPreferencesPersistence.persist(p, file);
        UserPreferences read = UserPreferencesPersistence.retrieve(file);
        assertEquals(TitleLanguage.JAPANESE, read.getTitleLanguage());
    }
}
