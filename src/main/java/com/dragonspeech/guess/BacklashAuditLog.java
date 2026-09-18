package com.dragonspeech.guess;

import com.dragonspeech.word.RiskTier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory record of recent SEVERE/CATASTROPHIC backlash events, so a
 * server moderator can answer "what just happened to that player" via
 * /dragonspeech audit. Deliberately in-memory only (lost on restart) -
 * this is a diagnostic tool, not a permanent record; persistent audit
 * logging can be a later config option if servers want it.
 */
public final class BacklashAuditLog {

    public record Entry(long gameTime, String playerName, RiskTier tier, float severityScale) {}

    private static final int MAX_ENTRIES = 50;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private BacklashAuditLog() {}

    public static synchronized void record(long gameTime, String playerName, RiskTier tier, float severityScale) {
        if (tier != RiskTier.SEVERE && tier != RiskTier.CATASTROPHIC) {
            return; // only serious events are worth a moderator's attention
        }
        ENTRIES.addFirst(new Entry(gameTime, playerName, tier, severityScale));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeLast();
        }
    }

    public static synchronized List<Entry> recent(int count) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (result.size() >= count) {
                break;
            }
            result.add(entry);
        }
        return result;
    }
}
