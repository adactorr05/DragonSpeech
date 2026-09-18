package com.dragonspeech.client.wow;

import com.dragonspeech.network.WordOfWordsActionPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Custom reality-editing interface opened only by speaking the current world's Word of Words. */
public final class WordOfWordsScreen extends Screen {
    private enum Page { ROOT, REMOVE, ADD, CHANGE, HALT, TIME, REVEAL, BIND, RESTORE, CONFIRM }

    private UUID sessionId;
    private JsonObject context;
    private Page page = Page.ROOT;
    private String confirmAction = "";
    private String confirmParam = "";
    private String confirmLabel = "";
    private float confirmCost;

    private int panelX, panelY;
    private static final int PANEL_W = 500;
    private static final int PANEL_H = 390;

    public WordOfWordsScreen(UUID sessionId, String contextJson) {
        super(Component.literal("Word of Words"));
        this.sessionId = sessionId;
        this.context = parse(contextJson);
    }

    public void refresh(UUID id, String contextJson) {
        this.sessionId = id;
        this.context = parse(contextJson);
        if (minecraft != null) rebuildWidgets();
    }

    private static JsonObject parse(String json) {
        try { return JsonParser.parseString(json).getAsJsonObject(); }
        catch (Exception ignored) { return new JsonObject(); }
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        if (page == Page.ROOT) buildRoot();
        else if (page == Page.CONFIRM) buildConfirm();
        else buildSubpage();
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(panelX + PANEL_W - 72, panelY + PANEL_H - 28, 58, 18).build());
    }

    private void buildRoot() {
        String[] labels = {"Remove", "Add", "Change", "Halt", "Time", "Reveal", "Bind", "Restore"};
        Page[] pages = {Page.REMOVE, Page.ADD, Page.CHANGE, Page.HALT, Page.TIME, Page.REVEAL, Page.BIND, Page.RESTORE};
        int w = 190, h = 42, gapX = 18, gapY = 12;
        int startX = panelX + (PANEL_W - (w * 2 + gapX)) / 2;
        int y = panelY + 88;
        for (int i = 0; i < labels.length; i++) {
            int col = i % 2, row = i / 2;
            final Page target = pages[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> { page = target; rebuildWidgets(); })
                .bounds(startX + col * (w + gapX), y + row * (h + gapY), w, h).build());
        }
    }

    private void buildSubpage() {
        addRenderableWidget(Button.builder(Component.literal("< Back"), b -> { page = Page.ROOT; rebuildWidgets(); })
            .bounds(panelX + 14, panelY + 54, 66, 18).build());
        int x = panelX + 32;
        int y = panelY + 84;
        int w = PANEL_W - 64;
        int h = 22;
        int gap = 5;

        switch (page) {
            case REMOVE -> {
                int colW = (w - 12) / 2;
                int leftY = y, rightY = y;
                JsonArray wards = array("wards");
                if (wards.isEmpty()) leftY = infoButton("No wards on selected target.", x, leftY, colW);
                for (int i = 0; i < wards.size() && i < 7; i++) {
                    JsonObject q = wards.get(i).getAsJsonObject();
                    String id = q.get("id").getAsString();
                    String label = "Remove " + q.get("type").getAsString() + " ward — " + Math.round(q.get("remove_cost").getAsFloat());
                    leftY = actionButton(label, "remove_ward", id, q.get("remove_cost").getAsFloat(), true, x, leftY, colW, h, gap);
                }

                JsonArray sigils = array("sigils");
                if (sigils.isEmpty()) rightY = infoButton("No traps near the focus.", x + colW + 12, rightY, colW);
                for (int i = 0; i < sigils.size() && i < 4; i++) {
                    JsonObject q = sigils.get(i).getAsJsonObject();
                    float c = q.has("remove_cost") ? q.get("remove_cost").getAsFloat() : wordCost(70f);
                    String label = "Remove " + q.get("element").getAsString() + " trap — " + Math.round(c);
                    rightY = actionButton(label, "remove_trap", q.get("id").getAsString(), c, true, x + colW + 12, rightY, colW, h, gap);
                }

                JsonArray halts = array("halts");
                if (!halts.isEmpty()) rightY += 4;
                for (int i = 0; i < halts.size() && i < 4; i++) {
                    JsonObject q = halts.get(i).getAsJsonObject();
                    float c = q.get("remove_cost").getAsFloat();
                    long ticks = q.get("remaining_ticks").getAsLong();
                    String label = "Remove " + q.get("label").getAsString() + " (" + shortDuration(ticks) + ") — " + Math.round(c);
                    String action = "target".equals(q.get("kind").getAsString()) ? "remove_halt_target" : "remove_halt_area";
                    rightY = actionButton(label, action, q.get("id").getAsString(), c, true, x + colW + 12, rightY, colW, h, gap);
                }
                y = Math.max(leftY, rightY);
            }
            case ADD -> {
                int colW = (w - 12) / 2;
                int leftY = y, rightY = y;
                for (String type : new String[]{"projectile","explosion","fall","melee","fire","magic"}) {
                    float c = wordCost(130f);
                    leftY = actionButton("Add " + cap(type) + " Ward — " + Math.round(c), "add_ward", type, c, false, x, leftY, colW, h, gap);
                }
                float revival = wordCost(950f);
                leftY = actionButton("Revival Ward — " + Math.round(revival), "add_ward", "revival", revival, true, x, leftY, colW, h, gap);
                for (String e : new String[]{"fire","ice","lightning","earth","wind"}) {
                    float c = wordCost(115f);
                    rightY = actionButton("Prepare " + cap(e) + " Sigil — " + Math.round(c), "add_sigil", e, c, false, x + colW + 12, rightY, colW, h, gap);
                }
                y = Math.max(leftY, rightY);
            }
            case CHANGE -> {
                float changeWordCost = wordCost(700f);
                y = actionButton("CHANGE THE WORD OF WORDS — " + Math.round(changeWordCost) + " stamina", "change_word", "", changeWordCost, true, x, y, w, h, gap);
                y = infoButton(bool("word_bound")
                    ? "Your memory is bound: you will know the replacement Word."
                    : "WARNING: without a memory binding, changing the Word makes you forget it.", x, y, w);
                y += 3;
                for (float m : new float[]{0.50f,0.75f,1.00f,1.25f,1.50f,2.00f}) {
                    float base = 260f + Math.abs(m - 1f) * 700f;
                    float c = wordCost(base);
                    y = actionButton("Set GLOBAL magic cost to x" + String.format(java.util.Locale.ROOT,"%.2f",m) + " — " + Math.round(c) + " stamina", "change_cost", Float.toString(m), c, true, x, y, w, h, gap);
                }
            }
            case HALT -> {
                y = actionButton(priceLabel("Halt selected caster's magic (45s)", 220f), "halt_magic_target", "900", wordCost(220f), true, x, y, w, h, gap);
                y = actionButton(priceLabel("Halt selected caster's magic (2m)", 220f * durationScale(2400, 900)), "halt_magic_target", "2400", wordCost(220f * durationScale(2400, 900)), true, x, y, w, h, gap);
                y = actionButton(priceLabel("Halt selected caster's magic (5m)", 220f * durationScale(6000, 900)), "halt_magic_target", "6000", wordCost(220f * durationScale(6000, 900)), true, x, y, w, h, gap);
                y = actionButton(priceLabel("Halt magic in 8-block area (30s)", 400f), "halt_magic_area", "8:600", wordCost(400f), true, x, y, w, h, gap);
                y = actionButton(priceLabel("Halt magic in 16-block area (2m)", 520f * durationScale(2400, 600)), "halt_magic_area", "16:2400", wordCost(520f * durationScale(2400, 600)), true, x, y, w, h, gap);
                float h15 = wordCost(180f);
                float h60 = wordCost(180f * durationScale(1200, 300));
                float h300 = wordCost(180f * durationScale(6000, 300));
                y = actionButton("Freeze selected target (15s) — " + Math.round(h15), "halt_target", "300", h15, false, x, y, w, h, gap);
                y = actionButton("Freeze selected target (1m) — " + Math.round(h60), "halt_target", "1200", h60, true, x, y, w, h, gap);
                y = actionButton("Freeze selected target (5m) — " + Math.round(h300), "halt_target", "6000", h300, true, x, y, w, h, gap);
                y = actionButton(priceLabel("Freeze nearby creatures (10s)", 520f), "halt_area", "", wordCost(520f), true, x, y, w, h, gap);
            }
            case TIME -> {
                float c180 = wordCost(180f), c420 = wordCost(420f), c360 = wordCost(360f), c650 = wordCost(650f), c900 = wordCost(900f);
                y = actionButton("Fast-forward 1,000 ticks — " + Math.round(c180) + " stamina", "time_fast", "1000", c180, true, x, y, w, h, gap);
                y = actionButton("Fast-forward 6,000 ticks — " + Math.round(c420) + " stamina", "time_fast", "6000", c420, true, x, y, w, h, gap);
                y = actionButton("Slow local time (30s) — " + Math.round(c360) + " stamina", "time_slow_area", "", c360, true, x, y, w, h, gap);
                y = actionButton("Local stasis field (12s) — " + Math.round(c650) + " stamina", "time_stasis_area", "", c650, true, x, y, w, h, gap);
                y = actionButton("REVERSE LOCAL STATE TO INVOCATION — " + Math.round(c900) + " stamina", "time_reverse_local", "", c900, true, x, y, w, h, gap);
            }
            case REVEAL -> {
                float c25 = wordCost(25f), c30 = wordCost(30f), c20 = wordCost(20f);
                y = actionButton("Reveal selected target's wards — " + Math.round(c25) + " stamina", "reveal_wards", "", c25, false, x, y, w, h, gap);
                y = actionButton("Reveal traps near focus — " + Math.round(c30) + " stamina", "reveal_traps", "", c30, false, x, y, w, h, gap);
                y = actionButton("Reveal selected target's state — " + Math.round(c20) + " stamina", "reveal_target", "", c20, false, x, y, w, h, gap);
            }
            case BIND -> {
                if (bool("word_bound")) {
                    y = infoButton("The Word of Words is already bound to your memory.", x, y, w);
                } else {
                    float c = wordCost(500f);
                    y = actionButton("Bind the Word of Words to my memory — " + Math.round(c) + " stamina", "bind_word_memory", "", c, true, x, y, w, h, gap);
                }
                y += 4;
                float c1 = wordCost(300f);
                float c5 = wordCost(300f * durationScale(6000, 1200));
                float c15 = wordCost(300f * durationScale(18000, 1200));
                y = actionButton("Bind selected caster's spoken magic (1m) — " + Math.round(c1), "bind_magic_target", "1200", c1, true, x, y, w, h, gap);
                y = actionButton("Bind selected caster's spoken magic (5m) — " + Math.round(c5), "bind_magic_target", "6000", c5, true, x, y, w, h, gap);
                y = actionButton("Bind selected caster's spoken magic (15m) — " + Math.round(c15), "bind_magic_target", "18000", c15, true, x, y, w, h, gap);
                float b30 = wordCost(260f);
                float b2 = wordCost(260f * durationScale(2400, 600));
                float b5 = wordCost(260f * durationScale(6000, 600));
                y = actionButton("Bind selected mob to place (30s) — " + Math.round(b30), "bind_target", "600", b30, true, x, y, w, h, gap);
                y = actionButton("Bind selected mob to place (2m) — " + Math.round(b2), "bind_target", "2400", b2, true, x, y, w, h, gap);
                y = actionButton("Bind selected mob to place (5m) — " + Math.round(b5), "bind_target", "6000", b5, true, x, y, w, h, gap);
            }
            case RESTORE -> {
                float selfHeal = num("restore_self_health_cost", wordCost(45f));
                y = actionButton("Restore MY health — " + Math.round(selfHeal) + " stamina", "restore_self_health", "", selfHeal, true, x, y, w, h, gap);
                float selfCondition = wordCost(120f);
                y = actionButton("Restore MY breath / warmth / hunger — " + Math.round(selfCondition) + " stamina", "restore_self_condition", "", selfCondition, false, x, y, w, h, gap);
                float healCost = num("restore_health_cost", 45f);
                y = actionButton("Restore selected target's health — " + Math.round(healCost) + " stamina", "restore_health", "", healCost, true, x, y, w, h, gap);
                JsonArray wards = array("wards");
                for (int i = 0; i < wards.size() && i < 6; i++) {
                    JsonObject q = wards.get(i).getAsJsonObject();
                    float c = q.get("restore_cost").getAsFloat();
                    y = actionButton("Restore " + q.get("type").getAsString() + " ward — " + Math.round(c) + " stamina", "restore_ward", q.get("id").getAsString(), c, false, x, y, w, h, gap);
                }
            }
            default -> {}
        }
    }

    private int actionButton(String label, String action, String param, float cost, boolean dangerous, int x, int y, int w, int h, int gap) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> {
            if (dangerous) {
                confirmAction = action; confirmParam = param; confirmLabel = label; confirmCost = cost; page = Page.CONFIRM; rebuildWidgets();
            } else send(action, param);
        }).bounds(x, y, w, h).build());
        return y + h + gap;
    }

    private int infoButton(String label, int x, int y, int w) {
        Button b = Button.builder(Component.literal(label), q -> {}).bounds(x, y, w, 22).build();
        b.active = false; addRenderableWidget(b); return y + 27;
    }

    private void buildConfirm() {
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> { page = Page.ROOT; rebuildWidgets(); })
            .bounds(panelX + 88, panelY + 235, 130, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Make reality obey"), b -> { send(confirmAction, confirmParam); page = Page.ROOT; rebuildWidgets(); })
            .bounds(panelX + 282, panelY + 235, 150, 24).build());
    }

    private void send(String action, String param) {
        ClientPlayNetworking.send(new WordOfWordsActionPayload(sessionId, action, param == null ? "" : param));
        if ("change_word".equals(action)) {
            onClose();
        }
    }

    private JsonArray array(String key) {
        return context.has(key) && context.get(key).isJsonArray() ? context.getAsJsonArray(key) : new JsonArray();
    }
    private String str(String key, String fallback) { return context.has(key) ? context.get(key).getAsString() : fallback; }
    private float num(String key, float fallback) { return context.has(key) ? context.get(key).getAsFloat() : fallback; }
    private boolean bool(String key) { return context.has(key) && context.get(key).getAsBoolean(); }
    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String fmt(float f) { return String.format(java.util.Locale.ROOT, "%.0f", f); }
    private float wordCost(float base) { return base * num("cost_multiplier", 1f); }
    private String priceLabel(String label, float base) { return label + " — " + Math.round(wordCost(base)) + " stamina"; }
    private static float durationScale(int ticks, int baseTicks) { return (float)Math.pow(Math.max(1f, ticks / (float)baseTicks), 0.72); }
    private static String shortDuration(long ticks) {
        long seconds = Math.max(1L, ticks / 20L);
        if (seconds >= 60) return (seconds / 60) + "m" + (seconds % 60 == 0 ? "" : (seconds % 60) + "s");
        return seconds + "s";
    }

    /**
     * Minecraft 1.21's Screen.renderBackground() runs the vanilla blur post-process.
     * Other Dragon Speech screens (Config/DragonBond/Grimoire/etc.) already suppress
     * it by overriding this method and drawing their own dim layer.  The Word screen
     * must do the same; merely avoiding an explicit call from render() is not enough
     * because super.render() calls renderBackground() internally as well.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: render() draws the only background this screen needs.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Plain dim overlay, no vanilla blur.
        g.fill(0, 0, width, height, 0xC8000000);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xEF090A12);
        g.fill(panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + 3, 0xFF8D6BE8);
        g.fill(panelX + 2, panelY + PANEL_H - 3, panelX + PANEL_W - 2, panelY + PANEL_H - 2, 0xFF493675);
        g.fill(panelX + 3, panelY + 3, panelX + 4, panelY + PANEL_H - 3, 0xFF493675);
        g.fill(panelX + PANEL_W - 4, panelY + 3, panelX + PANEL_W - 3, panelY + PANEL_H - 3, 0xFF493675);

        // Sparse runic spokes: custom without pretending to be a vanilla inventory.
        int cx = panelX + PANEL_W / 2, cy = panelY + 34;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            int x2 = cx + (int)(Math.cos(a) * 25), y2 = cy + (int)(Math.sin(a) * 16);
            g.hLine(Math.min(cx, x2), Math.max(cx, x2), y2, 0x668D6BE8);
        }

        g.drawCenteredString(font, Component.literal("THE WORD OF WORDS"), cx, panelY + 12, 0xFFE9E0FF);
        g.drawCenteredString(font, Component.literal(page == Page.ROOT ? "Reality waits for a command." : page == Page.CONFIRM ? "CONFIRM ALTERATION" : page.name()), cx, panelY + 30, 0xFFBBAAF2);
        g.drawString(font, "Focus: " + str("target_name", "Unknown"), panelX + 16, panelY + PANEL_H - 53, 0xFFD8D0E8, false);
        g.drawString(font, "Location: " + str("anchor", "?"), panelX + 16, panelY + PANEL_H - 41, 0xFF8E87A2, false);
        g.drawString(font, "Stamina: " + fmt(num("stamina",0)) + "/" + fmt(num("max_stamina",0)) + "    World cost: x" + String.format(java.util.Locale.ROOT,"%.2f",num("cost_multiplier",1)), panelX + 16, panelY + PANEL_H - 29, 0xFFC9BDF2, false);

        if (page == Page.TIME) {
            g.drawCenteredString(font, Component.literal("Close this screen, alter the same focus, then speak the Word again within 60s to resume this snapshot."), cx, panelY + PANEL_H - 68, 0xFF9A8EBB);
        }

        if (page == Page.CONFIRM) {
            g.drawCenteredString(font, Component.literal(confirmLabel), cx, panelY + 105, 0xFFFFD59B);
            g.drawCenteredString(font, Component.literal("Cost: " + Math.round(confirmCost) + " stamina"), cx, panelY + 130, 0xFFFFB7A8);
            g.drawCenteredString(font, Component.literal("This is not an ordinary spell. The change is applied server-side."), cx, panelY + 160, 0xFFB6AFC5);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
