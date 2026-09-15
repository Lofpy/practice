package com.poppy.practice.reach;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReachGuardConfigTest {
    private static final double ERROR = 0.0000001D;

    @Test
    public void defaultsObserveRawAabbAtThreeBlocks() {
        ReachGuardConfig config = ReachGuardConfig.load(null);

        assertEquals(ReachGuardMode.OBSERVE, config.getMode());
        assertEquals(3.00D, config.getBaseReach(), ERROR);
        assertEquals(0.0D, config.getHitboxExpansion(), ERROR);
        assertEquals(0.0D, config.getGeometryEpsilon(), ERROR);
        assertEquals(3.00D, config.getFlagThreshold(), ERROR);
        assertEquals(3.00D, config.getProtectHighCancel(), ERROR);
        assertEquals(3.00D, config.getProtectMediumCancel(), ERROR);
        assertEquals(3.00D, config.getStrictHighCancel(), ERROR);
        assertEquals(3.00D, config.getStrictMediumCancel(), ERROR);
        assertEquals(5, config.getSpawnGraceTicks());
        assertEquals("NMS_DIRECT", config.getPacketLibrary());
        assertEquals("OBSERVE", config.getLowTpsAction());
        assertTrue(config.isLoggingEnabled());
        assertTrue(config.isAlertsEnabled());
        assertFalse(config.isKickEnabled());
        assertTrue(config.supportsProtocol(5));
        assertTrue(config.supportsProtocol(47));
        assertFalse(config.supportsProtocol(4));
        assertFalse(config.supportsProtocol(48));
    }

    @Test
    public void shippedConfigurationObservesWithThreeBlockDetectionThreshold() throws Exception {
        InputStream stream = ReachGuardConfigTest.class.getClassLoader()
                .getResourceAsStream("config.yml");
        assertTrue("config.yml must be present on the test classpath", stream != null);

        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } finally {
            stream.close();
        }
        ReachGuardConfig config = ReachGuardConfig.load(yaml);

        assertEquals(ReachGuardMode.OBSERVE, config.getMode());
        assertTrue(config.isLoggingEnabled());
        assertTrue(config.isAlertsEnabled());
        assertFalse(config.isKickEnabled());
        assertEquals(0.0D, config.getTotalExpansion(), ERROR);
        assertEquals(3.00D, config.getFlagThreshold(), ERROR);
        assertEquals(3.00D, config.getProtectHighCancel(), ERROR);
        assertEquals(3.00D, config.getProtectMediumCancel(), ERROR);
        assertEquals(3.00D, config.getStrictHighCancel(), ERROR);
        assertEquals(3.00D, config.getStrictMediumCancel(), ERROR);
    }

    @Test
    public void missingModeDefaultsToObserveWithoutDisablingDetection() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertEquals(ReachGuardMode.OBSERVE, ReachGuardConfig.load(yaml).getMode());

        yaml.set("reachguard.logging.enabled", true);
        yaml.set("reachguard.damage-event-guard.enabled", true);
        yaml.set("reachguard.damage-event-guard.cancel-without-permit", true);
        ReachGuardConfig config = ReachGuardConfig.load(yaml);

        assertEquals(ReachGuardMode.OBSERVE, config.getMode());
        assertTrue(config.isLoggingEnabled());
        assertTrue(config.isAlertsEnabled());
        assertTrue(config.supportsProtocol(5));
        assertTrue(config.supportsProtocol(47));
    }

    @Test
    public void explicitProtectAndStrictModesRemainAvailable() {
        for (ReachGuardMode mode : new ReachGuardMode[] {
                ReachGuardMode.PROTECT, ReachGuardMode.STRICT }) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("reachguard.mode", mode.name());

            assertEquals(mode, ReachGuardConfig.load(yaml).getMode());
        }
    }

    @Test
    public void validOverridesAreLoadedWithoutClamping() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.mode", "STRICT");
        yaml.set("reachguard.reach.base-reach", 3.01D);
        yaml.set("reachguard.reach.flag-threshold", 3.04D);
        yaml.set("reachguard.reach.protect.high-confidence-cancel", 3.09D);
        yaml.set("reachguard.reach.protect.medium-confidence-cancel", 3.16D);
        yaml.set("reachguard.reach.strict.high-confidence-cancel", 3.04D);
        yaml.set("reachguard.reach.strict.medium-confidence-cancel", 3.04D);

        ReachGuardConfig config = ReachGuardConfig.load(yaml);

        assertEquals(ReachGuardMode.STRICT, config.getMode());
        assertEquals(3.01D, config.getBaseReach(), ERROR);
        assertEquals(3.04D, config.getFlagThreshold(), ERROR);
        assertEquals(3.09D, config.getProtectHighCancel(), ERROR);
        assertEquals(3.16D, config.getProtectMediumCancel(), ERROR);
    }

    @Test
    public void partialThresholdOverridesKeepDependentDefaultsValid() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.reach.base-reach", 3.01D);
        yaml.set("reachguard.reach.flag-threshold", 3.04D);
        yaml.set("reachguard.reach.protect.high-confidence-cancel", 3.09D);

        ReachGuardConfig config = ReachGuardConfig.load(yaml);

        assertEquals(3.04D, config.getFlagThreshold(), ERROR);
        assertEquals(3.09D, config.getProtectHighCancel(), ERROR);
        assertEquals(3.09D, config.getProtectMediumCancel(), ERROR);
        assertEquals(3.04D, config.getStrictHighCancel(), ERROR);
        assertEquals(3.04D, config.getStrictMediumCancel(), ERROR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownModeThrowsInsteadOfSilentlyFallingBack() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.mode", "AGGRESSIVE");

        ReachGuardConfig.load(yaml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFiniteReachThrowsInsteadOfUsingFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.reach.base-reach", Double.NaN);

        ReachGuardConfig.load(yaml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void outOfRangeHistorySizeThrowsInsteadOfClamping() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.tracking.attacker-history-size", 1);

        ReachGuardConfig.load(yaml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void descendingProtectThresholdsThrow() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.reach.protect.high-confidence-cancel", 3.20D);
        yaml.set("reachguard.reach.protect.medium-confidence-cancel", 3.10D);

        ReachGuardConfig.load(yaml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void strictCancelThresholdBelowFlagThresholdThrows() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.reach.flag-threshold", 3.10D);
        yaml.set("reachguard.reach.strict.high-confidence-cancel", 3.05D);

        ReachGuardConfig.load(yaml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unavailablePacketBridgeNameIsRejected() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reachguard.compatibility.packet-library", "PacketEvents");

        ReachGuardConfig.load(yaml);
    }
}
