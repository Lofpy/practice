package com.poppy.practice.reach.packet;

/**
 * Packet-layer protocol boundary for ReachGuard.
 *
 * <p>ProtocolSupport or ViaVersion exposes the original client protocol at install time,
 * then normalizes supported play packets to the server's v1_8_R3 packet
 * classes before this package sees them. Keeping the allow-list here as well
 * as in the validation configuration guarantees that an unknown protocol can
 * never have an ATTACK or vanilla transaction suppressed by the bridge.</p>
 */
final class ReachProtocolPolicy {
    static final int MINECRAFT_1_7_10 = 5;
    static final int MINECRAFT_1_8 = 47;
    static final int SYNC_WINDOW_ID = 0;

    private ReachProtocolPolicy() {
    }

    static boolean supports(int protocol) {
        return protocol == MINECRAFT_1_7_10 || protocol == MINECRAFT_1_8;
    }

    /**
     * ReachGuard markers use window zero and the negative half of the signed
     * short action-number space. The sink still validates the exact reserved
     * action ID before consuming a response.
     */
    static boolean mayBeReachTransaction(int protocol, int windowId,
                                         short actionId) {
        return supports(protocol) && windowId == SYNC_WINDOW_ID && actionId < 0;
    }
}
