package com.dragonspeech.client.construct;

import com.dragonspeech.client.grid.KnownWordsClientCache;
import com.dragonspeech.client.grid.LastSpellCache;
import com.dragonspeech.network.CastGridSubmitPayload;
import com.dragonspeech.network.ToggleFavoritePayload;
import com.google.gson.JsonArray;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Spell Construction screen: a Word Archive of colored, domain-tinted
 * tiles on the left (searchable, category- AND domain-filterable, with a
 * color legend so the tint means something visible); an UNLIMITED ordered
 * Spell Blueprint on the right; a reserved "Remembered Phrases" zone
 * living in the blueprint panel's own blank space (not a separate tab)
 * for spells saved via SAVE; a Spell Info panel; Cast/Save/Clear.
 *
 * Fully custom-rendered (fills + text + manual hit-testing) rather than
 * stock Button widgets. The search box is the one stock widget kept.
 */
public class SpellConstructionScreen extends Screen {

    // Static: state survives closing/reopening the screen.
    private static final List<KnownWordsClientCache.ClientWordEntry> blueprint = new ArrayList<>();

    private EditBox searchBox;
    private String searchQuery = "";
    private int archivePage = 0;
    // FIX: "when I favorite over the amount it shows, they disappear...
    // I cannot change the page. I can only change the page of the
    // unfavorited words" per explicit direction - confirmed real bug.
    // The favorites strip had a "+N more" indicator but genuinely no
    // way to reach whatever it was hiding - only the archive grid below
    // had a working pager. See computeFavoritePageStarts/
    // layoutFavoriteChips for why this needs its own page-boundary
    // computation rather than a simple page-size divide (favorite chips
    // are variable-width, so how many fit per page isn't a fixed count).
    private int favoritesPage = 0;
    private KnownWordsClientCache.ClientWordEntry hoveredWord;
    private String hoveredSavedName;

    // --- Renaming a Remembered Phrase --------------------------------
    private EditBox renameBox;
    private int renamingIndex = -1;

    private static final String[] CATEGORY_FILTERS = {"all", "verb", "noun_target", "modifier", "scope", "binding", "control"};
    private int categoryFilter = 0;

    // "all" plus every Domain, matched case-insensitively against the word's domain string.
    private static final String[] DOMAIN_FILTERS = {
        "all", "fire", "water", "earth", "air", "life", "death", "mind", "force", "motion", "binding", "truth",
        "time", "gravity", "fate", "void", "weapon"
    };
    private int domainFilter = 0;

    /** The filter tab requested for the enchantment system - a simple on/off toggle rather than a third cyclable value list, since there are only two states worth having ("show everything" / "show only enchantment words"). See KnownWordsClientCache.ClientWordEntry.isEnchantmentWord() for how a word qualifies. */
    private boolean enchantFilterOn = false;

    // --- Layout ---------------------------------------------------------
    private static final int MARGIN = 12;
    private static final int ARCHIVE_W = 250;
    private static final int TILE_W = 76;
    private static final int TILE_H = 30;
    private static final int TILE_GAP = 5;
    private static final int TILES_PER_ROW = 3;
    private static final int TILE_ROWS = 4;
    private static final int TILES_PER_PAGE = TILES_PER_ROW * TILE_ROWS;

    /** Where favorited words live now - a dedicated strip below the pager (previously unused space), instead of mixed into the same paginated grid as everything else. Freed up by dropping the grid from 6 rows to 5. */
    private static final int FAVORITES_STRIP_Y_OFFSET = 20;
    private static final int FAVORITE_CHIP_H = 14;
    /** FIXED BUG: this used to be a single row that silently DROPPED any favorite that didn't fit - not truncated-with-an-indicator, just gone, invisible, with no sign anything was missing. That's exactly what made a favorited "skjoldr" disappear from both the archive (correctly excluded, it's favorited) AND the favorites strip (a real bug) if enough other favorites alphabetically sorted before it filled the one available row. Now wraps to this many rows before finally showing an honest "+N more" instead of silently vanishing. */
    private static final int FAVORITES_MAX_ROWS = 2;
    private static final int FAVORITE_ROW_HEIGHT = FAVORITE_CHIP_H + 3;

    /** Where the live blueprint's word-flow area ends and the Remembered Phrases zone begins - a fixed split, not a dynamic one, so long spells can never collide with the saved list below. */
    private static final int BLUEPRINT_WORDS_HEIGHT = 96;

    // --- Palette --------------------------------------------------------
    private static final int C_BG = 0xE60D0B14;
    private static final int C_PANEL = 0xCC15121E;
    private static final int C_GOLD = 0xFFC9A24B;
    private static final int C_GOLD_DIM = 0xFF7A6230;
    private static final int C_TEXT = 0xFFE8E0CC;
    private static final int C_TEXT_DIM = 0xFF9A917E;

