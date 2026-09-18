package com.dragonspeech.client.mind;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.mind.BarType;
import com.dragonspeech.mind.DuelAction;
import net.minecraft.resources.ResourceLocation;

/**
 * Central registry of every texture ResourceLocation used by the mind-
 * duel screens, plus their exact pixel dimensions. All source art was
 * pre-resized to match these dimensions exactly (see the asset pipeline
 * notes in docs/MIND_DUEL_PHASE6.md), so every blit() call is a plain
 * 1:1 draw with no runtime scaling - deliberately the simplest, most
 * version-stable overload.
 */
public final class MindTextures {

    private MindTextures() {}

    private static ResourceLocation gui(String path) {
        return DragonSpeech.id("textures/gui/mind/" + path);
    }

    // --- Defense Breach: barrier + cracks + mends (the real per-variant art pack)
    /** The opponent's barrier - what you attack, rendered blue. */
    public static final ResourceLocation BREACH_BARRIER_BASE = gui("breach/barrier_base.png");
    /** Your own barrier - what you mend, rendered gold. Two barriers now render side by side (see MindDuelScreen), so each is smaller than the old single-barrier size. */
    public static final ResourceLocation BREACH_BARRIER_BASE_GOLD = gui("breach/barrier_base_gold.png");
    public static final int BREACH_BARRIER_SIZE = 170;

    public static ResourceLocation crackOverlay(int variantId) {
        return gui("breach/cracks/crack_" + variantId + ".png");
    }

    public static ResourceLocation mendOverlay(int variantId) {
        return gui("breach/mends/mend_" + variantId + ".png");
    }

    // --- Bars: frame + fill per BarType color
    public static final int BAR_FRAME_WIDTH = 220;
    public static final int BAR_FRAME_HEIGHT = 20;
    public static final int BAR_FILL_MAX_WIDTH = 200;
    public static final int BAR_FILL_HEIGHT = 12;

    public static ResourceLocation barFrame(BarType bar) {
        return gui("bars/" + bar.getSerializedName() + "_frame.png");
    }

    public static ResourceLocation barFill(BarType bar) {
        return gui("bars/" + bar.getSerializedName() + "_fill.png");
    }

    // --- Cards: one clickable card per BarType, used to select which bar a click spends
    public static final int CARD_WIDTH = 90;
    public static final int CARD_HEIGHT = 120;

    public static ResourceLocation card(BarType bar) {
        return gui("cards/" + bar.getSerializedName() + "_card.png");
    }

    // --- Buttons: compact bar-selection during Breach (one per BarType color)
    public static final int SMALL_BUTTON_WIDTH = 90;
    public static final int SMALL_BUTTON_HEIGHT = 27;

    public static ResourceLocation smallButton(BarType accent) {
        return gui("buttons/" + accent.getSerializedName() + "_small.png");
    }

    // --- Panels
    public static final ResourceLocation LARGE_PANEL = gui("panels/large_panel.png");
    public static final int LARGE_PANEL_WIDTH = 460;
    public static final int LARGE_PANEL_HEIGHT = 230;
    public static final ResourceLocation STATUS_PANEL = gui("panels/status_panel.png");
    public static final int STATUS_PANEL_WIDTH = 300;
    public static final int STATUS_PANEL_HEIGHT = 80;
    public static final ResourceLocation PHASE_BADGE = gui("panels/phase_badge.png");
    public static final int PHASE_BADGE_WIDTH = 200;
    public static final int PHASE_BADGE_HEIGHT = 40;

    // --- Slots / decor
    public static final ResourceLocation PORTRAIT_FRAME = gui("slots/portrait_frame.png");
    public static final int PORTRAIT_FRAME_SIZE = 60;
    public static final ResourceLocation STATUS_ROW_FRAME = gui("slots/status_row_frame.png");
    public static final int STATUS_ROW_WIDTH = 300;
    public static final int STATUS_ROW_HEIGHT = 46;

    public static ResourceLocation hexSlot(BarType bar) {
        return gui("slots/hex_" + bar.getSerializedName() + ".png");
    }
    public static final int HEX_SLOT_SIZE = 40;

    // --- Action icons for the handful of DuelActions that are still simple named buttons (Struggle, commands, etc.)
    public static final int ICON_SIZE = 20;

    public static ResourceLocation iconFor(DuelAction action) {
        return switch (action) {
            case SPEAK_TRUE_NAME -> gui("icons/speak_true_name.png");
            case DISENGAGE -> gui("icons/disengage.png");
            // STRIKE_CRACK/SEAL_CRACK/SEIZE_CONTROL and command-card actions
            // aren't simple named buttons any more (or never were) - no dedicated icon needed.
            default -> null;
        };
    }
}
