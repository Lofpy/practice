package com.poppy.practice.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ComboConfigSettingsTest {
    @Test
    public void namesAndEffectiveSettingsHaveTheSameCompleteStableOrder() {
        List<String> names = ComboConfig.getSettingNames();
        Map<String, Object> settings = defaults().getSettings();

        assertEquals(26, names.size());
        assertEquals(names.size(), new HashSet<String>(names).size());
        assertEquals(names, new ArrayList<String>(settings.keySet()));
        assertEquals("no-damage-ticks", names.get(0));
        assertEquals("stop-sprint", names.get(1));
        assertEquals("fall-height", names.get(2));
        assertEquals("fall-speed", names.get(3));
        assertEquals(Integer.valueOf(2), settings.get("no-damage-ticks"));
        assertEquals(Boolean.TRUE, settings.get("stop-sprint"));
        assertEquals(Double.valueOf(3.0D), settings.get("fall-height"));
        assertEquals(Double.valueOf(0.08D), settings.get("fall-speed"));
        assertEquals(Double.valueOf(0.3D), settings.get("horizontal"));
        for (String projectile : new String[] {"rod", "arrow", "pearl", "snowball", "egg"}) {
            assertEquals(Double.valueOf(0.4D), settings.get("projectiles." + projectile + ".horizontal"));
            assertEquals(Double.valueOf(0.4D), settings.get("projectiles." + projectile + ".vertical"));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void settingNamesAreImmutable() {
        ComboConfig.getSettingNames().add("some.other.config");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void effectiveSettingsAreImmutable() {
        defaults().getSettings().put("horizontal", 4.0D);
    }

    @Test
    public void pathsAreRestrictedToTheComboNamespace() {
        for (String name : ComboConfig.getSettingNames()) {
            String expected = "no-damage-ticks".equals(name)
                    ? "combo.no-damage-ticks" : "combo.knockback." + name;
            assertEquals(expected, ComboConfig.configurationPath(name));
        }
        for (String invalid : new String[] {null, "", "horizontal ", "Horizontal", "combo.knockback.horizontal",
                "knockback.current", "projectiles", "../../server.properties"}) {
            try {
                ComboConfig.configurationPath(invalid);
                fail("Expected unknown setting to fail: " + invalid);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Unknown Combo setting"));
            }
        }
    }

    @Test
    public void updatesAreTypedAndLeaveTheOriginalSnapshotUnchanged() {
        ComboConfig original = defaults();
        ComboConfig updated = original.withSetting("horizontal", "0.42")
                .withSetting("no-damage-ticks", "3")
                .withSetting("stop-sprint", "false")
                .withSetting("projectiles.pearl.vertical", "0.55");

        assertEquals(Double.valueOf(0.42D), updated.getSettings().get("horizontal"));
        assertEquals(Integer.valueOf(3), updated.getSettings().get("no-damage-ticks"));
        assertEquals(Boolean.FALSE, updated.getSettings().get("stop-sprint"));
        assertEquals(Double.valueOf(0.55D), updated.getSettings().get("projectiles.pearl.vertical"));
        assertEquals(Double.valueOf(0.3D), original.getSettings().get("horizontal"));
        assertEquals(Integer.valueOf(2), original.getSettings().get("no-damage-ticks"));
        assertEquals(Boolean.TRUE, original.getSettings().get("stop-sprint"));
        assertEquals(Double.valueOf(0.4D), original.getSettings().get("projectiles.pearl.vertical"));
        assertEquals(0.42D, updated.newKnockbackProfile().getHorizontal(), 0.000001D);
        assertEquals(6, updated.getMaximumNoDamageTicks());
    }

    @Test
    public void updatingOneSettingPreservesEveryOtherCustomizedValue() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (String name : ComboConfig.getSettingNames()) {
            Object value = "stop-sprint".equals(name) ? Boolean.FALSE
                    : "no-damage-ticks".equals(name) ? Integer.valueOf(7)
                    : "fall-speed".equals(name) ? Double.valueOf(0.12D) : Double.valueOf(1.25D);
            yaml.set(ComboConfig.configurationPath(name), value);
        }
        ComboConfig original = ComboConfig.load(yaml);
        ComboConfig updated = original.withSetting("horizontal", "0.75");

        for (String name : ComboConfig.getSettingNames()) {
            assertEquals(name, "horizontal".equals(name) ? Double.valueOf(0.75D)
                    : original.getSettings().get(name), updated.getSettings().get(name));
        }
    }

    @Test
    public void fallHeightCommandUpdatesRoundTripWithoutChangingTheOriginalOrNativeProfile() {
        ComboConfig original = defaults();
        ComboConfig updated = original.withSetting("fall-height", " 2.5 ");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> setting : updated.getSettings().entrySet()) {
            yaml.set(ComboConfig.configurationPath(setting.getKey()), setting.getValue());
        }

        assertEquals(2.5D, ComboConfig.load(yaml).getFallHeight(), 0.000001D);
        assertEquals(3.0D, original.getFallHeight(), 0.000001D);
        assertEquals(0.0D, updated.withSetting("fall-height", "0").getFallHeight(), 0.000001D);
        assertEquals(50.0D, updated.withSetting("fall-height", "50").getFallHeight(), 0.000001D);
        for (String name : ComboConfig.getSettingNames()) {
            if (!"fall-height".equals(name)) {
                assertEquals(name, original.getSettings().get(name), updated.getSettings().get(name));
            }
        }
        assertEquals(original.newKnockbackProfile().getVertical(),
                updated.newKnockbackProfile().getVertical(), 0.000001D);
    }

    @Test
    public void rejectsMalformedOrNonFiniteValuesWithoutChangingOriginal() {
        for (String invalid : new String[] {null, "", " ", "bad", "true", "NaN", "Infinity", "-Infinity",
                "1e9999", "4.01", "-0.1"}) {
            assertInvalid("horizontal", invalid);
        }
        for (String invalid : new String[] {null, "", "yes", "no", "1", "0", "false-ish"}) {
            assertInvalid("stop-sprint", invalid);
        }
        for (String invalid : new String[] {"-1", "101", "1.5", "NaN", "Infinity"}) {
            assertInvalid("no-damage-ticks", invalid);
        }
        for (String invalid : new String[] {null, "", "true", "NaN", "Infinity", "-Infinity",
                "1e9999", "-0.01", "50.01"}) {
            assertInvalid("fall-height", invalid);
        }
        for (String invalid : new String[] {null, "", "true", "NaN", "Infinity", "-Infinity",
                "1e9999", "-0.01", "0", "0.039", "0.501"}) {
            assertInvalid("fall-speed", invalid);
        }
    }

    @Test
    public void validatesPerSettingBoundsAndTheEntireVerticalRange() {
        assertInvalid("friction-horizontal", "0.5");
        assertInvalid("friction-vertical", "101");
        assertInvalid("extra-horizontal", "-0.1");
        assertInvalid("extra-vertical", "-4.1");
        assertInvalid("add-horizontal", "4.1");
        assertInvalid("projectiles.pearl.vertical", "-0.1");
        assertInvalid("vertical-min", "0.2");
        assertInvalid("vertical-max", "-1.1");
        assertEquals(Double.valueOf(0.2D), defaults().withSetting("vertical-max", "0.2")
                .withSetting("vertical-min", "0.2").getSettings().get("vertical-min"));
    }

    @Test
    public void everyNumericSettingCanBeEditedUsingTheCanonicalName() {
        ComboConfig config = defaults().withSetting("vertical-max", "1.25");
        for (String name : ComboConfig.getSettingNames()) {
            if (!"no-damage-ticks".equals(name) && !"stop-sprint".equals(name)) {
                double value = "fall-speed".equals(name) ? 0.12D : 1.25D;
                config = config.withSetting(name, Double.toString(value));
                assertEquals(name, Double.valueOf(value), config.getSettings().get(name));
            }
        }
    }

    @Test
    public void fallSpeedCommandRoundTripsWithoutChangingOtherSettings() {
        ComboConfig original = defaults();
        ComboConfig updated = original.withSetting("fall-speed", " 0.05 ");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> setting : updated.getSettings().entrySet()) {
            yaml.set(ComboConfig.configurationPath(setting.getKey()), setting.getValue());
        }

        assertEquals(0.05D, ComboConfig.load(yaml).getFallSpeed(), 0.000001D);
        assertEquals(0.08D, original.getFallSpeed(), 0.000001D);
        assertEquals(0.04D, updated.withSetting("fall-speed", "0.04").getFallSpeed(), 0.000001D);
        assertEquals(0.5D, updated.withSetting("fall-speed", "0.5").getFallSpeed(), 0.000001D);
        for (String name : ComboConfig.getSettingNames()) {
            if (!"fall-speed".equals(name)) {
                assertEquals(name, original.getSettings().get(name), updated.getSettings().get(name));
            }
        }
        assertEquals(original.newKnockbackProfile().getVertical(),
                updated.newKnockbackProfile().getVertical(), 0.000001D);
    }

    @Test
    public void booleanCaseAndSurroundingValueWhitespaceAreAccepted() {
        ComboConfig config = defaults().withSetting("stop-sprint", " FALSE ")
                .withSetting("horizontal", " 0.2 ");
        assertEquals(Boolean.FALSE, config.getSettings().get("stop-sprint"));
        assertEquals(Double.valueOf(0.2D), config.getSettings().get("horizontal"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void editsCannotAddressUnknownPaths() {
        defaults().withSetting("knockback.horizontal", "0.1");
    }

    @Test
    public void parsingAllowsRepairBeforeFullCandidateValidation() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combo.knockback.horizontal", "broken-value");
        yaml.set("combo.knockback.vertical", 0.12D);

        Object parsed = ComboConfig.parseSettingValue("horizontal", "0.42");
        assertEquals(Double.valueOf(0.42D), parsed);
        yaml.set(ComboConfig.configurationPath("horizontal"), parsed);
        ComboConfig repaired = ComboConfig.load(yaml);

        assertEquals(Double.valueOf(0.42D), repaired.getSettings().get("horizontal"));
        assertEquals(Double.valueOf(0.12D), repaired.getSettings().get("vertical"));
        assertEquals(Boolean.FALSE, ComboConfig.parseSettingValue("stop-sprint", "false"));
    }

    @Test
    public void parsingDoesNotReplaceFullRangeValidation() {
        Object parsed = ComboConfig.parseSettingValue("horizontal", "5");
        assertEquals(Double.valueOf(5.0D), parsed);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(ComboConfig.configurationPath("horizontal"), parsed);
        try {
            ComboConfig.load(yaml);
            fail("Parsed values must still be fully validated before application");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("horizontal"));
        }
    }

    private static ComboConfig defaults() {
        return ComboConfig.load(new YamlConfiguration());
    }

    private static void assertInvalid(String name, String rawValue) {
        ComboConfig original = defaults();
        Map<String, Object> before = original.getSettings();
        try {
            original.withSetting(name, rawValue);
            fail("Expected " + name + "=" + rawValue + " to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(name));
        }
        assertEquals(before, original.getSettings());
    }
}
