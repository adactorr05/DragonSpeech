package com.dragonspeech.client.config;

import com.dragonspeech.config.DragonSpeechConfig;
import com.dragonspeech.network.ResetDifficultyTuningPayload;
import com.dragonspeech.network.SetDifficultyTuningPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.dragonspeech.network.ConfigRequestPayload;

/**
 * "I want a config that opens a sub config that edits what each magic difficulty does. Right now,
 * easy, normal, and hard have a set ruleset on what it affects." - per explicit direction.
 *
 * Opened from the Server tab's "Edit Magic Difficulty Rules" button. 3 sub-tabs, one per
 * DragonSpeechConfig.Difficulty tier, each with the same 4 sliders:
 *   - Health Floor (shown in hearts) - how far magic overdraft can drain health before it stops.
 *   - Stamina Regen Multiplier
 *   - Spell Cost Multiplier
 *   - Structure Rarity Multiplier (higher = rarer)
 *
 * Which tier is CURRENTLY ACTIVE is a completely separate setting (still only changed via the
 * Create World screen or hand-editing the file) - this screen only redefines what each of the 3
 * named tiers actually MEANS. Same permission rules as the rest of the Server tab per explicit
 * direction: op level 4 only; offline (Title Screen) edits write straight to your own local
 * config/dragonspeech.json; once connected, edits go through the server (which re-validates
 * permission before applying), and a real server's own values always win on sync.
 */
