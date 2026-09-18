package com.dragonspeech.client;

import com.dragonspeech.client.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;

/**
 * The 3rd of the 3 ways to open the config GUI per explicit direction:
 * "Using another mod like ModMenu by Terraformers."
 *
 * SOFT DEPENDENCY - this class only exists to satisfy the "modmenu"
 * entrypoint declared in fabric.mod.json, and Fabric Loader only ever
 * INVOKES an entrypoint for a mod ID that's actually present and
 * matches. If ModMenu isn't installed, this class is simply never
 * loaded/instantiated at all - nothing about the rest of DragonSpeech
 * depends on it, and the mod works completely normally without it (the
 * Title Screen button and "/dragonspeechconfig" still both work).
 *
 * build.gradle declares ModMenu as modCompileOnly (compile-time only,
 * never bundled/required at runtime) - see that file's own comment for
 * the exact version/repo. fabric.mod.json lists it under "recommends",
 * not "depends", for the same reason.
 */
public class DragonSpeechModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
