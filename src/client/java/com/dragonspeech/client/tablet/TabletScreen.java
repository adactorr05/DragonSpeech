package com.dragonspeech.client.tablet;

import com.dragonspeech.network.TranslateAttemptPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The tablet reading screen: an ancient inscription rendered token by
 * token with its damage intact - faded, crossed out, reversed, or
 * shifting (obfuscated) - with the significant words legible and
 * clickable. Clicking one opens the translation choices; picking the
 * true meaning learns the word, picking wrong invites backlash scaled
 * to the tablet's tier.
 *
 * Everything here is display + input only; TranslateAttemptHandler
 * re-validates every attempt against the tablet the player is actually
 * holding, so nothing on this screen is trusted.
 */
public class TabletScreen extends Screen {

    private record Token(String text, String style, String wordId) {}

    private record PlacedToken(Token token, int x, int y, int width) {}

    private int tier = 1;
    private final List<Token> tokens = new ArrayList<>();
    private final List<String> solved = new ArrayList<>();
    private JsonObject choices = new JsonObject();

    private final List<PlacedToken> placed = new ArrayList<>();
    private Token selectedWord;
    private List<String> selectedChoices = List.of();

    private static final int C_BG = 0xE8141018;
    private static final int C_STONE = 0xFF2A2622;
    private static final int C_STONE_EDGE = 0xFF4A4238;
    private static final int C_GOLD = 0xFFC9A24B;
    private static final int C_TEXT = 0xFFB8AE9A;
    private static final int C_TEXT_FADED = 0xFF5A5348;
    private static final int C_WORD = 0xFFEEDB86;
    private static final int C_WORD_SOLVED = 0xFF8BC078;
    private static final int C_LORE = 0xFF9A8FB8;

    public TabletScreen(String contentJson) {
        super(Component.literal("Ancient Inscription"));
        parse(contentJson);
    }

    public void refresh(String contentJson) {
        tokens.clear();
        solved.clear();
        selectedWord = null;
        parse(contentJson);
    }

    private void parse(String contentJson) {
        try {
            JsonObject root = JsonParser.parseString(contentJson).getAsJsonObject();
            tier = root.get("tier").getAsInt();
            choices = root.getAsJsonObject("choices");
            for (JsonElement element : root.getAsJsonArray("solved")) {
                solved.add(element.getAsString());
            }
            for (JsonElement element : root.getAsJsonArray("tokens")) {
                JsonObject obj = element.getAsJsonObject();
                tokens.add(new Token(
                    obj.get("t").getAsString(),
                    obj.get("s").getAsString(),
                    obj.has("w") ? obj.get("w").getAsString() : null
                ));
            }
        } catch (Exception ignored) {
            // Malformed content - screen shows an empty slab rather than crashing.
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        int x0 = slabX();
        int y0 = 30;
        int x1 = x0 + slabW();
        int y1 = this.height - 30;
        g.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, C_STONE_EDGE);
        g.fill(x0, y0, x1, y1, C_STONE);
        g.fill(x0, y0, x1, y0 + 18, 0x33000000);
    }

    private int slabW() {
        return Math.min(380, this.width - 80);
    }

    private int slabX() {
        return (this.width - slabW()) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String tierName = switch (tier) { case 1 -> "Worn"; case 2 -> "Ancient"; default -> "Primordial"; };
        g.drawCenteredString(this.font, "\u2726 " + tierName + " Inscription \u2726", this.width / 2, 36, C_GOLD);

        layoutAndRenderTokens(g, mouseX, mouseY);

        if (selectedWord != null) {
            renderChoicePanel(g, mouseX, mouseY);
        } else {
            g.drawCenteredString(this.font, "The brighter words hold meaning - click one to attempt its translation.",
                this.width / 2, this.height - 24, C_TEXT_FADED);
        }
    }

