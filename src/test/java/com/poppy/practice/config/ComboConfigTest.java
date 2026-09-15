package com.poppy.practice.config;

import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class ComboConfigTest {
    private static final double EPSILON = 0.000000001D;

    @Test
    public void defaultsUseIndependentLowComboKnockback() {
        ComboConfig config = ComboConfig.load(new YamlConfiguration());
        KnockbackProfile profile = config.newKnockbackProfile();

        assertEquals(2, config.getNoDamageTicks());
        assertEquals(4, config.getMaximumNoDamageTicks());
        assertEquals(3.0D, config.getFallHeight(), EPSILON);
        assertEquals(0.08D, config.getFallSpeed(), EPSILON);
        assertEquals(0.30D, profile.getHorizontal(), EPSILON);
        assertEquals(0.10D, profile.getVertical(), EPSILON);
        assertEquals(0.15D, profile.getVerticalMax(), EPSILON);
        assertEquals(-1.0D, profile.getVerticalMin(), EPSILON);
        assertEquals(2.0D, profile.getFrictionHorizontal(), EPSILON);
        assertEquals(2.0D, profile.getFrictionVertical(), EPSILON);
        assertEquals(0.10D, profile.getExtraHorizontal(), EPSILON);
        assertEquals(0.0D, profile.getExtraVertical(), EPSILON);
        assertEquals(0.10D, profile.getWTapExtraHorizontal(), EPSILON);
        assertEquals(0.0D, profile.getWTapExtraVertical(), EPSILON);
        assertTrue(profile.isStopSprint());
    }

    @Test
    public void actualTickIntervalIsConvertedToTheNativeDoubleCounter() {
        for (int interval : new int[] {0, 1, 2, 5, 10, 100}) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("combo.no-damage-ticks", interval);
            ComboConfig config = ComboConfig.load(yaml);

            assertEquals(interval, config.getNoDamageTicks());
            assertEquals(interval * 2, config.getMaximumNoDamageTicks());
            assertEquals(interval, config.getMaximumNoDamageTicks()
                    - config.getMaximumNoDamageTicks() / 2);
        }
    }

    @Test
    public void fallHeightAcceptsFractionalBlocksAndBothInclusiveLimits() {
        for (double height : new double[] {0.0D, 0.25D, 3.0D, 50.0D}) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("combo.knockback.fall-height", height);
            ComboConfig config = ComboConfig.load(yaml);

            assertEquals(height, config.getFallHeight(), EPSILON);
            assertEquals(0.30D, config.newKnockbackProfile().getHorizontal(), EPSILON);
            assertEquals(0.10D, config.newKnockbackProfile().getVertical(), EPSILON);
        }
    }

    @Test
    public void rejectsInvalidFallHeights() {
        for (Object invalid : new Object[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -0.01D, 50.01D, "3", "bad", true}) {
            assertInvalid("combo.knockback.fall-height", invalid);
        }
    }

    @Test
    public void fallSpeedAcceptsFractionalBlocksPerTickAndInclusiveLimits() {
        for (double speed : new double[] {0.04D, 0.05D, 0.08D, 0.5D}) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("combo.knockback.fall-speed", speed);
            ComboConfig config = ComboConfig.load(yaml);

            assertEquals(speed, config.getFallSpeed(), EPSILON);
            assertEquals(0.30D, config.newKnockbackProfile().getHorizontal(), EPSILON);
            assertEquals(0.10D, config.newKnockbackProfile().getVertical(), EPSILON);
        }
    }

    @Test
    public void rejectsInvalidFallSpeedsIncludingZeroThatWouldSuspendPlayers() {
        for (Object invalid : new Object[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -0.01D, 0.0D, 0.039D, 0.501D, "0.08", "bad", true}) {
            assertInvalid("combo.knockback.fall-speed", invalid);
        }
    }

    @Test
    public void allKnockbackFieldsIncludingEveryProjectileAreConfigurable() throws Exception {
        String[][] mappings = {
                {"friction-horizontal", "getFrictionHorizontal"},
                {"friction-vertical", "getFrictionVertical"},
                {"horizontal", "getHorizontal"},
                {"vertical", "getVertical"},
                {"vertical-min", "getVerticalMin"},
                {"vertical-max", "getVerticalMax"},
                {"extra-horizontal", "getExtraHorizontal"},
                {"extra-vertical", "getExtraVertical"},
                {"wtap-extra-horizontal", "getWTapExtraHorizontal"},
                {"wtap-extra-vertical", "getWTapExtraVertical"},
                {"add-horizontal", "getAddHorizontal"},
                {"add-vertical", "getAddVertical"},
                {"projectiles.rod.horizontal", "getRodHorizontal"},
                {"projectiles.rod.vertical", "getRodVertical"},
                {"projectiles.arrow.horizontal", "getArrowHorizontal"},
                {"projectiles.arrow.vertical", "getArrowVertical"},
                {"projectiles.pearl.horizontal", "getPearlHorizontal"},
                {"projectiles.pearl.vertical", "getPearlVertical"},
                {"projectiles.snowball.horizontal", "getSnowballHorizontal"},
                {"projectiles.snowball.vertical", "getSnowballVertical"},
                {"projectiles.egg.horizontal", "getEggHorizontal"},
                {"projectiles.egg.vertical", "getEggVertical"}
        };
        YamlConfiguration yaml = new YamlConfiguration();
        for (String[] mapping : mappings) {
            yaml.set("combo.knockback." + mapping[0], 1.25D);
        }
        yaml.set("combo.knockback.stop-sprint", false);
        KnockbackProfile profile = ComboConfig.load(yaml).newKnockbackProfile();

        for (String[] mapping : mappings) {
            Method getter = KnockbackProfile.class.getMethod(mapping[1]);
            assertEquals(mapping[0], 1.25D, (Double) getter.invoke(profile), EPSILON);
        }
        assertFalse(profile.isStopSprint());
    }

    @Test
    public void profilesAreFreshAndConfigDoesNotRetainMutableYamlReferences() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combo.knockback.horizontal", 0.25D);
        yaml.set("combo.knockback.fall-height", 2.5D);
        yaml.set("combo.knockback.fall-speed", 0.05D);
        ComboConfig config = ComboConfig.load(yaml);
        KnockbackProfile first = config.newKnockbackProfile();
        KnockbackProfile second = config.newKnockbackProfile();
        first.setHorizontal(3.0D);
        first.setPearlVertical(3.0D);
        yaml.set("combo.knockback.horizontal", 2.0D);
        yaml.set("combo.knockback.fall-height", 5.0D);
        yaml.set("combo.knockback.fall-speed", 0.2D);

        assertNotSame(first, second);
        assertEquals(0.25D, second.getHorizontal(), EPSILON);
        assertEquals(0.4D, second.getPearlVertical(), EPSILON);
        assertEquals(0.25D, config.newKnockbackProfile().getHorizontal(), EPSILON);
        assertEquals(2.5D, config.getFallHeight(), EPSILON);
        assertEquals(0.05D, config.getFallSpeed(), EPSILON);
    }

    @Test
    public void unrelatedKitAndGlobalProfileValuesAreIgnored() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("knockback.horizontal", 3.0D);
        yaml.set("knockback.current", "some-other-profile");
        yaml.set("knockback.profiles.combo.horizontal", 2.0D);
        yaml.set("nodebuff.no-damage-ticks", 50);
        yaml.set("combat.no-damage-ticks", 60);

        ComboConfig config = ComboConfig.load(yaml);

        assertEquals(0.30D, config.newKnockbackProfile().getHorizontal(), EPSILON);
        assertEquals(2, config.getNoDamageTicks());
    }

    @Test
    public void rejectsNonFiniteAndInvalidNumbers() {
        for (Object invalid : new Object[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -0.1D, 4.1D, "0.3", "bad", true}) {
            assertInvalid("combo.knockback.horizontal", invalid);
        }
        for (Object invalid : new Object[] {Double.NaN, Double.POSITIVE_INFINITY,
                -1, 101, 1.5D, "2"}) {
            assertInvalid("combo.no-damage-ticks", invalid);
        }
    }

    @Test
    public void rejectsZeroOrAmplifyingFrictionAndExtremeValues() {
        for (double invalid : new double[] {0.0D, -1.0D, 0.5D, 101.0D}) {
            assertInvalid("combo.knockback.friction-horizontal", invalid);
            assertInvalid("combo.knockback.friction-vertical", invalid);
        }
        assertInvalid("combo.knockback.extra-vertical", -4.1D);
        assertInvalid("combo.knockback.add-horizontal", 4.1D);
        assertInvalid("combo.knockback.projectiles.pearl.horizontal", Double.NaN);
        assertInvalid("combo.knockback.projectiles.egg.vertical", -0.1D);
    }

    @Test
    public void rejectsInvertedVerticalLimits() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combo.knockback.vertical-min", 0.5D);
        yaml.set("combo.knockback.vertical-max", 0.2D);
        try {
            ComboConfig.load(yaml);
            fail("Expected invalid vertical range to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("vertical-min"));
        }
    }

    @Test
    public void rejectsTextInsteadOfBoolean() {
        assertInvalid("combo.knockback.stop-sprint", "false");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullConfigurationIsRejected() {
        ComboConfig.load(null);
    }

    private static void assertInvalid(String key, Object value) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(key, value);
        try {
            ComboConfig.load(yaml);
            fail("Expected " + key + "=" + value + " to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(key));
        }
    }
}
