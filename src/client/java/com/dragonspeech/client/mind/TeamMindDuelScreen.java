package com.dragonspeech.client.mind;

import com.dragonspeech.network.TeamActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The team-duel counterpart of MindDuelScreen. One roster view per role:
 * the attacker sees every linked defender with per-target attack
 * buttons; a defender sees the Link Strength meter, their own bars, and
 * per-ally coordination buttons (Reinforce/Protect/Revive), plus the
 * team-wide Focus Burst. Same "stock widgets, not the bespoke mockup
 * art" tradeoff as MindDuelScreen - see docs/MIND_DUEL_PHASE6.md.
 */
public class TeamMindDuelScreen extends Screen {

    private static final int C_BG = 0xD0141018;
    private static final int C_PANEL = 0xFF1E1A16;
    private static final int C_FRAME = 0xFF4A4238;
    private static final int C_FOCUS = 0xFF4B9CD3;
    private static final int C_STAMINA = 0xFFB05010;
    private static final int C_LINK = 0xFF8B5CF6;
    private static final int C_DOWNED = 0xFF3A2020;

    private TeamMindDuelClientState state;

    public TeamMindDuelScreen(String initialJson) {
        super(Component.literal("Mind Link Battle"));
        this.state = TeamMindDuelClientState.parse(initialJson);
    }

    public void refresh(String json) {
        this.state = TeamMindDuelClientState.parse(json);
        if (this.state == null) {
            onClose();
            return;
        }
        clearWidgets();
        rebuildButtons();
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        if (state == null) {
            return;
        }
        iconSpotsXY.clear();
        iconSpotsTex.clear();
        if (state.ended) {
            addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());
            return;
        }

        int rowY = 190;
        int rowHeight = 46;

        if (state.isAttacker) {
            for (TeamMindDuelClientState.Member member : state.defenders) {
                if (!member.downed()) {
                    addRow(member.id(), rowY, new String[] {"Overwhelm", "Isolate", "False Targets", "Wear Down"},
                        new String[] {"overwhelm", "isolate", "false_targets", "wear_them_down"});
                }
                rowY += rowHeight;
            }
            addRenderableWidget(Button.builder(Component.literal("Disrupt Links"), button -> send("disrupt_links", null))
                .bounds(this.width / 2 - 200, this.height - 60, 150, 20)
                .build());
        } else {
            for (TeamMindDuelClientState.Member member : state.defenders) {
                if (member.isSelf()) {
                    continue;
                }
                String[] labels = member.downed() ? new String[] {"Revive"} : new String[] {"Reinforce", "Protect"};
                String[] actionIds = member.downed() ? new String[] {"revive"} : new String[] {"reinforce", "protect"};
                addRow(member.id(), rowY, labels, actionIds);
                rowY += rowHeight;
            }
            addRenderableWidget(Button.builder(Component.literal("Focus Burst"), button -> send("focus_burst", null))
                .bounds(this.width / 2 - 220, this.height - 60, 130, 20)
                .build());
            addRenderableWidget(Button.builder(Component.literal("Restore Focus"), button -> send("restore_focus", null))
                .bounds(this.width / 2 - 80, this.height - 60, 130, 20)
                .build());
        }

