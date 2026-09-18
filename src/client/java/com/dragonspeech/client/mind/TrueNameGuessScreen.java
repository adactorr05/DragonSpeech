package com.dragonspeech.client.mind;

import com.dragonspeech.mind.DuelAction;
import com.dragonspeech.network.MindDuelActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A real "type the name" prompt for True Name Domination - per the
 * user's explicit design: this is not a card that just fires an action,
 * it's a text field. Submitting sends the typed guess as the
 * SPEAK_TRUE_NAME action's param; the server (MindDuelActionService)
 * checks it against the real hashed true name and either grants
 * permanent-mode access (correct) or ends the duel outright (incorrect)
 * - there is no partial credit and no retry within the same duel.
 */
public class TrueNameGuessScreen extends Screen {

    private final MindDuelScreen parent;
    private EditBox input;

    public TrueNameGuessScreen(MindDuelScreen parent) {
        super(Component.literal("Speak Their True Name"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        input = new EditBox(this.font, this.width / 2 - 120, this.height / 2 - 10, 240, 20, Component.literal("True name"));
        input.setMaxLength(64);
        input.setResponder(text -> {});
        addRenderableWidget(input);
        setInitialFocus(input);

        addRenderableWidget(Button.builder(Component.literal("Speak"), button -> submit())
            .bounds(this.width / 2 - 100, this.height / 2 + 20, 95, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(this.width / 2 + 5, this.height / 2 + 20, 95, 20)
            .build());
    }

    private void submit() {
        String guess = input.getValue();
        if (guess == null || guess.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(new MindDuelActionPayload(DuelAction.SPEAK_TRUE_NAME.getSerializedName(), guess));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        graphics.drawCenteredString(this.font, "Speak their true name.", this.width / 2, this.height / 2 - 40, 0xFFD9A24B);
        graphics.drawCenteredString(this.font, "A wrong guess severs the connection entirely.", this.width / 2, this.height / 2 - 26, 0xFFB8AE9A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
