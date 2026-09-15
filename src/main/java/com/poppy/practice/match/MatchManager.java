package com.poppy.practice.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchManager {
    private final Map<UUID, Match> matchesByPlayer = new HashMap<UUID, Match>();
    private final Map<String, Match> matchesByArenaId = new HashMap<String, Match>();

    public boolean register(Match match) {
        if (match == null || match.getState() != MatchState.STARTING
                || matchesByPlayer.containsKey(match.getFirstPlayerId())
                || matchesByPlayer.containsKey(match.getSecondPlayerId())
                || matchesByArenaId.containsKey(match.getArenaId())) {
            return false;
        }
        matchesByPlayer.put(match.getFirstPlayerId(), match);
        matchesByPlayer.put(match.getSecondPlayerId(), match);
        matchesByArenaId.put(match.getArenaId(), match);
        return true;
    }

    public Match getByPlayer(UUID playerId) {
        return matchesByPlayer.get(playerId);
    }

    public Match getByArena(String arenaId) {
        return matchesByArenaId.get(arenaId);
    }

    public void remove(Match match) {
        if (matchesByPlayer.get(match.getFirstPlayerId()) == match) {
            matchesByPlayer.remove(match.getFirstPlayerId());
        }
        if (matchesByPlayer.get(match.getSecondPlayerId()) == match) {
            matchesByPlayer.remove(match.getSecondPlayerId());
        }
        if (matchesByArenaId.get(match.getArenaId()) == match) {
            matchesByArenaId.remove(match.getArenaId());
        }
    }

    public int size() {
        return matchesByArenaId.size();
    }

    public Collection<Match> all() {
        return new ArrayList<Match>(matchesByArenaId.values());
    }

    public void clear() {
        matchesByPlayer.clear();
        matchesByArenaId.clear();
    }
}
