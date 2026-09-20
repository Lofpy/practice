package com.poppy.lobby;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The proxy must enable BungeeCord plugin-message compatibility. */
public final class ProxyConnectMessage {
    private ProxyConnectMessage() { }

    public static byte[] encode(String serverName) {
        if (serverName == null || !serverName.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Proxy backend names must contain 1-64 letters, digits, underscores or hyphens");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            out.close();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode in-memory proxy message", impossible);
        }
    }
}
