package com.example.crophud.client;

import com.example.crophud.CropHudMod;
import com.example.crophud.command.ClientCommandAliasNormalizer;
import com.example.crophud.update.UpdateChecker;
import com.example.crophud.command.CropPriceCommand;
import com.example.crophud.command.HudCommand;
import com.example.crophud.hud.HudAnchor;
import com.example.crophud.hud.HudPositionStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CropHudClientMod implements ClientModInitializer {

    private static final Identifier HUD_LAYER_ID = Identifier.of("crophud", "session_hud");

    // Layout constants — package-visible so HudEditScreen can use them.
    static final int HUD_MARGIN       = 8;
    static final int CARD_HEIGHT      = 76;   // 5 data rows × 10px + header 26px
    private static final int CARD_PADDING_X = 6;
    private static final int CARD_PADDING_Y = 5;
    private static final int ROW_GAP        = 10;

    /** Keybinding that opens the HUD position editor. Unbound by default. */
    public static KeyBinding OPEN_EDITOR_KEY;

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    @Override
    public void onInitializeClient() {
        CropHudMod.initializeClient();

        OPEN_EDITOR_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crophud.open_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.crophud"
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CropPriceCommand.register(dispatcher);
            HudCommand.register(dispatcher);
        });

        ClientSendMessageEvents.MODIFY_COMMAND.register(ClientCommandAliasNormalizer::normalize);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CropHudMod.tracker().reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> UpdateChecker.checkAsync(client));

        // Open the HUD editor when the keybinding is pressed.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_EDITOR_KEY.wasPressed()) {
                if (client.player != null && !(client.currentScreen instanceof HudEditScreen)) {
                    client.setScreen(new HudEditScreen());
                }
            }
        });

        HudLayerRegistrationCallback.EVENT.register(layeredDrawer ->
                layeredDrawer.attachLayerBefore(IdentifiedLayer.CHAT, HUD_LAYER_ID,
                        (drawContext, tickCounter) -> renderHud(drawContext)));
    }

    // -------------------------------------------------------------------------
    // HUD rendering entry point
    // -------------------------------------------------------------------------

    private static void renderHud(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        // While the editor is open it renders its own live preview — skip the overlay.
        if (client.currentScreen instanceof HudEditScreen) return;

        int cardWidth = computeCardWidth(client);
        int[] pos = resolvePosition(drawContext.getScaledWindowWidth(),
                drawContext.getScaledWindowHeight(), cardWidth, CARD_HEIGHT);
        renderHudCard(drawContext, pos[0], pos[1], cardWidth, CARD_HEIGHT);
    }

    // -------------------------------------------------------------------------
    // Package-accessible helpers (used by HudEditScreen)
    // -------------------------------------------------------------------------

    /**
     * Returns the card width required to fit the current HUD content.
     */
    static int computeCardWidth(MinecraftClient client) {
        Text cropLabel    = Text.translatable("crophud.hud.crop_label");
        Text unitsLabel   = Text.translatable("crophud.hud.units_label");
        Text specialLabel = Text.translatable("crophud.hud.special_label");
        Text activeLabel  = Text.translatable("crophud.hud.active_label");
        Text valueLabel   = Text.translatable("crophud.hud.value_label");
        Text title        = Text.translatable("crophud.hud.title");
        Text statusText   = Text.translatable(ClientSessionState.active()
                ? "crophud.hud.status.active" : "crophud.hud.status.paused");

        Item cwCrop    = currentCrop();
        Text cropVal   = cwCrop != null
                ? new net.minecraft.item.ItemStack(cwCrop).getName()
                : Text.translatable("crophud.hud.crop_empty");
        Text unitsVal   = Text.literal(String.valueOf(ClientSessionState.harvestedUnits()));
        Text specialVal = Text.literal(String.valueOf(ClientSessionState.specialDrops()));
        Text activeVal  = Text.literal(formatDuration(ClientSessionState.activeMillis()));
        Text valueVal   = Text.literal(formatInteger(ClientSessionState.totalValue()));

        int labelW = Math.max(
                Math.max(client.textRenderer.getWidth(cropLabel),
                         client.textRenderer.getWidth(unitsLabel)),
                Math.max(client.textRenderer.getWidth(specialLabel),
                        Math.max(client.textRenderer.getWidth(activeLabel),
                                 client.textRenderer.getWidth(valueLabel))));

        int titleW  = client.textRenderer.getWidth(title);
        int statusW = client.textRenderer.getWidth(statusText) + 10;
        int rowsW = Math.max(
                rowWidth(client, cropVal,    false, labelW),
                Math.max(rowWidth(client, unitsVal,   false, labelW),
                        Math.max(rowWidth(client, specialVal, false, labelW),
                                Math.max(rowWidth(client, activeVal, false, labelW),
                                         rowWidth(client, valueVal,  false, labelW)))));

        return Math.max(titleW + statusW + 12, rowsW) + (CARD_PADDING_X * 2);
    }

    /**
     * Draws the HUD card at the given (x, y).
     * Called by both the live renderer and the editor screen for its live preview.
     */
    static void renderHudCard(DrawContext ctx, int x, int y, int cardWidth, int cardHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // --- Gather display values ---
        String time       = formatDuration(ClientSessionState.activeMillis());
        String value      = formatInteger(ClientSessionState.totalValue());
        int    specials   = ClientSessionState.specialDrops();
        Text statusText   = Text.translatable(ClientSessionState.active()
                ? "crophud.hud.status.active" : "crophud.hud.status.paused");

        Item crop     = currentCrop();
        Text cropVal  = crop != null
                ? new net.minecraft.item.ItemStack(crop).getName()
                : Text.translatable("crophud.hud.crop_empty");

        Text title        = Text.translatable("crophud.hud.title");
        Text cropLabel    = Text.translatable("crophud.hud.crop_label");
        Text unitsLabel   = Text.translatable("crophud.hud.units_label");
        Text unitsVal     = Text.literal(String.valueOf(ClientSessionState.harvestedUnits()));
        Text specialLabel = Text.translatable("crophud.hud.special_label");
        Text specialVal   = Text.literal(String.valueOf(specials));
        Text activeLabel  = Text.translatable("crophud.hud.active_label");
        Text activeVal    = Text.literal(time);
        Text valueLabel   = Text.translatable("crophud.hud.value_label");
        Text valueVal     = Text.literal(value);

        int labelW  = Math.max(
                Math.max(client.textRenderer.getWidth(cropLabel),
                         client.textRenderer.getWidth(unitsLabel)),
                Math.max(client.textRenderer.getWidth(specialLabel),
                        Math.max(client.textRenderer.getWidth(activeLabel),
                                 client.textRenderer.getWidth(valueLabel))));
        int statusW = client.textRenderer.getWidth(statusText) + 10;

        // --- Draw card background (skipped when user has disabled it) ---
        if (CropHudMod.hudPositionStore().isShowBackground()) {
            ctx.fill(x,     y,     x + cardWidth, y + cardHeight, 0xB5262033);
            ctx.fill(x + 2, y + 2, x + cardWidth - 2, y + cardHeight - 2, 0xCC2F2940);
            ctx.fill(x,     y,     x + cardWidth, y + 3,          0xEFA9D7D0);
            ctx.fill(x + CARD_PADDING_X, y + 19,
                     x + cardWidth - CARD_PADDING_X, y + 20, 0x55FFEAF6);
        }

        // --- Status badge ---
        int statusX  = x + cardWidth - CARD_PADDING_X - statusW;
        int statusY  = y + 5;
        int statusBg = ClientSessionState.active() ? 0xD86ABF9E : 0xD8E2B6A6;
        int statusFg = ClientSessionState.active() ? 0xFFF8FFFB : 0xFF5A3D35;
        ctx.fill(statusX, statusY, statusX + statusW, statusY + 9, statusBg);

        // --- Title & status text ---
        int textX  = x + CARD_PADDING_X;
        int titleY = y + CARD_PADDING_Y;
        ctx.drawText(client.textRenderer, title,      textX,       titleY,      0xFFFDF7FF, true);
        ctx.drawText(client.textRenderer, statusText, statusX + 4, statusY + 1, statusFg,   false);

        // --- Data rows ---
        int r = y + 23;
        // Row 0 — 작물
        drawRow(ctx, client, cropLabel,    cropVal,   null, textX, r,              0xD9C3D6, 0xFFFDF7FF, labelW);
        // Row 1 — 수확량
        drawRow(ctx, client, unitsLabel,   unitsVal,  null, textX, r + ROW_GAP,    0xD9C3D6, 0xFFE7F4FF, labelW);
        // Row 2 — 특수 드랍  (highlighted in amber when > 0)
        int specialColor = specials > 0 ? 0xFFFFD080 : 0xFFE7F4FF;
        drawRow(ctx, client, specialLabel, specialVal, null, textX, r + ROW_GAP * 2, 0xD9C3D6, specialColor, labelW);
        // Row 3 — 활성 시간
        drawRow(ctx, client, activeLabel,  activeVal,  null, textX, r + ROW_GAP * 3, 0xD9C3D6, 0xFFFFF1B8, labelW);
        // Row 4 — 예상 수익
        drawRow(ctx, client, valueLabel,   valueVal,   null, textX, r + ROW_GAP * 4, 0xD9C3D6, 0xFFB7FF9C, labelW);
    }

    // -------------------------------------------------------------------------
    // Position resolution
    // -------------------------------------------------------------------------

    static int[] resolvePosition(int screenW, int screenH, int cardW, int cardH) {
        HudPositionStore store = CropHudMod.hudPositionStore();
        if (store.isCustom()) {
            int x = clamp(store.getCustomX(), 0, screenW - cardW);
            int y = clamp(store.getCustomY(), 0, screenH - cardH);
            return new int[]{x, y};
        }
        return new int[]{
                resolveAnchorX(store.getAnchor(), screenW, cardW),
                resolveAnchorY(store.getAnchor(), screenH, cardH)
        };
    }

    static int resolveAnchorX(HudAnchor anchor, int screenW, int cardW) {
        return switch (anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT            -> screenW - cardW - HUD_MARGIN;
            case CENTER, CENTER_TOP, CENTER_BOTTOM  -> (screenW - cardW) / 2;
            default                                 -> HUD_MARGIN;
        };
    }

    static int resolveAnchorY(HudAnchor anchor, int screenH, int cardH) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT, CENTER_BOTTOM -> screenH - cardH - HUD_MARGIN;
            case CENTER                                    -> (screenH - cardH) / 2;
            case CENTER_TOP                               -> HUD_MARGIN;
            default                                       -> HUD_MARGIN;
        };
    }

    // -------------------------------------------------------------------------
    // Private drawing helpers
    // -------------------------------------------------------------------------

    private static int rowWidth(MinecraftClient client, Text value, boolean withIcon, int labelW) {
        return labelW + 14 + client.textRenderer.getWidth(value) + (withIcon ? 18 : 0);
    }

    private static void drawRow(DrawContext ctx, MinecraftClient client,
                                Text label, Text value, Item iconItem,
                                int x, int y, int labelColor, int valueColor, int labelW) {
        ctx.drawText(client.textRenderer, label, x, y, labelColor, false);
        int valueX = x + labelW + 14;
        if (iconItem != null) {
            ctx.drawItem(new ItemStack(iconItem), valueX, y - 4);
            valueX += 18;
        }
        ctx.drawText(client.textRenderer, value, valueX, y, valueColor, false);
    }

    static Item currentCrop() {
        // Locked crop takes priority — show it immediately even before any harvest occurs.
        String lockedId = ClientSessionState.lockedCropId();
        if (lockedId != null && !lockedId.isBlank()) {
            Identifier parsed = Identifier.tryParse(lockedId);
            if (parsed != null && Registries.ITEM.containsId(parsed)) {
                return Registries.ITEM.get(parsed);
            }
        }
        String id = ClientSessionState.currentCropId();
        if (id == null || id.isBlank()) return null;
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null || !Registries.ITEM.containsId(parsed)) return null;
        return Registries.ITEM.get(parsed);
    }

    static String formatDuration(long millis) {
        long total   = Math.max(0L, millis / 1000L);
        long hours   = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    static String formatDecimal(BigDecimal value) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat fmt = new DecimalFormat("#,##0.##", sym);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(value);
    }

    static String formatInteger(BigDecimal value) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat fmt = new DecimalFormat("#,##0", sym);
        fmt.setMaximumFractionDigits(0);
        fmt.setRoundingMode(java.math.RoundingMode.DOWN);
        return fmt.format(value);
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
