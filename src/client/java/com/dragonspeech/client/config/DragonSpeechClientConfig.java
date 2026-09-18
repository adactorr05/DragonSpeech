package com.dragonspeech.client.config;

import com.dragonspeech.DragonSpeech;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Client tab of the config GUI (ConfigScreen). Deliberately a
 * SEPARATE file/class from DragonSpeechConfig (the server config):
 * these values are purely local presentation/performance choices, never
 * sent to or read from a server, and every player on a server can set
 * their own independently - the opposite of DragonSpeechConfig's
 * world-wide, admin-only fields.
 *
 * Loaded once on client init (see DragonSpeechClient#onInitializeClient)
 * and saved immediately whenever the config screen changes anything,
 * same load/save shape as DragonSpeechConfig for consistency.
 */
public final class DragonSpeechClientConfig {

    public enum HudPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** Multiplies how many particles a single spell-fx spawn packet actually renders. See ClientParticleSpawner. */
    private static float particleDensity = 1.0f;
    /** Show/hide the gold stamina HUD bar entirely. See StaminaHudOverlay. */
    private static boolean staminaHudEnabled = true;
    /** Where the stamina bar sits on screen. Default matches the bar's original hardcoded position (right-aligned above the hunger bar). */
    private static HudPosition staminaHudPosition = HudPosition.BOTTOM_RIGHT;
    /** Background panel opacity (0-1) behind the Casting Grid screen. See CastingGridScreen. */
    private static float castingGridOpacity = 0.53f; // matches the original hardcoded 0x88 alpha (136/255)
    /** Show/hide the visual ward ring around a warded entity. The ward mechanic itself is unaffected - purely cosmetic. See WardRingRenderer. */
    private static boolean wardRingVisible = true;
    /** Show/hide the traveling Contact beam while reaching out with your mind. Purely cosmetic. See ContactBeamRenderer. */
    private static boolean contactBeamVisible = true;
    /** Multiplies how far the camera rolls/banks while riding a flying dragon. 0 = no roll at all. See DragonCameraState. */
    private static float dragonCameraRollIntensity = 1.0f;
    /** Full tooltip lines (ward durability/blessing level) vs. just the enchantment's name. See MagicEnchantmentTooltips. */
    private static boolean enchantTooltipVerbose = true;

    private DragonSpeechClientConfig() {}

    public static float particleDensity() {
        return particleDensity;
    }

    public static void setParticleDensity(float value) {
        particleDensity = clamp(value, 0.0f, 3.0f);
        save();
    }

    public static boolean staminaHudEnabled() {
        return staminaHudEnabled;
    }

    public static void setStaminaHudEnabled(boolean value) {
        staminaHudEnabled = value;
        save();
    }

    public static HudPosition staminaHudPosition() {
        return staminaHudPosition;
    }

    public static void setStaminaHudPosition(HudPosition value) {
        staminaHudPosition = value;
        save();
    }

    public static float castingGridOpacity() {
        return castingGridOpacity;
    }

    public static void setCastingGridOpacity(float value) {
        castingGridOpacity = clamp(value, 0.1f, 1.0f);
        save();
    }

    public static boolean wardRingVisible() {
        return wardRingVisible;
    }

    public static void setWardRingVisible(boolean value) {
        wardRingVisible = value;
        save();
    }

    public static boolean contactBeamVisible() {
        return contactBeamVisible;
    }

    public static void setContactBeamVisible(boolean value) {
        contactBeamVisible = value;
        save();
    }

    public static float dragonCameraRollIntensity() {
        return dragonCameraRollIntensity;
    }

    public static void setDragonCameraRollIntensity(float value) {
        dragonCameraRollIntensity = clamp(value, 0.0f, 2.0f);
        save();
    }

    public static boolean enchantTooltipVerbose() {
        return enchantTooltipVerbose;
    }

    public static void setEnchantTooltipVerbose(boolean value) {
        enchantTooltipVerbose = value;
        save();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("dragonspeech_client.json");
        try {
            if (Files.exists(path)) {
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (root.has("particle_density")) particleDensity = root.get("particle_density").getAsFloat();
                if (root.has("stamina_hud_enabled")) staminaHudEnabled = root.get("stamina_hud_enabled").getAsBoolean();
                if (root.has("stamina_hud_position")) {
                    try {
                        staminaHudPosition = HudPosition.valueOf(root.get("stamina_hud_position").getAsString().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        staminaHudPosition = HudPosition.BOTTOM_RIGHT;
                    }
                }
                if (root.has("casting_grid_opacity")) castingGridOpacity = root.get("casting_grid_opacity").getAsFloat();
                if (root.has("ward_ring_visible")) wardRingVisible = root.get("ward_ring_visible").getAsBoolean();
                if (root.has("contact_beam_visible")) contactBeamVisible = root.get("contact_beam_visible").getAsBoolean();
                if (root.has("dragon_camera_roll_intensity")) dragonCameraRollIntensity = root.get("dragon_camera_roll_intensity").getAsFloat();
                if (root.has("enchant_tooltip_verbose")) enchantTooltipVerbose = root.get("enchant_tooltip_verbose").getAsBoolean();
            } else {
                save();
            }
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Could not read client config, using defaults: {}", e.getMessage());
        }
    }

    /** "Reset This Page" (Client tab) in the config GUI - restores every local-only setting to its shipped default and saves. Always purely local, regardless of connection state - these values are never synced from a server. */
    public static void resetToDefaults() {
        particleDensity = 1.0f;
        staminaHudEnabled = true;
        staminaHudPosition = HudPosition.BOTTOM_RIGHT;
        castingGridOpacity = 0.53f;
        wardRingVisible = true;
        contactBeamVisible = true;
        dragonCameraRollIntensity = 1.0f;
        enchantTooltipVerbose = true;
        save();
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("dragonspeech_client.json");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("particle_density", particleDensity);
            root.addProperty("_particle_density_options", "0.0 - 3.0 - multiplies how many particles spell effects spawn. Lower helps performance / visual clutter in big fights.");
            root.addProperty("stamina_hud_enabled", staminaHudEnabled);
            root.addProperty("stamina_hud_position", staminaHudPosition.name());
            root.addProperty("_stamina_hud_position_options", "TOP_LEFT | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_RIGHT");
            root.addProperty("casting_grid_opacity", castingGridOpacity);
            root.addProperty("ward_ring_visible", wardRingVisible);
            root.addProperty("contact_beam_visible", contactBeamVisible);
            root.addProperty("dragon_camera_roll_intensity", dragonCameraRollIntensity);
            root.addProperty("_dragon_camera_roll_intensity_options", "0.0 - 2.0 - how much the camera banks while flying. 0 disables it entirely (helps with motion sickness).");
            root.addProperty("enchant_tooltip_verbose", enchantTooltipVerbose);
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Could not save client config: {}", e.getMessage());
        }
    }
}
