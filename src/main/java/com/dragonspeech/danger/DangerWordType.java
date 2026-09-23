package com.dragonspeech.danger;

import com.dragonspeech.ward.WardType;
import java.util.Arrays;
import java.util.Optional;

public enum DangerWordType {
    LIFSKAD("lifskad", WardType.DANGER_LIFSKAD, 0.25f, 12f),
    LIFROF("lifrof", WardType.DANGER_LIFROF, 0.36f, 15f),
    LIFSLIT("lifslit", WardType.DANGER_LIFSLIT, 0.50f, 19f),
    LIFSTILLA("lifstilla", WardType.DANGER_LIFSTILLA, 0.63f, 23f),
    LIFTHAGN("lifthagn", WardType.DANGER_LIFTHAGN, 0.75f, 28f);
    private final String trueName; private final WardType wardType; private final float fraction; private final float pressure;
    DangerWordType(String n, WardType w, float f, float p) { trueName=n; wardType=w; fraction=f; pressure=p; }
    public String trueName(){return trueName;} public WardType wardType(){return wardType;}
    public float ordinaryHealthFraction(){return fraction;} public float wardPressure(){return pressure;}
    public static Optional<DangerWordType> fromTrueName(String name) {
        if (name == null) return Optional.empty();
        return Arrays.stream(values()).filter(v -> v.trueName.equalsIgnoreCase(name)).findFirst();
    }
}
