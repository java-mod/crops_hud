package com.example.crophud.client;

import com.example.crophud.CropHudMod;
import com.example.crophud.hud.HudAnchor;
import com.example.crophud.hud.HudPositionStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Full-screen HUD position editor.
 *
 * <p>Opening this screen (via the keybinding or {@code /harvestsession hud edit})
 * overlays a translucent backdrop on the running game world. The HUD card can be
 * clicked and dragged anywhere on screen. Alignment guides snap the card to screen
 * edges and center lines. Closing the screen (ESC / Enter) persists the new
 * position as a custom pixel offset in {@link HudPositionStore}.</p>
 */
public class HudEditScreen extends Screen {

    /** Pixel distance within which the card snaps to a guide line. */
    private static final int SNAP_THRESHOLD = 6;

    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;

    /** Whether this is the very first {@link #init()} call (used to set position from store). */
    private boolean firstInit = true;

    private boolean dragging = false;
    private int dragOffsetX;
    private int dragOffsetY;

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
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Suppress the default opaque/blurred screen background so the game
        // world remains visible behind our translucent overlay.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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

        // ── Position readout (top-left) ───────────────────────────────────────
        ctx.drawText(textRenderer,
                Text.literal(String.format("X: %d  Y: %d", cardX, cardY)),
                4, 4, 0xFFAAAAAA, false);

        // ── Instruction bar (bottom) ──────────────────────────────────────────
        Text hint  = Text.translatable("crophud.hud_editor.hint");
        int hintW  = textRenderer.getWidth(hint);
        ctx.fill(0, height - 24, width, height, 0xAA000000);
        ctx.drawText(textRenderer, hint, (width - hintW) / 2, height - 16, 0xFFE0D4F0, true);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------
    // Mouse interaction
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverCard((int) mouseX, (int) mouseY)) {
            dragging     = true;
            dragOffsetX  = (int) mouseX - cardX;
            dragOffsetY  = (int) mouseY - cardY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (dragging) {
            int rawX = (int) mouseX - dragOffsetX;
            int rawY = (int) mouseY - dragOffsetY;
            cardX = snapX(CropHudClientMod.clamp(rawX, 0, width  - cardWidth));
            cardY = snapY(CropHudClientMod.clamp(rawY, 0, height - cardHeight));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // -------------------------------------------------------------------------
    // Close / save
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        // Persist the final position as a custom pixel offset.
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
}
