package com.dragonspeech.client.config;

import com.dragonspeech.compat.SentienceConfig;
import com.dragonspeech.config.DragonSpeechConfig;
import com.dragonspeech.network.ConfigRequestPayload;
import com.dragonspeech.network.ConfigUpdatePayload;
import com.dragonspeech.network.ReloadSentiencePayload;
import com.dragonspeech.network.ResetAllConfigPayload;
import com.dragonspeech.network.ResetServerTabPayload;
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

/**
 * Dragon Speech's main configuration screen.
 *
 * The underlying config model is deliberately unchanged: CLIENT values are local-only and SERVER
 * values still follow the existing offline-direct / online-permission-gated network path. This class
 * is now only responsible for presenting those settings clearly: grouped sections, readable labels
 * and descriptions, compact controls, visible Client/Server separation, bounded scrolling, and a
 * fixed footer that never collides with scrolling content.
 */
public class ConfigScreen extends Screen {

    private enum Tab { CLIENT, SERVER }

    private static final int MAX_PANEL_W = 690;
    private static final int MAX_PANEL_H = 530;
    private static final int HEADER_H = 86;
    private static final int FOOTER_H = 56;
    private static final int ROW_H = 48;
    private static final int SECTION_H = 32;

    // The config screen is intentionally its own visual space rather than a reskinned vanilla list.
    // Client uses cold arcane cyan; Server uses warm ward-gold, so the active scope is readable at
    // a glance even before reading the tab label.
    private static final int C_SCREEN = 0xFF05060A;
    private static final int C_PANEL = 0xF20B0D14;
    private static final int C_PANEL_INNER = 0xF5121520;
    private static final int C_HEADER = 0xF6171A26;
    private static final int C_BORDER = 0xFF3A4052;
    private static final int C_ROW = 0xC0131722;
    private static final int C_ROW_ALT = 0xC0181B27;
    private static final int C_ROW_HOVER = 0xE0222837;
    private static final int C_TEXT = 0xFFF4F1E8;
    private static final int C_TEXT_DIM = 0xFF9EA8BA;
    private static final int C_CLIENT = 0xFF55D8FF;
    private static final int C_SERVER = 0xFFFFC761;
    private static final int C_DANGER = 0xFFFF7D83;

    private record RowVisual(int y, int height, String label, String description, boolean alternate) {}
    private record SectionVisual(int y, String title, String subtitle) {}
    private record InfoLine(int y, String text, int color) {}

    private final Screen parent;
    private Tab activeTab = Tab.CLIENT;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentTop;
    private int contentBottom;
    private int scrollOffset;
    private int maxScroll;

    private final List<RowVisual> rows = new ArrayList<>();
    private final List<SectionVisual> sections = new ArrayList<>();
    private final List<InfoLine> infoLines = new ArrayList<>();

    // Local mirror of the Server tab, pushed through the same existing config/network path.
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
    private boolean editBonusChestDragonEgg;
    private float editAiComputeBudget;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Dragon Speech Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(MAX_PANEL_W, Math.max(300, this.width - 24));
        panelH = Math.min(MAX_PANEL_H, Math.max(220, this.height - 20));
        panelW = Math.min(panelW, Math.max(1, this.width - 8));
        panelH = Math.min(panelH, Math.max(1, this.height - 8));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        contentTop = panelY + HEADER_H;
        contentBottom = panelY + panelH - FOOTER_H;