public class DifficultyTuningScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int ROW_H = 24;
    private static final int LIST_TOP = 62;

    private static final int C_BG = 0xE8141018;
    private static final int C_BORDER = 0xFF4A4238;
    private static final int C_TEXT = 0xFFE8DEC8;
    private static final int C_TAB_ACTIVE_TEXT = 0xFFFFFFFF;

    private final Screen parent;
    private DragonSpeechConfig.Difficulty activeTier = DragonSpeechConfig.Difficulty.EASY;

    /** Local edit mirror, one entry per tier, loaded from whichever source applies (see loadFromSource()) and pushed on every slider change. */
    private final Map<DragonSpeechConfig.Difficulty, float[]> edit = new EnumMap<>(DragonSpeechConfig.Difficulty.class);
    // Index order within each float[4]: healthFloorHearts, regenMultiplier, costMultiplier, structureSpacing.

    private int panelX;
    private int panelY;
    private int panelH;

    public DifficultyTuningScreen(Screen parent) {
        super(Component.literal("Magic Difficulty Rules"));
        this.parent = parent;
    }

    private boolean isConnected() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    protected void init() {
        this.panelH = Math.min(this.height - 40, 300);
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - panelH) / 2;

        loadFromSource();
        if (isConnected()) {
            ClientPlayNetworking.send(new ConfigRequestPayload());
        }
        rebuild();
    }

    private void loadFromSource() {
        for (DragonSpeechConfig.Difficulty tier : DragonSpeechConfig.Difficulty.values()) {
            float healthFloor;
            float regen;
            float cost;
            float spacing;
            ServerConfigClientCache.DifficultyTuning synced = isConnected()
                    ? ServerConfigClientCache.difficultyTuning(tier.name().toLowerCase(Locale.ROOT))
                    : null;
            if (synced != null) {
                healthFloor = synced.healthFloor();
                regen = synced.regenMultiplier();
                cost = synced.costMultiplier();
                spacing = synced.structureSpacing();
            } else if (!isConnected()) {
                healthFloor = DragonSpeechConfig.minSurvivableHealth(tier);
                regen = DragonSpeechConfig.regenMultiplier(tier);
                cost = DragonSpeechConfig.costMultiplier(tier);
                spacing = DragonSpeechConfig.structureSpacingMultiplier(tier);
            } else {
                // Connected but haven't heard back from the server yet - keep whatever was already
                // loaded (or the field-default 0s on first open) rather than guessing.
                float[] existing = edit.get(tier);
                edit.put(tier, existing != null ? existing : new float[]{2f, 1f, 1f, 1f});
                continue;
            }
            edit.put(tier, new float[]{healthFloor / 2f, regen, cost, spacing});
        }
    }

    /** Called after a fresh ConfigSyncPayload arrives while this screen is open - see DragonSpeechClient. */
    public void onServerSyncReceived() {
        loadFromSource();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int tabW = (PANEL_W - 16) / 3;
        DragonSpeechConfig.Difficulty[] tiers = DragonSpeechConfig.Difficulty.values();
        for (int i = 0; i < tiers.length; i++) {
            DragonSpeechConfig.Difficulty tier = tiers[i];
            Component label = Component.literal(prettyName(tier)).withStyle(style ->
                    tier == activeTier ? style.withColor(C_TAB_ACTIVE_TEXT).withBold(true) : style);
            addRenderableWidget(Button.builder(label, b -> {
                        activeTier = tier;
                        rebuild();
                    })
                    .bounds(panelX + 8 + i * (tabW + 4), panelY + 22, tabW, 20).build());
        }

        int y = panelY + LIST_TOP;
        int rowX = panelX + 12;
        int rowW = PANEL_W - 24;
        float[] values = edit.get(activeTier);

        y = row(y, rowX, rowW, v -> "Health Floor (" + fmt(v) + " hearts, 0 = overdraft can kill you)",
                values[0], 0f, 20f, v -> setValue(0, v),
                "How far magic overdraft can drain " + prettyName(activeTier) + "'s health before it stops, in hearts. 0 means overdraft can genuinely kill you on this tier.");
        y = row(y, rowX, rowW, v -> "Stamina Regen Multiplier (" + fmt(v) + "x)",
                values[1], 0.1f, 5.0f, v -> setValue(1, v),
                "Multiplies stamina regen speed on " + prettyName(activeTier) + ".");
        y = row(y, rowX, rowW, v -> "Spell Cost Multiplier (" + fmt(v) + "x)",
                values[2], 0.1f, 5.0f, v -> setValue(2, v),
                "Multiplies every spell's stamina cost on " + prettyName(activeTier) + ".");
        y = row(y, rowX, rowW, v -> "Structure Rarity Multiplier (" + fmt(v) + "x, higher = rarer)",
                values[3], 0.1f, 10.0f, v -> setValue(3, v),
                "Multiplies dragonspeech structure spacing on " + prettyName(activeTier) + " - higher means the mod's structures generate more rarely.");

        addRenderableWidget(tip(Button.builder(Component.literal("Reset to Defaults"), b -> resetToDefaults())
                .bounds(panelX + 12, panelY + panelH - 26, 140, 20).build(),
                "Restores all 3 tiers' original shipped values (health floor, both multipliers, and structure rarity for Easy, Normal, and Hard alike)."));

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + PANEL_W - 92, panelY + panelH - 26, 80, 20).build());
    }

    private int row(int y, int rowX, int rowW, java.util.function.Function<Float, String> labelFormatter, float value, float min, float max, Consumer<Float> onCommit, String tooltip) {
        addRenderableWidget(tip(new FloatSlider(rowX, y, rowW, 20, labelFormatter, value, min, max, onCommit), tooltip));
        return y + ROW_H;
    }

    private void setValue(int index, float value) {
        edit.get(activeTier)[index] = value;
        commitTier(activeTier);
    }

    private void commitTier(DragonSpeechConfig.Difficulty tier) {
        float[] v = edit.get(tier);
        float healthFloorRaw = v[0] * 2f;
        if (isConnected()) {
            ClientPlayNetworking.send(new SetDifficultyTuningPayload(tier.name(), healthFloorRaw, v[1], v[2], v[3]));
        } else {
            DragonSpeechConfig.setMinSurvivableHealth(tier, healthFloorRaw);
            DragonSpeechConfig.setRegenMultiplier(tier, v[1]);
            DragonSpeechConfig.setCostMultiplier(tier, v[2]);
            DragonSpeechConfig.setStructureSpacingMultiplier(tier, v[3]);
        }
    }

    private void resetToDefaults() {
        if (isConnected()) {
            ClientPlayNetworking.send(new ResetDifficultyTuningPayload());
        } else {
            DragonSpeechConfig.resetDifficultyTuningToDefaults();
            loadFromSource();
        }
        rebuild();
    }

    private static String prettyName(DragonSpeechConfig.Difficulty tier) {
        String name = tier.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static <T extends AbstractWidget> T tip(T widget, String text) {
        widget.setTooltip(Tooltip.create(Component.literal(text)));
        return widget;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Same fix as ConfigScreen/SentienceEditorScreen - see ConfigScreen's own doc for the full explanation.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF0A0808);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + panelH + 2, C_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, C_BG);
        graphics.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 6, C_TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Same generic float slider shape used throughout the config GUI - see ConfigScreen's own FloatSlider (fixed the same way, for the same live-update bug). */
    private static class FloatSlider extends AbstractSliderButton {
        private final java.util.function.Function<Float, String> labelFormatter;
        private final float min;
        private final float max;
        private final Consumer<Float> onCommit;

        FloatSlider(int x, int y, int w, int h, java.util.function.Function<Float, String> labelFormatter, float initial, float min, float max, Consumer<Float> onCommit) {
            super(x, y, w, h, Component.literal(labelFormatter.apply(initial)), clampFraction((initial - min) / (max - min)));
            this.labelFormatter = labelFormatter;
            this.min = min;
            this.max = max;
            this.onCommit = onCommit;
        }

        private static double clampFraction(double v) {
            if (Double.isNaN(v)) return 0.0;
            return Math.max(0.0, Math.min(1.0, v));
        }

        private float currentValue() {
            return (float) (min + (max - min) * this.value);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(labelFormatter.apply(currentValue())));
        }

        @Override
        protected void applyValue() {
            if (onCommit != null) {
                onCommit.accept(currentValue());
            }
        }
    }
}
