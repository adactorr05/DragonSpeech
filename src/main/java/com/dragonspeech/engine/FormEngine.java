package com.dragonspeech.engine;

import com.dragonspeech.effect.EffectResult;

/**
 * One reusable spell FORM - the compiled behavior behind a SpellShape.
 * ElementalWorkingHandler resolves the sentence into a WorkingContext and
 * hands it to exactly one of these. Engines never read words directly;
 * everything word-derived arrives pre-resolved in the context, which keeps
 * "what the sentence means" in one place (the handler) and "what the form
 * does" in another (here).
 */
public interface FormEngine {
    EffectResult run(WorkingContext ctx);
}
