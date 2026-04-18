package com.chenweikeng.mcparks.subtitle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class SubtitleRenderer extends GuiComponent {
    private static final int FADE_TICKS = 10; // 0.5 seconds at 20 TPS
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int SHADOW_COLOR = 0x3F3F3F;
    private static final float SCALE = 1.5f;

    private static final int[][] OUTLINE_DIRECTIONS = {
        {-1, -1}, {0, -1}, {1, -1},
        {-1, 0},          {1, 0},
        {-1, 1},  {0, 1},  {1, 1}
    };

    public void render(PoseStack poseStack, float tickDelta) {
        String subtitle = SubtitleManager.getCurrentCaption();
        if (subtitle == null || subtitle.isEmpty()) {
            return;
        }

        int displayTicks = SubtitleManager.getDisplayTicks();
        if (displayTicks <= 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // Calculate alpha for fade out during last FADE_TICKS
        int alpha = 255;
        if (displayTicks <= FADE_TICKS) {
            alpha = (int)(255 * ((float) displayTicks / FADE_TICKS));
        }

        if (alpha <= 0) {
            return;
        }

        // Wrap text to fit 80% of screen width
        int maxWidth = (int)(screenWidth * 0.8f / SCALE);
        List<FormattedCharSequence> lines = font.split(Component.literal(subtitle), maxWidth);

        if (lines.isEmpty()) {
            return;
        }

        int lineHeight = font.lineHeight;
        int lineSpacing = 2;
        int totalHeight = lines.size() * (lineHeight + lineSpacing) - lineSpacing;

        int textColorWithAlpha = (alpha << 24) | (TEXT_COLOR & 0x00FFFFFF);
        int shadowColorWithAlpha = ((alpha / 2) << 24) | (SHADOW_COLOR & 0x00FFFFFF);

        // Draw 8-direction outline first
        for (int[] dir : OUTLINE_DIRECTIONS) {
            poseStack.pushPose();
            poseStack.translate(screenWidth / 2.0f + dir[0] * 1.5f, screenHeight * 2.0f / 3.0f + dir[1] * 1.5f, 0);
            poseStack.scale(SCALE, SCALE, 1.0f);

            int y = -totalHeight / 2;
            for (FormattedCharSequence line : lines) {
                int lineWidth = font.width(line);
                int x = -lineWidth / 2;
                font.draw(poseStack, line, x, y, shadowColorWithAlpha);
                y += lineHeight + lineSpacing;
            }

            poseStack.popPose();
        }

        // Draw main text centered horizontally, lower third vertically
        poseStack.pushPose();
        poseStack.translate(screenWidth / 2.0f, screenHeight * 2.0f / 3.0f, 0);
        poseStack.scale(SCALE, SCALE, 1.0f);

        int y = -totalHeight / 2;
        for (FormattedCharSequence line : lines) {
            int lineWidth = font.width(line);
            int x = -lineWidth / 2;
            font.draw(poseStack, line, x, y, textColorWithAlpha);
            y += lineHeight + lineSpacing;
        }

        poseStack.popPose();
    }
}
