package com.dragonspeech.client.api;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * How an addon adds its own button to the config GUI's Server tab, per
 * explicit direction: "When the mod is added, the config option in my
 * config will appear and everything." Deliberately GENERIC (per explicit
 * direction: "reusable for any future addon, not just this one") rather
 * than a Neural-Network-specific hook - ConfigScreen renders one button per
 * registered entry and never references any addon's class directly, so
 * this same mechanism works for whatever addon comes after this one too.
 *
 * CLIENT-SIDE ONLY, deliberately separate from MindDuelBrainRegistry (which
 * is server-side, main source set) - this registry is purely "what button
 * shows up and what screen does it open," a GUI concern, and has nothing to
 * do with where the addon's actual AI logic runs. An addon typically
 * registers here from its own client entrypoint (onInitializeClient),
 * mirroring how DragonSpeechClient registers its own screens/buttons today.
 *
 * USAGE (from an addon's client init):
 * <pre>{@code
 * DragonSpeechAddonScreens.register(
 *     "Neural Network",
 *     "Design a player-editable AI brain for your entities and mind-duel tiers.",
 *     parent -> new NeuralNetworkScreen(parent));
 * }</pre>
 * ConfigScreen calls the factory with itself as the parent (matching every
 * other sub-screen it already opens - DifficultyTuningScreen,
 * SentienceEditorScreen), so the addon's screen's own Done/back button can
 * return to the config screen the normal way.
 */
public final class DragonSpeechAddonScreens {

    public record Entry(String buttonLabel, String tooltip, Function<Screen, Screen> screenFactory) {}

    private static final List<Entry> entries = new ArrayList<>();

    private DragonSpeechAddonScreens() {}

    public static void register(String buttonLabel, String tooltip, Function<Screen, Screen> screenFactory) {
        entries.add(new Entry(buttonLabel, tooltip, screenFactory));
    }

    /** Read-only snapshot - ConfigScreen iterates this every rebuild() to add one button per entry. */
    public static List<Entry> entries() {
        return List.copyOf(entries);
    }
}
