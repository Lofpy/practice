package com.poppy.practice.language;

import com.poppy.practice.cosmetic.PreferencesService;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LanguageServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void eachViewerHasTheirOwnLanguageAndColorsAreOnlyAppliedOnSend() {
        PreferencesService preferences = new PreferencesService(temporary.getRoot(), Logger.getAnonymousLogger());
        LanguageService languages = new LanguageService(preferences);
        Player japanese = mock(Player.class);
        Player english = mock(Player.class);
        when(japanese.getUniqueId()).thenReturn(UUID.randomUUID());
        when(english.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(preferences.setLanguage(english.getUniqueId(), PlayerLanguage.ENGLISH));
        assertEquals("&c日本語", languages.text(japanese, "&c日本語", "&cEnglish"));
        assertEquals("&cEnglish", languages.text(english, "&c日本語", "&cEnglish"));
        languages.send(english, "&c日本語", "&cEnglish");
        verify(english).sendMessage("\u00a7cEnglish");
        assertEquals(PlayerLanguage.JAPANESE, languages.language((Player) null));
    }

    @Test public void localeCodesAreStableAndUnknownCodesAreRejected() {
        assertEquals(PlayerLanguage.JAPANESE, PlayerLanguage.fromCode("ja"));
        assertEquals(PlayerLanguage.ENGLISH, PlayerLanguage.fromCode("en"));
        assertEquals(PlayerLanguage.ENGLISH, PlayerLanguage.JAPANESE.next());
        assertEquals(PlayerLanguage.JAPANESE, PlayerLanguage.ENGLISH.next());
        try { PlayerLanguage.fromCode("es"); fail("unknown language"); }
        catch (IllegalArgumentException expected) { }
    }
}
