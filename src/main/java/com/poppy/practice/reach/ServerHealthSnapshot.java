package com.poppy.practice.reach;

public final class ServerHealthSnapshot {
    private final int serverTick;
    private final double tps;
    private final long lastTickDurationMs;
    private final boolean recentStall;
    private final long capturedNanoTime;

    public ServerHealthSnapshot(int serverTick, double tps, long lastTickDurationMs,
                                boolean recentStall) {
        this(serverTick, tps, lastTickDurationMs, recentStall, 0L);
    }

    public ServerHealthSnapshot(int serverTick, double tps, long lastTickDurationMs,
                                boolean recentStall, long capturedNanoTime) {
        this.serverTick = serverTick;
        this.tps = tps;
        this.lastTickDurationMs = lastTickDurationMs;
        this.recentStall = recentStall;
        this.capturedNanoTime = capturedNanoTime;
    }

    public int getServerTick() { return serverTick; }
    public double getTps() { return tps; }
    public long getLastTickDurationMs() { return lastTickDurationMs; }
    public boolean hasRecentStall() { return recentStall; }
    public long getCapturedNanoTime() { return capturedNanoTime; }
}
