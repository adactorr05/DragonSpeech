package com.dragonspeech.client.guess;

import com.dragonspeech.network.GuessSubmitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Plain text-entry guessing screen - see the design note in this turn's
 * summary for why this isn't a custom rune-glyph keyboard yet. Same
 * version-risk profile as CastingGridScreen: stock widgets only, no
 * custom texture dependency.
 */
public class GuessScreen extends Screen {

    private EditBox input;

    public GuessScreen() {
        super(Component.literal("Speak a Word"));
    }

    @Override
    protected void init() {
        super.init();

        input = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 20, 200, 20, Component.literal("word"));
        input.setMaxLength(32);
        addRenderableWidget(input);
        setInitialFocus(input);

        addRenderableWidget(Button.builder(Component.literal("Speak"), button -> submitGuess())
            .bounds(this.width / 2 - 50, this.height / 2 + 10, 100, 20).build());
    }

    private void submitGuess() {
        String candidate = input.getValue();
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(new GuessSubmitPayload(candidate));
        input.setValue("");
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // GLFW_KEY_ENTER
            submitGuess();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics); // skip 1.21's blur post-process
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
