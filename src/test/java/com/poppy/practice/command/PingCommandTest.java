package com.poppy.practice.command;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PingCommandTest {
    @Test
    public void interceptsOnlyTheSelfPingCommand() {
        assertTrue(PingCommand.isSelfPingCommand("/ping"));
        assertTrue(PingCommand.isSelfPingCommand(" /PING "));
        assertFalse(PingCommand.isSelfPingCommand("/ping OtherPlayer"));
        assertFalse(PingCommand.isSelfPingCommand("/practice ping"));
    }
}
