package com.dragonspeech.client.gui;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.dragon.DragonColor;
import com.dragonspeech.dragon.DragonEntity;
import com.dragonspeech.network.DragonBondSettingsPayload;
import com.dragonspeech.network.SetDragonNamePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * "When you hatch an egg, your dragon becomes 'bonded' to you. This
 * should open up a screen that gives you options for your dragon."
 * Built from the uploaded Dragons_Mark_gui.json layout, revised after
 * real in-game screenshots showed the first pass's controls misaligned
 * and clipped - every control now shares ONE left edge (CONTROL_X) and
 * ONE width (CONTROL_W), rather than the mockup's slightly-inconsistent
 * placeholder coordinates.
 *
 *   - the mark image is recolored per the bonded dragon's actual color
 *   - checkbox_7/8 ("Option A"/"Option B") and checkbox_3 ("Stamina
 *     usage before/after") are real toggle switches; checkbox_9 ("Use
 *     Dragon Stamina") is the one real enable/disable checkbox,
 *     bracket-styled to look different from the switches
 *   - the entity slot shows the ACTUAL bonded dragon, scaled to its
 *     real current size (hatchling vs adult are ~20x apart) instead of
 *     a fixed zoom level
 *   - "Option A" = follow owner while wandering, "Option B" = join in
 *     whatever the owner is fighting
 *   - a name field lets you choose your dragon's display name; its true
 *     name (see DragonEntity#trueNameKnown) shows once the bond has
 *     matured past HATCHLING
 *
 * Every control sends the full settings state to the server on change;
 * the server re-validates ownership on every packet regardless (see
 * DragonSpeechNetworking's receivers for these payloads).
 *
 * VERSION-RISK NOTE: the live entity preview uses InventoryScreen.
 * renderEntityInInventoryFollowsMouse - this project's Model/
 * renderToBuffer API is confirmed (via real compiler output earlier in
 * this project) to be the classic pre-RenderState shape, which is what
 * that helper's signature has long assumed; if the exact parameter
 * order doesn't match your Loom setup, that's the one call to check
 * first. The scale formula below is an approximation (vanilla doesn't
 * expose an exact "fit this bounding box" helper) - tune SCALE_FACTOR
 * if the preview is too big/small.
 */
public class DragonBondScreen extends Screen {

    private static final int PANEL_W = 560;
    private static final int PANEL_H = 302;

    // Every control shares this left edge and width - the mockup's
    // per-control x values varied slightly and looked misaligned/clipped
    // in practice (see the bug report this revision fixes).
    private static final int CONTROL_X = 150;
    private static final int CONTROL_W = 210;

    private static final int C_BG = 0xE8141018;
    private static final int C_BORDER = 0xFF4A4238;
    private static final int C_TEXT = 0xFFE8DEC8;
    private static final int C_TEXT_DIM = 0xFFA89A80;
    private static final int C_BAR_BG = 0xFF241E1A;
    private static final int C_BAR_FILL = 0xFFC9A24B;

    private final UUID dragonId;
    private int panelX;
    private int panelY;

    private Button useDragonStaminaButton;
    private Button followingButton;
    private Button stayButton;
    private Button aggressiveButton;
    private Button attackHostileButton;
    private Button attackNeutralButton;
    private Button attackPassiveButton;
    private Button beforeAfterButton;
    private Button giveHeartButton;
    private LimiterSlider limiterSlider;
    private EditBox nameBox;

    public DragonBondScreen(UUID dragonId) {
        super(Component.literal("The Dragon's Mark"));
        this.dragonId = dragonId;
    }

    /** Used by the keybind (see DragonSpeechClient) to open this screen without any server round-trip - settings are all SynchedEntityData, already current client-side. */
    public static DragonEntity findNearestBondedClientSide() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        DragonEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof DragonEntity d && d.isBondedTo(mc.player)) {
                double distSq = d.distanceToSqr(mc.player);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = d;
                }
            }
        }
        return nearest;
    }

    private DragonEntity dragon() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof DragonEntity d && d.getUUID().equals(dragonId)) {
                return d;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        for (Button b : com.dragonspeech.client.nav.DragonSpeechTabBar.buildButtons(this.width, com.dragonspeech.client.nav.DragonSpeechTabBar.Tab.BONDED_DRAGONS)) {
            addRenderableWidget(b);
        }

        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        DragonEntity d = dragon();
        boolean useStamina = d != null && d.useDragonStamina();
        boolean beforeOwn = d == null || d.staminaBeforeOwn();
        int limiter = d != null ? d.staminaLimiterPercent() : 20;
        boolean following = d == null || d.optionFollowing();
        boolean stay = d != null && d.optionStay();
        boolean aggressive = d != null && d.optionAggressiveAssist();

        int y = panelY + 44;
        int rowH = 26;

        // checkbox_9 - the one real enable/disable checkbox (bracket-style label, not ON/OFF)
        useDragonStaminaButton = Button.builder(checkboxLabel("Use Dragon Stamina", useStamina), b -> {
                boolean now = !checkboxState(b.getMessage());
                b.setMessage(checkboxLabel("Use Dragon Stamina", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X, y, CONTROL_W, 20)
            .build();
        addRenderableWidget(useDragonStaminaButton);
        y += rowH;

        // checkbox_7 / "Option A" - follow owner
        followingButton = Button.builder(switchLabel("Follow Owner", following), b -> {
                boolean now = !switchState(b.getMessage());
                b.setMessage(switchLabel("Follow Owner", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X, y, CONTROL_W, 20)
            .build();
        addRenderableWidget(followingButton);
        y += rowH;

        // "Stay" - the counterpart to Follow Owner. Holds position,
        // overrides both following and normal wandering while on.
        stayButton = Button.builder(switchLabel("Stay", stay), b -> {
                boolean now = !switchState(b.getMessage());
                b.setMessage(switchLabel("Stay", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X, y, CONTROL_W, 20)
            .build();
        addRenderableWidget(stayButton);
        y += rowH;

        // checkbox_8 / "Option B" - aggressive assist
        aggressiveButton = Button.builder(switchLabel("Join Fights", aggressive), b -> {
                boolean now = !switchState(b.getMessage());
                b.setMessage(switchLabel("Join Fights", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X, y, CONTROL_W, 20)
            .build();
        addRenderableWidget(aggressiveButton);
        y += rowH;

        // "attack nearby X" - proactive targeting filter, independent of
        // Join Fights' original owner-hit-triggered assist behavior.
        // Compact row of 3 small checkboxes rather than 3 full-width
        // rows, since there's no real ambiguity to worry about here
        // (unlike the earlier ON/OFF switches, these are short and
        // self-explanatory with a tooltip).
        int smallW = (CONTROL_W - 8) / 3;
        boolean attackHostile = d != null && d.attackNearbyHostile();
        boolean attackNeutral = d != null && d.attackNearbyNeutral();
        boolean attackPassive = d != null && d.attackNearbyPassive();

        attackHostileButton = Button.builder(checkboxLabel("Hostile", attackHostile), b -> {
                boolean now = !checkboxState(b.getMessage());
                b.setMessage(checkboxLabel("Hostile", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X, y, smallW, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Join Fights also proactively attacks nearby hostile mobs (zombies, skeletons, etc)")))
            .build();
        addRenderableWidget(attackHostileButton);

        attackNeutralButton = Button.builder(checkboxLabel("Neutral", attackNeutral), b -> {
                boolean now = !checkboxState(b.getMessage());
                b.setMessage(checkboxLabel("Neutral", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X + smallW + 4, y, smallW, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Join Fights also proactively attacks nearby neutral mobs (wolves, piglins, bees, etc)")))
            .build();
        addRenderableWidget(attackNeutralButton);

        attackPassiveButton = Button.builder(checkboxLabel("Passive", attackPassive), b -> {
                boolean now = !checkboxState(b.getMessage());
                b.setMessage(checkboxLabel("Passive", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X + (smallW + 4) * 2, y, smallW, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Join Fights also proactively attacks nearby passive mobs (cows, pigs, sheep, etc)")))
            .build();
        addRenderableWidget(attackPassiveButton);
        y += rowH;

        // slider_5 - stamina limiter percent (label drawn in render(), directly above)
        y += 12;
        limiterSlider = new LimiterSlider(panelX + CONTROL_X, y, CONTROL_W, 20, limiter, this::sendSettings);
        addRenderableWidget(limiterSlider);
        y += rowH;

        // checkbox_3 - stamina usage before/after
        beforeAfterButton = Button.builder(switchLabel(beforeOwn ? "Dragon Stamina First" : "Dragon Stamina Last", beforeOwn), b -> {
                boolean now = !switchState(b.getMessage());
                b.setMessage(switchLabel(now ? "Dragon Stamina First" : "Dragon Stamina Last", now));
                sendSettings();
            })
            .bounds(panelX + CONTROL_X, y, CONTROL_W, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Use Dragon Stamina before or after your own stamina")))
            .build();
        addRenderableWidget(beforeAfterButton);
        y += rowH + 12;

        // name field (label drawn in render(), directly above)
        nameBox = new EditBox(font, panelX + CONTROL_X, y, CONTROL_W, 18, Component.literal("Dragon name"));
        nameBox.setMaxLength(32);
        if (d != null && d.hasCustomName()) {
            nameBox.setValue(d.getCustomName().getString());
        }
        addRenderableWidget(nameBox);
        y += 26;

        // "Give Heart" - replaces the old repeatable "/dragon eldunari"
        // command entirely per explicit direction ("replaced with a
        // button on the bonded dragon gui that is there when the heart
        // is there and is gone when the heart has been given"). Only
        // added at all once - if the dragon has already given its one
        // heart, this button simply doesn't exist rather than being
        // shown disabled, matching "is gone when the heart has been
        // given" literally.
        if (d != null && !d.heartGiven()) {
            giveHeartButton = Button.builder(Component.literal("Give Heart"), b ->
                    ClientPlayNetworking.send(new com.dragonspeech.network.GiveDragonHeartPayload(dragonId)))
                .bounds(panelX + CONTROL_X, y, CONTROL_W, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Your dragon offers its heart to you freely, once - it will answer to you without a mind duel")))
                .build();
            addRenderableWidget(giveHeartButton);
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(panelX + PANEL_W - 60, panelY + PANEL_H - 24, 50, 18)
            .build());
    }

    // ---- toggle label helpers ----

    private static Component checkboxLabel(String text, boolean on) {
        return Component.literal((on ? "[x] " : "[ ] ") + text);
    }

    private static boolean checkboxState(Component label) {
        return label.getString().startsWith("[x]");
    }

    private static Component switchLabel(String text, boolean on) {
        return Component.literal(text + (on ? "  ( ON)" : "  (OFF)"))
            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static boolean switchState(Component label) {
        return label.getString().endsWith("( ON)");
    }

    private void sendSettings() {
        boolean useStamina = checkboxState(useDragonStaminaButton.getMessage());
        boolean beforeOwn = switchState(beforeAfterButton.getMessage());
        boolean following = switchState(followingButton.getMessage());
        boolean stay = switchState(stayButton.getMessage());
        boolean aggressive = switchState(aggressiveButton.getMessage());
        boolean attackHostile = checkboxState(attackHostileButton.getMessage());
        boolean attackNeutral = checkboxState(attackNeutralButton.getMessage());
        boolean attackPassive = checkboxState(attackPassiveButton.getMessage());
        int limiter = limiterSlider.percent();
        ClientPlayNetworking.send(DragonBondSettingsPayload.pack(dragonId, useStamina, beforeOwn, limiter, following, aggressive,
            attackHostile, attackNeutral, attackPassive, stay));
    }

    @Override
    public void onClose() {
        if (nameBox != null) {
            ClientPlayNetworking.send(new SetDragonNamePayload(dragonId, nameBox.getValue()));
        }
        super.onClose();
    }

    /**
     * THE REAL FIX, confirmed against your actual decompiled Screen.java:
     * Screen.render() calls this.renderBackground(...) internally, which
     * calls renderBlurredBackground() -> gameRenderer.processBlurEffect().
     * The previous round's fix only skipped OUR OWN call to
     * renderBackground() from render() below - it missed that render()
     * still calls super.render() at the end (needed to actually draw
     * the buttons/sliders/etc in the renderables list), and THAT
     * independently calls this.renderBackground() again on its own,
     * reintroducing the exact blur that fix was supposed to remove.
     * Overriding renderBackground() itself as a no-op stops it at the
     * source regardless of which path calls it - we already draw our
     * own dark overlay manually in render() below, so nothing here
     * needs replacing, just disabling.
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Our own plain dim overlay - renderBackground() above is now a
        // no-op, so this is the only background this screen draws.
        guiGraphics.fill(0, 0, width, height, 0xC0000000);

        guiGraphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, C_BG);
        guiGraphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, C_BORDER);

        guiGraphics.drawCenteredString(font, "The Dragon's Mark", panelX + PANEL_W / 2, panelY + 9, C_TEXT);

        DragonEntity d = dragon();

        // Growth progress - "right below where it says The Dragon's Mark"
        int growthBarX = panelX + 16, growthBarY = panelY + 30, growthBarW = 107, growthBarH = 10;
        guiGraphics.fill(growthBarX, growthBarY, growthBarX + growthBarW, growthBarY + growthBarH, C_BAR_BG);
        if (d != null) {
            int fillW = (int) (growthBarW * (d.growthPercent() / 100f));
            guiGraphics.fill(growthBarX, growthBarY, growthBarX + fillW, growthBarY + growthBarH, C_BAR_FILL);
        }
        guiGraphics.renderOutline(growthBarX, growthBarY, growthBarW, growthBarH, C_BORDER);
        String growthLabel = d != null && d.growthPercent() >= 100 ? "Growth (Fully Grown)" : "Growth";
        guiGraphics.drawString(font, growthLabel, growthBarX, growthBarY - 10, C_TEXT_DIM, false);

        // progress_bar_4 - the dragon's own stamina readout
        int barX = panelX + 16, barY = panelY + 54, barW = 107, barH = 10;
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, C_BAR_BG);
        if (d != null && d.maxDragonStamina() > 0) {
            int fillW = (int) (barW * Math.min(1f, d.dragonStamina() / d.maxDragonStamina()));
            guiGraphics.fill(barX, barY, barX + fillW, barY + barH, C_BAR_FILL);
        }
        guiGraphics.renderOutline(barX, barY, barW, barH, C_BORDER);
        guiGraphics.drawString(font, "Dragon Stamina", barX, barY - 10, C_TEXT_DIM, false);

        // image_2 - the recolored mark
        DragonColor color = d != null ? d.color() : DragonColor.RED;
        ResourceLocation mark = DragonSpeech.id("textures/gui/dragon_bond/mark_" + color.getSerializedName() + ".png");
        int markSize = 110;
        int markX = panelX + 12, markY = panelY + 73;
        guiGraphics.blit(mark, markX, markY, 0, 0, markSize, markSize, markSize, markSize);

        // name / true name, under the mark
        int infoY = markY + markSize + 6;
        if (d != null) {
            String display = d.hasCustomName() ? d.getCustomName().getString() : "(unnamed)";
            guiGraphics.drawString(font, "Name: " + display, markX, infoY, C_TEXT, false);
            if (d.trueNameKnown()) {
                guiGraphics.drawString(font, "True name: " + d.trueName(), markX, infoY + 11, 0xFFB89AE0, false);
            } else {
                guiGraphics.drawString(font, "True name: unknown", markX, infoY + 11, C_TEXT_DIM, false);
            }
        }

        label(guiGraphics, "Stamina Limiter", panelX + CONTROL_X, limiterSlider.getY() - 12);
        label(guiGraphics, "Dragon's Name", panelX + CONTROL_X, nameBox.getY() - 11);

        // entity_10 - the actual bonded dragon, sized to its real current scale
        int entX = panelX + 375, entY = panelY + 34, entW = 165, entH = PANEL_H - 68;
        guiGraphics.renderOutline(entX, entY, entW, entH, C_BORDER);
        if (d != null) {
            renderDragonPreview(guiGraphics, d, entX, entY, entW, entH, mouseX, mouseY);
        } else {
            guiGraphics.drawCenteredString(font, "(dragon not nearby)", entX + entW / 2, entY + entH / 2, C_TEXT_DIM);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics guiGraphics, String text, int x, int y) {
        guiGraphics.drawString(font, text, x, y, C_TEXT, false);
    }

    private void renderDragonPreview(GuiGraphics guiGraphics, DragonEntity d, int x, int y, int w, int h, int mouseX, int mouseY) {
        // REAL FIX, derived from your actual decompiled InventoryScreen.
        // Two genuine bugs, not guesses:
        //
        // 1. Vanilla computes its internal render scale as
        //    (my requested size) / livingEntity.getScale() - dividing
        //    by the entity's OWN scale attribute. A hatchling has
        //    modelScale ~0.045, so my chosen size was being amplified
        //    ~22x for a small dragon - exactly "zoomed into a single
        //    gray texture, no dragon visible." Fixed by multiplying by
        //    d.getScale() before passing it in, which cancels out
        //    vanilla's internal division - the effective zoom is now
        //    the same regardless of the dragon's current age/scale.
        //
        // 2. The method does its own (boxCenter - mouseParam) centering
        //    internally (feeding an atan() for the rotation angle).
        //    Every previous attempt pre-computed that same subtraction
        //    BEFORE passing it in, which canceled the method's own
        //    subtraction and left something close to raw, unadjusted
        //    mouse coordinates going into atan() - producing a
        //    near-maximum rotation angle almost regardless of cursor
        //    position, matching the consistently broken look across
        //    many different mouse positions. Fixed by passing raw
        //    mouseX/mouseY directly, exactly like vanilla's own caller
        //    does, and letting the method center it itself.
        // Increased substantially - my box (~165x208) is roughly 3x
        // bigger in each dimension than vanilla's own reference box
        // (49x70, using scale=30 - see renderBg's own call in your
        // decompiled InventoryScreen), so the same absolute scale value
        // looked tiny by comparison, even though the math was correct.
        //
        // On "auto fit as it grows": this DESIRED_ZOOM approach cancels
        // out the entity's actual scale attribute (see the comment
        // above renderDragonPreview), which means every age stage
        // renders at the SAME size in this box - a hatchling and an
        // adult both "fill" it the same amount. That's a deliberate
        // tradeoff, not an oversight: letting size vary with actual
        // scale is exactly what caused the original 22x-zoom bug for
        // hatchlings, since vanilla's own internal division amplifies
        // small scale values dramatically. If you want the preview to
        // show TRUE relative size differences between ages instead
        // (adult visibly bigger than hatchling in this box), say so -
        // it's possible, just a different, more carefully-bounded
        // formula than a flat cancel-out.
        final float DESIRED_ZOOM = 90f;
        int scale = (int) Math.max(1, DESIRED_ZOOM * d.getScale());
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x, y, x + w, y + h, scale, 0.0625f, mouseX, mouseY, d);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** slider_5 - the stamina limiter, 0-100%. */
    private static class LimiterSlider extends AbstractSliderButton {
        private final Runnable onChange;

        LimiterSlider(int x, int y, int w, int h, int initialPercent, Runnable onChange) {
            super(x, y, w, h, Component.literal("Limiter: " + initialPercent + "%"), initialPercent / 100.0);
            this.onChange = onChange;
        }

        int percent() {
            return (int) Math.round(this.value * 100.0);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Limiter: " + percent() + "%"));
        }

        @Override
        protected void applyValue() {
            if (onChange != null) {
                onChange.run();
            }
        }
    }
}
