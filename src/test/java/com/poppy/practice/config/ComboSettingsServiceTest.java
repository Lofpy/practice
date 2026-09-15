package com.poppy.practice.config;

import com.poppy.practice.combat.ComboCombatService;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboSettingsServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private YamlConfiguration yaml;
    private ComboCombatService combat;
    private ComboSettingsService settings;
    private File file;
    private final Logger logger = mock(Logger.class);

    @Before
    public void setup() throws Exception {
        yaml = new YamlConfiguration();
        yaml.set("combo.knockback.horizontal", 0.30D);
        yaml.set("combo.knockback.vertical", 0.10D);
        yaml.set("combo.no-damage-ticks", 2);
        yaml.set("bot.settings.combat.attack-range", 2.8D);
        yaml.set("custom.message", "日本語設定");
        yaml.set("knockback.horizontal", 0.42D);
        file = temporary.newFile("config.yml");
        yaml.save(file);
        combat = new ComboCombatService(ComboConfig.load(yaml));
        settings = new ComboSettingsService(combat, () -> yaml, file, logger);
    }

    @Test
    public void savesAndPublishesOnlyTheRequestedComboProperty() throws Exception {
        settings.set("horizontal", "0.25");

        YamlConfiguration saved = readFile();
        assertEquals(0.25D, saved.getDouble("combo.knockback.horizontal"), 0.0D);
        assertEquals(0.25D, yaml.getDouble("combo.knockback.horizontal"), 0.0D);
        assertEquals(0.25D, settings.getSettings().get("horizontal"));
        assertEquals(0.10D, saved.getDouble("combo.knockback.vertical"), 0.0D);
        assertEquals(2.8D, saved.getDouble("bot.settings.combat.attack-range"), 0.0D);
        assertEquals("日本語設定", saved.getString("custom.message"));
        assertEquals(0.42D, saved.getDouble("knockback.horizontal"), 0.0D);
        assertEquals(1, file.getParentFile().listFiles().length);
    }

    @Test
    public void storesNativeBooleanIntegerAndProjectileTypes() throws Exception {
        settings.set("stop-sprint", "false");
        settings.set("no-damage-ticks", "5");
        settings.set("projectiles.pearl.horizontal", "0.35");

        YamlConfiguration saved = readFile();
        assertEquals(Boolean.FALSE, saved.get("combo.knockback.stop-sprint"));
        assertEquals(Integer.valueOf(5), saved.get("combo.no-damage-ticks"));
        assertEquals(0.35D, saved.getDouble("combo.knockback.projectiles.pearl.horizontal"), 0.0D);
        assertEquals(10, combat.getConfiguration().getMaximumNoDamageTicks());
    }

    @Test
    public void fallHeightIsSavedAndImmediatelyUpdatedForTrackedParticipants() throws Exception {
        Player player = participant();
        combat.apply(player);
        settings.set("fall-height", "1.25");

        assertEquals(1.25D, combat.getFallHeight(player.getUniqueId()), 0.0D);
        assertEquals(1.25D, readFile().getDouble("combo.knockback.fall-height"), 0.0D);
        assertEquals(0.30D, player.getKnockbackProfile().getHorizontal(), 0.0D);
        assertEquals(4, player.getMaximumNoDamageTicks());
        settings.set("fall-height", "0");
        assertEquals(0.0D, combat.getFallHeight(player.getUniqueId()), 0.0D);
        assertEquals(0.0D, readFile().getDouble("combo.knockback.fall-height"), 0.0D);
    }

    @Test
    public void failedFallHeightSaveRestoresPreviousActiveHeight() throws Exception {
        Player player = participant();
        combat.apply(player);
        File blockedTarget = temporary.newFolder("blocked-fall-height");
        assertTrue(new File(blockedTarget, "keep.txt").createNewFile());
        ComboSettingsService failing = new ComboSettingsService(combat, () -> yaml, blockedTarget, logger);
        try {
            failing.set("fall-height", "0");
            fail("Expected failed save");
        } catch (IOException expected) {
            assertEquals(3.0D, combat.getFallHeight(player.getUniqueId()), 0.0D);
            assertEquals(3.0D, combat.getConfiguration().getFallHeight(), 0.0D);
        }
        assertFalse(yaml.contains("combo.knockback.fall-height"));
    }

    @Test
    public void rejectsMalformedNonFiniteAndOutOfRangeValuesWithoutSaving() throws Exception {
        String[][] invalid = {
                {"horizontal", "NaN"}, {"horizontal", "Infinity"}, {"horizontal", "-0.1"},
                {"horizontal", "4.1"}, {"horizontal", "abc"}, {"friction-horizontal", "0"},
                {"no-damage-ticks", "0.5"}, {"no-damage-ticks", "101"},
                {"stop-sprint", "yes"}, {"horizontal", null}
        };
        for (String[] pair : invalid) {
            rejectUnchanged(pair[0], pair[1]);
        }
    }

    @Test
    public void rejectsArbitraryPathsAndInvalidVerticalBounds() throws Exception {
        rejectUnchanged("../knockback.horizontal", "1");
        rejectUnchanged("knockback.horizontal", "1");
        rejectUnchanged("bot.settings.combat.attack-range", "4");
        rejectUnchanged("vertical-min", "0.16");
        rejectUnchanged("vertical-max", "-1.01");
    }

    @Test
    public void saveFailureKeepsMemoryActiveSettingsAndOriginalTargetUnchanged() throws Exception {
        File blockedTarget = temporary.newFolder("blocked-target");
        File marker = new File(blockedTarget, "keep.txt");
        assertTrue(marker.createNewFile());
        ComboSettingsService failing = new ComboSettingsService(combat, () -> yaml, blockedTarget, logger);
        String before = yaml.saveToString();
        ComboConfig active = combat.getConfiguration();

        try {
            failing.set("horizontal", "0.2");
            fail("Expected failed replacement of a directory");
        } catch (IOException expected) {
            assertTrue(blockedTarget.isDirectory());
        }

        assertEquals(before, yaml.saveToString());
        assertSame(active, combat.getConfiguration());
        assertTrue(marker.exists());
        assertEquals(2, temporary.getRoot().listFiles().length);
    }

    @Test
    public void existingAndFutureParticipantsReceiveChangesImmediately() throws Exception {
        Player existing = participant();
        combat.apply(existing);
        settings.set("horizontal", "0.22");
        settings.set("no-damage-ticks", "3");
        Player future = participant();
        combat.apply(future);

        ArgumentCaptor<KnockbackProfile> existingProfile = ArgumentCaptor.forClass(KnockbackProfile.class);
        ArgumentCaptor<KnockbackProfile> futureProfile = ArgumentCaptor.forClass(KnockbackProfile.class);
        verify(existing, times(3)).setKnockbackProfile(existingProfile.capture());
        verify(future).setKnockbackProfile(futureProfile.capture());
        assertEquals(0.30D, existingProfile.getAllValues().get(0).getHorizontal(), 0.0D);
        assertEquals(0.22D, existingProfile.getValue().getHorizontal(), 0.0D);
        assertEquals(0.22D, futureProfile.getValue().getHorizontal(), 0.0D);
        verify(existing).setMaximumNoDamageTicks(4);
        verify(existing).setMaximumNoDamageTicks(6);
        verify(future).setMaximumNoDamageTicks(6);
    }

    @Test
    public void diskFailureRollsBackLiveParticipantsIncludingTheirHurtCounters() throws Exception {
        Player existing = participant();
        combat.apply(existing);
        existing.setNoDamageTicks(3);
        KnockbackProfile previousProfile = existing.getKnockbackProfile();
        ComboConfig previousConfig = combat.getConfiguration();
        File blockedTarget = temporary.newFolder("blocked-live-config");
        assertTrue(new File(blockedTarget, "keep.txt").createNewFile());
        ComboSettingsService failing = new ComboSettingsService(combat, () -> yaml, blockedTarget, logger);

        try {
            failing.set("no-damage-ticks", "5");
            fail("Expected failed save");
        } catch (IOException expected) {
            assertSame(previousProfile, existing.getKnockbackProfile());
        }

        assertEquals(4, existing.getMaximumNoDamageTicks());
        assertEquals(3, existing.getNoDamageTicks());
        assertSame(previousConfig, combat.getConfiguration());
        assertEquals(2, yaml.getInt("combo.no-damage-ticks"));
        assertEquals(2, readFile().getInt("combo.no-damage-ticks"));
        combat.restore(existing);
        assertNull(existing.getKnockbackProfile());
        assertEquals(20, existing.getMaximumNoDamageTicks());
    }

    @Test
    public void liveApplicationFailureDoesNotSaveOrChangeFutureSettings() throws Exception {
        Player existing = participant();
        combat.apply(existing);
        existing.setNoDamageTicks(3);
        KnockbackProfile previousProfile = existing.getKnockbackProfile();
        ComboConfig previousConfig = combat.getConfiguration();
        byte[] previousFile = Files.readAllBytes(file.toPath());
        doThrow(new IllegalStateException("native update failed"))
                .when(existing).setMaximumNoDamageTicks(6);

        try {
            settings.set("no-damage-ticks", "3");
            fail("Expected failed live application");
        } catch (IOException expected) {
            assertNotNull(expected.getCause());
        }

        assertSame(previousProfile, existing.getKnockbackProfile());
        assertEquals(4, existing.getMaximumNoDamageTicks());
        assertEquals(3, existing.getNoDamageTicks());
        assertSame(previousConfig, combat.getConfiguration());
        assertArrayEquals(previousFile, Files.readAllBytes(file.toPath()));
        assertEquals(2, yaml.getInt("combo.no-damage-ticks"));
    }

    @Test
    public void repeatedKnockbackChangesLeaveHurtCountersAndRestoreOriginalMatchSettings() throws Exception {
        Player existing = participant();
        combat.apply(existing);
        existing.setNoDamageTicks(3);

        settings.set("horizontal", "0.2");
        settings.set("vertical", "0.15");

        assertEquals(3, existing.getNoDamageTicks());
        assertEquals(0.2D, existing.getKnockbackProfile().getHorizontal(), 0.0D);
        assertEquals(0.15D, existing.getKnockbackProfile().getVertical(), 0.0D);
        verify(existing, times(1)).setMaximumNoDamageTicks(4);
        combat.restore(existing);
        assertNull(existing.getKnockbackProfile());
        assertEquals(20, existing.getMaximumNoDamageTicks());
        assertEquals(0, existing.getNoDamageTicks());
    }

    @Test
    public void readsTheEffectiveSnapshotAfterSuccessfulOrRejectedReloads() throws Exception {
        yaml.set("combo.knockback.horizontal", 0.4D);
        combat.reload(ComboConfig.load(yaml));
        assertEquals(0.4D, settings.getSettings().get("horizontal"));

        yaml.set("combo.knockback.horizontal", "broken");
        try {
            combat.reload(ComboConfig.load(yaml));
            fail("Invalid reload must fail");
        } catch (IllegalArgumentException expected) {
            assertEquals(0.4D, settings.getSettings().get("horizontal"));
        }
        settings.set("horizontal", "0.32");
        assertEquals(0.32D, settings.getSettings().get("horizontal"));
        assertEquals(0.32D, readFile().getDouble("combo.knockback.horizontal"), 0.0D);
    }

    @Test
    public void anotherInvalidComboPropertyPreventsPublishingPartialConfiguration() throws Exception {
        yaml.set("combo.knockback.vertical", "broken");
        rejectUnchanged("horizontal", "0.2");
    }

    @Test
    public void reloadReplacingYamlInstanceDoesNotLeaveTheCommandOnStaleConfig() throws Exception {
        AtomicReference<FileConfiguration> live = new AtomicReference<FileConfiguration>(yaml);
        ComboSettingsService reloading = new ComboSettingsService(combat, live::get, file);
        YamlConfiguration replacement = new YamlConfiguration();
        replacement.set("custom.new", "keep");
        replacement.set("combo.knockback.vertical", 0.12D);
        live.set(replacement);
        combat.reload(ComboConfig.load(replacement));

        reloading.set("horizontal", "0.24");

        assertEquals(0.30D, yaml.getDouble("combo.knockback.horizontal"), 0.0D);
        assertEquals(0.24D, replacement.getDouble("combo.knockback.horizontal"), 0.0D);
        assertEquals("keep", readFile().getString("custom.new"));
        assertEquals(0.12D, settings.getSettings().get("vertical"));
    }

    @Test
    public void newServiceReadsPersistedSettingsAfterRestart() throws Exception {
        settings.set("vertical", "0.14");
        settings.set("no-damage-ticks", "1");
        ComboCombatService restarted = new ComboCombatService(ComboConfig.load(readFile()));
        assertEquals(0.14D, restarted.getConfiguration().newKnockbackProfile().getVertical(), 0.0D);
        assertEquals(1, restarted.getConfiguration().getNoDamageTicks());
    }

    private void rejectUnchanged(String property, String value) throws Exception {
        byte[] diskBefore = Files.readAllBytes(file.toPath());
        String memoryBefore = yaml.saveToString();
        ComboConfig activeBefore = combat.getConfiguration();
        try {
            settings.set(property, value);
            fail("Expected invalid setting: " + property + " = " + value);
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
        assertArrayEquals(diskBefore, Files.readAllBytes(file.toPath()));
        assertEquals(memoryBefore, yaml.saveToString());
        assertSame(activeBefore, combat.getConfiguration());
    }

    private YamlConfiguration readFile() throws Exception {
        YamlConfiguration saved = new YamlConfiguration();
        saved.load(file);
        return saved;
    }

    private Player participant() {
        Player player = mock(Player.class);
        AtomicReference<KnockbackProfile> profile = new AtomicReference<KnockbackProfile>();
        AtomicInteger maximum = new AtomicInteger(20);
        AtomicInteger remaining = new AtomicInteger(0);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getKnockbackProfile()).thenAnswer(invocation -> profile.get());
        when(player.getMaximumNoDamageTicks()).thenAnswer(invocation -> maximum.get());
        when(player.getNoDamageTicks()).thenAnswer(invocation -> remaining.get());
        doAnswer(invocation -> {
            profile.set((KnockbackProfile) invocation.getArguments()[0]);
            return null;
        }).when(player).setKnockbackProfile(any(KnockbackProfile.class));
        doAnswer(invocation -> {
            maximum.set((Integer) invocation.getArguments()[0]);
            return null;
        }).when(player).setMaximumNoDamageTicks(anyInt());
        doAnswer(invocation -> {
            remaining.set((Integer) invocation.getArguments()[0]);
            return null;
        }).when(player).setNoDamageTicks(anyInt());
        return player;
    }
}
