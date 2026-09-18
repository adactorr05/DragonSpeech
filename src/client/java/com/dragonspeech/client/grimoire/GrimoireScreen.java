package com.dragonspeech.client.grimoire;

import com.dragonspeech.client.grid.DomainColors;
import com.dragonspeech.client.grid.KnownWordsClientCache;
import com.dragonspeech.network.ToggleFavoritePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The grimoire, now paginated (same fix as the casting grid - long word
 * lists no longer silently truncate) with hover tooltips and favorites
 * sorted to the top of the list.
 */
public class GrimoireScreen extends Screen {

    private EditBox searchBox;
    private String searchQuery = "";
    private String selectedId;
    private int page = 0;

    private static final int LIST_X = 20;
    private static final int LEGEND_Y = 44;
    private static final int LIST_TOP = 56;
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 210;
    private static final int DETAIL_X = 250;
    private static final int SKILLS_X = 460;
    /** Stacked BELOW the skills list, same column - only 7 skills exist so ~100px of headroom is comfortably more than that list ever needs, avoiding a 4th horizontal column (narrower-screen risk, already flagged once for SKILLS_X). */
    private static final int TRUE_NAMES_Y_OFFSET = 232;
    private static final int WORDS_PER_PAGE = 10;

    private static final int PANEL_COLOR = 0x88101018;
    private static final int HEADER_COLOR = 0xFFE8C878;

    public GrimoireScreen() {
        super(Component.literal("Grimoire"));
    }

