package com.poppy.lobby;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProxyConnectMessageTest {
    @Test public void encodesExactBungeeCordConnectSubchannelAndBackend() throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(ProxyConnectMessage.encode("pvp")));
        assertEquals("Connect", in.readUTF());
        assertEquals("pvp", in.readUTF());
        assertEquals(-1, in.read());
    }
    @Test public void acceptsLobbyAndConventionalNames() {
        assertNotNull(ProxyConnectMessage.encode("lobby"));
        assertNotNull(ProxyConnectMessage.encode("survival"));
        assertNotNull(ProxyConnectMessage.encode("practice-1_US"));
    }
    @Test public void rejectsUnsafeOrMissingNames() {
        for (String value : new String[] { null, "", "pvp:25565", "Connect\u0000ALL", "two words", "../lobby" }) {
            try {
                ProxyConnectMessage.encode(value);
                fail("Accepted invalid backend name: " + value);
            } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void rejectsUnboundedBackendName() {
        char[] chars = new char[65];
        java.util.Arrays.fill(chars, 'a');
        try {
            ProxyConnectMessage.encode(new String(chars));
            fail("Accepted overlong backend name");
        } catch (IllegalArgumentException expected) { }
    }
}
