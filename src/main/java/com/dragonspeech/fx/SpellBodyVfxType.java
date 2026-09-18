package com.dragonspeech.fx;

/**
 * Generic visual bodies for sentence-composed magic. These are forms, not named spells:
 * FIRE+LANCE and ICE+LANCE share LANCE while the element mask decides how the body looks.
 */
public enum SpellBodyVfxType {
    REMOVE(0),
    BOLT(1),
    RAY(2),
    ORB(3),
    BURST(4),
    RING(5),
    RAIN_STRIKE(6),
    CLOUD(7),
    AURA(8),
    SIGIL(9),
    LANCE(10),
    TETHER(11),
    CLAW(12),
    SPIRAL(13),
    SHELL(14),
    IMPACT(15),
    CONVERGENCE(16),
    ORBIT(17),
    REDIRECT(18),
    WALL_RISE(19),
    PILLAR_RISE(20),
    GALE(21),
    UPDRAFT(22),
    ARC(23),
    /** A moving sharp rotating ring/disc construct: hringr + sveira + hvassa + kasta. */
    CUTTING_RING(24);

    private final int id;

    SpellBodyVfxType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static SpellBodyVfxType byId(int id) {
        for (SpellBodyVfxType type : values()) {
            if (type.id == id) return type;
        }
        return REMOVE;
    }
}