    public SpellConstructionScreen() {
        super(Component.literal("Spell Construction"));
    }

    @Override
    protected void init() {
        super.init();

        for (Button b : com.dragonspeech.client.nav.DragonSpeechTabBar.buildButtons(this.width, com.dragonspeech.client.nav.DragonSpeechTabBar.Tab.SPELL_CONSTRUCTION)) {
            addRenderableWidget(b);
        }

        searchBox = new EditBox(this.font, MARGIN + 8, 44, ARCHIVE_W - 16, 16, Component.literal("search"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            archivePage = 0;
        });
        addRenderableWidget(searchBox);
    }

    // --- Data -----------------------------------------------------------

    private List<KnownWordsClientCache.ClientWordEntry> filteredWords() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        String category = CATEGORY_FILTERS[categoryFilter];
        String domain = DOMAIN_FILTERS[domainFilter];
        return KnownWordsClientCache.get().stream()
            .filter(w -> !w.favorited()) // favorites live in their own strip now - see favoritedWords()
            .filter(w -> category.equals("all") || w.category().equalsIgnoreCase(category))
            .filter(w -> domain.equals("all") || w.domain().equalsIgnoreCase(domain))
            .filter(w -> !enchantFilterOn || w.isEnchantmentWord())
            .filter(w -> query.isEmpty()
                || w.trueName().toLowerCase(Locale.ROOT).contains(query)
                || w.meaning().toLowerCase(Locale.ROOT).contains(query)
                || w.domain().toLowerCase(Locale.ROOT).contains(query)
                || w.category().toLowerCase(Locale.ROOT).contains(query))
            .sorted((a, b) -> a.trueName().compareToIgnoreCase(b.trueName()))
            .toList();
    }

    /** Favorited words, always shown regardless of the archive's own search/category/domain filters - this strip is meant to be quick, always-there access, not something you have to clear your filters to see. */
    private List<KnownWordsClientCache.ClientWordEntry> favoritedWords() {
        return KnownWordsClientCache.get().stream()
            .filter(KnownWordsClientCache.ClientWordEntry::favorited)
            .sorted((a, b) -> a.trueName().compareToIgnoreCase(b.trueName()))
            .toList();
    }

    private int maxPage() {
        return Math.max(0, (filteredWords().size() - 1) / TILES_PER_PAGE);
    }

    /** The same tint used for a word's archive tile and blueprint chip - see DomainColors, shared with GrimoireScreen so the color means the same thing everywhere. */
    private static int domainColor(String domain) {
        return com.dragonspeech.client.grid.DomainColors.of(domain);
    }

    // --- Geometry helpers -------------------------------------------------

    private int tileX(int col) {
        return MARGIN + 8 + col * (TILE_W + TILE_GAP);
    }

    private int tileY(int row) {
        return 96 + row * (TILE_H + TILE_GAP);
    }

    private int blueprintX() {
        return MARGIN + ARCHIVE_W + 16;
    }

    private int blueprintW() {
        return this.width - blueprintX() - MARGIN - 8;
    }

    private int savedZoneY() {
        return 52 + BLUEPRINT_WORDS_HEIGHT;
    }

    private int favoritesStripY() {
        return tileY(TILE_ROWS) + FAVORITES_STRIP_Y_OFFSET;
    }

    // --- Rendering --------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g); // 1.21 blur fix - see GuessScreen/GrimoireScreen for the same pattern

        drawFramedPanel(g, MARGIN - 6, 8, this.width - MARGIN + 6, this.height - 8);
        g.fill(MARGIN, 34, MARGIN + ARCHIVE_W, favoritesStripY() + FAVORITES_MAX_ROWS * FAVORITE_ROW_HEIGHT + 16, C_PANEL);
        g.fill(blueprintX(), 34, blueprintX() + blueprintW(), this.height - 118, C_PANEL);
        g.fill(blueprintX(), this.height - 110, blueprintX() + blueprintW(), this.height - 44, C_PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        pruneUnknownBlueprintWords();
        hoveredWord = null;
        hoveredSavedName = null;
        super.render(g, mouseX, mouseY, partialTick);

        // Title text removed - the tab bar's own active-tab marker now
        // shows this, right at this same y-position.
        renderArchive(g, mouseX, mouseY);
        renderBlueprint(g, mouseX, mouseY);
        renderInfoPanel(g);
        renderButtons(g, mouseX, mouseY);
    }

    private void renderArchive(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(this.font, "\u2727 WORD ARCHIVE \u2727", MARGIN + ARCHIVE_W / 2, 36, C_GOLD);

        // Filter row: [Type: x] and [Domain: x], each cyclable by click.
        String typeLabel = "[Type: " + CATEGORY_FILTERS[categoryFilter] + "]";
        String domLabel = "[Domain: " + DOMAIN_FILTERS[domainFilter] + "]";
        int filterY = 58;
        g.drawString(this.font, typeLabel, MARGIN + 8, filterY,
            isOver(mouseX, mouseY, MARGIN + 8, filterY - 2, this.font.width(typeLabel), 10) ? 0xFFFFF3B0 : C_GOLD);
        int domX = MARGIN + ARCHIVE_W - 8 - this.font.width(domLabel);
        g.drawString(this.font, domLabel, domX, filterY,
            isOver(mouseX, mouseY, domX, filterY - 2, this.font.width(domLabel), 10) ? 0xFFFFF3B0 : C_GOLD);

        // Enchantment filter - a distinct third row rather than folded
        // into Type/Domain, per the explicit "another tab" request.
        String enchantLabel = enchantFilterOn ? "[\u2727 Enchantments Only \u2727]" : "[Show Enchantments Only]";
        int enchantY = filterY + 12;
        int enchantX = MARGIN + ARCHIVE_W / 2 - this.font.width(enchantLabel) / 2;
        g.drawString(this.font, enchantLabel, enchantX, enchantY,
            enchantFilterOn ? 0xFFFFF3B0 : (isOver(mouseX, mouseY, enchantX, enchantY - 2, this.font.width(enchantLabel), 10) ? 0xFFD9A24B : C_TEXT_DIM));

        // The color legend: this is what makes the tile tint a real,
        // learnable sorting signal instead of unexplained decoration.
        int legendY = filterY + 24;
        int lx = MARGIN + 8;
        for (String domain : DOMAIN_FILTERS) {
            if (domain.equals("all")) continue;
            g.fill(lx, legendY, lx + 6, legendY + 6, domainColor(domain));
            lx += 9;
        }
        g.drawString(this.font, "\u2190 domain colors", lx + 4, legendY - 1, C_TEXT_DIM);

        List<KnownWordsClientCache.ClientWordEntry> words = filteredWords();
        int start = archivePage * TILES_PER_PAGE;

        for (int i = start; i < Math.min(start + TILES_PER_PAGE, words.size()); i++) {
            int slot = i - start;
            int x = tileX(slot % TILES_PER_ROW);
            int y = tileY(slot / TILES_PER_ROW);
            KnownWordsClientCache.ClientWordEntry word = words.get(i);

            boolean hovered = mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;
            if (hovered) {
                hoveredWord = word;
            }

            int base = domainColor(word.domain());
            g.fill(x - 1, y - 1, x + TILE_W + 1, y + TILE_H + 1, hovered ? C_GOLD : 0xFF000000);
            g.fill(x, y, x + TILE_W, y + TILE_H, base);
            g.fill(x, y + TILE_H - 3, x + TILE_W, y + TILE_H, 0x55000000);

            String label = word.trueName().toUpperCase(Locale.ROOT); // never favorited here - favorites live in their own strip now, see renderFavoritesStrip
            g.drawCenteredString(this.font, trimTo(label, TILE_W - 8), x + TILE_W / 2, y + 5, C_TEXT);
            g.drawCenteredString(this.font, "[" + word.category() + "]", x + TILE_W / 2, y + 17, C_TEXT_DIM);
        }

        int pagerY = tileY(TILE_ROWS) + 4;
        g.drawCenteredString(this.font, (archivePage + 1) + " / " + (maxPage() + 1), MARGIN + ARCHIVE_W / 2, pagerY, C_TEXT);
        g.drawString(this.font, "\u25C0", MARGIN + 24, pagerY, hoverArrow(mouseX, mouseY, MARGIN + 24, pagerY) ? C_GOLD : C_GOLD_DIM);
        g.drawString(this.font, "\u25B6", MARGIN + ARCHIVE_W - 32, pagerY, hoverArrow(mouseX, mouseY, MARGIN + ARCHIVE_W - 32, pagerY) ? C_GOLD : C_GOLD_DIM);

        renderFavoritesStrip(g, mouseX, mouseY);
    }

    /**
     * Favorited words, separated out from the main archive grid entirely
     * (not just sorted to the front of it) - a compact wrapped row of
     * small chips in the space freed by dropping the grid to 5 rows.
     * Same click behavior as an archive tile: left-click appends to the
     * blueprint, right-click unfavorites.
     */
    private record FavoriteChip(KnownWordsClientCache.ClientWordEntry word, int x, int y, int w) {}

    /**
     * Every page's starting index into the favorites list, computed by
     * simulating the exact same row-wrapping layout logic across the
     * WHOLE list once. Favorite chips are variable-width (sized to each
     * word's own text), so "how many favorites fit per page" isn't a
     * fixed number the way archive tiles are - this is the only
     * reliable way to know where page boundaries actually fall, and it
     * lets both "next" and "prev" (and jumping straight to a page
     * number) all work off the same source of truth rather than only
     * ever being able to go forward.
     */
    private List<Integer> computeFavoritePageStarts(List<KnownWordsClientCache.ClientWordEntry> favorites) {
        List<Integer> starts = new ArrayList<>();
        if (favorites.isEmpty()) {
            starts.add(0);
            return starts;
        }
        int x = MARGIN + 8;
        int row = 0;
        starts.add(0);
        for (int i = 0; i < favorites.size(); i++) {
            int w = this.font.width(favorites.get(i).trueName()) + 8;
            if (x + w > MARGIN + ARCHIVE_W - 8) {
                row++;
                if (row >= FAVORITES_MAX_ROWS) {
                    starts.add(i);
                    row = 0;
                }
                x = MARGIN + 8;
            }
            x += w + 4;
        }
        return starts;
    }

    private int maxFavoritesPage(List<KnownWordsClientCache.ClientWordEntry> favorites) {
        return Math.max(0, computeFavoritePageStarts(favorites).size() - 1);
    }

    /** Shared by render and click-handling so a chip's visible position and its clickable area can never drift apart - the exact bug class that caused the y-increment mistake earlier in this file's history. Lays out only ONE page's worth now, starting from that page's own start index. */
    private List<FavoriteChip> layoutFavoriteChips(List<KnownWordsClientCache.ClientWordEntry> favorites, int page) {
        List<Integer> pageStarts = computeFavoritePageStarts(favorites);
        page = Math.max(0, Math.min(page, pageStarts.size() - 1));
        int startIndex = pageStarts.get(page);
        int endIndex = page + 1 < pageStarts.size() ? pageStarts.get(page + 1) : favorites.size();

        List<FavoriteChip> chips = new ArrayList<>();
        int y0 = favoritesStripY();
        int x = MARGIN + 8;
        int y = y0 + 9;
        int row = 0;
        for (int i = startIndex; i < endIndex; i++) {
            KnownWordsClientCache.ClientWordEntry word = favorites.get(i);
            int w = this.font.width(word.trueName()) + 8;
            if (x + w > MARGIN + ARCHIVE_W - 8) {
                row++;
                x = MARGIN + 8;
                y += FAVORITE_ROW_HEIGHT;
            }
            chips.add(new FavoriteChip(word, x, y, w));
            x += w + 4;
        }
        return chips;
    }

    private void renderFavoritesStrip(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = favoritesStripY();
        g.fill(MARGIN + 6, y0 - 6, MARGIN + ARCHIVE_W - 6, y0 - 5, C_GOLD_DIM); // divider
        g.drawString(this.font, "\u2605 Favorites", MARGIN + 8, y0 - 2, C_GOLD);

        List<KnownWordsClientCache.ClientWordEntry> favorites = favoritedWords();
        if (favorites.isEmpty()) {
            g.drawString(this.font, "Right-click a word to favorite it.", MARGIN + 8, y0 + 10, C_TEXT_DIM);
            return;
        }

        int maxPage = maxFavoritesPage(favorites);
        favoritesPage = Math.max(0, Math.min(favoritesPage, maxPage));

        List<FavoriteChip> chips = layoutFavoriteChips(favorites, favoritesPage);
        for (FavoriteChip chip : chips) {
            boolean hovered = mouseX >= chip.x() && mouseX < chip.x() + chip.w() && mouseY >= chip.y() && mouseY < chip.y() + FAVORITE_CHIP_H;
            if (hovered) {
                hoveredWord = chip.word();
            }
            g.fill(chip.x() - 1, chip.y() - 1, chip.x() + chip.w() + 1, chip.y() + FAVORITE_CHIP_H + 1, hovered ? C_GOLD : 0xFF000000);
            g.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + FAVORITE_CHIP_H, domainColor(chip.word().domain()));
            g.drawCenteredString(this.font, chip.word().trueName(), chip.x() + chip.w() / 2, chip.y() + 3, C_TEXT);
        }

        // Real pagination now, replacing the old "+N more" dead end -
        // only shown once there's actually more than one page, same as
        // the archive's own pager only matters once there's more than
        // one page of results.
        if (maxPage > 0) {
            int pagerY = y0 + 9 + (FAVORITES_MAX_ROWS - 1) * FAVORITE_ROW_HEIGHT + FAVORITE_CHIP_H + 3;
            String label = (favoritesPage + 1) + " / " + (maxPage + 1);
            g.drawCenteredString(this.font, label, MARGIN + ARCHIVE_W / 2, pagerY, C_TEXT_DIM);
            boolean hoverLeft = hoverArrow(mouseX, mouseY, MARGIN + 24, pagerY);
            boolean hoverRight = hoverArrow(mouseX, mouseY, MARGIN + ARCHIVE_W - 32, pagerY);
            g.drawString(this.font, "\u25C0", MARGIN + 24, pagerY, hoverLeft ? C_GOLD : C_GOLD_DIM);
            g.drawString(this.font, "\u25B6", MARGIN + ARCHIVE_W - 32, pagerY, hoverRight ? C_GOLD : C_GOLD_DIM);
        }
    }

    private void renderBlueprint(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = blueprintX();
        int wordsBottom = 52 + BLUEPRINT_WORDS_HEIGHT;
        int blueprintBottom = this.height - 118;
        g.drawCenteredString(this.font, "\u2726 SPELL BLUEPRINT \u2726", x0 + blueprintW() / 2, 36, C_GOLD);

        if (blueprint.isEmpty()) {
            g.drawCenteredString(this.font, "Click words to build your spell - no limit to its length",
                x0 + blueprintW() / 2, 80, C_TEXT_DIM);
        } else {
            int x = x0 + 8;
            int y = 52;
            for (int i = 0; i < blueprint.size(); i++) {
                KnownWordsClientCache.ClientWordEntry word = blueprint.get(i);
                int w = this.font.width(word.trueName()) + 12;

                if (x + w > x0 + blueprintW() - 8) {
                    x = x0 + 8;
                    y += 20;
                }
                if (y > wordsBottom - 18) {
                    g.drawString(this.font, "+" + (blueprint.size() - i) + " more...", x, y, C_TEXT_DIM);
                    break;
                }

                boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
                if (hovered) {
                    hoveredWord = word;
                }

                g.fill(x - 1, y - 1, x + w + 1, y + 17, hovered ? C_GOLD : 0xFF000000);
                g.fill(x, y, x + w, y + 16, domainColor(word.domain()));
                g.drawCenteredString(this.font, word.trueName(), x + w / 2, y + 4, C_TEXT);

                x += w + 6;
            }
        }

        // Reserved zone: Remembered Phrases lives HERE, in the blueprint
        // panel's own blank space, rather than behind a separate tab.
        renderRememberedPhrases(g, mouseX, mouseY, x0, wordsBottom, blueprintBottom);
    }

    /** Wording display is capped to this width before the dot follows it - without a cap, a long spell would push the dot (and description) off the edge of the panel entirely. */
    private static final int MAX_WORDING_WIDTH = 90;

    private void renderRememberedPhrases(GuiGraphics g, int mouseX, int mouseY, int x0, int top, int bottom) {
        g.fill(x0 + 6, top, x0 + blueprintW() - 6, top + 1, C_GOLD_DIM); // divider
        g.drawString(this.font, "\u2727 Remembered Phrases", x0 + 8, top + 5, C_GOLD);

        var saved = SavedSpells.all();
        if (saved.isEmpty()) {
            g.drawString(this.font, "Build a spell and press SAVE to remember it here.", x0 + 8, top + 18, C_TEXT_DIM);
            return;
        }

        int y = top + 18;
        for (int i = 0; i < saved.size(); i++) {
            if (y > bottom - 12) {
                g.drawString(this.font, "+" + (saved.size() - i) + " more...", x0 + 8, y, C_TEXT_DIM);
                break;
            }

            SavedSpells.SavedSpell spell = saved.get(i);
            String wording = trimTo(savedSpellWording(spell), MAX_WORDING_WIDTH);
            int dotX = dotXFor(x0, wording);

            // Ordinary wording is preserved. Phrases containing the generated Word
            // are resolved dynamically so a reshuffle never leaves the old secret
            // visible in this client-side notebook.
            boolean wordingHovered = mouseX >= x0 + 8 && mouseX < dotX - 4 && mouseY >= y && mouseY < y + 11;
            if (wordingHovered) {
                hoveredSavedName = savedSpellWording(spell);
            }
            g.drawString(this.font, wording, x0 + 8, y, wordingHovered ? 0xFFFFF3B0 : C_TEXT);

            // The rename dot - now to the RIGHT of the wording, not the
            // left. Deliberately its own separate hit target from the
            // row's own click-to-recall behavior, so clicking the
            // wording still recalls; only the dot itself opens editing
            // the DESCRIPTION (never the wording - see SavedSpells'
            // own doc on why those are separate fields now).
            boolean dotHovered = mouseX >= dotX && mouseX < dotX + 6 && mouseY >= y + 1 && mouseY < y + 7;
            g.fill(dotX, y + 2, dotX + 4, y + 6, dotHovered ? 0xFFFF6B6B : 0xFFB03A3A);

            if (renamingIndex == i) {
                // The EditBox widget itself renders here (see startRename) - just leave this row's description blank.
                y += 12;
                continue;
            }

            if (!spell.description().isEmpty()) {
                int descMaxWidth = Math.max(20, (x0 + blueprintW() - 8) - (dotX + 8));
                g.drawString(this.font, trimTo(spell.description(), descMaxWidth), dotX + 8, y, C_TEXT_DIM);
            }
            y += 12;
        }
    }

    /** Shared by render and click-handling so the dot's hitbox always matches where it's actually drawn - both need to agree on the SAME wording string's width. */
    private int dotXFor(int x0, String trimmedWording) {
        return x0 + 8 + this.font.width(trimmedWording) + 6;
    }

    /** Opens the inline description box for Remembered Phrase `index`, positioned right after its dot. */
    private void startRename(int index) {
        var saved = SavedSpells.all();
        if (index < 0 || index >= saved.size()) {
            return;
        }
        int x0 = blueprintX();
        int y = savedZoneY() + 18 + index * 12;
        SavedSpells.SavedSpell spell = saved.get(index);
        String wording = trimTo(savedSpellWording(spell), MAX_WORDING_WIDTH);
        int dotX = dotXFor(x0, wording);
        int boxWidth = Math.max(30, (x0 + blueprintW() - 8) - (dotX + 8));

        renamingIndex = index;
        renameBox = new EditBox(this.font, dotX + 8, y - 1, boxWidth, 12, Component.literal("description"));
        renameBox.setMaxLength(64);
        renameBox.setValue(spell.description());
        renameBox.setBordered(false);
        addRenderableWidget(renameBox);
        setFocused(renameBox);
    }

    /** Saves whatever's in the description box (may be blank - that's allowed, it just clears the description) and closes it. Safe to call even when nothing is being edited. */
    private void commitRename() {
        if (renamingIndex < 0 || renameBox == null) {
            return;
        }
        SavedSpells.setDescription(renamingIndex, renameBox.getValue());
        closeRenameBox();
    }

    private void closeRenameBox() {
        if (renameBox != null) {
            removeWidget(renameBox);
            renameBox = null;
        }
        renamingIndex = -1;
    }

    private void renderInfoPanel(GuiGraphics g) {
        int x0 = blueprintX();
        int y0 = this.height - 110;
        g.drawCenteredString(this.font, "\u2727 SPELL INFO \u2727", x0 + blueprintW() / 2, y0 + 4, C_GOLD);

        if (hoveredWord != null) {
            g.drawString(this.font, hoveredWord.trueName(), x0 + 10, y0 + 18, 0xFFFFF3B0);
            g.drawString(this.font, "\"" + trimTo(hoveredWord.meaning(), blueprintW() - 24) + "\"", x0 + 10, y0 + 30, C_TEXT);
            g.drawString(this.font, "Domain: " + hoveredWord.domain()
                + "   Category: " + hoveredWord.category()
                + String.format("   Precision: %.2f", hoveredWord.precision()), x0 + 10, y0 + 44, C_TEXT_DIM);
        } else if (hoveredSavedName != null) {
            g.drawString(this.font, hoveredSavedName, x0 + 10, y0 + 18, 0xFFFFF3B0);
            g.drawString(this.font, "Click to recall into the blueprint - right-click to forget.", x0 + 10, y0 + 30, C_TEXT_DIM);
        } else {
            g.drawString(this.font, "Hover a word or phrase to study it.", x0 + 10, y0 + 24, C_TEXT_DIM);
        }
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        drawGoldButton(g, clearButtonX(), buttonsY(), 90, "CLEAR", isOver(mouseX, mouseY, clearButtonX(), buttonsY(), 90, 22), false);
        drawGoldButton(g, saveButtonX(), buttonsY(), 90, "SAVE", isOver(mouseX, mouseY, saveButtonX(), buttonsY(), 90, 22), false);
        drawGoldButton(g, castButtonX(), buttonsY(), 110, "CONSTRUCT", isOver(mouseX, mouseY, castButtonX(), buttonsY(), 110, 22), true);
    }

    private int buttonsY() {
        return this.height - 36;
    }

    private int clearButtonX() {
        return blueprintX();
    }

    private int saveButtonX() {
        return blueprintX() + (blueprintW() - 90) / 2;
    }

    private int castButtonX() {
        return blueprintX() + blueprintW() - 110;
    }

    private void drawGoldButton(GuiGraphics g, int x, int y, int w, String label, boolean hovered, boolean primary) {
        int fill = primary ? (hovered ? 0xFF6B4FA0 : 0xFF54397E) : (hovered ? 0xFF2E2A38 : 0xFF1E1A28);
        g.fill(x - 1, y - 1, x + w + 1, y + 23, hovered ? C_GOLD : C_GOLD_DIM);
        g.fill(x, y, x + w, y + 22, fill);
        g.drawCenteredString(this.font, label, x + w / 2, y + 7, primary ? 0xFFF0E6FF : C_TEXT);
    }

    private void drawFramedPanel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, C_GOLD_DIM);
        g.fill(x0, y0, x1, y1, C_BG);
    }

    // --- Input ------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 && button != 1) {
            return false;
        }

        // Clicking anywhere else while a rename is open commits it first,
        // rather than losing the edit silently - then falls through to
        // whatever else was actually clicked.
        if (renamingIndex >= 0) {
            commitRename();
        }

        // Filter cycles
        String typeLabel = "[Type: " + CATEGORY_FILTERS[categoryFilter] + "]";
        if (isOver((int) mouseX, (int) mouseY, MARGIN + 8, 56, this.font.width(typeLabel), 10)) {
            categoryFilter = (categoryFilter + 1) % CATEGORY_FILTERS.length;
            archivePage = 0;
            return true;
        }
        String domLabel = "[Domain: " + DOMAIN_FILTERS[domainFilter] + "]";
        int domX = MARGIN + ARCHIVE_W - 8 - this.font.width(domLabel);
        if (isOver((int) mouseX, (int) mouseY, domX, 56, this.font.width(domLabel), 10)) {
            domainFilter = (domainFilter + 1) % DOMAIN_FILTERS.length;
            archivePage = 0;
            return true;
        }
        String enchantLabel = enchantFilterOn ? "[\u2727 Enchantments Only \u2727]" : "[Show Enchantments Only]";
        int enchantX = MARGIN + ARCHIVE_W / 2 - this.font.width(enchantLabel) / 2;
        if (isOver((int) mouseX, (int) mouseY, enchantX, 68, this.font.width(enchantLabel), 10)) {
            enchantFilterOn = !enchantFilterOn;
            archivePage = 0;
            return true;
        }

        // Archive tiles - left-click appends, right-click toggles favorite
        List<KnownWordsClientCache.ClientWordEntry> words = filteredWords();
        int start = archivePage * TILES_PER_PAGE;
        for (int i = start; i < Math.min(start + TILES_PER_PAGE, words.size()); i++) {
            int slot = i - start;
            int x = tileX(slot % TILES_PER_ROW);
            int y = tileY(slot / TILES_PER_ROW);
            if (mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H) {
                if (button == 1) {
                    ClientPlayNetworking.send(new ToggleFavoritePayload(words.get(i).id()));
                } else {
                    blueprint.add(words.get(i));
                }
                return true;
            }
        }

        // Favorites strip - same left-click-appends/right-click-toggles behavior as an archive tile
        if (handleFavoritesStripClick(mouseX, mouseY, button)) {
            return true;
        }

        // Remembered Phrases rename dot - checked BEFORE the row-recall
        // click below, since the dot sits inside the same row's bounds.
        // Position must match renderRememberedPhrases' own dotXFor() call
        // exactly, or the visible dot and its clickable area drift apart.
        int x0 = blueprintX();
        int top = savedZoneY();
        var saved = SavedSpells.all();
        for (int i = 0; i < saved.size(); i++) {
            int y = top + 18 + i * 12;
            String wording = trimTo(savedSpellWording(saved.get(i)), MAX_WORDING_WIDTH);
            int dotX = dotXFor(x0, wording);
            if (mouseX >= dotX && mouseX < dotX + 6 && mouseY >= y + 1 && mouseY < y + 7) {
                startRename(i);
                return true;
            }
        }

        // Remembered Phrases - left-click the WORDING recalls, right-click forgets
        int y = top + 18;
        for (int i = 0; i < saved.size(); i++) {
            String wording = trimTo(savedSpellWording(saved.get(i)), MAX_WORDING_WIDTH);
            int dotX = dotXFor(x0, wording);
            if (mouseX >= x0 + 8 && mouseX < dotX - 4 && mouseY >= y && mouseY < y + 11) {
                if (button == 1) {
                    SavedSpells.delete(i);
                } else {
                    blueprint.clear();
                    for (String id : saved.get(i).wordIds()) {
                        KnownWordsClientCache.get().stream()
                            .filter(w -> w.id().equals(id))
                            .findFirst()
                            .ifPresent(blueprint::add);
                    }
                }
                return true;
            }
            y += 12;
        }

        if (button == 1) {
            return false;
        }

        // Blueprint words - click removes
        int bx = x0 + 8;
        int by = 52;
        int wordsBottom = 52 + BLUEPRINT_WORDS_HEIGHT;
        for (int i = 0; i < blueprint.size(); i++) {
            KnownWordsClientCache.ClientWordEntry word = blueprint.get(i);
            int w = this.font.width(word.trueName()) + 12;
            if (bx + w > x0 + blueprintW() - 8) {
                bx = x0 + 8;
                by += 20;
            }
            if (by > wordsBottom - 18) {
                break;
            }
            if (mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 16) {
                blueprint.remove(i);
                return true;
            }
            bx += w + 6;
        }

        // Pager arrows
        int pagerY = tileY(TILE_ROWS) + 4;
        if (hoverArrow((int) mouseX, (int) mouseY, MARGIN + 24, pagerY)) {
            archivePage = Math.max(0, archivePage - 1);
            return true;
        }
        if (hoverArrow((int) mouseX, (int) mouseY, MARGIN + ARCHIVE_W - 32, pagerY)) {
            archivePage = Math.min(maxPage(), archivePage + 1);
            return true;
        }

        // Buttons
        if (isOver((int) mouseX, (int) mouseY, clearButtonX(), buttonsY(), 90, 22)) {
            blueprint.clear();
            return true;
        }
        if (isOver((int) mouseX, (int) mouseY, saveButtonX(), buttonsY(), 90, 22)) {
            saveCurrentBlueprint();
            return true;
        }
        if (isOver((int) mouseX, (int) mouseY, castButtonX(), buttonsY(), 110, 22)) {
            submitCast();
            return true;
        }

        return false;
    }

    /** Mirrors renderFavoritesStrip's own layout exactly, via the same shared helper - see layoutFavoriteChips. Also handles the favorites pager arrows themselves, mirroring the archive pager's own hoverArrow-based click handling. */
    private boolean handleFavoritesStripClick(double mouseX, double mouseY, int button) {
        List<KnownWordsClientCache.ClientWordEntry> favorites = favoritedWords();
        if (favorites.isEmpty()) {
            return false;
        }

        int maxPage = maxFavoritesPage(favorites);
        if (maxPage > 0) {
            int pagerY = favoritesStripY() + 9 + (FAVORITES_MAX_ROWS - 1) * FAVORITE_ROW_HEIGHT + FAVORITE_CHIP_H + 3;
            if (hoverArrow((int) mouseX, (int) mouseY, MARGIN + 24, pagerY)) {
                favoritesPage = Math.max(0, favoritesPage - 1);
                return true;
            }
            if (hoverArrow((int) mouseX, (int) mouseY, MARGIN + ARCHIVE_W - 32, pagerY)) {
                favoritesPage = Math.min(maxPage, favoritesPage + 1);
                return true;
            }
        }

        for (FavoriteChip chip : layoutFavoriteChips(favorites, favoritesPage)) {
            if (mouseX >= chip.x() && mouseX < chip.x() + chip.w() && mouseY >= chip.y() && mouseY < chip.y() + FAVORITE_CHIP_H) {
                if (button == 1) {
                    ClientPlayNetworking.send(new ToggleFavoritePayload(chip.word().id()));
                } else {
                    blueprint.add(chip.word());
                }
                return true;
            }
        }
        return false;
    }


    /**
     * The generated Word of Words has a stable internal id but a changing
     * plaintext true name. A remembered phrase must therefore never keep
     * displaying the old plaintext after a reshuffle. Resolve phrases that
     * contain the special id from the CURRENT known-word cache; if the player
     * no longer knows the current generation, mask only that slot.
     */
    private String savedSpellWording(SavedSpells.SavedSpell spell) {
        if (!spell.wordIds().contains("dragonspeech:word_of_words")) {
            return spell.name();
        }
        StringBuilder out = new StringBuilder();
        for (String id : spell.wordIds()) {
            String display = KnownWordsClientCache.get().stream()
                .filter(w -> w.id().equals(id))
                .map(KnownWordsClientCache.ClientWordEntry::trueName)
                .findFirst()
                .orElse(id.equals("dragonspeech:word_of_words") ? "[forgotten Word]" : "[unknown]");
            if (!out.isEmpty()) out.append(' ');
            out.append(display);
        }
        return out.toString();
    }

    /**
     * Knowledge can change while this screen is open (notably when the Word
     * of Words is reshuffled). Remove stale blocks immediately instead of
     * leaving a visually usable block that the server would reject.
     */
    private void pruneUnknownBlueprintWords() {
        java.util.Set<String> knownIds = KnownWordsClientCache.get().stream()
            .map(KnownWordsClientCache.ClientWordEntry::id)
            .collect(java.util.stream.Collectors.toSet());
        blueprint.removeIf(word -> !knownIds.contains(word.id()));
    }

    private void saveCurrentBlueprint() {
        if (blueprint.isEmpty()) {
            return;
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < Math.min(4, blueprint.size()); i++) {
            if (i > 0) name.append(' ');
            name.append(blueprint.get(i).trueName());
        }
        if (blueprint.size() > 4) name.append(" ...");
        SavedSpells.save(name.toString(), blueprint.stream().map(KnownWordsClientCache.ClientWordEntry::id).toList());
    }

    private boolean hoverArrow(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x - 4 && mouseX < x + 14 && mouseY >= y - 4 && mouseY < y + 12;
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void submitCast() {
        if (blueprint.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (KnownWordsClientCache.ClientWordEntry word : blueprint) {
            array.add(word.id());
        }
        String payload = array.toString();
        LastSpellCache.remember(payload);
        ClientPlayNetworking.send(new CastGridSubmitPayload(payload));
        onClose();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renamingIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRenameBox(); // deliberately does NOT call commitRename - Escape discards the edit
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
