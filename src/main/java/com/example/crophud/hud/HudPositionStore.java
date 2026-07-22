package com.example.crophud.hud;

import com.example.crophud.CropHudMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HudPositionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "crophud_hud.json";

    private final Path filePath;
    private HudAnchor anchor = HudAnchor.TOP_RIGHT;
    // When useCustom is true, the renderer places the card at (customX, customY)
    // instead of computing a position from the anchor preset.
    private boolean useCustom = false;
    private int customX = 0;
    private int customY = 0;
    /** Whether the card background (fill rectangles) should be drawn. Default: on. */
    private boolean showBackground = true;
    /** Seconds of inactivity before the session timer pauses. Default: 5. Range: 1–300. */
    private int idlePauseSeconds = 5;
    /** Base RGB (0xRRGGBB) used for the card background fill. Default matches the original hardcoded panel color. */
    private int backgroundColor = 0x262033;
    /** Background fill opacity as a percentage (0–100). Default: 71 (~0xB5 alpha), matching the original panel. */
    private int backgroundOpacity = 71;
    /** When true, a user-supplied image (config/crophud/background.png) is drawn instead of the color fill. */
    private boolean useImageBackground = false;

    public HudPositionStore() {
        this.filePath = FabricLoader.getInstance().getConfigDir().resolve(CropHudMod.MOD_ID).resolve(FILE_NAME);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public void load() {
        if (!Files.exists(filePath)) {
            save();
            return;
        }

        try {
            String raw = Files.readString(filePath);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) {
                return;
            }

            if (root.has("anchor") && root.get("anchor").isJsonPrimitive()) {
                anchor = HudAnchor.fromId(root.get("anchor").getAsString());
            }
            if (root.has("mode") && root.get("mode").isJsonPrimitive()
                    && "custom".equals(root.get("mode").getAsString())) {
                useCustom = true;
                if (root.has("customX") && root.get("customX").isJsonPrimitive()) {
                    customX = root.get("customX").getAsInt();
                }
                if (root.has("customY") && root.get("customY").isJsonPrimitive()) {
                    customY = root.get("customY").getAsInt();
                }
            }
            if (root.has("showBackground") && root.get("showBackground").isJsonPrimitive()) {
                showBackground = root.get("showBackground").getAsBoolean();
            }
            if (root.has("idlePauseSeconds") && root.get("idlePauseSeconds").isJsonPrimitive()) {
                idlePauseSeconds = Math.max(1, Math.min(300, root.get("idlePauseSeconds").getAsInt()));
            }
            if (root.has("backgroundColor") && root.get("backgroundColor").isJsonPrimitive()) {
                backgroundColor = root.get("backgroundColor").getAsInt() & 0xFFFFFF;
            }
            if (root.has("backgroundOpacity") && root.get("backgroundOpacity").isJsonPrimitive()) {
                backgroundOpacity = Math.max(0, Math.min(100, root.get("backgroundOpacity").getAsInt()));
            }
            if (root.has("useImageBackground") && root.get("useImageBackground").isJsonPrimitive()) {
                useImageBackground = root.get("useImageBackground").getAsBoolean();
            }
        } catch (Exception e) {
            CropHudMod.LOGGER.error("Failed to load HUD positions from {}", filePath, e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("mode", useCustom ? "custom" : "anchor");
            root.addProperty("anchor", anchor.id());
            root.addProperty("customX", customX);
            root.addProperty("customY", customY);
            root.addProperty("showBackground", showBackground);
            root.addProperty("idlePauseSeconds", idlePauseSeconds);
            root.addProperty("backgroundColor", backgroundColor);
            root.addProperty("backgroundOpacity", backgroundOpacity);
            root.addProperty("useImageBackground", useImageBackground);
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            CropHudMod.LOGGER.error("Failed to save HUD positions to {}", filePath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Anchor-based positioning
    // -------------------------------------------------------------------------

    public HudAnchor getAnchor() {
        return anchor;
    }

    /** Switches to anchor mode with the given preset and persists. */
    public void setPosition(HudAnchor anchor) {
        this.anchor = anchor;
        this.useCustom = false;
        save();
    }

    // -------------------------------------------------------------------------
    // Custom pixel positioning
    // -------------------------------------------------------------------------

    /** Returns true when a custom (x, y) position has been set via the HUD editor. */
    public boolean isCustom() {
        return useCustom;
    }

    public int getCustomX() {
        return customX;
    }

    public int getCustomY() {
        return customY;
    }

    /** Switches to custom mode with absolute pixel coordinates and persists. */
    public void setCustomPosition(int x, int y) {
        this.customX = x;
        this.customY = y;
        this.useCustom = true;
        save();
    }

    // -------------------------------------------------------------------------
    // Background visibility
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the card background should be rendered (default). */
    public boolean isShowBackground() {
        return showBackground;
    }

    /** Toggles card background visibility and persists. */
    public void setShowBackground(boolean show) {
        this.showBackground = show;
        save();
    }

    // -------------------------------------------------------------------------
    // Idle pause delay
    // -------------------------------------------------------------------------

    /** Returns the idle pause delay in milliseconds. */
    public long getIdlePauseMillis() {
        return idlePauseSeconds * 1000L;
    }

    /** Returns the idle pause delay in seconds (1–300). */
    public int getIdlePauseSeconds() {
        return idlePauseSeconds;
    }

    /** Sets the idle pause delay in seconds (clamped to 1–300) and persists. */
    public void setIdlePauseSeconds(int seconds) {
        this.idlePauseSeconds = Math.max(1, Math.min(300, seconds));
        save();
    }

    // -------------------------------------------------------------------------
    // Background appearance (color, opacity, custom image)
    // -------------------------------------------------------------------------

    /** Returns the card background base color as 0xRRGGBB. */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /** Sets the card background base color (any alpha bits in {@code rgb} are ignored) and persists. */
    public void setBackgroundColor(int rgb) {
        this.backgroundColor = rgb & 0xFFFFFF;
        save();
    }

    /** Returns the card background opacity as a percentage (0–100). */
    public int getBackgroundOpacity() {
        return backgroundOpacity;
    }

    /** Sets the card background opacity in percent (clamped to 0–100) and persists. */
    public void setBackgroundOpacity(int percent) {
        this.backgroundOpacity = Math.max(0, Math.min(100, percent));
        save();
    }

    /** Returns {@code true} when a user-supplied image should be used instead of the color fill. */
    public boolean isUseImageBackground() {
        return useImageBackground;
    }

    /** Toggles custom image background mode and persists. */
    public void setUseImageBackground(boolean use) {
        this.useImageBackground = use;
        save();
    }
}
