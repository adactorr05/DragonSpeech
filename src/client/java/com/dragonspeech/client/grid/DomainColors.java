package com.dragonspeech.client.grid;

import java.util.Locale;

/**
 * The one place a domain's tint is defined. SpellConstructionScreen's
 * word tiles and GrimoireScreen's list both pull from here, so the
 * color always means the same thing everywhere the player sees it -
 * which is the whole point of surfacing it as a real sorting signal
 * rather than unexplained decoration.
 */
public final class DomainColors {

    public static final String[] ALL_DOMAINS = {
        "fire", "water", "earth", "air", "life", "death", "mind", "force", "motion", "binding", "truth",
        "time", "gravity", "fate", "void", "weapon"
    };

    private DomainColors() {}

    public static int of(String domain) {
        return switch (domain.toLowerCase(Locale.ROOT)) {
            case "fire" -> 0xFF8C3A1E;
            case "water" -> 0xFF2A5F73;
            case "earth" -> 0xFF5C4A2E;
            case "air" -> 0xFF6E7B80;
            case "life" -> 0xFF4E6B2E;
            case "death" -> 0xFF3C3244;
            case "mind" -> 0xFF5A3E73;
            case "force" -> 0xFF71592A;
            case "motion" -> 0xFF2E6B5E;
            case "binding" -> 0xFF6B2E4A;
            case "truth" -> 0xFF365580;
            case "time" -> 0xFF3B7C86;
            case "gravity" -> 0xFF36345F;
            case "fate" -> 0xFF80613B;
            case "void" -> 0xFF241B30;
            case "weapon" -> 0xFF5D6269;
            default -> 0xFF44404E;
        };
    }
}
