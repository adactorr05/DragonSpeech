package com.dragonspeech.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The gold stamina bar, drawn just above the hunger bar (right side of
 * the hotbar, mirroring where vanilla puts mount health). Simple
 * fill-based rendering - no texture atlas dependency.
 *
 * VERSION-RISK NOTE: HudRenderCallback is this file's fabric-api touch
 * point. If its lambda signature differs in your version (some versions
 * pass a DeltaTracker as the second argument instead of a float), adjust
 * the lambda's second parameter type - the body stays identical.
 */
public final class StaminaHudOverlay {

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;

    private static final int COLOR_FRAME = 0xFF3A3020;
    private static final int COLOR_BACK = 0xAA1A1610;
    private static final int COLOR_FILL = 0xFFE8C030;   // gold
    private static final int COLOR_FILL_LOW = 0xFFB05010; // ember-orange when nearly spent

    private StaminaHudOverlay() {}

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> render(guiGraphics));
    }

    private static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isCreative()) {
            return;
        }
        // Config GUI (Client tab) "Stamina HUD" toggle.
        if (!com.dragonspeech.client.config.DragonSpeechClientConfig.staminaHudEnabled()) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Config GUI (Client tab) "Stamina HUD Position" - BOTTOM_RIGHT matches the bar's original
        // hardcoded position (right-aligned above the hunger bar); the other 3 corners are new.
        int x;
        int y;
        switch (com.dragonspeech.client.config.DragonSpeechClientConfig.staminaHudPosition()) {
            case TOP_LEFT -> {
                x = screenWidth / 2 - 91;
                y = 10;
            }
            case TOP_RIGHT -> {
                x = screenWidth / 2 + 91 - BAR_WIDTH;
                y = 10;
            }
            case BOTTOM_LEFT -> {
                x = screenWidth / 2 - 91;
                y = screenHeight - 39 - 10;
            }
            default -> { // BOTTOM_RIGHT
                x = screenWidth / 2 + 91 - BAR_WIDTH;
                y = screenHeight - 39 - 10;
            }
        }

        float fraction = ClientStaminaCache.maxStamina() > 0f
            ? Math.min(1f, ClientStaminaCache.stamina() / ClientStaminaCache.maxStamina())
            : 0f;
        int fillWidth = Math.round((BAR_WIDTH - 2) * fraction);
        int fillColor = fraction < 0.2f ? COLOR_FILL_LOW : COLOR_FILL;

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, COLOR_FRAME);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BACK);
        if (fillWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + BAR_HEIGHT - 1, fillColor);
        }
    }
}
