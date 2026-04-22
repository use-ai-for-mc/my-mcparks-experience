package com.chenweikeng.mcparks.chat;

import com.chenweikeng.mcparks.config.ModConfig;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side command aliasing: when the {@code imagineFunCommandAliases}
 * config option is on, rewrite outgoing commands so the user can type the
 * ImagineFun forms against an MCParks server.
 *
 * <ul>
 *   <li>User types {@code /msg <player> <message>} — we send {@code /w ...}</li>
 *   <li>User types {@code /w <name>} — we send {@code /warp <name>}</li>
 * </ul>
 *
 * The receive-side {@code CommandTreeAliaser} renames the opposite direction
 * in the locally-stored command tree so Brigadier's tab-completion knows
 * about {@code /msg} and {@code /w}. Suggestion requests and responses
 * carry offsets that must be shifted by the length delta between the
 * user-facing and wire-facing forms.
 */
public final class CommandAliases {

    /** ImagineFun name (user-facing) -> MCParks name (wire). */
    public static final Map<String, String> USER_TO_WIRE = Map.of(
        "msg", "w",
        "w",   "warp"
    );

    /** MCParks name (wire) -> ImagineFun name (user-facing). Reverse of above. */
    public static final Map<String, String> WIRE_TO_USER = Map.of(
        "w",    "msg",
        "warp", "w"
    );

    private CommandAliases() {}

    public static boolean enabled() {
        return ModConfig.currentSetting.imagineFunCommandAliases;
    }

    /**
     * Rewrite a typed command line (without the leading {@code /}) so the
     * first token is in the wire form. Returns the original if no alias
     * matches or the feature is disabled.
     */
    public static String rewriteOutgoing(String command) {
        if (!enabled() || command == null || command.isEmpty()) return command;
        int space = command.indexOf(' ');
        String head = space < 0 ? command : command.substring(0, space);
        String tail = space < 0 ? ""       : command.substring(space);
        String wire = USER_TO_WIRE.get(head.toLowerCase(Locale.ROOT));
        if (wire == null) return command;
        return wire + tail;
    }

    /**
     * Length delta introduced by rewriting the first token of {@code userCommand}
     * from user-form to wire-form. Positive when wire form is longer; negative
     * when shorter. Used to shift suggestion offsets on the wire.
     */
    public static int outgoingDelta(String userCommand) {
        if (!enabled() || userCommand == null || userCommand.isEmpty()) return 0;
        int space = userCommand.indexOf(' ');
        String head = space < 0 ? userCommand : userCommand.substring(0, space);
        String wire = USER_TO_WIRE.get(head.toLowerCase(Locale.ROOT));
        if (wire == null) return 0;
        return wire.length() - head.length();
    }

    /**
     * Rewrite a full slash-command buffer (e.g. {@code "/msg Alex hi"} as
     * used in suggestion requests) by aliasing its first token. Returns the
     * original on no match or when disabled.
     */
    public static String rewriteSlash(String slashCommand) {
        if (!enabled() || slashCommand == null || slashCommand.length() < 2) return slashCommand;
        if (slashCommand.charAt(0) != '/') return slashCommand;
        String body = slashCommand.substring(1);
        String rewritten = rewriteOutgoing(body);
        if (rewritten == body) return slashCommand; // reference equality — fast path
        return "/" + rewritten;
    }
}
