package com.dragonspeech.client.grid;

import com.dragonspeech.grid.GridSlot;
import com.dragonspeech.network.CastGridSubmitPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The casting grid, now with pagination (fixes known words silently
 * vanishing once the list outgrew the screen) and hover tooltips showing
 * each word's meaning/domain/precision.
 *
 * VERSION-RISK NOTE: Tooltip.create(Component) + Button.Builder.tooltip()
 * is the one newly-touched API here - if it doesn't compile, deleting the
 * .tooltip(...) calls loses only the hover text, nothing functional.
 */
public class CastingGridScreen extends Screen {

    // Static so the assembled spell survives closing/reopening the screen -
    // recasting the same spell no longer means rebuilding it from scratch.
    private static final Map<GridSlot, KnownWordsClientCache.ClientWordEntry> assignments = new EnumMap<>(GridSlot.class);
    private KnownWordsClientCache.ClientWordEntry selectedPaletteWord;
    private int palettePage = 0;

    private static final int PALETTE_X = 20;
    private static final int GRID_X = 250;
    private static final int TOP_Y = 40;
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 200;
    private static final int WORDS_PER_PAGE = 10;

    private static final int HEADER_COLOR = 0xFFE8C878;

    /** Config GUI (Client tab) "Casting Grid Opacity" - alpha channel recomputed each render from the current setting; 0x101018 is the original panel's fixed RGB, only the alpha (originally a hardcoded 0x88) is now adjustable. */
    private static int panelColor() {
        int alpha = Math.round(com.dragonspeech.client.config.DragonSpeechClientConfig.castingGridOpacity() * 255f);
        return (alpha << 24) | 0x101018;
    }

    public CastingGridScreen() {
        super(Component.literal("The Ancient Language"));
    }

    @Override
    protected void init() {
        super.init();
        refreshLayout();
    }

    private void refreshLayout() {
        clearWidgets();
        layoutPalette();
        layoutGrid();
        layoutActionButtons();
    }

    private List<KnownWordsClientCache.ClientWordEntry> allWords() {
        return KnownWordsClientCache.get().stream()
                .sorted((a, b) -> Boolean.compare(b.favorited(), a.favorited())) // favorites first
                .toList();
    }

    /** One-click placement: drop the word straight into the first empty slot of its category. Falls back to hold-and-place if all matching slots are full. */
    private void onPaletteWordClicked(KnownWordsClientCache.ClientWordEntry word) {
        for (GridSlot slot : GridSlot.values()) {
            if (slot.category().getSerializedName().equalsIgnoreCase(word.category())
                    && !assignments.containsKey(slot)) {
                assignments.put(slot, word);
                selectedPaletteWord = null;
                refreshLayout();
                return;
            }
        }
        // No empty slot of this category - hold it so a click on a filled
        // slot can swap it in.
        selectedPaletteWord = word;
        refreshLayout();
    }

    private int maxPage() {
        return Math.max(0, (allWords().size() - 1) / WORDS_PER_PAGE);
    }

