package com.dragonspeech.client.mind;

import com.dragonspeech.mind.BarType;
import com.dragonspeech.mind.DuelAction;
import com.dragonspeech.network.MindDuelActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen for every phase from Defense Breach through True Name
 * Domination (Contact has no screen - it's resolved by a keybind before
 * any ActiveMindDuel/sync payload exists).
 *
 * Defense Breach is a direct-interaction mini-game, not a menu: five
 * bars (Focus/Stamina/Willpower/Power/Speed), five cards (pick one to
 * decide which bar a click spends), and the barrier itself is what you
 * click - the attacker strikes a crack to widen it, the defender clicks
 * the same crack to seal it. See mouseClicked() for the actual hit
 * testing, and MindDuelActionService (server) for the resolution this
 * calls into.
 */
public class MindDuelScreen extends Screen {

    private static final int C_BG = 0x00000000;
    private static final int C_PANEL = 0xFF1E1A16;
    private static final int C_FRAME = 0xFF4A4238;
    private static final int C_BREACH = 0xFFC9A24B;
    private static final int C_CONTROL_SELF = 0xFF8B5CF6;
    private static final int C_CONTROL_OPP = 0xFF4B9CD3;

    /** Only used within Defense Breach's own layout (barrier position + click hit-testing). Pushed down from its old value to leave room for the stat bars, which now render above the barriers instead of beside them. */
    private static final int BARRIER_CENTER_Y = 300;

    private MindDuelClientState state;
    private String lastPhase = "";
    /** Client-only UI selection: which card is currently "armed" for the next click. Defaults to Stamina - the safest, cheapest bar. */
    private BarType selectedCard = BarType.STAMINA;
    private String postAccessPanel = "root";

    private final List<IconSpot> iconSpots = new ArrayList<>();
    private record IconSpot(ResourceLocation texture, int x, int y) {}

    public MindDuelScreen(String initialJson) {
        super(Component.literal("Mind Duel"));
        this.state = MindDuelClientState.parse(initialJson);
    }

    public void refresh(String json) {
        this.state = MindDuelClientState.parse(json);
        if (this.state == null) {
            onClose();
            return;
        }
        if (!this.state.phase.equals(lastPhase)) {
            postAccessPanel = "root";
            clearWidgets();
            rebuildActions();
        }
    }

    @Override
    protected void init() {
        super.init();
        rebuildActions();
    }

    // ---------------------------------------------------------------- widgets

