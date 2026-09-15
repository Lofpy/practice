package com.poppy.practice.reach.packet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class NmsReachPacketBridgeTest {
    @Test
    public void handlerIsAfterNormalizedDecoderAndBeforePacketHandler() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            channel.pipeline().addLast("decoder", new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("poppy_chatter_kb_protocol_47",
                    new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("poppy_artificial_latency",
                    new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("packet_handler",
                    new ChannelInboundHandlerAdapter());

            NmsReachPacketBridge.installNormalizedPacketHandler(
                    channel.pipeline(), new ChannelDuplexHandler());

            List<String> names = channel.pipeline().names();
            assertTrue(names.indexOf("decoder")
                    < names.indexOf(NmsReachPacketBridge.HANDLER_NAME));
            assertTrue(names.indexOf("poppy_artificial_latency")
                    < names.indexOf(NmsReachPacketBridge.HANDLER_NAME));
            assertTrue(names.indexOf(NmsReachPacketBridge.HANDLER_NAME)
                    < names.indexOf("packet_handler"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void missingDecoderFailsInstallationOpen() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            channel.pipeline().addLast("packet_handler",
                    new ChannelInboundHandlerAdapter());
            NmsReachPacketBridge.installNormalizedPacketHandler(
                    channel.pipeline(), new ChannelDuplexHandler());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void negativeTransactionActionSurvivesSignedShortWireDecode() {
        assertTransactionActionRoundTrip((short) -1);
        assertTransactionActionRoundTrip((short) -12345);
        assertTransactionActionRoundTrip(Short.MIN_VALUE);
    }

    private static void assertTransactionActionRoundTrip(short actionId) {
        ByteBuf payload = Unpooled.buffer();
        try {
            // ProtocolSupport's 1.7 clientbound transformer writes and its
            // serverbound transformer reads this exact payload shape. Netty's
            // signed readShort preserves the two's-complement action number.
            payload.writeByte(0);
            payload.writeShort(actionId);
            payload.writeBoolean(true);

            assertEquals(0, payload.readUnsignedByte());
            assertEquals(actionId, payload.readShort());
            assertTrue(payload.readBoolean());
        } finally {
            payload.release();
        }
    }
}
