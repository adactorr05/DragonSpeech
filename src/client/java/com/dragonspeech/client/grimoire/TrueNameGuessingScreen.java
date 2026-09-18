package com.dragonspeech.client.grimoire;

import com.dragonspeech.client.grid.KnownWordsClientCache;
import com.dragonspeech.network.TrueNameGuessSubmitPayload;
import com.dragonspeech.network.TrueNameLetterAttemptPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Opened by clicking an entry on the Grimoire's True Names list -
 * REPLACES the old TrueNameGuessScreen entirely (that one was mid-duel,
 * high-stakes, single-shot; this one is post-victory, low-stakes,
 * repeatable). Two actions, both just send a payload and wait for the
 * server's chat-message response (TrueNameProgressService via
 * DragonSpeechNetworking) - this screen doesn't try to predict outcomes
 * client-side, it just asks and shows whatever comes back.
 *
 * The 5 mind-word buttons (hugsnert/hugleita/hugrista/hugvarna/hugbinda)
 * only show for words the player actually KNOWS - reads
 * KnownWordsClientCache the same way SpellConstructionScreen already
 * does, rather than showing all 5 and letting an unknown-word attempt
 * fail server-side (clearer, and matches "you can't use a spell you
 * don't know" being a hard rule everywhere else in this mod).
 */
public class TrueNameGuessingScreen extends Screen {

    private static final List<String> MIND_WORDS = List.of("hugsnert", "hugleita", "hugrista", "hugvarna", "hugbinda");

    private final Screen parent;
    private final UUID targetId;
    private final String targetName;
    private EditBox guessInput;
    private String lastMessage = "";

    public TrueNameGuessingScreen(Screen parent, UUID targetId, String targetName) {
        super(Component.literal("True Name: " + targetName));
        this.parent = parent;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        guessInput = new EditBox(this.font, centerX - 120, this.height / 2 - 10, 240, 20, Component.literal("guess"));
        guessInput.setMaxLength(64);
        addRenderableWidget(guessInput);
        setInitialFocus(guessInput);

        addRenderableWidget(Button.builder(Component.literal("Submit Guess"), button -> submitGuess())
            .bounds(centerX - 60, this.height / 2 + 16, 120, 20)
            .build());

        List<String> knownMindWords = MIND_WORDS.stream()
            .filter(w -> KnownWordsClientCache.get().stream().anyMatch(entry -> entry.trueName().equals(w)))
            .toList();

        int buttonY = this.height / 2 + 50;
        int totalWidth = knownMindWords.size() * 90 + Math.max(0, knownMindWords.size() - 1) * 6;
        int startX = centerX - totalWidth / 2;
        for (int i = 0; i < knownMindWords.size(); i++) {
            String word = knownMindWords.get(i);
            int bx = startX + i * 96;
            addRenderableWidget(Button.builder(Component.literal(word), button -> attemptLetter())
                .bounds(bx, buttonY, 90, 20)
                .build());
        }
        if (knownMindWords.isEmpty()) {
            lastMessage = "You know none of the mind-words needed to reach for a letter yet.";
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX - 40, this.height - 40, 80, 20)
            .build());
    }

    private void submitGuess() {
        String guess = guessInput.getValue();
        if (guess == null || guess.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(new TrueNameGuessSubmitPayload(targetId, guess));
        guessInput.setValue("");
    }

    private void attemptLetter() {
        ClientPlayNetworking.send(new TrueNameLetterAttemptPayload(targetId));
    }

    /**
     * Same confirmed fix as DragonBondScreen - Screen.render() calls
     * renderBackground() internally (both directly and again via
     * super.render()), which triggers the vanilla blur regardless of
     * this screen already drawing its own manual dim overlay below.
     * Overriding this as a no-op stops it at the source.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, "Learning the true name of: " + targetName, centerX, this.height / 2 - 60, 0xFFD9A24B);

        String collected = ClientTrueNameCache.get().stream()
            .filter(e -> e.targetId().equals(targetId))
            .findFirst()
            .map(ClientTrueNameCache.Entry::collectedLetters)
            .orElse("");
        String lettersDisplay = collected.isEmpty() ? "(no letters yet)" : String.join(" ", collected.split(""));
        graphics.drawCenteredString(this.font, "Letters gathered: " + lettersDisplay, centerX, this.height / 2 - 44, 0xFFB8AE9A);

        graphics.drawCenteredString(this.font, "Type your assembled guess below, or reach for another letter:",
            centerX, this.height / 2 - 30, 0xFFB8AE9A);

        if (!lastMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, lastMessage, centerX, this.height / 2 + 74, 0xFFCC8888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && guessInput.isFocused()) {
            submitGuess();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
