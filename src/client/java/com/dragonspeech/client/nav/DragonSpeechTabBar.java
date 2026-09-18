package com.dragonspeech.client.nav;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * "I want to combine all of my keybinded screens into only needing 1
 * keybind. The change will add tabs at the top" per explicit direction.
 * Shared by SpellConstructionScreen, GrimoireScreen, and DragonBondScreen
 * - one keybind (see DragonSpeechClient) now opens Spell Construction by
 * default, and this bar (present on all three) switches between them.
 *
 * Deliberately built as real Button widgets rather than raw pixel-click
 * handling, added via each screen's own addRenderableWidget in its
 * init()/rebuild() - addRenderableWidget is protected on Screen, so it
 * has to be called FROM the screen subclass itself, not from this
 * utility class directly. This was the safer choice specifically
 * because it means zero changes were needed to any of the three
 * screens' existing mouseClicked/render logic beyond "also add these
 * buttons and also draw this bar" - SpellConstructionScreen's own
 * custom mouseClicked already calls super.mouseClicked() FIRST and
 * short-circuits on any widget hit (confirmed by reading it), so a
 * registered Button here is guaranteed first crack at the click, the
 * exact same way its own search box already works. No raw coordinate
 * math to keep in sync with three different screens' own layouts.
 *
 * Tab bar sits at the very top (y 0-16) of every screen it appears on.
 * All three screens had comfortable empty headroom there already (their
 * own title text sits around y 8-16, with real content starting lower;
 * DragonBondScreen's dim overlay is drawn first and doesn't create any
 * new conflict since this bar is added as a widget and renders after) -
 * nothing else needed to shift to make room.
 */
public final class DragonSpeechTabBar {

    public enum Tab {
        SPELL_CONSTRUCTION("Spell Construction"),
        GRIMOIRE("Grimoire"),
        BONDED_DRAGONS("Bonded Dragon");

        private final String label;
        Tab(String label) {
            this.label = label;
        }
        public String label() {
            return label;
        }
    }

    public static final int BAR_HEIGHT = 16;
    private static final int TAB_WIDTH = 110;

    private DragonSpeechTabBar() {}

    /**
     * Which tabs exist right now - Bonded Dragon only included once the
     * player actually has one, per explicit direction ("this screen only
     * appears when you first bond to a dragon. Then the tab will
     * appear"). Reuses DragonBondScreen's own existing lookup rather
     * than tracking bonded state separately here.
     */
    public static List<Tab> availableTabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.SPELL_CONSTRUCTION);
        tabs.add(Tab.GRIMOIRE);
        if (com.dragonspeech.client.gui.DragonBondScreen.findNearestBondedClientSide() != null) {
            tabs.add(Tab.BONDED_DRAGONS);
        }
        return tabs;
    }

    /**
     * One Button per available tab, centered across the top of the
     * screen. The CURRENT tab's button is still a real widget (so the
     * row never visually shifts as tabs come and go) but disabled/inert
     * rather than re-opening the same screen it's already on.
     */
    public static List<Button> buildButtons(int screenWidth, Tab current) {
        List<Tab> tabs = availableTabs();
        int totalWidth = tabs.size() * TAB_WIDTH;
        int x0 = (screenWidth - totalWidth) / 2;

        List<Button> buttons = new ArrayList<>();
        int x = x0;
        for (Tab tab : tabs) {
            boolean isCurrent = tab == current;
            String prefix = isCurrent ? "\u25AA " : "";
            Button button = Button.builder(
                Component.literal(prefix + tab.label()),
                b -> switchTo(tab)
            ).bounds(x, 0, TAB_WIDTH - 2, BAR_HEIGHT).build();
            button.active = !isCurrent;
            buttons.add(button);
            x += TAB_WIDTH;
        }
        return buttons;
    }

    private static void switchTo(Tab tab) {
        Minecraft mc = Minecraft.getInstance();
        switch (tab) {
            case SPELL_CONSTRUCTION -> mc.setScreen(new com.dragonspeech.client.construct.SpellConstructionScreen());
            case GRIMOIRE -> mc.setScreen(new com.dragonspeech.client.grimoire.GrimoireScreen());
            case BONDED_DRAGONS -> {
                var bonded = com.dragonspeech.client.gui.DragonBondScreen.findNearestBondedClientSide();
                if (bonded != null) {
                    mc.setScreen(new com.dragonspeech.client.gui.DragonBondScreen(bonded.getUUID()));
                }
                // If null (dragon wandered out of range between renders), the
                // tab simply wouldn't have been offered - nothing to handle here.
            }
        }
    }
}
