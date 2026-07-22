package com.example.crophud.client;

import com.example.crophud.CropHudMod;
import com.example.crophud.hud.HudPositionStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen HUD editor: drag-and-drop positioning plus a live background settings
 * panel (color, opacity, custom image), all in one screen.
 *
 * <p>Opening this screen (via the keybinding or {@code /hud edit}) overlays a translucent
 * backdrop on the running game world. The HUD card can be clicked and dragged anywhere on
 * screen; alignment guides snap it to screen edges and center lines. Closing the screen
 * (ESC / Enter) persists the position as a custom pixel offset in {@link HudPositionStore}.</p>
 *
 * <p>The background panel writes straight through to {@link HudPositionStore} on every
 * change — there is no separate "apply" step, so the live HUD card rendered here doubles
 * as the preview. Background images can be supplied by dropping a file onto this screen
 * ({@link #onFilesDropped}) or via the OS file picker; both are resized to cover the card's
 * aspect ratio by {@link HudBackgroundImageProcessor} before being written to
 * {@code config/crophud/background.png}.</p>
 */
public class HudEditScreen extends Screen {

    /** Pixel distance within which the card snaps to a guide line. */
    private static final int SNAP_THRESHOLD = 6;

    private static final int PANEL_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 16;
    private static final int SWATCH_SIZE = 14;
    private static final int[] PRESET_COLORS = {
            0x262033, 0x1A1A1A, 0x203A2E, 0x2B2410, 0x1B2A3D, 0x3D1B2A, 0x333333, 0xFFFFFF,
    };

    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;

    /** Whether this is the very first {@link #init()} call (used to set position from store). */
    private boolean firstInit = true;

    private boolean dragging = false;
    private boolean wasLeftMouseDown = false;
    private int dragOffsetX;
    private int dragOffsetY;

    // ── Background settings panel layout (computed once in init()) ─────────
    private int panelX;
    private int panelY;
    private int panelBottom;
    private int toggleImageY;
    private int chooseImageY;
    private int colorLabelY;
    private int swatchY;
    private int colorFieldY;
    private int opacityLabelY;
    private int opacityTrackY;
    private int statusY;
    private int dropHintY;

    private TextFieldWidget colorField;
    private boolean draggingOpacity = false;
    private Text statusMessage = null;

    public HudEditScreen() {
        super(Text.translatable("crophud.hud_editor.title"));
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        cardHeight = CropHudClientMod.CARD_HEIGHT;
        cardWidth  = CropHudClientMod.computeCardWidth(client);

        if (firstInit) {
            firstInit = false;
            HudPositionStore store = CropHudMod.hudPositionStore();
            if (store.isCustom()) {
                cardX = store.getCustomX();
                cardY = store.getCustomY();
            } else {
                // Seed the editor position from the current anchor preset.
                cardX = CropHudClientMod.resolveAnchorX(store.getAnchor(), width, cardWidth);
                cardY = CropHudClientMod.resolveAnchorY(store.getAnchor(), height, cardHeight);
            }
        }

        // Keep card on screen after any window resize.
        cardX = CropHudClientMod.clamp(cardX, 0, width  - cardWidth);
        cardY = CropHudClientMod.clamp(cardY, 0, height - cardHeight);

        initBackgroundPanel();
    }

    private void initBackgroundPanel() {
        panelX = 8;
        panelY = 8;

        int y = panelY + 16;
        toggleImageY = y;                      y += BUTTON_HEIGHT + 4;
        chooseImageY = y;                      y += BUTTON_HEIGHT + 10;
        colorLabelY = y;                       y += 11;
        swatchY = y;                           y += SWATCH_SIZE + 5;
        colorFieldY = y;                       y += 18 + 10;
        opacityLabelY = y;                     y += 11;
        opacityTrackY = y;                     y += 10;
        statusY = y + 4;
        dropHintY = statusY + 11;
        panelBottom = dropHintY + 8;

        HudPositionStore store = CropHudMod.hudPositionStore();
        colorField = new TextFieldWidget(textRenderer, panelX + 6, colorFieldY, PANEL_WIDTH - 12, 18,
                Text.translatable("crophud.hud_bg_editor.color_label"));
        colorField.setMaxLength(7);
        colorField.setTextPredicate(s -> s.length() <= 7
                && s.chars().allMatch(c -> c == '#' || Character.digit(c, 16) != -1));
        colorField.setText(toHex(store.getBackgroundColor()));
        colorField.setChangedListener(text -> {
            Integer rgb = parseHex(text);
            if (rgb != null) {
                CropHudMod.hudPositionStore().setBackgroundColor(rgb);
            }
        });
        addDrawableChild(colorField);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Suppress the default opaque/blurred screen background so the game
        // world remains visible behind our translucent overlay.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        updatePointerState(mouseX, mouseY);

        // ── Background overlay ────────────────────────────────────────────────
        ctx.fill(0, 0, width, height, 0x7A000000);

        // ── Alignment guides (center cross + edge hints) ──────────────────────
        int cx = (width  - cardWidth)  / 2;
        int cy = (height - cardHeight) / 2;
        // Horizontal mid-line
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x33FFFFFF);
        // Vertical mid-line
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x33FFFFFF);
        // Center-snap guide (card center aligned)
        ctx.fill(cx, 0, cx + cardWidth, 1, 0x22A9D7D0);
        ctx.fill(0, cy, 1, cy + cardHeight, 0x22A9D7D0);

        // ── Hover / drag highlight border ─────────────────────────────────────
        boolean hovered = isOverCard(mouseX, mouseY);
        if (hovered || dragging) {
            int borderColor = dragging ? 0xCCA9D7D0 : 0x66A9D7D0;
            ctx.fill(cardX - 2, cardY - 2, cardX + cardWidth + 2, cardY + cardHeight + 2, borderColor);
        }

        // ── Live HUD card preview ─────────────────────────────────────────────
        CropHudClientMod.renderHudCard(ctx, cardX, cardY, cardWidth, cardHeight);

        // ── Background settings panel ─────────────────────────────────────────
        renderBackgroundPanel(ctx, mouseX, mouseY);

        // ── Position readout (top-right, out of the panel's way) ───────────────
        Text posText = Text.literal(String.format("X: %d  Y: %d", cardX, cardY));
        ctx.drawText(textRenderer, posText, width - textRenderer.getWidth(posText) - 4, 4, 0xFFAAAAAA, false);

        // ── Instruction bar (bottom) ──────────────────────────────────────────
        Text hint  = Text.translatable("crophud.hud_editor.hint");
        int hintW  = textRenderer.getWidth(hint);
        ctx.fill(0, height - 24, width, height, 0xAA000000);
        ctx.drawText(textRenderer, hint, (width - hintW) / 2, height - 16, 0xFFE0D4F0, true);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderBackgroundPanel(DrawContext ctx, int mouseX, int mouseY) {
        HudPositionStore store = CropHudMod.hudPositionStore();

        ctx.fill(panelX - 6, panelY - 6, panelX + PANEL_WIDTH + 6, panelBottom, 0xE01A1622);
        ctx.fill(panelX - 6, panelY - 6, panelX + PANEL_WIDTH + 6, panelY - 4, 0xFFA9D7D0);
        ctx.drawText(textRenderer, Text.translatable("crophud.hud_bg_editor.title"),
                panelX + 2, panelY, 0xFFFDF7FF, true);

        drawButton(ctx, mouseX, mouseY, toggleImageY, Text.translatable(
                "crophud.hud_bg_editor.toggle_image", onOff(store.isUseImageBackground())));
        drawButton(ctx, mouseX, mouseY, chooseImageY, Text.translatable("crophud.hud_bg_editor.choose_image"));

        ctx.drawText(textRenderer, Text.translatable("crophud.hud_bg_editor.color_label"),
                panelX + 6, colorLabelY, 0xFFD9C3D6, false);
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int sx = panelX + 6 + i * (SWATCH_SIZE + 4);
            ctx.fill(sx, swatchY, sx + SWATCH_SIZE, swatchY + SWATCH_SIZE, 0xFF000000 | PRESET_COLORS[i]);
            ctx.fill(sx, swatchY, sx + SWATCH_SIZE, swatchY + 1, 0x55FFFFFF);
        }

        ctx.drawText(textRenderer, Text.translatable("crophud.hud_bg_editor.opacity_label",
                        store.getBackgroundOpacity()),
                panelX + 6, opacityLabelY, 0xFFD9C3D6, false);
        drawOpacitySlider(ctx, store.getBackgroundOpacity());

        if (statusMessage != null) {
            ctx.drawText(textRenderer, statusMessage, panelX + 6, statusY, 0xFFE0D4F0, false);
        } else {
            boolean imageReady = HudBackgroundTexture.isAvailable();
            String error = HudBackgroundTexture.lastError();
            if (error != null) {
                ctx.drawText(textRenderer, Text.translatable("crophud.hud_bg_editor.image_error", error),
                        panelX + 6, statusY, 0xFFFF8080, false);
            } else {
                ctx.drawText(textRenderer, Text.translatable("crophud.hud_bg_editor.image_status",
                                Text.translatable(imageReady
                                        ? "crophud.hud_bg_editor.image_status_present"
                                        : "crophud.hud_bg_editor.image_status_missing")),
                        panelX + 6, statusY, 0xFFAAAAAA, false);
            }
        }
        ctx.drawText(textRenderer, Text.translatable("crophud.hud_bg_editor.drop_hint"),
                panelX + 6, dropHintY, 0xFF888888, false);
    }

    private void drawButton(DrawContext ctx, int mouseX, int mouseY, int y, Text label) {
        boolean hovered = isOver(mouseX, mouseY, panelX + 6, y, PANEL_WIDTH - 12, BUTTON_HEIGHT);
        ctx.fill(panelX + 6, y, panelX + PANEL_WIDTH - 6, y + BUTTON_HEIGHT, hovered ? 0xFF3D3452 : 0xFF2A2438);
        int textW = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label,
                panelX + 6 + (PANEL_WIDTH - 12 - textW) / 2, y + 4, 0xFFFDF7FF, false);
    }

    private void drawOpacitySlider(DrawContext ctx, int opacity) {
        int trackX = panelX + 6;
        int trackW = PANEL_WIDTH - 12;
        ctx.fill(trackX, opacityTrackY, trackX + trackW, opacityTrackY + 4, 0x66FFFFFF);
        int filledW = Math.round(trackW * (opacity / 100f));
        ctx.fill(trackX, opacityTrackY, trackX + filledW, opacityTrackY + 4, 0xFFA9D7D0);
        int handleX = trackX + filledW;
        ctx.fill(handleX - 2, opacityTrackY - 4, handleX + 2, opacityTrackY + 8, 0xFFFDF7FF);
    }

    // -------------------------------------------------------------------------
    // Pointer handling — raw GLFW polling drives both card dragging and the
    // background panel's buttons/swatches/slider.
    //
    // This intentionally avoids overriding Element#mouseClicked/mouseDragged/
    // mouseReleased: their signature changed (raw doubles → a Click record) in
    // 1.21.10, one of several Minecraft versions this mod's source is compiled
    // against unmodified (see CropHudClientMod's HUD-registration reflection
    // for the same problem elsewhere). Polling GLFW directly sidesteps that
    // entirely. The registered colorField TextFieldWidget still works: Screen's
    // own (un-overridden) default event dispatch keeps routing clicks/typing to
    // it regardless of which Click-vs-doubles signature this version uses.
    // -------------------------------------------------------------------------

    private void updatePointerState(int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        long windowHandle = client.getWindow().getHandle();
        boolean leftMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (leftMouseDown && !wasLeftMouseDown) {
            handlePressStart(mouseX, mouseY);
        } else if (leftMouseDown) {
            if (draggingOpacity) {
                updateOpacityFromMouse(mouseX);
            } else if (dragging) {
                int rawX = mouseX - dragOffsetX;
                int rawY = mouseY - dragOffsetY;
                cardX = snapX(CropHudClientMod.clamp(rawX, 0, width - cardWidth));
                cardY = snapY(CropHudClientMod.clamp(rawY, 0, height - cardHeight));
            }
        } else if (wasLeftMouseDown) {
            dragging = false;
            draggingOpacity = false;
        }

        wasLeftMouseDown = leftMouseDown;
    }

    private void handlePressStart(int mouseX, int mouseY) {
        HudPositionStore store = CropHudMod.hudPositionStore();

        if (isOver(mouseX, mouseY, panelX + 6, toggleImageY, PANEL_WIDTH - 12, BUTTON_HEIGHT)) {
            store.setUseImageBackground(!store.isUseImageBackground());
            return;
        }
        if (isOver(mouseX, mouseY, panelX + 6, chooseImageY, PANEL_WIDTH - 12, BUTTON_HEIGHT)) {
            openFileChooser();
            return;
        }
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int sx = panelX + 6 + i * (SWATCH_SIZE + 4);
            if (isOver(mouseX, mouseY, sx, swatchY, SWATCH_SIZE, SWATCH_SIZE)) {
                store.setBackgroundColor(PRESET_COLORS[i]);
                colorField.setText(toHex(PRESET_COLORS[i]));
                return;
            }
        }
        if (isOver(mouseX, mouseY, panelX + 6, opacityTrackY - 4, PANEL_WIDTH - 12, 16)) {
            draggingOpacity = true;
            updateOpacityFromMouse(mouseX);
            return;
        }

        // A press that starts inside the settings panel must never be interpreted as a
        // card-drag start, even if the (user-positioned) card visually overlaps the panel.
        if (!isOverPanel(mouseX, mouseY) && isOverCard(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - cardX;
            dragOffsetY = mouseY - cardY;
        }
    }

    private void updateOpacityFromMouse(int mouseX) {
        int trackX = panelX + 6;
        int trackW = PANEL_WIDTH - 12;
        int percent = Math.round((mouseX - trackX) / (float) trackW * 100f);
        percent = Math.max(0, Math.min(100, percent));
        if (percent != CropHudMod.hudPositionStore().getBackgroundOpacity()) {
            CropHudMod.hudPositionStore().setBackgroundOpacity(percent);
        }
    }

    // -------------------------------------------------------------------------
    // Image input — drag & drop and the OS file picker both funnel through here
    // -------------------------------------------------------------------------

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (!paths.isEmpty()) {
            handleImageFile(paths.get(0));
        }
    }

    private static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    private void openFileChooser() {
        statusMessage = Text.translatable("crophud.hud_bg_editor.picking");

        // Minecraft's window can otherwise keep the OS dialog stuck behind it (or block
        // interaction with it) under fullscreen — iconify it first so the dialog is
        // guaranteed visible and focusable, then restore it once the dialog closes.
        // GLFW calls must happen on the render thread, which is where this method runs
        // (called from render() → handlePressStart()), so no client.execute() wrapper
        // is needed for the iconify call itself.
        long windowHandle = client.getWindow().getHandle();
        GLFW.glfwIconifyWindow(windowHandle);

        Thread chooserThread = new Thread(() -> {
            final Path[] chosen = new Path[1];
            try {
                // java.awt.FileDialog delegates to the OS's native file picker (the normal
                // Windows Explorer-style "Open" dialog on Windows) instead of Swing's own
                // outdated cross-platform look, which is what JFileChooser would show.
                SwingUtilities.invokeAndWait(() -> {
                    FileDialog dialog = new FileDialog((Frame) null,
                            "Select HUD background image", FileDialog.LOAD);
                    dialog.setAlwaysOnTop(true);
                    dialog.setFilenameFilter((dir, name) -> {
                        String lower = name.toLowerCase(Locale.ROOT);
                        for (String ext : IMAGE_EXTENSIONS) {
                            if (lower.endsWith(ext)) return true;
                        }
                        return false;
                    });
                    dialog.setVisible(true);
                    String file = dialog.getFile();
                    String dir = dialog.getDirectory();
                    if (file != null && dir != null) {
                        chosen[0] = Path.of(dir, file);
                    }
                });
            } catch (Exception e) {
                CropHudMod.LOGGER.error("Failed to open HUD background file chooser", e);
            }

            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> GLFW.glfwRestoreWindow(windowHandle));
            if (chosen[0] != null) {
                Path picked = chosen[0];
                client.execute(() -> handleImageFile(picked));
            } else {
                client.execute(() -> statusMessage = null);
            }
        }, "crophud-file-chooser");
        chooserThread.setDaemon(true);
        chooserThread.start();
    }

    private void handleImageFile(Path path) {
        statusMessage = Text.translatable("crophud.hud_bg_editor.processing");
        int width  = cardWidth;
        int height = cardHeight;

        Thread processingThread = new Thread(() -> {
            String error = HudBackgroundImageProcessor.process(path, width, height);
            MinecraftClient.getInstance().execute(() -> {
                if (error == null) {
                    CropHudMod.hudPositionStore().setUseImageBackground(true);
                    statusMessage = Text.translatable("crophud.hud_bg_editor.processed");
                } else {
                    statusMessage = Text.translatable("crophud.hud_bg_editor.process_failed", error);
                }
            });
        }, "crophud-image-processor");
        processingThread.setDaemon(true);
        processingThread.start();
    }

    // -------------------------------------------------------------------------
    // Close / save
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        // Persist the final position as a custom pixel offset.
        // (Background settings already persist immediately, per change, via HudPositionStore.)
        CropHudMod.hudPositionStore().setCustomPosition(cardX, cardY);
        super.close();
    }

    @Override
    public boolean shouldPause() {
        // Keep the game running so the HUD card shows live data.
        return false;
    }

    // -------------------------------------------------------------------------
    // Snapping helpers
    // -------------------------------------------------------------------------

    /**
     * Snaps X to left edge (0), right edge, or horizontal center of the
     * card-within-screen range when within {@link #SNAP_THRESHOLD} pixels.
     */
    private int snapX(int v) {
        int rightEdge  = width  - cardWidth;
        int centerLine = rightEdge / 2;
        if (Math.abs(v)            <= SNAP_THRESHOLD) return 0;
        if (Math.abs(v - centerLine) <= SNAP_THRESHOLD) return centerLine;
        if (Math.abs(v - rightEdge) <= SNAP_THRESHOLD) return rightEdge;
        return v;
    }

    /**
     * Snaps Y to top edge (0), bottom edge, or vertical center of the
     * card-within-screen range when within {@link #SNAP_THRESHOLD} pixels.
     */
    private int snapY(int v) {
        int bottomEdge = height - cardHeight;
        int centerLine = bottomEdge / 2;
        if (Math.abs(v)             <= SNAP_THRESHOLD) return 0;
        if (Math.abs(v - centerLine)  <= SNAP_THRESHOLD) return centerLine;
        if (Math.abs(v - bottomEdge)  <= SNAP_THRESHOLD) return bottomEdge;
        return v;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private boolean isOverCard(int mx, int my) {
        return mx >= cardX && mx <= cardX + cardWidth
                && my >= cardY && my <= cardY + cardHeight;
    }

    private boolean isOverPanel(double mx, double my) {
        return mx >= panelX - 6 && mx <= panelX + PANEL_WIDTH + 6
                && my >= panelY - 6 && my <= panelBottom;
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static Text onOff(boolean value) {
        return Text.translatable(value ? "crophud.command.hud.background_on" : "crophud.command.hud.background_off");
    }

    private static String toHex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    /** Parses an RRGGBB (optionally "#"-prefixed) color string; returns null when invalid or incomplete. */
    private static Integer parseHex(String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) return null;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
