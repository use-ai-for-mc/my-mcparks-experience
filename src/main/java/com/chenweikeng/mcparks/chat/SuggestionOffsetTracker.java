package com.chenweikeng.mcparks.chat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bookkeeping for the tab-completion round trip when {@link CommandAliases}
 * rewrites the first token of a suggestion request. The outgoing-packet
 * mixin records {@code (id, delta)} where {@code delta = wireLen - userLen}
 * (length of the first token after rewrite minus before). The
 * incoming-packet mixin subtracts {@code delta} from each suggestion's
 * range {@code start}/{@code end} so suggestions line up with the text the
 * user actually typed, not the text we sent on the wire.
 */
public final class SuggestionOffsetTracker {
    private static final ConcurrentMap<Integer, Integer> DELTAS = new ConcurrentHashMap<>();

    /** Bound so a lost response doesn't leak forever. */
    private static final int MAX_PENDING = 64;

    private SuggestionOffsetTracker() {}

    public static void record(int id, int delta) {
        if (DELTAS.size() >= MAX_PENDING) DELTAS.clear();
        DELTAS.put(id, delta);
    }

    /** Consume and return the recorded delta for {@code id}, or 0 if none. */
    public static int consume(int id) {
        Integer d = DELTAS.remove(id);
        return d != null ? d : 0;
    }
}