        if (Minecraft.getInstance().player != null) {
            ClientPlayNetworking.send(new ConfigRequestPayload());
        }
        loadEditStateFromCache();
        if (activeTab == Tab.SERVER && !canShowServerTab()) activeTab = Tab.CLIENT;
        rebuild();
    }

    private boolean isConnected() {
        return Minecraft.getInstance().player != null;
    }

    private boolean canShowServerTab() {
        return !isConnected() || (ServerConfigClientCache.hasData() && ServerConfigClientCache.canEdit());
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
            editBonusChestDragonEgg = ServerConfigClientCache.bonusChestDragonEggEnabled();
            editAiComputeBudget = ServerConfigClientCache.aiComputeBudget();
        } else {
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
            editBonusChestDragonEgg = DragonSpeechConfig.bonusChestDragonEggEnabled();
            editAiComputeBudget = DragonSpeechConfig.aiComputeBudget();
        }
    }

    /** Called when the server answers ConfigRequestPayload while this screen is open. */
    public void onServerSyncReceived() {
        loadEditStateFromCache();
        if (activeTab == Tab.SERVER && !canShowServerTab()) activeTab = Tab.CLIENT;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        rows.clear();
        sections.clear();
        infoLines.clear();

        int innerX = panelX + 14;
        int innerW = panelW - 28;
        int tabGap = 6;
        int tabW = (innerW - tabGap) / 2;
        int tabY = panelY + 52;

        Button clientTab = Button.builder(
                Component.literal((activeTab == Tab.CLIENT ? "◆ " : "") + "CLIENT  //  THIS DEVICE"),
                b -> switchTab(Tab.CLIENT))
            .bounds(innerX, tabY, tabW, 24).build();
        clientTab.setTooltip(Tooltip.create(Component.literal("Visuals, HUD, camera, and interface settings stored only on this client.")));
        addRenderableWidget(clientTab);

        Button serverTab = Button.builder(
                Component.literal((activeTab == Tab.SERVER ? "◆ " : "") + "SERVER  //  WORLD RULES"),
                b -> switchTab(Tab.SERVER))
            .bounds(innerX + tabW + tabGap, tabY, tabW, 24).build();
        serverTab.active = canShowServerTab();
        serverTab.setTooltip(Tooltip.create(Component.literal(canShowServerTab()
            ? "World-wide Dragon Speech rules. Online editing requires server operator permission."
            : "Server settings are visible only after this server confirms operator permission.")));
        addRenderableWidget(serverTab);

        int y = contentTop - scrollOffset;
        int controlW = Math.max(150, Math.min(210, innerW / 3));
        int controlX = panelX + panelW - 14 - controlW;
        int textX = innerX + 10;
        int textW = Math.max(120, controlX - textX - 14);

        if (activeTab == Tab.CLIENT) {
            y = addSection(y, "Visual Effects", "Control how dense and prominent spell visuals are.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Particle Density", "Amount of spell particles. Lower values reduce clutter and GPU load.",
                DragonSpeechClientConfig.particleDensity(), 0f, 3f,
                v -> fmt(v) + "×", DragonSpeechClientConfig::setParticleDensity,
                "0 disables most optional particles; 1 is normal; higher values make effects denser.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Casting Grid Opacity", "Transparency of the panel behind the spell-construction grid.",
                DragonSpeechClientConfig.castingGridOpacity(), .1f, 1f,
                v -> Math.round(v * 100f) + "%", DragonSpeechClientConfig::setCastingGridOpacity,
                "Higher values make the Casting Grid background more opaque.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Ward Ring Visual", "Show the rotating ring around warded entities.",
                DragonSpeechClientConfig.wardRingVisible(), DragonSpeechClientConfig::setWardRingVisible,
                "Purely cosmetic. Wards still function when this is disabled.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Mind Contact Beam", "Show the traveling beam while reaching toward another mind.",
                DragonSpeechClientConfig.contactBeamVisible(), DragonSpeechClientConfig::setContactBeamVisible,
                "Purely cosmetic; it does not change reach speed or mechanics.");

            y = addSection(y, "HUD & Camera", "Personal display and dragon-riding comfort settings.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Stamina HUD", "Display your current magical stamina on screen.",
                DragonSpeechClientConfig.staminaHudEnabled(), DragonSpeechClientConfig::setStaminaHudEnabled,
                "Shows or hides the stamina bar.");
            y = addEnumRow(y, textX, textW, controlX, controlW,
                "HUD Position", "Choose which corner holds the stamina display.",
                DragonSpeechClientConfig.staminaHudPosition(), DragonSpeechClientConfig.HudPosition.values(),
                DragonSpeechClientConfig::setStaminaHudPosition,
                "Moves the stamina HUD without changing its behavior.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Dragon Camera Roll", "How strongly the camera banks while riding a flying dragon.",
                DragonSpeechClientConfig.dragonCameraRollIntensity(), 0f, 2f,
                v -> fmt(v) + "×", DragonSpeechClientConfig::setDragonCameraRollIntensity,
                "Set to 0 to disable camera banking completely.");

            y = addSection(y, "Interface", "Information shown in menus and item tooltips.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Detailed Enchant Tooltips", "Show durability, levels, and other Dragon Speech enchant details.",
                DragonSpeechClientConfig.enchantTooltipVerbose(), DragonSpeechClientConfig::setEnchantTooltipVerbose,
                "Disable for compact enchantment names only.");
        } else {
            y = addSection(y, "Magic Difficulty", "Current world tier and the rules that tier applies.");
            y = addDifficultyCard(y, textX, textW, controlX, controlW);

            y = addSection(y, "Dragons & Progression", "Bonding, Dragon Hearts, eggs, and world progression.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Allow Rebond After Death", "Allow a new bond after the previous bonded dragon has died.",
                editAllowRebondAfterDeath, v -> { editAllowRebondAfterDeath = v; pushServerUpdate(); },
                "A player still cannot have more than one living bonded dragon at a time.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Found Heart Stamina · Minimum", "Lowest stamina roll for Dragon Hearts without a living source dragon.",
                editFoundHeartMin, 100f, 2000f, v -> Integer.toString(Math.round(v)),
                v -> { editFoundHeartMin = v; pushServerUpdate(); },
                "Applies to found/creative hearts that cannot inherit a source dragon's real stamina.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Found Heart Stamina · Maximum", "Highest stamina roll for those source-less Dragon Hearts.",
                editFoundHeartMax, 100f, 2000f, v -> Integer.toString(Math.round(v)),
                v -> { editFoundHeartMax = v; pushServerUpdate(); },
                "Must remain meaningful relative to the minimum; values are clamped by the config layer.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Egg Hatch Speed", "Global multiplier for how quickly dragon eggs progress toward hatching.",
                editEggHatchSpeed, .1f, 5f, v -> fmt(v) + "×",
                v -> { editEggHatchSpeed = v; pushServerUpdate(); },
                "1.00× is the normal configured hatch speed.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Bonus Chest Dragon Egg", "Put one random Dragon Speech egg in a world-start bonus chest.",
                editBonusChestDragonEgg, v -> { editBonusChestDragonEgg = v; pushServerUpdate(); },
                "Only matters when Minecraft's Bonus Chest option is enabled for the world.");

            y = addSection(y, "Magic Balance", "Global stamina economy, backlash, and discovery pacing.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Stamina Regeneration", "Extra multiplier layered on top of the active difficulty tier.",
                editRegenExtra, .1f, 5f, v -> fmt(v) + "×",
                v -> { editRegenExtra = v; pushServerUpdate(); },
                "1.00× means no additional change beyond difficulty.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Spell Cost", "Extra multiplier applied to every normal Dragon Speech cast.",
                editCostExtra, .1f, 5f, v -> fmt(v) + "×",
                v -> { editCostExtra = v; pushServerUpdate(); },
                "1.00× means no additional change beyond difficulty.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Backlash Severity", "Scales the penalty when a caster overreaches or fails dangerously.",
                editBacklashSeverity, 0f, 3f, v -> fmt(v) + "×",
                v -> { editBacklashSeverity = v; pushServerUpdate(); },
                "0 disables the extra backlash penalty; 1.00× is normal.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "Word Loot Chance", "How often word tablets and scholar fragments appear in loot.",
                editWordLootChance, 0f, 3f, v -> fmt(v) + "×",
                v -> { editWordLootChance = v; pushServerUpdate(); },
                "Loot-table changes take effect after a world load or /reload.");

            y = addSection(y, "Mind Magic", "Player-vs-player mind rules and AI workload limits.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Allow PvP Mind Duels", "Permit players to Reach Out and start Mind Duels against other players.",
                editAllowPvpDuels, v -> { editAllowPvpDuels = v; pushServerUpdate(); },
                "Mind Duels against mobs are not affected.");
            y = addToggleRow(y, textX, textW, controlX, controlW,
                "Allow Controlling Players", "Permit a successful player Mind Duel to grant remote body control.",
                editAllowControlOfPlayers, v -> { editAllowControlOfPlayers = v; pushServerUpdate(); },
                "This is separate from merely allowing the duel itself.");
            y = addSliderRow(y, textX, textW, controlX, controlW,
                "AI Compute Budget", "Maximum mind-duel AI decisions processed per pulse across the server.",
                editAiComputeBudget, 0f, 500f,
                v -> Math.round(v) <= 0 ? "Unlimited" : Integer.toString(Math.round(v)),
                v -> { editAiComputeBudget = Math.round(v); pushServerUpdate(); },
                "0 is unlimited. Lower caps can reduce worst-case server load in very large fights.");

            y = addSection(y, "Advanced", "Detailed rules and addon-provided configuration.");
            y = addActionRow(y, textX, textW, controlX, controlW,
                "Sentience Tiers", "Assign mind strength and reaction-speed overrides to individual mob types.",
                "Open Editor", b -> Minecraft.getInstance().setScreen(new SentienceEditorScreen(this)),
                "Opens the full sentience editor.");
            y = addActionRow(y, textX, textW, controlX, controlW,
                "Reload Sentience Config", "Re-read config/dragonspeech/sentience.json without restarting.",
                "Reload Now", b -> {
                    if (isConnected()) ClientPlayNetworking.send(new ReloadSentiencePayload());
                    else SentienceConfig.bootstrap();
                }, "Reloads sentience overrides immediately.");

            for (var entry : com.dragonspeech.client.api.DragonSpeechAddonScreens.entries()) {
                y = addActionRow(y, textX, textW, controlX, controlW,
                    entry.buttonLabel(), "Configuration supplied by an installed Dragon Speech addon.",
                    "Open", b -> Minecraft.getInstance().setScreen(entry.screenFactory().apply(this)), entry.tooltip());
            }
        }

        int contentHeight = y - (contentTop - scrollOffset);
        int visibleHeight = contentBottom - contentTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight + 6);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            rebuild();
            return;
        }

        addFooterButtons();
    }

    private int addSection(int y, String title, String subtitle) {
        sections.add(new SectionVisual(y, title, subtitle));
        return y + SECTION_H;
    }

    private int addSliderRow(int y, int textX, int textW, int controlX, int controlW,
                             String label, String description, float value, float min, float max,
                             Function<Float, String> valueFormatter, java.util.function.Consumer<Float> onCommit,
                             String tooltip) {
        addRowVisual(y, label, description);
        if (isRowVisible(y)) {
            addRenderableWidget(tip(new FloatSlider(controlX, y + 11, controlW, 20, valueFormatter, value, min, max, onCommit), tooltip));
        }
        return y + ROW_H;
    }

    private int addToggleRow(int y, int textX, int textW, int controlX, int controlW,
                             String label, String description, boolean value,
                             java.util.function.Consumer<Boolean> onCommit, String tooltip) {
        addRowVisual(y, label, description);
        if (isRowVisible(y)) {
            Button button = Button.builder(Component.literal(value ? "Enabled" : "Disabled"), b -> {
                onCommit.accept(!value);
                rebuild();
            }).bounds(controlX, y + 11, controlW, 20).build();
            addRenderableWidget(tip(button, tooltip));
        }
        return y + ROW_H;
    }

    private <T> int addEnumRow(int y, int textX, int textW, int controlX, int controlW,
                               String label, String description, T current, T[] values,
                               java.util.function.Consumer<T> onCommit, String tooltip) {
        addRowVisual(y, label, description);
        if (isRowVisible(y)) {
            CycleButton<T> cycle = CycleButton.<T>builder(v -> Component.literal(prettyEnum(v.toString())))
                .withValues(values)
                .withInitialValue(current)
                .create(controlX, y + 11, controlW, 20, Component.literal("Position"),
                    (btn, val) -> onCommit.accept(val));
            addRenderableWidget(tip(cycle, tooltip));
        }
        return y + ROW_H;
    }

    private int addActionRow(int y, int textX, int textW, int controlX, int controlW,
                             String label, String description, String buttonText,
                             Button.OnPress onPress, String tooltip) {
        addRowVisual(y, label, description);
        if (isRowVisible(y)) {
            addRenderableWidget(tip(Button.builder(Component.literal(buttonText), onPress)
                .bounds(controlX, y + 11, controlW, 20).build(), tooltip));
        }
        return y + ROW_H;
    }

    private int addDifficultyCard(int y, int textX, int textW, int controlX, int controlW) {
        String diffName = isConnected() ? ServerConfigClientCache.difficulty() : DragonSpeechConfig.difficulty().name();
        float diffHealth = isConnected() ? ServerConfigClientCache.difficultyMinSurvivableHealth() : DragonSpeechConfig.minSurvivableHealth();
        float diffRegen = isConnected() ? ServerConfigClientCache.difficultyRegenMultiplier() : DragonSpeechConfig.regenMultiplier();
        float diffCost = isConnected() ? ServerConfigClientCache.difficultyCostMultiplier() : DragonSpeechConfig.costMultiplier();
        float diffSpacing = isConnected() ? ServerConfigClientCache.difficultyStructureSpacingMultiplier() : DragonSpeechConfig.structureSpacingMultiplier();

        int h = 78;
        rows.add(new RowVisual(y, h, "Difficulty: " + prettyEnum(diffName),
            "Active tier is selected during world creation. The button opens the rules behind each tier.", false));
        infoLines.add(new InfoLine(y + 37, "Health floor: " + fmt(diffHealth / 2f) + " hearts  ·  Regen: " + fmt(diffRegen) + "×  ·  Cost: " + fmt(diffCost) + "×", C_TEXT_DIM));
        infoLines.add(new InfoLine(y + 50, "Structure rarity: " + fmt(diffSpacing) + "×" + (!isConnected() ? "  ·  local defaults" : ""), C_TEXT_DIM));
        if (isRowVisible(y, h)) {
            addRenderableWidget(tip(Button.builder(Component.literal("Edit Tier Rules"),
                b -> Minecraft.getInstance().setScreen(new DifficultyTuningScreen(this)))
                .bounds(controlX, y + 11, controlW, 20).build(),
                "Redefine Easy/Normal/Hard health floor, stamina regeneration, spell cost, and structure rarity."));
        }
        return y + h;
    }

    private void addRowVisual(int y, String label, String description) {
        rows.add(new RowVisual(y, ROW_H, label, description, (rows.size() & 1) == 1));
    }

    private void addFooterButtons() {
        int gap = 6;
        int innerX = panelX + 14;
        int innerW = panelW - 28;
        int buttonW = (innerW - gap * 2) / 3;
        int y = panelY + panelH - 32;

        addRenderableWidget(tip(Button.builder(Component.literal("Reset Page"), b -> resetThisPage())
            .bounds(innerX, y, buttonW, 22).build(),
            activeTab == Tab.CLIENT
                ? "Reset only Client settings to defaults."
                : "Reset the main Server-tab settings. Difficulty and sentience editors keep their own separate resets."));
        addRenderableWidget(tip(Button.builder(Component.literal("Reset Everything"), b -> resetAllConfigs())
            .bounds(innerX + buttonW + gap, y, buttonW, 22).build(),
            "Reset Client settings plus all server extras, difficulty tuning, and sentience overrides."));
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(innerX + (buttonW + gap) * 2, y, buttonW, 22).build());
    }

    private boolean isRowVisible(int y) {
        return isRowVisible(y, ROW_H);
    }

    private boolean isRowVisible(int y, int height) {
        return y + height > contentTop && y < contentBottom;
    }

    private static <T extends AbstractWidget> T tip(T widget, String text) {
        widget.setTooltip(Tooltip.create(Component.literal(text)));
        return widget;
    }

    private void switchTab(Tab tab) {
        if (tab == Tab.SERVER && !canShowServerTab()) return;
        activeTab = tab;
        scrollOffset = 0;
        rebuild();
    }

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
            DragonSpeechConfig.setBonusChestDragonEggEnabled(editBonusChestDragonEgg);
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
        root.addProperty("bonus_chest_dragon_egg_enabled", editBonusChestDragonEgg);
        root.addProperty("ai_compute_budget", (int) editAiComputeBudget);
        ClientPlayNetworking.send(new ConfigUpdatePayload(root.toString()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(scrollY * 30.0)));
        rebuild();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally no vanilla blur. render() below supplies the complete background.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        int accent = activeTab == Tab.CLIENT ? C_CLIENT : C_SERVER;

        renderArcaneBackdrop(graphics, now, accent);

        // Multi-layer frame: dark glass center, faint outer glow, bright scope rail.
        glowRect(graphics, panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, accent, 26);
        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, C_BORDER);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        graphics.fill(panelX + 5, panelY + 5, panelX + panelW - 5, panelY + panelH - 5, C_PANEL_INNER);
        graphics.fill(panelX + 5, panelY + 5, panelX + panelW - 5, panelY + HEADER_H - 5, C_HEADER);

        // Scope rail + animated pulse travelling down it.
        graphics.fill(panelX + 5, panelY + 5, panelX + 8, panelY + panelH - 5, withAlpha(accent, 180));
        int railSpan = Math.max(1, panelH - 28);
        int pulseY = panelY + 14 + (int) ((now / 10L) % railSpan);
        graphics.fill(panelX + 4, pulseY - 5, panelX + 9, pulseY + 5, withAlpha(accent, 220));

        // Header identity. The sigil is deliberately geometry-only so no external texture pack is
        // required and it remains crisp at every GUI scale.
        drawArcaneSigil(graphics, panelX + 31, panelY + 26, 15, accent, now);
        graphics.drawString(this.font, "DRAGON SPEECH", panelX + 56, panelY + 13, C_TEXT, false);
        graphics.drawString(this.font, "CONFIGURATION MATRIX", panelX + 56, panelY + 26, withAlpha(accent, 235), false);
        String subtitle = activeTab == Tab.CLIENT
            ? "LOCAL CHANNEL  ·  visuals, HUD, camera and interface"
            : (isConnected()
                ? "WORLD CHANNEL  ·  authoritative rules synced from this server"
                : "WORLD CHANNEL  ·  defaults for worlds hosted by this installation");
        graphics.drawString(this.font, clip(subtitle, panelW - 150), panelX + 56, panelY + 39, C_TEXT_DIM, false);

        // Draw custom tab beds behind the vanilla click targets. Their active glow animates, while
        // the real Button widgets stay responsible for accessibility, keyboard focus and clicks.
        int innerX = panelX + 14;
        int innerW = panelW - 28;
        int tabGap = 6;
        int tabW = (innerW - tabGap) / 2;
        int tabY = panelY + 52;
        drawTabBed(graphics, innerX, tabY, tabW, 24, activeTab == Tab.CLIENT, C_CLIENT, now);
        drawTabBed(graphics, innerX + tabW + tabGap, tabY, tabW, 24, activeTab == Tab.SERVER, C_SERVER, now);

        graphics.enableScissor(panelX + 9, contentTop, panelX + panelW - 9, contentBottom);

        for (SectionVisual section : sections) {
            if (section.y + SECTION_H <= contentTop || section.y >= contentBottom) continue;
            boolean visible = section.y >= contentTop - SECTION_H && section.y <= contentBottom;
            if (!visible) continue;
            int pulse = 120 + (int) (70 * (0.5 + 0.5 * Math.sin(now / 360.0 + section.y * 0.045)));
            graphics.fill(panelX + 16, section.y + 4, panelX + panelW - 16, section.y + SECTION_H - 4, 0xB0141822);
            graphics.fill(panelX + 16, section.y + 4, panelX + 20, section.y + SECTION_H - 4, withAlpha(accent, pulse));
            graphics.fill(panelX + 22, section.y + SECTION_H - 6, panelX + panelW - 24, section.y + SECTION_H - 5, withAlpha(accent, 54));
            graphics.drawString(this.font, section.title.toUpperCase(java.util.Locale.ROOT), panelX + 28, section.y + 8, C_TEXT, false);
            int titleWidth = this.font.width(section.title.toUpperCase(java.util.Locale.ROOT)) + 10;
            String small = clip(section.subtitle, Math.max(0, panelW - 78 - titleWidth));
            if (!small.isEmpty()) graphics.drawString(this.font, small, panelX + 28 + titleWidth, section.y + 8, C_TEXT_DIM, false);
        }

        for (RowVisual row : rows) {
            if (row.y + row.height <= contentTop || row.y >= contentBottom) continue;
            boolean hovered = mouseX >= panelX + 16 && mouseX <= panelX + panelW - 16
                && mouseY >= row.y + 2 && mouseY <= row.y + row.height - 2;
            int bg = hovered ? C_ROW_HOVER : (row.alternate ? C_ROW_ALT : C_ROW);
            int left = panelX + 16;
            int right = panelX + panelW - 16;
            graphics.fill(left, row.y + 3, right, row.y + row.height - 3, bg);
            graphics.fill(left, row.y + 3, left + 2, row.y + row.height - 3, withAlpha(accent, hovered ? 220 : 72));
            if (hovered) {
                int travel = Math.max(1, right - left - 32);
                int sparkX = left + 10 + (int) ((now / 7L) % travel);
                graphics.fill(sparkX, row.y + 3, sparkX + 18, row.y + 4, withAlpha(accent, 180));
                graphics.fill(left + 4, row.y + row.height - 4, right - 4, row.y + row.height - 3, withAlpha(accent, 58));
            }
            graphics.drawString(this.font, clip(row.label, Math.max(92, panelW - 310)), panelX + 28, row.y + 10, C_TEXT, false);
            graphics.drawString(this.font, clip(row.description, Math.max(100, panelW - 320)), panelX + 28, row.y + 27, C_TEXT_DIM, false);
        }

        for (InfoLine line : infoLines) {
            if (line.y >= contentTop && line.y < contentBottom) {
                graphics.drawString(this.font, clip(line.text, panelW - 285), panelX + 28, line.y, line.color, false);
            }
        }

        graphics.disableScissor();

        // Footer is a separate command deck, not part of the scrolling list.
        graphics.fill(panelX + 10, contentBottom, panelX + panelW - 10, contentBottom + 1, withAlpha(accent, 90));
        graphics.fill(panelX + 12, contentBottom + 6, panelX + panelW - 12, panelY + panelH - 8, 0x9A0C0F17);
        graphics.drawString(this.font, activeTab == Tab.CLIENT ? "SCOPE: CLIENT" : "SCOPE: SERVER", panelX + 20, contentBottom + 8, withAlpha(accent, 190), false);

        if (maxScroll > 0) {
            int trackTop = contentTop + 5;
            int trackBottom = contentBottom - 5;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(24, (int) (trackH * (trackH / (double) (trackH + maxScroll))));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scrollOffset / (double) maxScroll));
            graphics.fill(panelX + panelW - 10, trackTop, panelX + panelW - 7, trackBottom, 0x55303848);
            graphics.fill(panelX + panelW - 10, thumbY, panelX + panelW - 7, thumbY + thumbH, withAlpha(accent, 210));
            graphics.fill(panelX + panelW - 11, thumbY + 2, panelX + panelW - 6, thumbY + thumbH - 2, withAlpha(accent, 48));
        }

        drawCornerRunes(graphics, panelX, panelY, panelW, panelH, accent, now);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderArcaneBackdrop(GuiGraphics graphics, long now, int accent) {
        graphics.fill(0, 0, this.width, this.height, C_SCREEN);

        // Slowly drifting one-pixel motes. Positions are deterministic, so the backdrop feels alive
        // without allocations, particles, textures, or random state.
        for (int i = 0; i < 28; i++) {
            int speed = 7 + (i % 5) * 3;
            int x = Math.floorMod(i * 83 + (int) (now / speed), Math.max(1, this.width));
            int baseY = Math.floorMod(i * 47, Math.max(1, this.height));
            int y = baseY + (int) Math.round(Math.sin(now / 750.0 + i * 1.7) * 5.0);
            int alpha = 24 + (i % 4) * 10;
            graphics.fill(x, y, x + 1 + (i % 2), y + 1 + (i % 2), withAlpha(accent, alpha));
        }

        // Faint horizontal magic traces moving in the opposite direction.
        int traceOffset = (int) ((now / 24L) % 96L);
        for (int y = 18; y < this.height; y += 46) {
            for (int x = -96 + traceOffset; x < this.width; x += 96) {
                graphics.fill(x, y, x + 24, y + 1, withAlpha(accent, 13));
            }
        }
    }

    private void drawArcaneSigil(GuiGraphics graphics, int cx, int cy, int radius, int accent, long now) {
        double phase = now / 550.0;
        int pulseAlpha = 130 + (int) (70 * (0.5 + 0.5 * Math.sin(now / 300.0)));
        // Core
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, withAlpha(accent, 235));
        glowRect(graphics, cx - 4, cy - 4, cx + 5, cy + 5, accent, 30);
        // Two orbiting motes plus a dotted ring.
        for (int i = 0; i < 20; i++) {
            double a = (Math.PI * 2.0 * i / 20.0) + phase * 0.18;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            graphics.fill(x, y, x + 1, y + 1, withAlpha(accent, 74));
        }
        for (int i = 0; i < 2; i++) {
            double a = phase * (i == 0 ? 1.0 : -0.72) + i * Math.PI;
            int r = radius - 3 + i * 5;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, withAlpha(accent, pulseAlpha));
        }
        // Four rune spokes.
        graphics.fill(cx - radius + 4, cy, cx - 5, cy + 1, withAlpha(accent, 80));
        graphics.fill(cx + 6, cy, cx + radius - 3, cy + 1, withAlpha(accent, 80));
        graphics.fill(cx, cy - radius + 4, cx + 1, cy - 5, withAlpha(accent, 80));
        graphics.fill(cx, cy + 6, cx + 1, cy + radius - 3, withAlpha(accent, 80));
    }

    private void drawTabBed(GuiGraphics graphics, int x, int y, int w, int h, boolean active, int color, long now) {
        int alpha = active ? 112 + (int) (38 * (0.5 + 0.5 * Math.sin(now / 330.0))) : 34;
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, withAlpha(color, alpha));
        graphics.fill(x + 2, y + h - 2, x + w - 2, y + h, withAlpha(color, active ? 230 : 64));
        if (active) glowRect(graphics, x - 2, y - 2, x + w + 2, y + h + 2, color, 16);
    }

    private void drawCornerRunes(GuiGraphics graphics, int x, int y, int w, int h, int accent, long now) {
        int a = 90 + (int) (50 * (0.5 + 0.5 * Math.sin(now / 480.0)));
        int c = withAlpha(accent, a);
        // Top-left / bottom-right bracket glyphs.
        graphics.fill(x + 10, y + 9, x + 30, y + 10, c);
        graphics.fill(x + 10, y + 9, x + 11, y + 22, c);
        graphics.fill(x + w - 30, y + h - 10, x + w - 10, y + h - 9, c);
        graphics.fill(x + w - 11, y + h - 22, x + w - 10, y + h - 9, c);
    }

    private static void glowRect(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int baseAlpha) {
        for (int i = 3; i >= 1; i--) {
            int alpha = Math.max(2, baseAlpha / (i + 1));
            graphics.fill(x1 - i, y1 - i, x2 + i, y1 - i + 1, withAlpha(color, alpha));
            graphics.fill(x1 - i, y2 + i - 1, x2 + i, y2 + i, withAlpha(color, alpha));
            graphics.fill(x1 - i, y1 - i, x1 - i + 1, y2 + i, withAlpha(color, alpha));
            graphics.fill(x2 + i - 1, y1 - i, x2 + i, y2 + i, withAlpha(color, alpha));
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    private String clip(String text, int width) {
        if (width <= 8) return "";
        if (font.width(text) <= width) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(1, width - font.width(ellipsis))) + ellipsis;
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String prettyEnum(String name) {
        if (name == null || name.isBlank()) return "Unknown";
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
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

    /** Compact value-only slider; the row label and explanation live beside it rather than inside it. */
    private static class FloatSlider extends AbstractSliderButton {
        private final Function<Float, String> formatter;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> onCommit;

        FloatSlider(int x, int y, int w, int h, Function<Float, String> formatter,
                    float initial, float min, float max, java.util.function.Consumer<Float> onCommit) {
            super(x, y, w, h, Component.literal(formatter.apply(initial)), clampFraction((initial - min) / (max - min)));
            this.formatter = formatter;
            this.min = min;
            this.max = max;
            this.onCommit = onCommit;
        }

        private static double clampFraction(double value) {
            if (Double.isNaN(value)) return 0.0;
            return Math.max(0.0, Math.min(1.0, value));
        }

        private float currentValue() {
            return (float) (min + (max - min) * this.value);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(formatter.apply(currentValue())));
        }

        @Override
        protected void applyValue() {
            if (onCommit != null) onCommit.accept(currentValue());
        }
    }
}
