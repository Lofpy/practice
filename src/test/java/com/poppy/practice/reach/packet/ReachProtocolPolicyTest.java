package com.poppy.practice.reach.packet;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReachProtocolPolicyTest {
    @Test
    public void onlyProtocolSupport17AndNative18AreEnforced() {
        assertTrue(ReachProtocolPolicy.supports(5));
        assertTrue(ReachProtocolPolicy.supports(47));

        assertFalse(ReachProtocolPolicy.supports(-1));
        assertFalse(ReachProtocolPolicy.supports(4));
        assertFalse(ReachProtocolPolicy.supports(48));
        assertFalse(ReachProtocolPolicy.supports(754));
    }

    @Test
    public void onlyNegativeWindowZeroResponsesCanBelongToReachGuard() {
        assertTrue(ReachProtocolPolicy.mayBeReachTransaction(
                5, 0, (short) -1));
        assertTrue(ReachProtocolPolicy.mayBeReachTransaction(
                5, 0, Short.MIN_VALUE));
        assertTrue(ReachProtocolPolicy.mayBeReachTransaction(
                47, 0, (short) -12345));

        assertFalse(ReachProtocolPolicy.mayBeReachTransaction(
                -1, 0, (short) -1));
        assertFalse(ReachProtocolPolicy.mayBeReachTransaction(
                754, 0, (short) -1));
        assertFalse(ReachProtocolPolicy.mayBeReachTransaction(
                5, 1, (short) -1));
        assertFalse(ReachProtocolPolicy.mayBeReachTransaction(
                5, 0, (short) 0));
        assertFalse(ReachProtocolPolicy.mayBeReachTransaction(
                5, 0, (short) 1));
    }
}
