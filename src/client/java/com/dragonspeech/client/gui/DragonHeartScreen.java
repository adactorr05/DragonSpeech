package com.dragonspeech.client.gui;

import com.dragonspeech.network.SetHeartStaminaSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * "A rundown version of the bonded screen (but for Dragon Hearts) that
 * have a setting to use its stamina before your own or not" per explicit
 * direction. Deliberately much simpler than DragonBondScreen - just the
 * heart's identity, its current/max stored strength as a bar, and the
 * two settings (mirroring DragonEntity's own useDragonStamina()/
 * staminaBeforeOwn() pattern exactly, per HEART_USE_STAMINA/
 * HEART_STAMINA_BEFORE_OWN's own doc).
 *
 * Standalone popup, not part of the main 3-tab suite
 * (DragonSpeechTabBar) - this opens from directly right-clicking a
 * usable heart, not from the tab-based navigation.
 */
public class DragonHeartScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 130;

    private final UUID heartId;
    private final String colorName;
    private final float energy;
    private final float maxEnergy;
    private boolean useStamina;
    private boolean staminaBeforeOwn;

    private int panelX;
    private int panelY;

    public DragonHeartScreen(UUID heartId, String colorName, float energy, float maxEnergy, boolean useStamina, boolean staminaBeforeOwn) {
        super(Component.literal("Dragon Heart"));
        this.heartId = heartId;
        this.colorName = colorName;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.useStamina = useStamina;
        this.staminaBeforeOwn = staminaBeforeOwn;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        addRenderableWidget(Checkbox.builder(Component.literal("Use this heart's stamina"), font)
            .pos(panelX + 12, panelY + 40)
            .selected(useStamina)
            .onValueChange((checkbox, value) -> {
                useStamina = value;
                sendSettings();
            })
            .build());

        addRenderableWidget(Checkbox.builder(Component.literal("Use before your own stamina (unchecked = after)"), font)
            .pos(panelX + 12, panelY + 64)
            .selected(staminaBeforeOwn)
            .onValueChange((checkbox, value) -> {
                staminaBeforeOwn = value;
                sendSettings();
            })
            .build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(panelX + PANEL_W / 2 - 40, panelY + PANEL_H - 24, 80, 20)
            .build());
    }

    private void sendSettings() {
        ClientPlayNetworking.send(new SetHeartStaminaSettingsPayload(heartId, useStamina, staminaBeforeOwn));
    }

    /**
     * Same confirmed fix as DragonBondScreen uses - Screen.render() calls
     * renderBackground() internally (both directly and again via
     * super.render() at the end), which triggers the vanilla blur effect
     * regardless of what render() itself does. Overriding this as a
     * no-op stops it at the source; our own plain dim overlay in
     * render() below is the only background this screen draws.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE0100C08);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF6b3fa0);

        String title = ("mad".equals(colorName) ? "Mad Dragon Heart" : capitalize(colorName) + " Dragon Heart");
        g.drawCenteredString(this.font, title, panelX + PANEL_W / 2, panelY + 8, 0xFFE8C878);

        int barY = panelY + 22;
        int barW = PANEL_W - 24;
        float fraction = maxEnergy > 0 ? Math.max(0f, Math.min(1f, energy / maxEnergy)) : 0f;
        g.fill(panelX + 12, barY, panelX + 12 + barW, barY + 8, 0xFF2A2018);
        g.fill(panelX + 12, barY, panelX + 12 + Math.round(barW * fraction), barY + 8, 0xFF8B5FBF);
        g.drawCenteredString(this.font, Math.round(energy) + " / " + Math.round(maxEnergy), panelX + PANEL_W / 2, barY + 10, 0xFFCCCCCC);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