        int disengageX = this.width - 100, disengageY = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("Disengage"), button -> send("disengage", null))
            .bounds(disengageX, disengageY, 90, 20)
            .build());
        addIcon(com.dragonspeech.mind.DuelAction.DISENGAGE, disengageX + 3, disengageY + 2);
    }

    private final java.util.List<int[]> iconSpotsXY = new java.util.ArrayList<>();
    private final java.util.List<net.minecraft.resources.ResourceLocation> iconSpotsTex = new java.util.ArrayList<>();

    /** Same idea as MindDuelScreen.addIcon() - only two team actions (restore_focus, disengage) share an identical concept and serialized id with an existing 1v1 icon, so only those two ever get one. */
    private void addIcon(com.dragonspeech.mind.DuelAction action, int x, int y) {
        var texture = MindTextures.iconFor(action);
        if (texture != null) {
            iconSpotsXY.add(new int[] {x, y});
            iconSpotsTex.add(texture);
        }
    }

    private void addRow(UUID memberId, int y, String[] labels, String[] actionIds) {
        int buttonWidth = 95;
        int spacing = 6;
        int totalWidth = labels.length * buttonWidth + (labels.length - 1) * spacing;
        int startX = this.width / 2 - totalWidth / 2;
        for (int i = 0; i < labels.length; i++) {
            String actionId = actionIds[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> send(actionId, memberId))
                .bounds(startX + i * (buttonWidth + spacing), y + 14, buttonWidth, 18)
                .build());
        }
    }

    private void send(String action, UUID targetId) {
        ClientPlayNetworking.send(new TeamActionPayload(action, Optional.ofNullable(targetId)));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, C_BG);

        if (state == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawCenteredString(this.font, "Mind Link Battle" + (state.isAttacker ? " - Attacker" : " - Defender"), this.width / 2, 16, 0xFFFFFF);
        drawLabeledBar(graphics, this.width / 2 - 150, 40, 300, "Link Strength", state.linkStrength / 100f, C_LINK);

        int panelWidth = 260;
        drawCombatantPanel(graphics, this.width / 2 - panelWidth / 2, 60, panelWidth,
            "Attacker: " + state.attacker.name(), state.attacker.focus(), state.attacker.maxFocus(), state.attacker.stamina(), state.attacker.maxStamina(), false);

        drawLinkWeb(graphics, this.width / 2, 140, state.linkStrength);

        int rowY = 190;
        int rowHeight = 46;
        for (TeamMindDuelClientState.Member member : state.defenders) {
            String label = member.name() + (member.isSelf() ? " (you)" : "");
            drawCombatantPanel(graphics, this.width / 2 - panelWidth / 2, rowY, panelWidth, label,
                member.focus(), member.maxFocus(), member.stamina(), member.maxStamina(), member.downed());
            rowY += rowHeight;
        }

        if (state.ended) {
            graphics.drawCenteredString(this.font, "The battle has ended.", this.width / 2, rowY + 10, 0xFFAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < iconSpotsXY.size(); i++) {
            int[] xy = iconSpotsXY.get(i);
            var texture = iconSpotsTex.get(i);
            graphics.blit(texture, xy[0], xy[1], 0f, 0f, MindTextures.ICON_SIZE, MindTextures.ICON_SIZE, MindTextures.ICON_SIZE, MindTextures.ICON_SIZE);
        }
    }

    /**
     * A small radial diagram in the otherwise-empty gap between the
     * attacker panel and the roster list: a central hub (the link
     * itself) with one spoke per linked defender, brightness tied to
     * that member's Focus and a slow pulse tied to overall Link
     * Strength - matches the design notes' "everyone linked to the
     * center" framing (image 8) without needing to restructure the
     * functional, button-aligned panel list below it.
     */
    private void drawLinkWeb(GuiGraphics graphics, int cx, int cy, float linkStrength) {
        if (state.defenders.isEmpty()) {
            return;
        }
        float strengthFraction = Math.max(0f, Math.min(1f, linkStrength / 100f));
        int hubRadius = 10 + Math.round(MindVisuals.pulse(1400) * 2f);
        int hubColor = blend(0xFF3A2E5A, C_LINK, strengthFraction);

        int spokeRadius = 34;
        int count = state.defenders.size();
        for (int i = 0; i < count; i++) {
            var member = state.defenders.get(i);
            double angle = (360.0 / count) * i - 90.0;
            double rad = Math.toRadians(angle);
            int x = cx + (int) Math.round(Math.cos(rad) * spokeRadius);
            int y = cy + (int) Math.round(Math.sin(rad) * spokeRadius * 0.55);

            float focusFraction = member.maxFocus() > 0 ? member.focus() / member.maxFocus() : 0f;
            int memberColor = member.downed() ? 0xFF5A3030 : blend(0xFF303030, C_FOCUS, focusFraction);
            int lineColor = blend(0xFF201A30, hubColor, strengthFraction * (member.downed() ? 0.3f : 1f));

            MindVisuals.drawLine(graphics, cx, cy, x, y, 2, lineColor);
            MindVisuals.fillCircle(graphics, x, y, 7, memberColor);
        }

        MindVisuals.drawRing(graphics, cx, cy, hubRadius + 4, 2, hubColor);
        MindVisuals.fillCircle(graphics, cx, cy, hubRadius, hubColor);
    }

    private void drawCombatantPanel(GuiGraphics graphics, int x, int y, int width, String name,
                                     float focus, float maxFocus, float stamina, float maxStamina, boolean downed) {
        graphics.fill(x - 4, y - 4, x + width + 4, y + 34, downed ? C_DOWNED : C_PANEL);
        graphics.drawString(this.font, name + (downed ? " - DOWNED" : ""), x, y, downed ? 0xFF8888 : 0xFFFFFF, false);
        drawLabeledBar(graphics, x, y + 12, width / 2 - 5, "", maxFocus > 0 ? focus / maxFocus : 0, C_FOCUS);
        drawLabeledBar(graphics, x + width / 2 + 5, y + 12, width / 2 - 5, "", maxStamina > 0 ? stamina / maxStamina : 0, C_STAMINA);
    }

    private void drawLabeledBar(GuiGraphics graphics, int x, int y, int width, String label, float fraction, int color) {
        int height = 8;
        if (!label.isEmpty()) {
            graphics.drawString(this.font, label, x, y - 10, 0xB8AE9A, false);
        }
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, C_FRAME);
        graphics.fill(x, y, x + width, y + height, 0xAA1A1610);
        int fillWidth = Math.round(width * Math.max(0f, Math.min(1f, fraction)));
        if (fillWidth > 0) {
            int shownColor = pulseIfLow(color, fraction);
            graphics.fill(x, y, x + fillWidth, y + height, shownColor);
            graphics.fill(x, y, x + fillWidth, y + Math.max(1, height / 3), lighten(shownColor));
        }
    }

    private static int pulseIfLow(int color, float fraction) {
        if (fraction >= 0.2f) {
            return color;
        }
        float t = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
        return blend(color, 0xFFB03030, t * 0.5f);
    }

    private static int lighten(int color) {
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = Math.min(255, r + 60); g = Math.min(255, g + 60); b = Math.min(255, b + 60);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int colorA, int colorB, float t) {
        int ar = (colorA >> 16) & 0xFF, ag = (colorA >> 8) & 0xFF, ab = colorA & 0xFF;
        int br = (colorB >> 16) & 0xFF, bg = (colorB >> 8) & 0xFF, bb = colorB & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
