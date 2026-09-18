package com.dragonspeech.client.config;

import com.dragonspeech.compat.SentienceConfig;
import com.dragonspeech.mind.SentienceTier;
import com.dragonspeech.network.ConfigRequestPayload;
import com.dragonspeech.network.SetSentienceOverridePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "This setting should open up a new config that shows all mobs in the game (and any modded ones
 * that were added as well). Along with showing each mob, it should also show a numerical number
 * beside it. This number is for the sentience tier. You can then edit the tiers to change how
 * powerful its mind is. (it should not be limited to only the 3/4 tiers it has. The higher the tier,
 * the faster that mind reacts and defends/attacks.)" - per explicit direction.
 *
 * Every registered EntityType (BuiltInRegistries.ENTITY_TYPE, which by the time this screen can even
 * be opened contains every mod's entities regardless of load order, same reasoning SentienceConfig's
 * own doc already establishes) gets a row with 2 independent, optional edits:
 *
 *   TIER (CycleButton, "Default" + the 8 SentienceTier presets): the qualitative "how hard is this
 *   mind to reach/read/break" axis - unchanged from before, still enum-based (see SentienceTier's own
 *   doc on why that wasn't also made numeric).
 *
 *   REACTION POWER (a free-typed whole number, EditBox): the NEW numeric override this feature adds -
 *   purely how fast that mind reacts/defends/attacks in a duel (MobMindCombatAI's own
 *   clickAttemptsPerPulse), unbounded rather than limited to the 3 tier-derived presets (1/2/4) that
 *   existed before this. Blank = "just use the tier's own small built-in default."
 *
 * The full entity registry can run into the hundreds once mods are counted, so this screen only ever
 * builds WIDGETS for the current page of the (searched/filtered) list - see rebuildRows() - rather
 * than one row's worth of live widgets per entity all at once.
 */
public class SentienceEditorScreen extends Screen {

    private static final int PANEL_W = 460;
    private static final int ROW_H = 22;
    private static final int LIST_TOP = 70;
    private static final int NAME_W = 220;
    private static final int TIER_W = 140;
    private static final int REACTION_W = 60;

    private static final int C_BG = 0xE8141018;
    private static final int C_BORDER = 0xFF4A4238;
    private static final int C_TEXT = 0xFFE8DEC8;
    private static final int C_TEXT_DIM = 0xFFA89A80;

    /** "Default" + the 8 real tiers - index 0 means "no tier override, use the automatic classifyTier() logic." */
    private static final List<String> TIER_LABELS = buildTierLabels();

    private static List<String> buildTierLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("Default");
        for (SentienceTier tier : SentienceTier.values()) {
            String name = tier.name().toLowerCase(Locale.ROOT);
            labels.add(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        }
        return labels;
    }

    private record RowData(String entityId, String displayName) {}

    private final Screen parent;
    private final List<RowData> allEntities = new ArrayList<>();
    private List<RowData> filtered = new ArrayList<>();
    private final Map<String, SentienceTier> tierOverrides = new HashMap<>();
    private final Map<String, Integer> reactionOverrides = new HashMap<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelH;
    private int rowsPerPage;
    private int scrollIndex = 0;
    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;

    public SentienceEditorScreen(Screen parent) {
        super(Component.literal("Sentience Editor"));
        this.parent = parent;
    }

    private boolean isConnected() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    protected void init() {
        this.panelH = Math.min(this.height - 40, 440);
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - panelH) / 2;
        this.rowsPerPage = Math.max(3, (panelH - LIST_TOP - 34) / ROW_H);

