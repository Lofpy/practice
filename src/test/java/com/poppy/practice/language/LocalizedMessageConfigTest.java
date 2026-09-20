package com.poppy.practice.language;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.config.MessageConfig;
import com.poppy.practice.cosmetic.PreferencesService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LocalizedMessageConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void configuredMessagesChooseTheRecipientsLocaleAndRetainPlaceholders() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.prefix", "&cAscendingMC &7");
        config.set("messages.joined-queue", "Joined {kit}.");
        config.set("messages-ja.joined-queue", "{kit} に参加。");
        PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
        Field field = JavaPlugin.class.getDeclaredField("newConfig");
        field.setAccessible(true);
        field.set(plugin, config);
        PreferencesService preferences = new PreferencesService(temporary.getRoot(), Logger.getAnonymousLogger());
        MessageConfig messages = new MessageConfig(plugin);
        messages.setLanguageService(new LanguageService(preferences));
        Player japanese = mock(Player.class);
        Player english = mock(Player.class);
        when(japanese.getUniqueId()).thenReturn(UUID.randomUUID());
        when(english.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(preferences.setLanguage(english.getUniqueId(), PlayerLanguage.ENGLISH));
        messages.send(japanese, "joined-queue", "kit", "Combo");
        messages.send(english, "joined-queue", "kit", "Combo");
        verify(japanese).sendMessage("\u00a7cAscendingMC \u00a77Combo に参加。");
        verify(english).sendMessage("\u00a7cAscendingMC \u00a77Joined Combo.");
        messages.send(japanese, "no-arena");
        verify(japanese).sendMessage(contains("使用できるアリーナがありません"));
    }
}