    private void layoutAndRenderTokens(GuiGraphics g, int mouseX, int mouseY) {
        placed.clear();
        int x0 = slabX() + 14;
        int maxX = slabX() + slabW() - 14;
        int x = x0;
        int y = 58;

        for (Token token : tokens) {
            String display = displayText(token);
            int w = this.font.width(display);

            if (x + w > maxX) {
                x = x0;
                y += 14;
            }
            if (y > this.height - (selectedWord != null ? 130 : 60)) {
                break;
            }

            boolean isWord = token.wordId() != null;
            boolean isSolved = isWord && solved.contains(token.wordId());
            boolean hovered = isWord && !isSolved
                && mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 10;

            int color;
            if (isWord) {
                color = isSolved ? C_WORD_SOLVED : (hovered ? 0xFFFFF3B0 : C_WORD);
            } else {
                color = switch (token.style()) {
                    case "faded" -> C_TEXT_FADED;
                    case "lore" -> C_LORE;
                    default -> C_TEXT;
                };
            }
            if ("lore".equals(token.style())) {
                color = C_LORE;
            }

            g.drawString(this.font, display, x, y, color);

            if ("crossed".equals(token.style())) {
                g.fill(x - 1, y + 3, x + w + 1, y + 5, 0xAA3A2E24);
            }
            if (isWord && !isSolved) {
                g.fill(x, y + 9, x + w, y + 10, hovered ? 0xFFFFF3B0 : 0x88C9A24B);
            }

            placed.add(new PlacedToken(token, x, y, w));
            x += w + this.font.width(" ");
        }
    }

    private String displayText(Token token) {
        return switch (token.style()) {
            case "reversed" -> new StringBuilder(token.text()).reverse().toString();
            case "obfuscated" -> ChatFormatting.OBFUSCATED + token.text() + ChatFormatting.RESET;
            default -> token.text();
        };
    }

    private void renderChoicePanel(GuiGraphics g, int mouseX, int mouseY) {
        int panelY = this.height - 30 - 18 - selectedChoices.size() * 16 - 20;
        g.fill(slabX() + 8, panelY - 4, slabX() + slabW() - 8, this.height - 34, 0xCC0E0C12);
        g.drawCenteredString(this.font, "\"" + selectedWord.text() + "\" means...", this.width / 2, panelY + 2, C_GOLD);

        int y = panelY + 16;
        for (int i = 0; i < selectedChoices.size(); i++) {
            boolean hovered = isOverChoice(mouseX, mouseY, i, panelY);
            g.drawCenteredString(this.font, (hovered ? "\u25B6 " : "") + selectedChoices.get(i),
                this.width / 2, y, hovered ? 0xFFFFF3B0 : C_TEXT);
            y += 16;
        }
    }

    private boolean isOverChoice(int mouseX, int mouseY, int index, int panelY) {
        int y = panelY + 16 + index * 16;
        return mouseX >= slabX() + 12 && mouseX < slabX() + slabW() - 12
            && mouseY >= y - 2 && mouseY < y + 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button) || button != 0) {
            return true;
        }

        if (selectedWord != null) {
            int panelY = this.height - 30 - 18 - selectedChoices.size() * 16 - 20;
            for (int i = 0; i < selectedChoices.size(); i++) {
                if (isOverChoice((int) mouseX, (int) mouseY, i, panelY)) {
                    ClientPlayNetworking.send(new TranslateAttemptPayload(selectedWord.wordId(), selectedChoices.get(i)));
                    selectedWord = null;
                    return true;
                }
            }
            selectedWord = null; // clicked elsewhere - close the panel
            return true;
        }

        for (PlacedToken placedToken : placed) {
            Token token = placedToken.token();
            if (token.wordId() == null || solved.contains(token.wordId())) {
                continue;
            }
            if (mouseX >= placedToken.x() && mouseX < placedToken.x() + placedToken.width()
                && mouseY >= placedToken.y() - 1 && mouseY < placedToken.y() + 10) {
                selectedWord = token;
                selectedChoices = readChoices(token.wordId());
                return true;
            }
        }
        return false;
    }

    private List<String> readChoices(String wordId) {
        List<String> result = new ArrayList<>();
        JsonArray array = choices.getAsJsonArray(wordId);
        if (array != null) {
            for (JsonElement element : array) {
                result.add(element.getAsString());
            }
        }
        return result;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