    @Override
    protected void init() {
        super.init();

        for (Button b : com.dragonspeech.client.nav.DragonSpeechTabBar.buildButtons(this.width, com.dragonspeech.client.nav.DragonSpeechTabBar.Tab.GRIMOIRE)) {
            addRenderableWidget(b);
        }

        searchBox = new EditBox(this.font, LIST_X, 28, LIST_WIDTH, 20, Component.literal("search"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(searchBox);

        layoutList();
        layoutDetailPanel();
        layoutTrueNames();
    }

    private void rebuild() {
        clearWidgets();
        for (Button b : com.dragonspeech.client.nav.DragonSpeechTabBar.buildButtons(this.width, com.dragonspeech.client.nav.DragonSpeechTabBar.Tab.GRIMOIRE)) {
            addRenderableWidget(b);
        }
        addRenderableWidget(searchBox);
        layoutList();
        layoutDetailPanel();
        layoutTrueNames();
    }

    private List<KnownWordsClientCache.ClientWordEntry> filteredWords() {
        String query = searchQuery.trim().toLowerCase();
        return KnownWordsClientCache.get().stream()
            .filter(w -> !w.id().equals("dragonspeech:word_of_words"))
            .filter(w -> query.isEmpty()
                || w.trueName().toLowerCase().contains(query)
                || w.meaning().toLowerCase().contains(query))
            .sorted((a, b) -> Boolean.compare(b.favorited(), a.favorited())) // favorites first
            .collect(Collectors.toList());
    }

    private int maxPage() {
        return Math.max(0, (filteredWords().size() - 1) / WORDS_PER_PAGE);
    }

    private void layoutList() {
        List<KnownWordsClientCache.ClientWordEntry> words = filteredWords();
        int start = page * WORDS_PER_PAGE;
        int end = Math.min(start + WORDS_PER_PAGE, words.size());

        int y = LIST_TOP;
        for (int i = start; i < end; i++) {
            KnownWordsClientCache.ClientWordEntry word = words.get(i);
            String star = word.favorited() ? "\u2605 " : "";
            // Indented past LIST_X so render()'s domain-color square (drawn
            // separately, since Button can't easily host a leading swatch)
            // has room to sit in front of the label without overlapping it.
            addRenderableWidget(Button.builder(
                Component.literal(star + word.trueName() + "  [" + word.category() + "]"),
                button -> {
                    selectedId = word.id();
                    rebuild();
                }
            ).bounds(LIST_X + 10, y, LIST_WIDTH - 10, ROW_HEIGHT - 2)
             .tooltip(Tooltip.create(Component.literal("\"" + word.meaning() + "\"")))
             .build());
            y += ROW_HEIGHT;
        }

        int pagerY = LIST_TOP + (WORDS_PER_PAGE * ROW_HEIGHT) + 4;
        if (maxPage() > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(LIST_X, pagerY, 30, 20).build());

            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = Math.min(maxPage(), page + 1);
                rebuild();
            }).bounds(LIST_X + LIST_WIDTH - 30, pagerY, 30, 20).build());
        }
    }

    private void layoutDetailPanel() {
        KnownWordsClientCache.ClientWordEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }

        int y = LIST_TOP + 96;
        addRenderableWidget(Button.builder(
            Component.literal(selected.favorited() ? "\u2605 Unfavorite" : "\u2606 Favorite"),
            button -> ClientPlayNetworking.send(new ToggleFavoritePayload(selected.id()))
            // Server flips the real state and pushes a fresh sync - the
            // client never assumes its own guess about the new state.
        ).bounds(DETAIL_X, y, 130, 20).build());
    }

    /**
     * One button per unlocked true-name-progress entry (see
     * TrueNameDuelHooks/ClientTrueNameCache) - clicking opens the actual
     * guessing screen (TrueNameGuessingScreen). Shows the SCRAMBLED
     * collected letters until solved, then the reassembled real name
     * instead - exactly the "2 areas... reassembles" split asked for,
     * just as one row that swaps its own text rather than two separate
     * always-visible areas, to keep this list compact.
     */
    private void layoutTrueNames() {
        int x = SKILLS_X;
        int y = LIST_TOP + TRUE_NAMES_Y_OFFSET + 10;
        var wow = KnownWordsClientCache.get().stream()
            .filter(w -> w.id().equals("dragonspeech:word_of_words"))
            .findFirst().orElse(null);
        if (wow != null) {
            addRenderableWidget(Button.builder(
                Component.literal(trimToWidth("Word of Words: " + wow.trueName(), 178)),
                button -> { selectedId = wow.id(); rebuild(); }
            ).bounds(x, y, 178, 16)
             .tooltip(Tooltip.create(Component.literal(wow.meaning())))
             .build());
            y += 18;
        }
        for (var entry : com.dragonspeech.client.grimoire.ClientTrueNameCache.get()) {
            String label = entry.solved()
                ? entry.targetName() + ": " + entry.solvedName()
                : entry.targetName() + " [" + scrambledDisplay(entry.collectedLetters()) + "]";
            addRenderableWidget(Button.builder(
                Component.literal(trimToWidth(label, 178)),
                button -> Minecraft.getInstance().setScreen(new TrueNameGuessingScreen(this, entry.targetId(), entry.targetName()))
            ).bounds(x, y, 178, 16).build());
            y += 18;
            if (y > this.height - 20) {
                break; // genuinely out of room - matches this project's own established "stop rather than overflow" pattern elsewhere
            }
        }
    }

    /** Letters separated for readability - "veilof" collected so far reads as "v e i l o f", not a run-together blob. */
    private static String scrambledDisplay(String collected) {
        if (collected.isEmpty()) {
            return "no letters yet";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < collected.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(collected.charAt(i));
        }
        return sb.toString();
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String result = text;
        while (!result.isEmpty() && this.font.width(result + ellipsis) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private KnownWordsClientCache.ClientWordEntry selectedEntry() {
        if (selectedId == null) {
            return null;
        }
        return KnownWordsClientCache.get().stream()
            .filter(w -> w.id().equals(selectedId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics); // skip 1.21's blur post-process
        graphics.fill(LIST_X - 6, 22, LIST_X + LIST_WIDTH + 6, LIST_TOP + (WORDS_PER_PAGE * ROW_HEIGHT) + 30, PANEL_COLOR);
        graphics.fill(DETAIL_X - 6, LIST_TOP - 6, DETAIL_X + 200, LIST_TOP + 122, PANEL_COLOR);
        graphics.fill(DETAIL_X - 6, LIST_TOP + 128, DETAIL_X + 200, this.height - 12, PANEL_COLOR);
        graphics.fill(SKILLS_X - 6, LIST_TOP + 128, SKILLS_X + 190, this.height - 12, PANEL_COLOR);
        graphics.fill(SKILLS_X - 6, LIST_TOP + TRUE_NAMES_Y_OFFSET - 6, SKILLS_X + 190, this.height - 12, PANEL_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title text removed - the tab bar's own active-tab marker
        // ("▪ Grimoire") now shows this, and it renders at this same
        // y-position, so keeping both would have overlapped.

        // The color legend - the same swatches used on every word's tile in
        // Spell Construction, so the tint reads as a real sorting signal
        // instead of unexplained decoration.
        int lx = LIST_X;
        for (String domain : DomainColors.ALL_DOMAINS) {
            graphics.fill(lx, LEGEND_Y, lx + 6, LEGEND_Y + 6, DomainColors.of(domain));
            lx += 9;
        }
        graphics.drawString(this.font, "\u2190 domains", lx + 4, LEGEND_Y - 1, 0xFF9A917E);

        // A small color square in front of each visible row, matching its word's domain.
        List<KnownWordsClientCache.ClientWordEntry> words = filteredWords();
        int start = page * WORDS_PER_PAGE;
        int end = Math.min(start + WORDS_PER_PAGE, words.size());
        int rowY = LIST_TOP;
        for (int i = start; i < end; i++) {
            graphics.fill(LIST_X + 1, rowY + 6, LIST_X + 7, rowY + 12, DomainColors.of(words.get(i).domain()));
            rowY += ROW_HEIGHT;
        }

        if (maxPage() > 0) {
            graphics.drawString(this.font, "Page " + (page + 1) + "/" + (maxPage() + 1),
                LIST_X + 40, LIST_TOP + (WORDS_PER_PAGE * ROW_HEIGHT) + 10, 0xFFAAAAAA);
        }

        KnownWordsClientCache.ClientWordEntry selected = selectedEntry();
        if (selected != null) {
            int x = DETAIL_X;
            int y = LIST_TOP;
            graphics.drawString(this.font, selected.trueName(), x, y, 0xFFFFFF55);
            graphics.drawString(this.font, "\"" + selected.meaning() + "\"", x, y + 14, 0xFFCCCCCC);
            graphics.drawString(this.font, "Category: " + selected.category(), x, y + 32, 0xFFAAAAAA);
            graphics.fill(x, y + 44, x + 6, y + 50, DomainColors.of(selected.domain()));
            graphics.drawString(this.font, " Domain: " + selected.domain(), x + 6, y + 44, 0xFFAAAAAA);
            graphics.drawString(this.font, String.format("Precision: %.2f", selected.precision()), x, y + 56, 0xFFAAAAAA);
            graphics.drawString(this.font, "Learned via: " + selected.discoveryMethod(), x, y + 68, 0xFFAAAAAA);
        } else {
            graphics.drawString(this.font, "Select a word to study it.", DETAIL_X, LIST_TOP, 0xFF888888);
        }

        renderScarsAndWards(graphics);
        renderSkills(graphics);
        renderTrueNamesHeader(graphics);
    }

    private void renderTrueNamesHeader(GuiGraphics graphics) {
        int x = SKILLS_X;
        int y = LIST_TOP + TRUE_NAMES_Y_OFFSET;
        graphics.drawString(this.font, "\u2727 True Names \u2727", x, y, HEADER_COLOR);
        boolean knowsWow = KnownWordsClientCache.get().stream().anyMatch(w -> w.id().equals("dragonspeech:word_of_words"));
        if (com.dragonspeech.client.grimoire.ClientTrueNameCache.get().isEmpty() && !knowsWow) {
            graphics.drawString(this.font, "Win a mind duel or discover a great true name.", x, y + 12, 0xFF888888);
        }
    }

    /**
     * Learned skills (PlayerSkills, synced via SkillsSyncPayload) shown
     * beside Scars & Wards - same vertical position, one column further
     * right. LAYOUT RISK WORTH FLAGGING: SKILLS_X is a fixed pixel
     * offset like every other position in this screen already is, so it
     * follows the existing convention rather than introducing a new
     * responsive-layout approach - but that also means on a narrow
     * window or high GUI scale, this column could run past the actual
     * screen edge. Nothing else in this file guards against that
     * either, so this isn't a new risk, just one worth knowing about if
     * it turns out to matter at your resolution.
     */
    private void renderSkills(GuiGraphics graphics) {
        int x = SKILLS_X;
        int y = LIST_TOP + 132;
        graphics.drawString(this.font, "\u2727 Skills Learned \u2727", x, y, HEADER_COLOR);
        y += 12;

        var skills = com.dragonspeech.client.grimoire.ClientSkillsCache.get();
        if (skills.isEmpty()) {
            graphics.drawString(this.font, "No skills learned yet.", x, y, 0xFF888888);
            return;
        }
        for (var skill : skills) {
            if (y > this.height - 16) {
                graphics.drawString(this.font, "...", x, y, 0xFF888888);
                break;
            }
            graphics.drawString(this.font, "\u2022 " + trimTo(skill.label(), 180), x, y, 0xFF88CC99);
            y += 11;
        }
    }

    private void renderScarsAndWards(GuiGraphics graphics) {
        int x = DETAIL_X;
        int y = LIST_TOP + 132;
        graphics.drawString(this.font, "\u2727 Scars & Wards \u2727", x, y, HEADER_COLOR);
        y += 12;

        var scars = com.dragonspeech.client.scar.ClientScarCache.get();
        if (scars.isEmpty()) {
            graphics.drawString(this.font, "No scars carried.", x, y, 0xFF888888);
            y += 11;
        } else {
            for (String description : scars) {
                if (y > this.height - 24) break;
                graphics.drawString(this.font, "\u2022 " + trimTo(description, 195), x, y, 0xFFCC8888);
                y += 11;
            }
        }

        y += 4;
        var wards = com.dragonspeech.client.ward.ClientWardCache.get();
        if (wards.isEmpty()) {
            graphics.drawString(this.font, "No wards active.", x, y, 0xFF888888);
        } else {
            for (var ward : wards) {
                if (y > this.height - 16) break;
                int barW = Math.max(2, Math.round(60 * Math.max(0f, Math.min(1f, ward.fraction()))));
                graphics.drawString(this.font, "\u2022 Ward: " + ward.type(), x, y, 0xFF88CCFF);
                graphics.fill(x + 2, y + 11, x + 62, y + 14, 0xFF2A2A2A);
                graphics.fill(x + 2, y + 11, x + 2 + barW, y + 14, 0xFF6FA8DC);
                y += 16;
            }
        }
    }

    private String trimTo(String text, int maxPixels) {
        if (this.font.width(text) <= maxPixels) {
            return text;
        }
        while (!text.isEmpty() && this.font.width(text + "..") > maxPixels) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
