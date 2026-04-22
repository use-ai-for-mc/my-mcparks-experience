package com.chenweikeng.mcparks.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.lang.reflect.Field;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-processes the client-side Brigadier command tree so ImagineFun-style
 * aliases are known to tab-completion. Renames top-level literals
 * {@code w → msg} and {@code warp → w} per
 * {@link CommandAliases#WIRE_TO_USER}. Idempotent — running twice is a
 * no-op. Uses reflection because Brigadier's backing maps are private.
 */
public final class CommandTreeAliaser {
    private static final Logger LOGGER = LoggerFactory.getLogger("my-mcparks-experience/aliases");

    private CommandTreeAliaser() {}

    public static void applyIfEnabled(CommandDispatcher<?> dispatcher) {
        if (!CommandAliases.enabled() || dispatcher == null) return;
        try {
            RootCommandNode<?> root = dispatcher.getRoot();
            int renamed = 0;
            for (Map.Entry<String, String> e : CommandAliases.WIRE_TO_USER.entrySet()) {
                if (renameLiteralChild(root, e.getKey(), e.getValue())) renamed++;
            }
            if (renamed > 0) {
                LOGGER.info("Renamed {} top-level command literals for ImagineFun aliases", renamed);
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to apply command tree aliases: {}", t.toString());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean renameLiteralChild(CommandNode root, String oldName, String newName) throws Exception {
        Map children   = childrenMap(root);
        Map literals   = privateMap(root, "literals");
        Map arguments  = privateMap(root, "arguments");

        // Already aliased — no-op.
        if (children.containsKey(newName)) return false;
        Object existing = children.get(oldName);
        if (!(existing instanceof LiteralCommandNode lit)) return false;

        LiteralCommandNode renamed = new LiteralCommandNode(
            newName,
            lit.getCommand(),
            lit.getRequirement(),
            lit.getRedirect(),
            lit.getRedirectModifier(),
            lit.isFork()
        );
        // Copy descendants. getChildren() returns Collection<CommandNode<S>>.
        for (Object grand : lit.getChildren()) {
            renamed.addChild((CommandNode) grand);
        }

        children.remove(oldName);
        if (literals != null) literals.remove(oldName);
        if (arguments != null) arguments.remove(oldName);
        root.addChild(renamed);
        return true;
    }

    private static Map<?, ?> childrenMap(Object target) throws Exception {
        Field f = CommandNode.class.getDeclaredField("children");
        f.setAccessible(true);
        return (Map<?, ?>) f.get(target);
    }

    private static Map<?, ?> privateMap(Object target, String fieldName) {
        try {
            Field f = CommandNode.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object m = f.get(target);
            return m instanceof Map<?, ?> map ? map : null;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }
}
