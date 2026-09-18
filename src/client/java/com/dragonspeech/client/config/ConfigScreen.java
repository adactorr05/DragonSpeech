package com.dragonspeech.client.config;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.dragonspeech.compat.SentienceConfig;
import com.dragonspeech.config.DragonSpeechConfig;
import com.dragonspeech.network.ConfigRequestPayload;
import com.dragonspeech.network.ConfigUpdatePayload;
import com.dragonspeech.network.ReloadSentiencePayload;
import com.dragonspeech.network.ResetAllConfigPayload;
import com.dragonspeech.network.ResetServerTabPayload;

/**
 * The DragonSpeech config GUI. Reachable 3 ways per explicit direction:
 * "/dragonspeechconfig" (client command, see DragonSpeechClient),
 * a button on the Title Screen (see TitleScreenConfigButtonMixin), and
 * ModMenu if it's installed (see DragonSpeechModMenuIntegration).
 *
 * TWO TABS:
 *   CLIENT - purely local (particles, HUD, camera feel, tooltips). Always visible, always editable,
 *            never touches the network at all - see DragonSpeechClientConfig.
 *   SERVER - world-wide settings (see DragonSpeechConfig). ALWAYS visible and editable from the
 *            Title Screen (edits your own local config/dragonspeech.json directly, same as any other
 *            "default config" screen). Once actually connected to a world/server, switches to the
 *            SYNCED, permission-gated path instead and only stays visible if THAT connection
 *            confirms this player is op level 4 - see isConnected()/canShowServerTab().
 *
 * Magic Difficulty's ACTIVE selection is still read-only here (only set via Create World or hand-
 * editing the file) - but per later direction, what each of EASY/NORMAL/HARD actually MEANS is now
 * editable via DifficultyTuningScreen (opened from a button on this tab).
 *
 * RESET BUTTONS (added per explicit direction): "Reset This Page" resets only whichever tab is
 * currently open (Client tab -> DragonSpeechClientConfig only; Server tab -> the 10 GUI-added extra
 * fields only, NOT difficulty tuning or sentience overrides, which have their own independent resets
 * on their own screens). "Reset All Configs" is the universal reset - client settings (always local)
 * plus every server-side area at once (extras, difficulty tuning, sentience overrides).
 *
 * LIVE SLIDERS (fixed per bug report): FloatSlider now takes a label FORMATTER (a function from the
 * live dragged value to display text) instead of a frozen pre-formatted string, so updateMessage() -
 * which vanilla's AbstractSliderButton already calls continuously during a drag - actually produces a
 * different string each time instead of resetting back to the same stale one. Previously the number
 * only ever appeared to update after a full rebuild() (e.g. scrolling or switching tabs), which is
 * exactly the bug that was reported.
 *
 * SCROLL CLAMPING (fixed per bug report): scrollOffset previously had a lower bound of 0 but NO upper
 * bound at all, so scrolling past the end of a tab's content just kept sliding everything further and
 * further up with nothing to stop it - overlapping the tab bar/reset/done row on the way, then
 * disappearing off the top of the panel entirely. maxScroll is now computed from each tab's real
 * content height every rebuild() and enforced in both directions.
 */
public class ConfigScreen extends Screen {

    private enum Tab { CLIENT, SERVER }

    private static final int PANEL_W = 360;
    private static final int ROW_H = 24;
    private static final int CONTENT_TOP = 56;
    private static final int BOTTOM_ROW_H = 46; // reset row (20px) + gap + done row (20px)

    private static final int C_BG = 0xE8141018;
    private static final int C_BORDER = 0xFF4A4238;
    private static final int C_TEXT = 0xFFE8DEC8;
    private static final int C_TEXT_DIM = 0xFFA89A80;

    private final Screen parent;
    private Tab activeTab = Tab.CLIENT;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int panelX;
    private int panelY;
    private int panelH;

