package com.dragonspeech.client.mixin;

import com.dragonspeech.client.config.ConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "DragonSpeech Config" button to the Title Screen per explicit
 * direction (one of the 3 ways the config GUI can be opened, alongside
 * "/dragonspeechconfig" and ModMenu - see ConfigScreen's own doc).
 *
 * FIX: "The Config screen is in the bottom left of the main menu. I want it above the language
 * button" per the bug report (screenshot showed it stacked with vanilla's own copyright text at the
 * bottom of the screen). Moved to sit directly above where vanilla's language/accessibility button
 * row lives (bottom-LEFT corner, one row up), instead of the previous fixed bottom-right position.
 * Still a fixed coordinate rather than reading the real language button's actual position - reading
 * that reliably would need @Local to capture Screen's own local Button variable inside init(), the
 * same @Local/nested-class fragility CreateWorldScreenMagicDifficultyMixin's own doc already
 * documents avoiding - so this uses a manually-measured offset instead. If a future vanilla version
 * shifts that bottom-left row's height/position, nudge Y below to match.
 *
 * DELIBERATE TRADEOFF, same reasoning as CreateWorldScreenMagicDifficultyMixin's own documented
 * choice: this injects at TAIL of TitleScreen's plain init() (a real, ordinary, public/protected
 * method every version of TitleScreen has, unlike GameTab's constructor) and places the button at a
 * FIXED screen position rather than trying to slot it precisely into vanilla's own button grid
 * layout via @Local capture.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenConfigButtonMixin extends net.minecraft.client.gui.screens.Screen {

    protected TitleScreenConfigButtonMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dragonspeech$addConfigButton(CallbackInfo ci) {
        // Bottom-left corner, stacked one row above vanilla's language/accessibility icon row (which
        // itself sits at roughly height-24 with a 20px row height) - see this class's own doc for why
        // this is a fixed offset rather than reading the real button's position.
        this.addRenderableWidget(Button.builder(Component.literal("DragonSpeech Config"),
                        b -> this.minecraft.setScreen(new ConfigScreen(this)))
                .bounds(4, this.height - 48, 150, 20)
                .build());
    }
}
