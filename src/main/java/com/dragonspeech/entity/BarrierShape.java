package com.dragonspeech.entity;

/**
 * The physical shape a "skjoldr" working takes - SPHERE is the default
 * (no shape word needed), CUBE/WALL are selected by speaking "teningr"/
 * "flata" alongside it. Same fixed-enum-selected-by-a-word pattern as
 * ToolMaterial/ToolType/BlockType elsewhere in this project: the words
 * pick from a small, code-defined set, they don't invent new geometry.
 */
public enum BarrierShape {
    SPHERE,
    CUBE,
    WALL
}