    /** Local mirror of the Server tab, edited live and pushed to the server on every change - avoids re-parsing ServerConfigClientCache mid-drag. */
    private boolean editAllowRebondAfterDeath;
    private float editFoundHeartMin;
    private float editFoundHeartMax;
    private float editRegenExtra;
    private float editCostExtra;
    private float editBacklashSeverity;
    private boolean editAllowPvpDuels;
    private boolean editAllowControlOfPlayers;
    private float editWordLootChance;
    private float editEggHatchSpeed;
    private float editAiComputeBudget; // stored as float for slider math, always an integer value in practice

    private final List<String> serverInfoLines = new ArrayList<>();
    private int serverInfoY = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Dragon Speech - Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelH = Math.min(this.height - 40, 420);
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - panelH) / 2;

        // Only ask the server for anything if we're actually connected to one - the Title Screen has
        // no connection at all, and ConfigRequestPayload would have nowhere to go.
        if (Minecraft.getInstance().player != null) {
            ClientPlayNetworking.send(new ConfigRequestPayload());
        }
        loadEditStateFromCache();
        if (activeTab == Tab.SERVER && !canShowServerTab()) {
            activeTab = Tab.CLIENT;
        }
        rebuild();
    }

    private boolean canShowServerTab() {
        // Offline (Title Screen) - always available, edits your own local config file directly, same
        // as any other "default config" screen. Online - only if THIS connection's server confirmed
        // op level 4 for this player.
        return !isConnected() || (ServerConfigClientCache.hasData() && ServerConfigClientCache.canEdit());
    }

    private boolean isConnected() {
        return Minecraft.getInstance().player != null;
    }

    private void loadEditStateFromCache() {
        if (isConnected()) {
            editAllowRebondAfterDeath = ServerConfigClientCache.allowRebondAfterDeath();
            editFoundHeartMin = ServerConfigClientCache.foundHeartStaminaMin();
            editFoundHeartMax = ServerConfigClientCache.foundHeartStaminaMax();
            editRegenExtra = ServerConfigClientCache.regenMultiplierExtra();
            editCostExtra = ServerConfigClientCache.costMultiplierExtra();
            editBacklashSeverity = ServerConfigClientCache.backlashSeverityMultiplier();
            editAllowPvpDuels = ServerConfigClientCache.allowPvpMindDuels();
            editAllowControlOfPlayers = ServerConfigClientCache.allowControlOfPlayers();
            editWordLootChance = ServerConfigClientCache.wordLootChanceMultiplier();
            editEggHatchSpeed = ServerConfigClientCache.eggHatchSpeedMultiplier();
            editAiComputeBudget = ServerConfigClientCache.aiComputeBudget();
        } else {
            // Offline - read straight from DragonSpeechConfig's own in-memory state (already loaded
            // from config/dragonspeech.json at mod init) rather than the network cache, which has
            // nothing in it yet with no connection to have synced from.
            editAllowRebondAfterDeath = DragonSpeechConfig.allowRebondAfterDeath();
            editFoundHeartMin = DragonSpeechConfig.foundHeartStaminaMin();
            editFoundHeartMax = DragonSpeechConfig.foundHeartStaminaMax();
            editRegenExtra = DragonSpeechConfig.regenMultiplierExtra();
            editCostExtra = DragonSpeechConfig.costMultiplierExtra();
            editBacklashSeverity = DragonSpeechConfig.backlashSeverityMultiplier();
            editAllowPvpDuels = DragonSpeechConfig.allowPvpMindDuels();
            editAllowControlOfPlayers = DragonSpeechConfig.allowControlOfPlayers();
            editWordLootChance = DragonSpeechConfig.wordLootChanceMultiplier();
            editEggHatchSpeed = DragonSpeechConfig.eggHatchSpeedMultiplier();
            editAiComputeBudget = DragonSpeechConfig.aiComputeBudget();
        }
    }

    /** Called after a fresh ConfigSyncPayload arrives while this screen is open - see DragonSpeechClient. */
    public void onServerSyncReceived() {
        loadEditStateFromCache();
        if (activeTab == Tab.SERVER && !canShowServerTab()) {
            activeTab = Tab.CLIENT;
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        serverInfoLines.clear();

        int tabW = canShowServerTab() ? PANEL_W / 2 - 2 : PANEL_W;
        addRenderableWidget(tip(Button.builder(Component.literal("Client"), b -> switchTab(Tab.CLIENT))
                .bounds(panelX + 4, panelY + 22, tabW, 20).build(),
                "Local-only settings - visuals, performance, HUD. Never sent to a server."));
        if (canShowServerTab()) {
            addRenderableWidget(tip(Button.builder(Component.literal("Server"), b -> switchTab(Tab.SERVER))
                    .bounds(panelX + 4 + tabW + 4, panelY + 22, tabW, 20).build(),
                    "World-wide settings. From the Title Screen this edits your own local defaults; a real server's own settings always take over once you connect."));
        }

        int startY = panelY + CONTENT_TOP - scrollOffset;
        int y = startY;
        int rowX = panelX + 12;
        int rowW = PANEL_W - 24;

        if (activeTab == Tab.CLIENT) {
            y = clientRow(y, rowX, rowW, "Particle Density", "x", DragonSpeechClientConfig.particleDensity(), 0.0f, 3.0f,
                    v -> DragonSpeechClientConfig.setParticleDensity(v),
                    "How many particles spell effects spawn. Lower this to cut down on visual clutter or help performance in big fights.");

            addRowWidget(y, tip(CycleButton.onOffBuilder(DragonSpeechClientConfig.staminaHudEnabled())
                    .create(rowX, y, rowW, 20, Component.literal("Stamina HUD"),
                            (btn, val) -> DragonSpeechClientConfig.setStaminaHudEnabled(val)),
                    "Shows or hides the gold stamina bar above your hunger bar."));
            y += ROW_H;

            addRowWidget(y, tip(CycleButton.<DragonSpeechClientConfig.HudPosition>builder(pos -> Component.literal(prettyEnum(pos.name())))
                    .withValues(DragonSpeechClientConfig.HudPosition.values())
                    .withInitialValue(DragonSpeechClientConfig.staminaHudPosition())
                    .create(rowX, y, rowW, 20, Component.literal("Stamina HUD Position"),
                            (btn, val) -> DragonSpeechClientConfig.setStaminaHudPosition(val)),
                    "Which corner of the screen the stamina bar is drawn in."));
            y += ROW_H;

            y = clientRow(y, rowX, rowW, "Casting Grid Opacity", "", DragonSpeechClientConfig.castingGridOpacity(), 0.1f, 1.0f,
                    v -> DragonSpeechClientConfig.setCastingGridOpacity(v),
                    "How see-through the background panel behind the Casting Grid (spellcasting) screen is.");

            addRowWidget(y, tip(CycleButton.onOffBuilder(DragonSpeechClientConfig.wardRingVisible())
                    .create(rowX, y, rowW, 20, Component.literal("Ward Ring Visual"),
                            (btn, val) -> DragonSpeechClientConfig.setWardRingVisible(val)),
                    "Shows or hides the rotating ring around a warded entity. Purely cosmetic - the ward itself still works the same either way."));
            y += ROW_H;

            addRowWidget(y, tip(CycleButton.onOffBuilder(DragonSpeechClientConfig.contactBeamVisible())
                    .create(rowX, y, rowW, 20, Component.literal("Contact Beam Visual"),
                            (btn, val) -> DragonSpeechClientConfig.setContactBeamVisible(val)),
                    "Shows or hides the traveling beam while you reach out with your mind toward someone. Purely cosmetic."));
            y += ROW_H;

            y = clientRow(y, rowX, rowW, "Dragon Camera Roll", "x", DragonSpeechClientConfig.dragonCameraRollIntensity(), 0.0f, 2.0f,
                    v -> DragonSpeechClientConfig.setDragonCameraRollIntensity(v),
                    "How much the camera banks/tilts while riding a flying dragon. Set to 0 to disable it entirely, e.g. if it causes motion sickness.");

            addRowWidget(y, tip(CycleButton.onOffBuilder(DragonSpeechClientConfig.enchantTooltipVerbose())
                    .create(rowX, y, rowW, 20, Component.literal("Verbose Enchant Tooltips"),
                            (btn, val) -> DragonSpeechClientConfig.setEnchantTooltipVerbose(val)),
                    "Shows full details (ward durability, blessing level) on item tooltips instead of just the enchantment's name."));
            y += ROW_H;
        } else {
            // Read-only Magic Difficulty breakdown - text only, no widget. Rendered in render() from
            // serverInfoLines (drawn starting at serverInfoY, one line per 10px) so it scrolls together
            // with everything else on this tab. Reads straight from DragonSpeechConfig when offline
            // (Title Screen - no sync to read from) or from ServerConfigClientCache when connected.
            serverInfoY = y;
            String diffName = isConnected() ? ServerConfigClientCache.difficulty() : DragonSpeechConfig.difficulty().name();
            float diffHealth = isConnected() ? ServerConfigClientCache.difficultyMinSurvivableHealth() : DragonSpeechConfig.minSurvivableHealth();
            float diffRegen = isConnected() ? ServerConfigClientCache.difficultyRegenMultiplier() : DragonSpeechConfig.regenMultiplier();
            float diffCost = isConnected() ? ServerConfigClientCache.difficultyCostMultiplier() : DragonSpeechConfig.costMultiplier();
            float diffSpacing = isConnected() ? ServerConfigClientCache.difficultyStructureSpacingMultiplier() : DragonSpeechConfig.structureSpacingMultiplier();
            serverInfoLines.add("Magic Difficulty: " + diffName + " (set via World Creation, not here)");
            serverInfoLines.add("  Health floor: " + fmt(diffHealth / 2f) + " hearts (0 = overdraft can kill you)");
            serverInfoLines.add("  Stamina regen: x" + fmt(diffRegen));
            serverInfoLines.add("  Spell cost: x" + fmt(diffCost));
            serverInfoLines.add("  Structure rarity: x" + fmt(diffSpacing) + " (higher = rarer)");
            if (!isConnected()) {
                serverInfoLines.add("These are your LOCAL defaults - a real server's own settings always win once you connect.");
            }
            y += serverInfoLines.size() * 10 + 6;

            addRowWidget(y, tip(Button.builder(Component.literal("Edit Magic Difficulty Rules"),
                    b -> Minecraft.getInstance().setScreen(new DifficultyTuningScreen(this)))
                    .bounds(rowX, y, rowW, 20).build(),
                    "Redefine what Easy/Normal/Hard actually do - health floor, regen/cost multipliers, and structure rarity, per tier. Which tier is currently active is still only changed via World Creation."));
            y += ROW_H;

            addRowWidget(y, tip(CycleButton.onOffBuilder(editAllowRebondAfterDeath)
                    .create(rowX, y, rowW, 20, Component.literal("Allow Rebond After Death"),
                            (btn, val) -> { editAllowRebondAfterDeath = val; pushServerUpdate(); }),
                    "Whether a player may bond a new dragon after their previous bonded dragon has died. A player can only ever have 1 bonded dragon alive at a time regardless of this setting."));
            y += ROW_H;

            y = serverRow(y, rowX, rowW, "Found Heart Stamina Min", "", editFoundHeartMin, 100f, 2000f,
                    v -> { editFoundHeartMin = v; pushServerUpdate(); },
                    "The lower end of the random stamina range rolled for a Dragon Heart with no living source dragon (found in the world, or obtained in creative).", true);
            y = serverRow(y, rowX, rowW, "Found Heart Stamina Max", "", editFoundHeartMax, 100f, 2000f,
                    v -> { editFoundHeartMax = v; pushServerUpdate(); },
                    "The upper end of that same random stamina range.", true);

            y = serverRow(y, rowX, rowW, "Regen Multiplier", "x, on top of difficulty", editRegenExtra, 0.1f, 5.0f,
                    v -> { editRegenExtra = v; pushServerUpdate(); },
                    "An extra stamina regen multiplier, layered on top of whatever Magic Difficulty already applies. 1.0 = no extra change.", false);
            y = serverRow(y, rowX, rowW, "Spell Cost Multiplier", "x, on top of difficulty", editCostExtra, 0.1f, 5.0f,
                    v -> { editCostExtra = v; pushServerUpdate(); },
                    "An extra spell cost multiplier, layered on top of whatever Magic Difficulty already applies. 1.0 = no extra change.", false);
            y = serverRow(y, rowX, rowW, "Backlash Severity", "x", editBacklashSeverity, 0.0f, 5.0f,
                    v -> { editBacklashSeverity = v; pushServerUpdate(); },
                    "How harsh the stamina/health penalty is for a wrong word guess. 0 removes the penalty entirely.", false);

            addRowWidget(y, tip(CycleButton.onOffBuilder(editAllowPvpDuels)
                    .create(rowX, y, rowW, 20, Component.literal("Allow PvP Mind Duels"),
                            (btn, val) -> { editAllowPvpDuels = val; pushServerUpdate(); }),
                    "Whether a player may Reach Out and start a Mind Duel against another real player. Duels against mobs are never affected by this."));
            y += ROW_H;

            addRowWidget(y, tip(CycleButton.onOffBuilder(editAllowControlOfPlayers)
                    .create(rowX, y, rowW, 20, Component.literal("Allow Controlling Players"),
                            (btn, val) -> { editAllowControlOfPlayers = val; pushServerUpdate(); }),
                    "Whether winning a Mind Duel against another player lets you take remote Control of their body. Off by default - this is a much bigger deal than simply dueling."));
            y += ROW_H;

            y = serverRow(y, rowX, rowW, "Word Loot Chance", "x, needs reload", editWordLootChance, 0.0f, 3.0f,
                    v -> { editWordLootChance = v; pushServerUpdate(); },
                    "Multiplies how often word tablets and scholar's fragments appear in loot. Takes effect the next time loot tables reload (world load, or /reload).", false);
            y = serverRow(y, rowX, rowW, "Egg Hatch Speed", "x", editEggHatchSpeed, 0.1f, 5.0f,
                    v -> { editEggHatchSpeed = v; pushServerUpdate(); },
                    "Multiplies how fast dragon eggs hatch on average.", false);

            y = row(y, rowX, rowW, v -> "AI Compute Budget (" + (Math.round(v) <= 0 ? "Unlimited" : Math.round(v)) + ")",
                    editAiComputeBudget, 0f, 500f,
                    v -> { editAiComputeBudget = Math.round(v); pushServerUpdate(); },
                    "Caps how many mind-duel AI actor-decisions run per pulse, server-wide - applies to the built-in heuristic AND any addon-registered AI alike. 0 = unlimited.");

            addRowWidget(y, tip(Button.builder(Component.literal("Edit Sentience Tiers"),
                    b -> Minecraft.getInstance().setScreen(new SentienceEditorScreen(this)))
                    .bounds(rowX, y, rowW, 20).build(),
                    "Opens the full mob list - assign a mind-strength tier and/or a numeric reaction-speed override to any mob, vanilla or modded."));
            y += ROW_H;

            addRowWidget(y, tip(Button.builder(Component.literal("Reload Sentience Config"),
                    b -> {
                        if (isConnected()) {
                            ClientPlayNetworking.send(new ReloadSentiencePayload());
                        } else {
                            SentienceConfig.bootstrap();
                        }
                    })
                    .bounds(rowX, y, rowW, 20).build(),
                    "Re-reads config/dragonspeech/sentience.json immediately, without needing to restart the server."));
            y += ROW_H;

            // Addon buttons - one per registered com.dragonspeech.client.api.DragonSpeechAddonScreens
            // entry (e.g. a Neural Network addon's own editor). ConfigScreen never references any
            // addon's class by name - if nothing's registered, this loop simply adds nothing, and the
            // Server tab looks exactly like it does today.
            for (var entry : com.dragonspeech.client.api.DragonSpeechAddonScreens.entries()) {
                addRowWidget(y, tip(Button.builder(Component.literal(entry.buttonLabel()),
                        b -> Minecraft.getInstance().setScreen(entry.screenFactory().apply(this)))
                        .bounds(rowX, y, rowW, 20).build(),
                        entry.tooltip()));
                y += ROW_H;
            }
        }

        // Real content height for THIS tab, independent of the current scrollOffset (it cancels out
        // in the subtraction below) - see this class's own doc for why this fixes the scroll bug.
        int contentHeight = y - startY;
        int visibleHeight = panelH - CONTENT_TOP - BOTTOM_ROW_H;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            rebuild();
            return;
        }

        addRenderableWidget(tip(Button.builder(Component.literal("Reset This Page"), b -> resetThisPage())
                .bounds(panelX + 8, panelY + panelH - 48, PANEL_W / 2 - 12, 20).build(),
                activeTab == Tab.CLIENT
                        ? "Restores every Client-tab setting to its default. Does not touch Server-tab settings."
                        : "Restores this tab's own settings to their defaults. Does not touch Magic Difficulty rules or sentience overrides - those reset separately on their own screens."));
        addRenderableWidget(tip(Button.builder(Component.literal("Reset All Configs"), b -> resetAllConfigs())
                .bounds(panelX + PANEL_W / 2 + 4, panelY + panelH - 48, PANEL_W / 2 - 12, 20).build(),
                "The universal reset - restores EVERYTHING: Client tab, Server tab, Magic Difficulty rules for all 3 tiers, and every sentience override."));

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + PANEL_W / 2 - 50, panelY + panelH - 24, 100, 20).build());
    }

    private int clientRow(int y, int rowX, int rowW, String labelPrefix, String suffix, float value, float min, float max, java.util.function.Consumer<Float> onCommit, String tooltip) {
        Function<Float, String> formatter = v -> labelPrefix + " (" + fmt(v) + suffix + ")";
        addRowWidget(y, tip(new FloatSlider(rowX, y, rowW, 20, formatter, value, min, max, onCommit), tooltip));
        return y + ROW_H;
    }

    /** wholeNumbers=true rounds the displayed value instead of showing 2 decimal places (used for the heart-stamina rows, which are always whole numbers in practice). */
    private int serverRow(int y, int rowX, int rowW, String labelPrefix, String suffix, float value, float min, float max, java.util.function.Consumer<Float> onCommit, String tooltip, boolean wholeNumbers) {
        Function<Float, String> formatter = wholeNumbers
                ? v -> labelPrefix + " (" + Math.round(v) + (suffix.isEmpty() ? "" : " " + suffix) + ")"
                : v -> labelPrefix + " (" + fmt(v) + suffix + ")";
        addRowWidget(y, tip(new FloatSlider(rowX, y, rowW, 20, formatter, value, min, max, onCommit), tooltip));
        return y + ROW_H;
    }

    /** Generic form for rows whose display text isn't a simple "prefix (value suffix)" shape - e.g. AI Compute Budget's "Unlimited" special-case at 0. */
    private int row(int y, int rowX, int rowW, Function<Float, String> formatter, float value, float min, float max, java.util.function.Consumer<Float> onCommit, String tooltip) {
        addRowWidget(y, tip(new FloatSlider(rowX, y, rowW, 20, formatter, value, min, max, onCommit), tooltip));
        return y + ROW_H;
    }

    private static <T extends AbstractWidget> T tip(T widget, String text) {
        widget.setTooltip(Tooltip.create(Component.literal(text)));
        return widget;
    }

    /**
     * THE ACTUAL FIX for "buttons overlap then continue... out of sight" (the bottom half of that bug
     * report): maxScroll alone only stops the SCROLL POSITION from going too far - it never stopped a
     * row's WIDGET from being added to the screen in the first place when its computed y fell outside
     * the visible content window (e.g. at scrollOffset=0 with more rows than fit). Every scrollable
     * row now goes through addRowWidget() below, which simply skips adding the widget at all when its
     * row isn't currently within the visible area - the row still occupies its normal ROW_H of space
     * for layout/scroll-math purposes, it just isn't drawn or clickable until scrolled into view. Tab
     * buttons, Reset buttons, and Done are unaffected - they're fixed-position and never go through
     * this check.
     */
    private boolean isRowVisible(int y) {
        int top = panelY + CONTENT_TOP - 4;
        int bottom = panelY + panelH - BOTTOM_ROW_H;
        return y + 20 > top && y < bottom;
    }

    private void addRowWidget(int y, AbstractWidget widget) {
        if (isRowVisible(y)) {
            addRenderableWidget(widget);
        }
    }

    private void switchTab(Tab tab) {
        activeTab = tab;
        scrollOffset = 0;
        rebuild();
    }

    /** "Reset This Page" - only the currently active tab. See this class's own doc for exactly what each tab's reset does and doesn't touch. */
    private void resetThisPage() {
        if (activeTab == Tab.CLIENT) {
            DragonSpeechClientConfig.resetToDefaults();
        } else if (isConnected()) {
            ClientPlayNetworking.send(new ResetServerTabPayload());
        } else {
            DragonSpeechConfig.resetServerExtrasToDefaults();
            loadEditStateFromCache();
        }
        rebuild();
    }

    /** "Reset All Configs" - the universal reset. Client settings always reset locally (they're never server data); everything else follows the same offline/online split as every other Server-tab edit. */
    private void resetAllConfigs() {
        DragonSpeechClientConfig.resetToDefaults();
        if (isConnected()) {
            ClientPlayNetworking.send(new ResetAllConfigPayload());
        } else {
            DragonSpeechConfig.resetServerExtrasToDefaults();
            DragonSpeechConfig.resetDifficultyTuningToDefaults();
            SentienceConfig.clearAllOverrides();
            loadEditStateFromCache();
        }
        rebuild();
    }

    /**
     * Applies the WHOLE current local Server-tab state - matches the "full state on every change"
     * convention. OFFLINE (Title Screen): writes straight to DragonSpeechConfig via its own setters
     * (each already clamps + saves to config/dragonspeech.json individually - see that class). ONLINE:
     * sends it to the server instead, which re-validates permission before applying anything - a
     * client is never trusted to enforce that on its own, even though the UI already hides this tab
     * from anyone the server hasn't confirmed as op level 4.
     */
    private void pushServerUpdate() {
        if (!isConnected()) {
            DragonSpeechConfig.setAllowRebondAfterDeath(editAllowRebondAfterDeath);
            DragonSpeechConfig.setFoundHeartStaminaMin(editFoundHeartMin);
            DragonSpeechConfig.setFoundHeartStaminaMax(editFoundHeartMax);
            DragonSpeechConfig.setRegenMultiplierExtra(editRegenExtra);
            DragonSpeechConfig.setCostMultiplierExtra(editCostExtra);
            DragonSpeechConfig.setBacklashSeverityMultiplier(editBacklashSeverity);
            DragonSpeechConfig.setAllowPvpMindDuels(editAllowPvpDuels);
            DragonSpeechConfig.setAllowControlOfPlayers(editAllowControlOfPlayers);
            DragonSpeechConfig.setWordLootChanceMultiplier(editWordLootChance);
            DragonSpeechConfig.setEggHatchSpeedMultiplier(editEggHatchSpeed);
            DragonSpeechConfig.setAiComputeBudget((int) editAiComputeBudget);
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("allow_rebond_after_death", editAllowRebondAfterDeath);
        root.addProperty("found_heart_stamina_min", editFoundHeartMin);
        root.addProperty("found_heart_stamina_max", editFoundHeartMax);
        root.addProperty("regen_multiplier_extra", editRegenExtra);
        root.addProperty("cost_multiplier_extra", editCostExtra);
        root.addProperty("backlash_severity_multiplier", editBacklashSeverity);
        root.addProperty("allow_pvp_mind_duels", editAllowPvpDuels);
        root.addProperty("allow_control_of_players", editAllowControlOfPlayers);
        root.addProperty("word_loot_chance_multiplier", editWordLootChance);
        root.addProperty("egg_hatch_speed_multiplier", editEggHatchSpeed);
        root.addProperty("ai_compute_budget", (int) editAiComputeBudget);
        ClientPlayNetworking.send(new ConfigUpdatePayload(root.toString()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // FIX: previously only clamped at 0 (no upper bound at all), so scrolling past the end of a
        // tab's content just kept going indefinitely - overlapping the tab bar/reset/done row on the
        // way, then vanishing off the top of the panel. maxScroll (recomputed from real content height
        // every rebuild()) now bounds both directions.
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * ROW_H)));
        rebuild();
        return true;
    }

    /**
     * THE REAL FIX (matches DragonBondScreen's own documented fix for the exact same bug):
     * Screen.render() calls this.renderBackground(...) internally, which calls
     * renderBlurredBackground() -> gameRenderer.processBlurEffect(). Simply not calling
     * this.renderBackground(...) from OUR OWN render() below isn't enough - render()'s own
     * super.render() call (needed to actually draw the buttons/sliders in the renderables list)
     * independently calls this.renderBackground() again on its own, reintroducing the exact
     * blur that was supposed to be removed. Overriding renderBackground() itself as a no-op
     * stops it at the source regardless of which path calls it - the plain solid fill in
     * render() below is this screen's only background now.
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Our own plain solid overlay - renderBackground() above is now a no-op, so this is the
        // only background this screen draws. No blur, matching this mod's other screens.
        graphics.fill(0, 0, this.width, this.height, 0xFF0A0808);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + panelH + 2, C_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, C_BG);
        graphics.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 6, C_TEXT);

        // Clip the scrolling info text to the content area so it disappears behind the reset/done row
        // rather than drawing over it while scrolled. maxScroll now also keeps the WIDGETS themselves
        // from ever reaching this far in the first place (see rebuild()'s own doc), so this scissor is
        // now a belt-and-suspenders safety net for the text specifically, not the only thing keeping
        // content contained.
        graphics.enableScissor(panelX, panelY + CONTENT_TOP - 14, panelX + PANEL_W, panelY + panelH - BOTTOM_ROW_H - 4);
        int lineY = serverInfoY;
        for (String line : serverInfoLines) {
            graphics.drawString(this.font, line, panelX + 12, lineY, C_TEXT_DIM, false);
            lineY += 10;
        }
        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String prettyEnum(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null;
    }

    /**
     * Generic float slider - same shape as DragonBondScreen's own LimiterSlider, generalized with
     * min/max/a label FORMATTER (fixed per bug report - see this class's own top-level doc for why a
     * frozen string wasn't enough to keep the displayed number live during a drag).
     */
    private static class FloatSlider extends AbstractSliderButton {
        private final Function<Float, String> labelFormatter;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> onCommit;

        FloatSlider(int x, int y, int w, int h, Function<Float, String> labelFormatter, float initial, float min, float max, java.util.function.Consumer<Float> onCommit) {
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
            // Recomputed from the LIVE dragged value every time vanilla calls this (continuously
            // during a drag) - this is the actual live-update fix, not just a cosmetic rename.
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
