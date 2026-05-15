package com.chenweikeng.mcparks.monkeycraft;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.chenweikeng.mcparks.ServerState;
import com.chenweikeng.monkeycraft_api.v1.ChatMessageResult;
import com.chenweikeng.monkeycraft_api.v1.MonkeycraftApi;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class McParksAudioLinkRewriter {

    // Two prefixes we treat as MCParks audio-session links. Current server
    // sends the query-string form (https://mcparks.us/audio?user=<name>);
    // the path form is kept in case the URL scheme ever changes. Both are
    // anchored so unrelated mcparks.us links (e.g. audio_files/*.mp3 in
    // cache logs) do NOT match.
    private static final String AUDIO_LINK_QUERY_PREFIX = "https://mcparks.us/audio?";
    private static final String AUDIO_LINK_PATH_PREFIX = "https://mcparks.us/audio/";
    private static final String HANDLER_TYPE = "mcparks-v1";
    private static final String SESSION_KEY = "mcparks-audio";

    private McParksAudioLinkRewriter() {}

    public static void register() {
        MonkeycraftApi.INCOMING_CHAT.register(ctx -> {
            // Defensive re-check: listeners persist for the lifetime of the
            // process and would fire on every INCOMING_CHAT invocation, even
            // for chat seen while the client is on an unrelated server (for
            // example if another mod paste-forwards or invokes the event
            // outside our own mixin). Only rewrite when actually on MCParks.
            if (!ServerState.isTargetServer()) return ChatMessageResult.PASS;
            try {
                Component in = ctx.getMessage();
                if (in == null) return ChatMessageResult.PASS;
                Rewrite r = rewrite(in);
                if (r.changed) {
                    ctx.setMessage(r.component);
                }
            } catch (Throwable t) {
                MCParksExperienceClient.LOGGER.warn("MCParks audio-link rewrite failed", t);
            }
            return ChatMessageResult.PASS;
        });
    }

    private static Rewrite rewrite(Component in) {
        Style style = in.getStyle();
        ClickEvent click = style.getClickEvent();
        boolean changedHere = false;
        if (click != null
                && click.getAction() == ClickEvent.Action.OPEN_URL
                && isAudioLink(click.getValue())) {
            String rewritten = buildMonkeycraftUri(click.getValue());
            style = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, rewritten));
            MCParksExperienceClient.LOGGER.info(
                "[MonkeyCraft] rewrote audio link: {} -> {}", click.getValue(), rewritten);
            changedHere = true;
        }

        boolean anyChildChanged = false;
        Component[] rewrittenChildren = null;
        java.util.List<Component> siblings = in.getSiblings();
        for (int i = 0; i < siblings.size(); i++) {
            Rewrite child = rewrite(siblings.get(i));
            if (child.changed) {
                if (rewrittenChildren == null) {
                    rewrittenChildren = siblings.toArray(new Component[0]);
                }
                rewrittenChildren[i] = child.component;
                anyChildChanged = true;
            }
        }

        if (!changedHere && !anyChildChanged) {
            return new Rewrite(in, false);
        }

        MutableComponent out = MutableComponent.create(in.getContents()).setStyle(style);
        if (rewrittenChildren != null) {
            for (Component c : rewrittenChildren) out.append(c);
        } else {
            for (Component c : siblings) out.append(c);
        }
        return new Rewrite(out, true);
    }

    private static boolean isAudioLink(String url) {
        if (url == null) return false;
        return url.startsWith(AUDIO_LINK_QUERY_PREFIX) || url.startsWith(AUDIO_LINK_PATH_PREFIX);
    }

    private static String buildMonkeycraftUri(String originalUrl) {
        String encoded = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
        return "monkeycraft://audio"
                + "?type=" + HANDLER_TYPE
                + "&session=" + SESSION_KEY
                + "&url=" + encoded;
    }

    private record Rewrite(Component component, boolean changed) {}
}