        if (allEntities.isEmpty()) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (id == null) {
                    continue;
                }
                String display = type.getDescription().getString();
                allEntities.add(new RowData(id.toString(), display));
            }
            allEntities.sort(Comparator.comparing(RowData::displayName, String.CASE_INSENSITIVE_ORDER));
        }

        loadOverridesFromSource();
        if (isConnected()) {
            ClientPlayNetworking.send(new ConfigRequestPayload());
        }

        this.searchBox = new EditBox(this.font, panelX + 12, panelY + 40, PANEL_W - 24, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search mobs..."));
        this.searchBox.setResponder(s -> {
            scrollIndex = 0;
            applyFilter(s);
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        prevPageButton = addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            scrollIndex = Math.max(0, scrollIndex - rowsPerPage);
            rebuildRows();
        }).bounds(panelX + 12, panelY + panelH - 26, 70, 20).build());

        nextPageButton = addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            int maxScroll = Math.max(0, filtered.size() - rowsPerPage);
            scrollIndex = Math.min(maxScroll, scrollIndex + rowsPerPage);
            rebuildRows();
        }).bounds(panelX + 12 + 74, panelY + panelH - 26, 70, 20).build());

        addRenderableWidget(tip(Button.builder(Component.literal("Clear All Overrides"), b -> clearAllOverrides())
                .bounds(panelX + 12 + 74 + 74, panelY + panelH - 26, 120, 20).build(),
                "Removes every tier/reaction override at once, on every mob - back to fully automatic classification."));

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + PANEL_W - 92, panelY + panelH - 26, 80, 20).build());

        applyFilter(searchBox.getValue());
    }

    /** "Clear All Overrides" button - matches the reset buttons on ConfigScreen/DifficultyTuningScreen. Offline writes straight to disk; online sends a payload the server re-validates against hasPermissions(4) before applying. */
    private void clearAllOverrides() {
        if (isConnected()) {
            ClientPlayNetworking.send(new com.dragonspeech.network.ClearSentienceOverridesPayload());
        } else {
            SentienceConfig.clearAllOverrides();
        }
        loadOverridesFromSource();
        rebuildRows();
    }

    private void loadOverridesFromSource() {
        tierOverrides.clear();
        reactionOverrides.clear();
        if (isConnected()) {
            for (ServerConfigClientCache.SentienceOverride o : ServerConfigClientCache.sentienceOverrides()) {
                if (o.tierName() != null) {
                    parseTier(o.tierName()).ifPresent(t -> tierOverrides.put(o.entityId(), t));
                }
                if (o.reactionPower() != null) {
                    reactionOverrides.put(o.entityId(), o.reactionPower());
                }
            }
        } else {
            for (SentienceConfig.Entry entry : SentienceConfig.currentOverrides()) {
                if (entry.tier() != null) {
                    tierOverrides.put(entry.entityId(), entry.tier());
                }
                if (entry.reactionPower() != null) {
                    reactionOverrides.put(entry.entityId(), entry.reactionPower());
                }
            }
        }
    }

    /** Called after a fresh ConfigSyncPayload arrives while this screen is open - see DragonSpeechClient. */
    public void onServerSyncReceived() {
        loadOverridesFromSource();
        rebuildRows();
    }

    private static Optional<SentienceTier> parseTier(String raw) {
        for (SentienceTier tier : SentienceTier.values()) {
            if (tier.getSerializedName().equalsIgnoreCase(raw)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered = allEntities;
        } else {
            filtered = allEntities.stream()
                    .filter(r -> r.displayName().toLowerCase(Locale.ROOT).contains(q) || r.entityId().toLowerCase(Locale.ROOT).contains(q))
                    .toList();
        }
        scrollIndex = Math.max(0, Math.min(scrollIndex, Math.max(0, filtered.size() - rowsPerPage)));
        rebuildRows();
    }

    private void rebuildRows() {
        // Only the row widgets (tier/reaction controls) are torn down and rebuilt on scroll/filter -
        // the search box, page buttons, and Done button are added once in init() and never touched
        // here, so their text/focus state survives every rebuild.
        for (AbstractWidget w : new ArrayList<>(rowWidgets)) {
            removeWidget(w);
        }
        rowWidgets.clear();

        int rowY = panelY + LIST_TOP;
        int end = Math.min(filtered.size(), scrollIndex + rowsPerPage);
        for (int i = scrollIndex; i < end; i++) {
            RowData row = filtered.get(i);
            int tierX = panelX + 12 + NAME_W;
            int reactionX = tierX + TIER_W + 6;

            SentienceTier currentTier = tierOverrides.get(row.entityId());
            int tierIndex = currentTier == null ? 0 : currentTier.ordinal() + 1;

            CycleButton<String> tierButton = tip(CycleButton.<String>builder(Component::literal)
                    .withValues(TIER_LABELS)
                    .withInitialValue(TIER_LABELS.get(tierIndex))
                    .create(tierX, rowY, TIER_W, ROW_H - 2, Component.empty(), (btn, val) -> {
                        int idx = TIER_LABELS.indexOf(val);
                        SentienceTier newTier = idx <= 0 ? null : SentienceTier.values()[idx - 1];
                        if (newTier == null) {
                            tierOverrides.remove(row.entityId());
                        } else {
                            tierOverrides.put(row.entityId(), newTier);
                        }
                        commitRow(row.entityId());
                    }), "How hard " + row.displayName() + "'s mind is to reach, read, or break. \"Default\" uses this mod's normal automatic classification for this entity.");
            addRenderableWidget(tierButton);
            rowWidgets.add(tierButton);

            Integer currentReaction = reactionOverrides.get(row.entityId());
            EditBox reactionBox = new EditBox(this.font, reactionX, rowY, REACTION_W, ROW_H - 2, Component.empty());
            reactionBox.setMaxLength(6);
            reactionBox.setValue(currentReaction == null ? "" : String.valueOf(currentReaction));
            reactionBox.setHint(Component.literal("auto"));
            reactionBox.setTooltip(Tooltip.create(Component.literal(
                    "How fast " + row.displayName() + "'s mind reacts, defends, and attacks in a duel - higher is faster. Blank uses its tier's own small built-in default.")));
            reactionBox.setResponder(s -> {
                String trimmed = s.trim();
                if (trimmed.isEmpty()) {
                    reactionOverrides.remove(row.entityId());
                    commitRow(row.entityId());
                    return;
                }
                try {
                    int value = Integer.parseInt(trimmed);
                    reactionOverrides.put(row.entityId(), value);
                    commitRow(row.entityId());
                } catch (NumberFormatException ignored) {
                    // Not a full valid number yet (e.g. a lone "-" while typing) - don't commit, just
                    // let them keep typing rather than fighting their input.
                }
            });
            addRenderableWidget(reactionBox);
            rowWidgets.add(reactionBox);

            rowY += ROW_H;
        }

        int maxScroll = Math.max(0, filtered.size() - rowsPerPage);
        if (prevPageButton != null) {
            prevPageButton.active = scrollIndex > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = scrollIndex < maxScroll;
        }
    }

    /** Sends this entity's current (post-edit) tier/reaction-power state - offline writes straight to disk via SentienceConfig, online sends it to the server (which re-validates permission before applying). */
    private void commitRow(String entityId) {
        SentienceTier tier = tierOverrides.get(entityId);
        Integer reaction = reactionOverrides.get(entityId);
        if (isConnected()) {
            ClientPlayNetworking.send(new SetSentienceOverridePayload(
                    entityId,
                    tier != null, tier != null ? tier.getSerializedName() : "",
                    reaction != null, reaction != null ? reaction : 0));
        } else {
            SentienceConfig.applyAndPersist(entityId, tier, reaction);
        }
    }

    private static <T extends AbstractWidget> T tip(T widget, String text) {
        widget.setTooltip(Tooltip.create(Component.literal(text)));
        return widget;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Same fix as ConfigScreen - see that class's own doc for the full explanation of why
        // overriding this to a no-op (rather than skipping our own call to it) is required.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF0A0808);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + panelH + 2, C_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, C_BG);
        graphics.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 8, C_TEXT);
        graphics.drawString(this.font, filtered.size() + " mobs" + (isConnected() ? "" : " (editing your local defaults)"),
                panelX + 12, panelY + 24, C_TEXT_DIM, false);

        // Column headers.
        graphics.drawString(this.font, "Mob", panelX + 12, panelY + LIST_TOP - 10, C_TEXT_DIM, false);
        graphics.drawString(this.font, "Tier", panelX + 12 + NAME_W, panelY + LIST_TOP - 10, C_TEXT_DIM, false);
        graphics.drawString(this.font, "Reaction", panelX + 12 + NAME_W + TIER_W + 6, panelY + LIST_TOP - 10, C_TEXT_DIM, false);

        int rowY = panelY + LIST_TOP;
        int end = Math.min(filtered.size(), scrollIndex + rowsPerPage);
        for (int i = scrollIndex; i < end; i++) {
            RowData row = filtered.get(i);
            String name = row.displayName();
            if (this.font.width(name) > NAME_W - 8) {
                while (name.length() > 1 && this.font.width(name + "...") > NAME_W - 8) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "...";
            }
            graphics.drawString(this.font, name, panelX + 12, rowY + 6, C_TEXT, false);
            rowY += ROW_H;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, filtered.size() - rowsPerPage);
        scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex - (int) Math.signum(scrollY)));
        rebuildRows();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
