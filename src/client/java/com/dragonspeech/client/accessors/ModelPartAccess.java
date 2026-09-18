package com.dragonspeech.client.accessors;

/**
 * Vanilla's ModelPart has no notion of per-axis scale on its own - this
 * is the accessor interface the ModelPartMixin (same package tree,
 * client/mixin) implements to add it, so DragonModel can stretch/squash
 * individual parts (the age-scaling growth effect, breathing, etc)
 * without needing a PoseStack.scale() call threaded through every
 * render call by hand. Ported from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.accessors.ModelPartAccess, GPL-3.0).
 */
public interface ModelPartAccess {

    float getXScale();

    float getYScale();

    float getZScale();

    void setXScale(float x);

    void setYScale(float y);

    void setZScale(float z);

    default void setRenderScale(float x, float y, float z) {
        setXScale(x);
        setYScale(y);
        setZScale(z);
    }
}
