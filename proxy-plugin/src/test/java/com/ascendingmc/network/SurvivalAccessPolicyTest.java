package com.ascendingmc.network;

import com.velocitypowered.api.network.ProtocolVersion;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class SurvivalAccessPolicyTest {
    static Properties configured() {
        Properties properties = new Properties();
        properties.setProperty("survival-server", "survival");
        properties.setProperty("fallback-server", "lobby");
        properties.setProperty("required-version", "26.3");
        properties.setProperty("required-protocol", "777");
        return properties;
    }

    @Test public void checkedInReleaseProtocolAgreesWithPinnedVelocityApi() {
        ProtocolVersion version = ProtocolVersion.getProtocolVersion(777);
        assertTrue(version.isSupported());
        assertEquals(java.util.Collections.singletonList("26.3"), version.getVersionsSupportedBy());
    }

    @Test public void onlyExactReleaseClientCanEnterSurvival() {
        SurvivalAccessPolicy policy = SurvivalAccessPolicy.load(configured());
        assertTrue(policy.allows("survival", 777, true));
        for (int protocol : new int[] { -2, -1, 4, 5, 47, 774, 775, 776, 778, (1 << 30) | 777 }) {
            assertFalse("protocol " + protocol, policy.allows("survival", protocol, true));
        }
        assertFalse(policy.allows("survival", 777, false));
    }

    @Test public void otherServersKeepExistingCompatibility() {
        SurvivalAccessPolicy policy = SurvivalAccessPolicy.load(configured());
        for (String server : new String[] { "lobby", "pvp" }) {
            for (int protocol : new int[] { 5, 47, 776, 777 }) assertTrue(policy.allows(server, protocol, true));
        }
    }

    @Test public void invalidConfigurationFailsClosedButDoesNotBlockLegacyServers() {
        SurvivalAccessPolicy policy = SurvivalAccessPolicy.closed(configured());
        assertFalse(policy.allows("survival", 777, true));
        assertTrue(policy.allows("lobby", 5, true));
        assertTrue(policy.allows("pvp", 47, true));
    }

    @Test public void customBackendAndCanonicalNameAreBothProtected() {
        Properties properties = configured();
        properties.setProperty("survival-server", "survival-new");
        SurvivalAccessPolicy policy = SurvivalAccessPolicy.load(properties);
        assertFalse(policy.allows("SURVIVAL-NEW", 47, true));
        assertFalse(policy.allows("survival", 47, true));
        assertFalse(SurvivalAccessPolicy.closed(properties).allows("survival-new", 777, true));
    }

    @Test public void missingPropertiesAreNotInferredFromRuntimeLatest() {
        for (String key : configured().stringPropertyNames()) {
            Properties properties = configured();
            properties.remove(key);
            assertInvalid(properties);
        }
    }

    @Test public void invalidProtocolsAndSnapshotNamesAreRejected() {
        for (String protocol : new String[] { "", "-1", "0", "1073742601", "latest", "999999999999999" }) {
            Properties properties = configured();
            properties.setProperty("required-protocol", protocol);
            assertInvalid(properties);
        }
        Properties properties = configured();
        properties.setProperty("required-version", "26.3-snapshot-1");
        assertInvalid(properties);
    }

    @Test public void invalidServerNamesAndFallbackLoopsAreRejected() {
        for (String name : new String[] { "", "../survival", "survival:25568", "two words" }) {
            Properties properties = configured();
            properties.setProperty("survival-server", name);
            assertInvalid(properties);
        }
        Properties properties = configured();
        properties.setProperty("fallback-server", "survival");
        assertInvalid(properties);
    }

    private static void assertInvalid(Properties properties) {
        try {
            SurvivalAccessPolicy.load(properties);
            fail("Invalid configuration accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