    private void layoutPalette() {
        List<KnownWordsClientCache.ClientWordEntry> known = allWords();
        int start = palettePage * WORDS_PER_PAGE;
        int end = Math.min(start + WORDS_PER_PAGE, known.size());

        int y = TOP_Y;
        for (int i = start; i < end; i++) {
            KnownWordsClientCache.ClientWordEntry word = known.get(i);
            boolean isSelected = word.equals(selectedPaletteWord);
            String prefix = isSelected ? "> " : "";
            addRenderableWidget(Button.builder(
                            Component.literal(prefix + (word.favorited() ? "\u2605 " : "") + word.trueName() + "  [" + word.category() + "]"),
                            button -> onPaletteWordClicked(word)
                    ).bounds(PALETTE_X, y, BUTTON_WIDTH, 20)
                    .tooltip(Tooltip.create(Component.literal(
                            "\"" + word.meaning() + "\"\n"
                                    + "Domain: " + word.domain() + "\n"
                                    + String.format("Precision: %.2f", word.precision()))))
                    .build());
            y += ROW_HEIGHT;
        }

        // Pager row
        int pagerY = TOP_Y + (WORDS_PER_PAGE * ROW_HEIGHT) + 6;
        if (maxPage() > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                palettePage = Math.max(0, palettePage - 1);
                refreshLayout();
            }).bounds(PALETTE_X, pagerY, 30, 20).build());

            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                palettePage = Math.min(maxPage(), palettePage + 1);
                refreshLayout();
            }).bounds(PALETTE_X + BUTTON_WIDTH - 30, pagerY, 30, 20).build());
        }
    }

    private void layoutGrid() {
        int y = TOP_Y;
        for (GridSlot slot : GridSlot.values()) {
            KnownWordsClientCache.ClientWordEntry filled = assignments.get(slot);
            Button.Builder builder = Button.builder(slotLabel(slot), button -> onSlotClicked(slot))
                    .bounds(GRID_X, y, BUTTON_WIDTH, 20);
            if (filled != null) {
                builder.tooltip(Tooltip.create(Component.literal(
                        "\"" + filled.meaning() + "\"\nClick to remove")));
            }
            addRenderableWidget(builder.build());
            y += ROW_HEIGHT;
        }
    }

    private void layoutActionButtons() {
        int y = TOP_Y + (GridSlot.values().length * ROW_HEIGHT) + 10;
        addRenderableWidget(Button.builder(Component.literal("Cast"), button -> submitCast())
                .bounds(GRID_X, y, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear All"), button -> {
            assignments.clear();
            refreshLayout();
        }).bounds(GRID_X + 105, y, 95, 20).build());
    }

    private void onSlotClicked(GridSlot slot) {
        boolean holdingMatch = selectedPaletteWord != null
                && selectedPaletteWord.category().equalsIgnoreCase(slot.category().getSerializedName());

        if (holdingMatch) {
            // Swap the held word in, whether the slot was filled or empty.
            assignments.put(slot, selectedPaletteWord);
            selectedPaletteWord = null;
        } else if (assignments.containsKey(slot)) {
            assignments.remove(slot);
        }
        refreshLayout();
    }

    private Component slotLabel(GridSlot slot) {
        KnownWordsClientCache.ClientWordEntry filled = assignments.get(slot);
        String text = slot.label() + ": " + (filled != null ? filled.trueName() : "-");
        return Component.literal(text);
    }

    private void submitCast() {
        JsonObject json = new JsonObject();
        for (Map.Entry<GridSlot, KnownWordsClientCache.ClientWordEntry> entry : assignments.entrySet()) {
            json.addProperty(entry.getKey().name(), entry.getValue().id());
        }
        String payload = json.toString();
        LastSpellCache.remember(payload);
        ClientPlayNetworking.send(new CastGridSubmitPayload(payload));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        // Panel backgrounds behind each column
        graphics.fill(PALETTE_X - 6, TOP_Y - 22, PALETTE_X + BUTTON_WIDTH + 6, TOP_Y + (WORDS_PER_PAGE * ROW_HEIGHT) + 32, panelColor());
        graphics.fill(GRID_X - 6, TOP_Y - 22, GRID_X + BUTTON_WIDTH + 6, TOP_Y + ((GridSlot.values().length + 2) * ROW_HEIGHT), panelColor());

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, HEADER_COLOR);
        graphics.drawString(this.font, "Known Words" + pageIndicator(), PALETTE_X, TOP_Y - 14, HEADER_COLOR);
        graphics.drawString(this.font, "Spell Structure", GRID_X, TOP_Y - 14, HEADER_COLOR);

        if (selectedPaletteWord != null) {
            graphics.drawString(this.font, "Holding: " + selectedPaletteWord.trueName()
                    + " - click a matching slot to place it", PALETTE_X, this.height - 20, 0xFFFFFF55);
        }
    }

    private String pageIndicator() {
        return maxPage() > 0 ? "  (" + (palettePage + 1) + "/" + (maxPage() + 1) + ")" : "";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}