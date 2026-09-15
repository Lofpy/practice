package com.poppy.practice.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ComboKbCommandTest {
    private ComboKbCommand.Settings settings;
    private CommandSender sender;
    private ComboKbCommand command;
    private Map<String, Object> values;

    @Before
    public void setUp() {
        settings = mock(ComboKbCommand.Settings.class);
        sender = mock(CommandSender.class);
        when(sender.hasPermission("practice.admin")).thenReturn(true);
        values = new LinkedHashMap<String, Object>();
        values.put("no-damage-ticks", 2);
        values.put("stop-sprint", true);
        values.put("friction-horizontal", 2.0D);
        values.put("horizontal", 0.3D);
        values.put("vertical", 0.1D);
        values.put("projectiles.pearl.horizontal", 0.4D);
        when(settings.getSettings()).thenReturn(values);
        command = new ComboKbCommand(settings);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingSettings() {
        new ComboKbCommand(null);
    }

    @Test
    public void deniesExecutionBeforeReadingOrChangingSettings() throws Exception {
        when(sender.hasPermission("practice.admin")).thenReturn(false);

        assertTrue(execute("set", "horizontal", "0.4"));

        assertTrue(messages().contains("permission"));
        verify(settings, never()).getSettings();
        verify(settings, never()).set(anyString(), anyString());
    }

    @Test
    public void noArgumentsShowsCoreSettingsAndUsageToConsoleSender() throws Exception {
        assertTrue(execute());

        String output = messages();
        assertTrue(output.contains("current and future PvP / Bot matches"));
        assertTrue(output.contains("horizontal: 0.3"));
        assertTrue(output.contains("no-damage-ticks: 2"));
        assertTrue(output.contains("stop-sprint: true"));
        assertFalse(output.contains("projectiles.pearl.horizontal:"));
        assertTrue(output.contains("/combokb view [all|property]"));
        assertTrue(output.contains("/combokb set <property> <value>"));
        verify(settings, never()).set(anyString(), anyString());
    }

    @Test
    public void viewAlsoShowsCoreSettings() {
        assertTrue(execute("VIEW"));

        assertTrue(messages().contains("vertical: 0.1"));
        assertFalse(messages().contains("projectiles.pearl.horizontal:"));
    }

    @Test
    public void viewAllIncludesProjectileSettings() {
        assertTrue(execute("VIEW", "ALL"));

        assertTrue(messages().contains("projectiles.pearl.horizontal: 0.4"));
    }

    @Test
    public void viewOnePropertyIsCaseInsensitiveAndDoesNotShowUnrelatedValues() {
        assertTrue(execute("view", "PROJECTILES.PEARL.HORIZONTAL"));

        assertEquals("projectiles.pearl.horizontal: 0.4", messages());
    }

    @Test
    public void unknownViewPropertyIsRejected() throws Exception {
        assertTrue(execute("view", "missing"));

        assertTrue(messages().contains("Unknown Combo property: missing"));
        verify(settings, never()).set(anyString(), anyString());
    }

    @Test
    public void unknownSetPropertyIsRejectedBeforePersistence() throws Exception {
        assertTrue(execute("set", "missing", "2"));

        assertTrue(messages().contains("Unknown Combo property: missing"));
        verify(settings, never()).set(anyString(), anyString());
    }

    @Test
    public void setPersistsAndExplainsImmediateApplicationIncludingCountdowns() throws Exception {
        doAnswer(invocation -> {
            values.put("horizontal", 0.42D);
            return null;
        }).when(settings).set("horizontal", "0.42");

        assertTrue(execute("SET", "HORIZONTAL", "0.42"));

        verify(settings).set("horizontal", "0.42");
        String output = messages();
        assertTrue(output.contains("Saved Combo horizontal = 0.42"));
        assertTrue(output.contains("Applied immediately to Combo PvP / Bot matches, including countdowns"));
        assertTrue(output.contains("Other kits are unchanged"));
        assertFalse(output.contains("next Combo"));
    }

    @Test
    public void numericValidationErrorDoesNotReportSuccess() throws Exception {
        doThrow(new IllegalArgumentException("horizontal must be finite and between 0 and 4."))
                .when(settings).set("horizontal", "NaN");

        assertTrue(execute("set", "horizontal", "NaN"));

        String output = messages();
        assertTrue(output.contains("Invalid Combo setting: horizontal must be finite"));
        assertFalse(output.contains("Saved"));
        assertEquals(0.3D, values.get("horizontal"));
    }

    @Test
    public void booleanValidationErrorIsShown() throws Exception {
        doThrow(new IllegalArgumentException("stop-sprint must be true or false."))
                .when(settings).set("stop-sprint", "yes");

        execute("set", "stop-sprint", "yes");

        assertTrue(messages().contains("stop-sprint must be true or false"));
        assertFalse(messages().contains("Saved"));
    }

    @Test
    public void failedSaveReportsUpdateFailureWithoutPromisingSuccessfulRollback() throws Exception {
        doThrow(new IOException("disk unavailable")).when(settings).set("vertical", "0.2");

        assertTrue(execute("set", "vertical", "0.2"));

        String output = messages();
        assertTrue(output.contains("Could not update Combo settings. Check the server log."));
        assertFalse(output.contains("Previous settings are unchanged"));
        assertFalse(output.contains("Saved"));
        assertFalse(output.contains("Applied immediately"));
    }

    @Test
    public void unexpectedApplyFailureIsReportedWithoutSuccessOrRollbackGuarantee() throws Exception {
        doThrow(new IllegalStateException("Live profile update failed"))
                .when(settings).set("horizontal", "0.4");

        assertTrue(execute("set", "horizontal", "0.4"));

        String output = messages();
        assertTrue(output.contains("Could not update Combo settings. Check the server log."));
        assertFalse(output.contains("Previous settings are unchanged"));
        assertFalse(output.contains("Saved"));
        assertFalse(output.contains("Applied immediately"));
    }

    @Test
    public void invalidArgumentCountsAndActionsOnlyShowUsage() throws Exception {
        for (String[] args : new String[][] {
                {"set"}, {"set", "horizontal"}, {"set", "horizontal", "0.4", "extra"},
                {"view", "horizontal", "extra"}, {"reset"}, {"horizontal", "0.4"}
        }) {
            assertTrue(execute(args));
        }

        assertTrue(messages().contains("Usage:"));
        verify(settings, never()).getSettings();
        verify(settings, never()).set(anyString(), anyString());
    }

    @Test
    public void completionRequiresPermissionAtEveryArgumentDepth() {
        when(sender.hasPermission("practice.admin")).thenReturn(false);

        assertTrue(complete("").isEmpty());
        assertTrue(complete("view", "").isEmpty());
        assertTrue(complete("set", "stop-sprint", "").isEmpty());
        verify(settings, never()).getSettings();
    }

    @Test
    public void completesActionsCaseInsensitively() {
        assertEquals(Arrays.asList("view", "set"), complete(""));
        assertEquals(Collections.singletonList("set"), complete("S"));
        assertTrue(complete("unknown").isEmpty());
    }

    @Test
    public void completesPropertiesAndAllOnlyForView() {
        assertTrue(complete("view", "").contains("all"));
        assertFalse(complete("set", "").contains("all"));
        assertEquals(Collections.singletonList("horizontal"), complete("SET", "HOR"));
        assertEquals(Collections.singletonList("projectiles.pearl.horizontal"),
                complete("view", "projectiles."));
        assertTrue(complete("invalid", "").isEmpty());
    }

    @Test
    public void completesBooleanChoicesAndCurrentNumericValues() {
        assertEquals(Arrays.asList("true", "false"), complete("set", "stop-sprint", ""));
        assertEquals(Collections.singletonList("false"), complete("set", "STOP-SPRINT", "F"));
        assertEquals(Collections.singletonList("0.3"), complete("set", "horizontal", ""));
        assertEquals(Collections.singletonList("2"), complete("set", "no-damage-ticks", ""));
        assertTrue(complete("set", "horizontal", "9").isEmpty());
        assertTrue(complete("set", "missing", "").isEmpty());
    }

    @Test
    public void completionDoesNotSuggestForInvalidArgumentCounts() {
        assertTrue(complete().isEmpty());
        assertTrue(complete("view", "all", "").isEmpty());
        assertTrue(complete("set", "horizontal", "0.3", "").isEmpty());
    }

    @Test
    public void propertyNormalizationDoesNotDependOnDefaultLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            execute("set", "FRICTION-HORIZONTAL", "3.0");
            verify(settings).set("friction-horizontal", "3.0");
            assertEquals(Collections.singletonList("friction-horizontal"),
                    complete("set", "FRICTION-H"));
        } finally {
            Locale.setDefault(original);
        }
    }

    private boolean execute(String... args) {
        return command.onCommand(sender, null, "combokb", args);
    }

    private List<String> complete(String... args) {
        return command.onTabComplete(sender, null, "combokb", args);
    }

    private String messages() {
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(messages.capture());
        return ChatColor.stripColor(String.join("\n", messages.getAllValues()));
    }
}