    private void rebuildActions() {
        if (state == null) {
            return;
        }
        lastPhase = state.phase;
        iconSpots.clear();

        if (state.pendingRoleChoice) {
            rebuildRoleChoiceButtons();
            return;
        }

        boolean isCommandPhase = "occupied_mind".equals(state.phase) || "true_name_domination".equals(state.phase);
        if ("defense_breach".equals(state.phase)) {
            rebuildBreachButtons();
        } else if (isCommandPhase && state.isAttacker) {
            rebuildCommandButtons();
        } else {
            rebuildPlainActions();
            if (isCommandPhase && state.mindControlledSelf && !"true_name_domination".equals(state.phase)) {
                addRenderableWidget(Button.builder(Component.literal("Resist Control"), button -> send(DuelAction.RESIST, "mind_control"))
                        .bounds(this.width / 2 - 70, this.height - 70, 140, 22)
                        .build());
            }
        }

        if (!"ended".equals(state.phase)) {
            int x = this.width - 100, y = this.height - 30;
            addRenderableWidget(Button.builder(Component.literal("Disengage"), button -> send(DuelAction.DISENGAGE, ""))
                    .bounds(x, y, 90, 20)
                    .build());
            addIcon(DuelAction.DISENGAGE, x + 3, y + 2);
        } else {
            addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                    .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                    .build());
        }
    }

    /** The five cards, laid out under the barrier - clicking one just changes which bar the NEXT barrier click spends. Clicking the barrier itself is the actual action (see mouseClicked). */
    private void rebuildBreachButtons() {
        BarType[] bars = BarType.values();
        int spacing = 8;
        int totalWidth = bars.length * MindTextures.SMALL_BUTTON_WIDTH + (bars.length - 1) * spacing;
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - MindTextures.SMALL_BUTTON_HEIGHT - 40;

        for (int i = 0; i < bars.length; i++) {
            BarType bar = bars[i];
            int x = startX + i * (MindTextures.SMALL_BUTTON_WIDTH + spacing);
            addRenderableWidget(Button.builder(Component.literal(""), button -> selectedCard = bar)
                    .bounds(x, y, MindTextures.SMALL_BUTTON_WIDTH, MindTextures.SMALL_BUTTON_HEIGHT)
                    .build());
        }
    }

    private void rebuildRoleChoiceButtons() {
        addRenderableWidget(Button.builder(Component.literal("Flee"), button -> send(DuelAction.DISENGAGE, ""))
                .bounds(this.width / 2 - 160, this.height / 2 + 40, 150, 24)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Seize Control"), button -> send(DuelAction.SEIZE_CONTROL, ""))
                .bounds(this.width / 2 + 10, this.height / 2 + 40, 150, 24)
                .build());
    }

    private void rebuildPlainActions() {
        List<DuelAction> actions = actionsFor(state.phase, state.isAttacker);
        int buttonWidth = 130, spacing = 10;
        int totalWidth = actions.size() * buttonWidth + Math.max(0, actions.size() - 1) * spacing;
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - 60;

        for (int i = 0; i < actions.size(); i++) {
            DuelAction action = actions.get(i);
            int x = startX + i * (buttonWidth + spacing);
            addRenderableWidget(Button.builder(Component.literal(label(action)), button -> send(action, ""))
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            addIcon(action, x + 3, y + 2);
        }
    }

    /**
     * The post-access command options - per the user's clarification,
     * the CARD art (one per BarType color) belongs here, once you have
     * broken into a mind and are choosing what to do with it - per the
     * user's explicit clarification. Bar-selection during Breach uses
     * the BUTTON art instead (see rebuildBreachButtons/renderCardSelection).
     */
    private record PostAccessCard(String label, BarType accent, String commandId, String panel) {}

    private static final PostAccessCard[] POST_ACCESS_ROOT = {
            new PostAccessCard("Control", BarType.POWER, "mind_control", null),
            new PostAccessCard("Inventory", BarType.SPEED, "inspect_inventory", null),
            new PostAccessCard("Check\nStamina", BarType.STAMINA, null, "stamina"),
            new PostAccessCard("Debuffs", BarType.WILLPOWER, null, "debuffs"),
            new PostAccessCard("Words", BarType.FOCUS, null, "words")
    };

    private static final PostAccessCard[] STAMINA_CARDS = {
            new PostAccessCard("Drain\nStamina", BarType.STAMINA, "drain_stamina", null)
    };

    private static final PostAccessCard[] DEBUFF_CARDS = {
            new PostAccessCard("Rage", BarType.POWER, "emotion_rage", null),
            new PostAccessCard("Fear", BarType.SPEED, "emotion_fear", null),
            new PostAccessCard("Despair", BarType.WILLPOWER, "emotion_despair", null),
            new PostAccessCard("Calm", BarType.FOCUS, "emotion_calm", null)
    };

    private static final PostAccessCard[] WORD_CARDS = {
            new PostAccessCard("View\nWords", BarType.FOCUS, "view_words", null),
            new PostAccessCard("Remove\nWord", BarType.POWER, "sever_random_word", null),
            new PostAccessCard("Learn\nWord", BarType.WILLPOWER, "learn_word", null)
    };

    private void rebuildCommandButtons() {
        PostAccessCard[] cards = activePostAccessCards();
        int w = MindTextures.CARD_WIDTH, h = MindTextures.CARD_HEIGHT, spacing = 10;
        int totalWidth = cards.length * w + Math.max(0, cards.length - 1) * spacing;
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - h - 55;

        for (int i = 0; i < cards.length; i++) {
            PostAccessCard card = cards[i];
            addRenderableWidget(Button.builder(Component.literal(""), button -> {
                        if (card.panel() != null) {
                            postAccessPanel = card.panel();
                            clearWidgets();
                            rebuildActions();
                        } else {
                            send(DuelAction.ISSUE_COMMAND, card.commandId());
                        }
                    })
                    .bounds(startX + i * (w + spacing), y, w, h)
                    .build());
        }

        if (!"root".equals(postAccessPanel)) {
            addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
                postAccessPanel = "root";
                clearWidgets();
                rebuildActions();
            }).bounds(this.width / 2 - 45, y - 28, 90, 20).build());
        }

        // The True Name card is BACK, per explicit direction - it just
        // opens the NEW post-victory guessing screen now instead of the
        // deleted high-stakes mid-duel one. Only shown once we actually
        // know who we're connected to (state.opponentId - added to the
        // sync payload specifically for this) and the server has
        // already unlocked progress for them (see
        // MindDuelActionService.afterBarrierChange(), which unlocks the
        // instant OCCUPIED_MIND begins, not only when the whole duel
        // formally ends).
        if (state.opponentId != null && "root".equals(postAccessPanel)) {
            String cardLabel = "True Name";
            int cardW = 90, cardH = 20;
            int cardX = this.width / 2 - cardW / 2;
            int cardY = y - h - 15;
            addRenderableWidget(Button.builder(Component.literal(cardLabel), button -> {
                java.util.UUID targetId = java.util.UUID.fromString(state.opponentId);
                String targetName = state.opponentName != null ? state.opponentName : "Unknown";
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.dragonspeech.client.grimoire.TrueNameGuessingScreen(this, targetId, targetName));
            }).bounds(cardX, cardY, cardW, cardH).build());
        }
    }

    /** Draws the card art + caption text for the post-access command row - mirrors renderCardSelection()'s "blank vanilla Button, texture+text drawn separately" pattern. */
    private void renderCommandButtons(GuiGraphics graphics) {
        PostAccessCard[] cards = activePostAccessCards();
        int w = MindTextures.CARD_WIDTH, h = MindTextures.CARD_HEIGHT, spacing = 10;
        int totalWidth = cards.length * w + Math.max(0, cards.length - 1) * spacing;
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - h - 55;

        graphics.drawCenteredString(this.font, postAccessTitle(), this.width / 2, y - 14, C_BREACH);

        for (int i = 0; i < cards.length; i++) {
            int x = startX + i * (w + spacing);
            graphics.blit(MindTextures.card(cards[i].accent()), x, y, 0f, 0f, w, h, w, h);
        }
        drawWrappedCaptions(graphics, cards, startX, y, w, h, spacing);
    }

    private void drawWrappedCaptions(GuiGraphics graphics, PostAccessCard[] cards, int startX, int y, int cardWidth, int cardHeight, int spacing) {
        for (int i = 0; i < cards.length; i++) {
            int x = startX + i * (cardWidth + spacing);
            String[] lines = cards[i].label().split("\n");
            int lineHeight = 11;
            int blockHeight = lines.length * lineHeight;
            int textY = y + 46 - blockHeight / 2;
            for (String line : lines) {
                graphics.drawCenteredString(this.font, line, x + cardWidth / 2, textY, 0xFFFFFF);
                textY += lineHeight;
            }
        }
    }

    private PostAccessCard[] activePostAccessCards() {
        return switch (postAccessPanel) {
            case "stamina" -> STAMINA_CARDS;
            case "debuffs" -> DEBUFF_CARDS;
            case "words" -> WORD_CARDS;
            default -> POST_ACCESS_ROOT;
        };
    }

    private String postAccessTitle() {
        return switch (postAccessPanel) {
            case "stamina" -> "Stamina Actions";
            case "debuffs" -> "Emotion Debuffs";
            case "words" -> "Known Words";
            default -> "Connected Mind";
        };
    }

    private static List<DuelAction> actionsFor(String phase, boolean isAttacker) {
        return switch (phase) {
            case "occupied_mind", "true_name_domination" -> List.of();
            default -> List.of();
        };
    }

    private static String label(DuelAction action) {
        return switch (action) {
            case SPEAK_TRUE_NAME -> "Speak True Name";
            case DISENGAGE -> "Disengage";
            case ISSUE_COMMAND -> "Issue Command";
            case STRIKE_CRACK -> "Strike";
            case SEAL_CRACK -> "Seal";
            case SEIZE_CONTROL -> "Seize Control";
            case RESIST -> "Resist";
        };
    }

    private void addIcon(DuelAction action, int x, int y) {
        ResourceLocation texture = MindTextures.iconFor(action);
        if (texture != null) {
            iconSpots.add(new IconSpot(texture, x, y));
        }
    }

    private void send(DuelAction action, String param) {
        ClientPlayNetworking.send(new MindDuelActionPayload(action.getSerializedName(), param));
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && state != null && "defense_breach".equals(state.phase) && !state.pendingRoleChoice) {
            int size = MindTextures.BREACH_BARRIER_SIZE;
            int myX = myBarrierX();
            int theirX = theirBarrierX();
            int barrierY = BARRIER_CENTER_Y - size / 2;

            if (withinBarrier(mouseX, mouseY, myX, barrierY, size)) {
                // Your own barrier - always a SEAL attempt, on whichever crack (if any) the click landed near.
                MindDuelClientState.CrackView crack = crackAt(mouseX, mouseY, myX, barrierY, size, state.myBarrier);
                if (crack != null) {
                    send(DuelAction.SEAL_CRACK, selectedCard.getSerializedName() + ":" + crack.id());
                }
                return true;
            }
            if (withinBarrier(mouseX, mouseY, theirX, barrierY, size)) {
                // The opponent's barrier - always a STRIKE, at the exact normalized position clicked.
                float nx = (float) ((mouseX - theirX) / size);
                float ny = (float) ((mouseY - barrierY) / size);
                send(DuelAction.STRIKE_CRACK, selectedCard.getSerializedName() + ":" + nx + ":" + ny);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean withinBarrier(double mouseX, double mouseY, int barrierX, int barrierY, int size) {
        double centerX = barrierX + size / 2.0;
        double centerY = barrierY + size / 2.0;
        double radius = size * 0.47;
        double dx = mouseX - centerX, dy = mouseY - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** X position of MY OWN barrier (gold, left side) - a fixed layout constant, factored out since both click handling and rendering need the exact same position. */
    private int myBarrierX() {
        int size = MindTextures.BREACH_BARRIER_SIZE;
        int gap = 40;
        int totalWidth = size * 2 + gap;
        return this.width / 2 - totalWidth / 2;
    }

    /** X position of THEIR barrier (blue, right side). */
    private int theirBarrierX() {
        return myBarrierX() + MindTextures.BREACH_BARRIER_SIZE + 40;
    }

    private MindDuelClientState.CrackView crackAt(double mouseX, double mouseY, int barrierX, int barrierY, int size, MindDuelClientState.BarrierView barrier) {
        if (barrier == null) {
            return null;
        }
        MindDuelClientState.CrackView best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MindDuelClientState.CrackView crack : barrier.cracks()) {
            double cx = barrierX + crack.x() * size;
            double cy = barrierY + crack.y() * size;
            double radius = crackClickRadius(crack, size);
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double distance = dx * dx + dy * dy;
            if (distance <= radius * radius && distance < bestDistance) {
                best = crack;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible behind the mind duel UI; the art already carries its own dark panels.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, C_BG);

        if (state == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawCenteredString(this.font, phaseTitle(state.phase), this.width / 2, 16, 0xFFFFFF);
        // "A way to know what it is your attacking with your mind...
        // maybe it also said what the target was" per explicit
        // direction - state.opponentName was already flowing to the
        // client (built server-side from the real entity's own display
        // name - a player's actual name, or the translated entity name
        // for anything else, "Dragon Heart" included per its own lang
        // entry) - it just wasn't shown here, only used later for the
        // True Name card during Occupied Mind. Same data, now also
        // shown where it's actually useful - the moment you're deciding
        // whether to keep attacking at all.
        if (state.opponentName != null) {
            graphics.drawCenteredString(this.font, (state.isAttacker ? "Target: " : "Attacker: ") + state.opponentName,
                this.width / 2, 28, 0xB8AE9A);
        }

        if ("defense_breach".equals(state.phase)) {
            renderBreach(graphics, mouseX, mouseY);
        } else {
            renderBars(graphics, this.width / 2 - 230, 45, "You", state.self, !state.isAttacker);
            renderBars(graphics, this.width / 2 + 30, 45, "Them", state.opponent, state.isAttacker);
        }

        if (state.pendingRoleChoice) {
            graphics.fill(this.width / 2 - 200, this.height / 2 - 20, this.width / 2 + 200, this.height / 2 + 30, 0xE0201010);
            graphics.drawCenteredString(this.font, "Your Focus has broken!", this.width / 2, this.height / 2 - 10, 0xFF6666);
            graphics.drawCenteredString(this.font, "Flee, or seize control and become the attacker?", this.width / 2, this.height / 2 + 4, 0xFFFFFF);
        }

        if ("ended".equals(state.phase)) {
            graphics.drawCenteredString(this.font, "The duel has ended.", this.width / 2, 170, 0xFFAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (IconSpot spot : iconSpots) {
            graphics.blit(spot.texture(), spot.x(), spot.y(), 0f, 0f, MindTextures.ICON_SIZE, MindTextures.ICON_SIZE, MindTextures.ICON_SIZE, MindTextures.ICON_SIZE);
        }

        if ("defense_breach".equals(state.phase)) {
            renderCardSelection(graphics);
        }
        if (("occupied_mind".equals(state.phase) || "true_name_domination".equals(state.phase)) && state.isAttacker) {
            renderCommandButtons(graphics);
        }
    }

    /** Two barriers side by side - MY OWN (gold, left) and THEIRS (blue, right), each with cracks drawn exactly where they were struck. Stat bars render above each barrier rather than beside them, since beside-barrier positioning was getting covered by the barriers themselves at smaller GUI scales - above-positioning uses vertical space, which is generally more abundant than horizontal space is at narrow/high-scale window sizes. */
    private void renderBreach(GuiGraphics graphics, int mouseX, int mouseY) {
        int size = MindTextures.BREACH_BARRIER_SIZE;
        int barrierY = BARRIER_CENTER_Y - size / 2;
        int myX = myBarrierX();
        int theirX = theirBarrierX();

        renderOneBarrier(graphics, mouseX, mouseY, myX, barrierY, size, MindTextures.BREACH_BARRIER_BASE_GOLD, state.myBarrier, 0xAAFFE066);
        renderOneBarrier(graphics, mouseX, mouseY, theirX, barrierY, size, MindTextures.BREACH_BARRIER_BASE, state.theirBarrier, 0xAA66D9FF);

        graphics.drawCenteredString(this.font, "Your Barrier", myX + size / 2, barrierY - 12, 0xFFD9A24B);
        graphics.drawCenteredString(this.font, String.format("%.0f%%", state.myBarrier != null ? state.myBarrier.integrity() : 100f),
                myX + size / 2, barrierY + size + 6, 0xFFD9A24B);

        graphics.drawCenteredString(this.font, "Their Barrier", theirX + size / 2, barrierY - 12, 0xFF66D9FF);
        graphics.drawCenteredString(this.font, String.format("%.0f%%", state.theirBarrier != null ? state.theirBarrier.integrity() : 100f),
                theirX + size / 2, barrierY + size + 6, 0xFF66D9FF);

        // Each bar column is ~280px wide in practice (220px frame plus the
        // label/value text extending past its edges) - noticeably wider
        // than the 170px barrier itself, so this deliberately does NOT
        // reuse myX/theirX for horizontal position; doing so would have
        // let the two bar columns overlap each other's space. Centered
        // independently as its own, wider block instead.
        int barColumnWidth = 280;
        int barColumnGap = 20;
        int barsY = 60; // clears the phase title above and the barrier + its "Your/Their Barrier" label below
        int barsLeftX = this.width / 2 - (barColumnWidth * 2 + barColumnGap) / 2;
        int barsRightX = barsLeftX + barColumnWidth + barColumnGap;
        renderBars(graphics, barsLeftX, barsY, "You", state.self, !state.isAttacker);
        renderBars(graphics, barsRightX, barsY, "Them", state.opponent, state.isAttacker);
    }

    private void renderOneBarrier(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int size,
                                  net.minecraft.resources.ResourceLocation texture, MindDuelClientState.BarrierView barrier, int hoverColor) {
        graphics.blit(texture, x, y, 0f, 0f, size, size, size, size);
        if (barrier == null) {
            return;
        }
        for (MindDuelClientState.CrackView crack : barrier.cracks()) {
            float crackAlpha = Math.max(0f, Math.min(1f, crack.openness() / 100f));
            int cx = x + Math.round(crack.x() * size);
            int cy = y + Math.round(crack.y() * size);
            int radius = (int) Math.round(crackClickRadius(crack, size));
            int crackColor = 0xFF071119 | (Math.round(180 * crackAlpha) << 24);
            int glowColor = 0xAA66D9FF;
            drawProceduralCrack(graphics, crack, cx, cy, radius, crackColor, glowColor);
            boolean hovered = (mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy) <= radius * radius;
            if (hovered) {
                MindVisuals.drawRing(graphics, cx, cy, radius + 3, 2, hoverColor);
            }
        }
    }

    private static double crackClickRadius(MindDuelClientState.CrackView crack, int barrierSize) {
        float openness = Math.max(0f, Math.min(1f, crack.openness() / 100f));
        return barrierSize * (0.045 + 0.06 * openness);
    }

    private static void drawProceduralCrack(GuiGraphics graphics, MindDuelClientState.CrackView crack, int cx, int cy, int radius, int crackColor, int glowColor) {
        float openness = Math.max(0.15f, Math.min(1f, crack.openness() / 100f));
        int branchCount = 3 + Math.round(openness * 4f);
        int length = Math.max(8, Math.round(radius * (0.65f + openness)));
        int thickness = openness > 0.65f ? 2 : 1;

        MindVisuals.fillCircle(graphics, cx, cy, Math.max(2, Math.round(2 + openness * 3f)), 0xBB7FEAFF);
        for (int i = 0; i < branchCount; i++) {
            float seed = MindVisuals.stableRandom(crack.id() * 31 + crack.variant() * 17 + i * 13);
            double angle = seed * Math.PI * 2.0;
            double branchLength = length * (0.45 + MindVisuals.stableRandom(crack.id() * 47 + i * 19) * 0.75);
            double ex = cx + Math.cos(angle) * branchLength;
            double ey = cy + Math.sin(angle) * branchLength;
            MindVisuals.drawLine(graphics, cx, cy, ex, ey, thickness + 2, glowColor);
            MindVisuals.drawLine(graphics, cx, cy, ex, ey, thickness, crackColor);

            if (openness > 0.35f) {
                double forkAngle = angle + (MindVisuals.stableRandom(crack.id() * 59 + i * 23) - 0.5) * 1.1;
                double forkStartX = cx + Math.cos(angle) * branchLength * 0.55;
                double forkStartY = cy + Math.sin(angle) * branchLength * 0.55;
                double forkLength = branchLength * 0.38;
                MindVisuals.drawLine(graphics, forkStartX, forkStartY,
                        forkStartX + Math.cos(forkAngle) * forkLength,
                        forkStartY + Math.sin(forkAngle) * forkLength,
                        thickness, crackColor);
            }
        }
    }

    /** The five card buttons, with the currently-selected one highlighted with a bright border. */
    private void renderCardSelection(GuiGraphics graphics) {
        BarType[] bars = BarType.values();
        int spacing = 8;
        int w = MindTextures.SMALL_BUTTON_WIDTH, h = MindTextures.SMALL_BUTTON_HEIGHT;
        int totalWidth = bars.length * w + (bars.length - 1) * spacing;
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - h - 40;

        for (int i = 0; i < bars.length; i++) {
            BarType bar = bars[i];
            int x = startX + i * (w + spacing);
            graphics.blit(MindTextures.smallButton(bar), x, y, 0f, 0f, w, h, w, h);
            if (bar == selectedCard) {
                MindVisuals.drawRing(graphics, x + w / 2, y + h / 2, Math.max(w, h) / 2 + 4, 3, 0xFFFFFFFF);
            }
            graphics.drawCenteredString(this.font, bar.getSerializedName(), x + w / 2, y + h + 2, 0xB8AE9A);
        }
    }

    /** All five bars for one side, stacked vertically, using the real bar frame/fill art. */
    private void renderBars(GuiGraphics graphics, int x, int y, String label, MindDuelClientState.Bars bars, boolean defensiveNames) {
        graphics.drawString(this.font, label, x, y - 12, 0xFFFFFF, false);
        int rowHeight = 22;
        drawBar(graphics, x, y, BarType.FOCUS, bars.focus(), bars.maxFocus(), barLabel(BarType.FOCUS, defensiveNames));
        drawBar(graphics, x, y + rowHeight, BarType.STAMINA, bars.stamina(), bars.maxStamina(), barLabel(BarType.STAMINA, defensiveNames));
        drawBar(graphics, x, y + rowHeight * 2, BarType.WILLPOWER, bars.willpower(), bars.maxWillpower(), barLabel(BarType.WILLPOWER, defensiveNames));
        drawBar(graphics, x, y + rowHeight * 3, BarType.POWER, bars.power(), bars.maxPower(), barLabel(BarType.POWER, defensiveNames));
        drawBar(graphics, x, y + rowHeight * 4, BarType.SPEED, bars.speed(), bars.maxSpeed(), barLabel(BarType.SPEED, defensiveNames));
    }

    private void drawBar(GuiGraphics graphics, int x, int y, BarType bar, float current, float max, String barLabel) {
        int frameW = MindTextures.BAR_FRAME_WIDTH, frameH = MindTextures.BAR_FRAME_HEIGHT;
        graphics.blit(MindTextures.barFrame(bar), x, y, 0f, 0f, frameW, frameH, frameW, frameH);

        float fraction = max > 0 ? Math.max(0f, Math.min(1f, current / max)) : 0f;
        int fillMaxWidth = MindTextures.BAR_FILL_MAX_WIDTH, fillHeight = MindTextures.BAR_FILL_HEIGHT;
        int fillWidth = Math.round(fillMaxWidth * fraction);
        int fillX = x + (frameW - fillMaxWidth) / 2;
        int fillY = y + (frameH - fillHeight) / 2;
        if (fillWidth > 0) {
            // Crop the fill texture horizontally by only blitting fillWidth of it - the fill art is a flat bar, so an unscaled left-aligned crop reads correctly as "partially filled".
            graphics.blit(MindTextures.barFill(bar), fillX, fillY, 0f, 0f, fillWidth, fillHeight, fillMaxWidth, fillHeight);
        }
        graphics.drawString(this.font, barLabel, x + 6, y + 5, 0xE8DDC6, false);
        graphics.drawString(this.font, String.format("%.0f/%.0f", current, max), x + frameW + 6, y + 5, 0xB8AE9A, false);
    }

    private static String barLabel(BarType bar, boolean defensiveNames) {
        if (!defensiveNames) {
            return switch (bar) {
                case FOCUS -> "Focus";
                case STAMINA -> "Stamina";
                case WILLPOWER -> "Will";
                case POWER -> "Power";
                case SPEED -> "Speed";
            };
        }
        return switch (bar) {
            case FOCUS -> "Focus";
            case SPEED -> "Reflexes";
            case STAMINA -> "Endurance";
            case WILLPOWER -> "Resolve";
            case POWER -> "Guard";
        };
    }

    // ---------------------------------------------------------------- shared visuals

    private void drawControlBar(GuiGraphics graphics, int x, int y, int width, float controlAdvantageForSelf) {
        int height = 10;
        graphics.drawCenteredString(this.font, "Control", x + width / 2, y - 12, 0xB8AE9A);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, C_FRAME);
        graphics.fill(x, y, x + width, y + height, 0xAA1A1610);
        int center = x + width / 2;
        float clamped = Math.max(-100f, Math.min(100f, controlAdvantageForSelf));
        int extent = Math.round((width / 2f) * (Math.abs(clamped) / 100f));
        if (clamped >= 0) {
            graphics.fill(center, y, center + extent, y + height, C_CONTROL_SELF);
        } else {
            graphics.fill(center - extent, y, center, y + height, C_CONTROL_OPP);
        }
        graphics.fill(center - 1, y - 2, center + 1, y + height + 2, 0xFFFFFFFF);
    }

    private static String phaseTitle(String phase) {
        return switch (phase) {
            case "defense_breach" -> "Defense Breach";
            case "occupied_mind" -> "Occupied Mind";
            case "true_name_domination" -> "True Name Domination";
            case "ended" -> "Duel Ended";
            default -> "Mind Duel";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}